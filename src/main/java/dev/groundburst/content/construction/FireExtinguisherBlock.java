package dev.groundburst.content.construction;

import java.util.List;

import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Fire Extinguisher (DESIGN.md §22.5): a red cylinder, its label to the front, standing on the floor or hung on a wall
 * on its bracket (a steel strip, two straps round it), put up as a torch is: on the wall clicked, its back to it
 * ({@link #WALL}; it falls if the wall goes), else on the floor facing its placer. A click gives a burst from its horn
 * toward its front: a white cloud, and the fire in the blocks before it (and on anything burning there) put out.
 */
public class FireExtinguisherBlock extends DecorBlock {
    /** Hung on the wall behind it (the way it faces away from), not standing on the floor. */
    public static final BooleanProperty WALL = BooleanProperty.create("wall");
    private static final VoxelShape[] HUNG = Fittings.facing(new double[] {5.8, 2.4, 10.6, 10.2, 14.9, 16});

    public FireExtinguisherBlock(Properties properties) {
        super(properties, List.of(new double[] {5.8, 0, 5.8, 10.2, 11.9, 10.2}));
        registerDefaultState(defaultBlockState().setValue(WALL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WALL);
    }

    /** On the wall clicked, if it is sturdy there; else standing on the floor, facing its placer. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            BlockState hung = defaultBlockState().setValue(FACING, face).setValue(WALL, true);
            if (hung.canSurvive(context.getLevel(), context.getClickedPos())) {
                return hung;
            }
        }
        return super.getStateForPlacement(context).setValue(WALL, false);
    }

    /** Hung, it needs the wall behind it whole on that face. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!state.getValue(WALL)) {
            return true;
        }
        Direction front = state.getValue(FACING);
        BlockPos wall = pos.relative(front.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, front);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) { net.minecraft.world.level.LevelAccessor ticks = level; net.minecraft.util.RandomSource random = level.getRandom();
        if (state.getValue(WALL) && direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(WALL) ? HUNG[state.getValue(FACING).get2DDataValue()] : super.getShape(state, level, pos, context);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            spray(server, pos, state.getValue(FACING));
            level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** The burst: its cloud, its hiss, and the fires within a block of the block before it put out (on the ground and on whatever burns). */
    public static void spray(ServerLevel level, BlockPos pos, Direction front) {
        double x = pos.getX() + 0.5 + front.getStepX() * 0.5;
        double z = pos.getZ() + 0.5 + front.getStepZ() * 0.5;
        for (int i = 1; i <= 4; i++) {
            double r = 0.15 * i;
            level.sendParticles(ParticleTypes.CLOUD, x + front.getStepX() * 0.35 * i, pos.getY() + 0.55, z + front.getStepZ() * 0.35 * i,
                    6, r, r * 0.6, r, 0.02);
        }
        level.playSound(null, pos, GBSounds.EXTINGUISHER_SPRAY.get(), SoundSource.BLOCKS, 0.8f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        BlockPos ahead = pos.relative(front);
        for (BlockPos p : BlockPos.betweenClosed(ahead.offset(-1, -1, -1), ahead.offset(1, 1, 1))) {
            if (level.getBlockState(p).is(BlockTags.FIRE)) {
                level.removeBlock(p, false);
            }
        }
        for (Entity e : level.getEntities((Entity) null, new AABB(ahead).inflate(1.0), Entity::isOnFire)) {
            e.clearFire();
        }
    }
}
