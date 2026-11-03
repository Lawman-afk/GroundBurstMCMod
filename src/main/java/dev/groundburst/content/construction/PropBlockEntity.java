package dev.groundburst.content.construction;

import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

import dev.groundburst.content.machine.AnimatedMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The block entity of a prop that only draws its block's GeckoLib model, still (the Missile Wreck). */
public class PropBlockEntity extends BlockEntity implements AnimatedMachine {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public PropBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
