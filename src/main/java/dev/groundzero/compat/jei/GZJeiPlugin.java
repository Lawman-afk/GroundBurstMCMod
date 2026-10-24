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
        m.put(ProcessingKind.FABRICATING, GZMachines.CIRCUIT_FABRICATOR.item().get());
        m.put(ProcessingKind.CENTRIFUGING, GZMachines.GAS_CENTRIFUGE.item().get());
        m.put(ProcessingKind.FUSION_SYNTHESIS, GZMachines.FUSION_FUEL_SYNTHESIZER.item().get());
        m.put(ProcessingKind.EXOTIC_FABRICATING, GZMachines.EXOTIC_MATTER_FABRICATOR.item().get());
        m.put(ProcessingKind.ACCELERATING, GZMachines.PARTICLE_ACCELERATOR.item().get());
        m.put(ProcessingKind.CANNING, GZMachines.CANNING_MACHINE.item().get());
        return m;
    }

    private static IRecipeHolderType<ProcessingRecipe> type(ProcessingKind kind) {
        return TYPES.computeIfAbsent(kind, k -> IRecipeHolderType.create(GZRecipes.type(k)));
    }

    @Override
    public Identifier getPluginUid() {
        return GroundZero.id("jei_plugin");
    }

    /**
     * Missiles are told apart by their looks (DESIGN.md §10.4, §17.11): each warhead has a missile of its own, and a
     * Light or Medium one a cruise missile too, each with its own recipe (the Missile Assembly category, or the
     * Strategic Assembly category for the hall's).
     */
    @Override
    public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(dev.groundzero.content.assembly.GZAssembly.MISSILE.get(), (stack, context) -> {
            var spec = dev.groundzero.content.assembly.MissileItem.spec(stack);
            return spec != null ? spec.look() : null;
        });
    }

    /** Every warhead's missile in JEI's list: R on one shows how its assembler (or the hall) builds it. */
    @Override
    public void registerExtraIngredients(mezz.jei.api.registration.IExtraIngredientRegistration registration) {
        registration.addExtraItemStacks(MissileCategory.recipes().stream().map(MissileCategory::output).toList());
        registration.addExtraItemStacks(StrategicCategory.recipes().stream().map(StrategicCategory::output).toList());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        machines().forEach((kind, machine) -> registration.addRecipeCategories(new ProcessingCategory(type(kind), machine, gui)));
        registration.addRecipeCategories(new AssemblyCategory(gui));
        registration.addRecipeCategories(new MissileCategory(gui));
        registration.addRecipeCategories(new StrategicCategory(gui));
        registration.addRecipeCategories(new AmmoFactoryCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (ProcessingKind kind : machines().keySet()) {
            registration.addRecipes(type(kind), new ArrayList<>(ClientRecipes.get(kind)));
        }
        registration.addRecipes(AssemblyCategory.TYPE, AssemblyCategory.recipes());
        registration.addRecipes(MissileCategory.TYPE, MissileCategory.recipes());
        registration.addRecipes(StrategicCategory.TYPE, StrategicCategory.recipes());
        registration.addRecipes(AmmoFactoryCategory.TYPE, AmmoFactoryCategory.recipes());
        // The superweapons (DESIGN.md §19.5): how each is built and works.
        info(registration, dev.groundzero.content.superweapon.GZSuperweapons.INFO_SCUD_STORM, dev.groundzero.content.superweapon.GZSuperweapons.SCUD_STORM_CONTROLLER.item().get(), dev.groundzero.content.superweapon.GZSuperweapons.ADOBE_WALL.item().get(),
                dev.groundzero.content.superweapon.GZSuperweapons.TOXIN_TANK.item().get(), dev.groundzero.content.superweapon.GZSuperweapons.SCUD_ERECTOR.item().get(), dev.groundzero.content.superweapon.GZSuperweapons.SCUD_STORM_SPAWNER.get(), dev.groundzero.content.superweapon.GZSuperweapons.TOXIN_SCUD.get());
        info(registration, dev.groundzero.content.superweapon.GZSuperweapons.INFO_NUCLEAR_MISSILE, dev.groundzero.content.superweapon.GZSuperweapons.NUCLEAR_MISSILE_CONTROLLER.item().get(), dev.groundzero.content.superweapon.GZSuperweapons.FIELDSTONE_WALL.item().get(),
                dev.groundzero.content.superweapon.GZSuperweapons.MISSILE_ERECTOR.item().get(), dev.groundzero.content.superweapon.GZSuperweapons.NUCLEAR_MISSILE_SPAWNER.get(), dev.groundzero.content.superweapon.GZSuperweapons.NUCLEAR_MISSILE_ROUND.get());
        info(registration, dev.groundzero.content.superweapon.GZSuperweapons.INFO_TOXIN, dev.groundzero.content.superweapon.GZSuperweapons.TOXIN_SCUD.get());
        info(registration, dev.groundzero.content.superweapon.GZSuperweapons.INFO_ARMS_DEALER, dev.groundzero.content.superweapon.GZSuperweapons.ARMS_DEALER_CONTROLLER.item().get(), dev.groundzero.content.superweapon.GZSuperweapons.ARMS_DEALER_SPAWNER.get());
        info(registration, dev.groundzero.content.superweapon.GZSuperweapons.INFO_WAR_FACTORY, dev.groundzero.content.superweapon.GZSuperweapons.WAR_FACTORY_CONTROLLER.item().get(), dev.groundzero.content.superweapon.GZSuperweapons.WAR_FACTORY_SPAWNER.get());
        // The Cruise Missile Launcher and the motor that makes a cruise missile (slice 9c, DESIGN.md §17.11).
        info(registration, dev.groundzero.content.launch.GZLaunch.INFO_CRUISE_LAUNCHER, dev.groundzero.content.launch.GZLaunch.CRUISE_CONTROLLER.item().get(),
                dev.groundzero.content.launch.GZLaunch.LAUNCH_BOX.item().get(), dev.groundzero.content.launch.GZLaunch.CRUISE_SPAWNER.get());
        info(registration, dev.groundzero.content.launch.GZLaunch.INFO_CRUISE_ENGINE, dev.groundzero.content.assembly.GZAssembly.CRUISE_ENGINE.get());
        registration.addIngredientInfo(List.of(GZParts.PLATE_DIE.get(), GZParts.ROD_DIE.get(), GZParts.WIRE_DIE.get(), GZParts.CANISTER_DIE.get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_DIE));
        registration.addIngredientInfo(List.of(GZMachines.CHEMICAL_PLANT.item().get(), GZMultiblocks.MACHINE_CASING.item().get(),
                        GZMultiblocks.REACTOR_GLASS.item().get(), GZMultiblocks.ITEM_PORT.item().get(), GZMultiblocks.FLUID_PORT.item().get(), GZMultiblocks.HOLO_PROJECTOR.get(),
                        GZMultiblocks.CHEMICAL_PLANT_SPAWNER.get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_CHEMICAL_PLANT));
        registration.addIngredientInfo(List.of(dev.groundzero.content.fluid.GZFluids.FLUID_TANK.item().get().getDefaultInstance()),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_FLUID_TANK));
        registration.addIngredientInfo(List.of(dev.groundzero.content.fluid.GZFluids.CREATIVE_FLUID_TANK.item().get().getDefaultInstance()),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_CREATIVE_FLUID_TANK));
        registration.addIngredientInfo(List.of(GZMachines.WRENCH.get().getDefaultInstance()),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_WRENCH));
        registration.addIngredientInfo(List.of(GZMachines.WATER_PUMP.item().get().getDefaultInstance()),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_WATER_PUMP));
        registration.addIngredientInfo(dev.groundzero.content.fluid.GZFluids.pipes().stream().map(p -> p.item().get().getDefaultInstance()).toList(),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_FLUID_PIPE));
        registration.addIngredientInfo(dev.groundzero.content.pipe.GZPipes.itemPipes().stream().map(p -> p.item().get().getDefaultInstance()).toList(),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_ITEM_PIPE));
        registration.addIngredientInfo(dev.groundzero.content.fluid.GZFluids.all().stream()
                        .map(f -> f.bucket().get().getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_FLUIDS));
        registration.addIngredientInfo(List.of(GZParts.OXYGEN_MEMBRANE.get(), GZParts.NITROGEN_MEMBRANE.get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_MEMBRANE));
        registration.addIngredientInfo(List.of(GZMachines.COMBUSTION_GENERATOR.item().get(), GZMachines.ORE_CRUSHER.item().get(),
                        GZMachines.ARC_FURNACE.item().get(), GZMachines.INDUSTRIAL_PRESS.item().get(),
                        GZMachines.ELECTROLYTIC_SEPARATOR.item().get(), GZMachines.CRYOGENIC_CONDENSER.item().get(),
                        GZMachines.CIRCUIT_FABRICATOR.item().get(), GZMachines.GAS_CENTRIFUGE.item().get(),
                        GZMachines.FUSION_FUEL_SYNTHESIZER.item().get(), GZMachines.EXOTIC_MATTER_FABRICATOR.item().get(),
                        GZMachines.DIESEL_GENERATOR.item().get(), GZMachines.RTG.item().get(), GZMachines.BATTERY_CHARGER.item().get(),
                        GZMachines.DECONTAMINATION_SHOWER.item().get(), GZMachines.CANNING_MACHINE.item().get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_SIDES));
        info(registration, MachineText.INFO_FISSION_REACTOR, GZMachines.FISSION_REACTOR.item().get(), GZMultiblocks.LEAD_LINED_CASING.item().get(),
                GZMultiblocks.FUEL_CHANNEL.item().get(),
                GZMultiblocks.ENERGY_PORT.item().get(), GZMultiblocks.FISSION_REACTOR_SPAWNER.get(), dev.groundzero.content.nuclear.GZNuclear.URANIUM_FUEL_ROD.get(),
                dev.groundzero.content.nuclear.GZNuclear.DU_TARGET_ROD.get(), dev.groundzero.content.nuclear.GZNuclear.LI6_TARGET_ROD.get());
        info(registration, MachineText.INFO_PARTICLE_ACCELERATOR, GZMachines.PARTICLE_ACCELERATOR.item().get(), GZMultiblocks.MAGNET_SEGMENT.item().get(),
                GZMultiblocks.INJECTOR.item().get(), GZMultiblocks.TARGET_CHAMBER.item().get(), GZMultiblocks.STEEL_PLATING.item().get(),
                GZMultiblocks.PARTICLE_ACCELERATOR_SPAWNER.get());
        info(registration, MachineText.INFO_FUSION_REACTOR, GZMachines.FUSION_REACTOR.item().get(), GZMultiblocks.TOROIDAL_FIELD_COIL.item().get(),
                GZMultiblocks.VACUUM_VESSEL.item().get(), GZMultiblocks.CENTRAL_SOLENOID.item().get(), GZMultiblocks.FUSION_REACTOR_SPAWNER.get());
        info(registration, MachineText.INFO_SINGULARITY_REACTOR, GZMachines.SINGULARITY_REACTOR.item().get(), GZMultiblocks.CONTAINMENT_EMITTER.item().get(),
                GZMultiblocks.CORE_CRADLE.item().get(), GZMultiblocks.SINGULARITY_REACTOR_SPAWNER.get(),
                dev.groundzero.content.nuclear.GZNuclear.SINGULARITY_SEED.get());
        info(registration, MachineText.INFO_DIESEL_GENERATOR, GZMachines.DIESEL_GENERATOR.item().get());
        info(registration, MachineText.INFO_RTG, GZMachines.RTG.item().get());
        info(registration, MachineText.INFO_CAPACITOR_BANK, GZMachines.CAPACITOR_BANKS.stream().map(b -> (ItemLike) b.item().get()).toArray(ItemLike[]::new));
        info(registration, MachineText.INFO_BATTERY_CHARGER, GZMachines.BATTERY_CHARGER.item().get());
        info(registration, MachineText.INFO_BATTERY, GZParts.BATTERY.get());
        info(registration, MachineText.INFO_DECONTAMINATION_SHOWER, GZMachines.DECONTAMINATION_SHOWER.item().get());
        info(registration, MachineText.INFO_CIRCUIT_FABRICATOR, GZMachines.CIRCUIT_FABRICATOR.item().get());
        info(registration, MachineText.INFO_GAS_CENTRIFUGE, GZMachines.GAS_CENTRIFUGE.item().get(),
                dev.groundzero.content.nuclear.GZNuclear.ENRICHED_URANIUM_NUGGET.get());
        info(registration, MachineText.INFO_FUSION_FUEL_SYNTHESIZER, GZMachines.FUSION_FUEL_SYNTHESIZER.item().get(),
                dev.groundzero.content.nuclear.GZNuclear.LITHIUM_DEUTERIDE_PELLET.get());
        info(registration, MachineText.INFO_EXOTIC_MATTER_FABRICATOR, GZMachines.EXOTIC_MATTER_FABRICATOR.item().get());
        info(registration, MachineText.INFO_CANNING_MACHINE, GZMachines.CANNING_MACHINE.item().get());
    }

    private static void info(IRecipeRegistration registration, String key, ItemLike... items) {
        registration.addIngredientInfo(java.util.Arrays.stream(items).map(i -> i.asItem().getDefaultInstance()).toList(),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(key));
    }

    /** For the FxPhotographer's pictures (a JEI-free hook): opens JEI on the Assembly Bench's recipes. */
    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        // The whole weapons' warheads have no recipe of their own (the hall builds them into their missiles): out of the
        // list, where they only got in the way of the missiles.
        runtime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                dev.groundzero.content.ordnance.GZOrdnance.WARHEADS.stream().filter(w -> w.block().get().kind.wholeWeapon())
                        .map(w -> new net.minecraft.world.item.ItemStack(w.item().get())).toList());
        dev.groundzero.client.fx.FxPhotographer.showAssemblyRecipes = () -> runtime.getRecipesGui().showTypes(List.of(AssemblyCategory.TYPE, MissileCategory.TYPE));
        dev.groundzero.client.fx.FxPhotographer.showHallRecipes = () -> runtime.getRecipesGui().showTypes(List.of(StrategicCategory.TYPE));
        dev.groundzero.client.fx.FxPhotographer.jeiFilter = text -> runtime.getIngredientFilter().setFilterText(text);
        dev.groundzero.client.fx.FxPhotographer.jeiRecipesFor = stack -> runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory()
                .createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        machines().forEach((kind, machine) -> registration.addCraftingStation(type(kind), machine));
        registration.addCraftingStation(AssemblyCategory.TYPE, dev.groundzero.content.assembly.GZAssembly.ASSEMBLY_BENCH.item().get());
        for (var c : dev.groundzero.content.assembler.AssemblerClass.values()) {
            registration.addCraftingStation(MissileCategory.TYPE, dev.groundzero.content.assembler.GZAssemblers.controller(c).item().get());
        }
        registration.addCraftingStation(StrategicCategory.TYPE, dev.groundzero.content.strategic.GZStrategic.HALL_CONTROLLER.item().get());
        registration.addCraftingStation(AmmoFactoryCategory.TYPE, dev.groundzero.content.superweapon.GZSuperweapons.ARMS_DEALER_CONTROLLER.item().get(),
                dev.groundzero.content.superweapon.GZSuperweapons.WAR_FACTORY_CONTROLLER.item().get());
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
                MachineLayout.Rect p = screen.pageLayout().progress();
                if (p != null && screen.getMenu().machine() instanceof dev.groundzero.content.assembly.AssemblyBenchBlockEntity) {
                    return List.of(IGuiClickableArea.createBasic(p.x(), p.y(), p.w(), p.h(), AssemblyCategory.TYPE));
                }
                if (p != null && screen.getMenu().machine() instanceof dev.groundzero.content.assembler.MissileAssemblerBlockEntity) {
                    return List.of(IGuiClickableArea.createBasic(p.x(), p.y(), p.w(), p.h(), MissileCategory.TYPE));
                }
                if (p != null && screen.getMenu().machine() instanceof dev.groundzero.content.superweapon.AmmoFactoryBlockEntity) {
                    return List.of(IGuiClickableArea.createBasic(p.x(), p.y(), p.w(), p.h(), AmmoFactoryCategory.TYPE));
                }
                if (p != null && screen.getMenu().machine() instanceof dev.groundzero.content.strategic.StrategicAssemblyBlockEntity) {
                    return List.of(IGuiClickableArea.createBasic(p.x(), p.y(), p.w(), p.h(), StrategicCategory.TYPE));
                }
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
