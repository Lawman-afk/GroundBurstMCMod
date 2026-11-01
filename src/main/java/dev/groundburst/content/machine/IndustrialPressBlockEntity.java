package dev.groundburst.content.machine;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBMenus;
import dev.groundburst.registry.GBSounds;
import dev.groundburst.content.part.GBParts;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Industrial Press (DESIGN.md §8): plates, rods, wire and canisters, shaped by the die in its catalyst slot (dies
 * are not consumed); 40 FE/t. Two blocks tall. Slots: 0 input, 1 die, 2 output.
 */
public class IndustrialPressBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public IndustrialPressBlockEntity(BlockPos pos, BlockState state) {
        super(GBBlockEntities.INDUSTRIAL_PRESS.get(), pos, state, ProcessingKind.PRESSING, 1, true, 1, 20_000, 200);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.INDUSTRIAL_PRESS.get(), containerId, inventory, this, MachineLayouts.INDUSTRIAL_PRESS, this.data);
    }

    private static final java.util.List<String> DIE_BONES = java.util.List.of("die_plate", "die_rod", "die_wire", "die_canister", "die_saw");

    /** The die in the catalyst slot shows on the press bed; others stay hidden. */
    @Override
    public java.util.Set<String> hiddenBones() {
        ItemStack die = this.inventory.getItem(catalystSlot());
        String shown = die.is(GBParts.PLATE_DIE.get()) ? "die_plate" : die.is(GBParts.ROD_DIE.get()) ? "die_rod"
                : die.is(GBParts.WIRE_DIE.get()) ? "die_wire" : die.is(GBParts.CANISTER_DIE.get()) ? "die_canister"
                : die.is(GBParts.DIAMOND_WIRE_SAW.get()) ? "die_saw" : "";
        java.util.Set<String> hidden = new java.util.HashSet<>(DIE_BONES);
        hidden.remove(shown);
        return hidden;
    }

    @Override
    protected void onSlotChanged(int slot) {
        super.onSlotChanged(slot);
        if ((slot == catalystSlot() || slot < 0) && this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
        return GBSounds.MACHINE_INDUSTRIAL_PRESS.get();
    }
}
