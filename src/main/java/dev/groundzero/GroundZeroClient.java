package dev.groundzero;

import dev.groundzero.client.recipe.ClientRecipes;
import dev.groundzero.client.render.MachineGeoRenderer;
import dev.groundzero.client.render.MultiblockPreview;
import dev.groundzero.client.screen.MachineScreen;
import dev.groundzero.client.sound.MachineSounds;
import dev.groundzero.config.GZClientConfig;
import dev.groundzero.content.machine.MachineClientHooks;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZMenus;
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
@Mod(value = GroundZero.MODID, dist = Dist.CLIENT)
public class GroundZeroClient {
    public GroundZeroClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, GZClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        MachineClientHooks.clientTick = MachineSounds::tick;
        ClientRecipes.register();
        MultiblockPreview.register();
        modBus.addListener(GroundZeroClient::registerScreens);
        dev.groundzero.client.manual.ManualHotkey.register(modBus);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent e) ->
                e.register(dev.groundzero.client.screen.ItemPreview.State.class, dev.groundzero.client.screen.ItemPreview.Renderer::new));
        modBus.addListener(GroundZeroClient::registerRenderers);
        modBus.addListener(dev.groundzero.client.render.FissionSections::register);
        // Each missile's shape (its stages, engines and sections), read with its models (tools/assets/missiles.py).
        modBus.addListener((net.neoforged.neoforge.client.event.AddClientReloadListenersEvent e) -> e.addListener(
                dev.groundzero.GroundZero.id("missile_shapes"), new dev.groundzero.client.render.MissileShapes.Loader()));
        dev.groundzero.client.render.IceShellRenderer.register(modBus);
        modBus.addListener(dev.groundzero.client.render.GZFluidModels::register);
        modBus.addListener(dev.groundzero.client.render.GZFluidModels::registerTints);
        dev.groundzero.client.blast.ClientBlasts.register(modBus);
        dev.groundzero.client.fx.Spectacle.register(modBus);
        dev.groundzero.client.fx.FxPhotographer.register();
        dev.groundzero.client.fx.BlastBenchClient.register();
        dev.groundzero.client.screen.ChargeScreens.register();
        dev.groundzero.client.screen.LaunchComputerScreen.register();
        dev.groundzero.client.screen.RadarArrayScreen.register();
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(GZMenus.ORE_CRUSHER.get(), MachineScreen::new);
        event.register(GZMenus.LAUNCH_SILO.get(), MachineScreen::new);
        event.register(GZMenus.MISSILE_SILO.get(), MachineScreen::new);
        event.register(GZMenus.ASSEMBLY_BENCH.get(), dev.groundzero.client.screen.AssemblyBenchScreen::new);
        event.register(GZMenus.STRATEGIC_ASSEMBLY.get(), dev.groundzero.client.screen.StrategicAssemblyScreen::new);
        event.register(GZMenus.MISSILE_ASSEMBLER.get(), dev.groundzero.client.screen.MissileAssemblerScreen::new);
        event.register(GZMenus.COMBUSTION_GENERATOR.get(), MachineScreen::new);
        event.register(GZMenus.ARC_FURNACE.get(), MachineScreen::new);
        event.register(GZMenus.INDUSTRIAL_PRESS.get(), MachineScreen::new);
        event.register(GZMenus.ELECTROLYTIC_SEPARATOR.get(), MachineScreen::new);
        event.register(GZMenus.CRYOGENIC_CONDENSER.get(), MachineScreen::new);
        event.register(GZMenus.CHEMICAL_PLANT.get(), MachineScreen::new);
        event.register(GZMenus.WATER_PUMP.get(), MachineScreen::new);
        event.register(GZMenus.DIESEL_GENERATOR.get(), MachineScreen::new);
        event.register(GZMenus.FISSION_REACTOR.get(), dev.groundzero.client.screen.FissionReactorScreen::new);
        event.register(GZMenus.FUSION_REACTOR.get(), MachineScreen::new);
        event.register(GZMenus.SINGULARITY_REACTOR.get(), MachineScreen::new);
        event.register(GZMenus.PARTICLE_ACCELERATOR.get(), MachineScreen::new);
        event.register(GZMenus.RTG.get(), MachineScreen::new);
        event.register(GZMenus.CAPACITOR_BANK.get(), MachineScreen::new);
        event.register(GZMenus.BATTERY_CHARGER.get(), MachineScreen::new);
        event.register(GZMenus.DECONTAMINATION_SHOWER.get(), MachineScreen::new);
        event.register(GZMenus.CIRCUIT_FABRICATOR.get(), MachineScreen::new);
        event.register(GZMenus.GAS_CENTRIFUGE.get(), MachineScreen::new);
        event.register(GZMenus.FUSION_FUEL_SYNTHESIZER.get(), MachineScreen::new);
        event.register(GZMenus.CANNING_MACHINE.get(), MachineScreen::new);
        event.register(GZMenus.EXOTIC_MATTER_FABRICATOR.get(), MachineScreen::new);
        event.register(GZMenus.SUPERWEAPON.get(), MachineScreen::new);
        event.register(GZMenus.ARMS_DEALER.get(), MachineScreen::new);
        event.register(GZMenus.WAR_FACTORY.get(), MachineScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(dev.groundzero.content.ordnance.GZOrdnance.THROWN_EXPLOSIVE.get(), dev.groundzero.client.fx.GrenadeRenderer::new);
        event.registerEntityRenderer(dev.groundzero.content.ordnance.GZOrdnance.SHRAPNEL.get(), dev.groundzero.client.fx.ShrapnelRenderer::new);
        event.registerBlockEntityRenderer(dev.groundzero.content.ordnance.GZOrdnance.CHARGE.get(), dev.groundzero.client.render.ChargeRenderer::new);
        event.registerEntityRenderer(dev.groundzero.content.launch.GZLaunch.MISSILE.get(), dev.groundzero.client.fx.MissileRenderer::new);
        event.registerBlockEntityRenderer(dev.groundzero.content.launch.GZLaunch.COMPLEX_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.launch.GZLaunch.COMPLEX_ENTITY.get(), 14, 4));
        // The hall: 13 blocks deep behind its controller, its crane 6 up.
        event.registerBlockEntityRenderer(dev.groundzero.content.strategic.GZStrategic.HALL_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.strategic.GZStrategic.HALL_ENTITY.get(), 7, 13));
        // The missile assemblers (DESIGN.md §10.4): each drawn as far as its structure reaches, up and out.
        event.registerBlockEntityRenderer(dev.groundzero.content.assembler.GZAssemblers.LIGHT_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.assembler.GZAssemblers.LIGHT_ENTITY.get(), 4, 6));
        event.registerBlockEntityRenderer(dev.groundzero.content.assembler.GZAssemblers.MEDIUM_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.assembler.GZAssemblers.MEDIUM_ENTITY.get(), 6, 7));
        event.registerBlockEntityRenderer(dev.groundzero.content.assembler.GZAssemblers.HEAVY_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.assembler.GZAssemblers.HEAVY_ENTITY.get(), 7, 9));
        event.registerBlockEntityRenderer(dev.groundzero.content.assembler.GZAssemblers.STRATEGIC_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.assembler.GZAssemblers.STRATEGIC_ENTITY.get(), 14, 5));
        // The Missile Silo: its console and open doors stand a little over the ground; its shaft reaches 14 blocks down.
        event.registerBlockEntityRenderer(dev.groundzero.content.launch.GZLaunch.MISSILE_SILO_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.launch.GZLaunch.MISSILE_SILO_ENTITY.get(), 2, 4, 14));
        event.registerBlockEntityRenderer(dev.groundzero.content.launch.GZLaunch.SILO_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.launch.GZLaunch.SILO_ENTITY.get(), 9, 2));
        // The superweapons (DESIGN.md §19.5): the SCUD Storm's pad and wall reach 16 blocks back, its raised SCUDs 7 up.
        event.registerBlockEntityRenderer(dev.groundzero.content.superweapon.GZSuperweapons.SCUD_STORM_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.superweapon.GZSuperweapons.SCUD_STORM_ENTITY.get(), 8, 17));
        // The ammunition factories: the Arms Dealer's compound 7 back, its hoist 4 up; the War Factory 13 back, its
        // chimney 6 up.
        event.registerBlockEntityRenderer(dev.groundzero.content.superweapon.GZSuperweapons.ARMS_DEALER_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.superweapon.GZSuperweapons.ARMS_DEALER_ENTITY.get(), 5, 7));
        event.registerBlockEntityRenderer(dev.groundzero.content.superweapon.GZSuperweapons.WAR_FACTORY_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.superweapon.GZSuperweapons.WAR_FACTORY_ENTITY.get(), 7, 13));
        // The Nuclear Missile Silo: its hall 15 blocks back, its tower and raised missile 11 up.
        event.registerBlockEntityRenderer(dev.groundzero.content.superweapon.GZSuperweapons.NUCLEAR_MISSILE_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.superweapon.GZSuperweapons.NUCLEAR_MISSILE_ENTITY.get(), 11, 15));
        event.registerBlockEntityRenderer(dev.groundzero.content.launch.GZLaunch.RADAR_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.launch.GZLaunch.RADAR_ENTITY.get(), 7, 2));
        event.registerBlockEntityRenderer(dev.groundzero.content.launch.GZLaunch.COMPUTER_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.launch.GZLaunch.COMPUTER_ENTITY.get(), 4, 1));
        event.registerBlockEntityRenderer(GZBlockEntities.ORE_CRUSHER.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.ORE_CRUSHER.get(), 1));
        // The bench reaches one block to its side, and its hoist stands nearly two blocks high.
        event.registerBlockEntityRenderer(dev.groundzero.content.assembly.GZAssembly.BENCH_ENTITY.get(),
                ctx -> new MachineGeoRenderer<>(ctx, dev.groundzero.content.assembly.GZAssembly.BENCH_ENTITY.get(), 2, 1));
        event.registerBlockEntityRenderer(GZBlockEntities.COMBUSTION_GENERATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.COMBUSTION_GENERATOR.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.ARC_FURNACE.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.ARC_FURNACE.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.INDUSTRIAL_PRESS.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.INDUSTRIAL_PRESS.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.ELECTROLYTIC_SEPARATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.ELECTROLYTIC_SEPARATOR.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.CRYOGENIC_CONDENSER.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.CRYOGENIC_CONDENSER.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.WATER_PUMP.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.WATER_PUMP.get(), 1));
        // Multiblocks: the formed model reaches beyond the controller (accelerator ring 8). The Fission Reactor is built of
        // ordinary blocks: its renderer only fills the core with coolant.
        event.registerBlockEntityRenderer(GZBlockEntities.FISSION_REACTOR.get(), ctx -> new dev.groundzero.client.render.FissionReactorRenderer());
        event.registerBlockEntityRenderer(GZBlockEntities.FUSION_REACTOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.FUSION_REACTOR.get(), 3, 8));
        event.registerBlockEntityRenderer(GZBlockEntities.SINGULARITY_REACTOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.SINGULARITY_REACTOR.get(), 5, 4));
        event.registerBlockEntityRenderer(GZBlockEntities.PARTICLE_ACCELERATOR.get(),
                ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.PARTICLE_ACCELERATOR.get(), 1, 9));
        event.registerBlockEntityRenderer(GZBlockEntities.DIESEL_GENERATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.DIESEL_GENERATOR.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.RTG.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.RTG.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.BATTERY_CHARGER.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.BATTERY_CHARGER.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.DECONTAMINATION_SHOWER.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.DECONTAMINATION_SHOWER.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.CAPACITOR_BANK.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.CAPACITOR_BANK.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.ADVANCED_CAPACITOR_BANK.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.ADVANCED_CAPACITOR_BANK.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.SUPERCONDUCTING_CAPACITOR_BANK.get(),
                ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.SUPERCONDUCTING_CAPACITOR_BANK.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.CREATIVE_CAPACITOR_BANK.get(),
                ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.CREATIVE_CAPACITOR_BANK.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.CIRCUIT_FABRICATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.CIRCUIT_FABRICATOR.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.GAS_CENTRIFUGE.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.GAS_CENTRIFUGE.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.FUSION_FUEL_SYNTHESIZER.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.FUSION_FUEL_SYNTHESIZER.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.CANNING_MACHINE.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.CANNING_MACHINE.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.EXOTIC_MATTER_FABRICATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.EXOTIC_MATTER_FABRICATOR.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.FLUID_TANK.get(), ctx -> new dev.groundzero.client.render.FluidTankRenderer<>());
        event.registerBlockEntityRenderer(GZBlockEntities.CREATIVE_FLUID_TANK.get(), ctx -> new dev.groundzero.client.render.FluidTankRenderer<>());
        // The formed Chemical Plant spans 2x2x2 from its controller in any rotation.
        event.registerBlockEntityRenderer(GZBlockEntities.CHEMICAL_PLANT.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.CHEMICAL_PLANT.get(), 2, 1));
    }
}
