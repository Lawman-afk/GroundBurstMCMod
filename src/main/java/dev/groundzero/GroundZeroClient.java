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
        modBus.addListener(GroundZeroClient::registerRenderers);
        modBus.addListener(dev.groundzero.client.render.GZFluidModels::register);
        modBus.addListener(dev.groundzero.client.render.GZFluidModels::registerTints);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(GZMenus.ORE_CRUSHER.get(), MachineScreen::new);
        event.register(GZMenus.COMBUSTION_GENERATOR.get(), MachineScreen::new);
        event.register(GZMenus.ARC_FURNACE.get(), MachineScreen::new);
        event.register(GZMenus.INDUSTRIAL_PRESS.get(), MachineScreen::new);
        event.register(GZMenus.ELECTROLYTIC_SEPARATOR.get(), MachineScreen::new);
        event.register(GZMenus.CRYOGENIC_CONDENSER.get(), MachineScreen::new);
        event.register(GZMenus.CHEMICAL_PLANT.get(), MachineScreen::new);
        event.register(GZMenus.WATER_PUMP.get(), MachineScreen::new);
        event.register(GZMenus.DIESEL_GENERATOR.get(), MachineScreen::new);
        event.register(GZMenus.FISSION_REACTOR.get(), MachineScreen::new);
        event.register(GZMenus.PARTICLE_ACCELERATOR.get(), MachineScreen::new);
        event.register(GZMenus.RTG.get(), MachineScreen::new);
        event.register(GZMenus.CAPACITOR_BANK.get(), MachineScreen::new);
        event.register(GZMenus.BATTERY_CHARGER.get(), MachineScreen::new);
        event.register(GZMenus.DECONTAMINATION_SHOWER.get(), MachineScreen::new);
        event.register(GZMenus.CIRCUIT_FABRICATOR.get(), MachineScreen::new);
        event.register(GZMenus.GAS_CENTRIFUGE.get(), MachineScreen::new);
        event.register(GZMenus.FUSION_FUEL_SYNTHESIZER.get(), MachineScreen::new);
        event.register(GZMenus.EXOTIC_MATTER_FABRICATOR.get(), MachineScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GZBlockEntities.ORE_CRUSHER.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.ORE_CRUSHER.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.COMBUSTION_GENERATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.COMBUSTION_GENERATOR.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.ARC_FURNACE.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.ARC_FURNACE.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.INDUSTRIAL_PRESS.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.INDUSTRIAL_PRESS.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.ELECTROLYTIC_SEPARATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.ELECTROLYTIC_SEPARATOR.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.CRYOGENIC_CONDENSER.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.CRYOGENIC_CONDENSER.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.WATER_PUMP.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.WATER_PUMP.get(), 1));
        // Multiblocks: the formed model reaches beyond the controller (reactor 1 block each side, accelerator ring 8).
        event.registerBlockEntityRenderer(GZBlockEntities.FISSION_REACTOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.FISSION_REACTOR.get(), 4, 3));
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
        event.registerBlockEntityRenderer(GZBlockEntities.EXOTIC_MATTER_FABRICATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.EXOTIC_MATTER_FABRICATOR.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.FLUID_TANK.get(), ctx -> new dev.groundzero.client.render.FluidTankRenderer());
        // The formed Chemical Plant spans 2x2x2 from its controller in any rotation.
        event.registerBlockEntityRenderer(GZBlockEntities.CHEMICAL_PLANT.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.CHEMICAL_PLANT.get(), 2, 1));
    }
}
