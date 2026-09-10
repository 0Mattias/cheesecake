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
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;

public interface IRenderer {

    Tessellator tessellator = Tessellator.getInstance();

    IEntityRenderManager renderManager = (IEntityRenderManager) MinecraftClient.getInstance()
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
     * The two layers only differ in their depth test function. Prior to 1.21.5 the "ignore depth" settings
     * were implemented by toggling GL_DEPTH_TEST around the draw call, but the render pipeline owns that
     * state now, so the toggle silently did nothing (and left vanilla's state tracker out of sync).
     */
    RenderLayer linesWithDepthRenderLayer = ((IRenderLayer) RenderLayers.LINES).cheesecake$createRenderLayer(
            "renderLayer/cheesecake_lines_with_depth",
            RenderSetup.builder(RenderPipeline.builder(CHEESECAKE_LINES_SNIPPET)
                    .withLocation("pipeline/cheesecake_lines_with_depth")
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .build())
                    .expectedBufferSize(256)
                    .build());

    RenderLayer linesNoDepthRenderLayer = ((IRenderLayer) RenderLayers.LINES).cheesecake$createRenderLayer(
            "renderLayer/cheesecake_lines_no_depth",
            RenderSetup.builder(RenderPipeline.builder(CHEESECAKE_LINES_SNIPPET)
                    .withLocation("pipeline/cheesecake_lines_no_depth")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build())
                    .expectedBufferSize(256)
                    .build());

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
        return tessellator.begin(VertexFormat.DrawMode.LINES, RenderLayers.LINES.getVertexFormat());
    }

    static BufferBuilder startLines(Color color) {
        return startLines(color, .4f);
    }

    static void endLines(BufferBuilder bufferBuilder, boolean ignoreDepth) {
        BuiltBuffer builtBuffer = bufferBuilder.endNullable();
        if (builtBuffer != null) {
            (ignoreDepth ? linesNoDepthRenderLayer : linesWithDepthRenderLayer).draw(builtBuffer);
        }
    }

    static void emitLine(BufferBuilder bufferBuilder, MatrixStack stack,
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

    static void emitLine(BufferBuilder bufferBuilder, MatrixStack stack,
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

    static void emitLine(BufferBuilder bufferBuilder, MatrixStack stack,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float nx, float ny, float nz,
            float lineWidth) {

        final MatrixStack.Entry entry = stack.peek();
        final Matrix4f matrix4f = entry.getPositionMatrix();

        bufferBuilder
                .vertex(matrix4f, x1, y1, z1)
                .color(color[0], color[1], color[2], color[3])
                .normal(entry, nx, ny, nz)
                .lineWidth(lineWidth);
        bufferBuilder
                .vertex(matrix4f, x2, y2, z2)
                .color(color[0], color[1], color[2], color[3])
                .normal(entry, nx, ny, nz)
                .lineWidth(lineWidth);
    }

    static void emitAABB(BufferBuilder bufferBuilder, MatrixStack stack, Box aabb, float lineWidth) {
        Box toDraw = aabb.offset(-renderManager.renderPosX(), -renderManager.renderPosY(), -renderManager.renderPosZ());

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

    static void emitAABB(BufferBuilder bufferBuilder, MatrixStack stack, Box aabb, double expand, float lineWidth) {
        emitAABB(bufferBuilder, stack, aabb.expand(expand, expand, expand), lineWidth);
    }

    static void emitLine(BufferBuilder bufferBuilder, MatrixStack stack, Vec3d start, Vec3d end, float lineWidth) {
        double vpX = renderManager.renderPosX();
        double vpY = renderManager.renderPosY();
        double vpZ = renderManager.renderPosZ();
        emitLine(bufferBuilder, stack,
                start.x - vpX, start.y - vpY, start.z - vpZ,
                end.x - vpX, end.y - vpY, end.z - vpZ,
                lineWidth);
    }
}
