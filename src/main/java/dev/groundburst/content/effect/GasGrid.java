package dev.groundburst.content.effect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import dev.groundburst.content.ordnance.Toxins;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Toxic gas as a grid (DESIGN.md §16.7): each open block holds a concentration of each {@link GasAgent}. Every step
 * (5 ticks) half of what a block holds flows out into the open blocks beside it, more of it down than up (the gases are
 * heavy and sink into hollows), a little more downwind (the wind turns now and then); solid blocks, doors, panes and
 * walls stop it (only low blocks such as slabs and carpets let it over), and it decays. Whatever breathes it (its feet
 * or its eyes in a gassed block) takes a dose every half second, cut by a suit ({@link Toxins}). One block a cell, not
 * the 2-block cells first planned: a one-block wall has to keep gas out.
 *
 * <p>Gas is not saved with the world: it clears within a couple of minutes anyway.
 */
public final class GasGrid {
    public static final int STEP = 5, DOSE = 10, SYNC = 10;
    /** Concentration at which a dose is at full strength; below VISIBLE nothing is drawn, below GONE it is dropped. */
    public static final float FULL = 0.3f, VISIBLE = 0.04f, GONE = 0.01f;
    /** How often (steps) a thick anthrax cloud sours the soil under it, and the chance each block of it does then. */
    static final int SOUR_EVERY = 4;
    static final float SOUR_CHANCE = 0.02f;
    /** Share of a block's gas that moves on each step; the share that goes up; how hard the wind pushes. */
    private static final float SPREAD = 0.5f, RISE = 0.05f, WIND = 0.6f;
    /** Blocks of gas a level may hold; past this it decays faster. Blocks of it sent to each player at most. */
    private static final int MAX_CELLS = 60_000, MAX_SENT = 3_000;
    private static final double SEND_RANGE = 80;
    private static final Map<ServerLevel, GasGrid> BY_LEVEL = new WeakHashMap<>();
    private static final GasAgent[] AGENTS = GasAgent.values();
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private Long2ObjectOpenHashMap<float[]> cells = new Long2ObjectOpenHashMap<>();
    private double windAngle;
    /** Steps run (for the soil, every few). */
    private int steps;
    private final Set<UUID> sentTo = new HashSet<>();
    private AABB bounds = new AABB(0, 0, 0, 0, 0, 0);

    private GasGrid(ServerLevel level) {
        this.windAngle = level.getRandom().nextDouble() * 2 * Math.PI;
    }

