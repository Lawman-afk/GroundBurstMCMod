package dev.groundburst.client.fx;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * Writes the effects' vertices (a colour, a place on the texture, full bright, no overlay, the normal straight up) the
 * quick way (DESIGN.md §11.10): each in one call, which the game's buffer for this vertex layout writes straight to
 * memory, instead of an attribute at a time; the pose's normal worked out once for the whole draw, not for every
 * vertex; and the pose's matrix left out where it is the identity (the level's own, which effects are drawn in). What is
 * written is the same as before.
 */
final class FxVertex {
    private final VertexConsumer buffer;
    private final Matrix4f matrix;
    private final boolean identity;
    private final float nx, ny, nz;
    private final Vector3f at = new Vector3f();

    FxVertex(VertexConsumer buffer, PoseStack.Pose pose) {
        this.buffer = buffer;
        this.matrix = pose.pose();
        this.identity = (this.matrix.properties() & Matrix4fc.PROPERTY_IDENTITY) != 0;
        Vector3f n = pose.transformNormal(0, 1, 0, new Vector3f());
        this.nx = n.x;
        this.ny = n.y;
        this.nz = n.z;
    }

    /**
     * One vertex the quick way without a writer kept for the draw: the identity pose (the level's own) needs no matrix and
     * leaves the normal straight up; any other is worked out as the game would.
     */
    static void put(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color) {
        Matrix4f m = pose.pose();
        if ((m.properties() & Matrix4fc.PROPERTY_IDENTITY) != 0) {
            buffer.addVertex(x, y, z, color, u, v, OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, 0, 1, 0);
            return;
        }
        Vector3f at = m.transformPosition(x, y, z, new Vector3f());
        Vector3f n = pose.transformNormal(0, 1, 0, new Vector3f());
        buffer.addVertex(at.x, at.y, at.z, color, u, v, OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, n.x, n.y, n.z);
    }

    void put(float x, float y, float z, float u, float v, int color) {
        if (!this.identity) {
            this.matrix.transformPosition(x, y, z, this.at);
            x = this.at.x;
            y = this.at.y;
            z = this.at.z;
        }
        this.buffer.addVertex(x, y, z, color, u, v, OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, this.nx, this.ny, this.nz);
    }
}
