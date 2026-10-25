package measure;

import baritone.process.elytra.pathfinder.Chunk;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BlockStateOctreeInterface.get0 on upstream 1.21.4 (the Java port), and the ways of fixing it.
 * Table is NetherPathfinder's chunk table and getChunkOrDefault as 1.21.4 has them.
 */
public final class JavaCaches {

    public interface Get0 {
        boolean get0(int x, int y, int z);
    }

    static final class Entry {
        final boolean fromCaller;
        final Chunk chunk;
        final int x;
        final int z;

        Entry(boolean fromCaller, Chunk chunk, int x, int z) {
            this.fromCaller = fromCaller;
            this.chunk = chunk;
            this.x = x;
            this.z = z;
        }
    }

    public static final class Table {
        private final ConcurrentHashMap<Long, Entry> chunks = new ConcurrentHashMap<>();

        static long key(int x, int z) {
            return (((long) x << 32) | (z & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L;
        }

        public void put(int x, int z, Chunk chunk) {
            this.chunks.put(key(x, z), new Entry(true, chunk, x, z));
        }

        public Chunk getChunkOrDefault(int x, int z, boolean solid) {
            final Entry e = this.chunks.get(key(x, z));
            return e != null ? e.chunk : (solid ? Chunk.SOLID : Chunk.AIR);
        }
    }

    /** As on 1.21.4 today: three plain fields. Also what a cache per solver is, one instance per solve. */
    public static final class Current implements Get0 {
        private final Table pathfinder;
        private final int minY;
        Chunk chunk;
        private int prevChunkX = Integer.MAX_VALUE;
        private int prevChunkZ = Integer.MAX_VALUE;

        public Current(Table pathfinder, int minY) {
            this.pathfinder = pathfinder;
            this.minY = minY;
        }

        @Override
        public boolean get0(final int x, final int y, final int z) {
            final int adjustedY = y - this.minY;
            if (adjustedY < 0 || adjustedY > 383) {
                return false;
            }
            final int chunkX = x >> 4;
            final int chunkZ = z >> 4;
            if (this.chunk == null | ((chunkX ^ this.prevChunkX) | (chunkZ ^ this.prevChunkZ)) != 0) {
                this.prevChunkX = chunkX;
                this.prevChunkZ = chunkZ;
                this.chunk = this.pathfinder.getChunkOrDefault(chunkX, chunkZ, true);
            }
            return this.chunk.isSolid(x & 0xF, adjustedY, z & 0xF);
        }
    }

    private record CachedChunk(int chunkX, int chunkZ, Chunk chunk) {}

    /** The fix as proposed in #5136: the pair as one record behind a volatile field. */
    public static final class RecordVolatile implements Get0 {
        private final Table pathfinder;
        private final int minY;
        private volatile CachedChunk cached;

        public RecordVolatile(Table pathfinder, int minY) {
            this.pathfinder = pathfinder;
            this.minY = minY;
        }

        @Override
        public boolean get0(final int x, final int y, final int z) {
            final int adjustedY = y - this.minY;
            if (adjustedY < 0 || adjustedY > 383) {
                return false;
            }
            final int chunkX = x >> 4;
            final int chunkZ = z >> 4;
            CachedChunk c = this.cached;
            if (c == null || c.chunkX != chunkX || c.chunkZ != chunkZ) {
                c = new CachedChunk(chunkX, chunkZ, this.pathfinder.getChunkOrDefault(chunkX, chunkZ, true));
                this.cached = c;
            }
            return c.chunk.isSolid(x & 0xF, adjustedY, z & 0xF);
        }
    }

    /** The same with a plain field. */
    public static final class RecordPlain implements Get0 {
        private final Table pathfinder;
        private final int minY;
        private CachedChunk cached;

        public RecordPlain(Table pathfinder, int minY) {
            this.pathfinder = pathfinder;
            this.minY = minY;
        }

        @Override
        public boolean get0(final int x, final int y, final int z) {
            final int adjustedY = y - this.minY;
            if (adjustedY < 0 || adjustedY > 383) {
                return false;
            }
            final int chunkX = x >> 4;
            final int chunkZ = z >> 4;
            CachedChunk c = this.cached;
            if (c == null || c.chunkX != chunkX || c.chunkZ != chunkZ) {
                c = new CachedChunk(chunkX, chunkZ, this.pathfinder.getChunkOrDefault(chunkX, chunkZ, true));
                this.cached = c;
            }
            return c.chunk.isSolid(x & 0xF, adjustedY, z & 0xF);
        }
    }

    /**
     * cabaletta/baritone#5156 (415acfcf) as it is, apart from the table type: the pair as a record
     * behind a plain field, read once into a local, compared with the original xor trick.
     */
    public static final class Pr5156Record implements Get0 {
        private final Table pathfinder;
        private final int minY;
        PrevChunk cached;

        public Pr5156Record(Table pathfinder, int minY) {
            this.pathfinder = pathfinder;
            this.minY = minY;
        }

        @Override
        public boolean get0(final int x, final int y, final int z) {
            final int adjustedY = y - this.minY;
            if (adjustedY < 0 || adjustedY > 383) {
                return false;
            }
            final int chunkX = x >> 4;
            final int chunkZ = z >> 4;
            PrevChunk cached = this.cached;
            final Chunk chunk;
            if (cached == null || ((chunkX ^ cached.x()) | (chunkZ ^ cached.z())) != 0) {
                chunk = this.pathfinder.getChunkOrDefault(chunkX, chunkZ, true);
                this.cached = new PrevChunk(chunk, chunkX, chunkZ);
            } else {
                chunk = cached.chunk();
            }
            return chunk.isSolid(x & 0xF, adjustedY, z & 0xF);
        }

        private static record PrevChunk(Chunk chunk, int x, int z) {}
    }

    /**
     * cabaletta/baritone#5155 (74dd28b2): the cache itself is Current, unchanged, one per reader,
     * but every one is created through getBoi, which puts it in a weak set the writer walks to
     * invalidate. getBoi is the part on the solve path; the walk runs on the write executor under
     * the write lock, when no solve is running, so it is not replayed.
     */
    public static final class Pr5155Registry {
        private final Table pathfinder;
        private final int minY;
        private final Set<Current> bois = Collections.newSetFromMap(new WeakHashMap<>());

        public Pr5155Registry(Table pathfinder, int minY) {
            this.pathfinder = pathfinder;
            this.minY = minY;
        }

        public Current getBoi() {
            Current boi = new Current(this.pathfinder, this.minY);
            this.bois.add(boi);
            return boi;
        }
    }

    /** A cache per thread: one ThreadLocal.get per lookup. */
    public static final class PerThread implements Get0 {
        private final ThreadLocal<Current> local;

        public PerThread(Table pathfinder, int minY) {
            this.local = ThreadLocal.withInitial(() -> new Current(pathfinder, minY));
        }

        @Override
        public boolean get0(final int x, final int y, final int z) {
            return this.local.get().get0(x, y, z);
        }
    }
}
