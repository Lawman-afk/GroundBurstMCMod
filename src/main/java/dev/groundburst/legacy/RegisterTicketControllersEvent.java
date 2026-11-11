package dev.groundburst.legacy;

import java.util.ArrayList;
import java.util.List;

import dev.groundburst.GroundBurst;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * NeoForge's ticket controller registration: posted on the mod bus at common setup ({@link Lifecycle}); Forge then asks
 * every registered controller's callback about the mod's tickets as each world loads.
 */
public final class RegisterTicketControllersEvent extends Event implements IModBusEvent {
    private static final List<TicketController> CONTROLLERS = new ArrayList<>();

    public RegisterTicketControllersEvent() {}

    public void register(TicketController controller) {
        CONTROLLERS.add(controller);
    }

    static void done() {
        ForgeChunkManager.setForcedChunkLoadingCallback(GroundBurst.MODID, (level, tickets) -> {
            for (TicketController c : CONTROLLERS) {
                if (c.callback != null) {
                    c.callback.validateTickets(level, tickets);
                }
            }
        });
    }
}
