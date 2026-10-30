package dev.groundburst;

import dev.groundburst.client.recipe.ClientRecipes;
import dev.groundburst.client.render.MachineGeoRenderer;
import dev.groundburst.client.render.MultiblockPreview;
import dev.groundburst.client.screen.MachineScreen;
import dev.groundburst.client.sound.MachineSounds;
import dev.groundburst.config.GBClientConfig;
import dev.groundburst.content.machine.MachineClientHooks;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Client-only entry point; never loaded on a dedicated server.
@Mod(value = GroundBurst.MODID, dist = Dist.CLIENT)
public class GroundBurstClient {
    public GroundBurstClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, GBClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        MachineClientHooks.clientTick = MachineSounds::tick;
        ClientRecipes.register();
        MultiblockPreview.register();
        modBus.addListener(GroundBurstClient::registerScreens);
        dev.groundburst.client.manual.ManualHotkey.register(modBus);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent e) ->
                e.register(dev.groundburst.client.screen.ItemPreview.State.class, dev.groundburst.client.screen.ItemPreview.Renderer::new));
        modBus.addListener(GroundBurstClient::registerRenderers);
        modBus.addListener(dev.groundburst.client.render.FissionSections::register);
        // Each missile's shape (its stages, engines and sections), read with its models (tools/assets/missiles.py).
        modBus.addListener((net.neoforged.neoforge.client.event.AddClientReloadListenersEvent e) -> e.addListener(
                dev.groundburst.GroundBurst.id("missile_shapes"), new dev.groundburst.client.render.MissileShapes.Loader()));
        dev.groundburst.client.render.IceShellRenderer.register(modBus);
        modBus.addListener(dev.groundburst.client.render.GBFluidModels::register);
        modBus.addListener(dev.groundburst.client.render.GBFluidModels::registerTints);
        dev.groundburst.client.blast.ClientBlasts.register(modBus);
        dev.groundburst.client.fx.Spectacle.register(modBus);
        dev.groundburst.client.fx.FxPhotographer.register();
        dev.groundburst.client.fx.BlastBenchClient.register();
        dev.groundburst.client.fx.WorldSurveyClient.register();
        dev.groundburst.client.fx.SitePhotographer.register();
        dev.groundburst.client.screen.ChargeScreens.register();
        dev.groundburst.client.screen.LaunchComputerScreen.register();
        dev.groundburst.client.screen.RadarArrayScreen.register();
        dev.groundburst.client.radiation.RadiationClient.register(modBus);
        dev.groundburst.client.fallout.FalloutClient.register(modBus);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(GBMenus.ORE_CRUSHER.get(), MachineScreen::new);
        event.register(GBMenus.LAUNCH_SILO.get(), MachineScreen::new);
        event.register(GBMenus.MISSILE_SILO.get(), MachineScreen::new);
        event.register(GBMenus.CRUISE_LAUNCHER.get(), MachineScreen::new);
        event.register(GBMenus.ASSEMBLY_BENCH.get(), dev.groundburst.client.screen.AssemblyBenchScreen::new);
        event.register(GBMenus.STRATEGIC_ASSEMBLY.get(), dev.groundburst.client.screen.StrategicAssemblyScreen::new);
        event.register(GBMenus.SPECIAL_WEAPONS.get(), dev.groundburst.client.screen.SpecialWeaponsScreen::new);
        event.register(GBMenus.INTERCEPTOR_BATTERY.get(), MachineScreen::new);
        event.register(GBMenus.INTERCEPTOR_COMPLEX.get(), MachineScreen::new);
        event.register(GBMenus.MISSILE_ASSEMBLER.get(), dev.groundburst.client.screen.MissileAssemblerScreen::new);
        event.register(GBMenus.COMBUSTION_GENERATOR.get(), MachineScreen::new);
        event.register(GBMenus.ARC_FURNACE.get(), MachineScreen::new);
        event.register(GBMenus.INDUSTRIAL_PRESS.get(), MachineScreen::new);
        event.register(GBMenus.ELECTROLYTIC_SEPARATOR.get(), MachineScreen::new);
        event.register(GBMenus.CRYOGENIC_CONDENSER.get(), MachineScreen::new);
        event.register(GBMenus.CHEMICAL_PLANT.get(), MachineScreen::new);
        event.register(GBMenus.WATER_PUMP.get(), MachineScreen::new);
        event.register(GBMenus.DIESEL_GENERATOR.get(), MachineScreen::new);
        event.register(GBMenus.FISSION_REACTOR.get(), dev.groundburst.client.screen.FissionReactorScreen::new);
        event.register(GBMenus.FUSION_REACTOR.get(), MachineScreen::new);
        event.register(GBMenus.SINGULARITY_REACTOR.get(), MachineScreen::new);
        event.register(GBMenus.PARTICLE_ACCELERATOR.get(), MachineScreen::new);
        event.register(GBMenus.RTG.get(), MachineScreen::new);
        event.register(GBMenus.CAPACITOR_BANK.get(), MachineScreen::new);
        event.register(GBMenus.BATTERY_CHARGER.get(), MachineScreen::new);
        event.register(GBMenus.DECONTAMINATION_SHOWER.get(), MachineScreen::new);
        event.register(GBMenus.CIRCUIT_FABRICATOR.get(), MachineScreen::new);
        event.register(GBMenus.GAS_CENTRIFUGE.get(), MachineScreen::new);
        event.register(GBMenus.FUSION_FUEL_SYNTHESIZER.get(), MachineScreen::new);
        event.register(GBMenus.CANNING_MACHINE.get(), MachineScreen::new);
        event.register(GBMenus.EXOTIC_MATTER_FABRICATOR.get(), MachineScreen::new);
        event.register(GBMenus.SUPERWEAPON.get(), MachineScreen::new);
        event.register(GBMenus.ARMS_DEALER.get(), MachineScreen::new);
        event.register(GBMenus.WAR_FACTORY.get(), MachineScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(dev.groundburst.content.ordnance.GBOrdnance.THROWN_EXPLOSIVE.get(), dev.groundburst.client.fx.GrenadeRenderer::new);
        event.registerEntityRenderer(dev.groundburst.content.ordnance.GBOrdnance.SHRAPNEL.get(), dev.groundburst.client.fx.ShrapnelRenderer::new);
        event.registerBlockEntityRenderer(dev.groundburst.content.ordnance.GBOrdnance.CHARGE.get(), dev.groundburst.client.render.ChargeRenderer::new);
        event.registerEntityRenderer(dev.groundburst.content.launch.GBLaunch.MISSILE.get(), dev.groundburst.client.fx.MissileRenderer::new);
        event.registerEntityRenderer(dev.groundburst.content.defence.GBDefence.INTERCEPTOR.get(), dev.groundburst.client.fx.InterceptorRenderer::new);
        event.registerBlockEntityRenderer(dev.groundburst.content.launch.GBLaunch.COMPLEX_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.launch.GBLaunch.COMPLEX_ENTITY.get(), 14, 4));
        // The hall: 13 blocks deep behind its controller, its crane 6 up.
        event.registerBlockEntityRenderer(dev.groundburst.content.strategic.GBStrategic.HALL_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.strategic.GBStrategic.HALL_ENTITY.get(), 7, 13));
        // The Interceptor Battery: two blocks tall, its turret on top.
        event.registerBlockEntityRenderer(dev.groundburst.content.defence.GBDefence.BATTERY_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.defence.GBDefence.BATTERY_ENTITY.get(), 3));
        // The Interceptor Complex: five blocks across and back from its controller at the front, four high with its mast.
        event.registerBlockEntityRenderer(dev.groundburst.content.defence.GBDefence.COMPLEX_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.defence.GBDefence.COMPLEX_ENTITY.get(), 5, 5));
        // The Special Weapons Assembler: as deep, its rings 4 up.
        event.registerBlockEntityRenderer(dev.groundburst.content.strategic.GBStrategic.SPECIAL_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.strategic.GBStrategic.SPECIAL_ENTITY.get(), 5, 13));
        // The missile assemblers (DESIGN.md §10.4): each drawn as far as its structure reaches, up and out.
        event.registerBlockEntityRenderer(dev.groundburst.content.assembler.GBAssemblers.LIGHT_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.assembler.GBAssemblers.LIGHT_ENTITY.get(), 4, 6));
        event.registerBlockEntityRenderer(dev.groundburst.content.assembler.GBAssemblers.MEDIUM_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.assembler.GBAssemblers.MEDIUM_ENTITY.get(), 6, 7));
        event.registerBlockEntityRenderer(dev.groundburst.content.assembler.GBAssemblers.HEAVY_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.assembler.GBAssemblers.HEAVY_ENTITY.get(), 7, 9));
        event.registerBlockEntityRenderer(dev.groundburst.content.assembler.GBAssemblers.STRATEGIC_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.assembler.GBAssemblers.STRATEGIC_ENTITY.get(), 14, 5));
        // The Missile Silo: its console and open doors stand a little over the ground; its shaft reaches 14 blocks down.
        event.registerBlockEntityRenderer(dev.groundburst.content.launch.GBLaunch.MISSILE_SILO_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.launch.GBLaunch.MISSILE_SILO_ENTITY.get(), 2, 4, 14));
        event.registerBlockEntityRenderer(dev.groundburst.content.launch.GBLaunch.SILO_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.launch.GBLaunch.SILO_ENTITY.get(), 9, 2));
        // The Cruise Missile Launcher (DESIGN.md §17.11): its deck 6 blocks back, its raised boxes 6 up.
        event.registerBlockEntityRenderer(dev.groundburst.content.launch.GBLaunch.CRUISE_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.launch.GBLaunch.CRUISE_ENTITY.get(), 7, 6));
        // The superweapons (DESIGN.md §19.5): the SCUD Storm's pad and wall reach 16 blocks back, its raised SCUDs 7 up.
        event.registerBlockEntityRenderer(dev.groundburst.content.superweapon.GBSuperweapons.SCUD_STORM_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.superweapon.GBSuperweapons.SCUD_STORM_ENTITY.get(), 8, 17));
        // The ammunition factories: the Arms Dealer's compound 7 back, its hoist 4 up; the War Factory 13 back, its
        // chimney 6 up.
        event.registerBlockEntityRenderer(dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER_ENTITY.get(), 5, 7));
        event.registerBlockEntityRenderer(dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY_ENTITY.get(), 7, 13));
        // The Nuclear Missile Silo: its hall 15 blocks back, its tower and raised missile 11 up.
        event.registerBlockEntityRenderer(dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE_ENTITY.get(), 11, 15));
        // The Search Radar's reflector reaches six blocks out and up from its bearing.
        // The Large Silo Door: its rails reach eight blocks either side of its middle.
        event.registerBlockEntityRenderer(dev.groundburst.content.construction.GBConstruction.LARGE_SILO_DOOR_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.construction.GBConstruction.LARGE_SILO_DOOR_ENTITY.get(), 1, 8));
        event.registerBlockEntityRenderer(dev.groundburst.content.construction.GBConstruction.RUSTED_LARGE_SILO_DOOR_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.construction.GBConstruction.RUSTED_LARGE_SILO_DOOR_ENTITY.get(), 1, 8));
        event.registerBlockEntityRenderer(dev.groundburst.content.construction.GBConstruction.SEARCH_RADAR_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.construction.GBConstruction.SEARCH_RADAR_ENTITY.get(), 6, 6));
        // The Missile Wreck: some eight blocks on from its block toward its facing, its nose buried a few blocks down.
        event.registerBlockEntityRenderer(dev.groundburst.content.construction.GBConstruction.MISSILE_WRECK_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.construction.GBConstruction.MISSILE_WRECK_ENTITY.get(), 3, 8, 3));
        event.registerBlockEntityRenderer(dev.groundburst.content.construction.GBConstruction.SEARCH_RADAR_WRECK_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.construction.GBConstruction.SEARCH_RADAR_WRECK_ENTITY.get(), 6, 6));
        event.registerBlockEntityRenderer(dev.groundburst.content.launch.GBLaunch.RADAR_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.launch.GBLaunch.RADAR_ENTITY.get(), 7, 2));
        event.registerBlockEntityRenderer(dev.groundburst.content.launch.GBLaunch.COMPUTER_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.launch.GBLaunch.COMPUTER_ENTITY.get(), 4, 1));
        event.registerBlockEntityRenderer(GBBlockEntities.ORE_CRUSHER.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.ORE_CRUSHER.get(), 1));
        // The bench reaches one block to its side, and its hoist stands nearly two blocks high.
        event.registerBlockEntityRenderer(dev.groundburst.content.assembly.GBAssembly.BENCH_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundburst.content.assembly.GBAssembly.BENCH_ENTITY.get(), 2, 1));
        event.registerBlockEntityRenderer(GBBlockEntities.COMBUSTION_GENERATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.COMBUSTION_GENERATOR.get(), 2));
        event.registerBlockEntityRenderer(GBBlockEntities.ARC_FURNACE.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.ARC_FURNACE.get(), 2));
        event.registerBlockEntityRenderer(GBBlockEntities.INDUSTRIAL_PRESS.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.INDUSTRIAL_PRESS.get(), 2));
        event.registerBlockEntityRenderer(GBBlockEntities.ELECTROLYTIC_SEPARATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.ELECTROLYTIC_SEPARATOR.get(), 1));
        event.registerBlockEntityRenderer(GBBlockEntities.CRYOGENIC_CONDENSER.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.CRYOGENIC_CONDENSER.get(), 2));
        event.registerBlockEntityRenderer(GBBlockEntities.WATER_PUMP.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.WATER_PUMP.get(), 1));
        // Multiblocks: the formed model reaches beyond the controller (accelerator ring 8). The Fission Reactor is built of
        // ordinary blocks: its renderer only fills the core with coolant.
        event.registerBlockEntityRenderer(GBBlockEntities.FISSION_REACTOR.get(), ctx -> new dev.groundburst.client.render.FissionReactorRenderer());
        event.registerBlockEntityRenderer(GBBlockEntities.FUSION_REACTOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.FUSION_REACTOR.get(), 3, 8));
        event.registerBlockEntityRenderer(GBBlockEntities.SINGULARITY_REACTOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.SINGULARITY_REACTOR.get(), 5, 4));
        event.registerBlockEntityRenderer(GBBlockEntities.PARTICLE_ACCELERATOR.get(),
                ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.PARTICLE_ACCELERATOR.get(), 1, 9));
        event.registerBlockEntityRenderer(GBBlockEntities.DIESEL_GENERATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.DIESEL_GENERATOR.get(), 1));
        event.registerBlockEntityRenderer(GBBlockEntities.RTG.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.RTG.get(), 1));
        event.registerBlockEntityRenderer(GBBlockEntities.BATTERY_CHARGER.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.BATTERY_CHARGER.get(), 1));
        event.registerBlockEntityRenderer(GBBlockEntities.DECONTAMINATION_SHOWER.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.DECONTAMINATION_SHOWER.get(), 2));
        event.registerBlockEntityRenderer(GBBlockEntities.CAPACITOR_BANK.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.CAPACITOR_BANK.get(), 1));
        event.registerBlockEntityRenderer(GBBlockEntities.ADVANCED_CAPACITOR_BANK.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.ADVANCED_CAPACITOR_BANK.get(), 1));
        event.registerBlockEntityRenderer(GBBlockEntities.SUPERCONDUCTING_CAPACITOR_BANK.get(),
                ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.SUPERCONDUCTING_CAPACITOR_BANK.get(), 1));
        event.registerBlockEntityRenderer(GBBlockEntities.CREATIVE_CAPACITOR_BANK.get(),
                ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.CREATIVE_CAPACITOR_BANK.get(), 1));
        event.registerBlockEntityRenderer(GBBlockEntities.CIRCUIT_FABRICATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.CIRCUIT_FABRICATOR.get(), 1));
        event.registerBlockEntityRenderer(GBBlockEntities.GAS_CENTRIFUGE.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.GAS_CENTRIFUGE.get(), 2));
        event.registerBlockEntityRenderer(GBBlockEntities.FUSION_FUEL_SYNTHESIZER.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.FUSION_FUEL_SYNTHESIZER.get(), 1));
        event.registerBlockEntityRenderer(GBBlockEntities.CANNING_MACHINE.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.CANNING_MACHINE.get(), 1));
        event.registerBlockEntityRenderer(GBBlockEntities.EXOTIC_MATTER_FABRICATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.EXOTIC_MATTER_FABRICATOR.get(), 2));
        event.registerBlockEntityRenderer(GBBlockEntities.FLUID_TANK.get(), ctx -> new dev.groundburst.client.render.FluidTankRenderer<>());
        event.registerBlockEntityRenderer(GBBlockEntities.CREATIVE_FLUID_TANK.get(), ctx -> new dev.groundburst.client.render.FluidTankRenderer<>());
        // The formed Chemical Plant spans 2x2x2 from its controller in any rotation.
        event.registerBlockEntityRenderer(GBBlockEntities.CHEMICAL_PLANT.get(), ctx -> new MachineGeoRenderer<>(ctx, GBBlockEntities.CHEMICAL_PLANT.get(), 2, 1));
    }
}
