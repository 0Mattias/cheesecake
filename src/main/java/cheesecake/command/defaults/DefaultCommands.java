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

package cheesecake.command.defaults;

import cheesecake.api.ICheesecake;
import cheesecake.api.command.ICommand;

import java.util.*;

public final class DefaultCommands {

    private DefaultCommands() {
    }

    public static List<ICommand> createAll(ICheesecake cheesecake) {
        Objects.requireNonNull(cheesecake);
        List<ICommand> commands = new ArrayList<>(Arrays.asList(
                new HelpCommand(cheesecake),
                new SetCommand(cheesecake),
                new CommandAlias(cheesecake, Arrays.asList("modified", "mod", "cheesecake", "modifiedsettings"), "List modified settings", "set modified"),
                new CommandAlias(cheesecake, "reset", "Reset all settings or just one", "set reset"),
                new GoalCommand(cheesecake),
                new GotoCommand(cheesecake),
                new PathCommand(cheesecake),
                new ProcCommand(cheesecake),
                new ETACommand(cheesecake),
                new VersionCommand(cheesecake),
                new RepackCommand(cheesecake),
                new BuildCommand(cheesecake),
                //new SchematicaCommand(cheesecake),
                new LitematicaCommand(cheesecake),
                new ComeCommand(cheesecake),
                new AxisCommand(cheesecake),
                new ForceCancelCommand(cheesecake),
                new GcCommand(cheesecake),
                new InvertCommand(cheesecake),
                new TunnelCommand(cheesecake),
                new RenderCommand(cheesecake),
                new FarmCommand(cheesecake),
                new FollowCommand(cheesecake),
                new PickupCommand(cheesecake),
                new ExploreFilterCommand(cheesecake),
                new ReloadAllCommand(cheesecake),
                new SaveAllCommand(cheesecake),
                new ExploreCommand(cheesecake),
                new BlacklistCommand(cheesecake),
                new FindCommand(cheesecake),
                new MineCommand(cheesecake),
                new ClickCommand(cheesecake),
                new SurfaceCommand(cheesecake),
                new ThisWayCommand(cheesecake),
                new WaypointsCommand(cheesecake),
                new CommandAlias(cheesecake, "sethome", "Sets your home waypoint", "waypoints save home"),
                new CommandAlias(cheesecake, "home", "Path to your home waypoint", "waypoints goto home"),
                new SelCommand(cheesecake),
                new ElytraCommand(cheesecake)
        ));
        ExecutionControlCommands prc = new ExecutionControlCommands(cheesecake);
        commands.add(prc.pauseCommand);
        commands.add(prc.resumeCommand);
        commands.add(prc.pausedCommand);
        commands.add(prc.cancelCommand);
        return Collections.unmodifiableList(commands);
    }
}
