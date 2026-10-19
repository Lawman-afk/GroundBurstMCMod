package dev.groundzero;

import dev.groundzero.client.recipe.ClientRecipes;
import dev.groundzero.client.render.MachineGeoRenderer;
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
        modBus.addListener(GroundZeroClient::registerScreens);
        modBus.addListener(GroundZeroClient::registerRenderers);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(GZMenus.ORE_CRUSHER.get(), MachineScreen::new);
        event.register(GZMenus.COMBUSTION_GENERATOR.get(), MachineScreen::new);
        event.register(GZMenus.ARC_FURNACE.get(), MachineScreen::new);
        event.register(GZMenus.INDUSTRIAL_PRESS.get(), MachineScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GZBlockEntities.ORE_CRUSHER.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.ORE_CRUSHER.get(), 1));
        event.registerBlockEntityRenderer(GZBlockEntities.COMBUSTION_GENERATOR.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.COMBUSTION_GENERATOR.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.ARC_FURNACE.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.ARC_FURNACE.get(), 2));
        event.registerBlockEntityRenderer(GZBlockEntities.INDUSTRIAL_PRESS.get(), ctx -> new MachineGeoRenderer<>(ctx, GZBlockEntities.INDUSTRIAL_PRESS.get(), 2));
    }
}
