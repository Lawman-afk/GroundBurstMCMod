package dev.groundburst.legacy.client;

import dev.groundburst.legacy.net.Network;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The client half of {@link dev.groundburst.legacy.Lifecycle}: NeoForge's client registration events posted on the mod
 * bus (menu screens and client extensions at client setup, HUD layers as Forge registers its overlays), and its HUD layer
 * events posted from Forge's overlay events.
 */
public final class ClientLifecycle {
    private ClientLifecycle() {}

    public static void install(IEventBus modBus) {
        Network.clientPlayer = () -> Minecraft.getInstance().player;
        dev.groundburst.legacy.Regs.client = () -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.registryAccess();
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            modBus.post(new RegisterMenuScreensEvent());
            modBus.post(new RegisterClientExtensionsEvent());
        }));
        modBus.addListener((RegisterGuiOverlaysEvent event) -> modBus.post(new RegisterGuiLayersEvent(event)));
        MinecraftForge.EVENT_BUS.addListener((RenderGuiOverlayEvent.Pre event) -> {
            RenderGuiLayerEvent.Pre ours = new RenderGuiLayerEvent.Pre(event.getGuiGraphics(), event.getPartialTick(), event.getOverlay().id());
            if (MinecraftForge.EVENT_BUS.post(ours)) {
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderGuiOverlayEvent.Post event) -> MinecraftForge.EVENT_BUS.post(
                new RenderGuiLayerEvent.Post(event.getGuiGraphics(), event.getPartialTick(), event.getOverlay().id())));
    }
}
