package dev.groundzero.content.fluid;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A bucket of gas (drawn upside down, like any lighter-than-air fluid's bucket). Emptying it releases a full gas
 * cloud, which then rises; machines and pipes treat it like any other bucket of its fluid.
 */
public class GasBucketItem extends BucketItem {
    private final Supplier<GasCloudBlock> cloud;

    public GasBucketItem(Fluid gas, Supplier<GasCloudBlock> cloud, Properties properties) {
        super(gas, properties);
        this.cloud = cloud;
    }

    @Override
    public boolean emptyContents(@Nullable LivingEntity user, Level level, BlockPos pos, @Nullable BlockHitResult hitResult, @Nullable ItemStack containerItem) {
        BlockState there = level.getBlockState(pos);
        if (!there.isAir() && !there.canBeReplaced()) {
            return false;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, this.cloud.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1.4f);
        return true;
    }
}
