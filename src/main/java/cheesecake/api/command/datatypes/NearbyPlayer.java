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

import cheesecake.api.ICheesecake;
import cheesecake.api.command.exception.CommandException;
import cheesecake.api.command.helpers.TabCompleteHelper;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * An {@link IDatatype} used to resolve nearby players, those within
 * render distance of the target {@link ICheesecake} instance.
 */
public enum NearbyPlayer implements IDatatypeFor<PlayerEntity> {
    INSTANCE;

    @Override
    public PlayerEntity get(IDatatypeContext ctx) throws CommandException {
        final String username = ctx.getConsumer().getString();
        return getPlayers(ctx).stream()
                .filter(s -> s.getName().getString().equalsIgnoreCase(username))
                .findFirst().orElse(null);
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        return new TabCompleteHelper()
                .append(getPlayers(ctx).stream().map(PlayerEntity::getName).map(Text::getString))
                .filterPrefix(ctx.getConsumer().getString())
                .sortAlphabetically()
                .stream();
    }

    private static List<? extends PlayerEntity> getPlayers(IDatatypeContext ctx) {
        return ctx.getCheesecake().getPlayerContext().world().getPlayers();
    }
}
