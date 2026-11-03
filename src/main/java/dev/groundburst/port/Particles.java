package dev.groundburst.port;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Minecraft 26.x's particle sends: to every player in the level, forced (seen from far off) or not. */
public final class Particles {
    private Particles() {}

    public static <T extends ParticleOptions> int send(ServerLevel level, T options, boolean force, double x, double y, double z, int count,
            double dx, double dy, double dz, double speed) {
        if (!force) {
            return level.sendParticles(options, x, y, z, count, dx, dy, dz, speed);
        }
        int sent = 0;
        for (ServerPlayer player : level.players()) {
            if (level.sendParticles(player, options, true, x, y, z, count, dx, dy, dz, speed)) {
                sent++;
            }
        }
        return sent;
    }
}
