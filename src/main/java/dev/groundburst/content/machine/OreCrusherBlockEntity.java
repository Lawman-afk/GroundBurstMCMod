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

/** Ore Crusher (DESIGN.md §8): ore/raw/ingot -> dusts with byproduct chances; 20 FE/t. Slots: 0 input, 1-2 outputs. */
public class OreCrusherBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    /** DESIGN.md §8: the crusher draws 20 FE/t while working. */
    public static final int ENERGY_PER_TICK = 20;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public OreCrusherBlockEntity(BlockPos pos, BlockState state) {
        super(GBBlockEntities.ORE_CRUSHER.get(), pos, state, ProcessingKind.CRUSHING, 1, 2, 10_000, 200);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.ORE_CRUSHER.get(), containerId, inventory, this, MachineLayouts.ORE_CRUSHER, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GBSounds.MACHINE_ORE_CRUSHER.get();
    }
}
