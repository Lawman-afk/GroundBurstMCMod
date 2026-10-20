package dev.groundzero.content.machine;

import java.util.Set;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chemical Plant (DESIGN.md §6, §8): every chemistry step (acids, fuels, explosives, yellowcake, UF6, lithium-6);
 * 60 FE/t; a 2x2x2 multiblock run from this controller. Takes water through its tank. Works only while formed.
 * Slots: 0-2 inputs, 3 bucket (feeds the tank), 4-5 outputs.
 */
public class ChemicalPlantBlockEntity extends ProcessingBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int ENERGY_PER_TICK = 60;
    private static final int RECHECK_INTERVAL = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ChemicalPlantBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.CHEMICAL_PLANT.get(), pos, state, ProcessingKind.CHEMISTRY, 3, false, 2, 60_000, 600,
                java.util.List.of(MachineTank.Spec.water(16_000), MachineTank.Spec.input(8_000), MachineTank.Spec.input(8_000),
                        MachineTank.Spec.output(8_000)));
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GZMultiblocks.CHEMICAL_PLANT;
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

    /** Nothing to configure: the side tab is hidden. */
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
    public static final int TANK_WATER = 0, TANK_INPUT_A = 1, TANK_INPUT_B = 2, TANK_PRODUCT = 3;
    /**
     * Tank windows (see tools/assets/chemical_plant.py): the process tanks show water (left, controller side) and the
     * product (right); the two feed silos behind them show the chemical inputs.
     */
    private static final java.util.List<TankRender> WINDOWS = java.util.List.of(
            new TankRender(TANK_WATER, -1.9f, 3.5f, 1.7f, 1.9f, 25.5f, 2.8f),
            new TankRender(TANK_PRODUCT, 14.1f, 3.5f, 1.7f, 17.9f, 25.5f, 2.8f),
            new TankRender(TANK_INPUT_A, 4.15f, 3.5f, 19.9f, 5.85f, 17.5f, 20.3f),
            new TankRender(TANK_INPUT_B, 10.05f, 3.5f, 19.9f, 11.75f, 17.5f, 20.3f));

    @Override
    public java.util.List<TankRender> tankRenders() {
        return WINDOWS;
    }

    @Override
    public boolean showsTanks() {
        return isFormed();
    }

    /**
     * Unformed, the controller is an ordinary block; the structure model only draws once formed. Bubbles rise in a tank
     * only while the plant runs and the liquid reaches above them.
     */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        boolean active = getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE);
        Set<String> hidden = new java.util.HashSet<>();
        int[] shown = {TANK_WATER, TANK_PRODUCT};
        for (int t = 0; t < 2; t++) {
            MachineTank tank = tank(shown[t]);
            if (!active || tank.amount() * 100L / tank.capacity() < 70) {
                for (int i = 0; i < 4; i++) {
                    hidden.add("bubble" + t + "_" + i);
                }
            }
        }
        return hidden;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.CHEMICAL_PLANT.get(), containerId, inventory, this, MachineLayouts.CHEMICAL_PLANT, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_CHEMICAL_PLANT.get();
    }
}
