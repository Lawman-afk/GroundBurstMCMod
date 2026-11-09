package dev.groundburst.content.launch;

import net.minecraft.server.level.ServerLevel;

/**
 * A network member that is neither a launcher nor a radar (DESIGN.md §17.17): a Seismograph or the Dead Hand. Linked
 * with Launch Codes as the others are, it reports to its network each {@link LaunchNetwork#REPORT} ticks while loaded
 * ({@link LaunchNetwork#tickMember}).
 */
public interface NetworkMember {
    /** What kind of member it is. */
    LaunchNetwork.Role networkRole();

    /**
     * What it reports now, as a launcher's report: its readiness ({@link LaunchSiloBlockEntity.Readiness} ordinal), a
     * countdown (-1: none), whether it is armed, a place it points to (the Dead Hand's strike, a seismograph's last
     * blast), a reach (its radius), what it holds (the Dead Hand's command rocket), whether it is working.
     */
    LaunchNetwork.Report networkReport(ServerLevel level);
}
