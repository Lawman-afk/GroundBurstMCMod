package dev.groundzero.content.ordnance.charge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.groundzero.blast.Blasts;
import dev.groundzero.content.ordnance.GZOrdnance;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Placed charges going off (DESIGN.md §9.4, §14): the charges loaded in each level (for the Remote Detonator), charges
 * caught in a blast going off a few ticks later (sympathetic detonation), and what each kind does: C4 and the timed
 * bomb blast, the breaching charge cuts a hole, thermite burns a shaft.
 */
public final class Charges {
    /** How far a Remote Detonator reaches, blocks. */
    public static final int DETONATOR_RANGE = 256;
    /** Breaching: the hole's half-width and depth. Thermite: the shaft's depth, and the hardest it burns through. */
    public static final int BREACH_HALF = 1, BREACH_DEPTH = 3, THERMITE_DEPTH = 8;
    public static final float THERMITE_MAX_RESISTANCE = 50, BREACH_MAX_RESISTANCE = 1200;
    /** Thermite: ticks of flare before it starts to melt, and ticks per block melted. */
    public static final int THERMITE_FLARE = 20, THERMITE_STEP = 6;

    private static final Map<ResourceKey<Level>, LongOpenHashSet> LOADED = new HashMap<>();
    private static final Map<ServerLevel, List<Pending>> PENDING = new WeakHashMap<>();

    /**
     * A charge that will go off at {@code at} (game time), from what was there when it was set off; or, for a later
     * {@code stage} of a bomb already going off, the next step (the thermobaric bomb igniting its cloud, the bunker
     * buster boring on with {@code count} blocks of depth left).
     */
    private record Pending(ChargeKind kind, BlockPos pos, Direction facing, int count, @Nullable UUID owner, long at, int stage) {
        Pending(ChargeKind kind, BlockPos pos, Direction facing, int count, @Nullable UUID owner, long at) {
            this(kind, pos, facing, count, owner, at, FIRE);
        }
    }

    private static final int FIRE = 0, IGNITE = 1, BORE = 2;

    /** For GameTests: how many charges have gone off since the server started. */
    public static int fired;
    /**
     * For GameTests: every bomb's reach (blast, napalm, bomblets, indoor overpressure) times this, so a test's bomb stays
     * in its own plot. Always 1 in play.
     */
    public static float bombScale = 1f;

