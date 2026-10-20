package dev.groundzero.content.fluid;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * A released gas: the opposite of a liquid. It rises a block at a time until something stops it, then pools against
 * the ceiling and spreads sideways, levelling out with its neighbours; under open sky it thins away. Each block holds
 * 1-8 parts (8 = one full bucket), drawn as a layer hanging from the top of the block. A full block can be scooped
 * back up with an empty bucket.
 */
public class GasCloudBlock extends Block implements BucketPickup {
    public static final int MAX = 8;
    public static final IntegerProperty VOLUME = IntegerProperty.create("volume", 1, MAX);
    private static final int TICK_DELAY = 4;
    private static final VoxelShape[] SHAPES = new VoxelShape[MAX + 1];

    static {
        for (int v = 1; v <= MAX; v++) {
            SHAPES[v] = Block.box(0, 16 - v * 2, 0, 16, 16, 16);
        }
    }

    private final ChemFluid.Hazard hazard;

    public GasCloudBlock(Properties properties, ChemFluid.Hazard hazard) {
        super(properties);
        this.hazard = hazard;
        registerDefaultState(this.stateDefinition.any().setValue(VOLUME, MAX));
    }

    /** The gas this cloud is made of. */
    private @Nullable ChemFluid fluid() {
        for (ChemFluid f : GZFluids.all()) {
            if (f.cloud() != null && f.cloud().get() == this) {
                return f;
            }
        }
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VOLUME);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(VOLUME)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Faces between touching clouds of the same gas are hidden, so a pooled cloud reads as one volume. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, Direction direction) {
        if (!neighbour.is(this)) {
            return false;
        }
        int mine = state.getValue(VOLUME), theirs = neighbour.getValue(VOLUME);
        return switch (direction) {
            case UP -> theirs == MAX;                         // the cloud above reaches down to our top
            case DOWN -> mine == MAX;                         // reaches the floor of our block, where theirs tops out
            default -> theirs >= mine;                        // their layer covers our side
        };
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, TICK_DELAY);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        ticks.scheduleTick(pos, this, TICK_DELAY);
        return state;
    }

    private boolean isRoom(BlockState state) {
        return state.isAir() || state.is(this);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int volume = state.getValue(VOLUME);
        BlockPos up = pos.above();
        BlockState above = level.getBlockState(up);
        // Rise into empty space, or top up the gas above.
        if (up.getY() < level.getMaxY() && isRoom(above)) {
            int room = above.is(this) ? MAX - above.getValue(VOLUME) : MAX;
            if (room > 0) {
                int moved = Math.min(room, volume);
                level.setBlock(up, defaultBlockState().setValue(VOLUME, (above.is(this) ? above.getValue(VOLUME) : 0) + moved), Block.UPDATE_ALL);
                setVolume(level, pos, volume - moved);
                return;
            }
        }
        // Thin away under open sky (it would otherwise climb to the build limit and sit there).
        if (level.canSeeSky(up) && random.nextInt(4) == 0) {
            setVolume(level, pos, volume - 1);
            return;
        }
        // Pooled under a ceiling: level out with lower neighbours, one part at a time.
        if (volume > 1) {
            List<BlockPos> lower = new ArrayList<>();
            int lowest = volume - 1;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = pos.relative(d);
                BlockState ns = level.getBlockState(n);
                if (!isRoom(ns)) {
                    continue;
                }
                int nv = ns.is(this) ? ns.getValue(VOLUME) : 0;
                if (nv < lowest) {
                    lowest = nv;
                    lower.clear();
                }
                if (nv == lowest) {
                    lower.add(n);
                }
            }
            if (!lower.isEmpty()) {
                BlockPos target = lower.get(random.nextInt(lower.size()));
                level.setBlock(target, defaultBlockState().setValue(VOLUME, lowest + 1), Block.UPDATE_ALL);
                setVolume(level, pos, volume - 1);
                return;
            }
        }
        // A lone wisp slowly disperses.
        if (volume == 1 && random.nextInt(20) == 0) {
            setVolume(level, pos, 0);
            return;
        }
        level.scheduleTick(pos, this, TICK_DELAY * 2);
    }

    private void setVolume(ServerLevel level, BlockPos pos, int volume) {
        if (volume <= 0) {
            level.removeBlock(pos, false);
        } else {
            level.setBlock(pos, defaultBlockState().setValue(VOLUME, volume), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (this.hazard == ChemFluid.Hazard.TOXIC && level instanceof ServerLevel server && entity instanceof LivingEntity living
                && living.tickCount % 10 == 0) {
            dev.groundzero.content.ordnance.Toxins.expose(server, living, 0, 60, dev.groundzero.content.ordnance.Toxins.GAS);
        }
    }

    // --- buckets ----------------------------------------------------------------------------------------------------------
    @Override
    public ItemStack pickupBlock(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state) {
        ChemFluid gas = fluid();
        if (state.getValue(VOLUME) < MAX || gas == null) {
            return ItemStack.EMPTY;
        }
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return new ItemStack(gas.bucket().get());
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }
}
