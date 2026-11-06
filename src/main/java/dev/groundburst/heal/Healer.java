package dev.groundburst.heal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.groundburst.config.GBServerConfig.HealOrder;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Puts remembered damage back (DESIGN.md §21.9), in each level, in the chunks that are loaded. Every tick it looks at
 * the damaged chunks loaded: one that is due (its wait is over, or the morning has come, or an operator said so) heals
 * at its speed, from the bottom up or at random as the server says, sharing a budget of blocks and milliseconds a tick
 * round all of them. When a chunk begins, its biome turns back and the radiation centred in it goes; when its last
 * block is back, its paintings and item frames are hung again and its record is dropped. A chunk not loaded waits
 * (its record is saved with it) and carries on when it is.
 *
 * <p>A block comes back exactly as it was, with its block entity's data, set as blasts set them: nothing round it told,
 * nothing dropped, nothing placed off it. Whatever stands in its way (the blast's leftovers, water or sand that has come
 * in, a fire) gives way; a block a player put there since is kept, dropped or replaced as the server says. A creature
 * a block comes back round is lifted out on top.
 *
 * <p>What keeps track of more than one block wakes up again. The game's own blocks need nothing: a blast set its blocks
 * as quietly, so what was round them never heard they went. This mod's cables and pipes are told they are back
 * ({@link Healable}: their networks are worked out again and a pull side pulls again). Another mod's block is placed as a
 * structure places its blocks, its onPlace running before its data is loaded. And a block with a block entity or from
 * another mod tells the other mods' blocks beside it, as a block set down beside them would, since their pipes and
 * machines may have noticed it go (a capability gone, a network rebuilt).
 */
public final class Healer {
    /** Ticks in a day. */
    static final long DAY = 24_000L;
    /** Block set flags for a healed block. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;
    /** ...and for another mod's block: placed as a structure places its blocks (its onPlace runs, before its data is back). */
    static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    private static final net.minecraft.core.Direction[] DIRECTIONS = net.minecraft.core.Direction.values();
    /** Sparkles a chunk shows a tick at most; ticks between its sounds. */
    private static final int SPARKLES = 3, SOUND_EVERY = 5;
    /**
     * Sparkles and sounds a level shows a tick at most, all its healing chunks together: a nuclear crater heals hundreds
     * of chunks at once, and each of them its own sparkles and sound filled the clients' particles and sound channels.
     */
    private static final int SPARKLES_A_TICK = 24, SOUNDS_A_TICK = 3;
    /** Ticks between looks for a fire by a chunk that waits for one to go out. */
    private static final int FIRE_CHECK = 100;

    private static final Map<ServerLevel, Healer> BY_LEVEL = new WeakHashMap<>();

    private final ServerLevel level;
    private final Long2ObjectOpenHashMap<LevelChunk> tracked = new Long2ObjectOpenHashMap<>();
    private int rotation;
    /** Where decorations came down this tick (their drops are held back), and the tick. */
    private final List<Vec3> hushed = new ArrayList<>();
    private final it.unimi.dsi.fastutil.ints.IntOpenHashSet taken = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
    private long hushedAt = Long.MIN_VALUE;
    private @Nullable List<Vec3> hushing;
    /** This tick's sparkles and sounds so far (SPARKLES_A_TICK, SOUNDS_A_TICK). */
    private int sparkled, sounded;

    private Healer(ServerLevel level) {
        this.level = level;
    }

    /** The last level asked for and its healer (asked for many times a tick, nearly always for the same level). */
    private static @Nullable Healer last;

    public static Healer of(ServerLevel level) {
        Healer h = last;
        if (h != null && h.level == level) {
            return h;
        }
        h = BY_LEVEL.computeIfAbsent(level, Healer::new);
        last = h;
        return h;
    }

    static void forget(ServerLevel level) {
        BY_LEVEL.remove(level);
        if (last != null && last.level == level) {
            last = null;
        }
    }

    /** True while healing sets a block back ({@link #restoring}). */
    private static boolean restoring;

    /**
     * Whether healing is setting a block back this moment. One of this mod's blocks whose onPlace works something out
     * afresh (a cable's or pipe's sides, a structure's forming, a tank's joining) leaves it be then: the block is back
     * exactly as it was, and healing tells it so ({@link Healable}). (Healing skips this mod's blocks' onPlace anyway on
     * game versions that let it; on 1.21.1 a block's onPlace always runs.)
     */
    public static boolean restoring() {
        return restoring;
    }

