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

package cheesecake.launch.mixins;

import cheesecake.api.CheesecakeAPI;
import cheesecake.api.ICheesecake;
import cheesecake.api.event.events.RenderEvent;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinWorldRenderer {

    @Inject(method = "render", at = @At("RETURN"))
    private void onStartHand(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean outline,
            CameraRenderState camera, Matrix4fc modelViewMatrix, GpuBufferSlice fog, Vector4f fogColor, boolean sky,
            CallbackInfo ci) {
        PoseStack matrixStackIn = new PoseStack();
        matrixStackIn.mulPose(modelViewMatrix);
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            icheesecake.getGameEventHandler()
                    .onRenderPass(new RenderEvent(partialTicks, matrixStackIn, camera.projectionMatrix));
        }
    }
}
