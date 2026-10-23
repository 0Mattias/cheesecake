/*
 * This file is part of Cheesecake.
 *
 * Cheesecake is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Cheesecake is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Cheesecake.  If not, see <https://www.gnu.org/licenses/>.
 */

package cheesecake.process.elytra;

import cheesecake.process.elytra.pathfinder.Chunk;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

/**
 * Measurement for cabaletta/baritone#5136 only, not for main: records every chunk-cache lookup the
 * elytra solver makes, which thread made it, where each solve starts and ends, and every chunk the
 * table held, so that the lookups of a real flight can be replayed through each way of caching
 * the chunk. Off unless CHEESECAKE_TRACE_DIR is set.
 *
 * Each thread writes its own file of big-endian int triples. A lookup is (x, y, z). A solve's
 * start is (MIN_VALUE, kind, 0) then (nanoTime high, nanoTime low, 0); its end is
 * (MIN_VALUE + 1, lookups in the solve, 1 if all of them were written) then the time the same way.
 * With CHEESECAKE_TRACE_LOOKUPS=0 only the solves are written, which leaves their timing alone.
 */
public final class LookupTrace {

    /** solveAngles; which thread ran it is in the file's name. */
    public static final int SOLVE = 0;
    /** pathfindAroundObstacles, on the game thread. */
    public static final int OBSTACLES = 2;

    private static final String DIR = System.getenv("CHEESECAKE_TRACE_DIR");
    public static final boolean ON = DIR != null;
    /** Whether get0 calls {@link #lookup} at all; off, only the solves are written and their timing is undisturbed. */
    public static final boolean LOOKUPS = ON && !"0".equals(System.getenv("CHEESECAKE_TRACE_LOOKUPS"));
    private static final long CAP = ON && System.getenv("CHEESECAKE_TRACE_CAP") != null
            ? Long.parseLong(System.getenv("CHEESECAKE_TRACE_CAP")) : 60_000_000L;
    private static final int GRANT = 1 << 16;

    private static final AtomicLong granted = new AtomicLong();
    private static volatile boolean full;
    private static final AtomicLong failures = new AtomicLong();
    private static final AtomicLong outsideSolves = new AtomicLong();
    private static final List<Writer> writers = new ArrayList<>();
    private static final ThreadLocal<Writer> LOCAL = ThreadLocal.withInitial(Writer::new);
    private static final ConcurrentHashMap<Long, Object[]> chunks = new ConcurrentHashMap<>();

    private LookupTrace() {}

    public static void lookup(int x, int y, int z) {
        LOCAL.get().lookup(x, y, z);
    }

    public static void begin(int kind) {
        LOCAL.get().begin(kind);
    }

    public static void end() {
        LOCAL.get().end();
    }

    /** Called with every chunk the table takes in, so that the last version of each can be written out. */
    public static void chunk(int x, int z, Chunk chunk) {
        chunks.put(((long) x << 32) | (z & 0xFFFFFFFFL), new Object[]{x, z, chunk});
    }

    private static boolean finished;

