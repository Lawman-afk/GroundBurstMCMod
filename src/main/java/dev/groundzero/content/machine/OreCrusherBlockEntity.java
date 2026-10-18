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

/** Ore Crusher (DESIGN.md §8): ore/raw/ingot -> dusts with byproduct chances; 20 FE/t. Slots: 0 input, 1-2 outputs. */
public class OreCrusherBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    /** DESIGN.md §8: the crusher draws 20 FE/t while working. */
    public static final int ENERGY_PER_TICK = 20;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public OreCrusherBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.ORE_CRUSHER.get(), pos, state, ProcessingKind.CRUSHING, 1, 2, 10_000, 200);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.ORE_CRUSHER.get(), containerId, inventory, this, MachineLayouts.ORE_CRUSHER, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_ORE_CRUSHER.get();
    }
}
