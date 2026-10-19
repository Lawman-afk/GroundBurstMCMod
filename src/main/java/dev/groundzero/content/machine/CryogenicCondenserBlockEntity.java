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
 * Cryogenic Condenser (DESIGN.md §8): liquefies air into canisters; the separation membrane in its catalyst slot
 * picks oxygen or nitrogen; 100 FE/t. Two blocks tall. Slots: 0 canister, 1 membrane, 2 output.
 */
public class CryogenicCondenserBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 100;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CryogenicCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.CRYOGENIC_CONDENSER.get(), pos, state, ProcessingKind.CRYO_CONDENSING, 0, true, 0, 50_000, 500,
                java.util.List.of(MachineTank.Spec.output(8_000)));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.CRYOGENIC_CONDENSER.get(), containerId, inventory, this, MachineLayouts.CRYOGENIC_CONDENSER, this.data);
    }

    /** The frost crust on the coils and the vapour puffs only show while the condenser is running. */
    @Override
    public java.util.Set<String> hiddenBones() {
        boolean active = getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE);
        return active ? java.util.Set.of() : java.util.Set.of("frost", "puff0", "puff1", "puff2");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_CRYOGENIC_CONDENSER.get();
    }
}
