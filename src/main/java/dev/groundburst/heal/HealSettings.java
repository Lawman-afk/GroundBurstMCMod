package dev.groundburst.heal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import dev.groundburst.config.GBServerConfig;
import dev.groundburst.config.GBServerConfig.HealOrder;
import dev.groundburst.config.GBServerConfig.HealPlayerBlocks;
import dev.groundburst.config.GBServerConfig.HealSchedule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The server's healing settings at a moment (DESIGN.md §21.9), read from {@code healing.*} at most once a second so a
 * change in the config screen takes hold at once. GameTests mark out areas of their own ({@link #testArea}) where
 * damage is remembered whatever the config says, and heal it themselves.
 */
public record HealSettings(boolean enabled, HealSchedule schedule, int waitTicks, int randomTicks, int blocksPerSecond,
        int longestTicks, HealOrder order, boolean effects, Set<Kind> kinds, boolean aftermath, boolean decorations,
        boolean biomes, boolean clearRadiation,
        List<Pattern> excludedWeapons, Set<Block> excludedBlocks, List<TagKey<Block>> excludedTags, Set<String> excludedDimensions,
        HealPlayerBlocks playerBlocks, int keepRadius, boolean drops, int maxBlocksPerTick, int msPerTick) {

    /**
     * What did the damage: a kind of this mod's weapons, or what set off one of the game's own explosions (or another
     * mod's that goes through the game's explosion code, {@link Explosions}). Each can be left out of healing.
     */
    public enum Kind {
        CONVENTIONAL, NUCLEAR, SPECIAL, CHEMICAL,
        /** The game's explosions, by what set them off. */
        CREEPER, TNT, END_CRYSTAL, FIREBALL, WITHER, BED,
        /** Any other explosion through the game's explosion code: another mod's. */
        OTHER_MOD
    }

    private static volatile @Nullable HealSettings cached;
    private static volatile long cachedAt;
    private static final List<TestArea> TEST_AREAS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private record TestArea(ServerLevel level, AABB box, HealSettings settings) {}

    /** The settings the config holds now. */
    public static HealSettings current() {
        HealSettings s = cached;
        long now = System.nanoTime();
        if (s == null || now - cachedAt > 1_000_000_000L) {
            try {
                s = read();
            } catch (IllegalStateException notLoaded) {
                return OFF;                                // the server's config is not loaded yet: nothing heals
            }
            cached = s;
            cachedAt = now;
        }
        return s;
    }

    /** Healing off (before the server's config has loaded). */
    private static final HealSettings OFF = new HealSettings(false, HealSchedule.AFTER_WAIT, 6000, 0, 20, 6000, HealOrder.BOTTOM_UP, false,
            Set.of(), false, false, false, false, List.of(), Set.of(), List.of(), Set.of(), HealPlayerBlocks.KEEP, 3, false, 2000, 5);

    /** The settings for damage at {@code at}: a GameTest's own where it has marked an area out, else the config's. */
    public static HealSettings at(ServerLevel level, Vec3 at) {
        if (TEST_AREAS.isEmpty()) {
            return current();                              // (outside the GameTests there are none)
        }
        for (TestArea a : TEST_AREAS) {
            if (a.level == level && a.box.contains(at)) {
                return a.settings;
            }
        }
        return current();
    }

    /** Whether any GameTest has marked an area out (never outside the GameTests). */
    static boolean testing() {
        return !TEST_AREAS.isEmpty();
    }

    /** Whether any GameTest area covers {@code at} (the automatic healing leaves those to their tests). */
    static boolean inTestArea(ServerLevel level, Vec3 at) {
        if (TEST_AREAS.isEmpty()) {
            return false;
        }
        for (TestArea a : TEST_AREAS) {
            if (a.level == level && a.box.contains(at)) {
                return true;
            }
        }
        return false;
    }

    /** GameTests: damage inside {@code box} is remembered with these settings, whatever the config says. */
    public static void testArea(ServerLevel level, AABB box, HealSettings settings) {
        TEST_AREAS.removeIf(a -> a.level == level && a.box.equals(box));
        TEST_AREAS.add(new TestArea(level, box, settings));
    }

    /** GameTests: the area is the config's again. */
    public static void endTestArea(ServerLevel level, AABB box) {
        TEST_AREAS.removeIf(a -> a.level == level && a.box.equals(box));
    }

    /** Settings for a GameTest: on, no wait, everything heals but radiation, nothing kept round players' blocks unless asked. */
    public static HealSettings forTests(HealPlayerBlocks players, int keepRadius) {
        // (Radiation left alone: another test's source may lie in the same chunk.)
        return new HealSettings(true, HealSchedule.AFTER_WAIT, 0, 0, 100_000, 0, HealOrder.BOTTOM_UP, false,
                Set.copyOf(EnumSet.allOf(Kind.class)), true, true, true, false, List.of(), Set.of(), List.of(), Set.of(), players,
                keepRadius, false, 100_000, 1_000);
    }

    /** A copy with another schedule (GameTests). */
    public HealSettings withSchedule(boolean on, HealSchedule schedule, int waitTicks, int randomTicks) {
        return new HealSettings(on, schedule, waitTicks, randomTicks, this.blocksPerSecond, this.longestTicks, this.order, this.effects,
                this.kinds, this.aftermath, this.decorations, this.biomes, this.clearRadiation, this.excludedWeapons, this.excludedBlocks,
                this.excludedTags, this.excludedDimensions, this.playerBlocks, this.keepRadius, this.drops, this.maxBlocksPerTick, this.msPerTick);
    }

    /** A copy that leaves these blocks and tags out, and these weapons (GameTests). */
    public HealSettings withExcluded(Set<Block> blocks, List<TagKey<Block>> tags, List<String> weapons) {
        return new HealSettings(this.enabled, this.schedule, this.waitTicks, this.randomTicks, this.blocksPerSecond, this.longestTicks,
                this.order, this.effects, this.kinds, this.aftermath, this.decorations, this.biomes, this.clearRadiation,
                weapons.stream().map(HealSettings::glob).toList(), Set.copyOf(blocks), List.copyOf(tags), this.excludedDimensions,
                this.playerBlocks, this.keepRadius, this.drops, this.maxBlocksPerTick, this.msPerTick);
    }

    /** A copy in which only these kinds heal (GameTests). */
    public HealSettings withKinds(Set<Kind> kinds) {
        return new HealSettings(this.enabled, this.schedule, this.waitTicks, this.randomTicks, this.blocksPerSecond, this.longestTicks,
                this.order, this.effects, Set.copyOf(kinds), this.aftermath, this.decorations, this.biomes, this.clearRadiation,
                this.excludedWeapons, this.excludedBlocks, this.excludedTags, this.excludedDimensions, this.playerBlocks, this.keepRadius,
                this.drops, this.maxBlocksPerTick, this.msPerTick);
    }

    private static HealSettings read() {
        List<Pattern> weapons = new ArrayList<>();
        for (String s : GBServerConfig.HEAL_EXCLUDED_WEAPONS.get()) {
            weapons.add(glob(s));
        }
        Set<Block> blocks = new HashSet<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        for (String s : GBServerConfig.HEAL_EXCLUDED_BLOCKS.get()) {
            if (s.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(s.substring(1));
                if (id != null) {
                    tags.add(TagKey.create(Registries.BLOCK, id));
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(s);
                if (id != null) {
                    BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add);
                }
            }
        }
        EnumSet<Kind> kinds = EnumSet.noneOf(Kind.class);
        kind(kinds, Kind.CONVENTIONAL, GBServerConfig.HEAL_CONVENTIONAL.get());
        kind(kinds, Kind.NUCLEAR, GBServerConfig.HEAL_NUCLEAR.get());
        kind(kinds, Kind.SPECIAL, GBServerConfig.HEAL_SPECIAL.get());
        kind(kinds, Kind.CHEMICAL, GBServerConfig.HEAL_CHEMICAL.get());
        kind(kinds, Kind.CREEPER, GBServerConfig.HEAL_CREEPERS.get());
        kind(kinds, Kind.TNT, GBServerConfig.HEAL_TNT.get());
        kind(kinds, Kind.END_CRYSTAL, GBServerConfig.HEAL_END_CRYSTALS.get());
        kind(kinds, Kind.FIREBALL, GBServerConfig.HEAL_FIREBALLS.get());
        kind(kinds, Kind.WITHER, GBServerConfig.HEAL_WITHER.get());
        kind(kinds, Kind.BED, GBServerConfig.HEAL_BEDS.get());
        kind(kinds, Kind.OTHER_MOD, GBServerConfig.HEAL_OTHER_MODS.get());
        return new HealSettings(GBServerConfig.HEAL_ENABLED.get(), GBServerConfig.HEAL_SCHEDULE.get(),
                GBServerConfig.HEAL_WAIT_SECONDS.get() * 20, GBServerConfig.HEAL_RANDOM_SECONDS.get() * 20,
                GBServerConfig.HEAL_BLOCKS_PER_SECOND.get(), GBServerConfig.HEAL_LONGEST_SECONDS.get() * 20,
                GBServerConfig.HEAL_ORDER.get(), GBServerConfig.HEAL_EFFECTS.get(),
                Set.copyOf(kinds), GBServerConfig.HEAL_AFTERMATH.get(), GBServerConfig.HEAL_DECORATIONS.get(),
                GBServerConfig.HEAL_BIOMES.get(), GBServerConfig.HEAL_CLEAR_RADIATION.get(), List.copyOf(weapons), Set.copyOf(blocks),
                List.copyOf(tags), Set.copyOf(GBServerConfig.HEAL_EXCLUDED_DIMENSIONS.get()), GBServerConfig.HEAL_PLAYER_BLOCKS.get(),
                GBServerConfig.HEAL_KEEP_RADIUS.get(), GBServerConfig.HEAL_DROPS.get(), GBServerConfig.HEAL_MAX_BLOCKS_PER_TICK.get(),
                GBServerConfig.HEAL_MS_PER_TICK.get());
    }

    private static void kind(Set<Kind> kinds, Kind kind, boolean on) {
        if (on) {
            kinds.add(kind);
        }
    }

    /** {@code groundburst:tsar*} as a pattern: a {@code *} matches anything, the rest only itself. */
    static Pattern glob(String s) {
        String[] parts = s.trim().split("\\*", -1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                b.append(".*");
            }
            if (!parts[i].isEmpty()) {
                b.append(Pattern.quote(parts[i]));
            }
        }
        return Pattern.compile(b.toString());
    }

    /**
     * Whether damage of this kind, by this weapon, in this level heals. (For the game's explosions and other mods', the
     * weapon is what set the explosion off: {@link Explosions#source}.)
     */
    public boolean heals(Kind kind, ResourceLocation weapon, ServerLevel level) {
        if (!this.enabled) {
            return false;
        }
        if (!this.kinds.contains(kind) || this.excludedDimensions.contains(level.dimension().location().toString())) {
            return false;
        }
        String id = weapon.toString();
        for (Pattern p : this.excludedWeapons) {
            if (p.matcher(id).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a block may be put back: not one of the mod's explosives (caught in a blast they go off: put back, they
     * would be had twice), nor one the server has left out.
     */
    public boolean keeps(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof dev.groundburst.content.ordnance.charge.ChargeBlock || this.excludedBlocks.contains(block)) {
            return false;
        }
        for (TagKey<Block> tag : this.excludedTags) {
            if (state.is(tag)) {
                return false;
            }
        }
        return true;
    }
}
