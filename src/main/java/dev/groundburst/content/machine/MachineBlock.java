package dev.groundburst.content.machine;

import java.util.function.Supplier;


import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.groundburst.port.transfer.Capabilities;
import dev.groundburst.port.transfer.access.ItemAccess;

/**
 * A horizontal-facing machine block (front = the side facing the player when placed). GeckoLib-rendered machines
 * use an invisible block model; the block entity renderer draws them.
 */
public class MachineBlock extends BaseEntityBlock {
    

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type;
    private final boolean geckoRendered;
    /** Collision/outline shape per facing; shapes are authored for a north-facing machine. */
    private final java.util.Map<Direction, VoxelShape> shapes;

    public MachineBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type, boolean geckoRendered, VoxelShape shape) {
        super(properties);
        this.type = type;
        this.geckoRendered = geckoRendered;
        this.shapes = dev.groundburst.port.Shapes2.rotateHorizontal(shape);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return this.geckoRendered ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return this.type.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != this.type.get()) {
            return null;
        }
        if (level.isClientSide()) {
            return (lvl, pos, st, be) -> MachineClientHooks.clientTick.accept((MachineBlockEntity) be);
        }
        return (lvl, pos, st, be) -> ((MachineBlockEntity) be).serverTick((ServerLevel) lvl);
    }

    /** Right-click with a full bucket or canister pours it in; sneak-right-click with an empty one fills it from a product tank. */
    protected dev.groundburst.legacy.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return useFluidContainer(state, level, pos, player, hand);
    }

    /** Shared with formed multiblock parts, which pass the click on to their controller. */
    public static dev.groundburst.legacy.ItemInteractionResult useFluidContainer(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        MachineBlockEntity machine = MachineFluidInteraction.machineAt(level, pos, state);
        if (machine == null || machine.tanks().isEmpty() || !MachineFluidInteraction.isFluidContainer(player, hand)) {
            return dev.groundburst.legacy.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return dev.groundburst.legacy.ItemInteractionResult.SUCCESS;
        }
        if (MachineFluidInteraction.use(machine, level, pos, player, hand)) {
            return dev.groundburst.legacy.ItemInteractionResult.SUCCESS;
        }
        // Nothing to pour or fill: sneaking does nothing, a plain click opens the machine.
        return player.isSecondaryUseActive() ? dev.groundburst.legacy.ItemInteractionResult.CONSUME : dev.groundburst.legacy.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && ((Object) level.getBlockEntity(pos)) instanceof MachineBlockEntity machine && ((Object) player) instanceof ServerPlayer serverPlayer) {
            net.minecraftforge.network.NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) serverPlayer, machine, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public net.minecraft.world.InteractionResult use(net.minecraft.world.level.block.state.BlockState state_, net.minecraft.world.level.Level level_, net.minecraft.core.BlockPos pos_, net.minecraft.world.entity.player.Player player_, net.minecraft.world.InteractionHand hand_, net.minecraft.world.phys.BlockHitResult hit_) {
        dev.groundburst.legacy.ItemInteractionResult item_ = this.useItemOn(player_.getItemInHand(hand_), state_, level_, pos_, player_, hand_, hit_);
        if (item_ != dev.groundburst.legacy.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return item_.result();
        }
        return hand_ == net.minecraft.world.InteractionHand.MAIN_HAND ? this.useWithoutItem(state_, level_, pos_, player_, hit_)
                : net.minecraft.world.InteractionResult.PASS;
    }
}
