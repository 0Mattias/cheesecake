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
import cheesecake.api.cache.IWaypoint;
import cheesecake.api.cache.Waypoint;
import cheesecake.api.event.events.BlockInteractEvent;
import cheesecake.api.utils.BetterBlockPos;
import cheesecake.api.utils.Helper;
import cheesecake.utils.BlockStateInterface;
import java.util.Set;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

// import static cheesecake.api.command.ICheesecakeChatControl.FORCE_COMMAND_PREFIX;

public class WaypointBehavior extends Behavior {

    public WaypointBehavior(Cheesecake cheesecake) {
        super(cheesecake);
    }

    @Override
    public void onBlockInteract(BlockInteractEvent event) {
        if (!Cheesecake.settings().doBedWaypoints.value)
            return;
        if (event.getType() == BlockInteractEvent.Type.USE) {
            BetterBlockPos pos = BetterBlockPos.from(event.getPos());
            BlockState state = BlockStateInterface.get(ctx, pos);
            if (state.getBlock() instanceof BedBlock) {
                if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
                    pos = pos.relative(state.getValue(BedBlock.FACING));
                }
                Set<IWaypoint> waypoints = cheesecake.getWorldProvider().getCurrentWorld().getWaypoints()
                        .getByTag(IWaypoint.Tag.BED);
                boolean exists = waypoints.stream().map(IWaypoint::getLocation).filter(pos::equals).findFirst()
                        .isPresent();
                if (!exists) {
                    cheesecake.getWorldProvider().getCurrentWorld().getWaypoints()
                            .addWaypoint(new Waypoint("bed", Waypoint.Tag.BED, pos));
                }
            }
        }
    }

    @Override
    public void onPlayerDeath() {
        if (!Cheesecake.settings().doDeathWaypoints.value)
            return;
        Waypoint deathWaypoint = new Waypoint("death", Waypoint.Tag.DEATH, ctx.playerFeet());
        cheesecake.getWorldProvider().getCurrentWorld().getWaypoints().addWaypoint(deathWaypoint);
        net.minecraft.network.chat.MutableComponent component = net.minecraft.network.chat.Component.literal("Death waypoint saved");
        component.setStyle(component.getStyle()
                .withColor(net.minecraft.ChatFormatting.WHITE));
        Helper.HELPER.logDirect(component);
    }

}
