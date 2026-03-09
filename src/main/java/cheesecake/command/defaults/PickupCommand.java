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
import cheesecake.api.command.datatypes.ItemById;
import cheesecake.api.command.exception.CommandException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class PickupCommand extends Command {

    public PickupCommand(ICheesecake cheesecake) {
        super(cheesecake, "pickup");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Set<Item> collecting = new HashSet<>();
        while (args.hasAny()) {
            Item item = args.getDatatypeFor(ItemById.INSTANCE);
            collecting.add(item);
        }
        if (collecting.isEmpty()) {
            cheesecake.getFollowProcess().pickup(stack -> true);
            logDirect("Picking up all items");
        } else {
            cheesecake.getFollowProcess().pickup(stack -> collecting.contains(stack.getItem()));
            logDirect("Picking up these items:");
            collecting.stream().map(Registries.ITEM::getId).map(Identifier::toString).forEach(this::logDirect);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        while (args.has(2)) {
            if (args.peekDatatypeOrNull(ItemById.INSTANCE) == null) {
                return Stream.empty();
            }
            args.get();
        }
        return args.tabCompleteDatatype(ItemById.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return "Pickup items";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Usage:",
                "> pickup - Pickup anything",
                "> pickup <item1> <item2> <...> - Pickup certain items"
        );
    }
}
