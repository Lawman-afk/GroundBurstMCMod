package dev.groundburst.content.machine;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * GeckoLib glue for machines: a single "main" controller that loops the model's "working" animation while the
 * block's ACTIVE state is set (ACTIVE is synced to clients with the block state, so no extra packets) and eases
 * back to the held "idle" rest pose otherwise. Returning STOP would not do: GeckoLib keeps cycling a looping
 * animation until another one replaces it.
 */
public interface AnimatedMachine extends GeoBlockEntity {
    RawAnimation WORKING = RawAnimation.begin().thenLoop("working");
    RawAnimation IDLE = RawAnimation.begin().thenPlayAndHold("idle");

    /** The pose it rests in while not working (the held "idle" animation; the Launch Complex's arms stay clear when empty). */
    default RawAnimation idleAnimation() {
        return IDLE;
    }

    /** Bones drawn scaled {x, y, z} about their pivots right now (e.g. the silo's missile, by its body). Read on the client. */
    default java.util.Map<String, float[]> boneScales() {
        return java.util.Map.of();
    }

    /**
     * Bones moved {x, y, z} model pixels right now, x as GeckoLib has it (negated), e.g. the Missile Silo's door leaves
     * sliding apart. Read on the client every frame.
     */
    default java.util.Map<String, float[]> boneOffsets(float partialTick) {
        return java.util.Map.of();
    }

    /**
     * Bones turned {x, y, z} radians right now, set over whatever the animation gives them (the Interceptor Battery's
     * turret and launcher, aimed at a missile). Read on the client every frame.
     */
    default java.util.Map<String, float[]> boneRotations(float partialTick) {
        return java.util.Map.of();
    }

    /**
     * The texture to draw the model in instead of its own (null: its own): the Warning Beacon's colour. Its glowmask is
     * the texture's {@code _glowmask}. Only for a renderer made with a {@code VariantGeoModel}.
     */
    default net.minecraft.resources.@org.jspecify.annotations.Nullable Identifier textureVariant() {
        return null;
    }

    /** Model bones not to draw right now (e.g. the dies a press is not holding). Read on the client. */
    default java.util.Set<String> hiddenBones() {
        return java.util.Set.of();
    }

    @Override
    default void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<AnimatedMachine>("main", 4, test -> {
            BlockEntity be = (BlockEntity) test.animatable();
            boolean active = be.getBlockState().hasProperty(MachineBlock.ACTIVE) && be.getBlockState().getValue(MachineBlock.ACTIVE);
            return test.setAndContinue(active ? WORKING : ((AnimatedMachine) test.animatable()).idleAnimation());
        }));
    }
}
