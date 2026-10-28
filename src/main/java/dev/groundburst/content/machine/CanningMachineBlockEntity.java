package dev.groundburst.content.machine;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBMenus;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Canning Machine (DESIGN.md §8): fills empty canisters and gas canisters with 1000 mB from its supply tank, and
 * empties filled canisters into its catch tank, by "canning" recipes (one each way for every fluid that has a
 * canister). Slots: 0 canister in, 1 canister out. Tanks: 0 supply (pipes and buckets fill it), 1 catch (pipes,
 * buckets and auto-eject drain it). 20 FE/t, two seconds a canister.
 */
public class CanningMachineBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 20;
    public static final int TIME = 40;
    public static final int TANK_CAPACITY = 16_000;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean showsCanister;

    public CanningMachineBlockEntity(BlockPos pos, BlockState state) {
        super(GBBlockEntities.CANNING_MACHINE.get(), pos, state, ProcessingKind.CANNING, 1, false, 1, 10_000, 200,
                java.util.List.of(MachineTank.Spec.input(TANK_CAPACITY), MachineTank.Spec.output(TANK_CAPACITY)));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.CANNING_MACHINE.get(), containerId, inventory, this, MachineLayouts.CANNING_MACHINE, this.data);
    }

    /** The glass reservoir shows the supply tank (see tools/assets/canning_machine.py). */
    private static final java.util.List<TankRender> RESERVOIR = java.util.List.of(new TankRender(0, -6.6f, 2.01f, -2.1f, -1.4f, 12.9f, 6.1f));

    @Override
    public java.util.List<TankRender> tankRenders() {
        return RESERVOIR;
    }

    // --- the model shows a canister in the cradle only while one is loaded ----------------------------------------------
    @Override
    protected void onSlotChanged(int slot) {
        super.onSlotChanged(slot);
        boolean loaded = loaded();
        if (loaded != this.showsCanister && this.level != null && !this.level.isClientSide()) {
            this.showsCanister = loaded;
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private boolean loaded() {
        return !this.inventory.getItem(0).isEmpty() || !this.inventory.getItem(1).isEmpty();
    }

    @Override
    public java.util.Set<String> hiddenBones() {
        return loaded() ? java.util.Set.of() : java.util.Set.of("canister");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GBSounds.MACHINE_CANNING_MACHINE.get();
    }
}
