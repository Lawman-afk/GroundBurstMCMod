package dev.groundburst.port.render;

import org.joml.Quaternionf;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Minecraft 26.x's view of the camera for a frame (where it is, how it is turned), taken from 1.21.1's camera. */
public final class CameraRenderState {
    public Vec3 pos = Vec3.ZERO;
    public BlockPos blockPos = BlockPos.ZERO;
    public final Quaternionf orientation = new Quaternionf();
    public float xRot, yRot;
    public boolean isFirstPerson;

    public static CameraRenderState of(Camera camera) {
        CameraRenderState s = new CameraRenderState();
        s.pos = camera.getPosition();
        s.blockPos = camera.getBlockPosition();
        s.orientation.set(camera.rotation());
        s.xRot = camera.getXRot();
        s.yRot = camera.getYRot();
        s.isFirstPerson = !camera.isDetached();
        return s;
    }

    /** The game's camera as it is now. */
    public static CameraRenderState current() {
        return of(Minecraft.getInstance().gameRenderer.getMainCamera());
    }
}
