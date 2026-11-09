package dev.groundburst.content.launch;

import net.minecraft.server.level.ServerLevel;

/**
 * A screen that shows a launch network without being a member of it (the War Room Display, DESIGN.md §17.17): Launch
 * Codes used on it set the network it shows; sneaking, it shows none.
 */
public interface NetworkDisplay {
    /** The network it shows (0: none). */
    long shownNetwork();

    /** Shows network {@code id} from now on (0: none). */
    void show(ServerLevel level, long id);
}
