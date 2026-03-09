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
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Brady
 * @since 2/13/2020
 */
@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    @Inject(method = "render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V", at = @At("RETURN"))
    private void onStartHand(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
            Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f matrix4f3,
            GpuBufferSlice gpuBufferSlice, Vector4f vector4f, boolean bl, CallbackInfo ci) {
        MatrixStack matrixStackIn = new MatrixStack();
        matrixStackIn.multiplyPositionMatrix(positionMatrix);
        float partialTicks = tickCounter.getTickProgress(true);

        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            icheesecake.getGameEventHandler()
                    .onRenderPass(new RenderEvent(partialTicks, matrixStackIn, projectionMatrix));
        }
    }
}