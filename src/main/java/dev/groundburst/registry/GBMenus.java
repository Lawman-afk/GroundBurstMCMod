package dev.groundburst.registry;

import java.util.function.Supplier;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.MachineLayout;
import dev.groundburst.content.machine.MachineLayouts;
import dev.groundburst.content.machine.MachineMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menu types. Every machine uses {@link MachineMenu}; the type carries its layout for the client side. */
public final class GBMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, GroundBurst.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> ORE_CRUSHER = machine("ore_crusher", () -> MachineLayouts.ORE_CRUSHER);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> COMBUSTION_GENERATOR = machine("combustion_generator", () -> MachineLayouts.COMBUSTION_GENERATOR);

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> ARC_FURNACE = machine("arc_furnace", () -> MachineLayouts.ARC_FURNACE);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> INDUSTRIAL_PRESS = machine("industrial_press", () -> MachineLayouts.INDUSTRIAL_PRESS);

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> ELECTROLYTIC_SEPARATOR = machine("electrolytic_separator", () -> MachineLayouts.ELECTROLYTIC_SEPARATOR);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> CRYOGENIC_CONDENSER = machine("cryogenic_condenser", () -> MachineLayouts.CRYOGENIC_CONDENSER);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> CRYSTAL_PULLER = machine("crystal_puller", () -> MachineLayouts.CRYSTAL_PULLER);
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

    /** The Launch Silo's controller screen (DESIGN.md §17.2). */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> LAUNCH_SILO = machine("launch_silo", () -> MachineLayouts.LAUNCH_SILO);
    /** The Missile Silo's controller screen (DESIGN.md §17.10): with its DOORS button. */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> MISSILE_SILO = machine("missile_silo", () -> MachineLayouts.MISSILE_SILO);
    /** The Cruise Missile Launcher's (DESIGN.md §17.11): its two boxes' missiles. */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> CRUISE_LAUNCHER = machine("cruise_launcher", () -> MachineLayouts.CRUISE_LAUNCHER);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> ASSEMBLY_BENCH = machine("assembly_bench", () -> MachineLayouts.ASSEMBLY_BENCH);
    /** The ICBM Assembly Hall's controller screen (DESIGN.md §15.2). */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> STRATEGIC_ASSEMBLY = machine("strategic_assembly_hall",
            () -> MachineLayouts.STRATEGIC_ASSEMBLY);
    /** The Interceptor Battery's screen (DESIGN.md §18.2). */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> INTERCEPTOR_BATTERY = machine("interceptor_battery",
            () -> MachineLayouts.INTERCEPTOR_BATTERY);
    /** The Interceptor Complex's screen (DESIGN.md §18.5). */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> INTERCEPTOR_COMPLEX = machine("interceptor_complex",
            () -> MachineLayouts.INTERCEPTOR_COMPLEX);
    /** The Special Weapons Assembler's controller screen (DESIGN.md §16.15). */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> SPECIAL_WEAPONS = machine("special_weapons_assembler",
            () -> MachineLayouts.SPECIAL_WEAPONS);
    /** The four missile assemblers' controller screen (DESIGN.md §10.4), shared: titled with the controller's name. */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> MISSILE_ASSEMBLER = machine("missile_assembler",
            () -> MachineLayouts.MISSILE_ASSEMBLER);

    /** The superweapons' controller screen (DESIGN.md §19.5), shared: titled with the controller's name. */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> SUPERWEAPON = machine("superweapon", () -> MachineLayouts.SUPERWEAPON);
    /** The ammunition factories' screens (DESIGN.md §19.5): the Arms Dealer's four parts, the War Factory's five. */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> ARMS_DEALER = machine("arms_dealer", () -> MachineLayouts.ARMS_DEALER);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> WAR_FACTORY = machine("war_factory", () -> MachineLayouts.WAR_FACTORY);

    private GBMenus() {}

    @SuppressWarnings("unchecked")
    private static DeferredHolder<MenuType<?>, MenuType<MachineMenu>> machine(String name, Supplier<MachineLayout> layout) {
        DeferredHolder<MenuType<?>, MenuType<MachineMenu>>[] self = new DeferredHolder[1];
        self[0] = MENUS.register(name, () -> IMenuTypeExtension.create((id, inv, buf) -> MachineMenu.client(self[0].get(), id, inv, buf, layout.get())));
        return self[0];
    }
}
