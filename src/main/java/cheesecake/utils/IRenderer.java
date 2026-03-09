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
// import com.mojang.blaze3d.systems.RenderSystem;
// import com.mojang.blaze3d.systems.RenderSystem;
// import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;

import java.awt.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public interface IRenderer {

    Tessellator tessellator = Tessellator.getInstance();

    class RenderState {
        static net.minecraft.client.render.BufferBuilder buffer;
    }

    IEntityRenderManager renderManager = (IEntityRenderManager) MinecraftClient.getInstance()
            .getEntityRenderDispatcher();
    TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
    Settings settings = CheesecakeAPI.getSettings();

    float[] color = new float[] { 1.0F, 1.0F, 1.0F, 255.0F };

    class LineState {
        static float currentLineWidth = 1.0F;
    }

    static void glColor(Color color, float alpha) {
        float[] colorComponents = color.getColorComponents(null);
        IRenderer.color[0] = colorComponents[0];
        IRenderer.color[1] = colorComponents[1];
        IRenderer.color[2] = colorComponents[2];
        IRenderer.color[3] = alpha;
    }

    static void startLines(Color color, float alpha, float lineWidth, boolean ignoreDepth) {
        glColor(color, alpha);
        LineState.currentLineWidth = lineWidth;

        // depth test configuration is now handled via RenderLayers in 1.21.2+

        RenderState.buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES,
                RenderLayers.LINES.getVertexFormat());
    }

    static void startLines(Color color, float lineWidth, boolean ignoreDepth) {
        startLines(color, .4f, lineWidth, ignoreDepth);
    }

    static void endLines(boolean ignoreDepth) {
        BuiltBuffer builtBuffer = RenderState.buffer.endNullable();
        if (builtBuffer != null) {
            if (ignoreDepth) {
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
                org.lwjgl.opengl.GL11.glDepthFunc(org.lwjgl.opengl.GL11.GL_ALWAYS);
            }

            RenderLayers.LINES.draw(builtBuffer);

            if (ignoreDepth) {
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
                org.lwjgl.opengl.GL11.glDepthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
            }
        }
    }

    static void emitLine(MatrixStack stack, double x1, double y1, double z1, double x2, double y2, double z2) {
        final double dx = x2 - x1;
        final double dy = y2 - y1;
        final double dz = z2 - z1;

        final double invMag = 1.0 / Math.sqrt(dx * dx + dy * dy + dz * dz);
        final float nx = (float) (dx * invMag);
        final float ny = (float) (dy * invMag);
        final float nz = (float) (dz * invMag);

        emitLine(stack, x1, y1, z1, x2, y2, z2, nx, ny, nz);
    }

    static void emitLine(MatrixStack stack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double nx, double ny, double nz) {
        emitLine(stack,
                (float) x1, (float) y1, (float) z1,
                (float) x2, (float) y2, (float) z2,
                (float) nx, (float) ny, (float) nz);
    }

    static void emitLine(MatrixStack stack,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float nx, float ny, float nz) {

        final Matrix4f matrix4f = stack.peek().getPositionMatrix();
        // final Matrix3f normal = stack.peek().getNormalMatrix();

        RenderState.buffer
                .vertex(matrix4f, x1, y1, z1)
                .color(color[0], color[1], color[2], color[3])
                .normal(stack.peek(), nx, ny, nz)
                .lineWidth(LineState.currentLineWidth)
                .vertex(matrix4f, x2, y2, z2)
                .color(color[0], color[1], color[2], color[3])
                .normal(stack.peek(), nx, ny, nz)
                .lineWidth(LineState.currentLineWidth);
    }

    static void emitAABB(MatrixStack stack, Box aabb) {
        Box toDraw = aabb.offset(-renderManager.renderPosX(), -renderManager.renderPosY(), -renderManager.renderPosZ());

        // bottom
        emitLine(stack, toDraw.minX, toDraw.minY, toDraw.minZ, toDraw.maxX, toDraw.minY, toDraw.minZ, 1.0, 0.0, 0.0);
        emitLine(stack, toDraw.maxX, toDraw.minY, toDraw.minZ, toDraw.maxX, toDraw.minY, toDraw.maxZ, 0.0, 0.0, 1.0);
        emitLine(stack, toDraw.maxX, toDraw.minY, toDraw.maxZ, toDraw.minX, toDraw.minY, toDraw.maxZ, -1.0, 0.0, 0.0);
        emitLine(stack, toDraw.minX, toDraw.minY, toDraw.maxZ, toDraw.minX, toDraw.minY, toDraw.minZ, 0.0, 0.0, -1.0);
        // top
        emitLine(stack, toDraw.minX, toDraw.maxY, toDraw.minZ, toDraw.maxX, toDraw.maxY, toDraw.minZ, 1.0, 0.0, 0.0);
        emitLine(stack, toDraw.maxX, toDraw.maxY, toDraw.minZ, toDraw.maxX, toDraw.maxY, toDraw.maxZ, 0.0, 0.0, 1.0);
        emitLine(stack, toDraw.maxX, toDraw.maxY, toDraw.maxZ, toDraw.minX, toDraw.maxY, toDraw.maxZ, -1.0, 0.0, 0.0);
        emitLine(stack, toDraw.minX, toDraw.maxY, toDraw.maxZ, toDraw.minX, toDraw.maxY, toDraw.minZ, 0.0, 0.0, -1.0);
        // corners
        emitLine(stack, toDraw.minX, toDraw.minY, toDraw.minZ, toDraw.minX, toDraw.maxY, toDraw.minZ, 0.0, 1.0, 0.0);
        emitLine(stack, toDraw.maxX, toDraw.minY, toDraw.minZ, toDraw.maxX, toDraw.maxY, toDraw.minZ, 0.0, 1.0, 0.0);
        emitLine(stack, toDraw.maxX, toDraw.minY, toDraw.maxZ, toDraw.maxX, toDraw.maxY, toDraw.maxZ, 0.0, 1.0, 0.0);
        emitLine(stack, toDraw.minX, toDraw.minY, toDraw.maxZ, toDraw.minX, toDraw.maxY, toDraw.maxZ, 0.0, 1.0, 0.0);
    }

    static void emitAABB(MatrixStack stack, Box aabb, double expand) {
        emitAABB(stack, aabb.expand(expand, expand, expand));
    }

    static void emitLine(MatrixStack stack, Vec3d start, Vec3d end) {
        double vpX = renderManager.renderPosX();
        double vpY = renderManager.renderPosY();
        double vpZ = renderManager.renderPosZ();
        emitLine(stack, start.x - vpX, start.y - vpY, start.z - vpZ, end.x - vpX, end.y - vpY, end.z - vpZ);
    }

}
