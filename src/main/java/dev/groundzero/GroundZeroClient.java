package dev.groundzero;

import com.geckolib.renderer.GeoBlockRenderer;

import dev.groundzero.client.screen.MachineScreen;
import dev.groundzero.config.GZClientConfig;
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

        modBus.addListener(GroundZeroClient::registerScreens);
        modBus.addListener(GroundZeroClient::registerRenderers);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(GZMenus.ORE_CRUSHER.get(), MachineScreen::new);
        event.register(GZMenus.COMBUSTION_GENERATOR.get(), MachineScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GZBlockEntities.ORE_CRUSHER.get(), ctx -> new GeoBlockRenderer<>(ctx, GZBlockEntities.ORE_CRUSHER.get()));
        event.registerBlockEntityRenderer(GZBlockEntities.COMBUSTION_GENERATOR.get(), ctx -> new GeoBlockRenderer<>(ctx, GZBlockEntities.COMBUSTION_GENERATOR.get()));
    }
}
