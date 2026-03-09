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

package cheesecake.utils;

import cheesecake.Cheesecake;
import cheesecake.api.process.ICheesecakeProcess;
import cheesecake.api.utils.Helper;
import cheesecake.api.utils.IPlayerContext;

public abstract class CheesecakeProcessHelper implements ICheesecakeProcess, Helper {

    protected final Cheesecake cheesecake;
    protected final IPlayerContext ctx;

    public CheesecakeProcessHelper(Cheesecake cheesecake) {
        this.cheesecake = cheesecake;
        this.ctx = cheesecake.getPlayerContext();
    }

    @Override
    public boolean isTemporary() {
        return false;
    }
}
