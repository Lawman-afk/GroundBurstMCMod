package dev.groundzero.content.power;

import java.util.List;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.machine.AnimatedMachine;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineLayouts;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.MachineTank;
import dev.groundzero.content.machine.MachineText;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Diesel Generator (DESIGN.md §7): burns liquid fuel piped into its tank for 180 FE/t and pushes power into
 * adjacent cables and machines. Fuels are matched by common fluid tags, so other mods' diesel works too. Burning
 * pauses while the buffer is full, so no fuel is wasted.
 */
public class DieselGeneratorBlockEntity extends MachineBlockEntity implements AnimatedMachine {
    public static final int RATE = 180;
    public static final int CAPACITY = 40_000;
    public static final int OUTPUT = 720;
    /** Fuel is drawn in sips of this many mB. */
    public static final int SIP = 10;
    public static final int DATA_BURN = 0, DATA_BURN_TOTAL = 1, DATA_FUEL_VALUE = 2;

    /** FE per mB for each accepted fuel (by common tag). */
    public record Fuel(TagKey<Fluid> tag, int fePerMb) {}

    public static final List<Fuel> FUELS = List.of(
            new Fuel(GZFluids.LIQUID_ROCKET_FUEL.commonTag(), 140),
            new Fuel(GZFluids.SYNTHETIC_FUEL.commonTag(), 90),
            new Fuel(common("diesel"), 90),
            new Fuel(common("biodiesel"), 80));

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int burn;
    private int burnTotal;
    private int fuelValue;

    public DieselGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.DIESEL_GENERATOR.get(), pos, state, CAPACITY, 0, OUTPUT, 0, new int[0], new int[0],
                List.of(MachineTank.Spec.input(8_000)));
    }

    private static TagKey<Fluid> common(String path) {
        return TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", path));
    }

    /** FE per mB of this fluid as diesel fuel, or 0 if it does not burn here. */
    public static int fuelValue(Fluid fluid) {
        for (Fuel fuel : FUELS) {
            if (fluid.is(fuel.tag())) {
                return fuel.fePerMb();
            }
        }
        return 0;
    }

    @Override
    protected boolean acceptsFluid(int tank, FluidResource resource) {
        return fuelValue(resource.getFluid()) > 0;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        boolean running = false;
        if (this.energy.stored() >= this.energy.capacity()) {
            this.status = MachineStatus.BUFFER_FULL;
        } else {
            if (this.burn <= 0) {
                sip();
            }
            if (this.burn > 0) {
                this.burn--;
                this.energy.generate(RATE);
                this.status = MachineStatus.RUNNING;
                running = true;
            } else {
                this.status = MachineStatus.NO_FUEL;
            }
        }
        PowerUtil.pushToNeighbours(level, this.worldPosition, this.energy, OUTPUT, side -> this.sides.energy(face(side)));
        return running;
    }

    /** Draws one sip of fuel: SIP mB worth fePerMb each, burnt at RATE. */
    private void sip() {
        MachineTank tank = this.tanks.get(0);
        int value = tank.isEmpty() ? 0 : fuelValue(tank.fluid().getFluid());
        if (value <= 0 || tank.amount() < SIP) {
            return;
        }
        tank.drainInternal(SIP);
        this.fuelValue = value;
        this.burn = SIP * value / RATE;
        this.burnTotal = this.burn;
        setChanged();
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        int cap = Math.max(1, menu.capacity());
        boolean burning = menu.extra(DATA_BURN) > 0;
        return List.of(Component.translatable(MachineText.OUTPUT, burning ? RATE : 0),
                Component.translatable(MachineText.STORED, (int) ((long) menu.energy() * 100 / cap)),
                Component.translatable(menu.status().translationKey()));
    }

    @Override
    protected int extraDataCount() {
        return 3;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_BURN -> this.burn;
            case DATA_BURN_TOTAL -> this.burnTotal;
            case DATA_FUEL_VALUE -> this.fuelValue;
            default -> 0;
        };
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.DIESEL_GENERATOR.get(), containerId, inventory, this, MachineLayouts.DIESEL_GENERATOR, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_DIESEL_GENERATOR.get();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("burn", this.burn);
        output.putInt("burn_total", this.burnTotal);
        output.putInt("fuel_value", this.fuelValue);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.burn = input.getIntOr("burn", 0);
        this.burnTotal = input.getIntOr("burn_total", 0);
        this.fuelValue = input.getIntOr("fuel_value", 0);
    }
}
