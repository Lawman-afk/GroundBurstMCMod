package dev.groundzero.content.fluid;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Creative fluid tank (DESIGN.md §6.4): looks like a single fluid tank in a violet creative frame and never runs dry
 * (see {@link CreativeFluidTankBlockEntity}). Pour a bucket or canister in to set its fluid (a different fluid replaces
 * the old one), sneak-right-click with an empty container to fill it, sneak-right-click with an empty hand to clear it.
 * It does not join other tanks.
 */
public class CreativeFluidTankBlock extends Block implements EntityBlock {
    public static final String STATUS = GZLang.add(GroundZero.MODID + ".creative_fluid_tank.status", "Creative Fluid Tank: endless %s");
    public static final String CLEARED = GZLang.add(GroundZero.MODID + ".creative_fluid_tank.cleared", "Creative Fluid Tank cleared");

    public CreativeFluidTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeFluidTankBlockEntity(pos, state);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, Direction direction) {
        return false;
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
        if (!(level.getBlockEntity(pos) instanceof CreativeFluidTankBlockEntity be)) {
            return InteractionResult.PASS;
        }
        ResourceHandler<FluidResource> item = ItemAccess.forPlayerInteraction(player, hand).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        CreativeFluidTankBlockEntity.Tank tank = be.tank();
        boolean drawOut = player.isSecondaryUseActive();
        if (!drawOut) {
            // Pouring in a different fluid swaps the tank over to it; the same fluid has nowhere to go (it is full).
            FluidResource held = item.size() > 0 ? item.getResource(0) : FluidResource.EMPTY;
            if (held.isEmpty() || held.equals(tank.getResource(0))) {
                return InteractionResult.CONSUME;
            }
            tank.clear();
        }
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
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CreativeFluidTankBlockEntity be) {
            if (player.isSecondaryUseActive()) {
                be.tank().clear();
                player.sendOverlayMessage(Component.translatable(CLEARED));
            } else {
                var f = be.tank().fluid();
                player.sendOverlayMessage(Component.translatable(STATUS, f.isEmpty() ? Component.translatable(FluidTankBlock.EMPTY) : f.getHoverName()));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
