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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fusion Fuel Synthesizer (DESIGN.md §8): lithium-6 dust and deuterium from its gas tank make lithium deuteride
 * pellets; 120 FE/t. Slots: 0 input, 1 output.
 */
public class FusionFuelSynthesizerBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 120;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FusionFuelSynthesizerBlockEntity(BlockPos pos, BlockState state) {
        super(GBBlockEntities.FUSION_FUEL_SYNTHESIZER.get(), pos, state, ProcessingKind.FUSION_SYNTHESIS, 1, false, 1, 60_000, 600,
                java.util.List.of(MachineTank.Spec.input(8_000)));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.FUSION_FUEL_SYNTHESIZER.get(), containerId, inventory, this, MachineLayouts.FUSION_FUEL_SYNTHESIZER, this.data);
    }

    /** The viewport glows and frosts over only while the chamber runs. */
    @Override
    public java.util.Set<String> hiddenBones() {
        boolean active = getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE);
        return active ? java.util.Set.of() : java.util.Set.of("frost", "core");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GBSounds.MACHINE_FUSION_FUEL_SYNTHESIZER.get();
    }
}
