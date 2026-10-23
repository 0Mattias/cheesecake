package measure;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reads what LookupTrace wrote during a flight, prints what the flight did (solves, lookups,
 * how often two threads were in the cache at once), and writes the replay set the benchmark reads:
 * the chunks plus a sample of whole solves, in the order they ran.
 *
 * Usage: Analyze traceDir [replayOut maxLookups minY]
 */
public final class Analyze {

    static final int BEGIN = Integer.MIN_VALUE;
    static final int END = Integer.MIN_VALUE + 1;

    static final class Solve {
        String thread;
        boolean gameThread;
        int kind;
        long t0;
        long t1;
        int count;
        boolean complete;
        Path file;
        long fromInt; // first int of its first lookup in the file
        long toInt;
    }

    public static void main(String[] args) throws IOException {
        final Path dir = Path.of(args[0]);
        final List<Solve> solves = new ArrayList<>();
        long outside = 0;
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().startsWith("thread-")).sorted().toList();
        }
        for (Path f : files) {
            final String name = f.getFileName().toString();
            final boolean game = name.contains("Render");
            try (FileChannel ch = FileChannel.open(f, StandardOpenOption.READ)) {
                final long ints = ch.size() / 4;
                long pos = 0;
                Solve cur = null;
                while (pos < ints) {
                    final long len = Math.min(ints - pos, 1L << 26);
                    final IntBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, pos * 4, len * 4).asIntBuffer();
                    // markers are two triples; never split one across windows
                    long i = 0;
                    while (i + 3 <= len) {
                        final int a = buf.get((int) i);
                        if (a == BEGIN || a == END) {
                            if (i + 6 > len) {
                                break;
                            }
                            final long t = ((long) buf.get((int) i + 3) << 32) | (buf.get((int) i + 4) & 0xFFFFFFFFL);
                            if (a == BEGIN) {
                                cur = new Solve();
                                cur.thread = name;
                                cur.gameThread = game;
                                cur.kind = buf.get((int) i + 1);
                                cur.t0 = t;
                                cur.file = f;
                                cur.fromInt = pos + i + 6;
                            } else if (cur != null) {
                                cur.t1 = t;
                                cur.count = buf.get((int) i + 1);
                                cur.toInt = pos + i;
                                cur.complete = buf.get((int) i + 2) == 1 && (cur.toInt - cur.fromInt) == 3L * cur.count;
                                solves.add(cur);
                                cur = null;
                            }
                            i += 6;
                        } else {
                            if (cur == null) {
                                outside++;
                            }
                            i += 3;
                        }
                    }
                    if (i == 0) {
                        break; // a marker cut off at the end of the file
                    }
                    pos += i;
                }
            }
        }
        solves.sort(Comparator.comparingLong(s -> s.t0));
        report(dir, files.size(), solves, outside);
        if (args.length >= 4) {
            writeReplay(dir, solves, Path.of(args[1]), Long.parseLong(args[2]), Integer.parseInt(args[3]));
        }
    }

    static String kindName(Solve s) {
        if (s.kind == 2) {
            return "obstacle check (game thread)";
        }
        return s.gameThread ? "solve on the game thread" : "solve on the solver thread";
    }

    static void report(Path dir, int threads, List<Solve> solves, long outside) {
        System.out.println("== " + dir);
        System.out.println("threads that looked up: " + threads + ", lookups outside a solve: " + outside);
        if (solves.isEmpty()) {
            System.out.println("no solves");
            return;
        }
        final long first = solves.get(0).t0;
        long last = 0;
        for (Solve s : solves) {
            last = Math.max(last, s.t1);
        }
        System.out.printf("span of the solves: %.1f s%n", (last - first) / 1e9);
        for (String kind : new String[]{"solve on the solver thread", "solve on the game thread", "obstacle check (game thread)"}) {
            final List<Solve> of = solves.stream().filter(s -> kindName(s).equals(kind)).toList();
            if (of.isEmpty()) {
                continue;
            }
            final long[] counts = of.stream().mapToLong(s -> s.count).sorted().toArray();
            final double[] ms = of.stream().mapToDouble(s -> (s.t1 - s.t0) / 1e6).sorted().toArray();
            final long total = Arrays.stream(counts).sum();
            System.out.printf("%-30s %7d of them, lookups %,d (mean %,.0f, median %,d, p90 %,d, max %,d), time ms mean %.3f median %.3f p90 %.3f p99 %.3f%n",
                    kind, of.size(), total, (double) total / of.size(), counts[counts.length / 2], counts[(int) (counts.length * 0.9)], counts[counts.length - 1],
                    Arrays.stream(ms).average().orElse(0), ms[ms.length / 2], ms[(int) (ms.length * 0.9)], ms[(int) (ms.length * 0.99)]);
        }
        // two threads in the cache at once: solves on different threads whose spans overlap
        long overlapNanos = 0;
        long busyNanos = 0;
        double lookupsInOverlap = 0;
        long overlappingSolves = 0;
        long totalLookups = 0;
        final double[] overlapOf = new double[solves.size()];
        for (int i = 0; i < solves.size(); i++) {
            final Solve a = solves.get(i);
            totalLookups += a.count;
            for (int j = i + 1; j < solves.size() && solves.get(j).t0 < a.t1; j++) {
                final Solve b = solves.get(j);
                if (b.thread.equals(a.thread)) {
                    continue;
                }
                final long o = Math.min(a.t1, b.t1) - b.t0;
                if (o > 0) {
                    overlapNanos += o;
                    overlapOf[i] += o;
                    overlapOf[j] += o;
                }
            }
        }
        long cursor = Long.MIN_VALUE;
        for (Solve s : solves) {
            final long from = Math.max(cursor, s.t0);
            if (s.t1 > from) {
                busyNanos += s.t1 - from;
                cursor = s.t1;
            }
        }
        for (int i = 0; i < solves.size(); i++) {
            final Solve s = solves.get(i);
            if (overlapOf[i] > 0) {
                overlappingSolves++;
                final long d = Math.max(1, s.t1 - s.t0);
                lookupsInOverlap += s.count * Math.min(1.0, overlapOf[i] / d);
            }
        }
        System.out.printf("time with a solve or check running: %.2f s; with two on different threads at once: %.3f s (%.2f%%)%n",
                busyNanos / 1e9, overlapNanos / 1e9, 100.0 * overlapNanos / Math.max(1, busyNanos));
        System.out.printf("solves/checks that overlapped one on another thread: %d of %d; lookups made while overlapping (estimate): %,.0f of %,d (%.2f%%)%n",
                overlappingSolves, solves.size(), lookupsInOverlap, totalLookups, 100.0 * lookupsInOverlap / Math.max(1, totalLookups));
        final long complete = solves.stream().filter(s -> s.complete).count();
        System.out.println("solves with every lookup recorded: " + complete + " of " + solves.size());
    }

    static void writeReplay(Path dir, List<Solve> all, Path out, long maxLookups, int minY) throws IOException {
        final List<Solve> complete = new ArrayList<>(all.stream().filter(s -> s.complete && s.count > 0).toList());
        long total = complete.stream().mapToLong(s -> s.count).sum();
        // whole solves, picked evenly over the flight, until the sample is big enough
        List<Solve> chosen = complete;
        if (total > maxLookups) {
            final SplittableRandom r = new SplittableRandom(5136);
            final double keep = (double) maxLookups / total;
            chosen = complete.stream().filter(s -> r.nextDouble() < keep).toList();
        }
        final long lookups = chosen.stream().mapToLong(s -> s.count).sum();
        // chunk changes, as one shared cache and as a cache per solve would see them
        long sharedChanges = 0;
        long perSolveChanges = 0;
        int px = Integer.MAX_VALUE;
        int pz = Integer.MAX_VALUE;
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(out), 1 << 16), 1 << 16))) {
            o.writeInt(0x5136);
            o.writeInt(minY);
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(dir.resolve("chunks.bin.gz")), 1 << 16))) {
                final int n = in.readInt();
                o.writeInt(n);
                for (int i = 0; i < n; i++) {
                    o.writeInt(in.readInt());
                    o.writeInt(in.readInt());
                    for (int j = 0; j < 1536; j++) {
                        o.writeLong(in.readLong());
                    }
                }
                System.out.println("chunks in the table over the flight: " + n);
            }
            o.writeInt(chosen.size());
            for (Solve s : chosen) {
                o.writeInt((s.gameThread ? 1 : 0) | (s.kind == 2 ? 2 : 0));
                o.writeInt(s.count);
            }
            for (Solve s : chosen) {
                try (FileChannel ch = FileChannel.open(s.file, StandardOpenOption.READ)) {
                    final ByteBuffer bytes = ByteBuffer.allocate((int) ((s.toInt - s.fromInt) * 4));
                    ch.read(bytes, s.fromInt * 4);
                    bytes.flip();
                    final IntBuffer ints = bytes.asIntBuffer();
                    int sx = Integer.MAX_VALUE;
                    int sz = Integer.MAX_VALUE;
                    for (int i = 0; i < s.count; i++) {
                        final int x = ints.get(), y = ints.get(), z = ints.get();
                        o.writeInt(x);
                        o.writeInt(y);
                        o.writeInt(z);
                        if (y - minY < 0 || y - minY > 383) {
                            continue;
                        }
                        final int cx = x >> 4, cz = z >> 4;
                        if (cx != px || cz != pz) {
                            sharedChanges++;
                            px = cx;
                            pz = cz;
                        }
                        if (cx != sx || cz != sz) {
                            perSolveChanges++;
                            sx = cx;
                            sz = cz;
                        }
                    }
                }
            }
        }
        Files.writeString(Path.of(out + ".count"), lookups + " " + chosen.size() + " " + sharedChanges + " " + perSolveChanges + "\n");
        System.out.printf("replay set: %d solves, %,d lookups; a chunk change every %.2f lookups with one shared cache, %.2f with a cache per solve (%.2f%% / %.2f%%)%n",
                chosen.size(), lookups, (double) lookups / Math.max(1, sharedChanges), (double) lookups / Math.max(1, perSolveChanges),
                100.0 * sharedChanges / Math.max(1, lookups), 100.0 * perSolveChanges / Math.max(1, lookups));
    }
}
