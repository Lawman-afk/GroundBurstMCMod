package dev.groundburst.legacy.net;

import java.util.concurrent.CompletableFuture;

import net.minecraft.world.entity.player.Player;

/**
 * NeoForge's payload context, over Forge 1.20.1's network context: the player it came from (on the server) or the
 * client's own player, and work for the main thread (the handlers already run there, so it runs at once).
 */
public interface IPayloadContext {
    Player player();

    default CompletableFuture<Void> enqueueWork(Runnable task) {
        task.run();
        return CompletableFuture.completedFuture(null);
    }

    default <T> CompletableFuture<T> enqueueWork(java.util.function.Supplier<T> task) {
        return CompletableFuture.completedFuture(task.get());
    }

    /** Sends a payload back where this one came from. */
    void reply(CustomPacketPayload payload);
}
