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

import cheesecake.Cheesecake;
import cheesecake.api.CheesecakeAPI;
import cheesecake.api.pathing.goals.GoalBlock;
import cheesecake.api.utils.BetterBlockPos;
import cheesecake.api.utils.Helper;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.Collections;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static cheesecake.api.command.ICheesecakeChatControl.FORCE_COMMAND_PREFIX;

@SuppressWarnings({"deprecation"})
public class GuiClick extends Screen implements Helper {

    private Matrix4f projectionViewMatrix;

    private BlockPos clickStart;
    private BlockPos currentMouseOver;

    public GuiClick() {
        super(Component.literal("CLICK"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        double mx = mc.mouseHandler.xpos();
        double my = mc.mouseHandler.ypos();

        my = mc.getWindow().getScreenHeight() - my;
        my *= mc.getWindow().getHeight() / (double) mc.getWindow().getScreenHeight();
        mx *= mc.getWindow().getWidth() / (double) mc.getWindow().getScreenWidth();
        Vec3 near = toWorld(mx, my, 0);
        Vec3 far = toWorld(mx, my, 1); // "Use 0.945 that's what stack overflow says" - leijurv

        if (near != null && far != null) {
            Vec3 viewerPos = new Vec3(PathRenderer.posX(), PathRenderer.posY(), PathRenderer.posZ());
            LocalPlayer player = CheesecakeAPI.getProvider().getPrimaryCheesecake().getPlayerContext().player();
            HitResult result = mc.level.clip(new ClipContext(near.add(viewerPos), far.add(viewerPos),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (result != null && result.getType() == HitResult.Type.BLOCK) {
                currentMouseOver = ((BlockHitResult) result).getBlockPos();
            }
        }
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY,
            float partialTicks) {
        // Deliberately empty: vanilla would blur and darken the world behind the screen, which makes it
        // impossible to see the blocks you are trying to click.
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        int mouseButton = click.button();
        if (currentMouseOver != null) { // Catch this, or else a click into void will result in a crash
            if (mouseButton == 0) {
                if (clickStart != null && !clickStart.equals(currentMouseOver)) {
                    // removed mouseDragged
                    CheesecakeAPI.getProvider().getPrimaryCheesecake().getSelectionManager().removeAllSelections();
                    CheesecakeAPI.getProvider().getPrimaryCheesecake().getSelectionManager()
                            .addSelection(BetterBlockPos.from(clickStart), BetterBlockPos.from(currentMouseOver));
                    MutableComponent component = Component
                            .literal("Selection made! For usage: " + Cheesecake.settings().prefix.value + "help sel");
                    component.setStyle(component.getStyle()
                            .withColor(ChatFormatting.WHITE)
                            .withClickEvent(new ClickEvent.RunCommand(
                                    FORCE_COMMAND_PREFIX + "help sel")));
                    Helper.HELPER.logDirect(component);
                    clickStart = null;
                } else {
                    CheesecakeAPI.getProvider().getPrimaryCheesecake().getCustomGoalProcess()
                            .setGoalAndPath(new GoalBlock(currentMouseOver));
                }
            } else if (mouseButton == 1) {
                CheesecakeAPI.getProvider().getPrimaryCheesecake().getCustomGoalProcess()
                        .setGoalAndPath(new GoalBlock(currentMouseOver.above()));
            }
        }
        clickStart = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean bl) {
        clickStart = currentMouseOver;
        return super.mouseClicked(click, bl);
    }

    public void onRender(PoseStack modelViewStack, Matrix4f projectionMatrix) {
        this.projectionViewMatrix = new Matrix4f(projectionMatrix);
        this.projectionViewMatrix.mul(modelViewStack.last().pose());
        this.projectionViewMatrix.invert();

        if (currentMouseOver != null) {
            Entity e = mc.getCameraEntity();
            // drawSingleSelectionBox WHEN?
            PathRenderer.drawManySelectionBoxes(modelViewStack, e, Collections.singletonList(currentMouseOver),
                    Color.CYAN);
            if (clickStart != null && !clickStart.equals(currentMouseOver)) {
                BufferBuilder bufferBuilder = IRenderer.startLines(Color.RED);
                BetterBlockPos a = new BetterBlockPos(currentMouseOver);
                BetterBlockPos b = new BetterBlockPos(clickStart);
                IRenderer.emitAABB(bufferBuilder, modelViewStack,
                        new AABB(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z),
                                Math.max(a.x, b.x) + 1, Math.max(a.y, b.y) + 1, Math.max(a.z, b.z) + 1),
                        Cheesecake.settings().pathRenderLineWidthPixels.value);
                IRenderer.endLines(bufferBuilder, true);
            }
        }
    }

    private Vec3 toWorld(double x, double y, double z) {
        if (this.projectionViewMatrix == null) {
            return null;
        }

        x /= mc.getWindow().getWidth();
        y /= mc.getWindow().getHeight();
        x = x * 2 - 1;
        y = y * 2 - 1;

        Vector4f pos = new Vector4f((float) x, (float) y, (float) z, 1.0F);
        projectionViewMatrix.transform(pos);

        if (pos.w() == 0) {
            return null;
        }

        pos.mul(1 / pos.w());
        return new Vec3(pos.x(), pos.y(), pos.z());
    }
}
