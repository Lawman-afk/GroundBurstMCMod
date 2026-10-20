package dev.groundzero.config;

import java.util.List;

import dev.groundzero.registry.GZLang;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server (per-world) settings: safety limits for the explosion engine and the aftermath systems.
 * Defaults are deliberately dramatic; server owners scale them down here.
 */
public final class GZServerConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.translation(GZLang.config("blast", "Blasts")).comment("Explosion engine limits.").push("blast");
    }

    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS = B
            .comment("Master switch. When false, blasts still hurt entities but never change terrain.")
            .translation(GZLang.config("blast.destroyBlocks", "Destroy blocks"))
            .define("destroyBlocks", true);

    public static final ModConfigSpec.DoubleValue RADIUS_MULTIPLIER = B
            .comment("Scales every blast zone radius of every weapon.")
            .translation(GZLang.config("blast.radiusMultiplier", "Radius multiplier"))
            .defineInRange("radiusMultiplier", 1.0, 0.1, 2.0);

    public static final ModConfigSpec.IntValue MAX_RADIUS = B
            .comment("Hard cap on any blast zone radius, after the multiplier.")
            .translation(GZLang.config("blast.maxRadius", "Maximum radius"))
            .defineInRange("maxRadius", 320, 8, 512);

    public static final ModConfigSpec.IntValue MS_BUDGET_PER_TICK = B
            .comment("Milliseconds per server tick that all running blasts may use together.",
                    "Large blasts spread over more ticks instead of stalling the server.")
            .translation(GZLang.config("blast.msBudgetPerTick", "Time budget per tick (ms)"))
            .defineInRange("msBudgetPerTick", 12, 1, 45);

    public static final ModConfigSpec.IntValue MAX_BLOCK_CHANGES_PER_TICK = B
            .comment("Maximum block changes per tick across all running blasts.")
            .translation(GZLang.config("blast.maxBlockChangesPerTick", "Block changes per tick"))
            .defineInRange("maxBlockChangesPerTick", 60_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue MAX_CONCURRENT_JOBS = B
            .comment("Blasts processed at the same time; further detonations wait in a queue.")
            .translation(GZLang.config("blast.maxConcurrentJobs", "Concurrent blasts"))
            .defineInRange("maxConcurrentJobs", 8, 1, 64);

    public static final ModConfigSpec.BooleanValue GENERATE_CHUNKS = B
            .comment("Whether blasts may generate chunks that have never been generated. Off keeps blasts at the world's edge cheap.")
            .translation(GZLang.config("blast.generateChunks", "Generate new chunks"))
            .define("generateChunks", false);

    public static final ModConfigSpec.BooleanValue PROTECT_SPAWN = B
            .comment("Protect the world spawn area (vanilla spawn-protection radius) from terrain damage.")
            .translation(GZLang.config("blast.protectSpawn", "Protect spawn"))
            .define("protectSpawn", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_DIMENSIONS = B
            .comment("Dimensions (e.g. \"minecraft:the_end\") where blasts never change terrain.")
            .translation(GZLang.config("blast.protectedDimensions", "Protected dimensions"))
            .defineListAllowEmpty("protectedDimensions", List.of(), () -> "minecraft:overworld", GZServerConfig::isIdentifier);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_WEAPONS = B
            .comment("Weapon ids (e.g. \"groundzero:tsar_bomb\") that fizzle harmlessly instead of detonating.")
            .translation(GZLang.config("blast.disabledWeapons", "Disabled weapons"))
            .defineListAllowEmpty("disabledWeapons", List.of(), () -> "groundzero:", GZServerConfig::isIdentifier);

    static {
        B.pop();
        B.translation(GZLang.config("radiation", "Radiation")).comment("Radiation, fallout and contamination.").push("radiation");
    }

    public static final ModConfigSpec.BooleanValue RADIATION_ENABLED = B
            .comment("Master switch for radiation fields, dose and sickness.")
            .translation(GZLang.config("radiation.enabled", "Radiation enabled"))
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue RADIATION_DECAY_MULTIPLIER = B
            .comment("Multiplies decay speed. 2.0 makes irradiated areas clear twice as fast.")
            .translation(GZLang.config("radiation.decayMultiplier", "Decay speed multiplier"))
            .defineInRange("decayMultiplier", 1.0, 0.1, 100.0);

    public static final ModConfigSpec.BooleanValue RADIATION_SPREAD = B
            .comment("Whether fallout plumes drift downwind and deposit radiation and ash.")
            .translation(GZLang.config("radiation.spreadEnabled", "Fallout spreads downwind"))
            .define("spreadEnabled", true);

    static {
        B.pop();
        B.translation(GZLang.config("terrain", "Terrain")).comment("Blast aftermath on terrain.").push("terrain");
    }

    public static final ModConfigSpec.BooleanValue CONVERT_BIOMES = B
            .comment("Paint the nuclear wasteland biome over the scorched area of nuclear blasts.")
            .translation(GZLang.config("terrain.convertBiomes", "Create wasteland biome"))
            .define("convertBiomes", true);

    static {
        B.pop();
        B.translation(GZLang.config("missiles", "Missiles")).comment("Missile flight.").push("missiles");
    }

    public static final ModConfigSpec.IntValue MISSILE_MAX_RANGE = B
            .comment("Longest allowed missile flight in blocks, whatever the propulsion.")
            .translation(GZLang.config("missiles.maxRange", "Maximum range"))
            .defineInRange("maxRange", 30_000, 64, 1_000_000);

    public static final ModConfigSpec.BooleanValue MISSILE_LOAD_CHUNKS = B
            .comment("Load chunks along low flight paths and at the target so launches work with nobody nearby.")
            .translation(GZLang.config("missiles.loadChunksAlongPath", "Load chunks along the path"))
            .define("loadChunksAlongPath", true);

    static {
        B.pop();
        B.translation(GZLang.config("emp", "EMP")).comment("Electromagnetic pulse effects.").push("emp");
    }

    public static final ModConfigSpec.BooleanValue EMP_AFFECTS_REDSTONE = B
            .comment("Inside an EMP field, vanilla redstone signals read as zero.")
            .translation(GZLang.config("emp.affectsVanillaRedstone", "Affects vanilla redstone"))
            .define("affectsVanillaRedstone", true);

    public static final ModConfigSpec.BooleanValue EMP_BURNS_CABLES = B
            .comment("High-voltage cables inside an EMP field can burn out.")
            .translation(GZLang.config("emp.cablesBurnOut", "HV cables burn out"))
            .define("cablesBurnOut", false);

    static {
        B.pop();
        B.translation(GZLang.config("reactor", "Reactors")).comment("Breeder reactor behaviour.").push("reactor");
    }

    public static final ModConfigSpec.BooleanValue REACTOR_MELTDOWNS = B
            .comment("When true (the default), an overheating reactor goes critical and melts down unless SCRAMmed in time.",
                    "When false, it shuts itself down (SCRAM) at 900 C instead.")
            .translation(GZLang.config("reactor.meltdowns", "Meltdowns"))
            .define("meltdowns", true);

    static {
        B.pop();
        B.translation(GZLang.config("singularity", "Singularities")).comment("Black hole behaviour.").push("singularity");
    }

    public static final ModConfigSpec.IntValue SINGULARITY_MAX_BLOCKS = B
            .comment("Most blocks a single singularity may tear out of the ground.")
            .translation(GZLang.config("singularity.maxBlocksRipped", "Maximum blocks ripped"))
            .defineInRange("maxBlocksRipped", 20_000, 0, 500_000);

    static {
        B.pop();
        B.translation(GZLang.config("superweapons", "Superweapons")).comment("Particle Cannon, Nuclear Missile Silo and SCUD Storm.").push("superweapons");
    }

    public static final ModConfigSpec.BooleanValue SUPERWEAPON_ANNOUNCE = B
            .comment("Warn every player in the dimension when a superweapon is built, ready, or fired.")
            .translation(GZLang.config("superweapons.announce", "Announcements"))
            .define("announce", true);

    public static final ModConfigSpec.IntValue SUPERWEAPON_MAX_RANGE = B
            .comment("Maximum targeting range of superweapons, in blocks.")
            .translation(GZLang.config("superweapons.maxRange", "Maximum range"))
            .defineInRange("maxRange", 4_096, 64, 1_000_000);

    public static final ModConfigSpec.DoubleValue SUPERWEAPON_CHARGE_MULTIPLIER = B
            .comment("Multiplies superweapon charge times. 0.5 charges twice as fast.")
            .translation(GZLang.config("superweapons.chargeTimeMultiplier", "Charge time multiplier"))
            .defineInRange("chargeTimeMultiplier", 1.0, 0.05, 20.0);

    static {
        B.pop();
        B.translation(GZLang.config("manual", "Field Manual")).comment("The in-game guide book.").push("manual");
    }

    public static final ModConfigSpec.BooleanValue GIVE_MANUAL = B
            .comment("Give each player a Field Manual the first time they join the world.")
            .translation(GZLang.config("manual.giveOnFirstJoin", "Give on first join"))
            .define("giveOnFirstJoin", true);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private GZServerConfig() {}

    private static boolean isIdentifier(Object value) {
        return value instanceof String s && Identifier.tryParse(s) != null;
    }
}