    /** Another mod's block: not the game's own, and not this mod's. */
    static boolean modded(Block block) {
        String namespace = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        return !"minecraft".equals(namespace) && !dev.groundburst.GroundBurst.MODID.equals(namespace);
    }

    /** Whether any damaged chunk is loaded in this level. */
    boolean tracksAny() {
        return !this.tracked.isEmpty();
    }

    /** Whether the chunk at {@code (cx, cz)} is loaded and damaged. */
    boolean tracks(int cx, int cz) {
        return this.tracked.containsKey(ChunkPos.pack(cx, cz));
    }

    void track(LevelChunk chunk) {
        ChunkPos at = chunk.getPos();
        this.tracked.put(ChunkPos.pack(at.x(), at.z()), chunk);
    }

    void untrack(LevelChunk chunk) {
        ChunkPos at = chunk.getPos();
        this.tracked.remove(ChunkPos.pack(at.x(), at.z()));
    }

    /** A decoration came down at {@code at} this tick: what it drops now is held back. */
    void hush(Vec3 at) {
        refresh();
        this.hushed.add(at);
    }

    /** A weapon took {@code e} down this tick, to put it back later (it is not to be kept twice). */
    void taken(Entity e) {
        refresh();
        this.taken.add(e.getId());
    }

    boolean wasTaken(Entity e) {
        return this.level.getGameTime() == this.hushedAt && this.taken.contains(e.getId());
    }

    private void refresh() {
        long now = this.level.getGameTime();
        if (now != this.hushedAt) {
            this.hushed.clear();
            this.taken.clear();
            this.hushedAt = now;
        }
    }

    /**
     * While a weapon sets a block through a recorder, round {@code around} (its neighbours' middles): a neighbour that
     * comes off with it (a torch that loses its wall) will heal, so what it drops is held back. Null when it is done.
     */
    void hushWhile(@Nullable List<Vec3> around) {
        this.hushing = around;
    }

    /** Whether an item appearing at {@code at} is a held-back drop. */
    boolean hushed(Vec3 at) {
        if (this.hushing != null) {
            for (Vec3 h : this.hushing) {
                if (h.distanceToSqr(at) < 1.0) {
                    return true;
                }
            }
        }
        if (this.level.getGameTime() != this.hushedAt) {
            return false;
        }
        for (Vec3 h : this.hushed) {
            if (h.distanceToSqr(at) < 2.25) {
                return true;
            }
        }
        return false;
    }

    // --- the healing as time goes by ----------------------------------------------------------------------------------------------
    void tick() {
        Scars.resetCascade();
        this.sparkled = 0;
        this.sounded = 0;
        if (this.tracked.isEmpty()) {
            return;
        }
        HealSettings s = HealSettings.current();
        long now = this.level.getGameTime();
        long dayTime = Snapshots.dayTime(this.level);
        long today = dayTime < 0 ? -1 : Math.floorDiv(dayTime, DAY);
        int budget = s.maxBlocksPerTick();
        long deadline = System.nanoTime() + s.msPerTick() * 1_000_000L;
        LevelChunk[] chunks = this.tracked.values().toArray(new LevelChunk[0]);
        int n = chunks.length, start = Math.floorMod(this.rotation, n);
        var blasts = dev.groundburst.blast.BlastManager.of(this.level);
        for (int k = 0; k < n; k++) {
            LevelChunk chunk = chunks[(start + k) % n];
            ChunkScars scars = chunk.getExistingDataOrNull(GBHeal.SCARS.get());
            if (scars == null) {
                untrack(chunk);
                continue;
            }
            double cx = chunk.getPos().getMinBlockX() + 8, cz = chunk.getPos().getMinBlockZ() + 8;
            if (scars.isEmpty() && !scars.rushed && scars.started < 0 && now - scars.changed > 20) {
                drop(chunk);                               // all of it forgotten (players built over it)
                continue;
            }
            if (!due(scars, s, now, today, chunk) || HealSettings.testing() && HealSettings.inTestArea(this.level, new Vec3(cx, chunk.getMinY(), cz))
                    || blasts.forming(cx, cz)) {
                continue;                                  // (a blast still digging there: wait for it)
            }
            if (budget <= 0 || System.nanoTime() > deadline) {
                this.rotation = start + k;                 // the next tick begins where this one ran out
                return;
            }
            if (scars.started < 0 && !scars.rushed) {
                if (now < scars.fireCheck) {
                    continue;                              // a fire burnt by it a moment ago: it looks again in a while
                }
                if (fireNear(chunk, false)) {
                    scars.heldOff(now, today);             // a fire burns by it still: it waits again (what heals would burn)
                    scars.fireCheck = now + FIRE_CHECK;
                    chunk.markUnsaved();
                    continue;
                }
            }
            budget -= step(chunk, scars, s, now, budget, deadline);
        }
    }

