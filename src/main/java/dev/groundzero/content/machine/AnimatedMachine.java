package dev.groundzero.content.machine;

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

    /** Model bones not to draw right now (e.g. the dies a press is not holding). Read on the client. */
    default java.util.Set<String> hiddenBones() {
        return java.util.Set.of();
    }

    @Override
    default void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<AnimatedMachine>("main", 4, test -> {
            BlockEntity be = (BlockEntity) test.animatable();
            boolean active = be.getBlockState().hasProperty(MachineBlock.ACTIVE) && be.getBlockState().getValue(MachineBlock.ACTIVE);
            return test.setAndContinue(active ? WORKING : IDLE);
        }));
    }
}
