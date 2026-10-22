package dev.groundzero.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.launch.MissileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Spent stages falling away from missiles in flight (DESIGN.md §17.5, slice 9a): the client's own. When a missile it
 * can see drops a stage, the stage starts where it was on the missile, with the missile's way on less its thrust, and
 * tumbles down under gravity; it is lost with a puff of dust and a thud where it lands, or after a minute. Drawn with
 * the stage's own item model (launch.py: strategic_stage1, icbm_stage1, icbm_stage2) the way the missile is.
 */
final class SpentStages {
    /** Drawn four times over, as the missile is (MissileRenderer). */
    private static final float SCALE = 4;
    private static final int LIFE = 1200;

    private static final class Stage {
        final ItemStackRenderState item = new ItemStackRenderState();
        final float[] size;
        /** How far to lift the model (item blocks) so its middle sits at the stage's position. */
        final float lift;
        double x, y, z, xo, yo, zo, vx, vy, vz;
        float yaw, tilt, yawO, tiltO, yawSpin, tiltSpin;
        int age;

        Stage(ItemStack look, float[] size, float lift) {
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(this.item, look, ItemDisplayContext.NONE, null, null, 0);
            this.size = size;
            this.lift = lift;
        }
    }

    private static final List<Stage> STAGES = new ArrayList<>();

    private SpentStages() {}

    static boolean isEmpty() {
        return STAGES.isEmpty();
    }

    static void clear() {
        STAGES.clear();
    }

    /** {@code missile} has just dropped its {@code k}th stage: let it fall from where it was. */
    static void drop(MissileEntity missile, int k) {
        var body = missile.body();
        ItemStack look = new ItemStack(dev.groundzero.content.assembly.GZAssembly.MISSILE.get());
        look.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(
                List.of(), List.of(), List.of(MissileParts.flight(MissileParts.spentStage(body, k))), List.of()));
        // The model is drawn about its own middle (item pixel 8): lifted to stand the stage's middle on its position.
        float middle = MissileParts.spentStageMiddle(body, k), tail = body == dev.groundzero.content.assembly.SizeClass.XXL
                ? MissileParts.ICBM_TAIL : MissileParts.STRATEGIC_TAIL;
        float[] size = MissileParts.scale(body);
        Stage s = new Stage(look, size, 0.5f - middle / 16f);
        var random = missile.level().getRandom();
        Vec3 v = missile.getDeltaMovement();
        // Where its middle is now: up the missile's axis from its tail.
        double tilt = Math.toRadians(missile.tilt(1)), yaw = Math.toRadians(missile.getYRot());
        Vec3 axis = new Vec3(Math.sin(tilt) * Math.sin(yaw), Math.cos(tilt), Math.sin(tilt) * Math.cos(yaw));
        double up = (middle + tail) / 16 * SCALE * size[1];
        s.x = s.xo = missile.getX() + axis.x * up;
        s.y = s.yo = missile.getY() + axis.y * up;
        s.z = s.zo = missile.getZ() + axis.z * up;
        s.vx = v.x * 0.55 + (random.nextDouble() - 0.5) * 0.15;
        s.vy = v.y * 0.55;
        s.vz = v.z * 0.55 + (random.nextDouble() - 0.5) * 0.15;
        s.yaw = s.yawO = missile.getYRot();
        s.tilt = s.tiltO = missile.tilt(1);
        s.yawSpin = (random.nextFloat() - 0.5f) * 3;
        s.tiltSpin = 1.2f + random.nextFloat() * 2.2f;             // it tips over as it falls
        STAGES.add(s);
    }

    static void tick(ClientLevel level, Puffs puffs, double now, float quality) {
        for (var it = STAGES.iterator(); it.hasNext(); ) {
            Stage s = it.next();
            s.xo = s.x;
            s.yo = s.y;
            s.zo = s.z;
            s.yawO = s.yaw;
            s.tiltO = s.tilt;
            s.vy -= 0.045;
            s.vx *= 0.99;
            s.vy *= 0.99;
            s.vz *= 0.99;
            s.x += s.vx;
            s.y += s.vy;
            s.z += s.vz;
            s.yaw += s.yawSpin;
            s.tilt += s.tiltSpin;
            s.age++;
            // A thin wisp from its open end while it is fresh.
            if (s.age < 60 && s.age % 2 == 0) {
                Puffs.Puff p = new Puffs.Puff();
                p.kind = Puffs.Kind.SMOKE;
                p.x = s.x;
                p.y = s.y;
                p.z = s.z;
                p.drag = 0.05f;
                p.birth = now;
                p.life = 120;
                p.size0 = 1.2f;
                p.size1 = 3.5f;
                p.color0 = 0xD8D6D2;
                p.color1 = 0xAFAAA4;
                p.alpha = 0.5f * (1 - s.age / 60f);
                p.variant = level.getRandom().nextInt(8);
                puffs.add(p, quality);
            }
            BlockPos at = BlockPos.containing(s.x, s.y, s.z);
            boolean landed = level.isLoaded(at) && !level.getBlockState(at).getCollisionShape(level, at).isEmpty();
            if (landed || s.age > LIFE || s.y < level.getMinY()) {
                if (landed) {
                    land(level, s, puffs, now, quality);
                }
                it.remove();
            }
        }
    }

    /** Where it lands: a puff of dust and a heavy thud. */
    private static void land(ClientLevel level, Stage s, Puffs puffs, double now, float quality) {
        var random = level.getRandom();
        for (int i = 0; i < 14; i++) {
            Puffs.Puff p = new Puffs.Puff();
            p.kind = Puffs.Kind.SMOKE;
            double a = random.nextDouble() * Math.PI * 2;
            p.x = s.x;
            p.y = s.y + 0.5;
            p.z = s.z;
            float speed = 0.15f + random.nextFloat() * 0.25f;
            p.vx = (float) Math.cos(a) * speed;
            p.vz = (float) Math.sin(a) * speed;
            p.vy = 0.05f + random.nextFloat() * 0.08f;
            p.drag = 0.05f;
            p.birth = now;
            p.life = 140 + random.nextInt(80);
            p.size0 = 1.5f;
            p.size1 = 5;
            p.color0 = 0x9A8E7E;
            p.color1 = 0x7E7468;
            p.alpha = 0.7f;
            p.variant = random.nextInt(8);
            puffs.add(p, quality);
        }
        level.playLocalSound(s.x, s.y, s.z, net.minecraft.sounds.SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 3f, 0.4f, false);
        level.playLocalSound(s.x, s.y, s.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5f, 1.6f, false);
    }

    static void submit(SubmitNodeCollector collector, PoseStack poseStack, ClientLevel level, Vec3 camera, float partial) {
        for (Stage s : STAGES) {
            double x = s.xo + (s.x - s.xo) * partial, y = s.yo + (s.y - s.yo) * partial, z = s.zo + (s.z - s.zo) * partial;
            int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(x, y, z));
            poseStack.pushPose();
            poseStack.translate(x - camera.x, y - camera.y, z - camera.z);
            poseStack.rotate(Axis.YP.rotationDegrees(s.yawO + (s.yaw - s.yawO) * partial));
            poseStack.rotate(Axis.XP.rotationDegrees(s.tiltO + (s.tilt - s.tiltO) * partial));
            poseStack.scale(SCALE * s.size[0], SCALE * s.size[1], SCALE * s.size[0]);
            poseStack.translate(0, s.lift, 0);
            s.item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
