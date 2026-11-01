package dev.groundburst.content.machine;

import java.util.List;
import java.util.Set;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBMenus;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Clean Room (DESIGN.md §8.1): prints chips on silicon wafers. A wafer is coated with photoresist, the circuit's pattern
 * exposed into it under yellow light, etched with hydrofluoric acid and doped with phosphorus: four Microchips, moulded
 * in plastic. Two wafers bonded over an oxide grown in liquid oxygen (silicon-on-insulator) give two Rad-Hard
 * Microchips, sealed in a ceramic package. 320 FE/t. A 3x2x2 multiblock run from this controller; it works only while
 * formed, and connects only through its ports. Slots: 0-2 inputs (the wafers, the phosphorus, the package), 3 output;
 * three input tanks (photoresist, hydrofluoric acid, liquid oxygen, in any order).
 */
public class CleanRoomBlockEntity extends ProcessingBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int ENERGY_PER_TICK = 320;
    private static final int RECHECK_INTERVAL = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CleanRoomBlockEntity(BlockPos pos, BlockState state) {
        super(GBBlockEntities.CLEAN_ROOM.get(), pos, state, ProcessingKind.LITHOGRAPHY, 3, false, 1, 160_000, 1_600,
                List.of(MachineTank.Spec.input(8_000), MachineTank.Spec.input(8_000), MachineTank.Spec.input(8_000)));
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GBMultiblocks.CLEAN_ROOM;
    }

    @Override
    public BlockPos controllerPos() {
        return this.worldPosition;
    }

    @Override
    public Direction controllerFacing() {
        return getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
    }

    /** The controller connects to nothing: items, fluids and power all go through the structure's ports. */
    @Override
    public boolean faceOpen(SideConfig.Face face) {
        return false;
    }

    @Override
    public boolean hasSideConfig() {
        return false;
    }

    @Override
    public boolean isFormed() {
        return getBlockState().hasProperty(MultiblockPartBlock.FORMED) && getBlockState().getValue(MultiblockPartBlock.FORMED);
    }

    @Override
    public void revalidate() {
        if (this.level == null || this.level.isClientSide() || isRemoved()) {
            return;
        }
        boolean complete = definition().check(this.level, this.worldPosition, controllerFacing()).isEmpty();
        if (complete != isFormed()) {
            MultiblockController.setPartsFormed(this.level, this, complete);
            this.level.setBlock(this.worldPosition, getBlockState().setValue(MultiblockPartBlock.FORMED, complete), Block.UPDATE_ALL);
            setChanged();
        }
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

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide() && state.hasProperty(MultiblockPartBlock.FORMED) && state.getValue(MultiblockPartBlock.FORMED)) {
            MultiblockController.setPartsFormed(this.level, this, false);
        }
    }

    // --- rendering ----------------------------------------------------------------------------------------------------------
    /** Unformed, the controller is an ordinary block; the structure's model only draws once formed. */
    @Override
    public Set<String> hiddenBones() {
        return isFormed() ? Set.of() : Set.of("frame");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.CLEAN_ROOM.get(), containerId, inventory, this, MachineLayouts.CLEAN_ROOM, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GBSounds.MACHINE_CLEAN_ROOM.get();
    }
}
