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

import cheesecake.api.command.argument.IArgConsumer;
import cheesecake.api.command.exception.CommandException;
import cheesecake.api.pathing.goals.GoalXZ;
import cheesecake.api.utils.BetterBlockPos;
import java.util.stream.Stream;
import net.minecraft.util.math.MathHelper;

public enum RelativeGoalXZ implements IDatatypePost<GoalXZ, BetterBlockPos> {
    INSTANCE;

    @Override
    public GoalXZ apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
        if (origin == null) {
            origin = BetterBlockPos.ORIGIN;
        }

        final IArgConsumer consumer = ctx.getConsumer();
        return new GoalXZ(
                MathHelper.floor(consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.x)),
                MathHelper.floor(consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.z))
        );
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) {
        final IArgConsumer consumer = ctx.getConsumer();
        if (consumer.hasAtMost(2)) {
            return consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE);
        }
        return Stream.empty();
    }
}
