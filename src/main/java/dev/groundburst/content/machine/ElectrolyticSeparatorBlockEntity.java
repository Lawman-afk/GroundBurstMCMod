package dev.groundburst.content.machine;

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
        super(GBBlockEntities.ELECTROLYTIC_SEPARATOR.get(), pos, state, ProcessingKind.ELECTROLYSIS, 0, true, 0, 30_000, 300,
                java.util.List.of(MachineTank.Spec.water(16_000), MachineTank.Spec.output(8_000)));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.ELECTROLYTIC_SEPARATOR.get(), containerId, inventory, this, MachineLayouts.ELECTROLYTIC_SEPARATOR, this.data);
    }

    /** The cell interior inside the glass shows the water tank (see tools/assets/electrolytic_separator.py). */
    private static final java.util.List<TankRender> CELL = java.util.List.of(new TankRender(0, -6.1f, 2.01f, -5.1f, 2.1f, 12.9f, 5.1f));

    @Override
    public java.util.List<TankRender> tankRenders() {
        return CELL;
    }

    /** Bubbles only rise while the electrodes are under water. */
    @Override
    public java.util.Set<String> hiddenBones() {
        boolean submerged = tank(0).amount() * 100L / tank(0).capacity() >= 55;
        return submerged ? java.util.Set.of()
                : java.util.Set.of("bubble0", "bubble1", "bubble2", "bubble3", "bubble4", "bubble5");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GBSounds.MACHINE_ELECTROLYTIC_SEPARATOR.get();
    }
}
