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

package cheesecake.utils;

import cheesecake.api.utils.input.Input;

public class PlayerMovementInput extends net.minecraft.client.input.Input {

    private final InputOverrideHandler handler;

    PlayerMovementInput(InputOverrideHandler handler) {
        this.handler = handler;
    }

    @Override
    public void tick() {
        boolean jump = handler.isInputForcedDown(Input.JUMP);
        boolean forward = handler.isInputForcedDown(Input.MOVE_FORWARD);
        boolean backward = handler.isInputForcedDown(Input.MOVE_BACK);
        boolean left = handler.isInputForcedDown(Input.MOVE_LEFT);
        boolean right = handler.isInputForcedDown(Input.MOVE_RIGHT);
        boolean sneak = handler.isInputForcedDown(Input.SNEAK);
        boolean sprint = handler.isInputForcedDown(Input.SPRINT);

        this.playerInput = new net.minecraft.util.PlayerInput(forward, backward, left, right, jump, sneak, sprint);

        float forwardImpulse = forward ? 1.0F : (backward ? -1.0F : 0.0F);
        float leftImpulse = left ? 1.0F : (right ? -1.0F : 0.0F);
        if (sneak) {
            forwardImpulse *= 0.3F;
            leftImpulse *= 0.3F;
        }
        this.movementVector = new net.minecraft.util.math.Vec2f(leftImpulse, forwardImpulse);
    }
}
