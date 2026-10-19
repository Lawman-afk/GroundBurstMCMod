package dev.groundzero.content.machine;

import java.util.List;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Water Pump (DESIGN.md §6.4): stands on a water source block and, while powered, draws water from it without using
 * it up (like an infinite spring) and pushes it out of its two side outlets into pipes, tanks and machines. Its
 * bottom face is the intake and never connects.
 */
public class WaterPumpBlockEntity extends MachineBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 20, RATE = 50;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public WaterPumpBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.WATER_PUMP.get(), pos, state, 10_000, 200, 0, 0, new int[0], new int[0],
                List.of(MachineTank.Spec.output(8_000)));
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        // A pump always pushes what it holds: it is the source end of a pipe run.
        pushFluids(level);
        if (!level.getFluidState(this.worldPosition.below()).isSourceOfType(Fluids.WATER)) {
            this.status = MachineStatus.NO_WATER;
            return false;
        }
        MachineTank tank = tank(0);
        if (tank.space(Fluids.WATER) < RATE) {
            this.status = MachineStatus.OUTPUT_FULL;
            return false;
        }
        if (!this.energy.consume(ENERGY_PER_TICK)) {
            this.status = MachineStatus.NO_POWER;
            return false;
        }
        tank.fillInternal(new FluidStack(Fluids.WATER, RATE));
        this.status = MachineStatus.RUNNING;
        return true;
    }

    /** Water leaves only through the two flanged outlets on the model's sides. */
    @Override
    public boolean fluidFaceAllowed(SideConfig.Face face) {
        return face == SideConfig.Face.LEFT || face == SideConfig.Face.RIGHT;
    }

    /** The bottom is the intake pipe in the water. */
    @Override
    public boolean faceOpen(SideConfig.Face face) {
        return face != SideConfig.Face.BOTTOM;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.WATER_PUMP.get(), containerId, inventory, this, MachineLayouts.WATER_PUMP, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_WATER_PUMP.get();
    }
}
