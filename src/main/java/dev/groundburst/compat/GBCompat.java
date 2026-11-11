package dev.groundburst.compat;

import dev.groundburst.GroundBurst;
import dev.groundburst.compat.create.CarriedSync;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import dev.groundburst.legacy.net.RegisterPayloadHandlersEvent;

/**
 * This build's support for other mods' moving structures (main's DESIGN.md §17.16, docs/BACKPORT.md): Create's
 * contraptions (trains, bearings, pistons, gantries...), only when Create is installed (Create Aeronautics' ships, which
 * the 1.21.1 build supports through Sable, have no 1.20.1 build). Nothing here touches Create's classes unless it is.
 */
@EventBusSubscriber(modid = GroundBurst.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class GBCompat {
    private GBCompat() {}

    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("create")) {
                dev.groundburst.compat.create.CreateCompat.register();
                dev.groundburst.compat.create.ContraptionHulls.register();
                GroundBurst.LOGGER.info("Create found: machines and launchers work on its moving contraptions, and blasts break them");
            }
        });
    }

    /** On the client: the copies of carried machines that menus and screens find (Create only). */
    @SubscribeEvent
    static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("create")) {
            event.enqueueWork(dev.groundburst.compat.create.CarriedClient::register);
        }
    }

    /**
     * The carried machines' looks for clients, and the blocks a blast or a freeze changed in a contraption (sent only where
     * Create carries something; optional for clients without it).
     */
    @SubscribeEvent
    static void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToClient(CarriedSync.TYPE, CarriedSync.CODEC,
                (payload, context) -> context.enqueueWork(() -> dev.groundburst.compat.create.CarriedSyncClient.apply(payload)));
        registrar.playToClient(dev.groundburst.compat.create.BlocksChanged.TYPE, dev.groundburst.compat.create.BlocksChanged.CODEC,
                (payload, context) -> context.enqueueWork(() -> dev.groundburst.compat.create.BlocksChangedClient.apply(payload)));
    }
}
