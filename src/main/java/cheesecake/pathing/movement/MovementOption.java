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

package cheesecake.pathing.movement;

import cheesecake.api.utils.input.Input;
import java.util.stream.Stream;
import net.minecraft.util.math.MathHelper;

public record MovementOption(Input input1, Input input2, float motionX, float motionZ) {
    private static final float SPRINT_MULTIPLIER = 1.3f;

    public MovementOption(Input input1, float motionX, float motionZ) {
        this(input1, null, motionX, motionZ);
    }

    public void setInputs(MovementState movementState) {
        if (input1 != null) {
            movementState.setInput(input1, true);
        }
        if (input2 != null) {
            movementState.setInput(input2, true);
        }
    }

    public float distanceToSq(float otherX, float otherZ) {
        return MathHelper.abs(motionX() - otherX) + MathHelper.abs(motionZ() - otherZ);
    }
    
    public static Stream<MovementOption> getOptions(float motionX, float motionZ, boolean canSprint) {
        return Stream.of(
                new MovementOption(Input.MOVE_FORWARD, canSprint ? motionX * SPRINT_MULTIPLIER : motionX, canSprint ? motionZ * SPRINT_MULTIPLIER : motionZ),
                new MovementOption(Input.MOVE_BACK, -motionX, -motionZ),
                new MovementOption(Input.MOVE_LEFT, -motionZ, motionX),
                new MovementOption(Input.MOVE_RIGHT, motionZ, -motionX),
                new MovementOption(Input.MOVE_FORWARD, Input.MOVE_LEFT, (canSprint ? motionX * SPRINT_MULTIPLIER : motionX) - motionZ, (canSprint ? motionZ * SPRINT_MULTIPLIER : motionZ) + motionX),
                new MovementOption(Input.MOVE_FORWARD, Input.MOVE_RIGHT, (canSprint ? motionX * SPRINT_MULTIPLIER : motionX) + motionZ, (canSprint ? motionZ * SPRINT_MULTIPLIER : motionZ) - motionX),
                new MovementOption(Input.MOVE_BACK, Input.MOVE_LEFT, -motionX - motionZ, -motionZ + motionX),
                new MovementOption(Input.MOVE_BACK, Input.MOVE_RIGHT, -motionX + motionZ, -motionZ - motionX)
        );
    }
}
