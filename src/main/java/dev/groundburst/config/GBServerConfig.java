package dev.groundburst.config;

import java.util.List;

import dev.groundburst.registry.GBLang;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server (per-world) settings: safety limits for the explosion engine and the aftermath systems.
 * Defaults are deliberately dramatic; server owners scale them down here.
 */
public final class GBServerConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    static {
        B.translation(GBLang.config("blast", "Blasts")).comment("Explosion engine limits.").push("blast");
    }

    public static final ForgeConfigSpec.BooleanValue DESTROY_BLOCKS = B
            .comment("Master switch. When false, blasts still hurt entities but never change terrain.")
            .translation(GBLang.config("blast.destroyBlocks", "Destroy blocks"))
            .define("destroyBlocks", true);

    public static final ForgeConfigSpec.DoubleValue RADIUS_MULTIPLIER = B
            .comment("Scales every blast zone radius of every weapon.")
            .translation(GBLang.config("blast.radiusMultiplier", "Radius multiplier"))
            .defineInRange("radiusMultiplier", 1.0, 0.1, 2.0);

    public static final ForgeConfigSpec.IntValue MAX_RADIUS = B
            .comment("Hard cap on any blast zone radius, after the multiplier. The default lets the Tsar reach all the way.")
            // (Was maxRadius, 320: renamed with the new default so configs written before the Tsar pick it up.)
            .translation(GBLang.config("blast.maxBlastRadius", "Maximum radius"))
            .defineInRange("maxBlastRadius", 720, 8, 1024);

    public static final ForgeConfigSpec.IntValue MS_BUDGET_PER_TICK = B
            .comment("Milliseconds per server tick that all running blasts may use together.",
                    "Large blasts spread over more ticks instead of stalling the server.")
            .translation(GBLang.config("blast.msBudgetPerTick", "Time budget per tick (ms)"))
            .defineInRange("msBudgetPerTick", 12, 1, 45);

    public static final ForgeConfigSpec.IntValue MAX_BLOCK_CHANGES_PER_TICK = B
            .comment("Maximum block changes per tick across all running blasts.")
            .translation(GBLang.config("blast.maxBlockChangesPerTick", "Block changes per tick"))
            .defineInRange("maxBlockChangesPerTick", 60_000, 1_000, 1_000_000);

    public static final ForgeConfigSpec.IntValue MAX_CONCURRENT_JOBS = B
            .comment("Blasts processed at the same time; further detonations wait in a queue.")
            .translation(GBLang.config("blast.maxConcurrentJobs", "Concurrent blasts"))
            .defineInRange("maxConcurrentJobs", 8, 1, 64);

    public static final ForgeConfigSpec.BooleanValue GENERATE_CHUNKS = B
            .comment("Whether blasts may generate chunks that have never been generated. Off keeps blasts at the world's edge cheap.")
            .translation(GBLang.config("blast.generateChunks", "Generate new chunks"))
            .define("generateChunks", false);

    public static final ForgeConfigSpec.BooleanValue PROTECT_SPAWN = B
            .comment("Protect the world spawn area (vanilla spawn-protection radius) from terrain damage.")
            .translation(GBLang.config("blast.protectSpawn", "Protect spawn"))
            .define("protectSpawn", false);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROTECTED_DIMENSIONS = B
            .comment("Dimensions (e.g. \"minecraft:the_end\") where blasts never change terrain.")
            .translation(GBLang.config("blast.protectedDimensions", "Protected dimensions"))
            .defineListAllowEmpty("protectedDimensions", List.of(), GBServerConfig::isIdentifier);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_WEAPONS = B
            .comment("Weapon ids (e.g. \"groundburst:tsar_bomb\") that fizzle harmlessly instead of detonating.")
            .translation(GBLang.config("blast.disabledWeapons", "Disabled weapons"))
            .defineListAllowEmpty("disabledWeapons", List.of(), GBServerConfig::isIdentifier);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WEAPON_SETTINGS = B
            .comment("Settings for single weapons (DESIGN.md §21.10), one line each: the weapon, then its settings, as",
                    "  \"groundburst:tsar_warhead depth=x1.5 scorch=600\"   (the Tsar's crater half as deep again, its scorch 600 blocks)",
                    "  \"groundburst:*_grenade radius=x2\"                  (every grenade's blast twice the size)",
                    "  \"groundburst:singularity radius=x0.5\"              (the singularity's reach halved)",
                    "The weapon is a blast profile's id as /groundburst blast lists them (a * matches anything), or a special",
                    "weapon's: groundburst:antimatter, groundburst:singularity, groundburst:storm, groundburst:cryo_freeze.",
                    "A value is a number (blocks for a radius, a share for depth), or x and a number to multiply the weapon's own.",
                    "Settings: radius (every radius together; it always multiplies), crater, depth (how deep the crater digs, about",
                    "0.3-0.7), shock (where glass and leaves are blown out), scorch, fire, lethal, damage (the zones that hurt),",
                    "lethalDamage, edgeDamage, knockback, speed (of the front, blocks a tick), streaks, lobes (the crater's shape),",
                    "ejecta (rubble thrown out), drops (the share of destroyed blocks that drop), terrain (a terrain table's id: what",
                    "the blast turns blocks into), far (a nuclear blast's far aftermath, x0.5 half as far, 0 none).",
                    "Later lines win. /groundburst blast info <profile> shows a weapon as it will go off.")
            .translation(GBLang.config("blast.weapons", "Settings for single weapons"))
            .defineListAllowEmpty("weapons", List.of(), dev.groundburst.blast.BlastSettings::isLine);

    static {
        B.translation(GBLang.config("blast.byKind", "By kind of weapon"))
                .comment("Scales every weapon of one kind, on top of the radius multiplier (DESIGN.md §21.10).").push("byKind");
    }

    public static final ForgeConfigSpec.DoubleValue CONVENTIONAL_RADIUS = B
            .comment("Every radius of conventional weapons' blasts: grenades, charges, mines, bombs, shells, and every warhead",
                    "that is not nuclear or special (chemical weapons' bursts too).")
            .translation(GBLang.config("blast.byKind.conventionalRadius", "Conventional weapons' radius"))
            .defineInRange("conventionalRadius", 1.0, 0.1, 4.0);
    public static final ForgeConfigSpec.DoubleValue CONVENTIONAL_DEPTH = B
            .comment("How deep conventional weapons' craters dig, as a share of their usual depth.")
            .translation(GBLang.config("blast.byKind.conventionalCraterDepth", "Conventional craters' depth"))
            .defineInRange("conventionalCraterDepth", 1.0, 0.1, 4.0);
    public static final ForgeConfigSpec.DoubleValue NUCLEAR_RADIUS = B
            .comment("Every radius of nuclear blasts (and so their far aftermath, which goes by their reach).")
            .translation(GBLang.config("blast.byKind.nuclearRadius", "Nuclear weapons' radius"))
            .defineInRange("nuclearRadius", 1.0, 0.1, 4.0);
    public static final ForgeConfigSpec.DoubleValue NUCLEAR_DEPTH = B
            .comment("How deep nuclear craters dig, as a share of their usual depth.")
            .translation(GBLang.config("blast.byKind.nuclearCraterDepth", "Nuclear craters' depth"))
            .defineInRange("nuclearCraterDepth", 1.0, 0.1, 4.0);
    public static final ForgeConfigSpec.DoubleValue SPECIAL_RADIUS = B
            .comment("How far the special weapons reach: antimatter, the singularity, the storm and the cryo freeze.")
            .translation(GBLang.config("blast.byKind.specialRadius", "Special weapons' reach"))
            .defineInRange("specialRadius", 1.0, 0.1, 4.0);

    static {
        B.pop();
        B.pop();
        B.translation(GBLang.config("radiation", "Radiation")).comment("Radiation, fallout and contamination.").push("radiation");
    }

    public static final ForgeConfigSpec.BooleanValue RADIATION_ENABLED = B
            .comment("Master switch for radiation fields, dose and sickness.")
            .translation(GBLang.config("radiation.enabled", "Radiation enabled"))
            .define("enabled", true);

    public static final ForgeConfigSpec.DoubleValue RADIATION_DECAY_MULTIPLIER = B
            .comment("Multiplies decay speed. 2.0 makes irradiated areas clear twice as fast.")
            .translation(GBLang.config("radiation.decayMultiplier", "Decay speed multiplier"))
            .defineInRange("decayMultiplier", 1.0, 0.1, 100.0);

    public static final ForgeConfigSpec.BooleanValue RADIATION_SPREAD = B
            .comment("Whether fallout plumes drift downwind and deposit radiation and ash.")
            .translation(GBLang.config("radiation.spreadEnabled", "Fallout spreads downwind"))
            .define("spreadEnabled", true);

    public static final ForgeConfigSpec.BooleanValue BLACK_RAIN = B
            .comment("Black rain (DESIGN.md §21.10): rain over heavily irradiated land (a strong field at the ground, the nuclear",
                    "wasteland, under fallout plumes) falls black, and a nuclear ground burst's own cloud rains black for six minutes",
                    "from 40 seconds after it, whatever the weather. Plants under it die.")
            .translation(GBLang.config("radiation.blackRain", "Black rain"))
            .define("blackRain", true);
    public static final ForgeConfigSpec.BooleanValue BLACK_RAIN_HARMS = B
            .comment("Whether black rain harms what is out in it: the fallout it washes down (radiation) and acid burns, armour",
                    "corroding; a hazmat suit keeps its share out. Off: it only looks and kills plants.")
            .translation(GBLang.config("radiation.blackRainHarms", "Black rain harms"))
            .define("blackRainHarms", true);

    static {
        B.pop();
        B.translation(GBLang.config("terrain", "Terrain")).comment("Blast aftermath on terrain.").push("terrain");
    }

    public static final ForgeConfigSpec.BooleanValue CONVERT_BIOMES = B
            .comment("Paint the nuclear wasteland biome over the scorched area of nuclear blasts.")
            .translation(GBLang.config("terrain.convertBiomes", "Create wasteland biome"))
            .define("convertBiomes", true);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TERRAIN_SWAPS = B
            .comment("Swap rules for what blasts leave (DESIGN.md §21.10), checked before the terrain tables' own, one a line:",
                    "  <tables> <bands> <blocks> -> <block> [chance] [fade]",
                    "  \"nuclear lining #minecraft:sand -> mymod:rare_ore 5%\"   (5% of the sand in a nuclear crater's wall a rare",
                    "                                                         ore; the rest falls through to the table's trinitite)",
                    "  \"nuclear scorch,charred #minecraft:logs -> mymod:burnt_log\"",
                    "  \"* broken minecraft:glass -> mymod:cracked_glass 60% fade\"",
                    "Tables: conventional, nuclear, naval, or a datapack's (groundburst:terrain_table), * for all. Bands: shock (the",
                    "light blocks the shock blows out), scorch (the ground the fireball sees), lining (a nuclear crater's wall), and",
                    "a nuclear blast's far rings charred, broken and withered; * for all. Blocks: ids, #tags or patterns such as",
                    "*:*_planks, comma-separated. The block it becomes: an id with [properties] if wanted, air, keep (left as it is),",
                    "or a weighted choice, a*3|b*1. Chance: 0.05 or 5% (default all of them); a rule whose chance fails passes the",
                    "block on to the next rule. fade: the chance falls to nothing across the band, outward. Each band already",
                    "thins out toward its edge as it always has; the rules work inside that. A block keeps the properties the",
                    "new block shares with it (a log's axis, a stair's facing).")
            .translation(GBLang.config("terrain.swaps", "Swap rules"))
            .defineListAllowEmpty("swaps", List.of(), dev.groundburst.blast.TerrainRules::isLine);

    static {
        B.pop();
        B.translation(GBLang.config("aftermath", "Far aftermath"))
                .comment("A nuclear blast's far aftermath (DESIGN.md §21.10): past the blast's own reach, rings of charred, broken and",
                        "withered land, a far wave at the speed of sound, all of it healing as the nuclear kind's when healing is on.")
                .push("aftermath");
    }

    public static final ForgeConfigSpec.BooleanValue AFTERMATH_ENABLED = B
            .comment("Master switch. Off: a nuclear blast changes nothing past its own reach.")
            .translation(GBLang.config("aftermath.enabled", "Far aftermath"))
            .define("enabled", true);
    public static final ForgeConfigSpec.DoubleValue AFTERMATH_CHARRED = B
            .comment("How far the charred land reaches (what the fireball's heat sees burns: grass scorched, plants and leaves",
                    "burnt, logs charred; the wasteland's grey over it), as a multiple of the blast's reach (where it stops hurting).")
            .translation(GBLang.config("aftermath.charredReach", "Charred land's reach"))
            .defineInRange("charredReach", 1.4, 1.0, 10.0);
    public static final ForgeConfigSpec.DoubleValue AFTERMATH_BROKEN = B
            .comment("How far the broken land reaches (the blast wave's: glass facing the blast shattered, doors blown open,",
                    "leaves stripped, torches knocked off), as a multiple of the blast's reach.")
            .translation(GBLang.config("aftermath.brokenReach", "Broken land's reach"))
            .defineInRange("brokenReach", 3.0, 1.0, 10.0);
    public static final ForgeConfigSpec.DoubleValue AFTERMATH_WITHERED = B
            .comment("How far the withered land reaches (the heat's last reach: leaves and grass dying in patches, crops withered),",
                    "as a multiple of the blast's reach. The far wave runs out to here. The Tsar's, at 4.3: about 3 000 blocks.")
            .translation(GBLang.config("aftermath.witheredReach", "Withered land's reach"))
            .defineInRange("witheredReach", 4.3, 1.0, 10.0);
    public static final ForgeConfigSpec.IntValue AFTERMATH_MAX_REACH = B
            .comment("The farthest any far aftermath reaches, blocks, whatever the multiples above make it.")
            .translation(GBLang.config("aftermath.maxReach", "Farthest reach (blocks)"))
            .defineInRange("maxReach", 4096, 64, 16_384);
    public static final ForgeConfigSpec.BooleanValue AFTERMATH_WAVE = B
            .comment("The far wave: past the blast's reach its shock carries on at the speed of sound to the withered land's edge,",
                    "throwing creatures out in the open and, nearer, hurting them; players see it come and feel it.")
            .translation(GBLang.config("aftermath.farWave", "Far wave"))
            .define("farWave", true);
    public static final ForgeConfigSpec.BooleanValue AFTERMATH_LOAD = B
            .comment("Load the chunks inside the rings that exist but are not loaded, a few at a time, to change them (so maps and",
                    "Distant Horizons show the land as it is). Never generates chunks. Off: they change when they next load.")
            .translation(GBLang.config("aftermath.loadChunks", "Change unloaded chunks"))
            .define("loadChunks", true);
    public static final ForgeConfigSpec.BooleanValue AFTERMATH_NEW_CHUNKS = B
            .comment("Change chunks inside the rings that are generated later (explored after the blast), as they load.")
            .translation(GBLang.config("aftermath.convertNewChunks", "Change chunks generated later"))
            .define("convertNewChunks", true);
    public static final ForgeConfigSpec.IntValue AFTERMATH_MS = B
            .comment("Milliseconds a tick the far aftermath may take to change chunks.")
            .translation(GBLang.config("aftermath.msPerTick", "Time budget per tick (ms)"))
            .defineInRange("msPerTick", 3, 1, 40);

    static {
        B.pop();
        B.translation(GBLang.config("missiles", "Missiles")).comment("Missile flight.").push("missiles");
    }

    public static final ForgeConfigSpec.IntValue MISSILE_MAX_RANGE = B
            .comment("Longest allowed missile flight in blocks, whatever the propulsion.")
            .translation(GBLang.config("missiles.maxRange", "Maximum range"))
            .defineInRange("maxRange", 30_000, 64, 1_000_000);

    public static final ForgeConfigSpec.BooleanValue MISSILE_LOAD_CHUNKS = B
            .comment("Load chunks along low flight paths and at the target so launches work with nobody nearby.")
            .translation(GBLang.config("missiles.loadChunksAlongPath", "Load chunks along the path"))
            .define("loadChunksAlongPath", true);

    static {
        B.pop();
        B.translation(GBLang.config("emp", "EMP")).comment("Electromagnetic pulse effects.").push("emp");
    }

    public static final ForgeConfigSpec.BooleanValue EMP_AFFECTS_REDSTONE = B
            .comment("Inside an EMP field, vanilla redstone signals read as zero.")
            .translation(GBLang.config("emp.affectsVanillaRedstone", "Affects vanilla redstone"))
            .define("affectsVanillaRedstone", true);

    public static final ForgeConfigSpec.BooleanValue EMP_BURNS_CABLES = B
            .comment("High-voltage cables inside an EMP field can burn out.")
            .translation(GBLang.config("emp.cablesBurnOut", "HV cables burn out"))
            .define("cablesBurnOut", false);

    static {
        B.pop();
        B.translation(GBLang.config("reactor", "Reactors")).comment("Breeder reactor behaviour.").push("reactor");
    }

    public static final ForgeConfigSpec.BooleanValue REACTOR_MELTDOWNS = B
            .comment("When true (the default), an overheating reactor goes critical and melts down unless SCRAMmed in time.",
                    "When false, it shuts itself down (SCRAM) at 900 C instead.")
            .translation(GBLang.config("reactor.meltdowns", "Meltdowns"))
            .define("meltdowns", true);

    static {
        B.pop();
        B.translation(GBLang.config("singularity", "Singularities")).comment("Black hole behaviour.").push("singularity");
    }

    public static final ForgeConfigSpec.IntValue SINGULARITY_MAX_BLOCKS = B
            .comment("Most blocks a single singularity may tear out of the ground.")
            .translation(GBLang.config("singularity.maxBlocksRipped", "Maximum blocks ripped"))
            .defineInRange("maxBlocksRipped", 20_000, 0, 500_000);

    static {
        B.pop();
        B.translation(GBLang.config("superweapons", "Superweapons")).comment("Particle Cannon, Nuclear Missile Silo and SCUD Storm.").push("superweapons");
    }

    public static final ForgeConfigSpec.BooleanValue SUPERWEAPON_ANNOUNCE = B
            .comment("Warn every player in the dimension when a superweapon is built, ready, or fired.")
            .translation(GBLang.config("superweapons.announce", "Announcements"))
            .define("announce", true);

    public static final ForgeConfigSpec.IntValue SUPERWEAPON_MAX_RANGE = B
            .comment("Maximum targeting range of superweapons, in blocks.")
            .translation(GBLang.config("superweapons.maxRange", "Maximum range"))
            .defineInRange("maxRange", 4_096, 64, 1_000_000);

    public static final ForgeConfigSpec.DoubleValue SUPERWEAPON_CHARGE_MULTIPLIER = B
            .comment("Multiplies superweapon charge times. 0.5 charges twice as fast.")
            .translation(GBLang.config("superweapons.chargeTimeMultiplier", "Charge time multiplier"))
            .defineInRange("chargeTimeMultiplier", 1.0, 0.05, 20.0);

    static {
        B.pop();
        B.translation(GBLang.config("manual", "Field Manual")).comment("The in-game guide book.").push("manual");
    }

    public static final ForgeConfigSpec.BooleanValue GIVE_MANUAL = B
            .comment("Give each player a Field Manual the first time they join the world.")
            .translation(GBLang.config("manual.giveOnFirstJoin", "Give on first join"))
            .define("giveOnFirstJoin", true);

    static {
        B.pop();
        B.translation(GBLang.config("healing", "Healing"))
                .comment("Healing: the damage weapons and explosions do grows back over time, block for block as it was (DESIGN.md §21.9).",
                        "Off by default. Only damage done while it is on is remembered, so turn it on before the war starts.")
                .push("healing");
    }

    public static final ForgeConfigSpec.BooleanValue HEAL_ENABLED = B
            .comment("Master switch. When on, every block a weapon or an explosion changes is remembered as it was (with a chest's",
                    "contents, a sign's words, a door's facing) and put back later. Drops are held back so nothing is duplicated.",
                    "What heals is set below: this mod's weapons under What heals, creepers, TNT and other mods' under Other explosions.")
            .translation(GBLang.config("healing.enabled", "Heal damage over time"))
            .define("enabled", false);

    public static final ForgeConfigSpec.EnumValue<HealSchedule> HEAL_SCHEDULE = B
            .comment("When damaged ground heals:",
                    "  AFTER_WAIT   - once it has been left alone for the wait below (the usual).",
                    "  MORNING      - at the first sunrise after the wait (set the wait to 0 to heal every morning).",
                    "                 Dimensions without day and night use AFTER_WAIT.",
                    "  COMMAND_ONLY - never by itself; an operator runs /groundburst heal now.")
            .translation(GBLang.config("healing.schedule", "When healing happens"))
            .defineEnum("schedule", HealSchedule.AFTER_WAIT);

    public static final ForgeConfigSpec.EnumValue<HealClock> HEAL_CLOCK = B
            .comment("The clock the waits below are counted by (DESIGN.md §21.10):",
                    "  PLAYED - the time the world has been running: it stops while the server is off or a single-player",
                    "           world is shut (the usual: what you wait for is play).",
                    "  REAL   - real time, as on a clock on the wall: a server switched off over the weekend has healed when",
                    "           it starts again, so \"7d\" is a week of the calendar.")
            .translation(GBLang.config("healing.clock", "Clock the wait counts by"))
            .defineEnum("clock", HealClock.PLAYED);

    public static final ForgeConfigSpec.ConfigValue<String> HEAL_WAIT = B
            .comment("How long an area waits after the last damage before it starts to heal: a number and a unit, s (seconds),",
                    "m (minutes), h (hours), d (days) or w (weeks), several together if you like: \"30m\", \"12h\", \"7d\", \"1d12h\".",
                    "Fresh damage nearby starts the wait again, so a battle is not undone while it is being fought. At most 52 weeks.",
                    "(This was waitSeconds, a number of seconds: a value set there before must be set here again.)")
            .translation(GBLang.config("healing.wait", "Wait before healing"))
            .define("wait", "5m", o -> Durations.valid(o, GBServerConfig.MAX_HEAL_WAIT));

    public static final ForgeConfigSpec.ConfigValue<String> HEAL_RANDOM_EXTRA = B
            .comment("Up to this much longer, chosen at random for each chunk, so a crater heals in patches, not all at once",
                    "(written as the wait is: \"1m\", \"2h\", \"1d\"; at most 4 weeks).")
            .translation(GBLang.config("healing.randomExtra", "Random extra wait"))
            .define("randomExtra", "1m", o -> Durations.valid(o, GBServerConfig.MAX_HEAL_EXTRA));

    public static final ForgeConfigSpec.IntValue HEAL_BLOCKS_PER_SECOND = B
            .comment("Blocks each damaged chunk puts back a second, at the least.")
            .translation(GBLang.config("healing.blocksPerSecond", "Blocks healed a second (each chunk)"))
            .defineInRange("blocksPerSecond", 20, 1, 100_000);

    public static final ForgeConfigSpec.ConfigValue<String> HEAL_LONGEST = B
            .comment("The longest a chunk may take to heal once it begins (written as the wait is: \"5m\", \"1h\"; at most 4 weeks):",
                    "a chunk with much to put back heals faster to keep to it, so a nuclear crater heals in about the same time as",
                    "a grenade's hole. \"0\" lets big damage take as long as the speed above makes it. Healing is work the server",
                    "does in a loaded chunk, so this counts while the chunk is loaded, whichever clock the wait counts by.")
            .translation(GBLang.config("healing.longest", "Longest heal"))
            .define("longest", "5m", o -> Durations.valid(o, GBServerConfig.MAX_HEAL_EXTRA));

    public static final ForgeConfigSpec.EnumValue<HealOrder> HEAL_ORDER = B
            .comment("The order blocks come back in:",
                    "  BOTTOM_UP   - the lowest first, so the ground rises back and nothing hangs in the air while it heals.",
                    "  RANDOM      - here and there all through the damage.",
                    "  ALL_AT_ONCE - each chunk's damage comes back as soon as it is due, as fast as the server allows.")
            .translation(GBLang.config("healing.order", "Order"))
            .defineEnum("order", HealOrder.BOTTOM_UP);

    public static final ForgeConfigSpec.BooleanValue HEAL_EFFECTS = B
            .comment("Sounds and a few green sparkles as blocks come back.")
            .translation(GBLang.config("healing.effects", "Sounds and sparkles"))
            .define("effects", true);

    static {
        B.translation(GBLang.config("healing.what", "What heals")).comment("What heals, and what never does.").push("what");
    }

    public static final ForgeConfigSpec.BooleanValue HEAL_CONVENTIONAL = B
            .comment("Grenades, charges, mines, bombs, shells and every warhead that is not nuclear.")
            .translation(GBLang.config("healing.what.conventional", "Conventional weapons"))
            .define("conventional", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_NUCLEAR = B
            .comment("Nuclear blasts and what follows them: the crater, its lake, the wasteland's scorched ground, fallout ash,",
                    "plants killed by radiation, a meltdown's corium.")
            .translation(GBLang.config("healing.what.nuclear", "Nuclear weapons"))
            .define("nuclear", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_SPECIAL = B
            .comment("Antimatter, singularities, the cryo freeze and frost, storm strikes and the Particle Uplink Cannon's beam.")
            .translation(GBLang.config("healing.what.special", "Special weapons and superweapons"))
            .define("special", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_CHEMICAL = B
            .comment("Toxin residue, sludge and poisoned soil.")
            .translation(GBLang.config("healing.what.chemical", "Chemical weapons"))
            .define("chemical", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_AFTERMATH = B
            .comment("What the damage sets going next to it: blocks burnt by spreading fire, sand and gravel that fall, leaves that",
                    "decay once their tree is gone. Off: only what the weapons themselves change heals.")
            .translation(GBLang.config("healing.what.aftermath", "Fire spread, falling sand, decaying leaves"))
            .define("aftermath", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_DECORATIONS = B
            .comment("Paintings, item frames (with what is in them) and armour stands (with what they wear) a blast knocks down",
                    "are put back where they hung once the blocks round them have healed. They drop nothing meanwhile.")
            .translation(GBLang.config("healing.what.decorations", "Paintings, item frames, armour stands"))
            .define("decorations", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_BIOMES = B
            .comment("The nuclear wasteland's biome (grey grass, brown fog, falling ash) turns back to the biome it was.")
            .translation(GBLang.config("healing.what.biomes", "Biomes"))
            .define("biomes", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_CLEAR_RADIATION = B
            .comment("Radiation centred in a chunk goes when that chunk starts to heal, so healed plants are not killed again.")
            .translation(GBLang.config("healing.what.clearRadiation", "Clear radiation"))
            .define("clearRadiation", true);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HEAL_EXCLUDED_WEAPONS = B
            .comment("Weapons whose damage never heals: blast profile ids as /groundburst blast lists them (a * matches anything, so",
                    "\"groundburst:tsar*\" is the Tsar), or groundburst:particle_beam, groundburst:cryo_freeze, groundburst:frost,",
                    "groundburst:singularity, groundburst:antimatter, groundburst:storm, groundburst:fallout, groundburst:radiation,",
                    "groundburst:meltdown, groundburst:toxin, groundburst:breach, groundburst:incendiary.",
                    "Other explosions by what set them off: its entity's id (\"minecraft:tnt_minecart\", or another mod's, such as",
                    "\"somemod:rocket\" or \"somemod:*\"), or with no entity its damage type (\"minecraft:explosion\").")
            .translation(GBLang.config("healing.what.excludedWeapons", "Weapons that never heal"))
            .defineListAllowEmpty("excludedWeapons", List.of(), GBServerConfig::isPattern);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HEAL_EXCLUDED_BLOCKS = B
            .comment("Blocks never put back (ids, or #tags such as \"#minecraft:logs\"). Explosives never are: they went off.")
            .translation(GBLang.config("healing.what.excludedBlocks", "Blocks that never heal"))
            .defineListAllowEmpty("excludedBlocks", List.of(), GBServerConfig::isBlockOrTag);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HEAL_EXCLUDED_DIMENSIONS = B
            .comment("Dimensions (e.g. \"minecraft:the_nether\") where damage never heals.")
            .translation(GBLang.config("healing.what.excludedDimensions", "Dimensions that never heal"))
            .defineListAllowEmpty("excludedDimensions", List.of(), GBServerConfig::isIdentifier);

    static {
        B.pop();
        B.translation(GBLang.config("healing.explosions", "Other explosions"))
                .comment("The game's own explosions, and other mods' that go through the game's explosion code. Healing must be on",
                        "(the master switch above). Turn one off here if another mod heals it already.")
                .push("explosions");
    }

    public static final ForgeConfigSpec.BooleanValue HEAL_CREEPERS = B
            .comment("Creepers' explosions, charged ones too.")
            .translation(GBLang.config("healing.explosions.creepers", "Creepers"))
            .define("creepers", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_TNT = B
            .comment("TNT and TNT minecarts. TNT that an explosion lights goes off, so it is not put back. Turn this off if you",
                    "dig with TNT and want the holes to stay.")
            .translation(GBLang.config("healing.explosions.tnt", "TNT"))
            .define("tnt", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_END_CRYSTALS = B
            .comment("End crystals.")
            .translation(GBLang.config("healing.explosions.endCrystals", "End crystals"))
            .define("endCrystals", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_FIREBALLS = B
            .comment("Ghasts' fireballs (and, with the fire spread above on, what their fire burns).")
            .translation(GBLang.config("healing.explosions.ghastFireballs", "Ghast fireballs"))
            .define("ghastFireballs", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_WITHER = B
            .comment("The Wither: the blast as it is made, and its skulls.")
            .translation(GBLang.config("healing.explosions.wither", "The Wither"))
            .define("wither", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_BEDS = B
            .comment("Beds and respawn anchors that blow up where they do not work (a bed in the Nether or the End, an anchor",
                    "outside the Nether).")
            .translation(GBLang.config("healing.explosions.bedsAndAnchors", "Beds and respawn anchors"))
            .define("bedsAndAnchors", true);

    public static final ForgeConfigSpec.BooleanValue HEAL_OTHER_MODS = B
            .comment("Other mods' explosions that go through the game's own explosion code, as most mods' do. One that works out",
                    "its own crater (as this mod's weapons do) is not seen. Leave one mod's out by its id under What heals >",
                    "Weapons that never heal (\"somemod:*\").")
            .translation(GBLang.config("healing.explosions.otherMods", "Other mods' explosions"))
            .define("otherMods", true);

    static {
        B.pop();
        B.translation(GBLang.config("healing.players", "Players")).comment("Players and the healing ground.").push("players");
    }

    public static final ForgeConfigSpec.EnumValue<HealPlayerBlocks> HEAL_PLAYER_BLOCKS = B
            .comment("What healing does where a player has put a block in the damage since:",
                    "  KEEP    - the player's block stays, and the ground within the keep radius of it, and the air over it,",
                    "            are left as they are, so a base built in a crater is not filled in.",
                    "  DROP    - the player's block pops off as an item and the ground heals.",
                    "  REPLACE - the ground heals over it (the block is lost).")
            .translation(GBLang.config("healing.players.playerBlocks", "Blocks players placed"))
            .defineEnum("playerBlocks", HealPlayerBlocks.KEEP);

    public static final ForgeConfigSpec.IntValue HEAL_KEEP_RADIUS = B
            .comment("With KEEP: how far round a player's block the ground is left unhealed, in blocks (0: just the block).")
            .translation(GBLang.config("healing.players.keepRadius", "Keep radius"))
            .defineInRange("keepRadius", 3, 0, 16);

    public static final ForgeConfigSpec.BooleanValue HEAL_DROPS = B
            .comment("Whether blocks that will heal still drop items when destroyed. Off (the default) nothing is duplicated: they",
                    "drop nothing and come back with what they held. On, they drop too, and what spills out of one (the game's",
                    "explosions spill a chest) does not come back with it. This mod's explosions spill nothing: what they break comes back full.")
            .translation(GBLang.config("healing.players.dropItems", "Healing blocks drop items too"))
            .define("dropItems", false);

    static {
        B.pop();
        B.translation(GBLang.config("healing.performance", "Performance")).comment("How hard healing may work the server.").push("performance");
    }

    public static final ForgeConfigSpec.IntValue HEAL_MAX_BLOCKS_PER_TICK = B
            .comment("Most blocks healed in a tick across the whole world; a big crater shares them out round its chunks.")
            .translation(GBLang.config("healing.performance.maxBlocksPerTick", "Blocks healed per tick (whole server)"))
            .defineInRange("maxBlocksPerTick", 2_000, 50, 100_000);

    public static final ForgeConfigSpec.IntValue HEAL_MS_PER_TICK = B
            .comment("Milliseconds of each tick healing may take.")
            .translation(GBLang.config("healing.performance.msPerTick", "Time budget per tick (ms)"))
            .defineInRange("msPerTick", 5, 1, 40);

    static {
        B.pop();
        B.pop();
    }

    /** {@code healing.schedule}: when damaged ground heals. */
    public enum HealSchedule { AFTER_WAIT, MORNING, COMMAND_ONLY }

    /** {@code healing.clock}: the clock healing's wait counts by (DESIGN.md §21.10). */
    public enum HealClock { PLAYED, REAL }

    /** The longest healing wait, and the longest random extra and heal, in seconds (52 weeks; 4 weeks). */
    public static final long MAX_HEAL_WAIT = 52L * 604_800, MAX_HEAL_EXTRA = 4L * 604_800;

    /** A healing time from the config in ticks (a config value always holds a valid one; the default if not). */
    public static int healTicks(ForgeConfigSpec.ConfigValue<String> value) {
        long s = Durations.seconds(value.get());
        if (s < 0) {
            s = Durations.seconds(value.getDefault());
        }
        return (int) Math.min(Integer.MAX_VALUE, s * 20);
    }

    /** {@code healing.order}: the order blocks come back in. */
    public enum HealOrder { BOTTOM_UP, RANDOM, ALL_AT_ONCE }

    /** {@code healing.players.playerBlocks}: what healing does where a player has put a block. */
    public enum HealPlayerBlocks { KEEP, DROP, REPLACE }

    public static final ForgeConfigSpec SPEC = B.build();

    private GBServerConfig() {}

    private static boolean isIdentifier(Object value) {
        return ((Object) value) instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    /** An id in which a {@code *} stands for anything. */
    private static boolean isPattern(Object value) {
        return ((Object) value) instanceof String s && !s.isBlank() && ResourceLocation.tryParse(s.replace("*", "x")) != null;
    }

    /** A block id, or a block tag as {@code #namespace:path}. */
    private static boolean isBlockOrTag(Object value) {
        return ((Object) value) instanceof String s && ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s) != null;
    }
}
