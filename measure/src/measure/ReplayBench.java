package measure;

import baritone.process.elytra.pathfinder.Chunk;
import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;
import org.openjdk.jmh.annotations.*;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

/**
 * Replays the chunk-cache lookups of a recorded flight (whole solves, in the order they ran)
 * through each way of caching the chunk: upstream 1.21.4's Java chunks (java*) and 26.3's native
 * ones (native*). Every benchmark walks the solves the same way, so the variants differ only in the
 * cache; a cache per solver is published to a field, as SolverContext would hold it. One op is the whole replay; divide by the lookup count the setup prints for
 * ns per lookup. The *2 benchmarks run two threads that share one cache (each its own, for the
 * cache per solver), both replaying the same flight one solve apart, as when a solve that was
 * cancelled overlaps the game thread's.
 *
 * Generated; the methods differ only in the cache they use, so each is monomorphic.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ReplayBench {

    @State(Scope.Benchmark)
    public static class Replay {
        int minY;
        int lookups;
        int solves;
        int[] xs;
        int[] ys;
        int[] zs;
        int[] solveFrom;
        JavaCaches.Table table;
        long context;
        final AtomicInteger lanes = new AtomicInteger();
        /** Where a cache per solver is published, so that escape analysis cannot keep its fields in registers. */
        Object solverContext;
        JavaCaches.Current javaCurrent;
        JavaCaches.RecordVolatile javaRecordVolatile;
        JavaCaches.RecordPlain javaRecordPlain;
        JavaCaches.PerThread javaPerThread;
        NativeCaches.Current nativeCurrent;
        NativeCaches.RecordVolatile nativeRecordVolatile;
        NativeCaches.RecordPlain nativeRecordPlain;
        NativeCaches.PerThread nativePerThread;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            final String lib = System.getProperty("nativeLib");
            if (lib != null) {
                System.load(Path.of(lib).toAbsolutePath().toString());
            }
            final boolean useNative = Boolean.getBoolean("native");
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(Path.of(System.getProperty("replay"))), 1 << 16), 1 << 16))) {
                if (in.readInt() != 0x5136) {
                    throw new IOException("not a replay file");
                }
                this.minY = in.readInt();
                this.table = new JavaCaches.Table();
                if (useNative) {
                    this.context = NetherPathfinder.newContext(0L, null, NetherPathfinder.DIMENSION_NETHER, 256, true);
                }
                final int chunks = in.readInt();
                final long[] bits = new long[1536];
                for (int n = 0; n < chunks; n++) {
                    final int cx = in.readInt();
                    final int cz = in.readInt();
                    for (int j = 0; j < 1536; j++) {
                        bits[j] = in.readLong();
                    }
                    final Chunk chunk = new Chunk();
                    final long ptr = useNative ? NetherPathfinder.allocateAndInsertChunk(this.context, cx, cz) : 0L;
                    for (int i = 0; i < 98304; i++) {
                        if ((bits[i >> 6] >>> (i & 63) & 1L) != 0) {
                            final int x = i & 15, z = (i >> 4) & 15, y = i >> 8;
                            chunk.setBlock(x, y, z, true);
                            if (useNative) {
                                Octree.setBlock(ptr, x, y, z, true);
                            }
                        }
                    }
                    this.table.put(cx, cz, chunk);
                }
                this.solves = in.readInt();
                this.solveFrom = new int[this.solves + 1];
                long total = 0;
                for (int s = 0; s < this.solves; s++) {
                    in.readInt(); // which thread ran it, not needed to replay
                    this.solveFrom[s] = (int) total;
                    total += in.readInt();
                }
                this.solveFrom[this.solves] = (int) total;
                this.lookups = (int) total;
                this.xs = new int[this.lookups];
                this.ys = new int[this.lookups];
                this.zs = new int[this.lookups];
                for (int i = 0; i < this.lookups; i++) {
                    this.xs[i] = in.readInt();
                    this.ys[i] = in.readInt();
                    this.zs[i] = in.readInt();
                }
                System.out.println("[replay] " + this.lookups + " lookups in " + this.solves + " solves, " + chunks + " chunks, native " + useNative);
                if (useNative) {
                    // the native chunks must answer exactly as the Java ones, or the comparison means nothing
                    final JavaCaches.Current j = new JavaCaches.Current(this.table, this.minY);
                    final NativeCaches.Current n = new NativeCaches.Current(this.context, this.minY);
                    for (int i = 0; i < this.lookups; i++) {
                        if (j.get0(this.xs[i], this.ys[i], this.zs[i]) != n.get0(this.xs[i], this.ys[i], this.zs[i])) {
                            throw new IllegalStateException("java and native disagree at lookup " + i);
                        }
                    }
                    System.out.println("[replay] java and native agree on all " + this.lookups + " lookups");
                }
            }
            this.javaCurrent = new JavaCaches.Current(this.table, this.minY);
            this.javaRecordVolatile = new JavaCaches.RecordVolatile(this.table, this.minY);
            this.javaRecordPlain = new JavaCaches.RecordPlain(this.table, this.minY);
            this.javaPerThread = new JavaCaches.PerThread(this.table, this.minY);
            if (useNative) {
                this.nativeCurrent = new NativeCaches.Current(this.context, this.minY);
                this.nativeRecordVolatile = new NativeCaches.RecordVolatile(this.context, this.minY);
                this.nativeRecordPlain = new NativeCaches.RecordPlain(this.context, this.minY);
                this.nativePerThread = new NativeCaches.PerThread(this.context, this.minY);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (this.context != 0) {
                NetherPathfinder.freeContext(this.context);
                this.context = 0;
            }
        }
    }

    /** Which solve a thread starts from in the two-thread runs: 0 or 1. */
    @State(Scope.Thread)
    public static class Lane {
        int offset;

        @Setup(Level.Trial)
        public void setup(Replay r) {
            this.offset = r.lanes.getAndIncrement() & 1;
        }
    }

    @Benchmark
    public int javaCurrent(Replay r) {
        final JavaCaches.Current c = r.javaCurrent;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int s = 0, n = r.solves; s < n; s++) {
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    public int javaRecordVolatile(Replay r) {
        final JavaCaches.RecordVolatile c = r.javaRecordVolatile;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int s = 0, n = r.solves; s < n; s++) {
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    public int javaRecordPlain(Replay r) {
        final JavaCaches.RecordPlain c = r.javaRecordPlain;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int s = 0, n = r.solves; s < n; s++) {
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    public int javaPerThread(Replay r) {
        final JavaCaches.PerThread c = r.javaPerThread;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int s = 0, n = r.solves; s < n; s++) {
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    public int javaPerSolver(Replay r) {
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int s = 0, n = r.solves; s < n; s++) {
            final JavaCaches.Current c = new JavaCaches.Current(r.table, r.minY);
            r.solverContext = c; // held by the solve's context, as SolverContext would, so it is a real heap object
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    @Threads(2)
    public int javaCurrent2(Replay r, Lane lane) {
        final JavaCaches.Current c = r.javaCurrent;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int k = 0, n = r.solves; k < n; k++) {
            final int s = (k + lane.offset) % n;
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    @Threads(2)
    public int javaRecordVolatile2(Replay r, Lane lane) {
        final JavaCaches.RecordVolatile c = r.javaRecordVolatile;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int k = 0, n = r.solves; k < n; k++) {
            final int s = (k + lane.offset) % n;
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    @Threads(2)
    public int javaRecordPlain2(Replay r, Lane lane) {
        final JavaCaches.RecordPlain c = r.javaRecordPlain;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int k = 0, n = r.solves; k < n; k++) {
            final int s = (k + lane.offset) % n;
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    @Threads(2)
    public int javaPerSolver2(Replay r, Lane lane) {
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int k = 0, n = r.solves; k < n; k++) {
            final int s = (k + lane.offset) % n;
            final JavaCaches.Current c = new JavaCaches.Current(r.table, r.minY);
            r.solverContext = c; // held by the solve's context, as SolverContext would, so it is a real heap object
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    public int nativeCurrent(Replay r) {
        final NativeCaches.Current c = r.nativeCurrent;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int s = 0, n = r.solves; s < n; s++) {
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    public int nativeRecordVolatile(Replay r) {
        final NativeCaches.RecordVolatile c = r.nativeRecordVolatile;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int s = 0, n = r.solves; s < n; s++) {
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    public int nativeRecordPlain(Replay r) {
        final NativeCaches.RecordPlain c = r.nativeRecordPlain;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int s = 0, n = r.solves; s < n; s++) {
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    public int nativePerThread(Replay r) {
        final NativeCaches.PerThread c = r.nativePerThread;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int s = 0, n = r.solves; s < n; s++) {
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    public int nativePerSolver(Replay r) {
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int s = 0, n = r.solves; s < n; s++) {
            final NativeCaches.Current c = new NativeCaches.Current(r.context, r.minY);
            r.solverContext = c; // held by the solve's context, as SolverContext would, so it is a real heap object
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    @Threads(2)
    public int nativeCurrent2(Replay r, Lane lane) {
        final NativeCaches.Current c = r.nativeCurrent;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int k = 0, n = r.solves; k < n; k++) {
            final int s = (k + lane.offset) % n;
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    @Threads(2)
    public int nativeRecordVolatile2(Replay r, Lane lane) {
        final NativeCaches.RecordVolatile c = r.nativeRecordVolatile;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int k = 0, n = r.solves; k < n; k++) {
            final int s = (k + lane.offset) % n;
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    @Threads(2)
    public int nativeRecordPlain2(Replay r, Lane lane) {
        final NativeCaches.RecordPlain c = r.nativeRecordPlain;
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int k = 0, n = r.solves; k < n; k++) {
            final int s = (k + lane.offset) % n;
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }

    @Benchmark
    @Threads(2)
    public int nativePerSolver2(Replay r, Lane lane) {
        final int[] xs = r.xs, ys = r.ys, zs = r.zs, from = r.solveFrom;
        int solid = 0;
        for (int k = 0, n = r.solves; k < n; k++) {
            final int s = (k + lane.offset) % n;
            final NativeCaches.Current c = new NativeCaches.Current(r.context, r.minY);
            r.solverContext = c; // held by the solve's context, as SolverContext would, so it is a real heap object
            for (int i = from[s], end = from[s + 1]; i < end; i++) {
                if (c.get0(xs[i], ys[i], zs[i])) {
                    solid++;
                }
            }
        }
        return solid;
    }
}