    private Charges() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                tick(level);
            }
        });
        // A new world (or a restarted integrated server) starts with nothing known or pending.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            LOADED.clear();
            PENDING.clear();
        });
    }

    static void track(ServerLevel level, BlockPos pos) {
        LOADED.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet()).add(pos.asLong());
    }

    static void untrack(ServerLevel level, BlockPos pos) {
        LongOpenHashSet set = LOADED.get(level.dimension());
        if (set != null) {
            set.remove(pos.asLong());
        }
    }

    /**
     * A Remote Detonator pressed at {@code from}: every armed charge on {@code frequency} within range goes off, or starts
     * its timer. Returns how many did.
     */
    public static int remote(ServerLevel level, Vec3 from, int frequency, @Nullable Entity by) {
        return remote(level, from, frequency, by, DETONATOR_RANGE);
    }

    /** As {@link #remote(ServerLevel, Vec3, int, Entity)} within {@code range} (GameTests keep to their own plot). */
    public static int remote(ServerLevel level, Vec3 from, int frequency, @Nullable Entity by, double range) {
        LongOpenHashSet set = LOADED.get(level.dimension());
        if (set == null) {
            return 0;
        }
        int n = 0;
        for (long l : set.toLongArray()) {
            BlockPos p = BlockPos.of(l);
            ChargeBlockEntity be = ChargeBlock.at(level, p);
            if (be != null && be.kind().remote && be.frequency() == frequency && Vec3.atCenterOf(p).distanceTo(from) <= range
                    && be.trigger(level, by)) {
                n++;
            }
        }
        return n;
    }

    /** A charge caught in a blast (or broken while its timer runs): it goes off 2-5 ticks later, armed or not. */
    public static void caughtInBlast(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChargeBlock block) || block.kind == ChargeKind.THERMITE) {
            return;                                    // thermite only burns when lit; a blast just scatters it
        }
        ChargeBlockEntity be = ChargeBlock.at(level, pos);
        PENDING.computeIfAbsent(level, k -> new ArrayList<>()).add(new Pending(block.kind, pos.immutable(), state.getValue(ChargeBlock.FACING),
                state.hasProperty(ChargeBlock.COUNT) ? state.getValue(ChargeBlock.COUNT) : 1, be == null ? null : be.owner(),
                level.getGameTime() + 2 + level.getRandom().nextInt(4)));
    }

    private static void tick(ServerLevel level) {
        List<Pending> list = PENDING.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<Pending> due = new ArrayList<>();
        list.removeIf(p -> {
            if (p.at() <= now) {
                due.add(p);
                return true;
            }
            return false;
        });
        for (Pending p : due) {
            Entity by = p.owner() == null ? null : level.getEntity(p.owner());
            switch (p.stage()) {
                case IGNITE -> thermobaricIgnite(level, p.pos(), by);
                case BORE -> bore(level, p, by);
                default -> fire(level, p.kind(), p.pos(), p.facing(), p.count(), by);
            }
        }
    }

    /** Sets the charge at {@code pos} off now: removes it and does what it does. */
    static void detonate(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity by) {
        if (!(state.getBlock() instanceof ChargeBlock block)) {
            return;
        }
        level.removeBlock(pos, false);
        fire(level, block.kind, pos, state.getValue(ChargeBlock.FACING), state.hasProperty(ChargeBlock.COUNT) ? state.getValue(ChargeBlock.COUNT) : 1, by);
    }

    private static void fire(ServerLevel level, ChargeKind kind, BlockPos pos, Direction facing, int count, @Nullable Entity by) {
        if (level.getBlockState(pos).getBlock() instanceof ChargeBlock) {
            level.removeBlock(pos, false);
        }
        fired++;
        Vec3 c = Vec3.atCenterOf(pos);
        switch (kind) {
            case C4 -> {
                // Against the face it is stuck to; four stacked make a blast 1.6 times as wide (4.5 n^1/3).
                Vec3 at = c.add(Vec3.atLowerCornerOf(facing.getOpposite().getUnitVec3i()).scale(0.35));
                Blasts.detonate(level, at, Blasts.profile("c4"), by, (float) Math.cbrt(count));
            }
            case TIMED_BOMB -> Blasts.detonate(level, c.add(0, -0.2, 0), Blasts.profile("timed_bomb"), by, 1f);
            case BREACHING -> breach(level, pos, facing, by);
            case THERMITE -> {
            }
            case LANDMINE -> Blasts.detonate(level, c.add(0, -0.35, 0), Blasts.profile("landmine"), by, 1f);
            case BOUNCING_MINE -> {
                level.playSound(null, pos, GZOrdnance.MINE_POP.get(), SoundSource.BLOCKS, 1.2f, 1f);
                level.sendParticles(ParticleTypes.POOF, c.x, c.y - 0.2, c.z, 10, 0.3, 0.1, 0.3, 0.05);
                dev.groundzero.content.ordnance.ThrownExplosive.popUp(level, c.add(0, -0.3, 0), by);
            }
            case CLAYMORE -> claymore(level, pos, facing, by);
            case HE_BOMB, HEAVY_BOMB, MOAB -> Blasts.detonate(level, c.add(0, -0.2, 0), Blasts.profile(kind.id), by, bombScale);
            case NAPALM_BOMB -> {
                Blasts.detonate(level, c, Blasts.profile("napalm_bomb"), by, bombScale);
                napalm(level, pos, facing);
            }
            case CLUSTER_BOMB -> cluster(level, c, by);
            case THERMOBARIC_BOMB -> {
                // The fuel cloud spreads first; it is lit 0.6 s later.
                level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 160, 3.5, 1.5, 3.5, 0.25);
                level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 40, 1.5, 0.6, 1.5, 0.4);
                level.playSound(null, pos, GZOrdnance.FUEL_DISPERSE.get(), SoundSource.BLOCKS, 2f, 1f);
                schedule(level, new Pending(kind, pos, facing, 0, by == null ? null : by.getUUID(), level.getGameTime() + THERMOBARIC_DELAY, IGNITE));
            }
            // Set off where it stands it is just a bomb; it bores only when delivered at speed (Phase 9, see penetrate).
            case BUNKER_BUSTER -> Blasts.detonate(level, c.add(0, -0.2, 0), Blasts.profile("bunker_buster"), by, bombScale);
        }
    }

    // --- bombs ------------------------------------------------------------------------------------------------------------
    /** Thermobaric: ticks from dispersal to ignition; extra damage to anything with no open sky above it, and how far. */
    public static final int THERMOBARIC_DELAY = 12;
    public static final float THERMOBARIC_INDOOR_DAMAGE = 20, THERMOBARIC_REACH = 24;
    /** Bunker buster: blocks of depth it bores (obsidian-hard blocks cost 10), and blocks a tick. */
    public static final int BUNKER_DEPTH = 40, BUNKER_HARD_COST = 10, BUNKER_SPEED = 2;
    /** Cluster: bomblets, and the chance each is a dud (left lying as a live landmine). */
    public static final int CLUSTER_BOMBLETS = 24;
    public static final float CLUSTER_DUD_CHANCE = 0.05f;
    /** Napalm: the ellipse's half-length (along the bomb) and half-width. */
    public static final float NAPALM_HALF_LENGTH = 15, NAPALM_HALF_WIDTH = 6;

    private static void schedule(ServerLevel level, Pending p) {
        PENDING.computeIfAbsent(level, k -> new ArrayList<>()).add(p);
    }

    /** For GameTests: whether something is waiting to happen at {@code pos} (a thermobaric cloud about to be lit). */
    public static boolean pendingAt(ServerLevel level, BlockPos pos) {
        List<Pending> list = PENDING.get(level);
        return list != null && list.stream().anyMatch(p -> p.pos().equals(pos));
    }

    /** The thermobaric cloud lit: the blast, then extra harm to anything sheltering (the overpressure fills rooms and caves). */
    private static void thermobaricIgnite(ServerLevel level, BlockPos pos, @Nullable Entity by) {
        Vec3 c = Vec3.atCenterOf(pos);
        Blasts.detonate(level, c, Blasts.profile("thermobaric_bomb"), by, bombScale);
        double reach = THERMOBARIC_REACH * bombScale;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(reach))) {
            double d = e.position().distanceTo(c);
            if (d <= reach && !level.canSeeSky(BlockPos.containing(e.getEyePosition()))) {
                e.damageCooldownTime = 0;
                e.hurtServer(level, Blasts.damageSource(level, by, c), THERMOBARIC_INDOOR_DAMAGE * (float) (1 - d / reach));
            }
        }
    }

    /**
     * A bunker buster arriving at speed (a missile, a drop: delivery systems come in Phase 9) at {@code pos}: it bores
     * straight down, then goes off where it stops.
     */
    public static void penetrate(ServerLevel level, BlockPos pos, @Nullable Entity by) {
        level.playSound(null, pos, GZOrdnance.BOMB_DRILL.get(), SoundSource.BLOCKS, 2f, 1f);
        schedule(level, new Pending(ChargeKind.BUNKER_BUSTER, pos.immutable(), Direction.NORTH, BUNKER_DEPTH, by == null ? null : by.getUUID(),
                level.getGameTime() + 1, BORE));
    }

    /**
     * A step of the bunker buster boring down: up to two blocks a tick, each costing 1 of its 40 blocks of depth (10 if
     * obsidian-hard); it stops at anything unbreakable or the bottom of the world, and goes off where it stops.
     */
    private static void bore(ServerLevel level, Pending p, @Nullable Entity by) {
        BlockPos at = p.pos();
        int left = p.count();
        for (int i = 0; i < BUNKER_SPEED; i++) {
            BlockPos next = at.below();
            BlockState s = level.getBlockState(next);
            int cost = s.getBlock().getExplosionResistance() >= BREACH_MAX_RESISTANCE ? BUNKER_HARD_COST : 1;
            if (next.getY() < level.getMinY() || s.getDestroySpeed(level, next) < 0 || cost > left) {
                Blasts.detonate(level, Vec3.atCenterOf(at), Blasts.profile("bunker_buster"), by, bombScale);
                return;
            }
            if (!s.isAir()) {
                if (s.getBlock() instanceof ChargeBlock) {
                    caughtInBlast(level, next, s);
                }
                level.levelEvent(2001, next, Block.getId(s));
                level.setBlock(next, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            left -= cost;
            at = next;
        }
        Vec3 m = Vec3.atCenterOf(at);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y + 1, m.z, 4, 0.2, 0.3, 0.2, 0.02);
        schedule(level, new Pending(p.kind(), at, p.facing(), left, p.owner(), level.getGameTime() + 1, BORE));
    }

    /** Napalm over the ground in an ellipse centred on the bomb, long along its body, most of it alight. */
    private static void napalm(ServerLevel level, BlockPos pos, Direction facing) {
        Vec3 ahead = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        Vec3 side = Vec3.atLowerCornerOf(facing.getClockWise().getUnitVec3i());
        float halfLength = NAPALM_HALF_LENGTH * bombScale, halfWidth = Math.max(1, NAPALM_HALF_WIDTH * bombScale);
        Vec3 centre = Vec3.atCenterOf(pos);
        BlockState fire = GZOrdnance.NAPALM_FIRE.get().defaultBlockState();
        var r = level.getRandom();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int a = (int) -halfLength; a <= halfLength; a++) {
            for (int b = (int) -halfWidth; b <= halfWidth; b++) {
                double e = (a * a) / (halfLength * halfLength) + (b * b) / (halfWidth * halfWidth);
                if (e > 1 || r.nextFloat() > 0.9f - 0.3f * e) {
                    continue;
                }
                Vec3 p = centre.add(ahead.scale(a)).add(side.scale(b));
                at.set(p.x, pos.getY() + 3, p.z);
                for (int i = 0; i < 8 && level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty(); i++) {
                    at.move(Direction.DOWN);
                }
                BlockState here = level.getBlockState(at);
                if ((here.isAir() || here.canBeReplaced() && here.getFluidState().isEmpty()) && fire.canSurvive(level, at)) {
                    level.setBlockAndUpdate(at, fire);
                }
            }
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(halfLength, 4, halfLength))) {
            if (!e.fireImmune()) {
                e.igniteForSeconds(10);
            }
        }
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 2f, 0.6f);
    }

    /** The cluster bomb's casing bursts and throws its bomblets out, most of them 6-18 blocks away. */
    private static void cluster(ServerLevel level, Vec3 c, @Nullable Entity by) {
        Blasts.detonate(level, c, Blasts.profile("cluster_bomb"), by, bombScale);
        var r = level.getRandom();
        for (int i = 0; i < CLUSTER_BOMBLETS; i++) {
            double a = i * 2.39996 + r.nextDouble() * 0.4;
            double speed = (0.2 + Math.sqrt(r.nextDouble()) * 0.45) * bombScale;
            Vec3 v = new Vec3(Math.cos(a) * speed, 0.55 + r.nextDouble() * 0.25, Math.sin(a) * speed);
            dev.groundzero.content.ordnance.ThrownExplosive.bomblet(level, c.add(0, 0.6, 0), v, by, r.nextFloat() < CLUSTER_DUD_CHANCE);
        }
    }

    // --- mines ------------------------------------------------------------------------------------------------------------
    /** The claymore's tripwire: how far it reaches, and where it starts. */
    public static final double LASER_RANGE = 6;
    /** Claymore: fragments, the cone's half-angle (degrees), and how fast they fly (they reach about 12 blocks). */
    public static final int CLAYMORE_FRAGMENTS = 60;
    public static final double CLAYMORE_HALF_ANGLE = 30;

    public static Vec3 laserStart(BlockPos pos, BlockState state) {
        Vec3 dir = Vec3.atLowerCornerOf(state.getValue(ChargeBlock.FACING).getUnitVec3i());
        return Vec3.atBottomCenterOf(pos).add(0, 4.5 / 16, 0).add(dir.scale(0.1));    // its lens
    }

    /** How far the claymore's laser reaches before something solid stops it. */
    public static double laserLength(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        Vec3 from = laserStart(pos, state);
        Vec3 dir = Vec3.atLowerCornerOf(state.getValue(ChargeBlock.FACING).getUnitVec3i());
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (double d = 0.4; d < LASER_RANGE; d += 0.25) {
            Vec3 p = from.add(dir.scale(d));
            at.set(p.x, p.y, p.z);
            if (!at.equals(pos) && !level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                return d;
            }
        }
        return LASER_RANGE;
    }

    /** Whether an armed mine senses something: a creature on its plate, or across the claymore's laser. */
    static boolean sensed(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChargeBlock block)) {
            return false;
        }
        AABB zone = switch (block.kind.sensor) {
            case PRESSURE -> new AABB(pos.getX() + 0.15, pos.getY(), pos.getZ() + 0.15, pos.getX() + 0.85, pos.getY() + 0.4, pos.getZ() + 0.85);
            case TRIPWIRE -> {
                Vec3 from = laserStart(pos, state);
                Vec3 to = from.add(Vec3.atLowerCornerOf(state.getValue(ChargeBlock.FACING).getUnitVec3i()).scale(laserLength(level, pos, state)));
                yield new AABB(from, to).inflate(0.05);
            }
            case NONE -> null;
        };
        return zone != null && !level.getEntitiesOfClass(LivingEntity.class, zone, e -> e.isAlive() && !e.isSpectator()).isEmpty();
    }

    /**
     * The claymore: a small flash and bang at its face (no blast of its own) and 60 fragments spread evenly across a 60
     * degree cone ahead, at five heights from level to a little upward, so anything standing in the cone is struck
     * several times: lethal out to about half their 10-block reach.
     */
    private static void claymore(ServerLevel level, BlockPos pos, Direction facing, @Nullable Entity by) {
        Vec3 dir = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        Vec3 front = Vec3.atBottomCenterOf(pos).add(0, 0.25, 0).add(dir.scale(0.25));
        level.sendParticles(ParticleTypes.EXPLOSION, front.x, front.y, front.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, front.x, front.y, front.z, 8, 0.2, 0.1, 0.2, 0.03);
        level.sendParticles(ParticleTypes.FLAME, front.x, front.y, front.z, 10, 0.1, 0.1, 0.1, 0.08);
        level.playSound(null, front.x, front.y, front.z, dev.groundzero.registry.GZSounds.blast("small", 0), SoundSource.BLOCKS, 1.2f, 1.3f);
        var r = level.getRandom();
        double[] heights = {0, 2, 4, 1, 3};                     // degrees up, cycled across the cone
        for (int i = 0; i < CLAYMORE_FRAGMENTS; i++) {
            double yaw = Math.toRadians(-CLAYMORE_HALF_ANGLE + (i + 0.5) * 2 * CLAYMORE_HALF_ANGLE / CLAYMORE_FRAGMENTS + (r.nextDouble() - 0.5) * 0.8);
            double pitch = Math.toRadians(heights[i % heights.length] + (r.nextDouble() - 0.5) * 0.6);
            Vec3 d = dir.yRot((float) yaw);
            d = new Vec3(d.x * Math.cos(pitch), Math.sin(pitch), d.z * Math.cos(pitch)).normalize();
            level.addFreshEntity(new dev.groundzero.content.ordnance.Shrapnel(level, by, front.add(d.scale(0.2)),
                    d.scale(0.85 + r.nextDouble() * 0.2)).claymore());
        }
    }

    /**
     * The breaching charge: a clean 3x3 hole 3 deep into what it is stuck to (anything short of obsidian), a sharp crack
     * on this side, and whatever is in or just past the hole knocked through it and hurt.
     */
    private static void breach(ServerLevel level, BlockPos pos, Direction facing, @Nullable Entity by) {
        Direction into = facing.getOpposite();
        Direction u = into.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        Direction v = into.getAxis() == Direction.Axis.Y ? Direction.EAST : into.getClockWise();
        for (int d = 1; d <= BREACH_DEPTH; d++) {
            for (int a = -BREACH_HALF; a <= BREACH_HALF; a++) {
                for (int b = -BREACH_HALF; b <= BREACH_HALF; b++) {
                    BlockPos p = pos.relative(into, d).relative(u, a).relative(v, b);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, p) < 0
                            || s.getBlock().getExplosionResistance() >= BREACH_MAX_RESISTANCE) {
                        continue;
                    }
                    if (s.getBlock() instanceof ChargeBlock) {
                        caughtInBlast(level, p, s);
                    }
                    level.levelEvent(2001, p, Block.getId(s));          // break particles and sound
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        Blasts.detonate(level, Vec3.atCenterOf(pos), Blasts.profile("breaching_charge"), by, 1f);
        Vec3 push = Vec3.atLowerCornerOf(into.getUnitVec3i());
        AABB through = new AABB(Vec3.atCenterOf(pos), Vec3.atCenterOf(pos.relative(into, BREACH_DEPTH + 4))).inflate(1.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, through)) {
            e.hurtServer(level, Blasts.damageSource(level, by, Vec3.atCenterOf(pos)), 6f);
            e.push(push.x * 1.2, push.y * 1.2 + 0.25, push.z * 1.2);
        }
    }

    /**
     * A tick of burning thermite at {@code pos}, {@code burned} ticks in: a flare, then one block melted every few
     * ticks along a 1x1 shaft into what it is stuck to, until it has gone 8 deep or reaches something too hard (or
     * water). Molten slag is left at the bottom. True when it has burned out (and gone).
     */
    static boolean thermite(ServerLevel level, BlockPos pos, BlockState state, int burned) {
        Direction into = state.getValue(ChargeBlock.FACING).getOpposite();
        Vec3 c = Vec3.atCenterOf(pos);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.3);
        level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.02);
        if (burned % 20 == 0) {
            level.playSound(null, pos, GZOrdnance.THERMITE_BURN.get(), SoundSource.BLOCKS, 1.2f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(1))) {
            if (!e.fireImmune()) {
                e.igniteForSeconds(5);
                e.hurtServer(level, level.damageSources().inFire(), 2f);
            }
        }
        if (burned < THERMITE_FLARE || (burned - THERMITE_FLARE) % THERMITE_STEP != 0) {
            return false;
        }
        int step = (burned - THERMITE_FLARE) / THERMITE_STEP + 1;
        BlockPos p = pos.relative(into, step);
        BlockState s = level.getBlockState(p);
        boolean stop = step > THERMITE_DEPTH || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, p) < 0
                || s.getBlock().getExplosionResistance() >= THERMITE_MAX_RESISTANCE;
        if (!stop) {
            if (!s.isAir()) {
                if (s.getBlock() instanceof ChargeBlock) {
                    caughtInBlast(level, p, s);
                }
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                Vec3 m = Vec3.atCenterOf(p);
                level.sendParticles(ParticleTypes.LAVA, m.x, m.y, m.z, 6, 0.3, 0.3, 0.3, 0);
                level.playSound(null, p, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.4f);
            }
            return false;
        }
        // Burned out: slag where it stopped (if it got anywhere), and the pot is gone.
        if (!s.getFluidState().isEmpty()) {
            Vec3 m = Vec3.atCenterOf(p);
            level.sendParticles(ParticleTypes.CLOUD, m.x, m.y, m.z, 20, 0.4, 0.4, 0.4, 0.05);
            level.playSound(null, p, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.8f);
        }
        BlockPos last = pos.relative(into, step - 1);
        if (step > 1 && level.getBlockState(last).isAir()) {
            level.setBlock(last, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.removeBlock(pos, false);
        return true;
    }
}
