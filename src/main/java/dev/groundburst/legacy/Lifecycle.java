package dev.groundburst.legacy;

import dev.groundburst.legacy.net.Network;
import dev.groundburst.legacy.net.RegisterPayloadHandlersEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Starts this branch's stand-ins for NeoForge's systems as the mod is made (the port calls {@link #install} first thing
 * in the mod's constructor): the capability bridge, data attachments and data maps on Forge's game bus, and, once every
 * part of the mod has added its listeners, NeoForge's registration events posted on the mod bus at common setup.
 */
public final class Lifecycle {
    private Lifecycle() {}

    private static boolean installed;

    public static void install(IEventBus modBus, ModContainer container) {
        if (installed) {
            return;
        }
        installed = true;
        CapabilityBridge.register();
        Attachments.register();
        DataMaps.register();
        dev.groundburst.legacy.loot.CopyComponentsFunction.register(modBus);
        modBus.addListener(Attachments::registerCapability);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            modBus.post(new RegisterPayloadHandlersEvent());
            Network.open(Network.version());
            modBus.post(new RegisterCapabilitiesEvent());
            modBus.post(new RegisterTicketControllersEvent());
            RegisterTicketControllersEvent.done();
            modBus.post(new RegisterDataMapTypesEvent());
        }));
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.groundburst.legacy.client.ClientLifecycle.install(modBus));
    }
}
