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

import cheesecake.api.CheesecakeAPI;
import cheesecake.api.Settings;
import cheesecake.utils.accessor.IEntityRenderManager;
import cheesecake.utils.accessor.IRenderLayer;
import cheesecake.utils.accessor.IRenderPipelines;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.function.BiFunction;

public interface IRenderer {

    Tesselator tessellator = Tesselator.getInstance();

    IEntityRenderManager renderManager = (IEntityRenderManager) Minecraft.getInstance()
            .getEntityRenderDispatcher();
    Settings settings = CheesecakeAPI.getSettings();

    /**
     * Vanilla's lines snippet with alpha blending enabled and depth writes/backface culling disabled,
     * so overlapping path lines blend instead of fighting each other.
     */
    RenderPipeline.Snippet CHEESECAKE_LINES_SNIPPET = RenderPipeline
            .builder(((IRenderPipelines) new RenderPipelines()).cheesecake$getLinesSnippet())
            .withBlend(new BlendFunction(
                    SourceFactor.SRC_ALPHA,
                    DestFactor.ONE_MINUS_SRC_ALPHA,
                    SourceFactor.ONE,
                    DestFactor.ZERO))
            .withDepthWrite(false)
            .withCull(false)
            .buildSnippet();

    /**
     * Vanilla's beacon beam pipeline, rebuilt from the same snippet, shaders and vertex format the beacon
     * block entity uses, so the two pipelines below can drop the depth test the way the line layers do.
     */
    RenderPipeline.Snippet CHEESECAKE_BEACON_BEAM_SNIPPET = RenderPipeline
            .builder(((IRenderPipelines) new RenderPipelines()).cheesecake$getTransformsProjectionFogSnippet())
            .withVertexShader("core/rendertype_beacon_beam")
            .withFragmentShader("core/rendertype_beacon_beam")
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS)
            .buildSnippet();

    RenderPipeline BEACON_BEAM_OPAQUE = ((IRenderPipelines) new RenderPipelines()).cheesecake$registerPipeline(
            RenderPipeline.builder(CHEESECAKE_BEACON_BEAM_SNIPPET)
                    .withLocation("pipeline/cheesecake_beacon_beam_opaque")
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(true)
                    .build());

    RenderPipeline BEACON_BEAM_TRANSLUCENT = ((IRenderPipelines) new RenderPipelines()).cheesecake$registerPipeline(
            RenderPipeline.builder(CHEESECAKE_BEACON_BEAM_SNIPPET)
                    .withLocation("pipeline/cheesecake_beacon_beam_translucent")
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(true)
                    .build());

    /**
     * The two layers only differ in their depth test function. Prior to 1.21.5 the "ignore depth" settings
     * were implemented by toggling GL_DEPTH_TEST around the draw call, but the render pipeline owns that
     * state now, so the toggle silently did nothing (and left vanilla's state tracker out of sync).
     */
    RenderType linesWithDepthRenderLayer = ((IRenderLayer) RenderTypes.LINES).cheesecake$createRenderLayer(
            "renderLayer/cheesecake_lines_with_depth",
            RenderSetup.builder(RenderPipeline.builder(CHEESECAKE_LINES_SNIPPET)
                    .withLocation("pipeline/cheesecake_lines_with_depth")
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .build())
                    .bufferSize(256)
                    .createRenderSetup());

    RenderType linesNoDepthRenderLayer = ((IRenderLayer) RenderTypes.LINES).cheesecake$createRenderLayer(
            "renderLayer/cheesecake_lines_no_depth",
            RenderSetup.builder(RenderPipeline.builder(CHEESECAKE_LINES_SNIPPET)
                    .withLocation("pipeline/cheesecake_lines_no_depth")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build())
                    .bufferSize(256)
                    .createRenderSetup());

    /**
     * Beacon beam layers that ignore depth, keyed by texture and translucency like vanilla's
     * {@link RenderTypes#beaconBeam(Identifier, boolean)}.
     */
    BiFunction<Identifier, Boolean, RenderType> BEACON_BEAM = Util.memoize(
            (texture, translucent) -> ((IRenderLayer) RenderTypes.LINES).cheesecake$createRenderLayer(
                    translucent ? "renderLayer/cheesecake_beacon_beam_translucent"
                            : "renderLayer/cheesecake_beacon_beam_opaque",
                    RenderSetup.builder(translucent ? BEACON_BEAM_TRANSLUCENT : BEACON_BEAM_OPAQUE)
                            .withTexture("Sampler0", texture)
                            .sortOnUpload()
                            .createRenderSetup()));

    float[] color = new float[] { 1.0F, 1.0F, 1.0F, 255.0F };

    static void glColor(Color color, float alpha) {
        float[] colorComponents = color.getColorComponents(null);
        IRenderer.color[0] = colorComponents[0];
        IRenderer.color[1] = colorComponents[1];
        IRenderer.color[2] = colorComponents[2];
        IRenderer.color[3] = alpha;
    }

    static BufferBuilder startLines(Color color, float alpha) {
        glColor(color, alpha);
        return tessellator.begin(VertexFormat.Mode.LINES, RenderTypes.LINES.format());
    }

    static BufferBuilder startLines(Color color) {
        return startLines(color, .4f);
    }

    static void endLines(BufferBuilder bufferBuilder, boolean ignoreDepth) {
        MeshData builtBuffer = bufferBuilder.build();
        if (builtBuffer != null) {
            (ignoreDepth ? linesNoDepthRenderLayer : linesWithDepthRenderLayer).draw(builtBuffer);
        }
    }

    static BufferBuilder startBlockQuads() {
        return tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
    }

    static void endBuffer(BufferBuilder bufferBuilder, RenderType renderLayer) {
        MeshData builtBuffer = bufferBuilder.build();
        if (builtBuffer != null) {
            renderLayer.draw(builtBuffer);
        }
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float lineWidth) {
        final double dx = x2 - x1;
        final double dy = y2 - y1;
        final double dz = z2 - z1;

        final double invMag = 1.0 / Math.sqrt(dx * dx + dy * dy + dz * dz);
        final float nx = (float) (dx * invMag);
        final float ny = (float) (dy * invMag);
        final float nz = (float) (dz * invMag);

        emitLine(bufferBuilder, stack, x1, y1, z1, x2, y2, z2, nx, ny, nz, lineWidth);
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double nx, double ny, double nz,
            float lineWidth) {
        emitLine(bufferBuilder, stack,
                (float) x1, (float) y1, (float) z1,
                (float) x2, (float) y2, (float) z2,
                (float) nx, (float) ny, (float) nz,
                lineWidth);
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float nx, float ny, float nz,
            float lineWidth) {

        final PoseStack.Pose entry = stack.last();
        final Matrix4f matrix4f = entry.pose();

        bufferBuilder
                .addVertex(matrix4f, x1, y1, z1)
                .setColor(color[0], color[1], color[2], color[3])
                .setNormal(entry, nx, ny, nz)
                .setLineWidth(lineWidth);
        bufferBuilder
                .addVertex(matrix4f, x2, y2, z2)
                .setColor(color[0], color[1], color[2], color[3])
                .setNormal(entry, nx, ny, nz)
                .setLineWidth(lineWidth);
    }

    static void emitAABB(BufferBuilder bufferBuilder, PoseStack stack, AABB aabb, float lineWidth) {
        AABB toDraw = aabb.move(-renderManager.renderPosX(), -renderManager.renderPosY(), -renderManager.renderPosZ());

        // bottom
        emitLine(bufferBuilder, stack, toDraw.minX, toDraw.minY, toDraw.minZ, toDraw.maxX, toDraw.minY, toDraw.minZ, 1.0, 0.0, 0.0, lineWidth);
        emitLine(bufferBuilder, stack, toDraw.maxX, toDraw.minY, toDraw.minZ, toDraw.maxX, toDraw.minY, toDraw.maxZ, 0.0, 0.0, 1.0, lineWidth);
        emitLine(bufferBuilder, stack, toDraw.maxX, toDraw.minY, toDraw.maxZ, toDraw.minX, toDraw.minY, toDraw.maxZ, -1.0, 0.0, 0.0, lineWidth);
        emitLine(bufferBuilder, stack, toDraw.minX, toDraw.minY, toDraw.maxZ, toDraw.minX, toDraw.minY, toDraw.minZ, 0.0, 0.0, -1.0, lineWidth);
        // top
        emitLine(bufferBuilder, stack, toDraw.minX, toDraw.maxY, toDraw.minZ, toDraw.maxX, toDraw.maxY, toDraw.minZ, 1.0, 0.0, 0.0, lineWidth);
        emitLine(bufferBuilder, stack, toDraw.maxX, toDraw.maxY, toDraw.minZ, toDraw.maxX, toDraw.maxY, toDraw.maxZ, 0.0, 0.0, 1.0, lineWidth);
        emitLine(bufferBuilder, stack, toDraw.maxX, toDraw.maxY, toDraw.maxZ, toDraw.minX, toDraw.maxY, toDraw.maxZ, -1.0, 0.0, 0.0, lineWidth);
        emitLine(bufferBuilder, stack, toDraw.minX, toDraw.maxY, toDraw.maxZ, toDraw.minX, toDraw.maxY, toDraw.minZ, 0.0, 0.0, -1.0, lineWidth);
        // corners
        emitLine(bufferBuilder, stack, toDraw.minX, toDraw.minY, toDraw.minZ, toDraw.minX, toDraw.maxY, toDraw.minZ, 0.0, 1.0, 0.0, lineWidth);
        emitLine(bufferBuilder, stack, toDraw.maxX, toDraw.minY, toDraw.minZ, toDraw.maxX, toDraw.maxY, toDraw.minZ, 0.0, 1.0, 0.0, lineWidth);
        emitLine(bufferBuilder, stack, toDraw.maxX, toDraw.minY, toDraw.maxZ, toDraw.maxX, toDraw.maxY, toDraw.maxZ, 0.0, 1.0, 0.0, lineWidth);
        emitLine(bufferBuilder, stack, toDraw.minX, toDraw.minY, toDraw.maxZ, toDraw.minX, toDraw.maxY, toDraw.maxZ, 0.0, 1.0, 0.0, lineWidth);
    }

    static void emitAABB(BufferBuilder bufferBuilder, PoseStack stack, AABB aabb, double expand, float lineWidth) {
        emitAABB(bufferBuilder, stack, aabb.inflate(expand, expand, expand), lineWidth);
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack, Vec3 start, Vec3 end, float lineWidth) {
        double vpX = renderManager.renderPosX();
        double vpY = renderManager.renderPosY();
        double vpZ = renderManager.renderPosZ();
        emitLine(bufferBuilder, stack,
                start.x - vpX, start.y - vpY, start.z - vpZ,
                end.x - vpX, end.y - vpY, end.z - vpZ,
                lineWidth);
    }

    static void emitTexturedVertex(BufferBuilder bufferBuilder, PoseStack.Pose entry, float x, float y, float z,
            int color, float u, float v, float nx, float ny, float nz) {
        bufferBuilder.addVertex(entry, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(entry, nx, ny, nz);
    }

    static RenderType beaconBeam(Identifier texture, boolean translucent) {
        return BEACON_BEAM.apply(texture, translucent);
    }

    static RenderType beaconBeam(Identifier texture, boolean translucent, boolean ignoreDepth) {
        return ignoreDepth ? beaconBeam(texture, translucent) : RenderTypes.beaconBeam(texture, translucent);
    }
}
