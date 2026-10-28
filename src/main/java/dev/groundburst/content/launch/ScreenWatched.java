package dev.groundburst.content.launch;

import net.minecraft.server.level.ServerPlayer;

/** A block entity whose screen is fed while it is open (the Launch Computer, the Radar Array): who is watching. */
public interface ScreenWatched {
    void addViewer(ServerPlayer player);

    void removeViewer(ServerPlayer player);
}
