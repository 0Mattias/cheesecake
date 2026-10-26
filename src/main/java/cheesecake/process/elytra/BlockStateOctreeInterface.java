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
     * record swapped through a single reference. A reader sees a whole (chunk, position) pair or
     * nothing, never one thread's coordinates against another's chunk, which three separate
     * fields allowed and which answered a query out of the wrong chunk. The field is plain, not
     * volatile: the record's fields are final, so whoever sees the reference sees the whole pair,
     * and the writer clears it under the write lock whenever chunks are replaced or culled, which
     * every later read-lock hold is ordered after. This is cabaletta/baritone#5156 (ZacSharp,
     * 415acfcf) as it is.
     */
    PrevChunk cached;

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
