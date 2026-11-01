package dev.groundburst.compat.jei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.client.recipe.ClientRecipes;
import dev.groundburst.client.screen.MachineScreen;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineLayout;
import dev.groundburst.content.machine.MachineText;
import dev.groundburst.content.part.GBParts;
import dev.groundburst.content.recipe.GBRecipes;
import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.content.recipe.ProcessingRecipe;
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
public final class GBJeiPlugin implements IModPlugin {
    private static final Map<ProcessingKind, IRecipeHolderType<ProcessingRecipe>> TYPES = new EnumMap<>(ProcessingKind.class);

    /** The machine that runs each kind of recipe (kinds without a machine yet are left out). */
    private static Map<ProcessingKind, ItemLike> machines() {
        Map<ProcessingKind, ItemLike> m = new EnumMap<>(ProcessingKind.class);
        m.put(ProcessingKind.CRUSHING, GBMachines.ORE_CRUSHER.item().get());
        m.put(ProcessingKind.ARC_SMELTING, GBMachines.ARC_FURNACE.item().get());
        m.put(ProcessingKind.PRESSING, GBMachines.INDUSTRIAL_PRESS.item().get());
        m.put(ProcessingKind.ELECTROLYSIS, GBMachines.ELECTROLYTIC_SEPARATOR.item().get());
        m.put(ProcessingKind.CRYO_CONDENSING, GBMachines.CRYOGENIC_CONDENSER.item().get());
        m.put(ProcessingKind.CHEMISTRY, GBMachines.CHEMICAL_PLANT.item().get());
        m.put(ProcessingKind.FABRICATING, GBMachines.CIRCUIT_FABRICATOR.item().get());
        m.put(ProcessingKind.CENTRIFUGING, GBMachines.GAS_CENTRIFUGE.item().get());
        m.put(ProcessingKind.FUSION_SYNTHESIS, GBMachines.FUSION_FUEL_SYNTHESIZER.item().get());
        m.put(ProcessingKind.EXOTIC_FABRICATING, GBMachines.EXOTIC_MATTER_FABRICATOR.item().get());
        m.put(ProcessingKind.ACCELERATING, GBMachines.PARTICLE_ACCELERATOR.item().get());
        m.put(ProcessingKind.CANNING, GBMachines.CANNING_MACHINE.item().get());
        return m;
    }

    private static IRecipeHolderType<ProcessingRecipe> type(ProcessingKind kind) {
        return TYPES.computeIfAbsent(kind, k -> IRecipeHolderType.create(GBRecipes.type(k)));
    }

    @Override
    public Identifier getPluginUid() {
        return GroundBurst.id("jei_plugin");
    }

