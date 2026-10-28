package dev.groundburst.content.ordnance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Burning napalm from a napalm bomb (DESIGN.md §14): clinging fire that burns for about 30 seconds whatever the
 * weather (rain does not put it out), sets anything in it alight and hurts it, and now and then lights flammable
 * blocks beside it. It burns out in four stages.
 */
public class NapalmFireBlock extends Block {
    public static final int STAGES = 4;
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, STAGES - 1);
    /** Ticks per stage (plus up to 2 s more, so it burns out unevenly): about 30 s in all. */
    public static final int STAGE_TICKS = 150;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public NapalmFireBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, STAGE_TICKS + level.getRandom().nextInt(40));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Now and then it lights something flammable beside it with ordinary fire.
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            if (random.nextInt(3) == 0 && level.getBlockState(n).isAir() && BaseFireBlock.canBePlacedAt(level, n, d)
                    && level.getBlockState(n.below()).ignitedByLava()) {
                level.setBlockAndUpdate(n, BaseFireBlock.getState(level, n));
            }
        }
        int age = state.getValue(AGE);
        if (age + 1 >= STAGES) {
            level.removeBlock(pos, false);
        } else {
            level.setBlockAndUpdate(pos, state.setValue(AGE, age + 1));
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel server && !entity.fireImmune()) {
            entity.igniteForSeconds(10);
            if (entity instanceof LivingEntity living && living.tickCount % 10 == 0) {
                living.hurtServer(server, server.damageSources().inFire(), 2f);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
        level.addParticle(ParticleTypes.FLAME, x, pos.getY() + 0.2, z, 0, 0.03, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, x, pos.getY() + 0.8, z, 0, 0.04, 0);
        }
        if (random.nextInt(30) == 0) {
            level.playLocalSound(pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8f, 0.8f + random.nextFloat() * 0.3f, false);
        }
    }
}