    public static GasGrid of(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, GasGrid::new);
    }

    static void clearAll() {
        BY_LEVEL.clear();
    }

    /** Whether gas can be in (and pass through) a block: air, plants, and low blocks; not solids, fluids, doors, walls or panes. */
    public static boolean passable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) < 0.6;
    }

    /** Adds {@code amount} of {@code agent} to the block at {@code pos} (or the one above it if that one is shut). */
    public void emit(ServerLevel level, BlockPos pos, GasAgent agent, float amount) {
        BlockPos at = passable(level, pos) ? pos : pos.above();
        if (!passable(level, at)) {
            return;
        }
        this.cells.computeIfAbsent(at.asLong(), k -> new float[AGENTS.length])[agent.ordinal()] += amount;
    }

    /** Fills the open blocks within {@code radius} of {@code centre} (a half-sphere over the ground) with {@code agent}. */
    public void fill(ServerLevel level, Vec3 centre, float radius, GasAgent agent, float concentration) {
        int r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(centre);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r / 2; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (passable(level, p)) {
                        float[] v = this.cells.computeIfAbsent(p.asLong(), k -> new float[AGENTS.length]);
                        v[agent.ordinal()] = Math.max(v[agent.ordinal()], concentration);
                    }
                }
            }
        }
    }

    /** How much of {@code agent} is in the block at {@code pos}. */
    public float concentration(BlockPos pos, GasAgent agent) {
        float[] v = this.cells.get(pos.asLong());
        return v == null ? 0 : v[agent.ordinal()];
    }

    /** How many blocks hold gas. */
    public int size() {
        return this.cells.size();
    }

    void tick(ServerLevel level) {
        if (this.cells.isEmpty()) {
            if (!this.sentTo.isEmpty() && level.getGameTime() % SYNC == 0) {
                sync(level);                                   // tell clients it has all gone
            }
            return;
        }
        long t = level.getGameTime();
        if (t % 1200 == 0) {
            this.windAngle += (level.getRandom().nextDouble() - 0.5) * 1.5;   // the wind backs and veers
        }
        if (t % STEP == 0) {
            step(level);
        }
        if (t % DOSE == 0) {
            dose(level);
        }
        if (t % SYNC == 0) {
            sync(level);
        }
    }

    /** One step: every block's gas flows a share into the open blocks round it (down most), the rest stays, and it all decays. */
    private void step(ServerLevel level) {
        Long2ObjectOpenHashMap<float[]> next = new Long2ObjectOpenHashMap<>(this.cells.size() * 2);
        Long2ByteOpenHashMap open = new Long2ByteOpenHashMap();
        float overflow = this.cells.size() > MAX_CELLS ? 0.05f : 0;
        double wx = Math.cos(this.windAngle), wz = Math.sin(this.windAngle);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE,
                maxZ = -Double.MAX_VALUE;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (Long2ObjectMap.Entry<float[]> e : this.cells.long2ObjectEntrySet()) {
            long key = e.getLongKey();
            BlockPos p = BlockPos.of(key);
            if (!isOpen(level, open, p.asLong(), p)) {
                continue;                                      // the block filled up: its gas is gone
            }
            float[] v = e.getValue();
            // Where it can go: down, up, and the four sides (each weighted by the wind).
            long down = BlockPos.asLong(p.getX(), p.getY() - 1, p.getZ()), up = BlockPos.asLong(p.getX(), p.getY() + 1, p.getZ());
            boolean openDown = isOpen(level, open, down, q.set(p.getX(), p.getY() - 1, p.getZ()));
            boolean openUp = isOpen(level, open, up, q.set(p.getX(), p.getY() + 1, p.getZ()));
            long[] side = new long[4];
            float[] sideW = new float[4];
            float sideTotal = 0;
            for (int i = 0; i < 4; i++) {
                Direction d = SIDES[i];
                side[i] = BlockPos.asLong(p.getX() + d.getStepX(), p.getY(), p.getZ() + d.getStepZ());
                if (isOpen(level, open, side[i], q.set(p.getX() + d.getStepX(), p.getY(), p.getZ() + d.getStepZ()))) {
                    sideW[i] = (float) Math.max(0.1, 1 + WIND * (d.getStepX() * wx + d.getStepZ() * wz));
                    sideTotal += sideW[i];
                }
            }
            for (int a = 0; a < AGENTS.length; a++) {
                float amount = v[a];
                if (amount <= 0) {
                    continue;
                }
                GasAgent agent = AGENTS[a];
                float keep = 1 - agent.decay - overflow;
                float moving = amount * SPREAD;
                float stay = amount - moving;
                float toDown = openDown ? moving * agent.sink : 0;
                float toUp = openUp ? moving * RISE : 0;
                float sideShare = moving * (1 - agent.sink - RISE);
                if (sideTotal > 0) {
                    for (int i = 0; i < 4; i++) {
                        if (sideW[i] > 0) {
                            add(next, side[i], a, sideShare * sideW[i] / sideTotal * keep);
                        }
                    }
                } else {
                    stay += sideShare;                         // walled in on every side: it stays
                }
                if (openDown) {
                    add(next, down, a, toDown * keep);
                } else {
                    stay += moving * agent.sink;
                }
                if (openUp) {
                    add(next, up, a, toUp * keep);
                } else {
                    stay += moving * RISE;
                }
                add(next, key, a, stay * keep);
            }
        }
        // Drop what has thinned out to nothing, and note where the rest is.
        next.long2ObjectEntrySet().removeIf(e -> {
            float[] v = e.getValue();
            float total = 0;
            for (float f : v) {
                total += f;
            }
            return total < GONE;
        });
        for (long key : next.keySet()) {
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x + 1);
            maxY = Math.max(maxY, y + 1);
            maxZ = Math.max(maxZ, z + 1);
        }
        this.cells = next;
        this.bounds = next.isEmpty() ? new AABB(0, 0, 0, 0, 0, 0) : new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        if (++this.steps % SOUR_EVERY == 0) {
            sour(level);
        }
    }

    /**
     * Anthrax lying thick on soil sours it (slice 12e): now and then a block of gas at full strength or more, resting on
     * soil, turns that soil to {@link dev.groundburst.content.fallout.ToxicSoilBlock toxic soil} (and kills what grows
     * in it), so a cloud leaves patches of it as it settles.
     */
    private void sour(ServerLevel level) {
        int anthrax = GasAgent.ANTHRAX.ordinal(), scud = GasAgent.SCUD_TOXIN.ordinal();
        var random = level.getRandom();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (Long2ObjectMap.Entry<float[]> e : this.cells.long2ObjectEntrySet()) {
            float[] v = e.getValue();
            if (v[anthrax] + v[scud] < FULL || random.nextFloat() >= SOUR_CHANCE) {
                continue;
            }
            long key = e.getLongKey();
            below.set(BlockPos.getX(key), BlockPos.getY(key) - 1, BlockPos.getZ(key));
            if (level.isLoaded(below)) {
                dev.groundburst.content.fallout.ToxicSoilBlock.taint(level, below);
            }
        }
    }

    private static void add(Long2ObjectOpenHashMap<float[]> map, long key, int agent, float amount) {
        if (amount > 0) {
            map.computeIfAbsent(key, k -> new float[AGENTS.length])[agent] += amount;
        }
    }

    private static boolean isOpen(ServerLevel level, Long2ByteOpenHashMap cache, long key, BlockPos pos) {
        byte b = cache.getOrDefault(key, (byte) -1);
        if (b < 0) {
            b = (byte) (passable(level, pos) ? 1 : 0);
            cache.put(key, b);
        }
        return b == 1;
    }

    /** Everything breathing gas takes a dose: the stronger of what is at its feet and at its eyes, of each agent. */
    private void dose(ServerLevel level) {
        if (this.cells.isEmpty()) {
            return;
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.bounds.inflate(2))) {
            BlockPos feet = e.blockPosition(), eyes = BlockPos.containing(e.getEyePosition());
            for (GasAgent agent : AGENTS) {
                float c = Math.max(concentration(feet, agent), concentration(eyes, agent));
                if (c < VISIBLE) {
                    continue;
                }
                float strength = Math.min(1.5f, c / FULL);
                Toxins.expose(level, e, agent.damage * strength, Math.round(agent.poisonTicks * Math.min(1, strength)), agent.amplifier);
            }
        }
    }

    /** Tells each player the gas near them (to draw its haze). */
    private void sync(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            List<GasPayloads.Cell> near = new ArrayList<>();
            Vec3 at = player.position();
            if (!this.cells.isEmpty() && this.bounds.inflate(SEND_RANGE).contains(at)) {
                for (Long2ObjectMap.Entry<float[]> e : this.cells.long2ObjectEntrySet()) {
                    long key = e.getLongKey();
                    if (Math.abs(BlockPos.getX(key) - at.x) > SEND_RANGE || Math.abs(BlockPos.getZ(key) - at.z) > SEND_RANGE
                            || Math.abs(BlockPos.getY(key) - at.y) > SEND_RANGE) {
                        continue;
                    }
                    float[] v = e.getValue();
                    int best = 0;
                    for (int a = 1; a < v.length; a++) {
                        if (v[a] > v[best]) {
                            best = a;
                        }
                    }
                    if (v[best] >= VISIBLE) {
                        near.add(new GasPayloads.Cell(key, (byte) best, (byte) Math.min(255, Math.round(v[best] / FULL * 128))));
                        if (near.size() >= MAX_SENT) {
                            break;
                        }
                    }
                }
            }
            if (!near.isEmpty() || this.sentTo.remove(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new GasPayloads.Cells(near));
            }
            if (!near.isEmpty()) {
                this.sentTo.add(player.getUUID());
            }
        }
    }
}