    /** Whether a chunk's damage is due to heal now. */
    static boolean due(ChunkScars c, HealSettings s, long now, long today, @Nullable LevelChunk chunk) {
        if (c.rushed) {
            return true;
        }
        if (s.schedule() == dev.groundburst.config.GBServerConfig.HealSchedule.MORNING && today >= 0 && c.waitedDay < 0
                && s.enabled() && waited(s, c.changed, c.jitter, now)) {
            c.waitedDay = today;                           // the wait ran out today: the next sunrise
            if (chunk != null) {
                chunk.markUnsaved();
            }
        }
        return dueAt(s, c.changed, c.jitter, now, c.waitedDay, today);
    }

    private static boolean waited(HealSettings s, long changed, float jitter, long now) {
        return now - changed >= s.waitTicks() + (long) (jitter * s.randomTicks());
    }

    /**
     * Whether damage last changed at {@code changed} (with its chunk's share {@code jitter} of the random wait) is due
     * at {@code now}: after the wait; in the morning after it ({@code waitedDay}: the day its wait ran out, -1 not yet;
     * {@code today} -1 where there is no day and night); never by itself with COMMAND_ONLY.
     */
    public static boolean dueAt(HealSettings s, long changed, float jitter, long now, long waitedDay, long today) {
        if (!s.enabled()) {
            return false;
        }
        boolean waited = waited(s, changed, jitter, now);
        return switch (s.schedule()) {
            case AFTER_WAIT -> waited;
            case COMMAND_ONLY -> false;
            case MORNING -> waited && (today < 0 || waitedDay >= 0 && today > waitedDay);
        };
    }

    /** One tick's healing in one due chunk; the blocks it took. */
    private int step(LevelChunk chunk, ChunkScars scars, HealSettings s, long now, int budget, long deadline) {
        if (scars.started < 0) {
            begin(chunk, scars, s, now);
        }
        int healed = 0;
        if (scars.count > 0) {
            int allowed;
            if (s.order() == HealOrder.ALL_AT_ONCE || scars.rushed) {
                allowed = budget;
            } else {
                float perSecond = Math.max(s.blocksPerSecond(), s.longestTicks() > 0 ? scars.total * 20f / s.longestTicks() : 0);
                float perTick = perSecond / 20f;
                scars.credit = Math.min(scars.credit + perTick, Math.max(1, perTick) * 2);
                allowed = Math.min(budget, (int) scars.credit);
            }
            if (allowed > 0) {
                healed = heal(chunk, scars, s, allowed, deadline, null);
                scars.credit = Math.max(0, scars.credit - healed);
            }
        }
        if (scars.count == 0) {
            finish(chunk, scars, s);
        } else {
            chunk.markUnsaved();
        }
        return healed;
    }

    /** A chunk begins to heal: its speed is set from what it has to put back, its biome turns back, its radiation goes. */
    private void begin(LevelChunk chunk, ChunkScars scars, HealSettings s, long now) {
        if (scars.rushed) {
            fireNear(chunk, true);                         // told to heal now: the fires by it go out first
        }
        scars.started = now;
        scars.total = scars.count;
        scars.credit = 0;
        if (!scars.biomes.isEmpty()) {
            if (s.biomes()) {
                restoreBiomes(chunk, scars);
            }
            scars.biomes.clear();
        }
        if (s.clearRadiation()) {
            ChunkPos at = chunk.getPos();
            dev.groundburst.content.radiation.RadiationSources.of(this.level).clearCentredIn(at.x(), at.z());
        }
        chunk.markUnsaved();
    }

