package dev.groundzero.client.fx;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.groundzero.content.ordnance.Shrapnel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** A shrapnel fragment as a short glowing streak along its flight, turned to face the camera (an additive spark). */
public class ShrapnelRenderer extends EntityRenderer<Shrapnel, ShrapnelRenderer.State> {
    public static class State extends EntityRenderState {
        public float vx, vy, vz;
    }

    public ShrapnelRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Shrapnel entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.vx = (float) entity.getDeltaMovement().x;
        state.vy = (float) entity.getDeltaMovement().y;
        state.vz = (float) entity.getDeltaMovement().z;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Vector3f dir = new Vector3f(state.vx, state.vy, state.vz);
        float speed = dir.length();
        if (speed < 1e-3f) {
            return;
        }
        dir.div(speed);
        // The streak's width runs across the line of sight, so it always shows its full width.
        Vector3f toCamera = new Vector3f((float) (camera.pos.x - state.x), (float) (camera.pos.y - state.y), (float) (camera.pos.z - state.z));
        Vector3f side = new Vector3f(dir).cross(toCamera);
        if (side.lengthSquared() < 1e-6f) {
            return;
        }
        side.normalize(0.05f);
        Vector3f back = new Vector3f(dir).mul(-Math.min(1.2f, speed * 0.6f));
        float u0 = 0, v0 = 3f / Puffs.ROWS, u1 = 0.25f, v1 = v0 + 1f / Puffs.ROWS;   // the spark cell
        int head = 0xFFFFD890, tail = 0x00FF8030;
        collector.submitCustomGeometry(poseStack, FxRenderTypes.glow(Puffs.SHEET), (pose, b) -> {
            Puffs.vertex(b, pose, -side.x, -side.y, -side.z, u0, v1, head);
            Puffs.vertex(b, pose, side.x, side.y, side.z, u1, v1, head);
            Puffs.vertex(b, pose, back.x + side.x, back.y + side.y, back.z + side.z, u1, v0, tail);
            Puffs.vertex(b, pose, back.x - side.x, back.y - side.y, back.z - side.z, u0, v0, tail);
        });
        super.submit(state, poseStack, collector, camera);
    }
}
