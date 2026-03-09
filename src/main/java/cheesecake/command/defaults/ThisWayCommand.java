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
import cheesecake.api.command.Command;
import cheesecake.api.command.argument.IArgConsumer;
import cheesecake.api.command.exception.CommandException;
import cheesecake.api.pathing.goals.GoalXZ;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ThisWayCommand extends Command {

    public ThisWayCommand(ICheesecake cheesecake) {
        super(cheesecake, "thisway", "forward");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireExactly(1);
        GoalXZ goal = GoalXZ.fromDirection(
                ctx.playerFeetAsVec(),
                ctx.player().getHeadYaw(),
                args.getAs(Double.class)
        );
        cheesecake.getCustomGoalProcess().setGoal(goal);
        logDirect(String.format("Goal: %s", goal));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Travel in your current direction";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Creates a GoalXZ some amount of blocks in the direction you're currently looking",
                "",
                "Usage:",
                "> thisway <distance> - makes a GoalXZ distance blocks in front of you"
        );
    }
}
