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

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> CHEMICAL_PLANT = machine("chemical_plant", () -> MachineLayouts.CHEMICAL_PLANT);

    private GZMenus() {}

    @SuppressWarnings("unchecked")
    private static DeferredHolder<MenuType<?>, MenuType<MachineMenu>> machine(String name, Supplier<MachineLayout> layout) {
        DeferredHolder<MenuType<?>, MenuType<MachineMenu>>[] self = new DeferredHolder[1];
        self[0] = MENUS.register(name, () -> IMenuTypeExtension.create((id, inv, buf) -> MachineMenu.client(self[0].get(), id, inv, buf, layout.get())));
        return self[0];
    }
}
