package dev.groundburst.content.construction;

import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import dev.groundburst.content.machine.AnimatedMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Tape Computer's lower block (DESIGN.md §22.4): it only draws the cabinet. While the block runs ({@code ACTIVE}) it
 * loops one of three takes of its "working" animation (the same reading, stopping and rewinding, begun four and eight
 * seconds on: the model script's), chosen by where it stands, so a row of them never runs in step; stopped, "idle".
 */
public class TapeComputerBlockEntity extends BlockEntity implements AnimatedMachine {
    private static final RawAnimation[] TAKES = {RawAnimation.begin().thenLoop("working"), RawAnimation.begin().thenLoop("working_b"),
            RawAnimation.begin().thenLoop("working_c")};
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TapeComputerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation take = TAKES[Math.floorMod(this.worldPosition.getX() * 7 + this.worldPosition.getZ() * 13 + this.worldPosition.getY(), TAKES.length)];
        controllers.add(new AnimationController<TapeComputerBlockEntity>(this, "main", 4, test -> {
            BlockState state = test.getAnimatable().getBlockState();
            boolean on = state.hasProperty(TapeComputerBlock.ACTIVE) && state.getValue(TapeComputerBlock.ACTIVE);
            return test.setAndContinue(on ? take : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
