package dev.groundburst.heal;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The damage remembered in one chunk (DESIGN.md §21.9), kept with the chunk (a data attachment, saved and loaded with
 * it): for every block a weapon changed, what it was before, with its block entity's data; the paintings and item
 * frames taken down; the biome cells the wasteland painted over. A block is remembered as it was the first time it
 * was damaged, so a block hit twice comes back as it was before the first. And when the chunk is due to heal: the last
 * damage's time, how far the healing has got.
 *
 * <p>A section's blocks are kept as the game keeps them: a palette of the states found, an index into it for each of
 * the 4 096 cells (a byte each, two once it holds more than 256 states), a bit for each cell that is remembered.
 */
public final class ChunkScars {
    static final int CELLS = 4096;

    /** Game time of the last damage here that holds healing off. */
    long changed;
    /** The day (since the clock began) of that damage, and the day its wait ran out (MORNING); -1 none or not yet. */
    long day = -1, waitedDay = -1;
    /** This chunk's share (0 to 1) of the random extra wait, fixed when it was first damaged. */
    float jitter;
    /** Game time it began to heal (-1: not yet) and how many blocks it had to heal then. */
    long started = -1;
    int total;
    /** To heal now, whatever the wait (the heal command). */
    boolean rushed;
    final Int2ObjectOpenHashMap<Section> sections = new Int2ObjectOpenHashMap<>();
    /** Paintings, item frames and armour stands taken down, as saved entities. */
    final List<CompoundTag> entities = new ArrayList<>();
    /** By section y: each of its 64 biome cells' biome before the wasteland (null where it was not painted). */
    final Int2ObjectOpenHashMap<String[]> biomes = new Int2ObjectOpenHashMap<>();

    // Not saved.
    /** Blocks still to heal. */
    int count;
    /** Blocks it may heal this tick (fractions carry over). */
    float credit;
    /** Ticks until it may make a sound again. */
    int quiet;
    /** Game time it next looks for fires burning by it, while one does (it waits for them to go out). */
    long fireCheck;

    public ChunkScars() {}

