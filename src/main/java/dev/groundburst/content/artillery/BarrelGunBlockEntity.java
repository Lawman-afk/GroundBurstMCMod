package dev.groundburst.content.artillery;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A gun with a long barrel on trunnions (DESIGN.md §20.1): the 155 mm Howitzer. Its model's
 * "traverse" bone (the top carriage) turns about its pivot, its "barrel" bone (the cradle and the barrel, built level
 * pointing over its front) is raised about the trunnions, and the "recoil" bone in it (the barrel itself) runs back
 * along its axis as it fires and returns.
 */
public class BarrelGunBlockEntity extends GunBlockEntity {
    /** How far its barrel recoils, model pixels, and over how many ticks it runs out again. */
    private final float recoil, recoilTicks;

    public BarrelGunBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, GunSpec spec, float recoil, float recoilTicks) {
        super(type, pos, state, spec);
        this.recoil = recoil;
        this.recoilTicks = recoilTicks;
    }

    public static BarrelGunBlockEntity howitzer(BlockPos pos, BlockState state) {
        return new BarrelGunBlockEntity(GBArtillery.HOWITZER_ENTITY.get(), pos, state, GBArtillery.HOWITZER_SPEC, 14, 24);
    }

    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        if (this.level == null) {
            return Map.of();
        }
        Map<String, float[]> out = new HashMap<>();
        out.put("traverse", new float[] {0, traverseRadians(partialTick), 0});
        out.put("barrel", new float[] {(float) Math.toRadians(shownElevation(partialTick)), 0, 0});
        return out;
    }

    /** The model at rest (as an item shows it): its barrel raised to its rest elevation. */
    public static Map<String, float[]> restPose(GunSpec spec) {
        return Map.of("barrel", new float[] {(float) Math.toRadians(spec.restElevation()), 0, 0});
    }

    /** As it fires the barrel slams back along its axis, then runs out again. */
    @Override
    public Map<String, float[]> boneOffsets(float partialTick) {
        double since = sinceFired(partialTick);
        double out = 2 + this.recoilTicks;
        if (since < 0 || since > out) {
            return Map.of();
        }
        double k = since < 2 ? since / 2 : 1 - (since - 2) / this.recoilTicks;
        k = k < 0 ? 0 : k * k * (3 - 2 * k);
        return Map.of("recoil", new float[] {0, 0, (float) (this.recoil * k)});
    }
}
