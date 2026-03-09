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

package cheesecake.utils.player;

import cheesecake.api.utils.IPlayerController;
import cheesecake.utils.accessor.IPlayerControllerMP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;


/**
 * Implementation of {@link IPlayerController} that chains to the primary player controller's methods
 *
 * @author Brady
 * @since 12/14/2018
 */
public final class CheesecakePlayerController implements IPlayerController {

    private final MinecraftClient mc;

    public CheesecakePlayerController(MinecraftClient mc) {
        this.mc = mc;
    }

    @Override
    public void syncHeldItem() {
        ((IPlayerControllerMP) mc.interactionManager).callSyncCurrentPlayItem();
    }

    @Override
    public boolean hasBrokenBlock() {
        return !((IPlayerControllerMP) mc.interactionManager).isHittingBlock();
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        return mc.interactionManager.updateBlockBreakingProgress(pos, side);
    }

    @Override
    public void resetBlockRemoving() {
        mc.interactionManager.cancelBlockBreaking();
    }

    @Override
    public void windowClick(int windowId, int slotId, int mouseButton, SlotActionType type, PlayerEntity player) {
        mc.interactionManager.clickSlot(windowId, slotId, mouseButton, type, player);
    }

    @Override
    public GameMode getGameType() {
        return mc.interactionManager.getCurrentGameMode();
    }

    @Override
    public ActionResult processRightClickBlock(ClientPlayerEntity player, World world, Hand hand, BlockHitResult result) {
        // primaryplayercontroller is always in a ClientWorld so this is ok
        return mc.interactionManager.interactBlock(player, hand, result);
    }

    @Override
    public ActionResult processRightClick(ClientPlayerEntity player, World world, Hand hand) {
        return mc.interactionManager.interactItem(player, hand);
    }

    @Override
    public boolean clickBlock(BlockPos loc, Direction face) {
        return mc.interactionManager.attackBlock(loc, face);
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
        ((IPlayerControllerMP) mc.interactionManager).setIsHittingBlock(hittingBlock);
    }
}
