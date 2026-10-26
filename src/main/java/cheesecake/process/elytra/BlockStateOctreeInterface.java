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
     * The chunk the last lookup fell in, so that a run of lookups inside one chunk costs one table
     * lookup. Three plain fields, as upstream had them, which is safe because this object is no
     * longer shared: cabaletta/baritone#5155 (ZacSharp, 74dd28b2), ported as it is, gives every
     * reader its own through {@link NetherPathfinderContext#getBoi()}, and the writer clears all of
     * them under the write lock.
     */
    Chunk chunk;

    // Guarantee that the first lookup will fetch the context by setting MAX_VALUE
    private int prevChunkX = Integer.MAX_VALUE;
    private int prevChunkZ = Integer.MAX_VALUE;

    public BlockStateOctreeInterface(final NetherPathfinderContext context) {
        this.context = context;
        this.pathfinder = context.context;
        this.minY = context.minY;
    }

    /** Forgets the cached chunk. Called under the write lock by whatever replaces or culls chunks. */
    void invalidate() {
        this.chunk = null;
    }

    /**
     * The caller must hold the context read lock and have exclusive access
     * to this {@code BlockStateOctreeInterface}.
     */
    public boolean get0(final int x, final int y, final int z) {
        if (LookupTrace.LOOKUPS) {
            LookupTrace.lookup(x, y, z);
        }
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
