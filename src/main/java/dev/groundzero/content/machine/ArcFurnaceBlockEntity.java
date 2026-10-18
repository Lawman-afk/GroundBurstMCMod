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
 * Arc Furnace (DESIGN.md §8): alloys and high-temperature smelting (steel, tungsten, carbide, magnets, cobalt);
 * 80 FE/t. Two blocks tall. Slots: 0-1 inputs, 2 product, 3 slag/byproduct.
 */
public class ArcFurnaceBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 80;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ArcFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.ARC_FURNACE.get(), pos, state, ProcessingKind.ARC_SMELTING, 2, 2, 40_000, 400);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.ARC_FURNACE.get(), containerId, inventory, this, MachineLayouts.ARC_FURNACE, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_ARC_FURNACE.get();
    }
}
