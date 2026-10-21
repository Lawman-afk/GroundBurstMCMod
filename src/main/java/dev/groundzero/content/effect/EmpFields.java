package dev.groundzero.content.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.groundzero.content.ordnance.charge.Charges;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * EMP fields (DESIGN.md §21.4): a sphere that, for as long as it lasts, shuts down every machine in it (they lose half
 * their stored energy as it hits, then sit dead, and reboot when it ends: Launch Silos cannot launch, Radar Arrays and
 * Launch Computers go dark), and makes a missile flying through it a dud one time in two. As it goes up it makes armed
 * electronic charges safe (remote, timer and laser fuzes: landmines and bouncing mines are pressure plates and stay live).
 * Not saved: fields last seconds. (Vanilla redstone reading zero and powered items shutting down come later.)
 */
public final class EmpFields {
    private record Field(Vec3 centre, double radius, long until) {}

    private static final Map<ServerLevel, List<Field>> BY_LEVEL = new WeakHashMap<>();

    private EmpFields() {}

    /** An EMP goes off at {@code centre}: a field of {@code radius} blocks for {@code ticks}. */
    public static void add(ServerLevel level, Vec3 centre, double radius, int ticks, @Nullable Entity by) {
        BY_LEVEL.computeIfAbsent(level, k -> new ArrayList<>()).add(new Field(centre, radius, level.getGameTime() + ticks));
        Charges.empDisarm(level, centre, radius);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y + 0.5, centre.z, 120, radius * 0.25, 1, radius * 0.25, 0.4);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y + 0.5, centre.z, 60, 0.3, 0.3, 0.3, 0.8);
        level.playSound(null, centre.x, centre.y, centre.z, GZSounds.EMP_ZAP.get(), SoundSource.BLOCKS, 3f, 1f);
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(centre) < radius + 160) {
                PacketDistributor.sendToPlayer(p, new GasPayloads.Emp(centre, (float) radius, ticks));
            }
        }
    }

    /** Whether {@code pos} is inside a live field. */
    public static boolean inField(Level level, Vec3 pos) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        List<Field> fields = BY_LEVEL.get(server);
        if (fields == null || fields.isEmpty()) {
            return false;
        }
        long now = server.getGameTime();
        for (Field f : fields) {
            if (f.until() > now && f.centre().distanceToSqr(pos) <= f.radius() * f.radius()) {
                return true;
            }
        }
        return false;
    }

    public static boolean inField(Level level, BlockPos pos) {
        return inField(level, Vec3.atCenterOf(pos));
    }

    static void tick(ServerLevel level) {
        List<Field> fields = BY_LEVEL.get(level);
        if (fields != null) {
            long now = level.getGameTime();
            fields.removeIf(f -> f.until() <= now);
        }
    }

    static void clearAll() {
        BY_LEVEL.clear();
    }
}
