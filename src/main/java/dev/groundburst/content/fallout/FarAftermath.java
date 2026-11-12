package dev.groundburst.content.fallout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.groundburst.GroundBurst;
import dev.groundburst.blast.BlastProfile;
import dev.groundburst.blast.BlastSettings;
import dev.groundburst.blast.Blasts;
import dev.groundburst.blast.TerrainTable;
import dev.groundburst.config.GBServerConfig;
import dev.groundburst.heal.HealSettings;
import dev.groundburst.heal.Scars;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A nuclear blast's far aftermath (DESIGN.md §21.10): past its own reach D (its entity zone's outer radius), rings of
 * <b>charred</b> land (to 1.4 D: what the fireball's heat sees burns), <b>broken</b> land (to 3 D: the blast wave's broken
 * glass, doors blown open, leaves stripped) and <b>withered</b> land (to 4.3 D: leaves and grass dying in patches), each
 * fading out over its outer fifth, the edge moved in and out by value noise so no circle shows; a hillside turned away from
 * the fireball is spared the heat. What each block becomes is its terrain table's {@code charred}, {@code broken} and
 * {@code withered} rules (so the server's swap lines reach them); the charred land takes the Nuclear Wasteland's biome.
 * <p>
 * The <b>far wave</b> carries the shock on from D at the speed of sound to the withered ring's edge: a creature out in the
 * open (the line toward the blast clear for {@link #COVER} blocks) is thrown away from the blast and, out to 2 D, hurt;
 * the land changes as the wave passes, loaded chunks first, a few milliseconds a tick. Then the chunks that exist on disk
 * inside the rings are loaded a few at a time, changed and let go (never generated), and any chunk loaded later inside
 * them (one missed, or generated after) is changed as it loads. Each far aftermath is kept with the level (its centre,
 * rings, seed and table) and each chunk keeps the ones it has had ({@link #MARKS}), so nothing changes twice. All of it is
 * remembered to heal as the nuclear kind's (§21.9). Chunks changed are handed to Distant Horizons, if it is there.
 */
public final class FarAftermath {
    /** The far wave's speed: the speed of sound, 343 m/s (blocks a tick). */
    public static final float SPEED = 343f / 20;
    /** How far toward the blast a creature must have open air to be thrown (blocks). */
    static final int COVER = 24;
    /** The share of the profile's edge damage the far wave deals at the blast's reach (falling to nothing at twice it). */
    static final float HURT = 0.05f;
    /** How hard the far wave throws, at the blast's reach and at its own edge (blocks a tick). */
    static final float PUSH_NEAR = 0.9f, PUSH_FAR = 0.1f;
    /** How far a ring's edge is moved by the noise, as a share of its radius; the noise's grain (blocks). */
    static final float EDGE_NOISE = 0.1f, NOISE_GRAIN = 48;
    /** How deep down a column the far aftermath looks (blocks from its top). */
    static final int DEPTH = 28;
    /** Chunk reads and chunk loads a background pass keeps going at once (a tile's worth of loads). */
    static final int READS = 48, LOADS = 16;
    /** The background pass goes tile by tile (this many chunks a side), nearest tile first: chunks loaded side by side. */
    static final int TILE = 4;
    /** Ticks a background load may wait for its chunk. */
    static final int PATIENCE = 400;
    /** How many far aftermaths a level keeps (the oldest let go first). */
    static final int KEPT = 32;
    /** Block set flags: tell clients, no drops, no neighbour shape updates (as blasts set blocks). */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            GroundBurst.MODID);
    /** The far aftermaths a chunk has had (their ids), saved with it. */
    public static final Supplier<AttachmentType<List<Integer>>> MARKS = ATTACHMENTS.register("far_marks",
            () -> AttachmentType.<List<Integer>>builder(() -> new ArrayList<>())
                    .serialize(Codec.INT.listOf().<List<Integer>>xmap(ArrayList::new, List::copyOf).fieldOf("ids")).build());
    private FarAftermath() {}


    // --- one far aftermath ------------------------------------------------------------------------------------------------------
    /**
     * One blast's far aftermath, as kept with the level.
     *
     * @param id        its number in the level (the chunks' marks)
     * @param centre    the burst
     * @param inner     where the blast's own terrain work ends (the rings fade in from there)
     * @param reach     the blast's reach D, where the far wave starts
     * @param rings     the charred, broken and withered rings' outer radii
     * @param fireball  the fireball's height over the burst (which slopes face it)
     * @param seed      its edges' noise
     * @param table     its terrain table
     * @param weapon    its blast profile (healing goes by it)
     * @param born      game time it went off
     * @param waveStart game time the far wave sets out from D
     * @param hurt      the far wave's damage at D
     * @param land      whether it changes the land (the server lets blasts change terrain there)
     * @param next      how far the background pass has got through its chunks (-1: not begun; past the end: done)
     */
    public record Far(int id, Vec3 centre, float inner, float reach, List<Float> rings, float fireball, long seed, Identifier table,
            Identifier weapon, long born, long waveStart, float hurt, boolean land, int next) {
        static final Codec<Far> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Far::id),
                Vec3.CODEC.fieldOf("centre").forGetter(Far::centre),
                Codec.FLOAT.fieldOf("inner").forGetter(Far::inner),
                Codec.FLOAT.fieldOf("reach").forGetter(Far::reach),
                Codec.FLOAT.listOf().fieldOf("rings").forGetter(Far::rings),
                Codec.FLOAT.fieldOf("fireball").forGetter(Far::fireball),
                Codec.LONG.fieldOf("seed").forGetter(Far::seed),
                Identifier.CODEC.fieldOf("table").forGetter(Far::table),
                Identifier.CODEC.fieldOf("weapon").forGetter(Far::weapon),
                Codec.LONG.fieldOf("born").forGetter(Far::born),
                Codec.LONG.fieldOf("wave_start").forGetter(Far::waveStart),
                Codec.FLOAT.fieldOf("hurt").forGetter(Far::hurt),
                Codec.BOOL.fieldOf("land").forGetter(Far::land),
                Codec.INT.fieldOf("next").forGetter(Far::next)
        ).apply(i, Far::new));

        public float charred() {
            return this.rings.get(0);
        }

        public float broken() {
            return this.rings.get(1);
        }

        public float withered() {
            return this.rings.get(2);
        }

        /** The far wave's radius at game time {@code now} (the reach before it sets out). */
        public double wave(double now) {
            return now < this.waveStart ? this.reach : Math.min(withered(), this.reach + (now - this.waveStart) * SPEED);
        }

        Far withNext(int next) {
            return new Far(this.id, this.centre, this.inner, this.reach, this.rings, this.fireball, this.seed, this.table, this.weapon, this.born,
                    this.waveStart, this.hurt, this.land, next);
        }
    }

    /**
     * The rings' outer radii for a blast profile {@code id} as set off ({@code p}: its settings by weapon applied), in
     * blocks: charred, broken, withered; or null if it has none (not nuclear, the far aftermath off, or {@code far=0}). A
     * weapon's {@code far} share scales how far past its reach they go.
     */
    public static float @Nullable [] rings(Identifier id, BlastProfile p) {
        if (!TerrainTable.NUCLEAR.equals(p.terrain()) || !GBServerConfig.AFTERMATH_ENABLED.get()) {
            return null;
        }
        float k = BlastSettings.far(id);
        float d = Math.max(p.damage(), p.lethal());
        if (k <= 0 || d < 4) {
            return null;
        }
        float max = GBServerConfig.AFTERMATH_MAX_REACH.get();
        float charred = (float) (d + (GBServerConfig.AFTERMATH_CHARRED.get() - 1) * d * k);
        float broken = (float) (d + (GBServerConfig.AFTERMATH_BROKEN.get() - 1) * d * k);
        float withered = (float) (d + (GBServerConfig.AFTERMATH_WITHERED.get() - 1) * d * k);
        broken = Math.max(broken, charred);
        withered = Math.max(withered, broken);
        return new float[] {Math.min(max, charred), Math.min(max, broken), Math.min(max, withered)};
    }

    /**
     * A nuclear blast went off ({@code p} as set off): its far aftermath begins, its wave setting out once the blast's own
     * shock has reached its reach. {@code land}: whether it may change terrain. Not on the GameTest server (tests run side
     * by side; they make their own).
     */
    public static void start(ServerLevel level, Identifier weapon, Vec3 centre, BlastProfile p, long seed, boolean land) {
        if (level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;
        }
        float[] r = rings(weapon, p);
        if (r == null) {
            return;
        }
        float reach = Math.max(p.damage(), p.lethal());
        long now = level.getGameTime();
        long waveStart = now + (long) Math.ceil(reach / Math.max(0.1f, p.frontSpeed()));
        float fireball = Math.max(10, p.crater() * 0.6f);
        Far far = new Far(0, centre, Math.max(8, p.terrainRadius()), reach, List.of(r[0], r[1], r[2]), fireball, seed, p.terrain(), weapon, now,
                waveStart, HURT * p.edgeDamage(), land, -1);
        State.of(level).add(level, far);
    }

    // --- the level's far aftermaths ----------------------------------------------------------------------------------------------
    /** A level's far aftermaths (its saved data, {@code far_aftermaths.dat}) and the work going on in them. */
    public static final class State extends SavedData {
        record Saved(int nextId, List<Far> fars) {
            static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("next_id").forGetter(Saved::nextId),
                    Far.CODEC.listOf().fieldOf("fars").forGetter(Saved::fars)
            ).apply(i, Saved::new));
        }

        static final SavedDataType<State> TYPE = new SavedDataType<>(GroundBurst.id("far_aftermaths"), State::new,
                Saved.CODEC.xmap(State::new, s -> new Saved(s.nextId, List.copyOf(s.fars))));
        private static final Map<ServerLevel, State> BY_LEVEL = new WeakHashMap<>();

        private int nextId = 1;
        private final List<Far> fars = new ArrayList<>();
        /** Not saved: where each far wave was last tick, the chunks waiting to change, a background pass's state. */
        private final Map<Integer, Double> waveAt = new HashMap<>();
        private final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();
        private final Map<Long, Integer> queuedFor = new HashMap<>();
        private @Nullable Pass pass;

        private State() {}

        private State(Saved s) {
            this.nextId = s.nextId;
            this.fars.addAll(s.fars);
        }

        public static State of(ServerLevel level) {
            return BY_LEVEL.computeIfAbsent(level, l -> l.getDataStorage().computeIfAbsent(TYPE));
        }

        public List<Far> fars() {
            return List.copyOf(this.fars);
        }

        void add(ServerLevel level, Far far) {
            Far f = new Far(this.nextId++, far.centre(), far.inner(), far.reach(), far.rings(), far.fireball(), far.seed(), far.table(),
                    far.weapon(), far.born(), far.waveStart(), far.hurt(), far.land(), far.next());
            this.fars.add(f);
            while (this.fars.size() > KEPT) {
                this.fars.removeFirst();                   // the oldest let go
            }
            setDirty();
            PacketDistributor.sendToPlayersInDimension(level, FarWave.of(f));
        }

        @Nullable Far get(int id) {
            for (Far f : this.fars) {
                if (f.id() == id) {
                    return f;
                }
            }
            return null;
        }

        void replace(Far f) {
            for (int i = 0; i < this.fars.size(); i++) {
                if (this.fars.get(i).id() == f.id()) {
                    this.fars.set(i, f);
                    setDirty();
                    return;
                }
            }
        }

        void queue(long chunk, int far) {
            if (this.queue.add(chunk)) {
                this.queuedFor.put(chunk, far);
            }
        }
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        FarLoads.register(modBus);
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                State.BY_LEVEL.remove(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load e) -> {
            if (e.getLevel() instanceof ServerLevel level && e.getChunk() instanceof LevelChunk chunk) {
                loaded(level, chunk, e.isNewChunk());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> tell(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> tell(e.getEntity()));
    }

    /** A player joining a level hears of the far waves still running in it. */
    private static void tell(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer p && p.level() instanceof ServerLevel level) {
            long now = level.getGameTime();
            for (Far f : State.of(level).fars) {
                if (f.wave(now) < f.withered()) {
                    PacketDistributor.sendToPlayer(p, FarWave.of(f));
                }
            }
        }
    }

    // --- each tick ------------------------------------------------------------------------------------------------------------------
    private static void tick(ServerLevel level) {
        State s = State.of(level);
        if (s.fars.isEmpty() && s.queue.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Far f : List.copyOf(s.fars)) {
            double was = s.waveAt.getOrDefault(f.id(), f.reach() - 1.0);
            double at = f.wave(now);
            if (at > was && GBServerConfig.AFTERMATH_WAVE.get()) {
                hit(level, f, was, at, null, COVER);
            }
            if (at > was && f.land()) {
                queueLoaded(level, s, f, was, at);
            }
            s.waveAt.put(f.id(), at);
        }
        // One budget a tick for all of it: the chunks the wave has passed first, then the pass over the chunks on disk.
        long start = System.nanoTime();
        long until = start + GBServerConfig.AFTERMATH_MS.get() * 1_000_000L;
        convertQueued(level, s, until);
        background(level, s, now, until);
        Stats.tick(System.nanoTime() - start);
        // A far aftermath is let go once its pass is done and its land would have healed (healing on), or never.
        s.fars.removeIf(f -> f.next() > Integer.MAX_VALUE / 2 && healed(level, f, now));
    }

    /** Whether healing is on for its kind and the wait after it has run (no chunk loading later should change any more). */
    private static boolean healed(ServerLevel level, Far f, long now) {
        HealSettings h = HealSettings.current();
        return h.enabled() && h.heals(HealSettings.Kind.NUCLEAR, f.weapon(), level) && now - f.born() > h.waitTicks() + (long) h.randomTicks();
    }

    // --- the far wave ----------------------------------------------------------------------------------------------------------------
    /**
     * The far wave has come from {@code r0} to {@code r1}: the creatures it reached in the open (the line toward the blast
     * clear for {@code cover} blocks) thrown and, nearer, hurt; with {@code only}, those inside it alone (GameTests).
     */
    public static void hit(ServerLevel level, Far f, double r0, double r1, net.minecraft.world.phys.@Nullable AABB only, int cover) {
        Vec3 c = f.centre();
        float span = Math.max(1, f.withered() - f.reach());
        List<Entity> reached = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e == null || e.isSpectator() || !(e instanceof LivingEntity || e instanceof net.minecraft.world.entity.vehicle.VehicleEntity)) {
                continue;
            }
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r0 && d <= r1 && (only == null || only.contains(e.position()))) {
                reached.add(e);
            }
        }
        for (Entity e : reached) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
            if (!open(level, e, c, cover)) {
                continue;                                  // behind a wall or indoors: it passes over
            }
            float t = (float) Mth.clamp((d - f.reach()) / span, 0, 1);
            float push = Mth.lerp(t, PUSH_NEAR, PUSH_FAR);
            e.push(dx / d * push, 0.25 * push, dz / d * push);        // (marks it for its motion to be sent)
            if (d < 2 * f.reach() && f.hurt() > 0 && e instanceof LivingEntity) {
                float dmg = (float) (f.hurt() * (1 - (d - f.reach()) / f.reach()));
                if (dmg > 0.2f) {
                    e.hurtServer(level, Blasts.damageSource(level, null, c), dmg);
                }
            }
        }
    }

    /** Whether the line from {@code e}'s eyes toward the blast is clear for {@code cover} blocks (it stands in the open). */
    static boolean open(ServerLevel level, Entity e, Vec3 centre, int cover) {
        Vec3 eye = e.getEyePosition();
        Vec3 toward = new Vec3(centre.x - eye.x, 0, centre.z - eye.z);
        if (toward.lengthSqr() < 1e-6) {
            return true;
        }
        Vec3 end = eye.add(toward.normalize().scale(cover)).add(0, cover / 16.0, 0);
        return Boolean.TRUE.equals(net.minecraft.world.level.BlockGetter.traverseBlocks(eye, end, level, (lvl, at) -> {
            if (!lvl.isLoaded(at)) {
                return null;
            }
            BlockState state = lvl.getBlockState(at);
            if (state.isAir()) {
                return null;
            }
            var shape = state.getCollisionShape(lvl, at);
            return shape.isEmpty() || shape.clip(eye, end, at) == null ? null : Boolean.FALSE;
        }, lvl -> Boolean.TRUE));
    }

    // --- the land ------------------------------------------------------------------------------------------------------------------
    /** The loaded chunks the wave has just passed (centres between {@code r0} and {@code r1}) wait to change. */
    private static void queueLoaded(ServerLevel level, State s, Far f, double r0, double r1) {
        if (r1 < f.inner() - 16) {
            return;
        }
        Vec3 c = f.centre();
        int reach = Mth.ceil(r1) + 16;
        int cx0 = SectionPos.blockToSectionCoord(c.x - reach), cx1 = SectionPos.blockToSectionCoord(c.x + reach);
        int cz0 = SectionPos.blockToSectionCoord(c.z - reach), cz1 = SectionPos.blockToSectionCoord(c.z + reach);
        double lo = Math.max(f.inner() - 16, r0 - 12), hi = r1 + 12;
        for (int cx = cx0; cx <= cx1; cx++) {
            double ddx = cx * 16 + 8 - c.x;
            if (Math.abs(ddx) > hi) {
                continue;
            }
            for (int cz = cz0; cz <= cz1; cz++) {
                double ddz = cz * 16 + 8 - c.z;
                double d = Math.sqrt(ddx * ddx + ddz * ddz);
                if (d < lo || d > hi) {
                    continue;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null && !marked(chunk, f.id())) {
                    s.queue(ChunkPos.pack(cx, cz), f.id());
                }
            }
        }
    }

    /** Changes chunks from the queue for the tick's budget (each once loaded; one gone in the meantime is left for later). */
    private static void convertQueued(ServerLevel level, State s, long until) {
        if (s.queue.isEmpty()) {
            return;
        }
        while (!s.queue.isEmpty() && System.nanoTime() < until) {
            long key = s.queue.removeFirstLong();
            Integer id = s.queuedFor.remove(key);
            Far f = id == null ? null : s.get(id);
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (f != null && chunk != null) {
                convert(level, chunk, f);
            }
        }
    }

    /** A chunk has loaded: inside a far aftermath's rings and not changed by it, it waits to change. */
    private static void loaded(ServerLevel level, LevelChunk chunk, boolean fresh) {
        State s = State.of(level);
        if (s.fars.isEmpty()) {
            return;
        }
        double x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        long now = level.getGameTime();
        for (Far f : s.fars) {
            if (!f.land()) {
                continue;
            }
            double d = Math.hypot(x - f.centre().x, z - f.centre().z);
            if (d < f.inner() - 16 || d > f.withered() + 12 || d > f.wave(now) + 12 || marked(chunk, f.id())) {
                continue;
            }
            if (fresh && !GBServerConfig.AFTERMATH_NEW_CHUNKS.get()) {
                mark(chunk, f.id());                       // generated after: left as it is, and never looked at again
                continue;
            }
            s.queue(chunk.getPos().pack(), f.id());
        }
    }

    static boolean marked(LevelChunk chunk, int id) {
        List<Integer> marks = chunk.getExistingDataOrNull(MARKS.get());
        return marks != null && marks.contains(id);
    }

    static void mark(LevelChunk chunk, int id) {
        List<Integer> marks = chunk.getData(MARKS.get());
        if (!marks.contains(id)) {
            marks.add(id);
            chunk.markUnsaved();
        }
    }

    /**
     * One chunk's share of the far aftermath, column by column from the top down: what the heat sees (under the open sky, on
     * a slope facing the fireball) to the charred and withered rules, and what the blast wave reaches (in the open, or a
     * window, door or lamp with a face toward the blast) to the broken rules; the charred ring's cells to the wasteland's
     * biome. Marked as done, and handed to Distant Horizons. True if anything changed.
     */
    public static boolean convert(ServerLevel level, LevelChunk chunk, Far f) {
        return convert(level, chunk, f, null);
    }

    /** As {@link #convert(ServerLevel, LevelChunk, Far)}, only the columns inside {@code only} (GameTests: their own plot). */
    public static boolean convert(ServerLevel level, LevelChunk chunk, Far f, net.minecraft.world.level.levelgen.structure.@Nullable BoundingBox only) {
        if (marked(chunk, f.id())) {
            return false;
        }
        mark(chunk, f.id());
        long t0 = System.nanoTime();
        TerrainTable table = TerrainTable.byId(f.table());
        RandomSource random = RandomSource.create(f.seed() ^ chunk.getPos().pack() * 0x9E3779B97F4A7C15L);
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        // Remembered to heal by the settings where it is (a GameTest's plot: its own).
        double rx = only == null ? x0 + 8 : (Math.max(x0, only.minX()) + Math.min(x0 + 15, only.maxX())) / 2.0 + 0.5;
        double rz = only == null ? z0 + 8 : (Math.max(z0, only.minZ()) + Math.min(z0 + 15, only.maxZ())) / 2.0 + 0.5;
        double ry = only == null ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) : only.minY() + 1;
        Scars.Recorder scars = Scars.recorder(level, new Vec3(rx, ry, rz), f.weapon(), HealSettings.Kind.NUCLEAR);
        boolean changed = false;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        float[] charredCells = new float[16];
        // Each column's ground (under trees and roofs), found before anything changes: the slopes the heat is judged by.
        int[] grounds = grounds(level, chunk, x0, z0);
        // The world spawn, when the server protects it (as blasts spare it).
        BlockPos spawn = GBServerConfig.PROTECT_SPAWN.get() && level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                ? level.getRespawnData().pos() : null;
        int spawnRadius = level.getServer() instanceof net.minecraft.server.dedicated.DedicatedServer server ? server.spawnProtectionRadius() : 16;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                if (only != null && (x < only.minX() || x > only.maxX() || z < only.minZ() || z > only.maxZ())) {
                    continue;
                }
                double dx = x + 0.5 - f.centre().x, dz = z + 0.5 - f.centre().z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < f.inner() * 0.85 || d > f.withered()
                        || spawn != null && Math.abs(x - spawn.getX()) <= spawnRadius && Math.abs(z - spawn.getZ()) <= spawnRadius) {
                    continue;
                }
                // The ring edges moved in and out by noise: no circle shows.
                double nd = d * (1 + EDGE_NOISE * noise(f.seed(), x, z));
                float in = (float) Mth.clamp((nd - f.inner() * 0.85) / Math.max(8, f.inner() * 0.15), 0, 1);
                float wc = in * fade(nd, f.charred()), wb = in * fade(nd, f.broken()), ww = in * fade(nd, f.withered());
                if (wc <= 0 && wb <= 0 && ww <= 0) {
                    continue;
                }
                if (wc > 0.5f && only == null) {         // (a test's plot keeps its biome: the cells reach past it)
                    charredCells[(lx >> 2) * 4 + (lz >> 2)] = Math.max(charredCells[(lx >> 2) * 4 + (lz >> 2)], wc);
                }
                float heat = facing(f, grounds, lx, lz, x, z);
                changed |= column(level, chunk, table, scars, random, at, f, x, z, d, wc * heat, wb, ww * heat);
            }
        }
        if (paintBiome(level, chunk, f, charredCells, scars)) {
            changed = true;
        }
        long dh = 0;
        if (changed) {
            chunk.markUnsaved();
            long t1 = System.nanoTime();
            dev.groundburst.compat.DistantHorizons.chunkChanged(level, chunk);
            dh = System.nanoTime() - t1;
        }
        if (only == null) {
            Stats.chunk(changed, System.nanoTime() - t0, dh);
        }
        return changed;
    }

    /** What the far aftermath has done since the server started (the blast bench reports it). */
    public static final class Stats {
        public static long chunks, changed, convertNanos, dhNanos, ticks, tickNanos, maxTickNanos, passChunks;
        public static long passFrom = -1, passTo = -1;

        private Stats() {}

        static void chunk(boolean changed, long nanos, long dh) {
            Stats.chunks++;
            Stats.changed += changed ? 1 : 0;
            Stats.convertNanos += nanos;
            Stats.dhNanos += dh;
        }

        static void tick(long nanos) {
            Stats.ticks++;
            Stats.tickNanos += nanos;
            Stats.maxTickNanos = Math.max(Stats.maxTickNanos, nanos);
        }

        public static void reset() {
            chunks = changed = convertNanos = dhNanos = ticks = tickNanos = maxTickNanos = passChunks = 0;
            passFrom = passTo = -1;
        }
    }

    /** One column, from the top down (see {@link #convert}). */
    private static boolean column(ServerLevel level, LevelChunk chunk, TerrainTable table, Scars.Recorder scars, RandomSource random,
            BlockPos.MutableBlockPos at, Far f, int x, int z, double d, float wc, float wb, float ww) {
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        boolean changed = false, sheltered = false;
        float tc = (float) (d / f.charred()), tb = (float) (d / f.broken()), tw = (float) (d / f.withered());
        Direction toward = Direction.getApproximateNearest(f.centre().x - x - 0.5, 0, f.centre().z - z - 0.5);
        for (int y = top; y > top - DEPTH && y >= level.getMinY(); y--) {
            at.set(x, y, z);
            BlockState s = chunk.getBlockState(at);
            if (s.isAir()) {
                continue;
            }
            if (!s.getFluidState().isEmpty() && s.getFluidState().isSource()) {
                return changed;                            // a pond or the sea: its floor is spared
            }
            boolean ground = ground(s);
            BlockState out = null;
            if (!sheltered) {
                out = wc > 0 ? table.convert(TerrainTable.Band.CHARRED, s, random, tc, wc) : null;
                if (out == null && ww > 0) {
                    out = table.convert(TerrainTable.Band.WITHERED, s, random, tw, ww);
                }
            }
            if (out == null && wb > 0 && (!sheltered || window(s) && faces(level, at, toward))) {
                out = table.convert(TerrainTable.Band.BROKEN, s, random, tb, wb);
            }
            if (out != null) {
                set(level, scars, at.immutable(), s, out);
                changed = true;
            }
            if (ground) {
                return changed;                            // the ground: nothing under it is reached
            }
            // A roof or a wall shelters what is under it from the heat (leaves and trunks let it through).
            if (!sheltered && s.isSolidRender() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) {
                sheltered = true;
            }
        }
        return changed;
    }

    /** Sets a block the far aftermath changed (remembered to heal), a door's other half with it. */
    private static void set(ServerLevel level, Scars.Recorder scars, BlockPos pos, BlockState was, BlockState now) {
        scars.set(pos, now, FLAGS);
        if (was.getBlock() instanceof DoorBlock && now.getBlock() instanceof DoorBlock && now.hasProperty(BlockStateProperties.OPEN)) {
            BlockPos other = was.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState o = level.getBlockState(other);
            if (o.getBlock() == was.getBlock()) {
                scars.set(other, o.setValue(BlockStateProperties.OPEN, now.getValue(BlockStateProperties.OPEN)), FLAGS);
            }
        }
    }

    /** The natural ground a column ends at: earth, sand, stone, gravel, clay, terracotta, snow, ice, the aftermath's own. */
    static boolean ground(BlockState s) {
        return TerrainTable.earth(s) || TerrainTable.sand(s) || TerrainTable.rock(s) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.CLAY) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.MUD)
                || s.is(GBFallout.SCORCHED_EARTH.block().get()) || s.is(GBFallout.SCORCHED_STONE.block().get())
                || s.is(GBFallout.GLASSED_SAND.block().get());
    }

    /** What the blast wave breaks wherever it stands, if a face of it meets the wave: glass, panes, doors, trapdoors, lamps. */
    static boolean window(BlockState s) {
        return s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)
                || s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN);
    }

    /** Whether the block at {@code at} has open air on its side toward the blast (the wave strikes that face). */
    static boolean faces(ServerLevel level, BlockPos at, Direction toward) {
        BlockPos next = at.relative(toward);
        if (!level.isLoaded(next)) {
            return true;
        }
        BlockState n = level.getBlockState(next);
        return n.isAir() || !n.isSolidRender();
    }

    /** A ring's weight at (noisy) distance {@code d}: all of it inside, fading out over its outer fifth. */
    static float fade(double d, float radius) {
        return (float) Mth.clamp((radius - d) / (0.2 * radius), 0, 1);
    }

    /** Width of the ground grid round a chunk: the chunk and two columns either side (for the slopes at its edges). */
    static final int GRID = 20;

    /**
     * The ground's height (the top of its natural ground: under trees, plants, roofs and walls) for the chunk's columns and
     * two round it, a {@link #GRID}-square grid; columns outside it not loaded take the nearest inside's.
     */
    static int[] grounds(ServerLevel level, LevelChunk chunk, int x0, int z0) {
        int[] g = new int[GRID * GRID];
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                int x = x0 - 2 + i, z = z0 - 2 + j;
                boolean inside = i >= 2 && i < 18 && j >= 2 && j < 18;
                g[i * GRID + j] = inside || level.isLoaded(new BlockPos(x, level.getMinY(), z)) ? groundAt(level, x, z) : Integer.MIN_VALUE;
            }
        }
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                if (g[i * GRID + j] == Integer.MIN_VALUE) {
                    g[i * GRID + j] = g[Mth.clamp(i, 2, 17) * GRID + Mth.clamp(j, 2, 17)];
                }
            }
        }
        return g;
    }

    /** One column's ground height: down from its top, through leaves, logs, plants and anything built, to natural ground. */
    static int groundAt(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int y = top; y > top - DEPTH && y >= level.getMinY(); y--) {
            BlockState s = level.getBlockState(at.set(x, y, z));
            if (ground(s) || !s.getFluidState().isEmpty() && s.getFluidState().isSource()) {
                return y + 1;
            }
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /**
     * How much of the fireball's heat reaches a column (its local x and z, its world x and z): its ground's slope (from the
     * ground two columns round it) against the way to the fireball; a slope turned away from it is spared (0), level ground
     * and slopes facing it take it all (1).
     */
    static float facing(Far f, int[] g, int lx, int lz, int x, int z) {
        int i = lx + 2, j = lz + 2;
        int h = g[i * GRID + j];
        int e = g[(i + 2) * GRID + j], w = g[(i - 2) * GRID + j], s = g[i * GRID + j + 2], n = g[i * GRID + j - 2];
        Vec3 normal = new Vec3(-(e - w) / 4.0, 1, -(s - n) / 4.0).normalize();
        Vec3 to = new Vec3(f.centre().x - x - 0.5, f.centre().y + f.fireball() - h, f.centre().z - z - 0.5).normalize();
        double dot = normal.dot(to);
        return (float) Mth.clamp((dot + 0.02) / 0.14, 0, 1);
    }

    /** Smooth value noise in world space (-1 to 1, a grain of {@link #NOISE_GRAIN} blocks), seeded by the blast. */
    static double noise(long seed, int x, int z) {
        double fx = x / (double) NOISE_GRAIN, fz = z / (double) NOISE_GRAIN;
        int ix = Mth.floor(fx), iz = Mth.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = lattice(seed, ix, iz), b = lattice(seed, ix + 1, iz), c = lattice(seed, ix, iz + 1), d = lattice(seed, ix + 1, iz + 1);
        return Mth.lerp(tz, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d));
    }

    private static double lattice(long seed, int x, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) / (double) (1L << 53) * 2 - 1;
    }

    /** The charred ring's cells (4x4 columns each, where it holds) take the Nuclear Wasteland's biome, from under their ground up. */
    private static boolean paintBiome(ServerLevel level, LevelChunk chunk, Far f, float[] cells, Scars.Recorder scars) {
        if (!GBServerConfig.CONVERT_BIOMES.get()) {
            return false;
        }
        boolean any = false;
        for (float c : cells) {
            any |= c > 0.5f;
        }
        if (!any) {
            return false;
        }
        Holder<Biome> wasteland = level.registryAccess().lookupOrThrow(Registries.BIOME).get(Wasteland.BIOME).orElse(null);
        if (wasteland == null) {
            return false;
        }
        int floor = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) - Wasteland.DEPTH;
        boolean[] painted = {false};
        int qx0 = QuartPos.fromBlock(chunk.getPos().getMinBlockX()), qz0 = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
        chunk.fillBiomesFromNoise((qx, qy, qz) -> {
            Holder<Biome> now = chunk.getNoiseBiome(qx, qy, qz);
            int i = ((qx - qx0) & 3) * 4 + ((qz - qz0) & 3);
            if (cells[i] > 0.5f && QuartPos.toBlock(qy) + 3 >= floor && !now.is(Wasteland.BIOME)) {
                painted[0] = true;
                scars.biome(chunk, qx, qy, qz, now);
                return wasteland;
            }
            return now;
        });
        if (painted[0]) {
            level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
        }
        return painted[0];
    }

    // --- the chunks on disk ---------------------------------------------------------------------------------------------------------
    /**
     * Once a far aftermath's wave has gone its way, the chunks that exist on disk inside its rings (fully generated: never
     * generated anew) are loaded a few at a time, nearest first, changed and let go. One pass at a time in a level.
     */
    private static void background(ServerLevel level, State s, long now, long until) {
        Pass p = s.pass;
        if (p == null) {
            if (!GBServerConfig.AFTERMATH_LOAD.get()) {
                return;
            }
            for (Far f : s.fars) {
                if (f.land() && f.next() <= Integer.MAX_VALUE / 2 && f.wave(now) >= f.withered()) {
                    s.pass = p = new Pass(f, discFrom(f));
                    Stats.passFrom = now;
                    Stats.passChunks += p.keys.length;
                    break;
                }
            }
            if (p == null) {
                return;
            }
        }
        Far f = s.get(p.far);
        if (f == null) {
            p.release(level);
            s.pass = null;
            return;
        }
        if (p.step(level, s, f, until)) {
            p.release(level);
            s.replace(f.withNext(Integer.MAX_VALUE));      // done
            s.pass = null;
            Stats.passTo = now;
        } else if (p.next != f.next()) {
            s.replace(f.withNext(p.next));
        }
    }

    /**
     * A far aftermath's chunks inside its rings, tile by tile ({@link #TILE} chunks a side), nearest tile first: the chunks
     * loading at once lie side by side, sharing what is loaded round them, and the land changes outward from the blast.
     */
    private static long[] discFrom(Far f) {
        double cx = f.centre().x, cz = f.centre().z;
        float r = f.withered(), in = f.inner() * 0.85f - 16;
        int t0 = Math.floorDiv(SectionPos.blockToSectionCoord(cx - r), TILE), t1 = Math.floorDiv(SectionPos.blockToSectionCoord(cx + r), TILE);
        int u0 = Math.floorDiv(SectionPos.blockToSectionCoord(cz - r), TILE), u1 = Math.floorDiv(SectionPos.blockToSectionCoord(cz + r), TILE);
        // The tiles first (a sixteenth as many to sort), each by its middle's distance; then each tile's chunks in the ring.
        int across = u1 - u0 + 1, tiles = (t1 - t0 + 1) * across;
        double[] dist = new double[tiles];
        int[] order = new int[tiles];
        for (int i = 0; i < tiles; i++) {
            int tx = t0 + i / across, tz = u0 + i % across;
            dist[i] = Math.hypot((tx * TILE + TILE / 2.0) * 16 - cx, (tz * TILE + TILE / 2.0) * 16 - cz);
            order[i] = i;
        }
        it.unimi.dsi.fastutil.ints.IntArrays.quickSort(order, (a, b) -> Double.compare(dist[a], dist[b]));
        LongArrayList list = new LongArrayList();
        for (int i : order) {
            if (dist[i] > r + TILE * 16 || dist[i] < in - TILE * 16) {
                continue;                                  // the whole tile outside the rings
            }
            int tx = t0 + i / across, tz = u0 + i % across;
            for (int x = tx * TILE; x < tx * TILE + TILE; x++) {
                for (int z = tz * TILE; z < tz * TILE + TILE; z++) {
                    double d = Math.hypot(x * 16 + 8 - cx, z * 16 + 8 - cz);
                    if (d <= r + 12 && d >= in) {
                        list.add(ChunkPos.pack(x, z));
                    }
                }
            }
        }
        return list.toLongArray();
    }

    /** A background pass over one far aftermath's chunks: reads in flight, loads in flight, how far through. */
    private static final class Pass {
        final int far;
        final long[] keys;
        int next;
        /** What the disk said about each chunk asked (filled in from the IO thread): full, or not there. */
        final Map<Long, Boolean> known = new ConcurrentHashMap<>();
        final LongOpenHashSet asked = new LongOpenHashSet();
        final Map<Long, Integer> loading = new HashMap<>();

        Pass(Far f, long[] keys) {
            this.far = f.id();
            this.keys = keys;
            this.next = Math.max(0, f.next());
        }

        /** A tick's share, as far as the tick's budget ({@code until}) goes; true when every chunk has been seen to. */
        boolean step(ServerLevel level, State s, Far f, long until) {
            // Ask the disk ahead of the loads.
            for (int i = this.next; i < this.keys.length && this.asked.size() - this.known.size() < READS && i < this.next + 512; i++) {
                long k = this.keys[i];
                if (this.asked.add(k)) {
                    if (level.getChunkSource().getChunkNow(ChunkPos.getX(k), ChunkPos.getZ(k)) != null) {
                        this.known.put(k, Boolean.TRUE);
                        continue;
                    }
                    var status = new net.minecraft.nbt.visitors.CollectFields(
                            new net.minecraft.nbt.visitors.FieldSelector(net.minecraft.nbt.StringTag.TYPE, "Status"));
                    level.getChunkSource().chunkMap.chunkScanner().scanChunk(new ChunkPos(ChunkPos.getX(k), ChunkPos.getZ(k)), status)
                            .thenRun(() -> this.known.put(k, status.getResult() instanceof net.minecraft.nbt.CompoundTag tag
                                    && tag.getStringOr("Status", "").endsWith("full")))
                            .exceptionally(error -> {
                                this.known.put(k, Boolean.FALSE);
                                return null;
                            });
                }
            }
            // Loads in flight: changed once there (as the budget allows), let go after.
            for (var it = this.loading.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                long k = e.getKey();
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(k), ChunkPos.getZ(k));
                if (chunk != null && System.nanoTime() >= until) {
                    continue;                              // here, but the tick's time is spent: next tick
                }
                if (chunk != null || e.getValue() > PATIENCE) {
                    if (chunk != null) {
                        convert(level, chunk, f);
                    }
                    FarLoads.letGo(level, k);
                    it.remove();
                } else {
                    e.setValue(e.getValue() + 1);
                }
            }
            // On through the list in order, as the disk answers and the budget allows.
            while (this.next < this.keys.length && this.loading.size() < LOADS && System.nanoTime() < until) {
                long k = this.keys[this.next];
                Boolean full = this.known.get(k);
                if (full == null) {
                    break;                                 // still asking: wait for it
                }
                this.next++;
                this.known.remove(k);
                this.asked.remove(k);
                if (!full) {
                    continue;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(k), ChunkPos.getZ(k));
                if (chunk != null) {
                    convert(level, chunk, f);               // loaded already (marked if it has been changed)
                } else {
                    FarLoads.hold(level, k);
                    this.loading.put(k, 0);
                }
            }
            return this.next >= this.keys.length && this.loading.isEmpty();
        }

        void release(ServerLevel level) {
            for (long k : this.loading.keySet()) {
                FarLoads.letGo(level, k);
            }
            this.loading.clear();
        }
    }

    // --- GameTests ---------------------------------------------------------------------------------------------------------------------
    /** GameTests: a far aftermath of their own (never kept with the level). */
    public static Far forTests(Vec3 centre, float inner, float reach, float charred, float broken, float withered, long seed) {
        return new Far(1_000_000 + (int) (seed & 0xFFFF), centre, inner, reach, List.of(charred, broken, withered), 20, seed, TerrainTable.NUCLEAR,
                GroundBurst.id("test/far"), 0, 0, 8, true, -1);
    }
}
