package dev.groundzero.registry;

import java.util.function.Supplier;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.MachineLayout;
import dev.groundzero.content.machine.MachineLayouts;
import dev.groundzero.content.machine.MachineMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menu types. Every machine uses {@link MachineMenu}; the type carries its layout for the client side. */
public final class GZMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, GroundZero.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> ORE_CRUSHER = machine("ore_crusher", () -> MachineLayouts.ORE_CRUSHER);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> COMBUSTION_GENERATOR = machine("combustion_generator", () -> MachineLayouts.COMBUSTION_GENERATOR);

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> ARC_FURNACE = machine("arc_furnace", () -> MachineLayouts.ARC_FURNACE);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> INDUSTRIAL_PRESS = machine("industrial_press", () -> MachineLayouts.INDUSTRIAL_PRESS);

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> ELECTROLYTIC_SEPARATOR = machine("electrolytic_separator", () -> MachineLayouts.ELECTROLYTIC_SEPARATOR);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> CRYOGENIC_CONDENSER = machine("cryogenic_condenser", () -> MachineLayouts.CRYOGENIC_CONDENSER);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> WATER_PUMP = machine("water_pump", () -> MachineLayouts.WATER_PUMP);

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> CIRCUIT_FABRICATOR = machine("circuit_fabricator", () -> MachineLayouts.CIRCUIT_FABRICATOR);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> GAS_CENTRIFUGE = machine("gas_centrifuge", () -> MachineLayouts.GAS_CENTRIFUGE);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> FUSION_FUEL_SYNTHESIZER = machine("fusion_fuel_synthesizer", () -> MachineLayouts.FUSION_FUEL_SYNTHESIZER);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> CANNING_MACHINE = machine("canning_machine", () -> MachineLayouts.CANNING_MACHINE);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> EXOTIC_MATTER_FABRICATOR = machine("exotic_matter_fabricator", () -> MachineLayouts.EXOTIC_MATTER_FABRICATOR);

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> DIESEL_GENERATOR = machine("diesel_generator", () -> MachineLayouts.DIESEL_GENERATOR);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> RTG = machine("rtg", () -> MachineLayouts.RTG);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> CAPACITOR_BANK = machine("capacitor_bank", () -> MachineLayouts.CAPACITOR_BANK);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> BATTERY_CHARGER = machine("battery_charger", () -> MachineLayouts.BATTERY_CHARGER);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> DECONTAMINATION_SHOWER = machine("decontamination_shower", () -> MachineLayouts.DECONTAMINATION_SHOWER);

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> FISSION_REACTOR = machine("fission_reactor", () -> MachineLayouts.FISSION_REACTOR);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> FUSION_REACTOR = machine("fusion_reactor", () -> MachineLayouts.FUSION_REACTOR);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> SINGULARITY_REACTOR = machine("singularity_reactor", () -> MachineLayouts.SINGULARITY_REACTOR);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> PARTICLE_ACCELERATOR = machine("particle_accelerator", () -> MachineLayouts.PARTICLE_ACCELERATOR);

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> CHEMICAL_PLANT = machine("chemical_plant", () -> MachineLayouts.CHEMICAL_PLANT);

    private GZMenus() {}

    @SuppressWarnings("unchecked")
    private static DeferredHolder<MenuType<?>, MenuType<MachineMenu>> machine(String name, Supplier<MachineLayout> layout) {
        DeferredHolder<MenuType<?>, MenuType<MachineMenu>>[] self = new DeferredHolder[1];
        self[0] = MENUS.register(name, () -> IMenuTypeExtension.create((id, inv, buf) -> MachineMenu.client(self[0].get(), id, inv, buf, layout.get())));
        return self[0];
    }
}
