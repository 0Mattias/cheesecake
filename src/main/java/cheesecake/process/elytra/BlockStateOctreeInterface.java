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
import cheesecake.process.elytra.pathfinder.NetherPathfinder;

/**
 * @author Brady
 */
public final class BlockStateOctreeInterface {

    private final NetherPathfinderContext context;
    private final NetherPathfinder pathfinder;
    private final int minY;
    /**
     * The chunk the last lookup fell in, so that a run of lookups inside one chunk costs one
     * table lookup. Every solver thread under the read lock shares this object, and the read lock
     * admits them all at once -- there are two of them for a moment whenever a flight is re-planned,
     * the old behavior's solver finishing while the new one's starts -- so the pair is one immutable
     * value swapped through a single reference. A reader sees a whole (position, chunk) pair or
     * nothing, never one thread's coordinates against another's chunk, which three separate
     * fields allowed and which answered a query out of the wrong chunk. The writer clears it under
     * the write lock whenever chunks are replaced or culled, so that a lookup does not go on
     * answering out of a chunk the table no longer holds.
     */
    private volatile CachedChunk cached;

    private record CachedChunk(int chunkX, int chunkZ, Chunk chunk) {}

    public BlockStateOctreeInterface(final NetherPathfinderContext context) {
        this.context = context;
        this.pathfinder = context.context;
        this.minY = context.minY;
    }

    /** Forgets the cached chunk. Called under the write lock by whatever replaces or culls chunks. */
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
            c = new CachedChunk(chunkX, chunkZ, this.pathfinder.getChunkOrDefault(chunkX, chunkZ, true));
            this.cached = c;
        }
        return c.chunk.isSolid(x & 0xF, adjustedY, z & 0xF);
    }
}
