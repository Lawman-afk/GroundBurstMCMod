package dev.groundburst.content.construction;

import java.util.Map;
import java.util.Set;

import org.joml.Vector3f;

import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

import dev.groundburst.content.machine.AnimatedMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The block entity that draws a Searchlight (GeckoLib) and knows where it points: switched on, it sweeps the sky, its
 * yoke turning to and fro about its facing and its drum rising and falling (each light by its own phase, so a row never
 * sweeps in step), eased out of its rest and back into it as it is switched. Its beam is drawn by the client's effects
 * from {@link #lens} and {@link #forward}.
 */
public class SearchlightBlockEntity extends BlockEntity implements AnimatedMachine {
    /** The trunnions' height and how far the lens stands in front of them (model pixels): tools/assets/searchlight.py. */
    public static final float TRUNNION_Y = 17f, LENS = 4.95f;
    /** At rest: straight ahead, a little raised. */
    private static final double REST_TILT = Math.toRadians(8);
    /** The searchlights the client has loaded (their beams drawn by the effects). */
    public static final Set<SearchlightBlockEntity> CLIENT = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean wasOn;
    private double switchedAt = -1e9;

    public SearchlightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide()) {
            CLIENT.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        CLIENT.remove(this);
    }

    public boolean on() {
        BlockState state = getBlockState();
        return state.hasProperty(SearchlightBlock.ACTIVE) && state.getValue(SearchlightBlock.ACTIVE);
    }

    /** Its pose now: {pan, tilt} in radians (pan right-handed about up, tilt raising its front). */
    public float[] pose(float partialTick) {
        Level level = getLevel();
        if (level == null) {
            return new float[] {0, (float) REST_TILT};
        }
        double t = level.getGameTime() + partialTick;
        boolean on = on();
        if (on != this.wasOn) {
            this.wasOn = on;
            this.switchedAt = t;
        }
        double k = Math.clamp((t - this.switchedAt) / 30.0, 0.0, 1.0);
        k = k * k * (3 - 2 * k);
        double blend = on ? k : 1 - k;
        double phase = (getBlockPos().asLong() * 0x9E3779B97F4A7C15L >>> 40) / (double) (1L << 24) * Math.PI * 2;
        double s = t / 20.0;
        double pan = Math.toRadians(62) * Math.sin(s * Math.PI * 2 / 23.0 + phase) + Math.toRadians(10) * Math.sin(s * Math.PI * 2 / 7.1 + phase * 1.7);
        double tilt = Math.toRadians(40) + Math.toRadians(14) * Math.sin(s * Math.PI * 2 / 15.0 + phase * 0.6);
        return new float[] {(float) (pan * blend), (float) (REST_TILT + (tilt - REST_TILT) * blend)};
    }

    /** Where its beam points, in the world: its facing turned by its pan, raised by its tilt. */
    public Vec3 forward(float partialTick) {
        float[] p = pose(partialTick);
        Direction facing = getBlockState().hasProperty(SearchlightBlock.FACING) ? getBlockState().getValue(SearchlightBlock.FACING) : Direction.NORTH;
        Vector3f v = new Vector3f(0, 0, -1).rotateX(p[1]).rotateY(p[0]).rotateY((float) Math.toRadians(180 - facing.toYRot()));
        return new Vec3(v.x, v.y, v.z);
    }

    /** The middle of its lens, in the world. */
    public Vec3 lens(float partialTick) {
        Vec3 pivot = Vec3.atBottomCenterOf(getBlockPos()).add(0, TRUNNION_Y / 16.0, 0);
        return pivot.add(forward(partialTick).scale(LENS / 16.0));
    }

    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        float[] p = pose(partialTick);
        return Map.of("yoke", new float[] {0, p[0], 0}, "lamp", new float[] {p[1], 0, 0});
    }

    @Override
    public Map<String, float[]> boneScales() {
        return on() ? Map.of() : Map.of("glow", new float[] {0, 0, 0});
    }
}
