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
 * Exotic Matter Fabricator (DESIGN.md §8): stabilised exotic matter, containment cells and singularity seeds;
 * 400 FE/t. Two blocks tall. Slots: 0-2 inputs, 3 output.
 */
public class ExoticMatterFabricatorBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 400;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ExoticMatterFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.EXOTIC_MATTER_FABRICATOR.get(), pos, state, ProcessingKind.EXOTIC_FABRICATING, 3, false, 1, 200_000, 2_000);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.EXOTIC_MATTER_FABRICATOR.get(), containerId, inventory, this, MachineLayouts.EXOTIC_MATTER_FABRICATOR, this.data);
    }

    /** The sparks round the shard only show while the cage is energised. */
    @Override
    public java.util.Set<String> hiddenBones() {
        boolean active = getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE);
        return active ? java.util.Set.of() : java.util.Set.of("sparks");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_EXOTIC_MATTER_FABRICATOR.get();
    }
}
