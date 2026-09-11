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

import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;

/**
 * @author Brady
 */
public final class BlockStateOctreeInterface {

    private final NetherPathfinderContext context;
    private final long contextPtr;
    private final int minY;
    /**
     * The chunk the last lookup fell in, so that a run of lookups inside one chunk costs one
     * native call. Every solver thread under the read lock shares this object, and the read lock
     * admits them all at once -- there are two of them for a moment whenever a flight is re-planned,
     * the old behavior's solver finishing while the new one's starts -- so the pair is one immutable
     * value swapped through a single reference. A reader sees a whole (chunk, pointer) pair or
     * nothing, never one thread's coordinates against another's pointer, which three separate
     * fields allowed and which answered a query out of the wrong chunk. The writer clears it under
     * the write lock whenever chunks are replaced or freed, since the pointer may then be to memory
     * that has been handed back.
     */
    private volatile CachedChunk cached;

    private record CachedChunk(int chunkX, int chunkZ, long ptr) {}

    public BlockStateOctreeInterface(final NetherPathfinderContext context) {
        this.context = context;
        this.contextPtr = context.context;
        this.minY = context.minY;
    }

    /** Forgets the cached chunk. Called under the write lock by whatever replaces or frees chunks. */
    void invalidate() {
        this.cached = null;
    }

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
        return Octree.getBlock(c.ptr, x & 0xF, adjustedY, z & 0xF);
    }
}
