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

package cheesecake.process.elytra.pathfinder;

import net.minecraft.core.BlockPos;

enum Face {
    UP, DOWN, NORTH, SOUTH, EAST, WEST;

    /** {@code pos} moved n blocks this way. */
    BlockPos offset(BlockPos pos, int n) {
        switch (this) {
            case UP: return pos.above(n);
            case DOWN: return pos.below(n);
            case NORTH: return pos.north(n);
            case SOUTH: return pos.south(n);
            case EAST: return pos.east(n);
            default: return pos.west(n);
        }
    }
}
