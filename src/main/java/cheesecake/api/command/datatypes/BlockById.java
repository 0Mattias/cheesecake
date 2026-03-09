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

package cheesecake.api.command.datatypes;

import cheesecake.api.command.exception.CommandException;
import cheesecake.api.command.helpers.TabCompleteHelper;
import java.util.stream.Stream;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public enum BlockById implements IDatatypeFor<Block> {
    INSTANCE;

    @Override
    public Block get(IDatatypeContext ctx) throws CommandException {
        Identifier id = Identifier.of(ctx.getConsumer().getString());
        Block block;
        if ((block = Registries.BLOCK.getOptionalValue(id).orElse(null)) == null) {
            throw new IllegalArgumentException("no block found by that id");
        }
        return block;
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        String arg = ctx.getConsumer().getString();

        return new TabCompleteHelper()
                .append(
                        Registries.BLOCK.getIds()
                                .stream()
                                .map(Object::toString))
                .filterPrefixNamespaced(arg)
                .sortAlphabetically()
                .stream();
    }
}