    /**
     * Missiles are told apart by their looks (DESIGN.md §10.4, §17.11): each warhead has a missile of its own, and a
     * Light or Medium one a cruise missile too, each with its own recipe (the Missile Assembly category, or the
     * ICBM Assembly category for the hall's).
     */
    @Override
    public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(dev.groundburst.content.assembly.GBAssembly.MISSILE.get(), (stack, context) -> {
            var spec = dev.groundburst.content.assembly.MissileItem.spec(stack);
            return spec != null ? spec.look() : null;
        });
    }

    /** Every warhead's missile in JEI's list: R on one shows how its assembler (or the hall) builds it. */
    @Override
    public void registerExtraIngredients(mezz.jei.api.registration.IExtraIngredientRegistration registration) {
        registration.addExtraItemStacks(MissileCategory.recipes().stream().map(MissileCategory::output).toList());
        registration.addExtraItemStacks(StrategicCategory.recipes().stream().map(StrategicCategory::output).toList());
        registration.addExtraItemStacks(SpecialWeaponsCategory.recipes().stream().map(SpecialWeaponsCategory::output).toList());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        machines().forEach((kind, machine) -> registration.addRecipeCategories(new ProcessingCategory(type(kind), machine, gui)));
        registration.addRecipeCategories(new AssemblyCategory(gui));
        registration.addRecipeCategories(new MissileCategory(gui));
        registration.addRecipeCategories(new StrategicCategory(gui));
        registration.addRecipeCategories(new SpecialWeaponsCategory(gui));
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
        registration.addRecipes(SpecialWeaponsCategory.TYPE, SpecialWeaponsCategory.recipes());
        registration.addRecipes(AmmoFactoryCategory.TYPE, AmmoFactoryCategory.recipes());
        // Missile defence (DESIGN.md §18.2).
        info(registration, dev.groundburst.content.defence.GBDefence.INFO, dev.groundburst.content.defence.GBDefence.BATTERY.item().get(),
                dev.groundburst.content.defence.GBDefence.INTERCEPTOR_MISSILE.get());
        info(registration, dev.groundburst.content.alarm.GBAlarms.INFO, dev.groundburst.content.alarm.GBAlarms.all().stream()
                .map(b -> b.item().get()).toArray(net.minecraft.world.item.Item[]::new));
        info(registration, dev.groundburst.content.defence.GBDefence.INFO_COMPLEX,
                dev.groundburst.content.defence.GBDefence.COMPLEX_CONTROLLER.item().get(),
                dev.groundburst.content.defence.GBDefence.LAUNCH_CELL.item().get(),
                dev.groundburst.content.defence.GBDefence.PHASED_ARRAY.item().get());
        // The superweapons (DESIGN.md §19.5): how each is built and works.
        info(registration, dev.groundburst.content.superweapon.GBSuperweapons.INFO_SCUD_STORM, dev.groundburst.content.superweapon.GBSuperweapons.SCUD_STORM_CONTROLLER.item().get(), dev.groundburst.content.superweapon.GBSuperweapons.ADOBE_WALL.item().get(),
                dev.groundburst.content.superweapon.GBSuperweapons.TOXIN_TANK.item().get(), dev.groundburst.content.superweapon.GBSuperweapons.SCUD_ERECTOR.item().get(), dev.groundburst.content.superweapon.GBSuperweapons.SCUD_STORM_SPAWNER.get(), dev.groundburst.content.superweapon.GBSuperweapons.TOXIN_SCUD.get());
        info(registration, dev.groundburst.content.superweapon.GBSuperweapons.INFO_NUCLEAR_MISSILE, dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE_CONTROLLER.item().get(), dev.groundburst.content.superweapon.GBSuperweapons.FIELDSTONE_WALL.item().get(),
                dev.groundburst.content.superweapon.GBSuperweapons.MISSILE_ERECTOR.item().get(), dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE_SPAWNER.get(), dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE_ROUND.get());
        info(registration, dev.groundburst.content.superweapon.GBSuperweapons.INFO_TOXIN, dev.groundburst.content.superweapon.GBSuperweapons.TOXIN_SCUD.get());
        // The upgrade chips (DESIGN.md §7.3): where they go and what they do.
        info(registration, dev.groundburst.content.machine.GBUpgrades.INFO, dev.groundburst.content.machine.GBUpgrades.CHIPS.stream()
                .map(c -> (net.minecraft.world.level.ItemLike) c.get()).toArray(net.minecraft.world.level.ItemLike[]::new));
        info(registration, dev.groundburst.content.superweapon.GBSuperweapons.INFO_ARMS_DEALER, dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER_CONTROLLER.item().get(), dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER_SPAWNER.get());
        info(registration, dev.groundburst.content.superweapon.GBSuperweapons.INFO_WAR_FACTORY, dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY_CONTROLLER.item().get(), dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY_SPAWNER.get());
        // The Launch Control Station and its codes (DESIGN.md §17.12).
        info(registration, dev.groundburst.content.launch.GBLaunch.INFO_STATION, dev.groundburst.content.launch.GBLaunch.STATION_CONTROLLER.item().get(),
                dev.groundburst.content.launch.GBLaunch.LAUNCH_CODES.get(), dev.groundburst.content.launch.GBLaunch.STATION_SPAWNER.get());
        // The Laser Designator and the guidance that homes on it (DESIGN.md §17.13).
        info(registration, dev.groundburst.content.launch.GBLaunch.INFO_DESIGNATOR, dev.groundburst.content.launch.GBLaunch.LASER_DESIGNATOR.get(),
                dev.groundburst.content.assembly.GBAssembly.LASER_GUIDANCE.get());
        // The Cruise Missile Launcher and the motor that makes a cruise missile (slice 9c, DESIGN.md §17.11).
        info(registration, dev.groundburst.content.launch.GBLaunch.INFO_CRUISE_LAUNCHER, dev.groundburst.content.launch.GBLaunch.CRUISE_CONTROLLER.item().get(),
                dev.groundburst.content.launch.GBLaunch.LAUNCH_BOX.item().get(), dev.groundburst.content.launch.GBLaunch.CRUISE_SPAWNER.get());
        info(registration, dev.groundburst.content.launch.GBLaunch.INFO_CRUISE_ENGINE, dev.groundburst.content.assembly.GBAssembly.CRUISE_ENGINE.get());
        // Radiation's instruments and medicine (slice 12a, DESIGN.md §21.8).
        info(registration, dev.groundburst.content.radiation.RadiationItems.INFO_GEIGER, dev.groundburst.content.radiation.RadiationItems.GEIGER_COUNTER.get());
        info(registration, dev.groundburst.content.radiation.RadiationItems.INFO_DOSIMETER, dev.groundburst.content.radiation.RadiationItems.DOSIMETER_BADGE.get());
        info(registration, dev.groundburst.content.radiation.RadiationItems.INFO_LEAD_CONTAINER, dev.groundburst.content.radiation.RadiationItems.LEAD_CONTAINER.get());
        info(registration, dev.groundburst.content.fallout.GBFallout.INFO_FALLOUT_ASH, dev.groundburst.content.fallout.GBFallout.FALLOUT_ASH.item().get(),
                dev.groundburst.content.fallout.GBFallout.ASH.item().get());
        info(registration, dev.groundburst.content.fallout.GBFallout.INFO_FROZEN, dev.groundburst.content.fallout.GBFallout.FROSTED_STONE.item().get(), dev.groundburst.content.fallout.GBFallout.FROSTED_GRASS_BLOCK.item().get(),
                dev.groundburst.content.fallout.GBFallout.FROZEN_PLANT.item().get(), dev.groundburst.content.fallout.GBFallout.CRYO_CRYSTAL.item().get());
        info(registration, dev.groundburst.content.fallout.GBFallout.INFO_TOXIC_SOIL, dev.groundburst.content.fallout.GBFallout.TOXIC_SOIL.item().get());
        info(registration, dev.groundburst.content.salvage.GBSalvage.INFO_SALVAGE, dev.groundburst.content.salvage.GBSalvage.DAMAGED_WARHEAD.get());
        info(registration, dev.groundburst.content.salvage.GBSalvage.INFO_WASTE_DRUM, dev.groundburst.content.salvage.GBSalvage.WASTE_DRUM.item().get());
        info(registration, dev.groundburst.content.fallout.GBFallout.INFO_WASTELAND, dev.groundburst.content.fallout.GBFallout.DEAD_GRASS_BLOCK.item().get(), dev.groundburst.content.fallout.GBFallout.IRRADIATED_SOIL.item().get());
        info(registration, dev.groundburst.content.fallout.GBFallout.INFO_SCORCHED, dev.groundburst.content.fallout.GBFallout.blocks().stream()
                .filter(b -> b != dev.groundburst.content.fallout.GBFallout.ASH && b != dev.groundburst.content.fallout.GBFallout.FALLOUT_ASH && b != dev.groundburst.content.fallout.GBFallout.TOXIC_SOIL && b != dev.groundburst.content.fallout.GBFallout.FROSTED_STONE
                        && b != dev.groundburst.content.fallout.GBFallout.FROSTED_GRASS_BLOCK && b != dev.groundburst.content.fallout.GBFallout.FROZEN_PLANT && b != dev.groundburst.content.fallout.GBFallout.CRYO_CRYSTAL)
                .map(b -> (net.minecraft.world.item.Item) b.item().get()).toArray(net.minecraft.world.item.Item[]::new));
        info(registration, dev.groundburst.content.radiation.RadiationItems.INFO_MEDICINE, dev.groundburst.content.radiation.RadiationItems.RAD_AWAY.get(),
                dev.groundburst.content.radiation.RadiationItems.POTASSIUM_IODIDE.get(), dev.groundburst.content.radiation.RadiationItems.ANTI_RAD_INJECTOR.get());
        registration.addIngredientInfo(List.of(GBParts.PLATE_DIE.get(), GBParts.ROD_DIE.get(), GBParts.WIRE_DIE.get(), GBParts.CANISTER_DIE.get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_DIE));
        registration.addIngredientInfo(List.of(GBMachines.CHEMICAL_PLANT.item().get(), GBMultiblocks.MACHINE_CASING.item().get(),
                        GBMultiblocks.REACTOR_GLASS.item().get(), GBMultiblocks.ITEM_PORT.item().get(), GBMultiblocks.FLUID_PORT.item().get(), GBMultiblocks.HOLO_PROJECTOR.get(),
                        GBMultiblocks.CHEMICAL_PLANT_SPAWNER.get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_CHEMICAL_PLANT));
        registration.addIngredientInfo(List.of(dev.groundburst.content.fluid.GBFluids.FLUID_TANK.item().get().getDefaultInstance()),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_FLUID_TANK));
        registration.addIngredientInfo(List.of(dev.groundburst.content.fluid.GBFluids.CREATIVE_FLUID_TANK.item().get().getDefaultInstance()),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_CREATIVE_FLUID_TANK));
        registration.addIngredientInfo(List.of(GBMachines.WRENCH.get().getDefaultInstance()),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_WRENCH));
        registration.addIngredientInfo(List.of(GBMachines.WATER_PUMP.item().get().getDefaultInstance()),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_WATER_PUMP));
        registration.addIngredientInfo(dev.groundburst.content.fluid.GBFluids.pipes().stream().map(p -> p.item().get().getDefaultInstance()).toList(),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_FLUID_PIPE));
        registration.addIngredientInfo(dev.groundburst.content.pipe.GBPipes.itemPipes().stream().map(p -> p.item().get().getDefaultInstance()).toList(),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable(MachineText.INFO_ITEM_PIPE));
        registration.addIngredientInfo(dev.groundburst.content.fluid.GBFluids.all().stream()
                        .map(f -> f.bucket().get().getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_FLUIDS));
        registration.addIngredientInfo(List.of(GBParts.OXYGEN_MEMBRANE.get(), GBParts.NITROGEN_MEMBRANE.get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_MEMBRANE));
        registration.addIngredientInfo(List.of(GBMachines.COMBUSTION_GENERATOR.item().get(), GBMachines.ORE_CRUSHER.item().get(),
                        GBMachines.ARC_FURNACE.item().get(), GBMachines.INDUSTRIAL_PRESS.item().get(),
                        GBMachines.ELECTROLYTIC_SEPARATOR.item().get(), GBMachines.CRYOGENIC_CONDENSER.item().get(),
                        GBMachines.CIRCUIT_FABRICATOR.item().get(), GBMachines.GAS_CENTRIFUGE.item().get(),
                        GBMachines.FUSION_FUEL_SYNTHESIZER.item().get(), GBMachines.EXOTIC_MATTER_FABRICATOR.item().get(),
                        GBMachines.DIESEL_GENERATOR.item().get(), GBMachines.RTG.item().get(), GBMachines.BATTERY_CHARGER.item().get(),
                        GBMachines.DECONTAMINATION_SHOWER.item().get(), GBMachines.CANNING_MACHINE.item().get())
                .stream().map(i -> i.getDefaultInstance()).toList(), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable(MachineText.INFO_SIDES));
        info(registration, MachineText.INFO_FISSION_REACTOR, GBMachines.FISSION_REACTOR.item().get(), GBMultiblocks.LEAD_LINED_CASING.item().get(),
                GBMultiblocks.FUEL_CHANNEL.item().get(),
                GBMultiblocks.ENERGY_PORT.item().get(), GBMultiblocks.FISSION_REACTOR_SPAWNER.get(), dev.groundburst.content.nuclear.GBNuclear.URANIUM_FUEL_ROD.get(),
                dev.groundburst.content.nuclear.GBNuclear.DU_TARGET_ROD.get(), dev.groundburst.content.nuclear.GBNuclear.LI6_TARGET_ROD.get());
        info(registration, MachineText.INFO_PARTICLE_ACCELERATOR, GBMachines.PARTICLE_ACCELERATOR.item().get(), GBMultiblocks.MAGNET_SEGMENT.item().get(),
                GBMultiblocks.INJECTOR.item().get(), GBMultiblocks.TARGET_CHAMBER.item().get(), GBMultiblocks.STEEL_PLATING.item().get(),
                GBMultiblocks.PARTICLE_ACCELERATOR_SPAWNER.get());
        info(registration, MachineText.INFO_FUSION_REACTOR, GBMachines.FUSION_REACTOR.item().get(), GBMultiblocks.TOROIDAL_FIELD_COIL.item().get(),
                GBMultiblocks.VACUUM_VESSEL.item().get(), GBMultiblocks.CENTRAL_SOLENOID.item().get(), GBMultiblocks.FUSION_REACTOR_SPAWNER.get());
        info(registration, MachineText.INFO_SINGULARITY_REACTOR, GBMachines.SINGULARITY_REACTOR.item().get(), GBMultiblocks.CONTAINMENT_EMITTER.item().get(),
                GBMultiblocks.CORE_CRADLE.item().get(), GBMultiblocks.SINGULARITY_REACTOR_SPAWNER.get(),
                dev.groundburst.content.nuclear.GBNuclear.SINGULARITY_SEED.get());
        info(registration, MachineText.INFO_DIESEL_GENERATOR, GBMachines.DIESEL_GENERATOR.item().get());
        info(registration, MachineText.INFO_RTG, GBMachines.RTG.item().get());
        info(registration, MachineText.INFO_CAPACITOR_BANK, GBMachines.CAPACITOR_BANKS.stream().map(b -> (ItemLike) b.item().get()).toArray(ItemLike[]::new));
        info(registration, MachineText.INFO_BATTERY_CHARGER, GBMachines.BATTERY_CHARGER.item().get());
        info(registration, MachineText.INFO_BATTERY, GBParts.BATTERY.get());
        info(registration, MachineText.INFO_DECONTAMINATION_SHOWER, GBMachines.DECONTAMINATION_SHOWER.item().get());
        info(registration, MachineText.INFO_CIRCUIT_FABRICATOR, GBMachines.CIRCUIT_FABRICATOR.item().get());
        info(registration, MachineText.INFO_GAS_CENTRIFUGE, GBMachines.GAS_CENTRIFUGE.item().get(),
                dev.groundburst.content.nuclear.GBNuclear.ENRICHED_URANIUM_NUGGET.get());
        info(registration, MachineText.INFO_FUSION_FUEL_SYNTHESIZER, GBMachines.FUSION_FUEL_SYNTHESIZER.item().get(),
                dev.groundburst.content.nuclear.GBNuclear.LITHIUM_DEUTERIDE_PELLET.get());
        info(registration, MachineText.INFO_EXOTIC_MATTER_FABRICATOR, GBMachines.EXOTIC_MATTER_FABRICATOR.item().get());
        info(registration, MachineText.INFO_CANNING_MACHINE, GBMachines.CANNING_MACHINE.item().get());
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
                dev.groundburst.content.ordnance.GBOrdnance.WARHEADS.stream().filter(w -> w.block().get().kind.wholeWeapon())
                        .map(w -> new net.minecraft.world.item.ItemStack(w.item().get())).toList());
        dev.groundburst.client.fx.FxPhotographer.showAssemblyRecipes = () -> runtime.getRecipesGui().showTypes(List.of(AssemblyCategory.TYPE, MissileCategory.TYPE));
        dev.groundburst.client.fx.FxPhotographer.showHallRecipes = () -> runtime.getRecipesGui().showTypes(List.of(StrategicCategory.TYPE));
        dev.groundburst.client.fx.FxPhotographer.showSpecialRecipes = () -> runtime.getRecipesGui().showTypes(List.of(SpecialWeaponsCategory.TYPE));
        dev.groundburst.client.fx.FxPhotographer.jeiFilter = text -> runtime.getIngredientFilter().setFilterText(text);
        dev.groundburst.client.fx.FxPhotographer.jeiRecipesFor = stack -> runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory()
                .createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        machines().forEach((kind, machine) -> registration.addCraftingStation(type(kind), machine));
        registration.addCraftingStation(AssemblyCategory.TYPE, dev.groundburst.content.assembly.GBAssembly.ASSEMBLY_BENCH.item().get());
        for (var c : dev.groundburst.content.assembler.AssemblerClass.values()) {
            registration.addCraftingStation(MissileCategory.TYPE, dev.groundburst.content.assembler.GBAssemblers.controller(c).item().get());
        }
        registration.addCraftingStation(StrategicCategory.TYPE, dev.groundburst.content.strategic.GBStrategic.HALL_CONTROLLER.item().get());
        registration.addCraftingStation(SpecialWeaponsCategory.TYPE, dev.groundburst.content.strategic.GBStrategic.SPECIAL_CONTROLLER.item().get());
        registration.addCraftingStation(AmmoFactoryCategory.TYPE, dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER_CONTROLLER.item().get(),
                dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY_CONTROLLER.item().get());
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
                if (p != null && screen.getMenu().machine() instanceof dev.groundburst.content.assembly.AssemblyBenchBlockEntity) {
                    return List.of(IGuiClickableArea.createBasic(p.x(), p.y(), p.w(), p.h(), AssemblyCategory.TYPE));
                }
                if (p != null && screen.getMenu().machine() instanceof dev.groundburst.content.assembler.MissileAssemblerBlockEntity) {
                    return List.of(IGuiClickableArea.createBasic(p.x(), p.y(), p.w(), p.h(), MissileCategory.TYPE));
                }
                if (p != null && screen.getMenu().machine() instanceof dev.groundburst.content.superweapon.AmmoFactoryBlockEntity) {
                    return List.of(IGuiClickableArea.createBasic(p.x(), p.y(), p.w(), p.h(), AmmoFactoryCategory.TYPE));
                }
                if (p != null && screen.getMenu().machine() instanceof dev.groundburst.content.strategic.StrategicAssemblyBlockEntity) {
                    return List.of(IGuiClickableArea.createBasic(p.x(), p.y(), p.w(), p.h(), StrategicCategory.TYPE));
                }
                if (p != null && screen.getMenu().machine() instanceof dev.groundburst.content.strategic.SpecialWeaponsBlockEntity) {
                    return List.of(IGuiClickableArea.createBasic(p.x(), p.y(), p.w(), p.h(), SpecialWeaponsCategory.TYPE));
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
        return machine instanceof dev.groundburst.content.machine.ProcessingBlockEntity processing ? processing.kind() : null;
    }
}
