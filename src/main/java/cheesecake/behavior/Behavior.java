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

package cheesecake.behavior;

import cheesecake.Cheesecake;
import cheesecake.api.behavior.IBehavior;
import cheesecake.api.utils.IPlayerContext;

/**
 * A type of game event listener that is given {@link Cheesecake} instance context.
 *
 * @author Brady
 * @since 8/1/2018
 */
public class Behavior implements IBehavior {

    public final Cheesecake cheesecake;
    public final IPlayerContext ctx;

    protected Behavior(Cheesecake cheesecake) {
        this.cheesecake = cheesecake;
        this.ctx = cheesecake.getPlayerContext();
    }
}
