package dev.groundburst.port.render;

import org.joml.Quaternionfc;

import com.mojang.blaze3d.vertex.PoseStack;

/** Minecraft 26.x's turns and scales of one pose (its matrix and its normals together), over 1.21.1's pose. */
public final class Poses {
    private Poses() {}

    public static void rotate(PoseStack.Pose pose, Quaternionfc rotation) {
        pose.pose().rotate(rotation);
        pose.normal().rotate(rotation);
    }

    public static void set(PoseStack.Pose pose, PoseStack.Pose from) {
        pose.pose().set(from.pose());
        pose.normal().set(from.normal());
    }

    public static void translate(PoseStack.Pose pose, float x, float y, float z) {
        pose.pose().translate(x, y, z);
    }

    /** As 1.21.1's pose stack scales: the normals by the inverse, kept unit (a uniform scale only flips them when negative). */
    public static void scale(PoseStack.Pose pose, float x, float y, float z) {
        pose.pose().scale(x, y, z);
        if (x == y && y == z) {
            if (x > 0) {
                return;
            }
            pose.normal().scale(-1);
        }
        float ix = 1 / x, iy = 1 / y, iz = 1 / z;
        float k = net.minecraft.util.Mth.fastInvCubeRoot(ix * iy * iz);
        pose.normal().scale(k * ix, k * iy, k * iz);
    }
}