    static int cell(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    @Nullable Section section(int sectionY) {
        return this.sections.get(sectionY);
    }

    boolean has(BlockPos pos) {
        Section s = this.sections.get(pos.getY() >> 4);
        return s != null && s.has(cell(pos.getX(), pos.getY(), pos.getZ()));
    }

    boolean claimed(BlockPos pos) {
        Section s = this.sections.get(pos.getY() >> 4);
        return s != null && s.claimed(cell(pos.getX(), pos.getY(), pos.getZ()));
    }

    /**
     * Remembers the block at {@code pos} as {@code original} (with {@code be}, its block entity's data), unless it is
     * remembered already; a block a player put down since (claimed) is remembered afresh as theirs.
     */
    void put(BlockPos pos, BlockState original, @Nullable CompoundTag be) {
        int sy = pos.getY() >> 4, i = cell(pos.getX(), pos.getY(), pos.getZ());
        Section s = this.sections.computeIfAbsent(sy, k -> new Section());
        if (!s.has(i)) {
            s.put(i, original, be);
            this.count++;
        } else if (s.claimed(i)) {
            s.replace(i, original, be);
        }
    }

    /**
     * A remembered block entity's mark: what it held spilled out as it broke (the server let it drop), so it comes back
     * without it, and nothing is had twice (DESIGN.md §21.9).
     */
    static final String SPILLED = "groundburst:spilled";

    /** The block remembered at {@code pos}, or null if none is. */
    @Nullable BlockState original(BlockPos pos) {
        Section s = this.sections.get(pos.getY() >> 4);
        int i = cell(pos.getX(), pos.getY(), pos.getZ());
        return s != null && s.has(i) ? s.original(i) : null;
    }

    /** The block remembered at {@code pos} spilled what it held as it broke: it comes back empty. */
    void spilled(BlockPos pos) {
        Section s = this.sections.get(pos.getY() >> 4);
        CompoundTag be = s == null ? null : s.blockEntity(cell(pos.getX(), pos.getY(), pos.getZ()));
        if (be != null) {
            be.putBoolean(SPILLED, true);
        }
    }

    /**
     * The block remembered at {@code pos} still stands as it was remembered, and its block entity is saved again as it is
     * now ({@code be}): damage coming back for it takes what it holds now, not what it held the first time.
     */
    void refresh(BlockPos pos, CompoundTag be) {
        Section s = this.sections.get(pos.getY() >> 4);
        int i = cell(pos.getX(), pos.getY(), pos.getZ());
        if (s != null && s.has(i)) {
            s.blockEntities.put(i, be);
        }
    }

    /** Forgets the block at (x, y, z): it will not heal. */
    void clear(int x, int y, int z) {
        Section s = this.sections.get(y >> 4);
        if (s != null && s.clear(cell(x, y, z))) {
            this.count--;
            if (s.count == 0) {
                this.sections.remove(y >> 4);
            }
        }
    }

    /** A player has put a block at {@code pos}, over remembered damage. */
    void claim(BlockPos pos) {
        Section s = this.sections.get(pos.getY() >> 4);
        if (s != null) {
            s.claim(cell(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    /** Fresh damage: the wait starts again, and so does any healing already under way. */
    void heldOff(long now, long day) {
        this.changed = now;
        this.day = day;
        this.waitedDay = -1;
        this.started = -1;
        this.rushed = false;
    }

    /** The wasteland is about to paint biome cell (qx, qy, qz) (quarter coordinates) over {@code before}. */
    void biome(int qx, int qy, int qz, String before) {
        String[] cells = this.biomes.computeIfAbsent(qy >> 2, k -> new String[64]);
        int c = (qy & 3) << 4 | (qz & 3) << 2 | (qx & 3);
        if (cells[c] == null) {
            cells[c] = before;
        }
    }

    /** What biome cell (qx, qy, qz) was before the wasteland, or null if it was not painted. */
    @Nullable String biomeBefore(int qx, int qy, int qz) {
        String[] cells = this.biomes.get(qy >> 2);
        return cells == null ? null : cells[(qy & 3) << 4 | (qz & 3) << 2 | (qx & 3)];
    }

    /** Nothing left to heal: no block, no decoration, no biome. */
    boolean isEmpty() {
        return this.count == 0 && this.entities.isEmpty() && this.biomes.isEmpty();
    }

    /** One section's remembered blocks. */
    static final class Section {
        final long[] touched = new long[64];
        long @Nullable [] claimed;
        final List<BlockState> palette = new ArrayList<>();
        private @Nullable Object2IntOpenHashMap<BlockState> lookup;
        private byte @Nullable [] small = new byte[CELLS];
        private short @Nullable [] wide;
        final Int2ObjectOpenHashMap<CompoundTag> blockEntities = new Int2ObjectOpenHashMap<>();
        int count;

        boolean has(int i) {
            return (this.touched[i >> 6] & 1L << i) != 0;
        }

        boolean claimed(int i) {
            return this.claimed != null && (this.claimed[i >> 6] & 1L << i) != 0;
        }

        void put(int i, BlockState original, @Nullable CompoundTag be) {
            this.touched[i >> 6] |= 1L << i;
            this.count++;
            replace(i, original, be);
        }

        void replace(int i, BlockState original, @Nullable CompoundTag be) {
            setIndex(i, indexOf(original));
            if (be != null) {
                this.blockEntities.put(i, be);
            } else {
                this.blockEntities.remove(i);
            }
            if (this.claimed != null) {
                this.claimed[i >> 6] &= ~(1L << i);
            }
        }

        /** Forgets cell {@code i}; true if it was remembered. */
        boolean clear(int i) {
            if (!has(i)) {
                return false;
            }
            this.touched[i >> 6] &= ~(1L << i);
            this.count--;
            this.blockEntities.remove(i);
            if (this.claimed != null) {
                this.claimed[i >> 6] &= ~(1L << i);
            }
            return true;
        }

        void claim(int i) {
            if (has(i)) {
                if (this.claimed == null) {
                    this.claimed = new long[64];
                }
                this.claimed[i >> 6] |= 1L << i;
            }
        }

        BlockState original(int i) {
            int k = this.wide != null ? this.wide[i] & 0xFFFF : this.small[i] & 0xFF;
            return k < this.palette.size() ? this.palette.get(k) : Blocks.AIR.defaultBlockState();
        }

        @Nullable CompoundTag blockEntity(int i) {
            return this.blockEntities.get(i);
        }

        private int indexOf(BlockState state) {
            if (this.lookup == null) {
                this.lookup = new Object2IntOpenHashMap<>();
                this.lookup.defaultReturnValue(-1);
                for (int k = 0; k < this.palette.size(); k++) {
                    this.lookup.putIfAbsent(this.palette.get(k), k);
                }
            }
            int k = this.lookup.getInt(state);
            if (k < 0) {
                k = this.palette.size();
                this.palette.add(state);
                this.lookup.put(state, k);
            }
            return k;
        }

        private void setIndex(int i, int k) {
            if (this.wide == null && k > 255) {
                this.wide = new short[CELLS];
                for (int c = 0; c < CELLS; c++) {
                    this.wide[c] = (short) (this.small[c] & 0xFF);
                }
                this.small = null;
            }
            if (this.wide != null) {
                this.wide[i] = (short) k;
            } else {
                this.small[i] = (byte) k;
            }
        }

        private ByteBuffer indices() {
            if (this.wide != null) {
                ByteBuffer b = ByteBuffer.allocate(CELLS * 2);
                b.asShortBuffer().put(this.wide);
                return b;
            }
            return ByteBuffer.wrap(this.small.clone());
        }

        private static Section of(SavedSection saved) {
            Section s = new Section();
            System.arraycopy(saved.touched, 0, s.touched, 0, Math.min(64, saved.touched.length));
            if (saved.claimed.isPresent()) {
                s.claimed = Arrays.copyOf(saved.claimed.get(), 64);
            }
            s.palette.addAll(saved.palette);
            ByteBuffer b = saved.indices.duplicate();
            if (saved.wide && b.remaining() >= CELLS * 2) {
                s.wide = new short[CELLS];
                b.asShortBuffer().get(s.wide);
                s.small = null;
            } else if (!saved.wide && b.remaining() >= CELLS) {
                b.get(s.small);
            } else {
                java.util.Arrays.fill(s.touched, 0);       // unreadable: nothing remembered
            }
            for (SavedBlockEntity e : saved.blockEntities) {
                s.blockEntities.put(e.cell, e.tag);
            }
            for (long w : s.touched) {
                s.count += Long.bitCount(w);
            }
            return s;
        }

        private SavedSection save(int y) {
            List<SavedBlockEntity> bes = new ArrayList<>();
            for (Int2ObjectMap.Entry<CompoundTag> e : this.blockEntities.int2ObjectEntrySet()) {
                bes.add(new SavedBlockEntity(e.getIntKey(), e.getValue()));
            }
            return new SavedSection(y, this.touched.clone(), Optional.ofNullable(this.claimed).map(long[]::clone), List.copyOf(this.palette),
                    indices(), this.wide != null, bes);
        }
    }

    // --- saving ------------------------------------------------------------------------------------------------------------------
    private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(java.util.stream.LongStream::toArray, Arrays::stream);
    /** A state from a mod no longer there is read as air: that block stays as the damage left it. */
    private static final Codec<BlockState> STATE = BlockState.CODEC.orElse(Blocks.AIR.defaultBlockState());

    private record SavedBlockEntity(int cell, CompoundTag tag) {
        static final Codec<SavedBlockEntity> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("cell").forGetter(SavedBlockEntity::cell),
                CompoundTag.CODEC.fieldOf("tag").forGetter(SavedBlockEntity::tag)
        ).apply(i, SavedBlockEntity::new));
    }

    private record SavedSection(int y, long[] touched, Optional<long[]> claimed, List<BlockState> palette, ByteBuffer indices, boolean wide,
            List<SavedBlockEntity> blockEntities) {
        static final Codec<SavedSection> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("y").forGetter(SavedSection::y),
                LONGS.fieldOf("touched").forGetter(SavedSection::touched),
                LONGS.optionalFieldOf("claimed").forGetter(SavedSection::claimed),
                STATE.listOf().fieldOf("palette").forGetter(SavedSection::palette),
                Codec.BYTE_BUFFER.fieldOf("indices").forGetter(SavedSection::indices),
                Codec.BOOL.optionalFieldOf("wide", false).forGetter(SavedSection::wide),
                SavedBlockEntity.CODEC.listOf().optionalFieldOf("block_entities", List.of()).forGetter(SavedSection::blockEntities)
        ).apply(i, SavedSection::new));
    }

    private record SavedBiomes(int y, List<String> cells) {
        static final Codec<SavedBiomes> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("y").forGetter(SavedBiomes::y),
                Codec.STRING.listOf().fieldOf("cells").forGetter(SavedBiomes::cells)
        ).apply(i, SavedBiomes::new));
    }

    private record Saved(long changed, long day, long waitedDay, float jitter, long started, int total, boolean rushed,
            List<SavedSection> sections, List<CompoundTag> entities, List<SavedBiomes> biomes) {
        static final MapCodec<Saved> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.LONG.fieldOf("changed").forGetter(Saved::changed),
                Codec.LONG.optionalFieldOf("day", -1L).forGetter(Saved::day),
                Codec.LONG.optionalFieldOf("waited_day", -1L).forGetter(Saved::waitedDay),
                Codec.FLOAT.optionalFieldOf("jitter", 0f).forGetter(Saved::jitter),
                Codec.LONG.optionalFieldOf("started", -1L).forGetter(Saved::started),
                Codec.INT.optionalFieldOf("total", 0).forGetter(Saved::total),
                Codec.BOOL.optionalFieldOf("rushed", false).forGetter(Saved::rushed),
                SavedSection.CODEC.listOf().optionalFieldOf("sections", List.of()).forGetter(Saved::sections),
                CompoundTag.CODEC.listOf().optionalFieldOf("entities", List.of()).forGetter(Saved::entities),
                SavedBiomes.CODEC.listOf().optionalFieldOf("biomes", List.of()).forGetter(Saved::biomes)
        ).apply(i, Saved::new));
    }

    /** How a chunk's damage is saved with it. */
    public static final MapCodec<ChunkScars> CODEC = Saved.CODEC.xmap(ChunkScars::of, ChunkScars::save);

    /**
     * GameTests: saves the record of the chunk at {@code pos} as the game would and reads it back; what differs, or
     * null if every remembered block (its state, its block entity) and every decoration came back the same.
     */
    public static @Nullable String checkSaving(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        ChunkScars before = chunk == null ? null : chunk.getExistingDataOrNull(GBHeal.SCARS.get());
        if (before == null) {
            return "nothing is remembered there";
        }
        var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, level.registryAccess());
        var tag = CODEC.codec().encodeStart(ops, before).getOrThrow();
        ChunkScars after = CODEC.codec().parse(ops, tag).getOrThrow();
        if (after.count != before.count || after.entities.size() != before.entities.size() || after.changed != before.changed) {
            return "counts differ: " + before.count + " / " + after.count;
        }
        for (Int2ObjectMap.Entry<Section> e : before.sections.int2ObjectEntrySet()) {
            Section a = e.getValue(), b = after.sections.get(e.getIntKey());
            if (b == null || !Arrays.equals(a.touched, b.touched)) {
                return "section " + e.getIntKey() + " lost its cells";
            }
            for (int i = 0; i < CELLS; i++) {
                if (a.has(i) && (a.original(i) != b.original(i) || !java.util.Objects.equals(a.blockEntity(i), b.blockEntity(i)))) {
                    return "cell " + i + " of section " + e.getIntKey() + ": " + a.original(i) + " / " + b.original(i);
                }
            }
        }
        return null;
    }

    private static ChunkScars of(Saved saved) {
        ChunkScars c = new ChunkScars();
        c.changed = saved.changed;
        c.day = saved.day;
        c.waitedDay = saved.waitedDay;
        c.jitter = saved.jitter;
        c.started = saved.started;
        c.total = saved.total;
        c.rushed = saved.rushed;
        for (SavedSection s : saved.sections) {
            Section section = Section.of(s);
            if (section.count > 0) {
                c.sections.put(s.y, section);
                c.count += section.count;
            }
        }
        c.entities.addAll(saved.entities);
        for (SavedBiomes b : saved.biomes) {
            String[] cells = new String[64];
            for (int k = 0; k < Math.min(64, b.cells.size()); k++) {
                String id = b.cells.get(k);
                cells[k] = id.isEmpty() ? null : id;
            }
            c.biomes.put(b.y, cells);
        }
        return c;
    }

    private Saved save() {
        List<SavedSection> s = new ArrayList<>();
        for (Int2ObjectMap.Entry<Section> e : this.sections.int2ObjectEntrySet()) {
            if (e.getValue().count > 0) {
                s.add(e.getValue().save(e.getIntKey()));
            }
        }
        List<SavedBiomes> b = new ArrayList<>();
        for (Int2ObjectMap.Entry<String[]> e : this.biomes.int2ObjectEntrySet()) {
            List<String> cells = new ArrayList<>(64);
            for (String id : e.getValue()) {
                cells.add(id == null ? "" : id);
            }
            b.add(new SavedBiomes(e.getIntKey(), cells));
        }
        return new Saved(this.changed, this.day, this.waitedDay, this.jitter, this.started, this.total, this.rushed, s,
                List.copyOf(this.entities), b);
    }
}
