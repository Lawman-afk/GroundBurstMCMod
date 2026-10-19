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
import dev.groundzero.content.machine.MachineText;
import dev.groundzero.content.machine.SideConfig;
import dev.groundzero.registry.GZDataComponents;
import dev.groundzero.registry.GZLang;
import dev.groundzero.registry.GZMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.LimitingEnergyHandler;

/**
 * Capacitor Bank (DESIGN.md §7), three tiers. Stores FE: every face takes power in except the front, which only
 * gives it out (pushing into whatever cable or machine it faces). The wrench turns it, the side tab can switch faces
 * off. It keeps its charge when picked up, shows its charge on a gauge, and gives a comparator signal.
 */
public class CapacitorBankBlockEntity extends MachineBlockEntity implements AnimatedMachine {
    public static final String NET = GZLang.add(GroundZero.MODID + ".gui.net", "NET %s FE/t");

    /** Capacity and per-tick transfer (matching the cable tiers) of each bank. */
    public enum Tier {
        BASIC("capacitor_bank", "Capacitor Bank", 100_000, 1_024),
        ADVANCED("advanced_capacitor_bank", "Advanced Capacitor Bank", 1_000_000, 8_192),
        SUPERCONDUCTING("superconducting_capacitor_bank", "Superconducting Capacitor Bank", 10_000_000, 32_768);

        private final String id;
        private final String english;
        private final int capacity;
        private final int transfer;

        Tier(String id, String english, int capacity, int transfer) {
            this.id = id;
            this.english = english;
            this.capacity = capacity;
            this.transfer = transfer;
        }

        public String id() {
            return id;
        }

        public String english() {
            return english;
        }

        public int capacity() {
            return capacity;
        }

        public int transfer() {
            return transfer;
        }
    }

    /** Gauge steps shown on the model (bones bar0..bar7). */
    public static final int BARS = 8;
    public static final int DATA_NET = 0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Tier tier;
    private final EnergyHandler inputView;
    private final EnergyHandler outputView;
    private int lastStored = -1;
    private int net;
    private int syncedBars = -1;

    public CapacitorBankBlockEntity(BlockEntityType<?> type, Tier tier, BlockPos pos, BlockState state) {
        super(type, pos, state, tier.capacity(), tier.transfer(), tier.transfer(), 0, new int[0], new int[0]);
        this.tier = tier;
        this.inputView = new LimitingEnergyHandler(this.energy, tier.transfer(), 0);
        this.outputView = new LimitingEnergyHandler(this.energy, 0, tier.transfer());
    }

    public Tier tier() {
        return this.tier;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    /** The front only gives power out; every other face only takes it in. */
    @Override
    public @Nullable EnergyHandler energyFor(@Nullable Direction side) {
        EnergyHandler base = super.energyFor(side);
        if (side == null || base == null) {
            return base;
        }
        return face(side) == SideConfig.Face.FRONT ? this.outputView : this.inputView;
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        PowerUtil.pushToNeighbours(level, this.worldPosition, this.energy, this.tier.transfer(),
                side -> face(side) == SideConfig.Face.FRONT && this.sides.energy(SideConfig.Face.FRONT));
        int stored = this.energy.stored();
        this.net = this.lastStored < 0 ? 0 : stored - this.lastStored;
        this.lastStored = stored;
        this.status = stored >= this.energy.capacity() ? MachineStatus.BUFFER_FULL : stored == 0 ? MachineStatus.IDLE : MachineStatus.RUNNING;
        int bars = bars(stored);
        if (bars != this.syncedBars) {
            this.syncedBars = bars;
            level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(this.worldPosition, getBlockState().getBlock());
        }
        return this.net != 0;
    }

    private int bars(int stored) {
        return stored <= 0 ? 0 : 1 + (int) ((long) (stored - 1) * BARS / this.energy.capacity());
    }

    /** Comparator signal 0-15 from the charge. */
    public int signal() {
        int stored = this.energy.stored();
        return stored <= 0 ? 0 : 1 + (int) ((long) stored * 14 / this.energy.capacity());
    }

    /** Gauge bars above the charge level are not drawn. */
    @Override
    public java.util.Set<String> hiddenBones() {
        int lit = bars(this.energy.stored());
        java.util.Set<String> hidden = new java.util.HashSet<>();
        for (int i = lit; i < BARS; i++) {
            hidden.add("bar" + i);
        }
        return hidden;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- keeping the charge in the item -------------------------------------------------------------------------------
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.energy.stored() > 0) {
            components.set(GZDataComponents.ENERGY.get(), this.energy.stored());
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        int stored = components.getOrDefault(GZDataComponents.ENERGY.get(), 0);
        if (stored > 0) {
            this.energy.generate(stored);
        }
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        int cap = Math.max(1, menu.capacity());
        int net = menu.extra(DATA_NET);
        return List.of(Component.translatable(MachineText.STORED, (int) ((long) menu.energy() * 100 / cap)),
                Component.translatable(NET, (net > 0 ? "+" : "") + net));
    }

    @Override
    protected int extraDataCount() {
        return 1;
    }

    @Override
    protected int getExtraData(int index) {
        return index == DATA_NET ? Math.max(-32_000, Math.min(32_000, this.net)) : 0;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.CAPACITOR_BANK.get(), containerId, inventory, this, MachineLayouts.CAPACITOR_BANK, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
