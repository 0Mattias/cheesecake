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
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.Collections;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
// import net.minecraft.util.math.Vec3d;
// import net.minecraft.world.RaycastContext;

import static cheesecake.api.command.ICheesecakeChatControl.FORCE_COMMAND_PREFIX;

@SuppressWarnings({"deprecation"})
public class GuiClick extends Screen implements Helper {

    private Matrix4f projectionViewMatrix;

    private BlockPos clickStart;
    private BlockPos currentMouseOver;

    public GuiClick() {
        super(Text.literal("CLICK"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float partialTicks) {
        double mx = mc.mouse.getX();
        double my = mc.mouse.getY();

        my = mc.getWindow().getHeight() - my;
        my *= mc.getWindow().getFramebufferHeight() / (double) mc.getWindow().getHeight();
        mx *= mc.getWindow().getFramebufferWidth() / (double) mc.getWindow().getWidth();
        Vec3d near = toWorld(mx, my, 0);
        Vec3d far = toWorld(mx, my, 1); // "Use 0.945 that's what stack overflow says" - leijurv

        if (near != null && far != null) {
            Vec3d viewerPos = new Vec3d(PathRenderer.posX(), PathRenderer.posY(), PathRenderer.posZ());
            ClientPlayerEntity player = CheesecakeAPI.getProvider().getPrimaryCheesecake().getPlayerContext().player();
            HitResult result = mc.world.raycast(new RaycastContext(near.add(viewerPos), far.add(viewerPos),
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
            if (result != null && result.getType() == HitResult.Type.BLOCK) {
                currentMouseOver = ((BlockHitResult) result).getBlockPos();
            }
        }
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        int mouseButton = click.button();
        if (currentMouseOver != null) { // Catch this, or else a click into void will result in a crash
            if (mouseButton == 0) {
                if (clickStart != null && !clickStart.equals(currentMouseOver)) {
                    // removed mouseDragged
                    CheesecakeAPI.getProvider().getPrimaryCheesecake().getSelectionManager().removeAllSelections();
                    CheesecakeAPI.getProvider().getPrimaryCheesecake().getSelectionManager()
                            .addSelection(BetterBlockPos.from(clickStart), BetterBlockPos.from(currentMouseOver));
                    MutableText component = Text
                            .literal("Selection made! For usage: " + Cheesecake.settings().prefix.value + "help sel");
                    component.setStyle(component.getStyle()
                            .withColor(Formatting.WHITE)
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
                        .setGoalAndPath(new GoalBlock(currentMouseOver.up()));
            }
        }
        clickStart = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean bl) {
        clickStart = currentMouseOver;
        return super.mouseClicked(click, bl);
    }

    public void onRender(MatrixStack modelViewStack, Matrix4f projectionMatrix) {
        this.projectionViewMatrix = new Matrix4f(projectionMatrix);
        this.projectionViewMatrix.mul(modelViewStack.peek().getPositionMatrix());
        this.projectionViewMatrix.invert();

        if (currentMouseOver != null) {
            Entity e = mc.getCameraEntity();
            // drawSingleSelectionBox WHEN?
            PathRenderer.drawManySelectionBoxes(modelViewStack, e, Collections.singletonList(currentMouseOver),
                    Color.CYAN);
            if (clickStart != null && !clickStart.equals(currentMouseOver)) {
                IRenderer.startLines(Color.RED, Cheesecake.settings().pathRenderLineWidthPixels.value, true);
                BetterBlockPos a = new BetterBlockPos(currentMouseOver);
                BetterBlockPos b = new BetterBlockPos(clickStart);
                IRenderer.emitAABB(modelViewStack, new Box(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z),
                        Math.max(a.x, b.x) + 1, Math.max(a.y, b.y) + 1, Math.max(a.z, b.z) + 1));
                IRenderer.endLines(true);
            }
        }
    }

    private Vec3d toWorld(double x, double y, double z) {
        if (this.projectionViewMatrix == null) {
            return null;
        }

        x /= mc.getWindow().getFramebufferWidth();
        y /= mc.getWindow().getFramebufferHeight();
        x = x * 2 - 1;
        y = y * 2 - 1;

        Vector4f pos = new Vector4f((float) x, (float) y, (float) z, 1.0F);
        projectionViewMatrix.transform(pos);

        if (pos.w() == 0) {
            return null;
        }

        pos.mul(1 / pos.w());
        return new Vec3d(pos.x(), pos.y(), pos.z());
    }
}
