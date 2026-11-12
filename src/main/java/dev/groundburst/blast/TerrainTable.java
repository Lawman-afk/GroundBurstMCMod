package dev.groundburst.blast;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.groundburst.GroundBurst;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a blast turns blocks into (DESIGN.md §11.1 terrain table, §21.7, §21.10): a datapack registry entry
 * ({@code data/<ns>/groundburst/terrain_table/<id>.json}, the server's only), named by a blast profile. Its bands' rules
 * ({@link TerrainRule}) say what each block becomes:
 * <ul>
 * <li>{@code shock}: in the structural band the shock blows light blocks out of its way (glass, leaves, plants, doors,
 * torches, snow layers) and carries on through the gap;</li>
 * <li>{@code scorch}: the ground the fireball sees in the scorch band (§11.4c);</li>
 * <li>{@code lining}: a nuclear crater's wall (slag where it was rock, trinitite where it was sand, irradiated soil where
 * it was earth);</li>
 * <li>{@code charred}, {@code broken}, {@code withered}: a nuclear blast's far rings (§21.10).</li>
 * </ul>
 * The mod's three tables (conventional, nuclear, naval) are written by datagen from {@link #builtIn}, block for block what
 * the code they replace did; a datapack may change them or add more. The server's swap lines ({@link TerrainRules}) are
 * asked first. Within a band the first rule that takes a block and whose chance comes up decides; a rule whose chance
 * fails passes the block to the next. A block state's rules are worked out once and kept.
 */
public final class TerrainTable {
    public static final ResourceKey<Registry<TerrainTable>> REGISTRY = ResourceKey.createRegistryKey(GroundBurst.id("terrain_table"));
    public static final ResourceLocation CONVENTIONAL = GroundBurst.id("conventional");
    public static final ResourceLocation NUCLEAR = GroundBurst.id("nuclear");
    public static final ResourceLocation NAVAL = GroundBurst.id("naval");

    /** The bands a table's rules are for. */
    public enum Band {
        SHOCK, SCORCH, LINING, CHARRED, BROKEN, WITHERED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable Band of(String key) {
            for (Band b : values()) {
                if (b.key().equalsIgnoreCase(key)) {
                    return b;
                }
            }
            return null;
        }
    }

