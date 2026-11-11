package dev.groundburst.port.net;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.groundburst.legacy.net.CustomPacketPayload;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import dev.groundburst.legacy.net.IPayloadHandler;

/**
 * NeoForge 26.x's client payload handler registration: client-bound payloads are registered with their codec on both
 * sides and their handler here, on the client only. 1.21.1 takes the handler with the codec, so the common side gives
 * {@link #handler} (which looks here when a payload comes) and this event, posted on the mod bus at client setup, fills it.
 */
public final class RegisterClientPayloadHandlersEvent extends Event implements IModBusEvent {
    private static final Map<CustomPacketPayload.Type<?>, IPayloadHandler<?>> HANDLERS = new ConcurrentHashMap<>();

    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, IPayloadHandler<T> handler) {
        HANDLERS.put(type, handler);
    }

    /** The handler a client-bound payload is registered with: the one the client gave, when it comes. */
    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> IPayloadHandler<T> handler(CustomPacketPayload.Type<T> type) {
        return (payload, context) -> {
            IPayloadHandler<T> h = (IPayloadHandler<T>) HANDLERS.get(type);
            if (h != null) {
                h.handle(payload, context);
            }
        };
    }

    /** Posts this event on the mod bus at client setup. */
    public static void post(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent e) -> modBus.post(new RegisterClientPayloadHandlersEvent()));
    }
}
