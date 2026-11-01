package dev.groundburst.content.launch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.ordnance.ThrownExplosive;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The laser spots being lased right now (DESIGN.md §17.13), by each player holding a Laser Designator to the eye, in each
 * dimension: where the spot is, on which network (0: none), and when it was last lased. Laser-guided missiles home on
 * them; Launch Control Stations show their network's. A spot not lased for a few ticks is gone.
 */
public final class Designators {
    /** A spot: whose, on which network (0: its designator is linked to none), where, when it was last lased, on a creature. */
    public record Spot(UUID player, long net, Vec3 pos, long seen, boolean onEntity) {}

    /** How far a designator reaches over loaded ground, and how far it picks out creatures, blocks. */
    public static final int RANGE = 256, ENTITY_RANGE = 128;
    /** Ticks a spot lasts after it was last lased. */
    private static final int FRESH = 4;
    private static final Map<ResourceKey<Level>, Map<UUID, Spot>> SPOTS = new HashMap<>();
    /** Missiles homing on each player's spot: [the game time counted at, how many then]. */
    private static final Map<UUID, long[]> HOMING = new HashMap<>();

    private Designators() {}

    /** {@code player} lases {@code pos} this tick. */
    public static void lase(ServerLevel level, UUID player, long net, Vec3 pos, boolean onEntity) {
        SPOTS.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(player, new Spot(player, net, pos, level.getGameTime(), onEntity));
    }

    /** {@code player} stopped lasing (or lost the spot). */
    public static void stop(ServerLevel level, UUID player) {
        Map<UUID, Spot> m = SPOTS.get(level.dimension());
        if (m != null) {
            m.remove(player);
        }
    }

    /** A player's spot right now (null: none). */
    public static @Nullable Spot spotOf(ServerLevel level, UUID player) {
        Map<UUID, Spot> m = SPOTS.get(level.dimension());
        Spot s = m == null ? null : m.get(player);
        return s != null && level.getGameTime() - s.seen() <= FRESH ? s : null;
    }

    /** The live spots of a network's designators (a station's map). */
    public static List<Spot> spots(ServerLevel level, long net) {
        List<Spot> out = new ArrayList<>();
        Map<UUID, Spot> m = SPOTS.get(level.dimension());
        if (m == null || net == 0) {
            return out;
        }
        long now = level.getGameTime();
        for (Spot s : m.values()) {
            if (s.net() == net && now - s.seen() <= FRESH) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * The spot a missile's seeker homes on: one its network's designators lase (or, a missile from no network, its
     * launcher's player's), within {@code reach} of where it was aimed; of those, the nearest its target now (so it holds
     * the spot it has).
     */
    public static @Nullable Spot spotFor(ServerLevel level, long net, @Nullable UUID owner, Vec3 aim, double reach, Vec3 near) {
        Map<UUID, Spot> m = SPOTS.get(level.dimension());
        if (m == null) {
            return null;
        }
        long now = level.getGameTime();
        Spot best = null;
        double bestD = Double.MAX_VALUE;
        for (Spot s : m.values()) {
            if (now - s.seen() > FRESH) {
                continue;
            }
            boolean ours = net != 0 ? s.net() == net : owner != null && owner.equals(s.player());
            if (!ours || horizontal(s.pos(), aim) > reach) {
                continue;
            }
            double d = s.pos().distanceToSqr(near);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** A missile homes on {@code player}'s spot this tick (counted for its designator's screen). */
    static void homing(ServerLevel level, UUID player) {
        long now = level.getGameTime();
        long[] c = HOMING.computeIfAbsent(player, k -> new long[2]);
        if (c[0] != now) {
            c[0] = now;
            c[1] = 0;
        }
        c[1]++;
    }

    /** How many missiles homed on {@code player}'s spot last tick. */
    public static int homingOn(ServerLevel level, UUID player) {
        long[] c = HOMING.get(player);
        return c != null && level.getGameTime() - c[0] <= 1 ? (int) c[1] : 0;
    }

    static void clear() {
        SPOTS.clear();
        HOMING.clear();
    }

    // --- the beam ---------------------------------------------------------------------------------------------------------
    /**
     * Where a designator's beam lands: {@code pos} where missiles home (a block's face, or a creature's middle), {@code surface}
     * where the spot is seen (the face, or where the beam meets the creature), and the creature if it is one.
     */
    public record Hit(Vec3 pos, Vec3 surface, @Nullable Entity entity) {}

    /**
     * Where {@code holder}'s beam lands: along its look, up to {@link #RANGE} blocks over loaded ground (it stops where the
     * ground is not loaded rather than load it), the first creature within {@link #ENTITY_RANGE} or else the first
     * block; null if it reaches neither, or smoke is in the way.
     */
    public static @Nullable Hit trace(Level level, Entity holder) {
        return trace(level, holder, holder.getEyePosition(), holder.getViewVector(1f));
    }

    /** As {@link #trace(Level, Entity)}, from {@code eye} along {@code look} (a frame's, on the client). */
    public static @Nullable Hit trace(Level level, Entity holder, Vec3 eye, Vec3 look) {
        double length = loaded(level, eye, look);
        Vec3 end = eye.add(look.scale(length));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, holder));
        double blockAt = block.getType() == HitResult.Type.MISS ? length : eye.distanceTo(block.getLocation());
        double entityAt = Math.min(blockAt, ENTITY_RANGE);
        Vec3 entityEnd = eye.add(look.scale(entityAt));
        EntityHitResult creature = ProjectileUtil.getEntityHitResult(holder, eye, entityEnd, holder.getBoundingBox().expandTowards(look.scale(entityAt)).inflate(1),
                e -> !e.isSpectator() && e.isPickable() && e != holder.getVehicle(), entityAt * entityAt);
        Hit hit;
        if (creature != null) {
            Entity e = creature.getEntity();
            hit = new Hit(e.getBoundingBox().getCenter(), creature.getLocation(), e);
        } else if (block.getType() != HitResult.Type.MISS) {
            hit = new Hit(block.getLocation(), block.getLocation(), null);
        } else {
            return null;
        }
        return smoke(level, eye, hit.surface()) ? null : hit;
    }

    /** How far along {@code look} from {@code eye} the ground stays loaded (to {@link #RANGE}). */
    private static double loaded(Level level, Vec3 eye, Vec3 look) {
        double step = 8, d = step;
        while (d <= RANGE) {
            if (!level.hasChunkAt(BlockPos.containing(eye.add(look.scale(d))))) {
                return d - step;
            }
            d += step;
        }
        return RANGE;
    }

    /** Whether a smoke (or gas) grenade's cloud lies across the beam from {@code from} to {@code to}. */
    public static boolean smoke(Level level, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, to).inflate(ThrownExplosive.SMOKE_RADIUS + 1);
        for (ThrownExplosive g : level.getEntitiesOfClass(ThrownExplosive.class, box, ThrownExplosive::smoking)) {
            if (segmentDistance(g.position(), from, to) <= g.smokeRadius()) {
                return true;
            }
        }
        return false;
    }

    private static double segmentDistance(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1e-9 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len));
        return p.distanceTo(a.add(ab.scale(t)));
    }
}
