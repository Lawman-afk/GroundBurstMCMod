package dev.groundzero.content.machine;

import java.util.Set;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.recipe.ProcessingKind;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Particle Accelerator (DESIGN.md §8, §23.3): a 9x9x3 ring run from this controller. Fills containment cells with
 * antimatter slowly, at 2 000 FE/t. The controller connects to nothing: power comes in through the ring's Energy Port,
 * containment cells go in and antimatter cells come out through its Item Port.
 * Slots: 0 containment cell, 1 antimatter cell.
 */
public class ParticleAcceleratorBlockEntity extends ProcessingBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int ENERGY_PER_TICK = 2_000;
    private static final int RECHECK_INTERVAL = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ParticleAcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.PARTICLE_ACCELERATOR.get(), pos, state, ProcessingKind.ACCELERATING, 1, false, 1, 400_000, 8_192);
    }

    @Override
    public MultiblockDefinition definition() {
        return GZMultiblocks.PARTICLE_ACCELERATOR;
    }

    @Override
    public BlockPos controllerPos() {
        return this.worldPosition;
    }

    @Override
    public Direction controllerFacing() {
        return StructureMachines.facing(getBlockState());
    }

    @Override
    public boolean isFormed() {
        return StructureMachines.isFormed(getBlockState());
    }

    @Override
    public void revalidate() {
        StructureMachines.revalidate(this, this);
    }

    /** The controller connects to nothing: items, fluids and power all go through the structure's ports. */
    @Override
    public boolean faceOpen(SideConfig.Face face) {
        return false;
    }

    /** Nothing to configure: the side tab is hidden. */
    @Override
    public boolean hasSideConfig() {
        return false;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        StructureMachines.unformOnRemove(this, this, state);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            return false;
        }
        return super.tickMachine(level);
    }

    /** Unformed, the controller is an ordinary block; the bunch only races round the ring while it runs. */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        boolean active = getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE);
        return active ? Set.of() : Set.of("bunch");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.PARTICLE_ACCELERATOR.get(), containerId, inventory, this, MachineLayouts.PARTICLE_ACCELERATOR, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_PARTICLE_ACCELERATOR.get();
    }
}
