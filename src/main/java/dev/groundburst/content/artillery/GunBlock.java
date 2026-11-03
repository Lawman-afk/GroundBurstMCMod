package dev.groundburst.content.artillery;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A crewed gun (DESIGN.md §20.1): right-click it with its shells to load them, or with anything else to man it. Its
 * model (GeckoLib) is drawn by its block entity; it faces where its barrel rests (away from whoever places it).
 */
public class GunBlock extends BaseEntityBlock {
    @Override
    protected com.mojang.serialization.MapCodec<? extends GunBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Map<Direction, VoxelShape> shapes;
    private final Supplier<? extends BlockEntityType<? extends GunBlockEntity>> type;
    private final BiFunction<BlockPos, BlockState, ? extends GunBlockEntity> factory;

    /** {@code shape}: its footprint for a north-facing gun. */
    public GunBlock(Properties properties, VoxelShape shape, Supplier<? extends BlockEntityType<? extends GunBlockEntity>> type,
            BiFunction<BlockPos, BlockState, ? extends GunBlockEntity> factory) {
        super(properties);
        this.shapes = Shapes.rotateHorizontal(shape);
        this.type = type;
        this.factory = factory;
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return this.factory.apply(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != this.type.get() || level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((GunBlockEntity) be).serverTick((ServerLevel) lvl);
    }

    /** With its shells in hand: load them (as many as its rack takes). Anything else: man it. */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!(stack.getItem() instanceof ShellItem)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof GunBlockEntity gun) {
            if (gun.slotFor(stack) < 0) {
                player.displayClientMessage(Component.translatable(GunBlockEntity.T_WRONG_SHELL), true);
                return net.minecraft.world.ItemInteractionResult.CONSUME;
            }
            int before = stack.getCount();
            ItemStack left = gun.load(stack);
            if (left.getCount() < before) {
                if (!player.getAbilities().instabuild) {
                    player.setItemInHand(hand, left);
                }
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 0.8f, 0.8f);
                return net.minecraft.world.ItemInteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable(GunBlockEntity.T_RACK_FULL), true);
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }
        return net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof GunBlockEntity gun && player instanceof ServerPlayer serverPlayer && !player.isPassenger()) {
            gun.man(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
