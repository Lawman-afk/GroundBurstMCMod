package dev.groundburst.content.fluid;

import java.text.NumberFormat;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Fluid tank block (DESIGN.md §6.4): steel-framed glass. Blocks join into larger tanks ({@link FluidTankStructure});
 * the six connection properties say which neighbours belong to the same tank, so the frame only runs round the
 * outside of the whole box. Right-click with a full bucket or canister pours it in, sneak-right-click with an empty
 * one fills it, an empty hand shows what the tank holds.
 */
public class FluidTankBlock extends Block implements EntityBlock {
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.ROOT);
    public static final String STATUS = GBLang.add(GroundBurst.MODID + ".fluid_tank.status", "Fluid Tank %sx%sx%s: %s / %s mB %s");
    public static final String EMPTY = GBLang.add(GroundBurst.MODID + ".fluid_tank.empty", "(empty)");

    public FluidTankBlock(Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any();
        for (BooleanProperty p : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.PROPERTY_BY_DIRECTION.values().toArray(new BooleanProperty[0]));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidTankBlockEntity(pos, state);
    }

    // The block entity exists only after placement finishes, so joining up happens on the next tick.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FluidTankStructure.reform(level, pos);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, Direction direction) {
        return neighbour.is(this) && state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction));
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof FluidTankBlockEntity be)) {
            return InteractionResult.PASS;
        }
        ResourceHandler<FluidResource> item = ItemAccess.forPlayerInteraction(player, hand).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        ResourceHandler<FluidResource> tank = be.handler();
        boolean drawOut = player.isSecondaryUseActive();
        try (Transaction tx = Transaction.openRoot()) {
            int moved = drawOut ? ResourceHandlerUtil.move(tank, item, r -> true, Integer.MAX_VALUE, tx)
                    : ResourceHandlerUtil.move(item, tank, r -> true, Integer.MAX_VALUE, tx);
            if (moved <= 0) {
                return InteractionResult.CONSUME;
            }
            tx.commit();
        }
        level.playSound(null, pos, drawOut ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FluidTankBlockEntity be) {
            FluidTankBlockEntity c = be.controllerEntity();
            var f = c.tank().fluid();
            player.sendOverlayMessage(Component.translatable(STATUS, c.width(), c.width(), c.height(), NUM.format(f.getAmount()),
                    NUM.format(c.tank().capacity()), f.isEmpty() ? Component.translatable(EMPTY) : f.getHoverName()));
        }
        return InteractionResult.SUCCESS;
    }
}
