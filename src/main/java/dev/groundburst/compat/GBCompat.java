package dev.groundburst.compat;

import dev.groundburst.GroundBurst;
import dev.groundburst.compat.create.CarriedSync;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * This build's support for other mods' moving structures (main's DESIGN.md §17.16, docs/BACKPORT.md): Sable's sub-levels
 * (the ships, planes and cars of Create Aeronautics) and Create's contraptions (trains, bearings, pistons, gantries...),
 * each only when that mod is installed. Nothing here touches either mod's classes unless it is.
 */
@EventBusSubscriber(modid = GroundBurst.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class GBCompat {
    private GBCompat() {}

    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("sable")) {
                dev.groundburst.compat.sable.SableFrames.register();
                dev.groundburst.compat.sable.SableHulls.register();
                GroundBurst.LOGGER.info("Sable found: launchers work aboard its sub-levels (Create Aeronautics' ships), and blasts break and shove them");
            }
            if (ModList.get().isLoaded("create")) {
                dev.groundburst.compat.create.CreateCompat.register();
                dev.groundburst.compat.create.ContraptionHulls.register();
                GroundBurst.LOGGER.info("Create found: machines and launchers work on its moving contraptions, and blasts break them");
            }
        });
    }

    /** On the client: the copies of carried machines that menus and screens find (Create only). */
    @SubscribeEvent
    static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("create")) {
            event.enqueueWork(dev.groundburst.compat.create.CarriedClient::register);
        }
    }

    /**
     * The carried machines' looks for clients, and the blocks a blast broke out of a contraption (sent only where Create
     * carries something; optional for clients without it).
     */
    @SubscribeEvent
    static void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToClient(CarriedSync.TYPE, CarriedSync.CODEC,
                (payload, context) -> context.enqueueWork(() -> dev.groundburst.compat.create.CarriedSyncClient.apply(payload)));
        registrar.playToClient(dev.groundburst.compat.create.BlocksGone.TYPE, dev.groundburst.compat.create.BlocksGone.CODEC,
                (payload, context) -> context.enqueueWork(() -> dev.groundburst.compat.create.BlocksGoneClient.apply(payload)));
    }
}
