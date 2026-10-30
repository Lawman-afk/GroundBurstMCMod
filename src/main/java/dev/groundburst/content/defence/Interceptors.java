package dev.groundburst.content.defence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.groundburst.blast.Blasts;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.content.assembly.AssemblyStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Interceptors in the air (DESIGN.md §18.2), each in every level moved along every tick whether its chunk is loaded or
 * not, as missiles are ({@link Flights}): out of its cell straight up, then over onto the point where it meets its
 * missile, arriving on the tick the missile does. There it rolls its kill chance: a kill and the missile breaks up in
 * the air, its warhead never going off (a nuclear one flashes as it goes); a miss and the interceptor bursts alone. The
 * entity players see ({@link InterceptorEntity}) is only its picture, put where its chunk is loaded. Not saved.
 */
public final class Interceptors {
    /** The interceptor's own burst, and the bigger one of a missile breaking up. */
    public static final ResourceKey<dev.groundburst.blast.BlastProfile> BURST = Blasts.profile("interceptor_burst"),
            KILL = Blasts.profile("interceptor_kill");

    /** One interceptor's flight: from its cell over its bend to where it meets its missile, in its ticks. */
    static final class Shot {
        final UUID id = UUID.randomUUID();
        final BlockPos battery;
        final Vec3 from, bend, to;
        final int ticks;
        final UUID flight;
        final double kill;
        int age;
        private @Nullable InterceptorEntity shown;

        Shot(BlockPos battery, Vec3 from, Interception.Solution s, UUID flight, double kill) {
            this.battery = battery;
            this.from = from;
            this.to = s.at();
            this.ticks = Math.max(3, s.ticks());
            this.flight = flight;
            this.kill = kill;
            double d = from.distanceTo(this.to);
            this.bend = from.add(0, Math.min(60, d * 0.45), 0);      // up out of the cell first
        }

        /** Where it is {@code age} ticks after launch: a curve up and over, faster as it goes. */
        Vec3 at(int age) {
            double u = Math.pow(Math.min(1, age / (double) this.ticks), 1.1);
            double v = 1 - u;
            return this.from.scale(v * v).add(this.bend.scale(2 * v * u)).add(this.to.scale(u * u));
        }
    }

    private static final Map<ServerLevel, List<Shot>> SHOTS = new WeakHashMap<>();

    private Interceptors() {}

    /** How many interceptors are on their way to a missile. */
    public static int assigned(ServerLevel level, UUID flight) {
        List<Shot> shots = SHOTS.get(level);
        return shots == null ? 0 : (int) shots.stream().filter(s -> s.flight.equals(flight)).count();
    }

    /** How many are in the air in a level (for GameTests). */
    public static int inAir(ServerLevel level) {
        List<Shot> shots = SHOTS.get(level);
        return shots == null ? 0 : shots.size();
    }

    /** One fired from a battery at {@code battery}, out of its cell at {@code from}, to meet {@code flight} as solved. */
    static void fire(ServerLevel level, BlockPos battery, Vec3 from, Interception.Solution s, Flights.Flight flight, double kill) {
        Shot shot = new Shot(battery, from, s, flight.id(), kill);
        SHOTS.computeIfAbsent(level, l -> new ArrayList<>()).add(shot);
        proxy(level, shot, from, shot.at(1));
    }

    /** Once a level tick: every interceptor in the air moves on, and those that have reached their point burst. */
    public static void tick(ServerLevel level) {
        List<Shot> shots = SHOTS.get(level);
        if (shots == null || shots.isEmpty()) {
            return;
        }
        for (Shot shot : List.copyOf(shots)) {
            shot.age++;
            Vec3 was = shot.at(shot.age - 1), now = shot.at(shot.age);
            if (shot.age >= shot.ticks) {
                shots.remove(shot);
                resolve(level, shot, now);
            } else {
                proxy(level, shot, was, now);
            }
        }
    }

    static void forget(ServerLevel level) {
        SHOTS.remove(level);
    }

    public static void clear() {
        SHOTS.clear();
    }

    /** At its point: the kill roll against its missile, if that is still there (and near: it came on as reckoned). */
    private static void resolve(ServerLevel level, Shot shot, Vec3 at) {
        if (shot.shown != null) {
            shot.shown.discard();
            shot.shown = null;
        }
        Flights flights = Flights.of(level);
        Flights.Flight target = null;
        for (Flights.Flight f : flights.all()) {
            if (f.id().equals(shot.flight)) {
                target = f;
                break;
            }
        }
        boolean killed = false;
        if (target != null && target.position().distanceTo(at) < 24 && level.getRandom().nextDouble() < shot.kill) {
            Vec3 where = target.position();
            ChargeKind kind = AssemblyStats.kindOf(target.warhead());
            if (flights.shootDown(level, target)) {
                killed = true;
                Blasts.detonate(level, where, KILL, null);
                if (kind != null && kind.nuclear()) {
                    level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), true, true,
                            where.x, where.y, where.z, 3, 0.5, 0.5, 0.5, 0);
                }
            }
        }
        if (!killed) {
            Blasts.detonate(level, at, BURST, null);               // a miss (or its missile already gone): it bursts alone
        }
        if (level.getBlockEntity(shot.battery) instanceof InterceptorBatteryBlockEntity battery) {
            battery.reported(killed);
        }
    }

    /** The interceptor players see, where its chunk is loaded: found, made, or moved along (as missiles' are). */
    private static void proxy(ServerLevel level, Shot shot, Vec3 was, Vec3 now) {
        BlockPos at = BlockPos.containing(now);
        boolean loaded = level.hasChunkAt(at) && level.isPositionEntityTicking(at);
        InterceptorEntity e = shot.shown;
        if (e != null && (e.isRemoved() || !loaded)) {
            e.discard();
            shot.shown = null;
            e = null;
        }
        if (!loaded) {
            return;
        }
        if (e == null) {
            e = new InterceptorEntity(GBDefence.INTERCEPTOR.get(), level);
            e.setPos(now);
            if (!level.addFreshEntity(e)) {
                return;
            }
            shot.shown = e;
        }
        e.follow(was, now);
    }
}
