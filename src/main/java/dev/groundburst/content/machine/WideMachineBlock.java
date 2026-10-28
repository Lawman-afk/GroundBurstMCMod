package dev.groundburst.content.machine;

import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A machine two blocks wide (the Ordnance Assembly Bench, DESIGN.md §10). The main half holds the block entity and is
 * what the renderer draws from; the side half, to the right of the front as you face it, forwards use, capabilities
 * and breaking to it, like a bed. It cannot be turned with the Wrench (break it and place it again).
 */
public class WideMachineBlock extends MachineBlock {
    public enum Part implements StringRepresentable {
        MAIN, SIDE;

        @Override
        public String getSerializedName() {
            return this == MAIN ? "main" : "side";
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private final Map<Direction, VoxelShape> sideShapes;

    /** Shapes are authored for a machine facing north (its side half to the west). */
    public WideMachineBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type,
            boolean geckoRendered, VoxelShape mainShape, VoxelShape sideShape) {
        super(properties, type, geckoRendered, mainShape);
        this.sideShapes = Shapes.rotateHorizontal(sideShape);
        registerDefaultState(defaultBlockState().setValue(PART, Part.MAIN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    /** Where the side half is, from the main half, for a machine facing {@code facing} (to the right of its front). */
    public static Direction sideward(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** The main (block entity) position for either half. */
    public static BlockPos base(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.SIDE ? pos.relative(sideward(state.getValue(FACING)).getOpposite()) : pos;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        BlockPos side = context.getClickedPos().relative(sideward(state.getValue(FACING)));
        if (!context.getLevel().getBlockState(side).canBeReplaced(context) || !context.getLevel().getWorldBorder().isWithinBounds(side)) {
            return null;
        }
        return state.setValue(PART, Part.MAIN);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.relative(sideward(state.getValue(FACING))), state.setValue(PART, Part.SIDE), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Part part = state.getValue(PART);
        Direction toPartner = part == Part.MAIN ? sideward(state.getValue(FACING)) : sideward(state.getValue(FACING)).getOpposite();
        if (direction == toPartner) {
            boolean partner = neighbourState.is(this) && neighbourState.getValue(PART) != part
                    && neighbourState.getValue(FACING) == state.getValue(FACING);
            if (!partner) {
                return Blocks.AIR.defaultBlockState();
            }
            if (part == Part.SIDE) {
                return state.setValue(ACTIVE, neighbourState.getValue(ACTIVE));
            }
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Breaking the side half in creative or with the wrong tool: remove the main half (which holds the loot table)
        // without drops. Otherwise the main half breaks through updateShape and drops normally.
        if (!level.isClientSide() && (player.preventsBlockDrops() || !player.hasCorrectToolForDrops(state)) && state.getValue(PART) == Part.SIDE) {
            BlockPos main = base(pos, state);
            BlockState mainState = level.getBlockState(main);
            if (mainState.is(this) && mainState.getValue(PART) == Part.MAIN) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, main, Block.getId(mainState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? super.newBlockEntity(pos, state) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos base = base(pos, state);
        return super.useWithoutItem(level.getBlockState(base), level, base, player, hit);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART) == Part.SIDE ? this.sideShapes.get(state.getValue(FACING)) : super.getShape(state, level, pos, context);
    }
}
