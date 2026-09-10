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

import cheesecake.agent.AgentStatus;
import cheesecake.api.ICheesecake;
import cheesecake.api.command.Command;
import cheesecake.api.command.argument.IArgConsumer;
import cheesecake.api.command.exception.CommandException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class StatusCommand extends Command {

    public StatusCommand(ICheesecake cheesecake) {
        super(cheesecake, "status");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        logDirect(AgentStatus.snapshot(cheesecake).toString());
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Print a JSON summary of the bot's state";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The status command prints one line of JSON describing the player, the process in control,",
                "the current path and the elytra state. It is meant for programs that read the chat.",
                "",
                "The same snapshot is available over the local control socket (see agentApiPort).",
                "",
                "Usage:",
                "> status"
        );
    }
}
