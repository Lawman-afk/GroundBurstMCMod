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
 * Circuit Fabricator (DESIGN.md §8): circuits, sensor arrays and superconducting coils; 40 FE/t. A coolant tank
 * takes liquid nitrogen for the coils. Slots: 0-3 inputs, 4 output.
 */
public class CircuitFabricatorBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CircuitFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.CIRCUIT_FABRICATOR.get(), pos, state, ProcessingKind.FABRICATING, 4, false, 1, 20_000, 200,
                java.util.List.of(MachineTank.Spec.input(4_000)));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.CIRCUIT_FABRICATOR.get(), containerId, inventory, this, MachineLayouts.CIRCUIT_FABRICATOR, this.data);
    }

    /** The chip in the arm's gripper only shows while it is placing chips. */
    @Override
    public java.util.Set<String> hiddenBones() {
        boolean active = getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE);
        return active ? java.util.Set.of() : java.util.Set.of("chip");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_CIRCUIT_FABRICATOR.get();
    }
}
