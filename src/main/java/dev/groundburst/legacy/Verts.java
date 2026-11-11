package dev.groundburst.legacy;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Minecraft 1.21's vertex calls on 1.20.1 (the port writes a 1.21 vertex, {@code buf.addVertex(pose, x, y, z).setColor(c)...},
 * as 1.20.1's, {@code buf.vertex(Verts.matrix(pose), x, y, z).color(c)...endVertex()}): a pose's matrices, and the whole
 * vertex 1.21 gives in one call.
 */
public final class Verts {
    private Verts() {}

    public static Matrix4f matrix(PoseStack.Pose pose) {
        return pose.pose();
    }

    public static Matrix4f matrix(Matrix4f matrix) {
        return matrix;
    }

    public static Matrix3f normal(PoseStack.Pose pose) {
        return pose.normal();
    }

    public static Matrix3f normal(Matrix3f matrix) {
        return matrix;
    }

    /** 1.21's addVertex(x, y, z, colour, u, v, overlay, light, nx, ny, nz): 1.20.1's whole vertex, colour split in four. */
    public static void full(VertexConsumer buf, float x, float y, float z, int argb, float u, float v, int overlay, int light, float nx,
            float ny, float nz) {
        buf.vertex(x, y, z, ((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f,
                u, v, overlay, light, nx, ny, nz);
    }
}
