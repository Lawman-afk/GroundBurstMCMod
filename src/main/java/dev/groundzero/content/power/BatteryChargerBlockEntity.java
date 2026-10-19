package dev.groundzero.content.power;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.AnimatedMachine;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineLayouts;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZLang;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Battery Charger (DESIGN.md §8): charges whatever sits in its cradle (any item with the FE item capability, so other
 * mods' batteries and tools too) from the power its cables bring in, then moves the full item to the output slot.
 * Slots: 0 charging, 1 charged.
 */
public class BatteryChargerBlockEntity extends MachineBlockEntity implements AnimatedMachine {
    public static final int RATE = 2_048;
    public static final int CAPACITY = 50_000;
    /** Sync slots shaped like a processing machine's (progress, max) so the GUI arrow fills with the item's charge. */
    public static final int DATA_ITEM_PERCENT = 0, DATA_HUNDRED = 1;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int itemPercent = -1;

    public BatteryChargerBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.BATTERY_CHARGER.get(), pos, state, CAPACITY, RATE, 0, 2, new int[] {0}, new int[] {1});
    }

    private static @Nullable EnergyHandler energyOf(ItemAccess access) {
        return access.getCapability(Capabilities.Energy.ITEM);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0 && energyOf(ItemAccess.forStack(stack.copy())) != null;
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        ItemStack stack = this.inventory.getItem(0);
        if (stack.isEmpty()) {
            this.itemPercent = -1;
            this.status = MachineStatus.IDLE;
            return false;
        }
        EnergyHandler item = energyOf(ItemAccess.forHandlerIndex(this.inventory, 0));
        if (item == null) {
            this.status = MachineStatus.NO_RECIPE;
            return false;
        }
        long cap = item.getCapacityAsLong();
        this.itemPercent = cap <= 0 ? 0 : (int) (item.getAmountAsLong() * 100 / cap);
        if (item.getAmountAsLong() >= cap) {
            // Full: move it on so the next item can charge (if the output is free).
            if (this.inventory.getItem(1).isEmpty()) {
                this.inventory.setItem(1, stack.copy());
                this.inventory.setItem(0, ItemStack.EMPTY);
            }
            this.status = this.inventory.getItem(1).isEmpty() ? MachineStatus.IDLE : MachineStatus.OUTPUT_FULL;
            return false;
        }
        int offer = Math.min(RATE, this.energy.stored());
        if (offer <= 0) {
            this.status = MachineStatus.NO_POWER;
            return false;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int accepted = item.insert(offer, tx);
            if (accepted > 0) {
                tx.commit();
                this.energy.consume(accepted);
            }
            this.status = accepted > 0 ? MachineStatus.RUNNING : MachineStatus.NO_RECIPE;
            return accepted > 0;
        }
    }

    /** The charge LEDs only chase while charging. */
    @Override
    public java.util.Set<String> hiddenBones() {
        boolean active = getBlockState().hasProperty(dev.groundzero.content.machine.MachineBlock.ACTIVE)
                && getBlockState().getValue(dev.groundzero.content.machine.MachineBlock.ACTIVE);
        return active ? java.util.Set.of() : java.util.Set.of("led0", "led1", "led2", "led3", "led4");
    }

    @Override
    protected int extraDataCount() {
        return 2;
    }

    @Override
    protected int getExtraData(int index) {
        return index == DATA_ITEM_PERCENT ? this.itemPercent : index == DATA_HUNDRED ? 100 : 0;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.BATTERY_CHARGER.get(), containerId, inventory, this, MachineLayouts.BATTERY_CHARGER, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_BATTERY_CHARGER.get();
    }
}
