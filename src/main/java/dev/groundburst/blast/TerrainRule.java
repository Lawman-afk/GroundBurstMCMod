package dev.groundburst.blast;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.groundburst.GroundBurst;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * One swap rule of a terrain table (DESIGN.md §21.10): the blocks it takes ({@code from}), what they become ({@code to}),
 * the chance it does so and whether that chance fades across its band. Written as data:
 * <pre>
 *   {"from": ["#minecraft:sand", "#c:sandstone/blocks"], "to": "groundburst:trinitite"}
 *   {"from": ["#minecraft:logs&*:*_wood"], "to": "groundburst:charred_wood"}
 *   {"from": ["#minecraft:grass_blocks"], "to": "groundburst:scorched_earth*1|minecraft:coarse_dirt*1", "chance": 0.9}
 *   {"from": ["#minecraft:doors"], "to": "~[open=true]", "chance": 0.5}
 * </pre>
 * {@code from}: block ids, {@code #tags}, id patterns with {@code *}, {@code $replaceable} (anything a block may be set
 * into: plants, snow layers), {@code *} (anything), {@code a&b} for blocks both take and {@code !a} for blocks it does
 * not. {@code to}: a block state as
 * {@code id[property=value]}, {@code air}, {@code keep} (left as it is: the rules after it are not asked), {@code ~[...]}
 * (the same block with those properties), or a weighted choice {@code a*3|b*1}. The new block takes the properties it
 * shares with the old (a log's axis, a stair's facing, waterlogging), unless the rule names them. What a rule names is
 * worked out the first time it is used; a block or tag that does not exist (another mod's, not installed) never matches,
 * and a target that does not read leaves the rule out, with a warning in the log.
 */
public final class TerrainRule {
    public static final Codec<TerrainRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("from").forGetter(r -> r.from),
            Codec.STRING.fieldOf("to").forGetter(r -> r.to),
            Codec.FLOAT.optionalFieldOf("chance", 1f).forGetter(r -> r.chance),
            Codec.BOOL.optionalFieldOf("fade", false).forGetter(r -> r.fade)
    ).apply(i, TerrainRule::new));

    final List<String> from;
    final String to;
    final float chance;
    final boolean fade;

    /** Worked out on first use. */
    private @Nullable Predicate<BlockState> matcher;
    private @Nullable List<Choice> choices;
    private boolean keep, broken;

    /** One of the blocks a rule may set: a state (null: the block it takes), the properties it names, its weight. */
    private record Choice(@Nullable BlockState state, Set<String> named, List<String[]> sameWith, int weight) {}

    public TerrainRule(List<String> from, String to, float chance, boolean fade) {
        this.from = List.copyOf(from);
        this.to = to;
        this.chance = chance;
        this.fade = fade;
    }

    /** A rule that always applies. */
    public static TerrainRule of(String to, String... from) {
        return new TerrainRule(List.of(from), to, 1, false);
    }

    /** A rule that applies with {@code chance}. */
    public static TerrainRule of(float chance, String to, String... from) {
        return new TerrainRule(List.of(from), to, chance, false);
    }

    public float chance() {
        return this.chance;
    }

    public boolean fade() {
        return this.fade;
    }

    /** Whether the rule takes this block. */
    public boolean matches(BlockState state) {
        if (this.matcher == null) {
            this.matcher = compile(this.from);
        }
        return this.matcher.test(state);
    }

    /** Whether the rule leaves the blocks it takes as they are ({@code keep}). */
    public boolean keeps() {
        targets();
        return this.keep;
    }

    /** What {@code state} becomes by this rule (null for keep, or a rule whose target does not read). */
    public @Nullable BlockState result(BlockState state, RandomSource random) {
        List<Choice> c = targets();
        if (this.keep || this.broken || c.isEmpty()) {
            return null;
        }
        Choice pick = c.get(0);
        if (c.size() > 1) {
            int total = 0;
            for (Choice k : c) {
                total += k.weight;
            }
            int roll = random.nextInt(Math.max(1, total));
            for (Choice k : c) {
                roll -= k.weight;
                if (roll < 0) {
                    pick = k;
                    break;
                }
            }
        }
        BlockState out = pick.state == null ? state : pick.state;
        if (pick.state == null) {
            for (String[] pv : pick.sameWith) {
                out = with(out, pv[0], pv[1]);
            }
            return out;
        }
        return carry(state, out, pick.named);
    }

    // --- working out what it names ----------------------------------------------------------------------------------------------
    private List<Choice> targets() {
        if (this.choices == null) {
            List<Choice> list = new ArrayList<>();
            String t = this.to.trim();
            if (t.equalsIgnoreCase("keep")) {
                this.keep = true;
            } else {
                for (String part : t.split("\\|")) {
                    Choice c = choice(part.trim());
                    if (c == null) {
                        GroundBurst.LOGGER.warn("Ground Burst: a terrain rule's \"{}\" does not read as a block; the rule is left out", part);
                        this.broken = true;
                        break;
                    }
                    list.add(c);
                }
            }
            this.choices = List.copyOf(list);
        }
        return this.choices;
    }

    private static @Nullable Choice choice(String text) {
        int weight = 1;
        int star = text.lastIndexOf('*');
        if (star > 0 && star > text.lastIndexOf(']') && text.substring(star + 1).chars().allMatch(Character::isDigit) && star < text.length() - 1) {
            weight = Integer.parseInt(text.substring(star + 1));
            text = text.substring(0, star).trim();
        }
        if (text.equalsIgnoreCase("air")) {
            return new Choice(Blocks.AIR.defaultBlockState(), Set.of(), List.of(), weight);
        }
        if (text.startsWith("~")) {
            // The same block, with these properties.
            List<String[]> props = new ArrayList<>();
            String inside = text.substring(1).trim();
            if (inside.startsWith("[") && inside.endsWith("]")) {
                for (String pv : inside.substring(1, inside.length() - 1).split(",")) {
                    String[] kv = pv.split("=");
                    if (kv.length == 2) {
                        props.add(new String[] {kv[0].trim(), kv[1].trim()});
                    }
                }
            }
            return new Choice(null, Set.of(), List.copyOf(props), weight);
        }
        try {
            var result = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false);
            Set<String> named = new java.util.HashSet<>();
            for (Property<?> p : result.properties().keySet()) {
                named.add(p.getName());
            }
            return new Choice(result.blockState(), Set.copyOf(named), List.of(), weight);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return null;
        }
    }

    /** {@code out} with the properties it shares with {@code in} set as {@code in} has them (except the ones the rule names). */
    static BlockState carry(BlockState in, BlockState out, Set<String> named) {
        if (in.getBlock() == out.getBlock() && named.isEmpty()) {
            return in;
        }
        for (Property<?> p : out.getProperties()) {
            if (named.contains(p.getName())) {
                continue;
            }
            for (Property<?> q : in.getProperties()) {
                if (q.getName().equals(p.getName())) {
                    out = copy(in, q, out, p);
                    break;
                }
            }
        }
        return out;
    }

    private static <A extends Comparable<A>, B extends Comparable<B>> BlockState copy(BlockState in, Property<A> q, BlockState out, Property<B> p) {
        String value = q.getName(in.getValue(q));
        return p.getValue(value).map(v -> out.setValue(p, v)).orElse(out);
    }

    private static BlockState with(BlockState state, String name, String value) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(name)) {
                return set(state, p, value);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> p, String value) {
        return p.getValue(value).map(v -> state.setValue(p, v)).orElse(state);
    }

    /** A matcher for a rule's {@code from} list: any of its entries. */
    static Predicate<BlockState> compile(List<String> entries) {
        List<Predicate<BlockState>> any = new ArrayList<>();
        for (String entry : entries) {
            List<Predicate<BlockState>> all = new ArrayList<>();
            for (String part : entry.split("&")) {
                all.add(one(part.trim()));
            }
            any.add(state -> {
                for (Predicate<BlockState> p : all) {
                    if (!p.test(state)) {
                        return false;
                    }
                }
                return true;
            });
        }
        return state -> {
            for (Predicate<BlockState> p : any) {
                if (p.test(state)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static Predicate<BlockState> one(String part) {
        if (part.startsWith("!")) {
            Predicate<BlockState> not = one(part.substring(1).trim());
            return state -> !not.test(state);
        }
        if (part.equals("*")) {
            return state -> !state.isAir();
        }
        if (part.equalsIgnoreCase("$replaceable")) {
            return state -> state.canBeReplaced() && state.getFluidState().isEmpty() && !state.isAir();
        }
        if (part.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(part.substring(1));
            if (id == null) {
                return state -> false;
            }
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
            return state -> state.is(tag);
        }
        if (part.contains("*")) {
            String glob = part.contains(":") ? part : "*:" + part;
            Pattern pattern = dev.groundburst.heal.HealSettings.glob(glob.toLowerCase(Locale.ROOT));
            return state -> pattern.matcher(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()).matches();
        }
        ResourceLocation id = ResourceLocation.tryParse(part.contains(":") ? part : "minecraft:" + part);
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            return state -> false;                         // another mod's block, not installed: nothing to take
        }
        return state -> state.is(block);
    }

    /** What is wrong with a block list entry (for the config's check), or null. */
    static @Nullable String checkFrom(String part) {
        for (String p : part.split("&")) {
            String s = p.trim();
            if (s.startsWith("!")) {
                s = s.substring(1).trim();
            }
            if (s.isEmpty()) {
                return "an empty block";
            }
            if (s.equals("*") || s.equalsIgnoreCase("$replaceable") || s.contains("*")) {
                continue;
            }
            if (ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s) == null) {
                return s + " is not a block id or #tag";
            }
        }
        return null;
    }

    /** What is wrong with a target (for the config's check), or null. */
    static @Nullable String checkTo(String to) {
        String t = to.trim();
        if (t.equalsIgnoreCase("keep")) {
            return null;
        }
        for (String part : t.split("\\|")) {
            String p = part.trim();
            int star = p.lastIndexOf('*');
            if (star > 0 && star > p.lastIndexOf(']')) {
                p = p.substring(0, star).trim();
            }
            if (p.equalsIgnoreCase("air") || p.startsWith("~")) {
                continue;
            }
            String id = p.contains("[") ? p.substring(0, p.indexOf('[')) : p;
            if (ResourceLocation.tryParse(id) == null) {
                return p + " is not a block";
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.join(",", this.from) + " -> " + this.to + (this.chance < 1 ? " " + this.chance : "") + (this.fade ? " fade" : "");
    }
}
