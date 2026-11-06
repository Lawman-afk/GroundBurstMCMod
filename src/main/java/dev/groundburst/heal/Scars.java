package dev.groundburst.heal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.heal.HealSettings.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * How weapons change blocks so that healing can put them back (DESIGN.md §21.9). A weapon takes a {@link Recorder}
 * when it goes off and sets its blocks through it: with healing on for its kind, each block is remembered as it was
 * before ({@link ChunkScars}, with the chunk), and one that will heal drops nothing and spills nothing (it comes back
 * with what it held); with healing off it is a plain block set. What the damage sets going (a fire burning on, sand
 * falling, leaves decaying, a painting losing its wall) is remembered by {@link #aftermath} where damage already is.
 */
public final class Scars {
    /** The weapons that are not blast profiles, as {@code healing.what.excludedWeapons} names them. */
    public static final Identifier PARTICLE_BEAM = GroundBurst.id("particle_beam"), CRYO_FREEZE = GroundBurst.id("cryo_freeze"),
            FROST = GroundBurst.id("frost"), SINGULARITY = GroundBurst.id("singularity"), ANTIMATTER = GroundBurst.id("antimatter"),
            STORM = GroundBurst.id("storm"), FALLOUT = GroundBurst.id("fallout"), RADIATION = GroundBurst.id("radiation"),
            MELTDOWN = GroundBurst.id("meltdown"), TOXIN = GroundBurst.id("toxin"), BREACH = GroundBurst.id("breach"),
            INCENDIARY = GroundBurst.id("incendiary");

    private Scars() {}

    /** The recorder for a weapon of {@code kind} going off at {@code at}: it remembers what it changes if that heals here. */
    public static Recorder recorder(ServerLevel level, Vec3 at, Identifier weapon, Kind kind) {
        HealSettings s = HealSettings.at(level, at);
        return new Recorder(level, s.heals(kind, weapon, level) ? s : null, true);
    }

    /**
     * As {@link #recorder}, for slow damage that should not hold healing off (radiation killing plants goes on for as
     * long as the radiation lasts: it must not keep the ground from ever healing).
     */
    public static Recorder slowRecorder(ServerLevel level, Vec3 at, Identifier weapon, Kind kind) {
        HealSettings s = HealSettings.at(level, at);
        return new Recorder(level, s.heals(kind, weapon, level) ? s : null, false);
    }

    /**
     * The kind of damage a blast profile does, for {@code healing.what}: nuclear by its terrain (and the meltdown, the
     * fizzle and the dirty bomb), special and chemical by name, conventional otherwise.
     */
    public static Kind kindOf(Identifier profile, boolean nuclearTerrain) {
        String path = profile.getPath();
        if (nuclearTerrain || path.contains("nuclear") || path.contains("meltdown") || path.contains("dirty")) {
            return Kind.NUCLEAR;
        }
        if (path.contains("cryo") || path.contains("emp") || path.contains("storm") || path.contains("antimatter")
                || path.contains("singularity")) {
            return Kind.SPECIAL;
        }
        if (path.contains("chemical") || path.contains("gas") || path.contains("toxic")) {
            return Kind.CHEMICAL;
        }
        return Kind.CONVENTIONAL;
    }

    /** Sets one block as a weapon of {@code kind} does (for weapons that change a block or two now and then). */
    public static boolean set(ServerLevel level, BlockPos pos, BlockState state, int flags, Identifier weapon, Kind kind) {
        return recorder(level, Vec3.atCenterOf(pos), weapon, kind).set(pos, state, flags);
    }

    /**
     * Something the damage set going is about to change the block at {@code pos} (fire burning it, leaves decaying,
     * sand falling from it): remembered if there is damage in its chunk or one beside it. True if it will heal.
     * {@code holdOff}: it starts the chunk's wait again (a fire still burning does; leaves decaying, which goes on for
     * minutes, and sand that has fallen do not).
     */
    public static boolean aftermath(ServerLevel level, BlockPos pos, boolean holdOff) {
        return aftermath(level, pos, null, null, holdOff);
    }

    /** As {@link #aftermath(ServerLevel, BlockPos, boolean)}, with what the block was, if it is already gone (sand that has fallen). */
    public static boolean aftermath(ServerLevel level, BlockPos pos, @Nullable BlockState original, @Nullable CompoundTag be, boolean holdOff) {
        if (!nearDamage(level, pos)) {
            return false;                                  // (the usual answer, and the quickest found: most ticks there is none)
        }
        HealSettings s = HealSettings.at(level, Vec3.atCenterOf(pos));
        return s.enabled() && s.aftermath() && record(level, s, pos, original, be, holdOff);
    }

    /**
     * How many remembered changes are running their vanilla course now (the server thread's alone): while one does,
     * what vanilla's shape updates change round it (a crop losing its farmland to water running in, a torch its wall to
     * a fire, a fence an arm) is remembered too. A player's own building never runs inside one.
     */
    private static int cascade;

    /** A remembered change is about to run its course (call {@link #endCascade} after, in a finally). */
    public static void beginCascade() {
        cascade++;
    }

    public static void endCascade() {
        if (cascade > 0) {
            cascade--;
        }
    }

    /** Each tick: none can still be running (should one have thrown on its way). */
    static void resetCascade() {
        cascade = 0;
    }

    /**
     * Vanilla is about to change the block at {@code pos} because a neighbour changed shape. Inside a remembered
     * change's course it is remembered first; true if it is to go without its drops (it will come back).
     */
    public static boolean shapeChanged(ServerLevel level, BlockPos pos) {
        if (cascade <= 0) {
            return false;
        }
        HealSettings s = HealSettings.at(level, Vec3.atCenterOf(pos));
        return s.enabled() && record(level, s, pos, null, null, false) && !s.drops();
    }

    /**
     * {@link #aftermath(ServerLevel, BlockPos, boolean)}, and if it remembered the change, {@code change} runs as its
     * course (what its shape updates change is remembered too).
     */
    public static void aftermathCourse(ServerLevel level, BlockPos pos, boolean holdOff, Runnable change) {
        boolean remembered = nearDamage(level, pos) && aftermath(level, pos, holdOff);
        if (remembered) {
            beginCascade();
        }
        try {
            change.run();
        } finally {
            if (remembered) {
                endCascade();
            }
        }
    }

    /** Whether healing is on and there is remembered damage near {@code pos} (a fire there may burn what heals). */
    public static boolean watching(ServerLevel level, BlockPos pos) {
        if (!nearDamage(level, pos)) {
            return false;
        }
        HealSettings s = HealSettings.at(level, Vec3.atCenterOf(pos));
        return s.enabled() && s.aftermath();
    }

    /**
     * Whether there is remembered damage in the chunk at {@code pos} or one beside it (a damaged chunk that is loaded
     * is one the healer keeps track of: nine lookups in its map, none at all where nothing is damaged). It runs for
     * every fire's tick, fluid's spread, grass's spread and farmland's tick in the world, so it must be quick.
     */
    static boolean nearDamage(ServerLevel level, BlockPos pos) {
        Healer healer = Healer.of(level);
        if (!healer.tracksAny()) {
            return false;
        }
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (healer.tracks(cx + dx, cz + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a block that will heal at {@code pos} may still drop items (the server's {@code dropItems}). */
    public static boolean healingDrops(ServerLevel level, BlockPos pos) {
        return HealSettings.at(level, Vec3.atCenterOf(pos)).drops();
    }

    /** Whether the block at {@code pos} is remembered (it will heal). */
    public static boolean remembered(ServerLevel level, BlockPos pos) {
        if (!Healer.of(level).tracks(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;                                  // (its chunk is not one with damage: at once)
        }
        LevelChunk c = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        ChunkScars scars = c == null ? null : c.getExistingDataOrNull(GBHeal.SCARS.get());
        return scars != null && scars.has(pos);
    }

    /**
     * A player has put a block down at {@code pos}. Over remembered damage, the server's {@code playerBlocks} says
     * what becomes of it: KEEP leaves the ground round it (and the air over it) unhealed, DROP and REPLACE mark it.
     */
    public static void claim(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        ChunkScars scars = chunk == null ? null : chunk.getExistingDataOrNull(GBHeal.SCARS.get());
        if (scars == null || !scars.has(pos)) {
            return;
        }
        HealSettings s = HealSettings.at(level, Vec3.atCenterOf(pos));
        switch (s.playerBlocks()) {
            case KEEP -> spare(level, pos, s.keepRadius());
            case DROP, REPLACE -> {
                scars.claim(pos);
                chunk.markUnsaved();
            }
        }
    }

    /** The remembered damage within {@code r} of {@code pos}, and in the columns over that up to the sky, is forgotten. */
    static void spare(ServerLevel level, BlockPos pos, int r) {
        int top = level.getMaxY();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int across = dx * dx + dz * dz;
                if (across > r * r) {
                    continue;
                }
                int x = pos.getX() + dx, z = pos.getZ() + dz;
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                ChunkScars scars = chunk == null ? null : chunk.getExistingDataOrNull(GBHeal.SCARS.get());
                if (scars == null) {
                    continue;
                }
                int below = (int) Math.floor(Math.sqrt(r * r - across));
                for (int y = pos.getY() - below; y <= top; y++) {
                    if (scars.section(y >> 4) == null) {
                        y |= 15;                           // nothing remembered in this section: on to the next
                        continue;
                    }
                    scars.clear(x, y, z);
                }
                chunk.markUnsaved();
            }
        }
    }

    /**
     * Remembers the block at {@code pos} as it is now (or as {@code known}, if it has already changed) before damage
     * changes it; true if it will heal (it is remembered, now or from earlier damage). {@code holdOff}: the damage
     * starts its chunk's wait again.
     */
    static boolean record(ServerLevel level, HealSettings s, BlockPos pos, @Nullable BlockState known, @Nullable CompoundTag knownBe,
            boolean holdOff) {
        if (level.isOutsideBuildHeight(pos)) {
            return false;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        ChunkScars scars = chunk.getExistingDataOrNull(GBHeal.SCARS.get());
        boolean known_ = scars != null && scars.has(pos) && !scars.claimed(pos);
        if (!known_) {
            BlockState original = unburnt(known != null ? known : chunk.getBlockState(pos));
            if (!s.keeps(original)) {
                return false;
            }
            CompoundTag be = knownBe;
            if (known == null && original.hasBlockEntity()) {
                BlockEntity e = level.getBlockEntity(pos);
                if (e != null) {
                    be = Snapshots.save(e, level);
                }
            }
            if (scars == null) {
                scars = new ChunkScars();
                scars.jitter = level.getRandom().nextFloat();
                scars.changed = level.getGameTime();
                chunk.setData(GBHeal.SCARS.get(), scars);
            }
            scars.put(pos, original, be);
            Healer.of(level).track(chunk);
        } else if (known == null) {
            // Remembered already, and maybe still standing as it was (the half of a double chest the first blast left):
            // what it holds now is what this damage takes, so it is saved again.
            BlockState live = chunk.getBlockState(pos), was = scars.original(pos);
            if (was != null && live.hasBlockEntity() && live.getBlock() == was.getBlock()) {
                BlockEntity e = level.getBlockEntity(pos);
                if (e != null) {
                    scars.refresh(pos, Snapshots.save(e, level));
                }
            }
        }
        if (holdOff) {
            long dayTime = Snapshots.dayTime(level);
            scars.heldOff(level.getGameTime(), dayTime < 0 ? -1 : Math.floorDiv(dayTime, Healer.DAY));
        }
        chunk.markUnsaved();
        return true;
    }

    /**
     * The block at {@code pos}, remembered as {@code broke} was, spilled what it held as it broke (the server lets healing
     * blocks drop): it comes back without it. One remembered as something else (it stood in for what the damage took
     * first) keeps the first one's contents.
     */
    static void spilled(ServerLevel level, BlockPos pos, Block broke) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        ChunkScars scars = chunk == null ? null : chunk.getExistingDataOrNull(GBHeal.SCARS.get());
        BlockState was = scars == null ? null : scars.original(pos);
        if (was != null && was.getBlock() == broke) {
            scars.spilled(pos);
            chunk.markUnsaved();
        }
    }

    /** The block remembered at {@code pos}, or null if none is (or its chunk is not loaded). */
    static @Nullable BlockState rememberedAs(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        ChunkScars scars = chunk == null ? null : chunk.getExistingDataOrNull(GBHeal.SCARS.get());
        return scars == null ? null : scars.original(pos);
    }

    /**
     * What a block is remembered as: a fire is the air it burns in (a fire next to a burning tree changes shape as the
     * tree burns, and would be remembered so; put back, it burnt the healed tree again, and that healed with its fire
     * again, over and over). A fire is never put back.
     */
    static BlockState unburnt(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : state;
    }

    /**
     * A weapon's way of setting blocks (see the class comment). One with healing off for it (or none, {@link #NONE}'s
     * level aside) sets blocks as they are set anyway.
     */
    public static final class Recorder {
        private final ServerLevel level;
        private final @Nullable HealSettings settings;
        private final boolean holdOff;

        Recorder(ServerLevel level, @Nullable HealSettings settings, boolean holdOff) {
            this.level = level;
            this.settings = settings;
            this.holdOff = holdOff;
        }

        /** An explosion's own hit on a block ({@link Recorder#hit}), told whether to swallow the block's drops. */
        @FunctionalInterface
        public interface Hit {
            void run(boolean swallowDrops);
        }

        /** A recorder that remembers nothing (for the GameTests' own paint and flood, and for weapons that do not heal). */
        public static Recorder none(ServerLevel level) {
            return new Recorder(level, null, false);
        }

        /** Whether what it changes is remembered. */
        public boolean active() {
            return this.settings != null;
        }

        /** The block last remembered, and whether it will heal: a weapon asks, then sets it (once, not twice). */
        private long lastPos = Long.MIN_VALUE;
        private boolean lastHeals;

        /** Remembers the block at {@code pos} before the weapon changes it; true if it will heal (so it drops nothing). */
        public boolean record(BlockPos pos) {
            if (this.settings == null) {
                return false;
            }
            this.lastHeals = Scars.record(this.level, this.settings, pos, null, null, this.holdOff);
            this.lastPos = pos.asLong();
            return this.lastHeals;
        }

        /**
         * Sets a block as the weapon does, remembering what was there first. Set with shape updates, its neighbours may
         * change with it (grass under new snow, a fence's arms, a torch or a door that loses its wall): what each was is
         * noted first, those that changed are remembered too, and what they drop as they come off is held back.
         */
        public boolean set(BlockPos pos, BlockState state, int flags) {
            if (this.settings == null) {
                return this.level.setBlock(pos, state, flags);
            }
            boolean heals = pos.asLong() == this.lastPos ? this.lastHeals : record(pos);   // (just asked: a blast's destroy)
            this.lastPos = Long.MIN_VALUE;
            boolean hush = !this.settings.drops();
            if (heals && hush) {
                quiet(pos);
            }
            // Let drop, it spills what it holds as it goes (unless the write skips that, as the blast engine's does).
            Spill spill = heals && !hush && (flags & Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS) == 0 ? Spill.of(this.level, pos) : null;
            if ((flags & Block.UPDATE_KNOWN_SHAPE) != 0) {
                boolean done = this.level.setBlock(pos, state, flags);
                if (spill != null) {
                    spill.check(this.level, pos);
                }
                return done;
            }
            Around around = Around.of(this.level, pos);
            Healer healer = Healer.of(this.level);
            if (hush) {
                healer.hushWhile(around.centres);
            }
            boolean done;
            beginCascade();                                // (and what changes further off, through its neighbours)
            try {
                done = this.level.setBlock(pos, state, flags);
            } finally {
                endCascade();
                if (hush) {
                    healer.hushWhile(null);
                }
            }
            if (spill != null) {
                spill.check(this.level, pos);
            }
            around.remember(this.level, this.settings, this.holdOff);
            return done;
        }

        /**
         * A block that will heal is about to drop whole as it breaks (the server lets it): what it holds stays behind for
         * its healing and is left out of what it drops (a shulker box drops empty and comes back full), so nothing is
         * had twice. Only for the block remembered there (not one that stands in for it).
         */
        public void holdContents(BlockPos pos) {
            if (this.settings == null) {
                return;
            }
            BlockState was = rememberedAs(this.level, pos);
            BlockEntity e = this.level.getBlockEntity(pos);
            if (was != null && e != null && was.getBlock() == this.level.getBlockState(pos).getBlock()) {
                Snapshots.empty(e, this.level);
            }
        }

        /**
         * The game's or another mod's explosion hits the block at {@code pos} ({@link Explosions}): it is remembered first,
         * then {@code hit} runs the explosion's own hit, told to swallow the block's drops if it will heal (unless the
         * server lets them drop), its contents kept (its block entity goes quietly first), its experience held back. What
         * its going takes with it round it (a torch on it, the other half of a door) is remembered as with {@link #set}.
         * True if it will heal.
         */
        public boolean hit(BlockPos pos, Hit hit) {
            if (this.settings == null) {
                hit.run(false);
                return false;
            }
            boolean heals = record(pos);
            this.lastPos = Long.MIN_VALUE;
            boolean hush = !this.settings.drops();
            if (heals && hush) {
                quiet(pos);
            }
            Spill spill = heals && !hush ? Spill.of(this.level, pos) : null;   // (let drop, the game's explosions spill a chest)
            Around around = Around.of(this.level, pos);
            Healer healer = Healer.of(this.level);
            if (hush) {
                List<Vec3> held = new java.util.ArrayList<>(around.centres);
                if (heals) {
                    held.add(Vec3.atCenterOf(pos));            // its own experience (an ore a player's TNT blew)
                }
                healer.hushWhile(held);
            }
            beginCascade();
            try {
                hit.run(heals && hush);
            } finally {
                endCascade();
                if (hush) {
                    healer.hushWhile(null);
                }
            }
            if (spill != null) {
                spill.check(this.level, pos);
            }
            around.remember(this.level, this.settings, this.holdOff);
            return heals;
        }

        /** A block entity about to go with its block, the server letting it spill: whether it did is told after. */
        private record Spill(BlockEntity held, Block block) {
            static @Nullable Spill of(ServerLevel level, BlockPos pos) {
                BlockEntity e = level.getBlockEntity(pos);
                return e == null ? null : new Spill(e, level.getBlockState(pos).getBlock());
            }

            /** Gone with its block (a chest broken spills what it held): it comes back without it. */
            void check(ServerLevel level, BlockPos pos) {
                if (level.getBlockEntity(pos) != this.held) {
                    spilled(level, pos, this.block);
                }
            }
        }

        /** {@link #set} with the usual flags (neighbours told, clients told). */
        public boolean setAndUpdate(BlockPos pos, BlockState state) {
            return set(pos, state, Block.UPDATE_ALL);
        }

        /** Removes a block as the weapon does (a waterlogged one leaves its water), remembering it first. */
        public boolean remove(BlockPos pos) {
            return set(pos, this.level.getFluidState(pos).createLegacyBlock(), Block.UPDATE_ALL);
        }

        /**
         * Whether a block the weapon destroys may drop itself, {@code heals} being what {@link #record} said of it: one
         * that will heal does not, unless the server lets it.
         */
        public boolean mayDrop(boolean heals) {
            return !heals || this.settings == null || this.settings.drops();
        }

        /**
         * Takes a decoration (a painting, an item frame, an armour stand) down, to be put back when its chunk heals,
         * instead of letting the blast knock it down with its drops. True if it did.
         */
        public boolean takeDown(Entity e) {
            if (this.settings == null || !this.settings.decorations() || e.isRemoved()) {
                return false;
            }
            return Scars.takeDown(this.level, e, this.holdOff);
        }

        /** Whether decorations (paintings, item frames, armour stands) the damage breaks are kept, to be put back. */
        public boolean keepsDecorations() {
            return this.settings != null && this.settings.decorations();
        }

        /** {@code e}, broken by the damage just now, is kept as {@code saved} (it as it was before) to be put back. */
        void keep(Entity e, CompoundTag saved) {
            Healer.of(this.level).taken(e);
            Scars.keep(this.level, e.blockPosition(), saved, this.holdOff);
        }

        /** The wasteland is about to paint a biome cell (quarter coordinates) of {@code chunk} over {@code before}. */
        public void biome(LevelChunk chunk, int qx, int qy, int qz, Holder<Biome> before) {
            if (this.settings == null || !this.settings.biomes()) {
                return;
            }
            ChunkScars scars = chunk.getExistingDataOrNull(GBHeal.SCARS.get());
            if (scars == null) {
                scars = new ChunkScars();
                scars.jitter = this.level.getRandom().nextFloat();
                scars.changed = this.level.getGameTime();
                chunk.setData(GBHeal.SCARS.get(), scars);
                Healer.of(this.level).track(chunk);
            }
            String id = before.unwrapKey().map(k -> k.identifier().toString()).orElse(null);
            if (id != null) {
                scars.biome(qx, qy, qz, id);
            }
        }

        /** A block that will heal comes back with its contents: its block entity goes quietly, spilling nothing. */
        private void quiet(BlockPos pos) {
            if (this.level.getBlockState(pos).hasBlockEntity()) {
                this.level.removeBlockEntity(pos);
            }
        }
    }

    /**
     * A block's neighbours as they were before it changed (not those remembered already), with the other half of any
     * door or tall plant among them (it goes with its half).
     */
    private record Around(BlockPos[] pos, BlockState[] state, @Nullable CompoundTag[] be, int n, List<Vec3> centres) {
        static Around of(ServerLevel level, BlockPos at) {
            BlockPos[] pos = new BlockPos[12];
            BlockState[] state = new BlockState[12];
            CompoundTag[] be = new CompoundTag[12];
            List<Vec3> centres = new java.util.ArrayList<>(6);
            int n = 0;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos p = at.relative(d);
                centres.add(Vec3.atCenterOf(p));
                n = note(level, p, pos, state, be, n);
                BlockState s = n > 0 && pos[n - 1] == p ? state[n - 1] : null;
                if (s != null && s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                    boolean lower = s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                            == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
                    n = note(level, lower ? p.above() : p.below(), pos, state, be, n);
                }
            }
            return new Around(pos, state, be, n, centres);
        }

        private static int note(ServerLevel level, BlockPos p, BlockPos[] pos, BlockState[] state, CompoundTag[] be, int n) {
            if (n >= pos.length || level.isOutsideBuildHeight(p) || !level.isLoaded(p) || remembered(level, p)) {
                return n;
            }
            BlockState s = level.getBlockState(p);
            pos[n] = p;
            state[n] = s;
            if (s.hasBlockEntity()) {
                BlockEntity e = level.getBlockEntity(p);
                be[n] = e == null ? null : Snapshots.save(e, level);
            }
            return n + 1;
        }

        /** Remembers the neighbours that have changed since. */
        void remember(ServerLevel level, HealSettings s, boolean holdOff) {
            for (int i = 0; i < this.n; i++) {
                if (level.getBlockState(this.pos[i]) != this.state[i]) {
                    Scars.record(level, s, this.pos[i], this.state[i], this.be[i], holdOff);
                }
            }
        }
    }

    /** Saves {@code e} into its chunk's damage and takes it out of the world, dropping nothing; true if it did. */
    static boolean takeDown(ServerLevel level, Entity e, boolean holdOff) {
        if (!keep(level, e, holdOff)) {
            return false;
        }
        Healer.of(level).taken(e);
        e.discard();
        return true;
    }

    /**
     * A painting or an item frame fell off a wall that damage took away: it hangs again when its chunk heals, and drops
     * nothing now (if healing remembers damage there).
     */
    public static void lostWall(ServerLevel level, Entity e) {
        if (Healer.of(level).wasTaken(e)) {
            return;
        }
        HealSettings s = HealSettings.at(level, e.position());
        if (s.enabled() && s.decorations() && s.aftermath() && nearDamage(level, e.blockPosition()) && keep(level, e, false)) {
            Healer.of(level).hush(e.position());
        }
    }

    /** Saves {@code e} into the damage of its chunk, to be put back when the chunk heals; true if it did. */
    private static boolean keep(ServerLevel level, Entity e, boolean holdOff) {
        return keep(level, e.blockPosition(), Snapshots.save(e, level), holdOff);
    }

    /** Puts a saved entity ({@code tag}, null if it is not one that is saved) into the damage of the chunk at {@code pos}. */
    private static boolean keep(ServerLevel level, BlockPos pos, @Nullable CompoundTag tag, boolean holdOff) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || tag == null) {
            return false;
        }
        ChunkScars scars = chunk.getExistingDataOrNull(GBHeal.SCARS.get());
        if (scars == null) {
            scars = new ChunkScars();
            scars.jitter = level.getRandom().nextFloat();
            scars.changed = level.getGameTime();
            chunk.setData(GBHeal.SCARS.get(), scars);
        }
        scars.entities.add(tag);
        if (holdOff) {
            long dayTime = Snapshots.dayTime(level);
            scars.heldOff(level.getGameTime(), dayTime < 0 ? -1 : Math.floorDiv(dayTime, Healer.DAY));
        }
        chunk.markUnsaved();
        Healer.of(level).track(chunk);
        return true;
    }
}
