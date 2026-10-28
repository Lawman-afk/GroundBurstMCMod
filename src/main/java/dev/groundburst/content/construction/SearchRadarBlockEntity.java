package dev.groundburst.content.construction;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.content.machine.AnimatedMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity that draws a Search Radar or its wreck (GeckoLib): the head turns ("working") while the block is
 * active, as any animated machine's; a wreck has no {@code active} and rests.
 */
public class SearchRadarBlockEntity extends BlockEntity implements AnimatedMachine {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SearchRadarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
