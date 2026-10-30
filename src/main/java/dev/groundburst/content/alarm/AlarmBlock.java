package dev.groundburst.content.alarm;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.machine.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An alarm device's block (DESIGN.md §18.6): mounted on a wall (turned four ways) or standing on a floor, or on any face
 * ({@link Six}). Right-clicked, it opens its settings; a redstone signal into it sets it off (or, into an Alarm Panel,
 * raises its channel); a receiver, a monitor and a relay give redstone out. No power needed.
 */
public class AlarmBlock extends Block implements EntityBlock {
    public static final BooleanProperty ACTIVE = MachineBlock.ACTIVE;
    public static final EnumProperty<Direction> HORIZONTAL = BlockStateProperties.HORIZONTAL_FACING;
    /** Client hook: opens a device's settings screen (set by the client). */
    public static Consumer<BlockPos> screenHook = p -> {};

    protected final AlarmDevice device;

    public AlarmBlock(Properties properties, AlarmDevice device) {
        super(properties);
        this.device = device;
        registerDefaultState(defaults(this.stateDefinition.any()));
    }

    public AlarmDevice device() {
        return this.device;
    }

    protected BlockState defaults(BlockState state) {
        return state.setValue(HORIZONTAL, Direction.NORTH).setValue(ACTIVE, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL, ACTIVE);
    }

    /** The way its front faces. */
    public Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL);
    }

    /** On a wall it faces out from the wall; on a floor or a ceiling, toward whoever places it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = this.device.mount == AlarmDevice.Mount.WALL && face.getAxis().isHorizontal() ? face
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(HORIZONTAL, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.device.shape(facing(state));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return this.device == AlarmDevice.SIREN || this.device == AlarmDevice.BEACON ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.getBlockEntity(pos) instanceof AlarmBlockEntity be) {
            be.setPowered(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof AlarmBlockEntity be) {
            be.setPowered(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (this.device.givesRedstone()) {
            level.updateNeighborsAt(pos, this);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return this.device.givesRedstone();
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof AlarmBlockEntity be ? be.output() : 0;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            screenHook.accept(pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** A Warning Beacon takes a dye: red, orange or yellow (amber), blue, green or white sets its colour. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        DyeColor dye = stack.get(DataComponents.DYE);
        int colour = dye == null || this.device != AlarmDevice.BEACON ? -1 : switch (dye) {
            case RED -> 0;
            case ORANGE, YELLOW -> 1;
            case BLUE, LIGHT_BLUE, CYAN -> 2;
            case GREEN, LIME -> 3;
            case WHITE -> 4;
            default -> -1;
        };
        if (colour < 0) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AlarmBlockEntity be) {
            be.setOption(this.device.option("colour"), colour);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GBAlarms.type(this.device).get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != GBAlarms.type(this.device).get()) {
            return null;
        }
        return level.isClientSide()
                ? (l, p, s, be) -> ((AlarmBlockEntity) be).clientTick()
                : (l, p, s, be) -> ((AlarmBlockEntity) be).serverTick((ServerLevel) l);
    }

    /** A device on any face (the Warning Beacon, the Emergency Light): it faces out from the face it was placed on. */
    public static class Six extends AlarmBlock {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

        public Six(Properties properties, AlarmDevice device) {
            super(properties, device);
        }

        @Override
        protected BlockState defaults(BlockState state) {
            return state.setValue(FACING, Direction.UP).setValue(ACTIVE, false);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, ACTIVE);
        }

        @Override
        public Direction facing(BlockState state) {
            return state.getValue(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getClickedFace());
        }
    }

    /** What an Emergency Light shows: dark, white, or red (flashing) in alarm. */
    public enum Lamp implements StringRepresentable {
        OFF, WHITE, RED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** The Emergency Light: a caged bulkhead lamp on any face, its lamp state a block state (its model and its light). */
    public static class Light extends Six {
        public static final EnumProperty<Lamp> LAMP = EnumProperty.create("lamp", Lamp.class);

        public Light(Properties properties, AlarmDevice device) {
            super(properties, device);
        }

        @Override
        protected BlockState defaults(BlockState state) {
            return super.defaults(state).setValue(LAMP, Lamp.OFF);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, ACTIVE, LAMP);
        }
    }
}
