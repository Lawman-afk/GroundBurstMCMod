package dev.groundzero.content.machine;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.content.recipe.ProcessingKind;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electrolytic Separator (DESIGN.md §8): splits water to collect deuterium into gas canisters; 60 FE/t. Takes
 * water from pipes, buckets in its bucket slot, or a bucket used on the block. Slots: 0 canister, 1 bucket (feeds
 * the tank), 2 output.
 */
public class ElectrolyticSeparatorBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 60;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ElectrolyticSeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.ELECTROLYTIC_SEPARATOR.get(), pos, state, ProcessingKind.ELECTROLYSIS, 2, false, 1, 30_000, 300, MachineTank.Spec.water(16_000, 1));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.ELECTROLYTIC_SEPARATOR.get(), containerId, inventory, this, MachineLayouts.ELECTROLYTIC_SEPARATOR, this.data);
    }

    /** The cell interior inside the glass (see tools/assets/electrolytic_separator.py). */
    private static final TankRender CELL = new TankRender(-6.1f, 2.01f, -5.1f, 2.1f, 12.9f, 5.1f);
    /** Fill steps clients are told about: enough for a smooth-looking level without a packet per millibucket. */
    private static final int SYNC_STEPS = 48;
    private int lastSyncedStep = -1;
    private int lastSyncedFluid = -1;

    @Override
    public TankRender tankRender() {
        return CELL;
    }

    @Override
    protected void onTankChanged() {
        super.onTankChanged();
        if (this.level != null && !this.level.isClientSide() && this.tank != null) {
            int step = (int) ((long) this.tank.amount() * SYNC_STEPS / this.tank.capacity());
            if (step != this.lastSyncedStep || this.tank.fluidId() != this.lastSyncedFluid) {
                this.lastSyncedStep = step;
                this.lastSyncedFluid = this.tank.fluidId();
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Bubbles only rise while the electrodes are under water. */
    @Override
    public java.util.Set<String> hiddenBones() {
        boolean submerged = this.tank != null && this.tank.amount() * 100L / this.tank.capacity() >= 55;
        return submerged ? java.util.Set.of()
                : java.util.Set.of("bubble0", "bubble1", "bubble2", "bubble3", "bubble4", "bubble5");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_ELECTROLYTIC_SEPARATOR.get();
    }
}