    private record Data(boolean harsh, boolean naval, float ash, List<TerrainRule> shock, List<TerrainRule> scorch,
            List<TerrainRule> lining, List<TerrainRule> charred, List<TerrainRule> broken, List<TerrainRule> withered) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("harsh", false).forGetter(Data::harsh),
                Codec.BOOL.optionalFieldOf("naval", false).forGetter(Data::naval),
                Codec.FLOAT.optionalFieldOf("ash", 0f).forGetter(Data::ash),
                TerrainRule.CODEC.listOf().optionalFieldOf("shock", List.of()).forGetter(Data::shock),
                TerrainRule.CODEC.listOf().optionalFieldOf("scorch", List.of()).forGetter(Data::scorch),
                TerrainRule.CODEC.listOf().optionalFieldOf("lining", List.of()).forGetter(Data::lining),
                TerrainRule.CODEC.listOf().optionalFieldOf("charred", List.of()).forGetter(Data::charred),
                TerrainRule.CODEC.listOf().optionalFieldOf("broken", List.of()).forGetter(Data::broken),
                TerrainRule.CODEC.listOf().optionalFieldOf("withered", List.of()).forGetter(Data::withered)
        ).apply(i, Data::new));
    }

    public static final Codec<TerrainTable> CODEC = Data.CODEC.xmap(TerrainTable::new, TerrainTable::data);

    /** Nuclear tables: the fire band leaves ash, the crater is lined, the ground scorched harder. */
    private final boolean harsh;
    /** Charges made to go off in water: set off in water the sea is not blown away (§14.6). */
    private final boolean naval;
    /** The share of the fire band's flames that are a layer or two of ash instead. */
    private final float ash;
    private final EnumMap<Band, List<TerrainRule>> rules = new EnumMap<>(Band.class);
    /** Its id once it is looked up by one (the server's swap lines go by it). */
    private @Nullable ResourceLocation id;
    /** Each band's rules for each block state seen (the swap lines' first), and the swap lines' version they are from. */
    private final EnumMap<Band, IdentityHashMap<BlockState, TerrainRule[]>> seen = new EnumMap<>(Band.class);
    private int swapsVersion = -1;
    private static final TerrainRule[] NONE = new TerrainRule[0];
    /** For callers with no random source of their own (rules certain to apply are not rolled for). */
    private static final RandomSource RANDOM = RandomSource.create();

    private TerrainTable(Data d) {
        this(d.harsh, d.naval, d.ash, Map.of(Band.SHOCK, d.shock, Band.SCORCH, d.scorch, Band.LINING, d.lining, Band.CHARRED, d.charred,
                Band.BROKEN, d.broken, Band.WITHERED, d.withered));
    }

    public TerrainTable(boolean harsh, boolean naval, float ash, Map<Band, List<TerrainRule>> rules) {
        this.harsh = harsh;
        this.naval = naval;
        this.ash = ash;
        for (Band b : Band.values()) {
            this.rules.put(b, List.copyOf(rules.getOrDefault(b, List.of())));
            this.seen.put(b, new IdentityHashMap<>());
        }
    }

    private Data data() {
        return new Data(this.harsh, this.naval, this.ash, this.rules.get(Band.SHOCK), this.rules.get(Band.SCORCH),
                this.rules.get(Band.LINING), this.rules.get(Band.CHARRED), this.rules.get(Band.BROKEN), this.rules.get(Band.WITHERED));
    }

    /** The table by id: the server's data, or the mod's own if the server has none by that id (or there is no server). */
    public static TerrainTable byId(ResourceLocation id) {
        return TerrainTables.get(id);
    }

    void bind(ResourceLocation id) {
        this.id = id;
    }

    public @Nullable ResourceLocation id() {
        return this.id;
    }

    /** Whether, set off in water, the blast leaves the sea as it is and fills what it breaks under it with water. */
    public boolean naval() {
        return this.naval;
    }

    /** Whether the fire band leaves ash on the scorched ground, and the crater is lined ({@link #lining}). */
    public boolean nuclear() {
        return this.harsh;
    }

    /** The share of the fire band's flames that are ash instead. */
    public float ash() {
        return this.ash;
    }

    /** The band's own rules (not the swap lines), as written. */
    public List<TerrainRule> rules(Band band) {
        return this.rules.get(band);
    }

    /**
     * What {@code state} becomes in {@code band}, or null if it stays as it is: the first rule that takes it and whose
     * chance comes up, the chance times {@code weight} (the band's own blend where it is) and, for a fading rule, times
     * {@code 1 - t} ({@code t}: how far across the band, 0 at its inner edge to 1 at its outer).
     */
    public @Nullable BlockState convert(Band band, BlockState state, RandomSource random, float t, float weight) {
        for (TerrainRule rule : rulesFor(band, state)) {
            float chance = rule.chance() * weight * (rule.fade() ? Math.max(0, 1 - t) : 1);
            if (chance >= 1 || chance > 0 && random.nextFloat() < chance) {
                BlockState out = rule.result(state, random);
                return out == state ? null : out;          // (keep, or a block that would only become itself)
            }
        }
        return null;
    }

    /** Whether any rule of the band takes this block (whatever its chance). */
    public boolean takes(Band band, BlockState state) {
        for (TerrainRule rule : rulesFor(band, state)) {
            if (!rule.keeps()) {
                return true;
            }
            return false;                                  // kept as it is by the first rule that takes it
        }
        return false;
    }

    /** Whether the shock blows this block out of the way (the structural band; a pressure ray carries on through it). */
    public boolean blowsOut(BlockState state) {
        return takes(Band.SHOCK, state);
    }

    /** What the shock leaves of a block it blows out (air, or what a swap rule makes of it), or null if it stops there. */
    public @Nullable BlockState shocked(BlockState state, RandomSource random) {
        return convert(Band.SHOCK, state, random, 0, 1);
    }

    /** What an exposed surface becomes in the scorch band, or null if it stays as it is. */
    public @Nullable BlockState scorched(BlockState state) {
        return convert(Band.SCORCH, state, RANDOM, 0, 1);
    }

    /** As {@link #scorched(BlockState)}, its rules' chances rolled with {@code random}. */
    public @Nullable BlockState scorched(BlockState state, RandomSource random) {
        return convert(Band.SCORCH, state, random, 0, 1);
    }

    /**
     * What the nuclear crater's wall becomes where it stands next to the hole (null: as it was): rock melts to Slag,
     * sand fuses to Trinitite, earth takes up the fallout as Irradiated Soil.
     */
    public @Nullable BlockState lining(BlockState state) {
        return lining(state, RANDOM, 0);
    }

    /** As {@link #lining(BlockState)}, {@code t} how far out across the lining it stands (0 at the rim). */
    public @Nullable BlockState lining(BlockState state, RandomSource random, float t) {
        return this.harsh ? convert(Band.LINING, state, random, t, 1) : null;
    }

    private TerrainRule[] rulesFor(Band band, BlockState state) {
        int version = TerrainRules.version();
        if (version != this.swapsVersion) {
            for (IdentityHashMap<BlockState, TerrainRule[]> m : this.seen.values()) {
                m.clear();
            }
            this.swapsVersion = version;
        }
        IdentityHashMap<BlockState, TerrainRule[]> m = this.seen.get(band);
        TerrainRule[] found = m.get(state);
        if (found == null) {
            List<TerrainRule> list = new ArrayList<>();
            for (TerrainRule r : TerrainRules.rules(this.id, band)) {
                if (r.matches(state)) {
                    list.add(r);
                }
            }
            for (TerrainRule r : this.rules.get(band)) {
                if (r.matches(state)) {
                    list.add(r);
                }
            }
            found = list.isEmpty() ? NONE : list.toArray(new TerrainRule[0]);
            m.put(state, found);
        }
        return found;
    }

    // --- the rock, sand and earth the nuclear tables go by (the particle beam's pit too) -------------------------------------
    /** Rock: the overworld's stone (stone, granite, diorite, andesite, tuff, deepslate) and cobblestone. */
    public static boolean rock(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.COBBLESTONE);
    }

    /** Sand and sandstone (a desert's ground is sandstone a few blocks down, and flat worlds' is on top). */
    public static boolean sand(BlockState state) {
        return state.is(BlockTags.SAND) || state.is(Tags.Blocks.SANDSTONE);
    }

    /** Earth: dirt, grass blocks (grass, podzol, mycelium, and dead grass), moss, farmland. */
    public static boolean earth(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.DIRT) || state.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK) || state.is(Blocks.FARMLAND);
    }

    // --- the mod's own tables -----------------------------------------------------------------------------------------------------
    /**
     * The mod's tables (DESIGN.md §21.7, §21.10), as datagen writes them and as the server uses them when it has no table
     * by that id: conventional, nuclear and naval.
     */
    public static Map<ResourceLocation, TerrainTable> builtIn() {
        String glass = "#" + Tags.Blocks.GLASS.location(), panes = "#" + Tags.Blocks.GLASS_PANES.location();
        String cobble = "#" + Tags.Blocks.COBBLESTONE.location();
        String sandstone = "#" + Tags.Blocks.SANDSTONE.location();
        String[] earth = {"#minecraft:dirt", "#minecraft:grass_blocks", "#minecraft:moss_blocks", "minecraft:farmland"};
        String[] rock = {"#minecraft:base_stone_overworld", cobble};
        String[] sand = {"#minecraft:sand", sandstone};
        // The shock: glass, leaves, doors, trapdoors, flowers, snow layers, torches, lanterns, and anything a block may be
        // set into (plants), blown out of its way.
        List<TerrainRule> shock = List.of(TerrainRule.of("air", glass, panes, "#minecraft:leaves", "#minecraft:doors",
                "#minecraft:trapdoors", "#minecraft:flowers", "minecraft:snow", "minecraft:torch", "minecraft:wall_torch",
                "minecraft:lantern", "$replaceable"));
        // Conventional scorching: grass burnt to earth, snow gone, fields and paths to dirt.
        List<TerrainRule> mild = List.of(
                TerrainRule.of("minecraft:dirt", "minecraft:grass_block", "minecraft:mycelium", "minecraft:podzol"),
                TerrainRule.of("air", "minecraft:snow_block", "minecraft:powder_snow"),
                TerrainRule.of("minecraft:dirt", "minecraft:farmland", "minecraft:dirt_path"));
        // Nuclear scorching: earth to Scorched Earth, stone to Scorched Stone, sand glassed, wood charred (its axis kept);
        // grass to coarse earth, snow gone, ice melted.
        List<TerrainRule> harsh = new ArrayList<>();
        harsh.add(TerrainRule.of("groundburst:scorched_earth", cat(earth, "minecraft:dirt_path", "#minecraft:mud")));
        harsh.add(TerrainRule.of("groundburst:scorched_stone", rock));
        harsh.add(TerrainRule.of("groundburst:glassed_sand", sand));
        harsh.add(TerrainRule.of("groundburst:charred_wood", "#minecraft:logs&*:*_wood", "#minecraft:logs&*:*_hyphae"));
        harsh.add(TerrainRule.of("groundburst:charred_log", "#minecraft:logs"));
        harsh.add(TerrainRule.of("groundburst:charred_planks", "#minecraft:planks"));
        harsh.add(TerrainRule.of("minecraft:coarse_dirt", "minecraft:grass_block", "minecraft:mycelium", "minecraft:podzol"));
        harsh.add(TerrainRule.of("air", "minecraft:snow_block", "minecraft:powder_snow"));
        harsh.add(TerrainRule.of("minecraft:water", "minecraft:ice", "minecraft:packed_ice"));
        harsh.add(TerrainRule.of("minecraft:dirt", "minecraft:farmland", "minecraft:dirt_path"));
        List<TerrainRule> lining = List.of(
                TerrainRule.of("groundburst:slag", cat(rock, "groundburst:scorched_stone")),
                TerrainRule.of("groundburst:trinitite", cat(sand, "groundburst:glassed_sand")),
                TerrainRule.of("groundburst:irradiated_soil", cat(earth, "groundburst:scorched_earth")));
        // The far rings (§21.10). The far aftermath offers each its blocks (the ground, the plants on it, the leaves and
        // logs under the open sky; in the broken ring the glass with a face toward the blast, the doors facing it, the
        // lamps) and fades each ring out at its edge; these say what they become.
        String[] plants = {"#minecraft:flowers", "minecraft:grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
                "minecraft:bush", "minecraft:short_dry_grass", "minecraft:tall_dry_grass"};
        String[] lamps = {"minecraft:torch", "minecraft:wall_torch", "minecraft:lantern", "minecraft:soul_torch", "minecraft:soul_wall_torch",
                "minecraft:soul_lantern"};
        List<TerrainRule> charred = List.of(
                TerrainRule.of(0.9f, "groundburst:scorched_earth*2|minecraft:coarse_dirt*1|groundburst:dead_grass_block*2",
                        "#minecraft:grass_blocks&!groundburst:dead_grass_block", "minecraft:mycelium", "minecraft:podzol"),
                TerrainRule.of(0.9f, "air", plants),
                TerrainRule.of(0.6f, "minecraft:dead_bush", "#minecraft:saplings"),
                TerrainRule.of(0.85f, "groundburst:dead_leaves*3|air*1", "#minecraft:leaves&!groundburst:dead_leaves"),
                TerrainRule.of(0.5f, "groundburst:charred_wood", "#minecraft:logs&*:*_wood", "#minecraft:logs&*:*_hyphae"),
                TerrainRule.of(0.5f, "groundburst:charred_log", "#minecraft:logs&!groundburst:charred_log"),
                TerrainRule.of(0.7f, "groundburst:withered_crops", "#minecraft:crops"),
                TerrainRule.of("air", "minecraft:snow", "minecraft:snow_block", "minecraft:powder_snow"),
                TerrainRule.of("minecraft:water", "minecraft:ice"),
                TerrainRule.of(0.8f, "air", glass, panes),
                TerrainRule.of(0.8f, "air", lamps));
        List<TerrainRule> broken = List.of(
                TerrainRule.of(0.85f, "air", glass, panes),
                TerrainRule.of(0.5f, "~[open=true]", "#minecraft:doors", "#minecraft:trapdoors"),
                TerrainRule.of(0.6f, "air", lamps),
                TerrainRule.of(0.3f, "air", "#minecraft:leaves"),
                TerrainRule.of(0.3f, "groundburst:dead_grass_block", "#minecraft:grass_blocks&!groundburst:dead_grass_block"),
                TerrainRule.of(0.35f, "air", plants),
                TerrainRule.of(0.35f, "groundburst:withered_crops", "#minecraft:crops"),
                TerrainRule.of(0.5f, "air", "minecraft:snow"));
        List<TerrainRule> withered = List.of(
                TerrainRule.of(0.35f, "groundburst:dead_leaves", "#minecraft:leaves&!groundburst:dead_leaves"),
                TerrainRule.of(0.3f, "groundburst:dead_grass_block", "#minecraft:grass_blocks&!groundburst:dead_grass_block"),
                TerrainRule.of(0.4f, "air*4|minecraft:dead_bush*1", plants),
                TerrainRule.of(0.5f, "groundburst:withered_crops", "#minecraft:crops"));
        Map<ResourceLocation, TerrainTable> out = new LinkedHashMap<>();
        out.put(CONVENTIONAL, new TerrainTable(false, false, 0, Map.of(Band.SHOCK, shock, Band.SCORCH, mild)));
        out.put(NUCLEAR, new TerrainTable(true, false, BlastJob.ASH_SHARE, Map.of(Band.SHOCK, shock, Band.SCORCH, harsh, Band.LINING, lining,
                Band.CHARRED, charred, Band.BROKEN, broken, Band.WITHERED, withered)));
        out.put(NAVAL, new TerrainTable(false, true, 0, Map.of(Band.SHOCK, shock, Band.SCORCH, mild)));
        return out;
    }

    private static String[] cat(String[] a, String... b) {
        String[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
