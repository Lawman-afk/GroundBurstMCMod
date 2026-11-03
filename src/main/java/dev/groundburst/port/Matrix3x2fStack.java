package dev.groundburst.port;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * The 2D pose stack a 26.x screen draws with ({@code org.joml.Matrix3x2fStack}), over 1.21.1's {@link PoseStack} (which
 * the GUI shares with 3D: x and y as given, z untouched).
 */
public final class Matrix3x2fStack {
    private final PoseStack pose;

    Matrix3x2fStack(PoseStack pose) {
        this.pose = pose;
    }

    /** 1.21.1's pose stack underneath. */
    public PoseStack unwrap() {
        return this.pose;
    }

    public Matrix3x2fStack pushMatrix() {
        this.pose.pushPose();
        return this;
    }

    public Matrix3x2fStack popMatrix() {
        this.pose.popPose();
        return this;
    }

    public Matrix3x2fStack translate(float x, float y) {
        this.pose.translate(x, y, 0);
        return this;
    }

    public Matrix3x2fStack scale(float x, float y) {
        this.pose.scale(x, y, 1);
        return this;
    }

    public Matrix3x2fStack scale(float s) {
        this.pose.scale(s, s, 1);
        return this;
    }

    /** Turned {@code radians} about the screen's normal (clockwise as the screen is drawn, as JOML's 2D rotate is). */
    public Matrix3x2fStack rotate(float radians) {
        this.pose.mulPose(Axis.ZP.rotation(radians));
        return this;
    }
}
