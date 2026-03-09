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

package cheesecake.utils.pathing;

import cheesecake.api.pathing.movement.ActionCosts;

/**
 * The result of a calculated movement, with destination x, y, z, and the cost of performing the movement
 *
 * @author leijurv
 */
public final class MutableMoveResult {

    public int x;
    public int y;
    public int z;
    public double cost;

    public MutableMoveResult() {
        reset();
    }

    public final void reset() {
        x = 0;
        y = 0;
        z = 0;
        cost = ActionCosts.COST_INF;
    }
}
