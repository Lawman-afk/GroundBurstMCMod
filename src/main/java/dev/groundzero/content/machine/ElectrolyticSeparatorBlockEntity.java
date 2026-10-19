package dev.groundzero.content.machine;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.content.recipe.ProcessingKind;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_ELECTROLYTIC_SEPARATOR.get();
    }
}
