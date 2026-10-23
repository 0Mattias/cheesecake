package measure;

import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;

/**
 * BlockStateOctreeInterface.get0 on upstream 26.3 (nether-pathfinder 1.6, native), and the same
 * ways of fixing it. The chunk is a pointer into the library's memory.
 */
public final class NativeCaches {

    /** As on 26.3 today. Also what a cache per solver is, one instance per solve. */
    public static final class Current implements JavaCaches.Get0 {
        private final long contextPtr;
        private final int minY;
        transient long chunkPtr;
        private int prevChunkX = Integer.MAX_VALUE;
        private int prevChunkZ = Integer.MAX_VALUE;

        public Current(long contextPtr, int minY) {
            this.contextPtr = contextPtr;
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
            if (this.chunkPtr == 0 | ((chunkX ^ this.prevChunkX) | (chunkZ ^ this.prevChunkZ)) != 0) {
                this.prevChunkX = chunkX;
                this.prevChunkZ = chunkZ;
                this.chunkPtr = NetherPathfinder.getChunkOrDefault(this.contextPtr, chunkX, chunkZ, true);
            }
            return Octree.getBlock(this.chunkPtr, x & 0xF, adjustedY, z & 0xF);
        }
    }

    private record CachedChunk(int chunkX, int chunkZ, long chunkPtr) {}

    public static final class RecordVolatile implements JavaCaches.Get0 {
        private final long contextPtr;
        private final int minY;
        private volatile CachedChunk cached;

        public RecordVolatile(long contextPtr, int minY) {
            this.contextPtr = contextPtr;
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
                c = new CachedChunk(chunkX, chunkZ, NetherPathfinder.getChunkOrDefault(this.contextPtr, chunkX, chunkZ, true));
                this.cached = c;
            }
            return Octree.getBlock(c.chunkPtr, x & 0xF, adjustedY, z & 0xF);
        }
    }

    public static final class RecordPlain implements JavaCaches.Get0 {
        private final long contextPtr;
        private final int minY;
        private CachedChunk cached;

        public RecordPlain(long contextPtr, int minY) {
            this.contextPtr = contextPtr;
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
                c = new CachedChunk(chunkX, chunkZ, NetherPathfinder.getChunkOrDefault(this.contextPtr, chunkX, chunkZ, true));
                this.cached = c;
            }
            return Octree.getBlock(c.chunkPtr, x & 0xF, adjustedY, z & 0xF);
        }
    }

    public static final class PerThread implements JavaCaches.Get0 {
        private final ThreadLocal<Current> local;

        public PerThread(long contextPtr, int minY) {
            this.local = ThreadLocal.withInitial(() -> new Current(contextPtr, minY));
        }

        @Override
        public boolean get0(final int x, final int y, final int z) {
            return this.local.get().get0(x, y, z);
        }
    }
}
