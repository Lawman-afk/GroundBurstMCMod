package dev.groundburst.blast;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBServerConfig;
import dev.groundburst.heal.HealSettings;
import dev.groundburst.heal.Scars;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The server's settings by weapon (DESIGN.md §21.10), laid over a blast profile as it goes off: the multipliers by kind
 * ({@code blast.byKind}: every radius, and the crater's depth, for conventional, nuclear and special weapons) and then the
 * lines of {@code blast.weapons}, each a weapon (a profile's id, a pattern with {@code *}, or a special weapon's id) and
 * its settings:
 * <pre>
 *   groundburst:tsar_warhead depth=0.4 scorch=x1.5 far=x0.5
 *   groundburst:*_grenade radius=x2
 *   groundburst:singularity radius=x0.5
 * </pre>
 * A value is a number (blocks for a radius, a share for depth), or {@code x} and a number to multiply what the weapon
 * has; {@code radius} always multiplies; {@code terrain} takes a terrain table's id. Later lines win over earlier ones.
 * The lines are read once each time the config changes. Then the server's radius multiplier and cap apply
 * ({@link #asSetOff}).
 */
public final class BlastSettings {
    /** The names a line's settings go by (lower case), and what each sets. */
    static final List<String> NAMES = List.of("radius", "crater", "depth", "shock", "scorch", "fire", "lethal", "damage", "lethaldamage",
            "edgedamage", "knockback", "speed", "streaks", "lobes", "ejecta", "drops", "terrain", "far");

    /** One setting of a line: its name, and a value to set or (with {@code times}) to multiply by; or a table's id. */
    record Setting(String name, float value, boolean times, @Nullable ResourceLocation id) {}

    /** One line: the weapons it is for, and its settings in order. */
    record Line(Pattern weapon, List<Setting> settings) {}

    private static volatile List<Line> lines = List.of();
    private static volatile @Nullable List<? extends String> readFrom;

    private BlastSettings() {}

    /**
     * {@code profile} (the blast profile {@code id}) as the server sets it off: its kind's multipliers, the lines for it,
     * {@code scale} (a stack of charges) with the server's radius multiplier, and the server's cap.
     */
    public static BlastProfile asSetOff(ResourceLocation id, BlastProfile profile, float scale) {
        return adjust(id, profile).scaled(GBServerConfig.RADIUS_MULTIPLIER.get().floatValue() * scale, GBServerConfig.MAX_RADIUS.get());
    }

    /** GameTests: {@code profile} with only these lines laid over it (no kind multipliers, nothing of the config's). */
    public static BlastProfile adjustWith(ResourceLocation id, BlastProfile profile, List<String> texts) {
        Values v = new Values(profile);
        for (String text : texts) {
            Line line = parse(text);
            if (line != null && line.weapon().matcher(id.toString()).matches()) {
                for (Setting s : line.settings()) {
                    v.apply(s);
                }
            }
        }
        return v.profile(profile);
    }

    /** {@code profile} with its kind's multipliers and the lines for {@code id} laid over it (before the server's own scaling). */
    public static BlastProfile adjust(ResourceLocation id, BlastProfile profile) {
        HealSettings.Kind kind = Scars.kindOf(id, TerrainTable.NUCLEAR.equals(profile.terrain()));
        float radius = k(kind == HealSettings.Kind.NUCLEAR ? GBServerConfig.NUCLEAR_RADIUS
                : kind == HealSettings.Kind.SPECIAL ? GBServerConfig.SPECIAL_RADIUS : GBServerConfig.CONVENTIONAL_RADIUS);
        float depth = k(kind == HealSettings.Kind.NUCLEAR ? GBServerConfig.NUCLEAR_DEPTH : GBServerConfig.CONVENTIONAL_DEPTH);
        Values v = new Values(profile);
        v.scaleRadii(radius);
        v.craterDepth *= depth;
        for (Line line : lines()) {
            if (line.weapon().matcher(id.toString()).matches()) {
                for (Setting s : line.settings()) {
                    v.apply(s);
                }
            }
        }
        return v.profile(profile);
    }

    /**
     * A special weapon's reach (antimatter, singularity, storm, cryo: {@code groundburst:<name>}) as the server sets it:
     * {@code base} times the special kind's radius and its lines' {@code radius}.
     */
    public static float special(ResourceLocation id, float base) {
        float k = k(GBServerConfig.SPECIAL_RADIUS);
        for (Line line : lines()) {
            if (line.weapon().matcher(id.toString()).matches()) {
                for (Setting s : line.settings()) {
                    if (s.name().equals("radius")) {
                        k *= s.value();
                    }
                }
            }
        }
        return base * k;
    }

    /** How far {@code id}'s far aftermath reaches, as a share of its usual (DESIGN.md §21.10): its lines' {@code far}, 1 by default. */
    public static float far(ResourceLocation id) {
        float far = 1;
        for (Line line : lines()) {
            if (line.weapon().matcher(id.toString()).matches()) {
                for (Setting s : line.settings()) {
                    if (s.name().equals("far")) {
                        far = s.times() ? far * s.value() : s.value();
                    }
                }
            }
        }
        return Math.max(0, far);
    }

    /** A multiplier from the config, 1 before the config is loaded. */
    private static float k(net.minecraftforge.common.ForgeConfigSpec.DoubleValue value) {
        try {
            return value.get().floatValue();
        } catch (IllegalStateException notLoaded) {
            return 1;
        }
    }

    /** The lines of the config as it is now (read again when it has changed). */
    static List<Line> lines() {
        List<? extends String> now;
        try {
            now = GBServerConfig.WEAPON_SETTINGS.get();
        } catch (IllegalStateException notLoaded) {
            return List.of();                              // (the config is not loaded: the profiles as they are)
        }
        if (now != readFrom) {
            List<Line> read = new ArrayList<>();
            for (String text : now) {
                Line line = parse(text);
                if (line == null) {
                    GroundBurst.LOGGER.warn("Ground Burst: blast.weapons line not understood, left out: \"{}\" ({})", text, check(text));
                } else {
                    read.add(line);
                }
            }
            lines = List.copyOf(read);
            readFrom = now;
        }
        return lines;
    }

    /** Whether a config value is a line that reads (the config screen's check). */
    public static boolean isLine(Object o) {
        return ((Object) o) instanceof String s && check(s) == null;
    }

    /** What is wrong with a line, or null if it reads. */
    public static @Nullable String check(String text) {
        String[] tokens = tokens(text);
        if (tokens.length < 2) {
            return "a weapon and at least one setting, as \"groundburst:tsar_warhead depth=x1.5\"";
        }
        for (int i = 1; i < tokens.length; i++) {
            String t = tokens[i];
            int eq = t.indexOf('=');
            if (eq <= 0 || eq == t.length() - 1) {
                return "\"" + t + "\" is not a setting=value";
            }
            String name = t.substring(0, eq).toLowerCase(Locale.ROOT);
            if (!NAMES.contains(name)) {
                return "no setting called " + t.substring(0, eq) + " (the settings: " + String.join(", ", NAMES) + ")";
            }
            String value = t.substring(eq + 1);
            if (name.equals("terrain")) {
                if (ResourceLocation.tryParse(value.contains(":") ? value : GroundBurst.MODID + ":" + value) == null) {
                    return value + " is not a terrain table's id";
                }
            } else if (number(value) == null) {
                return value + " is not a number (or x and a number)";
            }
        }
        return null;
    }

    /** A line read, or null if it does not read. */
    static @Nullable Line parse(String text) {
        if (check(text) != null) {
            return null;
        }
        String[] tokens = tokens(text);
        String weapon = tokens[0].contains(":") || tokens[0].startsWith("*") ? tokens[0] : GroundBurst.MODID + ":" + tokens[0];
        List<Setting> settings = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            String t = tokens[i];
            int eq = t.indexOf('=');
            String name = t.substring(0, eq).toLowerCase(Locale.ROOT), value = t.substring(eq + 1);
            if (name.equals("terrain")) {
                settings.add(new Setting(name, 0, false, ResourceLocation.tryParse(value.contains(":") ? value : GroundBurst.MODID + ":" + value)));
            } else {
                float[] n = number(value);
                // A radius setting always multiplies (a radius in blocks would mean nothing across every zone).
                settings.add(new Setting(name, n[0], n[1] > 0 || name.equals("radius"), null));
            }
        }
        return new Line(HealSettings.glob(weapon.toLowerCase(Locale.ROOT)), List.copyOf(settings));
    }

    private static String[] tokens(String text) {
        String t = text == null ? "" : text.trim();
        return t.isEmpty() ? new String[0] : t.split("[\\s,;]+");
    }

    /** {@code {value, times ? 1 : 0}}, or null: "0.4", "x1.5", "*2", "150%" (a share). */
    private static float @Nullable [] number(String s) {
        boolean times = false;
        String v = s.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("x") || v.startsWith("*")) {
            times = true;
            v = v.substring(1);
        }
        float k = 1;
        if (v.endsWith("%")) {
            k = 0.01f;
            v = v.substring(0, v.length() - 1);
        }
        try {
            float f = Float.parseFloat(v) * k;
            return Float.isFinite(f) && f >= 0 ? new float[] {f, times ? 1 : 0} : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A profile's numbers being changed. */
    private static final class Values {
        float core, crater, structural, scorch, fire, lethal, damage, lethalDamage, edgeDamage, knockback, frontSpeed, craterDepth,
                lobes, streaks, ejecta, drops;
        ResourceLocation terrain;

        Values(BlastProfile p) {
            this.core = p.core();
            this.crater = p.crater();
            this.structural = p.structural();
            this.scorch = p.scorch();
            this.fire = p.fire();
            this.lethal = p.lethal();
            this.damage = p.damage();
            this.lethalDamage = p.lethalDamage();
            this.edgeDamage = p.edgeDamage();
            this.knockback = p.knockback();
            this.frontSpeed = p.frontSpeed();
            this.craterDepth = p.craterDepth();
            this.lobes = p.lobes();
            this.streaks = p.streaks();
            this.ejecta = p.ejecta();
            this.drops = p.drops();
            this.terrain = p.terrain();
        }

        void scaleRadii(float k) {
            this.core *= k;
            this.crater *= k;
            this.structural *= k;
            this.scorch *= k;
            this.fire *= k;
            this.lethal *= k;
            this.damage *= k;
        }

        void apply(Setting s) {
            switch (s.name()) {
                case "radius" -> scaleRadii(s.value());
                case "crater" -> {
                    // The core keeps its share of the crater.
                    float before = this.crater;
                    this.crater = set(this.crater, s);
                    this.core = before > 0 ? this.core * this.crater / before : this.core;
                }
                case "depth" -> this.craterDepth = set(this.craterDepth, s);
                case "shock" -> this.structural = set(this.structural, s);
                case "scorch" -> this.scorch = set(this.scorch, s);
                case "fire" -> this.fire = set(this.fire, s);
                case "lethal" -> this.lethal = set(this.lethal, s);
                case "damage" -> this.damage = set(this.damage, s);
                case "lethaldamage" -> this.lethalDamage = set(this.lethalDamage, s);
                case "edgedamage" -> this.edgeDamage = set(this.edgeDamage, s);
                case "knockback" -> this.knockback = set(this.knockback, s);
                case "speed" -> this.frontSpeed = Math.max(0.1f, set(this.frontSpeed, s));
                case "streaks" -> this.streaks = set(this.streaks, s);
                case "lobes" -> this.lobes = set(this.lobes, s);
                case "ejecta" -> this.ejecta = set(this.ejecta, s);
                case "drops" -> this.drops = Math.min(1, set(this.drops, s));
                case "terrain" -> this.terrain = s.id() == null ? this.terrain : s.id();
                default -> {
                    // "far" is the far aftermath's (see far()); nothing of the profile's
                }
            }
        }

        private static float set(float was, Setting s) {
            return s.times() ? was * s.value() : s.value();
        }

        BlastProfile profile(BlastProfile p) {
            return new BlastProfile(this.core, this.crater, this.structural, this.scorch, this.fire,
                    new BlastProfile.EntityZones(this.lethal, this.damage, this.lethalDamage, this.edgeDamage, this.knockback),
                    this.frontSpeed, this.craterDepth, this.lobes, this.streaks, this.ejecta, this.drops, this.terrain, p.instant(),
                    p.sound(), p.visual());
        }
    }
}
