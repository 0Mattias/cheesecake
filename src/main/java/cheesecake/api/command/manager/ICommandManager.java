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

package cheesecake.api.command.manager;

import cheesecake.api.ICheesecake;
import cheesecake.api.command.ICommand;
import cheesecake.api.command.argument.ICommandArgument;
import cheesecake.api.command.registry.Registry;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.util.Pair;

/**
 * @author Brady
 * @since 9/21/2019
 */
public interface ICommandManager {

    ICheesecake getCheesecake();

    Registry<ICommand> getRegistry();

    /**
     * @param name The command name to search for.
     * @return The command, if found.
     */
    ICommand getCommand(String name);

    boolean execute(String string);

    boolean execute(Pair<String, List<ICommandArgument>> expanded);

    Stream<String> tabComplete(Pair<String, List<ICommandArgument>> expanded);

    Stream<String> tabComplete(String prefix);
}
