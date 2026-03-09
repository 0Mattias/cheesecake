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

import cheesecake.Cheesecake;
import cheesecake.api.pathing.goals.Goal;
import cheesecake.api.process.IElytraProcess;
import cheesecake.api.process.PathingCommand;
import cheesecake.api.utils.BetterBlockPos;
import cheesecake.utils.CheesecakeProcessHelper;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.math.BlockPos;

/**
 * @author Brady
 */
public final class NullElytraProcess extends CheesecakeProcessHelper implements IElytraProcess {

    public NullElytraProcess(Cheesecake cheesecake) {
        super(cheesecake);
    }

    @Override
    public void repackChunks() {
        throw new UnsupportedOperationException("Called repackChunks() on NullElytraBehavior");
    }

    @Override
    public BlockPos currentDestination() {
        return null;
    }

    @Override
    public List<BetterBlockPos> getPath() {
        return Collections.emptyList();
    }

    @Override
    public void pathTo(BlockPos destination) {
        throw new UnsupportedOperationException("Called pathTo() on NullElytraBehavior");
    }

    @Override
    public void pathTo(Goal destination) {
        throw new UnsupportedOperationException("Called pathTo() on NullElytraBehavior");
    }

    @Override
    public void resetState() {

    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        throw new UnsupportedOperationException("Called onTick on NullElytraProcess");
    }

    @Override
    public void onLostControl() {

    }

    @Override
    public String displayName0() {
        return "NullElytraProcess";
    }

    @Override
    public boolean isLoaded() {
        return false;
    }

    @Override
    public boolean isSafeToCancel() {
        return true;
    }
}
