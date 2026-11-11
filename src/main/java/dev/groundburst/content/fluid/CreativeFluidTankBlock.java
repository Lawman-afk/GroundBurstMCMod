package dev.groundburst.content.fluid;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
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
import dev.groundburst.port.transfer.Capabilities;
import dev.groundburst.port.transfer.ResourceHandler;
import dev.groundburst.port.transfer.ResourceHandlerUtil;
import dev.groundburst.port.transfer.access.ItemAccess;
import dev.groundburst.port.transfer.fluid.FluidResource;
import dev.groundburst.port.transfer.transaction.Transaction;

/**
 * Creative fluid tank (DESIGN.md §6.4): looks like a single fluid tank in a violet creative frame and never runs dry
 * (see {@link CreativeFluidTankBlockEntity}). Pour a bucket or canister in to set its fluid (a different fluid replaces
 * the old one), sneak-right-click with an empty container to fill it, sneak-right-click with an empty hand to clear it.
 * It does not join other tanks.
 */
public class CreativeFluidTankBlock extends Block implements EntityBlock {
    public static final String STATUS = GBLang.add(GroundBurst.MODID + ".creative_fluid_tank.status", "Creative Fluid Tank: endless %s");
    public static final String CLEARED = GBLang.add(GroundBurst.MODID + ".creative_fluid_tank.cleared", "Creative Fluid Tank cleared");

    public CreativeFluidTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeFluidTankBlockEntity(pos, state);
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState neighbour, Direction direction) {
        return false;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level_, BlockPos pos_) {
        return true;
    }

    protected dev.groundburst.legacy.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (dev.groundburst.legacy.Caps.get(ItemAccess.forPlayerInteraction(player, hand), Capabilities.Fluid.ITEM) == null) {
            return dev.groundburst.legacy.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return dev.groundburst.legacy.ItemInteractionResult.SUCCESS;
        }
        if (!(((Object) level.getBlockEntity(pos)) instanceof CreativeFluidTankBlockEntity be)) {
            return dev.groundburst.legacy.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        ResourceHandler<FluidResource> item = dev.groundburst.legacy.Caps.get(ItemAccess.forPlayerInteraction(player, hand).oneByOne(), Capabilities.Fluid.ITEM);
        CreativeFluidTankBlockEntity.Tank tank = be.tank();
        boolean drawOut = player.isSecondaryUseActive();
        if (!drawOut) {
            // Pouring in a different fluid swaps the tank over to it; the same fluid has nowhere to go (it is full).
            FluidResource held = item.size() > 0 ? item.getResource(0) : FluidResource.EMPTY;
            if (held.isEmpty() || held.equals(tank.getResource(0))) {
                return dev.groundburst.legacy.ItemInteractionResult.CONSUME;
            }
            tank.clear();
        }
        try (Transaction tx = Transaction.openRoot()) {
            int moved = drawOut ? ResourceHandlerUtil.move(tank, item, r -> true, Integer.MAX_VALUE, tx)
                    : ResourceHandlerUtil.move(item, tank, r -> true, Integer.MAX_VALUE, tx);
            if (moved <= 0) {
                return dev.groundburst.legacy.ItemInteractionResult.CONSUME;
            }
            tx.commit();
        }
        level.playSound(null, pos, drawOut ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        return dev.groundburst.legacy.ItemInteractionResult.SUCCESS;
    }

    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && ((Object) level.getBlockEntity(pos)) instanceof CreativeFluidTankBlockEntity be) {
            if (player.isSecondaryUseActive()) {
                be.tank().clear();
                player.displayClientMessage(Component.translatable(CLEARED), true);
            } else {
                var f = be.tank().fluid();
                player.displayClientMessage(Component.translatable(STATUS, f.isEmpty() ? Component.translatable(FluidTankBlock.EMPTY) : f.getDisplayName()), true);
            }
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