    /**
     * Whether a fire burns in the chunk or one beside it, near the chunk's damage (in a section with damage, or the one
     * above or below), that will go out by itself (not one on netherrack or the like, which burns for good, nor a soul
     * fire, which never spreads): what heals there now could burn again. With {@code putOut}, each such fire goes out.
     * A fire that cannot burn out where it is (nobody near enough for fire to burn, or its chunk not ticking:
     * {@link Snapshots#burnsOut}) would stand there for good and hold the healing off for ever: it goes out now, and is
     * no reason to wait (this is asked only of a chunk that is due).
     */
    public boolean fireNear(LevelChunk chunk, boolean putOut) {
        ChunkScars scars = chunk.getExistingDataOrNull(GBHeal.SCARS.get());
        if (scars == null || scars.sections.isEmpty()) {
            return false;
        }
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int sy : scars.sections.keySet().toIntArray()) {
            lo = Math.min(lo, sy);
            hi = Math.max(hi, sy);
        }
        boolean found = false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        ChunkPos at = chunk.getPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk c = this.level.getChunkSource().getChunkNow(at.x() + dx, at.z() + dz);
                if (c == null) {
                    continue;
                }
                int x0 = c.getPos().getMinBlockX(), z0 = c.getPos().getMinBlockZ();
                for (int sy = lo - 1; sy <= hi + 1; sy++) {
                    int index = c.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= c.getSectionsCount()) {
                        continue;
                    }
                    var section = c.getSection(index);
                    // The palette first (a section that never held fire is passed over at once), then block by block.
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(st -> st.is(net.minecraft.world.level.block.Blocks.FIRE))) {
                        continue;
                    }
                    for (int i = 0; i < 4096; i++) {
                        if (!section.getBlockState(i & 15, i >> 8, (i >> 4) & 15).is(net.minecraft.world.level.block.Blocks.FIRE)) {
                            continue;
                        }
                        p.set(x0 + (i & 15), (sy << 4) + (i >> 8), z0 + ((i >> 4) & 15));
                        BlockPos below = p.below();
                        if (this.level.getBlockState(below).isFireSource(this.level, below, net.minecraft.core.Direction.UP)) {
                            continue;                      // it burns for good there: a hearth, the Nether's netherrack
                        }
                        if (!putOut && Snapshots.burnsOut(this.level, p)) {
                            return true;
                        }
                        found = true;
                        this.level.removeBlock(p, false);
                    }
                }
            }
        }
        return putOut && found;
    }

    /** A chunk's last block is back: its decorations hang again and its record goes. */
    private void finish(LevelChunk chunk, ChunkScars scars, HealSettings s) {
        if (!scars.biomes.isEmpty() && s.biomes()) {
            restoreBiomes(chunk, scars);
        }
        for (CompoundTag tag : scars.entities) {
            Entity e = Snapshots.load(tag, this.level);
            if (e != null && this.level.getEntity(e.getUUID()) == null) {
                this.level.addFreshEntity(e);
            }
        }
        scars.entities.clear();
        drop(chunk);
    }

    /** The decorations remembered inside {@code box} hang again (the rest wait for their chunk). */
    private void hangInside(ChunkScars scars, BoundingBox box) {
        scars.entities.removeIf(tag -> {
            Entity e = Snapshots.load(tag, this.level);
            if (e == null || !box.isInside(e.blockPosition())) {
                return false;
            }
            if (this.level.getEntity(e.getUUID()) == null) {
                this.level.addFreshEntity(e);
            }
            return true;
        });
    }

    private void drop(LevelChunk chunk) {
        chunk.removeData(GBHeal.SCARS.get());
        untrack(chunk);
    }

    private void restoreBiomes(LevelChunk chunk, ChunkScars scars) {
        var biomes = this.level.registryAccess().lookupOrThrow(Registries.BIOME);
        chunk.fillBiomesFromNoise((qx, qy, qz) -> {
            Holder<Biome> now = chunk.getNoiseBiome(qx, qy, qz);
            String before = scars.biomeBefore(qx, qy, qz);
            Identifier id = before == null ? null : Identifier.tryParse(before);
            if (id == null) {
                return now;
            }
            return biomes.get(ResourceKey.create(Registries.BIOME, id)).<Holder<Biome>>map(h -> h).orElse(now);
        });
        scars.biomes.clear();
        chunk.markUnsaved();
        this.level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
    }

    /**
     * Heals up to {@code max} remembered blocks of a chunk, in the server's order (only those inside {@code within}, if
     * given); how many it went through.
     */
    int heal(LevelChunk chunk, ChunkScars scars, HealSettings s, int max, long deadline, @Nullable BoundingBox within) {
        Cell cell = new Cell(chunk, scars, s, within);
        int[] ys = scars.sections.keySet().toIntArray();
        if (ys.length == 0) {
            return 0;
        }
        if (s.order() == HealOrder.RANDOM && !scars.rushed && within == null) {
            RandomSource r = this.level.getRandom();
            for (int tries = 0; cell.done < max && scars.count > 0 && tries < max * 4; tries++) {
                if ((tries & 63) == 63 && System.nanoTime() > deadline) {
                    break;
                }
                int sy = ys[r.nextInt(ys.length)];
                ChunkScars.Section section = scars.section(sy);
                if (section == null) {
                    ys = scars.sections.keySet().toIntArray();
                    if (ys.length == 0) {
                        break;
                    }
                    continue;
                }
                int i = next(section, r.nextInt(ChunkScars.CELLS));
                if (i >= 0) {
                    cell.heal(section, sy, i);
                }
            }
        } else {
            IntArrays.quickSort(ys);
            outer:
            for (int sy : ys) {
                ChunkScars.Section section = scars.section(sy);
                if (section == null) {
                    continue;
                }
                for (int w = 0; w < 64; w++) {
                    long bits = section.touched[w];
                    while (bits != 0) {
                        if (cell.done >= max || (cell.done & 63) == 63 && System.nanoTime() > deadline) {
                            break outer;
                        }
                        int i = w << 6 | Long.numberOfTrailingZeros(bits);
                        bits &= bits - 1;
                        cell.heal(section, sy, i);
                    }
                }
            }
        }
        cell.show();
        return cell.done;
    }

    /** The first remembered cell at or after {@code from} (round to the start), or -1. */
    private static int next(ChunkScars.Section section, int from) {
        for (int k = 0; k <= 64; k++) {
            int w = ((from >> 6) + k) & 63;
            long bits = section.touched[w];
            if (k == 0) {
                bits &= -1L << (from & 63);
            }
            if (bits != 0) {
                return w << 6 | Long.numberOfTrailingZeros(bits);
            }
        }
        return -1;
    }

    /** Healing cells of one chunk in one go. */
    private final class Cell {
        final LevelChunk chunk;
        final ChunkScars scars;
        final HealSettings s;
        final @Nullable BoundingBox within;
        final int x0, z0;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        @Nullable List<LivingEntity> standing;
        int done, shown;
        @Nullable BlockPos heard;
        @Nullable BlockState heardState;

        Cell(LevelChunk chunk, ChunkScars scars, HealSettings s, @Nullable BoundingBox within) {
            this.chunk = chunk;
            this.scars = scars;
            this.s = s;
            this.within = within;
            this.x0 = chunk.getPos().getMinBlockX();
            this.z0 = chunk.getPos().getMinBlockZ();
        }

        void heal(ChunkScars.Section section, int sy, int i) {
            int x = this.x0 + (i & 15), y = (sy << 4) + (i >> 8), z = this.z0 + ((i >> 4) & 15);
            if (this.within != null && !this.within.isInside(x, y, z)) {
                return;
            }
            this.done++;
            BlockState original = Scars.unburnt(section.original(i));   // (a fire remembered before fires were not)
            CompoundTag be = section.blockEntity(i);
            boolean claimed = section.claimed(i);
            this.scars.clear(x, y, z);                     // forgotten whatever happens next: it is dealt with
            if (!this.s.keeps(original)) {
                return;
            }
            BlockPos.MutableBlockPos p = this.pos.set(x, y, z);
            BlockState current = this.chunk.getBlockState(p);
            if (claimed) {
                switch (this.s.playerBlocks()) {
                    case KEEP -> {
                        return;                            // (KEEP forgets the ground round a player's block when it is placed)
                    }
                    case DROP -> {
                        if (!current.isAir()) {
                            Healer.this.level.destroyBlock(p, true);
                            current = this.chunk.getBlockState(p);
                        }
                    }
                    case REPLACE -> { }
                }
            }
            if (current == original) {
                return;                                    // as it was already (the damage did not change it after all)
            }
            ServerLevel level = Healer.this.level;
            if (current.hasBlockEntity()) {
                level.removeBlockEntity(p);                // what stands there gives way quietly
            }
            Block block = original.getBlock();
            boolean modded = modded(block);
            boolean set;
            restoring = true;
            try {
                set = level.setBlock(p, original, modded ? PLACE_FLAGS : FLAGS);
            } finally {
                restoring = false;
            }
            if (!set) {
                return;
            }
            if (be != null) {
                BlockEntity now = level.getBlockEntity(p);
                if (now != null) {
                    Snapshots.load(now, be, level);
                    now.setChanged();
                }
            }
            if (block instanceof Healable healable) {
                healable.healed(level, p.immutable(), original);
            }
            if (modded || original.hasBlockEntity()) {
                wake(level, p.immutable(), block);
            }
            if (original.is(net.minecraft.tags.BlockTags.LOGS)) {
                // A trunk back: its leaves hear of it (they had begun to count themselves cut off, and would decay).
                original.updateNeighbourShapes(level, p, Block.UPDATE_CLIENTS);
            }
            VoxelShape shape = original.getCollisionShape(level, p);
            if (!shape.isEmpty()) {
                lift(p.immutable(), shape);
            }
            if (this.s.effects()) {
                if (this.shown < SPARKLES && Healer.this.sparkled < SPARKLES_A_TICK && level.getRandom().nextInt(4) == 0) {
                    this.shown++;
                    Healer.this.sparkled++;
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + 0.5, y + 0.8, z + 0.5, 2, 0.35, 0.25, 0.35, 0);
                }
                if (!original.isAir()) {
                    this.heard = p.immutable();
                    this.heardState = original;
                }
            }
        }

        /**
         * A block with a block entity, or another mod's, is back: the blocks beside it that keep track of their
         * neighbours hear of it, this mod's cables and pipes through {@link Healable} and another mod's blocks as a block
         * set down beside them would tell them. The game's own blocks are not told (they never heard it go), nor is a
         * neighbour whose chunk is not loaded.
         */
        private void wake(ServerLevel level, BlockPos at, Block block) {
            int cx = this.x0 >> 4, cz = this.z0 >> 4;
            for (net.minecraft.core.Direction d : DIRECTIONS) {
                BlockPos n = at.relative(d);
                BlockState there;
                if (n.getX() >> 4 == cx && n.getZ() >> 4 == cz) {
                    there = this.chunk.getBlockState(n);
                } else if (level.hasChunkAt(n)) {
                    there = level.getBlockState(n);
                } else {
                    continue;
                }
                if (there.getBlock() instanceof Healable healable) {
                    healable.besideHealed(level, n, there);
                } else if (modded(there.getBlock())) {
                    Snapshots.tellNeighbour(level, there, n, block, at);
                }
            }
        }

        /** Creatures standing where a block came back are lifted out on top of it. */
        private void lift(BlockPos at, VoxelShape shape) {
            if (this.standing == null) {
                AABB box = new AABB(this.x0 - 1, Healer.this.level.getMinY(), this.z0 - 1, this.x0 + 17, Healer.this.level.getMaxY() + 1, this.z0 + 17);
                this.standing = Healer.this.level.getEntitiesOfClass(LivingEntity.class, box, e -> !e.isSpectator());
            }
            if (this.standing.isEmpty()) {
                return;
            }
            AABB block = shape.bounds().move(at);
            for (LivingEntity e : this.standing) {
                if (!e.getBoundingBox().intersects(block)) {
                    continue;
                }
                double y = block.maxY;
                for (int up = 0; up < 48; up++, y += 1) {
                    AABB there = e.getBoundingBox().move(0, y - e.getY(), 0);
                    if (Healer.this.level.noCollision(e, there)) {
                        e.teleportTo(e.getX(), y, e.getZ());
                        break;
                    }
                }
            }
        }

        /** One sound for the chunk now and then. */
        void show() {
            if (this.heard == null) {
                return;
            }
            if (--this.scars.quiet > 0 || Healer.this.sounded >= SOUNDS_A_TICK) {
                return;
            }
            this.scars.quiet = SOUND_EVERY;
            Healer.this.sounded++;
            var sound = this.heardState.getSoundType().getPlaceSound();
            Healer.this.level.playSound(null, this.heard, sound, SoundSource.BLOCKS, 0.45f, 0.75f + Healer.this.level.getRandom().nextFloat() * 0.25f);
        }
    }

    // --- the heal command ------------------------------------------------------------------------------------------------------
    /** What healing there is to do round a place: blocks and chunks waiting, and the soonest a chunk begins (ticks; 0 now). */
    public record Status(int blocks, int chunks, int healing, long soonest, @Nullable BlockPos nearest) {}

    /** The damaged chunks loaded within {@code radius} of {@code centre} (every one if {@code centre} is null). */
    private List<LevelChunk> within(@Nullable Vec3 centre, double radius) {
        List<LevelChunk> out = new ArrayList<>();
        for (LevelChunk chunk : this.tracked.values()) {
            if (chunk.getExistingDataOrNull(GBHeal.SCARS.get()) == null) {
                continue;
            }
            if (centre != null) {
                double dx = chunk.getPos().getMinBlockX() + 8 - centre.x, dz = chunk.getPos().getMinBlockZ() + 8 - centre.z;
                if (dx * dx + dz * dz > (radius + 12) * (radius + 12)) {
                    continue;
                }
            }
            out.add(chunk);
        }
        return out;
    }

    /** Every damaged chunk round {@code centre} heals now, as fast as the server allows; {chunks, blocks}. */
    public int[] rush(@Nullable Vec3 centre, double radius) {
        int chunks = 0, blocks = 0;
        for (LevelChunk chunk : within(centre, radius)) {
            ChunkScars scars = chunk.getExistingDataOrNull(GBHeal.SCARS.get());
            scars.rushed = true;
            chunk.markUnsaved();
            chunks++;
            blocks += scars.count;
        }
        return new int[] {chunks, blocks};
    }

    /** The damage remembered round {@code centre} is forgotten: it stays as it is; {chunks, blocks}. */
    public int[] forgetDamage(@Nullable Vec3 centre, double radius) {
        int chunks = 0, blocks = 0;
        for (LevelChunk chunk : within(centre, radius)) {
            ChunkScars scars = chunk.getExistingDataOrNull(GBHeal.SCARS.get());
            chunks++;
            blocks += scars.count;
            drop(chunk);
        }
        return new int[] {chunks, blocks};
    }

    /** What is waiting to heal in the loaded chunks, and how soon the nearest to {@code at} begins. */
    public Status status(Vec3 at) {
        HealSettings s = HealSettings.current();
        long now = this.level.getGameTime();
        long dayTime = Snapshots.dayTime(this.level);
        long today = dayTime < 0 ? -1 : Math.floorDiv(dayTime, DAY);
        int blocks = 0, chunks = 0, healing = 0;
        long soonest = Long.MAX_VALUE;
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (LevelChunk chunk : within(null, 0)) {
            ChunkScars scars = chunk.getExistingDataOrNull(GBHeal.SCARS.get());
            chunks++;
            blocks += scars.count;
            boolean due = due(scars, s, now, today, chunk);
            if (due) {
                healing++;
            }
            double dx = chunk.getPos().getMinBlockX() + 8 - at.x, dz = chunk.getPos().getMinBlockZ() + 8 - at.z;
            double d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                nearest = new BlockPos(chunk.getPos().getMinBlockX() + 8, (int) at.y, chunk.getPos().getMinBlockZ() + 8);
                soonest = due ? 0 : s.schedule() == dev.groundburst.config.GBServerConfig.HealSchedule.AFTER_WAIT
                        ? Math.max(0, scars.changed + s.waitTicks() + (long) (scars.jitter * s.randomTicks()) - now) : -1;
            }
        }
        return new Status(blocks, chunks, healing, soonest == Long.MAX_VALUE ? -1 : soonest, nearest);
    }

    /**
     * GameTests: heals now every remembered block inside {@code box} with {@code s}, its chunks begun and (once empty)
     * finished as the healing does them; how many blocks it went through.
     */
    public int healNow(BoundingBox box, HealSettings s) {
        int healed = 0;
        long now = this.level.getGameTime();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = this.level.getChunkSource().getChunkNow(cx, cz);
                ChunkScars scars = chunk == null ? null : chunk.getExistingDataOrNull(GBHeal.SCARS.get());
                if (scars == null) {
                    continue;
                }
                if (scars.started < 0) {
                    begin(chunk, scars, s, now);
                }
                healed += heal(chunk, scars, s, Integer.MAX_VALUE, Long.MAX_VALUE, box);
                if (scars.count == 0) {
                    finish(chunk, scars, s);
                } else {
                    hangInside(scars, box);                // (another test's damage may share the chunk)
                    chunk.markUnsaved();
                }
            }
        }
        return healed;
    }
}
