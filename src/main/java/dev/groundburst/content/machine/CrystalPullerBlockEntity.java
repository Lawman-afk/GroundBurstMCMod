package dev.groundburst.content.machine;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBMenus;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Crystal Puller (DESIGN.md §8.1): grows silicon crystals by the Czochralski method. Polysilicon melts in a quartz
 * crucible under argon and a seed crystal is drawn slowly up out of the melt, turning, a single crystal growing on it:
 * a minute a boule, 160 FE/t. Two blocks tall. Slots: 0-1 the polysilicon and the crucible, 2 the boule; one input tank,
 * for argon.
 *
 * <p>The crystal shows in the pull chamber's window, rising as the run goes on: its progress reaches clients in
 * {@link #GROWTH_STEPS} steps a run.
 */
public class CrystalPullerBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 160;
    private static final int GROWTH_STEPS = 24;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int syncedGrowth = -1;

    public CrystalPullerBlockEntity(BlockPos pos, BlockState state) {
        super(GBBlockEntities.CRYSTAL_PULLER.get(), pos, state, ProcessingKind.CRYSTAL_PULLING, 2, false, 1, 80_000, 800,
                List.of(MachineTank.Spec.input(8_000)));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.CRYSTAL_PULLER.get(), containerId, inventory, this, MachineLayouts.CRYSTAL_PULLER, this.data);
    }

    @Override
    public void serverTick(ServerLevel level) {
        super.serverTick(level);
        int step = (int) (progressFraction() * GROWTH_STEPS);
        if (step != this.syncedGrowth) {
            this.syncedGrowth = step;
            level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The crystal, drawn up from the melt: as long as the run is done. */
    @Override
    public Map<String, float[]> boneScales() {
        return Map.of("crystal", new float[] {1f, Math.max(0.02f, progressFraction()), 1f});
    }

    /** The melt glows in the viewport only while the heater is on; no crystal shows between runs. */
    @Override
    public Set<String> hiddenBones() {
        boolean active = getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE);
        if (progressFraction() <= 0f) {
            return active ? Set.of("crystal") : Set.of("crystal", "melt_glow");
        }
        return active ? Set.of() : Set.of("melt_glow");
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
        return GBSounds.MACHINE_CRYSTAL_PULLER.get();
    }
}