    /** Writes everything out, once, on the game thread when the stages are done. Never throws. */
    public static synchronized void finish() {
        if (!ON || finished) {
            return;
        }
        finished = true;
        try {
            finish0();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void finish0() {
        try {
            Thread.sleep(3000); // let the last solver threads wind down
        } catch (InterruptedException ignored) {}
        final Path dir = Path.of(DIR);
        long lookups = 0;
        long written = 0;
        long solves = 0;
        synchronized (writers) {
            for (Writer w : writers) {
                synchronized (w) {
                    w.close();
                    lookups += w.lookups;
                    written += w.written;
                    solves += w.solves;
                }
            }
        }
        if (failures.get() > 0) {
            System.err.println("[LookupTrace] " + failures.get() + " writes failed; the trace is incomplete");
        }
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(dir.resolve("chunks.bin.gz")), 1 << 16))) {
            out.writeInt(chunks.size());
            for (Object[] e : chunks.values()) {
                final Chunk c = (Chunk) e[2];
                out.writeInt((Integer) e[0]);
                out.writeInt((Integer) e[1]);
                final long[] bits = new long[Chunk.HEIGHT * 256 / 64];
                for (int y = 0; y < Chunk.HEIGHT; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (c.isSolid(x, y, z)) {
                                final int i = (y << 8) | (z << 4) | x;
                                bits[i >> 6] |= 1L << (i & 63);
                            }
                        }
                    }
                }
                for (long l : bits) {
                    out.writeLong(l);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (PrintWriter p = new PrintWriter(Files.newBufferedWriter(dir.resolve("summary.txt")))) {
            p.println("threads " + writers.size());
            p.println("solves " + solves);
            p.println("lookups " + lookups);
            p.println("lookups outside solves " + outsideSolves.get());
            p.println("lookups written " + written);
            p.println("chunks " + chunks.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final class Writer {
        /**
         * Not a FileChannel: the game cancels a solve by interrupting its thread, and an
         * interrupt closes a FileChannel that thread is writing to. A stream ignores it.
         */
        private final FileOutputStream out;
        private final int[] buf = new int[3 * 8192];
        private int pos;
        private int allowance;
        private int depth;
        private long solveLookups;
        private boolean solveComplete;
        private boolean closed;
        long lookups;
        long written;
        long solves;

        Writer() {
            final Thread t = Thread.currentThread();
            FileOutputStream o = null;
            try {
                final Path dir = Path.of(DIR);
                Files.createDirectories(dir);
                o = new FileOutputStream(dir.resolve("thread-" + t.threadId() + "-" + t.getName().replaceAll("[^A-Za-z0-9_-]", "_") + ".bin").toFile());
            } catch (IOException e) {
                failures.incrementAndGet();
                e.printStackTrace();
                this.closed = true;
            }
            this.out = o;
            synchronized (writers) {
                writers.add(this);
            }
        }

        void lookup(int x, int y, int z) {
            this.lookups++;
            if (this.depth > 0) {
                this.solveLookups++;
            } else {
                outsideSolves.incrementAndGet();
            }
            if (this.closed) {
                return;
            }
            if (this.allowance == 0) {
                if (full || granted.getAndAdd(GRANT) + GRANT > CAP) {
                    full = true;
                    this.solveComplete = false;
                    return;
                }
                this.allowance = GRANT;
            }
            this.allowance--;
            this.written++;
            put(x, y, z);
        }

        void begin(int kind) {
            if (this.depth++ > 0 || this.closed) {
                return;
            }
            this.solves++;
            this.solveLookups = 0;
            this.solveComplete = LOOKUPS;
            final long t = System.nanoTime();
            put(Integer.MIN_VALUE, kind, 0);
            put((int) (t >>> 32), (int) t, 0);
        }

        void end() {
            if (--this.depth > 0 || this.closed) {
                return;
            }
            final long t = System.nanoTime();
            put(Integer.MIN_VALUE + 1, (int) Math.min(Integer.MAX_VALUE, this.solveLookups), this.solveComplete ? 1 : 0);
            put((int) (t >>> 32), (int) t, 0);
        }

        private void put(int a, int b, int c) {
            this.buf[this.pos++] = a;
            this.buf[this.pos++] = b;
            this.buf[this.pos++] = c;
            if (this.pos == this.buf.length) {
                flush();
            }
        }

        // never throws: a failure here must not change what the solver does
        private synchronized void flush() {
            if (this.out != null && this.pos > 0) {
                final ByteBuffer bytes = ByteBuffer.allocate(this.pos * 4);
                bytes.asIntBuffer().put(this.buf, 0, this.pos);
                try {
                    this.out.write(bytes.array(), 0, this.pos * 4);
                } catch (IOException e) {
                    failures.incrementAndGet();
                }
            }
            this.pos = 0;
        }

        void close() {
            if (this.closed) {
                return;
            }
            flush();
            this.closed = true;
            try {
                if (this.out != null) {
                    this.out.close();
                }
            } catch (IOException e) {
                failures.incrementAndGet();
            }
        }
    }
}
