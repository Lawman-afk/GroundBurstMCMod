package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.construction.SearchlightBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import dev.groundburst.port.render.SubmitCustomGeometryEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The Searchlights' beams (DESIGN.md §22.6): from each lit light's lens out along where it points, a cone of light drawn
 * as ribbons facing the eye (a soft middle, wider and fainter as it goes, gone at its end), the lens a glare; added to
 * what lies behind (the effects' glow), so it shows at night and barely by day, as a searchlight's does.
 */
public final class SearchlightFx {
    private static final ResourceLocation BEAM = GroundBurst.id("textures/fx/searchlight_beam.png");
    private static final ResourceLocation GLARE = GroundBurst.id("textures/fx/searchlight_glare.png");
    /** How far a beam is drawn, its radius at the lens and at its end (blocks), its light. */
    private static final double LENGTH = 56, R0 = 0.32, R1 = 2.6;
    private static final int LIGHT = 0xFFF0D2;

    private SearchlightFx() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(SearchlightFx::submit);
    }

    private record Beam(Vec3 from, Vec3 dir) {}

    private static void submit(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SearchlightBlockEntity.CLIENT.isEmpty()) {
            return;
        }
        float partial = mc.isPaused() ? 1 : mc.getFrameTime();
        var camera = event.getLevelRenderState().cameraRenderState;
        Vec3 eye = camera.pos;
        double reach = mc.options.getEffectiveRenderDistance() * 16.0 + LENGTH;
        List<Beam> beams = new ArrayList<>();
        for (SearchlightBlockEntity light : SearchlightBlockEntity.CLIENT) {
            if (light.isRemoved() || light.getLevel() != mc.level || !light.on()) {
                continue;
            }
            Vec3 from = light.lens(partial);
            if (from.distanceToSqr(eye) > reach * reach) {
                continue;
            }
            beams.add(new Beam(from, light.forward(partial)));
        }
        if (beams.isEmpty()) {
            return;
        }
        Quaternionf orientation = camera.orientation;
        Vector3f right = orientation.transform(new Vector3f(1, 0, 0));
        Vector3f up = orientation.transform(new Vector3f(0, 1, 0));
        PoseStack poseStack = event.getPoseStack();
        // By day the beam all but goes (the sky as bright as it), by night it shows whole: by how dark the sky is.
        float night = 0.22f + 0.78f * dev.groundburst.legacy.J21.clamp(mc.level.getSkyDarken() / 11f, 0f, 1f);
        FxRenderTypes.submit(event.getSubmitNodeCollector(), poseStack, FxRenderTypes.glow(BEAM), (pose, buffer) -> {
            for (Beam b : beams) {
                cone(buffer, pose, b, eye, R0, R1, 0.2f * night);
                cone(buffer, pose, b, eye, R0 * 0.5, R1 * 0.32, 0.2f * night);
            }
        });
        FxRenderTypes.submit(event.getSubmitNodeCollector(), poseStack, FxRenderTypes.glow(GLARE), (pose, buffer) -> {
            for (Beam b : beams) {
                Vec3 toEye = eye.subtract(b.from()).normalize();
                float facing = (float) Math.max(0, toEye.dot(b.dir()));        // brightest looking into the lens
                glare(buffer, pose, b.from(), eye, right, up, 0.3f + 0.6f * facing * facing, (0.3f + 0.7f * facing) * (0.5f + 0.5f * night));
            }
        });
    }

    /** A cone of light as ribbons facing the eye, piece by piece along it (so each piece faces the eye from where it is). */
    private static void cone(VertexConsumer b, PoseStack.Pose pose, Beam beam, Vec3 camera, double r0, double r1, float alpha) {
        double t = 0;
        for (int i = 0; i < 64 && t < LENGTH; i++) {
            double near = beam.from().add(beam.dir().scale(t)).distanceTo(camera);
            double t1 = Math.min(LENGTH, t + dev.groundburst.legacy.J21.clamp(near * 0.4, 0.5, 6.0));
            Vec3 p0 = beam.from().add(beam.dir().scale(t)), p1 = beam.from().add(beam.dir().scale(t1));
            double w0 = r0 + (r1 - r0) * t / LENGTH, w1 = r0 + (r1 - r0) * t1 / LENGTH;
            // Seen end on (looking down the beam, or up it into the lens) a ribbon facing the eye would fill the view:
            // its light goes as it turns end on, the lens's glare taking over.
            Vec3 view = p0.add(p1).scale(0.5).subtract(camera).normalize();
            double across = view.cross(beam.dir()).length();
            float side = (float) dev.groundburst.legacy.J21.clamp((across - 0.12) / 0.45, 0.0, 1.0);
            float a0 = alpha * fade(t) * side, a1 = alpha * fade(t1) * side;
            ribbon(b, pose, p0, p1, camera, w0, w1, a0, a1);
            t = t1;
        }
    }

    /** The light along the beam: full a little out from the lens, gone at its end. */
    private static float fade(double t) {
        double k = t / LENGTH;
        return (float) (Math.min(1.0, 0.55 + t / 3.0) * (1 - k) * (1 - k));
    }

    private static void ribbon(VertexConsumer b, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 camera, double w0, double w1, float a0, float a1) {
        float x0 = (float) (p0.x - camera.x), y0 = (float) (p0.y - camera.y), z0 = (float) (p0.z - camera.z);
        float x1 = (float) (p1.x - camera.x), y1 = (float) (p1.y - camera.y), z1 = (float) (p1.z - camera.z);
        Vector3f along = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
        Vector3f view = new Vector3f(-(x0 + x1) / 2, -(y0 + y1) / 2, -(z0 + z1) / 2);
        Vector3f side = along.cross(view, new Vector3f());
        if (side.lengthSquared() < 1e-12f) {
            return;
        }
        side.normalize();
        float s0 = (float) w0, s1 = (float) w1;
        int c0 = color(a0), c1 = color(a1);
        FxVertex.put(b, pose, x0 - side.x * s0, y0 - side.y * s0, z0 - side.z * s0, 0, 0, c0);
        FxVertex.put(b, pose, x0 + side.x * s0, y0 + side.y * s0, z0 + side.z * s0, 1, 0, c0);
        FxVertex.put(b, pose, x1 + side.x * s1, y1 + side.y * s1, z1 + side.z * s1, 1, 1, c1);
        FxVertex.put(b, pose, x1 - side.x * s1, y1 - side.y * s1, z1 - side.z * s1, 0, 1, c1);
    }

    private static void glare(VertexConsumer b, PoseStack.Pose pose, Vec3 at, Vec3 camera, Vector3f right, Vector3f up, float half, float alpha) {
        float x = (float) (at.x - camera.x), y = (float) (at.y - camera.y), z = (float) (at.z - camera.z);
        float rx = right.x * half, ry = right.y * half, rz = right.z * half;
        float ux = up.x * half, uy = up.y * half, uz = up.z * half;
        int c = color(alpha);
        FxVertex.put(b, pose, x - rx - ux, y - ry - uy, z - rz - uz, 0, 1, c);
        FxVertex.put(b, pose, x + rx - ux, y + ry - uy, z + rz - uz, 1, 1, c);
        FxVertex.put(b, pose, x + rx + ux, y + ry + uy, z + rz + uz, 1, 0, c);
        FxVertex.put(b, pose, x - rx + ux, y - ry + uy, z - rz + uz, 0, 0, c);
    }

    private static int color(float alpha) {
        return (int) (Math.max(0, Math.min(1, alpha)) * 255) << 24 | LIGHT;
    }
}
