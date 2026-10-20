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

    /** A charge that will go off at {@code at} (game time), from what was there when it was set off. */
    private record Pending(ChargeKind kind, BlockPos pos, Direction facing, int count, @Nullable UUID owner, long at) {}

    /** For GameTests: how many charges have gone off since the server started. */
    public static int fired;

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
     * A Remote Detonator pressed at {@code from}: every armed charge on {@code frequency} with a Remote fuze within
     * range goes off. Returns how many did.
     */
    public static int remote(ServerLevel level, Vec3 from, int frequency, @Nullable Entity by) {
        LongOpenHashSet set = LOADED.get(level.dimension());
        if (set == null) {
            return 0;
        }
        int n = 0;
        for (long l : set.toLongArray()) {
            BlockPos p = BlockPos.of(l);
            ChargeBlockEntity be = ChargeBlock.at(level, p);
            if (be != null && be.armed() && be.mode() == ChargeBlockEntity.Mode.REMOTE && be.frequency() == frequency
                    && Vec3.atCenterOf(p).distanceTo(from) <= DETONATOR_RANGE) {
                be.trigger(level, by);
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
            fire(level, p.kind(), p.pos(), p.facing(), p.count(), p.owner() == null ? null : level.getEntity(p.owner()));
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
            case TIMED_BOMB -> Blasts.detonate(level, c, Blasts.profile("timed_bomb"), by, 1f);
            case BREACHING -> breach(level, pos, facing, by);
            case THERMITE -> {
            }
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
