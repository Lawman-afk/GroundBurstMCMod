package dev.groundburst.blast;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBServerConfig;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The server's swap lines ({@code terrain.swaps}; DESIGN.md §21.10), asked before a terrain table's own rules:
 * <pre>
 *   &lt;tables&gt; &lt;bands&gt; &lt;blocks&gt; -&gt; &lt;block&gt; [chance] [fade]
 *   nuclear lining #minecraft:sand -> mymod:rare_ore 5%
 *   nuclear scorch,charred #minecraft:logs, #minecraft:planks -> mymod:burnt_wood
 *   * broken minecraft:glass -> mymod:cracked_glass 60% fade
 * </pre>
 * Tables by id ({@code nuclear} is {@code groundburst:nuclear}) or {@code *}; bands by name ({@link TerrainTable.Band}) or
 * {@code *}; the blocks and what they become as a {@link TerrainRule} writes them. Read again whenever the config loads.
 */
public final class TerrainRules {
    /** One line: the tables and bands it is for (empty: all), and its rule. */
    record Line(Set<ResourceLocation> tables, Set<TerrainTable.Band> bands, TerrainRule rule) {}

    private static volatile List<Line> lines = List.of();
    /** Bumped each time the lines are read again (the tables let go of what they worked out from the old ones). */
    private static volatile int version;

    private TerrainRules() {}

    /** The config was loaded or changed: the lines are read again. */
    public static void reload() {
        List<Line> read = new ArrayList<>();
        try {
            for (String text : GBServerConfig.TERRAIN_SWAPS.get()) {
                Line line = parse(text);
                if (line == null) {
                    GroundBurst.LOGGER.warn("Ground Burst: terrain.swaps line not understood, left out: \"{}\" ({})", text, check(text));
                } else {
                    read.add(line);
                }
            }
        } catch (IllegalStateException notLoaded) {
            // no config yet: no lines
        }
        lines = List.copyOf(read);
        version++;
    }

    static int version() {
        return version;
    }

    /** The lines' rules for one table's band, in order. */
    static List<TerrainRule> rules(@Nullable ResourceLocation table, TerrainTable.Band band) {
        List<Line> all = lines;
        if (all.isEmpty()) {
            return List.of();
        }
        List<TerrainRule> out = new ArrayList<>();
        for (Line l : all) {
            if ((l.tables().isEmpty() || table != null && l.tables().contains(table)) && (l.bands().isEmpty() || l.bands().contains(band))) {
                out.add(l.rule());
            }
        }
        return out;
    }

    /** Whether a config value is a line that reads (the config screen's check). */
    public static boolean isLine(Object o) {
        return o instanceof String s && check(s) == null;
    }

    /** What is wrong with a line, or null if it reads. */
    public static @Nullable String check(String text) {
        Parts p = split(text);
        if (p == null) {
            return "tables, bands and blocks, then -> and what they become, as \"nuclear lining #minecraft:sand -> mymod:rare_ore 5%\"";
        }
        if (!p.tables.equals("*")) {
            for (String t : p.tables.split(",")) {
                if (ResourceLocation.tryParse(t.contains(":") ? t : GroundBurst.MODID + ":" + t) == null) {
                    return t + " is not a table's id";
                }
            }
        }
        if (!p.bands.equals("*")) {
            for (String b : p.bands.split(",")) {
                if (TerrainTable.Band.of(b) == null) {
                    return "no band called " + b + " (the bands: shock, scorch, lining, charred, broken, withered)";
                }
            }
        }
        for (String f : p.from) {
            String wrong = TerrainRule.checkFrom(f);
            if (wrong != null) {
                return wrong;
            }
        }
        String wrong = TerrainRule.checkTo(p.to);
        if (wrong != null) {
            return wrong;
        }
        if (p.chance < 0) {
            return "the chance is a number from 0 to 1, or a percentage";
        }
        return null;
    }

    /** A line read, or null. */
    static @Nullable Line parse(String text) {
        if (check(text) != null) {
            return null;
        }
        Parts p = split(text);
        Set<ResourceLocation> tables = new HashSet<>();
        if (!p.tables.equals("*")) {
            for (String t : p.tables.split(",")) {
                tables.add(ResourceLocation.tryParse(t.contains(":") ? t : GroundBurst.MODID + ":" + t));
            }
        }
        Set<TerrainTable.Band> bands = EnumSet.noneOf(TerrainTable.Band.class);
        if (!p.bands.equals("*")) {
            for (String b : p.bands.split(",")) {
                bands.add(TerrainTable.Band.of(b));
            }
        }
        return new Line(Set.copyOf(tables), bands.isEmpty() ? Set.of() : Set.copyOf(bands), new TerrainRule(p.from, p.to, p.chance, p.fade));
    }

    private record Parts(String tables, String bands, List<String> from, String to, float chance, boolean fade) {}

    private static @Nullable Parts split(String text) {
        if (text == null) {
            return null;
        }
        int arrow = text.indexOf("->");
        if (arrow < 0) {
            arrow = text.indexOf('>');                     // "a > b" as the request wrote it
            if (arrow < 0) {
                return null;
            }
        }
        String left = text.substring(0, arrow).trim(), right = text.substring(arrow + (text.startsWith("->", arrow) ? 2 : 1)).trim();
        String[] l = left.split("\\s+", 3);
        String[] r = right.isEmpty() ? new String[0] : right.split("\\s+");
        if (l.length < 3 || r.length < 1) {
            return null;
        }
        List<String> from = new ArrayList<>();
        for (String f : l[2].split(",")) {
            if (!f.isBlank()) {
                from.add(f.trim());
            }
        }
        if (from.isEmpty()) {
            return null;
        }
        float chance = 1;
        boolean fade = false;
        for (int i = 1; i < r.length; i++) {
            String t = r[i].toLowerCase(Locale.ROOT);
            if (t.equals("fade")) {
                fade = true;
            } else {
                try {
                    chance = t.endsWith("%") ? Float.parseFloat(t.substring(0, t.length() - 1)) / 100f : Float.parseFloat(t);
                    if (!(chance >= 0 && chance <= 1)) {
                        chance = -1;
                    }
                } catch (NumberFormatException e) {
                    chance = -1;
                }
            }
        }
        return new Parts(l[0].toLowerCase(Locale.ROOT), l[1].toLowerCase(Locale.ROOT), from, r[0], chance, fade);
    }
}
