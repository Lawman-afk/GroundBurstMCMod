package dev.groundzero.compat.jei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.groundzero.GroundZero;
import dev.groundzero.client.recipe.ClientRecipes;
import dev.groundzero.client.screen.MachineScreen;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineLayout;
import dev.groundzero.content.machine.MachineText;
import dev.groundzero.content.part.GZParts;
import dev.groundzero.content.recipe.GZRecipes;
import dev.groundzero.content.recipe.ProcessingKind;
import dev.groundzero.content.recipe.ProcessingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * JEI integration. Crafting and smelting recipes show through JEI's vanilla support; this adds one category per
 * processing machine, the machines as catalysts, clickable progress arrows, info pages, and keeps JEI's overlay
 * clear of the side-configuration tab. Loaded by JEI only, so JEI stays optional.
 */
@JeiPlugin
public final class GZJeiPlugin implements IModPlugin {
    private static final Map<ProcessingKind, IRecipeHolderType<ProcessingRecipe>> TYPES = new EnumMap<>(ProcessingKind.class);

    /** The machine that runs each kind of recipe (kinds without a machine yet are left out). */
    private static Map<ProcessingKind, ItemLike> machines() {
        Map<ProcessingKind, ItemLike> m = new EnumMap<>(ProcessingKind.class);
        m.put(ProcessingKind.CRUSHING, GZMachines.ORE_CRUSHER.item().get());
        m.put(ProcessingKind.ARC_SMELTING, GZMachines.ARC_FURNACE.item().get());
        m.put(ProcessingKind.PRESSING, GZMachines.INDUSTRIAL_PRESS.item().get());
        m.put(ProcessingKind.ELECTROLYSIS, GZMachines.ELECTROLYTIC_SEPARATOR.item().get());
        m.put(ProcessingKind.CRYO_CONDENSING, GZMachines.CRYOGENIC_CONDENSER.item().get());
        m.put(ProcessingKind.CHEMISTRY, GZMachines.CHEMICAL_PLANT.item().get());
        return m;
    }

    private static IRecipeHolderType<ProcessingRecipe> type(ProcessingKind kind) {
        return TYPES.computeIfAbsent(kind, k -> IRecipeHolderType.create(GZRecipes.type(k)));
    }

    @Override
    public Identifier getPluginUid() {
        return GroundZero.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        machines().forEach((kind, machine) -> registration.addRecipeCategories(new ProcessingCategory(type(kind), machine, gui)));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (ProcessingKind kind : machines().keySet()) {
            registration.addRecipes(type(kind), new ArrayList<>(ClientRecipes.get(kind)));
        }
        registration.addIngredientInfo(List.of(GZParts.PLATE_DIE.get(), GZParts.ROD_DIE.get(), GZParts.WIRE_DIE.get(), GZParts.CANISTER_DIE.get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_DIE));
        registration.addIngredientInfo(List.of(GZMachines.CHEMICAL_PLANT.item().get(), GZMultiblocks.MACHINE_CASING.item().get(),
                        GZMultiblocks.REACTOR_GLASS.item().get(), GZMultiblocks.ITEM_PORT.item().get(), GZMultiblocks.HOLO_PROJECTOR.get(),
                        GZMultiblocks.CHEMICAL_PLANT_SPAWNER.get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_CHEMICAL_PLANT));
        registration.addIngredientInfo(List.of(GZParts.OXYGEN_MEMBRANE.get(), GZParts.NITROGEN_MEMBRANE.get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_MEMBRANE));
        registration.addIngredientInfo(List.of(GZMachines.COMBUSTION_GENERATOR.item().get(), GZMachines.ORE_CRUSHER.item().get(),
                        GZMachines.ARC_FURNACE.item().get(), GZMachines.INDUSTRIAL_PRESS.item().get(),
                        GZMachines.ELECTROLYTIC_SEPARATOR.item().get(), GZMachines.CRYOGENIC_CONDENSER.item().get(), GZMachines.CHEMICAL_PLANT.item().get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_SIDES));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        machines().forEach((kind, machine) -> registration.addCraftingStation(type(kind), machine));
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(MachineScreen.class, new IGuiContainerHandler<MachineScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(MachineScreen screen) {
                return screen.extraAreas();
            }

            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(MachineScreen screen, double mouseX, double mouseY) {
                MachineLayout.Rect p = screen.getMenu().layout().progress();
                ProcessingKind kind = kindOf(screen.getMenu().machine());
                if (p == null || kind == null) {
                    return List.of();
                }
                return List.of(IGuiClickableArea.createBasic(p.x(), p.y(), p.w(), p.h(), type(kind)));
            }
        });
    }

    private static ProcessingKind kindOf(MachineBlockEntity machine) {
        return machine instanceof dev.groundzero.content.machine.ProcessingBlockEntity processing ? processing.kind() : null;
    }
}
