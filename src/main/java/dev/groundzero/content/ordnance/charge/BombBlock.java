package dev.groundzero.content.ordnance.charge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A bomb (DESIGN.md §14, Phase 6 slice 6d): a placed charge lying on its side that falls like sand when nothing holds
 * it up, keeping its fuze settings. Its impact fuze: armed, it goes off when it lands after a fall or when a projectile
 * hits it. Safe, it just lands.
 */
public class BombBlock extends ChargeBlock implements Fallable {
    private static final int FALL_DELAY = 2;

    public BombBlock(ChargeKind kind, Properties properties) {
        super(kind, properties);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;                                   // unsupported, it falls rather than popping off
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.scheduleTick(pos, this, FALL_DELAY);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        ticks.scheduleTick(pos, this, FALL_DELAY);
        return state;
    }

    /** Nothing under it: it falls, carrying its fuze settings (the falling block entity does not keep them by itself). */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!FallingBlock.isFree(level.getBlockState(pos.below())) || pos.getY() < level.getMinY()) {
            return;
        }
        ChargeBlockEntity be = at(level, pos);
        CompoundTag data = be == null ? null : be.saveWithoutMetadata(level.registryAccess());
        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
        falling.blockData = data;
    }

    /** The impact fuze: an armed bomb that lands goes off. */
    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replacedBlock, FallingBlockEntity entity) {
        if (level instanceof ServerLevel server && state.getValue(ARMED)) {
            ChargeBlockEntity be = at(level, pos);
            Charges.detonate(server, pos, state, be == null || be.owner() == null ? null : server.getEntity(be.owner()));
        }
    }

    /** The impact fuze: an armed bomb hit by a projectile goes off. */
    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (level instanceof ServerLevel server && state.getValue(ARMED)) {
            Charges.detonate(server, hit.getBlockPos(), state, projectile.getOwner());
        }
    }
}
