package dev.groundzero.content.machine;

import org.jspecify.annotations.Nullable;

import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import dev.groundzero.content.multiblock.Multiblocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Buckets and canisters on machines. Fluids are meant to move through pipes; by hand there are only two shortcuts:
 * right-click with a full container pours it into the input tank that takes it, and sneak-right-click with an empty
 * one fills it from a product tank.
 */
public final class MachineFluidInteraction {
    private MachineFluidInteraction() {}

    /** The machine a click on this block reaches: itself, a tall machine's base, or a formed multiblock's controller. */
    public static @Nullable MachineBlockEntity machineAt(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof MachineBlock) {
            return level.getBlockEntity(TallMachineBlock.base(pos, state)) instanceof MachineBlockEntity m ? m : null;
        }
        if (state.hasProperty(MultiblockPartBlock.FORMED) && state.getValue(MultiblockPartBlock.FORMED)) {
            MultiblockController c = Multiblocks.formedControllerOf(level, pos);
            return c != null && level.getBlockEntity(c.controllerPos()) instanceof MachineBlockEntity m ? m : null;
        }
        return null;
    }

    public static boolean isFluidContainer(Player player, InteractionHand hand) {
        return ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) != null;
    }

    /** Pours a held container in (or, sneaking, fills it from a product tank). Server side; true if fluid moved. */
    public static boolean use(MachineBlockEntity machine, Level level, BlockPos pos, Player player, InteractionHand hand) {
        // One container at a time: a stack of canisters would otherwise split the fluid between them.
        ResourceHandler<FluidResource> item = ItemAccess.forPlayerInteraction(player, hand).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (item == null || machine.tanks().isEmpty()) {
            return false;
        }
        boolean drawOut = player.isSecondaryUseActive();
        try (Transaction tx = Transaction.openRoot()) {
            int moved = 0;
            if (drawOut) {
                for (MachineTank tank : machine.tanks()) {
                    if (!tank.isInput() && !tank.isEmpty()) {
                        moved = ResourceHandlerUtil.move(tank, item, r -> true, Integer.MAX_VALUE, tx);
                        if (moved > 0) {
                            break;
                        }
                    }
                }
            } else {
                moved = ResourceHandlerUtil.move(item, machine.fluidHandler(), r -> true, Integer.MAX_VALUE, tx);
            }
            if (moved <= 0) {
                return false;
            }
            tx.commit();
        }
        level.playSound(null, pos, drawOut ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    /**
     * Sneaking with an item in hand normally skips the block's use and lets the item act (a bucket would pour into the
     * world). For a fluid container on a machine, use the block instead so sneak-right-click can fill it.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!player.isSecondaryUseActive() || stack.isEmpty() || !isFluidContainer(player, event.getHand())) {
            return;
        }
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        MachineBlockEntity machine = machineAt(level, event.getPos(), state);
        if (machine != null && !machine.tanks().isEmpty() || state.getBlock() instanceof dev.groundzero.content.fluid.FluidTankBlock
                || state.getBlock() instanceof dev.groundzero.content.fluid.CreativeFluidTankBlock) {
            event.setUseBlock(TriState.TRUE);
            event.setUseItem(TriState.FALSE);
        }
    }
}
