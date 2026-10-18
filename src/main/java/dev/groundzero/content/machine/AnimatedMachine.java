package dev.groundzero.content.machine;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * GeckoLib glue for machines: a single "main" controller that loops the model's "working" animation while the
 * block's ACTIVE state is set (ACTIVE is synced to clients with the block state, so no extra packets).
 */
public interface AnimatedMachine extends GeoBlockEntity {
    RawAnimation WORKING = RawAnimation.begin().thenLoop("working");

    @Override
    default void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<AnimatedMachine>("main", 4, test -> {
            BlockEntity be = (BlockEntity) test.animatable();
            boolean active = be.getBlockState().hasProperty(MachineBlock.ACTIVE) && be.getBlockState().getValue(MachineBlock.ACTIVE);
            return active ? test.setAndContinue(WORKING) : PlayState.STOP;
        }));
    }
}
