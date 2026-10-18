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
 * Industrial Press (DESIGN.md §8): plates, rods, wire and canisters, shaped by the die in its catalyst slot (dies
 * are not consumed); 40 FE/t. Two blocks tall. Slots: 0 input, 1 die, 2 output.
 */
public class IndustrialPressBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public IndustrialPressBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.INDUSTRIAL_PRESS.get(), pos, state, ProcessingKind.PRESSING, 1, true, 1, 20_000, 200);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.INDUSTRIAL_PRESS.get(), containerId, inventory, this, MachineLayouts.INDUSTRIAL_PRESS, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_INDUSTRIAL_PRESS.get();
    }
}
