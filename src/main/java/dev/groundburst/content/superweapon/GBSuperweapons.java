package dev.groundburst.content.superweapon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockControllerBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockDefinition.Entry;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.content.multiblock.PartCut;
import dev.groundburst.content.multiblock.StructureSpawnerItem;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The C&C Generals superweapons (DESIGN.md §19.5): the GLA's SCUD Storm and China's Nuclear Missile, their themed
 * structure blocks and their ammunition.
 */
public final class GBSuperweapons {
    // --- the SCUD Storm's blocks ---
    /** Sun-dried mud brick, plastered: the SCUD Storm's wall and watchtowers. */
    public static final WithItem<MultiblockPartBlock> ADOBE_WALL = part("adobe_wall", "Adobe Wall", MapColor.SAND, SoundType.MUD_BRICKS);
    /** The SCUD Storm's tank of toxin in the middle of its pad. */
    public static final WithItem<MultiblockPartBlock> TOXIN_TANK = part("toxin_tank", "Toxin Tank", MapColor.COLOR_GREEN, SoundType.METAL);
    /** A SCUD's erector: the boom that cradles it and raises it upright to fire. */
    public static final WithItem<MultiblockPartBlock> SCUD_ERECTOR = part("scud_erector", "SCUD Erector", MapColor.TERRACOTTA_BROWN, SoundType.METAL);

    public static final WithItem<MultiblockControllerBlock> SCUD_STORM_CONTROLLER = GBBlocks.register("scud_storm", "SCUD Storm Controller",
            GBTab.MISSILES, p -> new MultiblockControllerBlock(p, GBSuperweapons.SCUD_STORM_ENTITY),
            p -> p.mapColor(MapColor.SAND).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScudStormBlockEntity>> SCUD_STORM_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("scud_storm", () -> new BlockEntityType<>(ScudStormBlockEntity::new,
                    SCUD_STORM_CONTROLLER.block().get()));

    // --- the Nuclear Missile Silo's blocks ---
    /** Dressed fieldstone: the hall's and the control house's walls. */
    public static final WithItem<MultiblockPartBlock> FIELDSTONE_WALL = part("fieldstone_wall", "Fieldstone Wall", MapColor.STONE,
            SoundType.STONE);
    /** The strongback the Nuclear Missile lies on in its hall, on its hinge and rams. */
    public static final WithItem<MultiblockPartBlock> MISSILE_ERECTOR = part("missile_erector", "Missile Erector", MapColor.COLOR_GREEN,
            SoundType.METAL);

    public static final WithItem<MultiblockControllerBlock> NUCLEAR_MISSILE_CONTROLLER = GBBlocks.register("nuclear_missile_silo",
            "Nuclear Missile Silo Controller", GBTab.MISSILES, p -> new MultiblockControllerBlock(p, GBSuperweapons.NUCLEAR_MISSILE_ENTITY),
            p -> p.mapColor(MapColor.COLOR_RED).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NuclearMissileBlockEntity>> NUCLEAR_MISSILE_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("nuclear_missile_silo", () -> new BlockEntityType<>(NuclearMissileBlockEntity::new,
                    NUCLEAR_MISSILE_CONTROLLER.block().get()));

    // --- the ammunition's factories ---
    public static final WithItem<MultiblockControllerBlock> ARMS_DEALER_CONTROLLER = GBBlocks.register("arms_dealer", "Arms Dealer Controller",
            GBTab.MISSILES, p -> new MultiblockControllerBlock(p, GBSuperweapons.ARMS_DEALER_ENTITY),
            p -> p.mapColor(MapColor.SAND).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AmmoFactoryBlockEntity>> ARMS_DEALER_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("arms_dealer", () -> new BlockEntityType<>(
                    (pos, state) -> new AmmoFactoryBlockEntity(GBSuperweapons.ARMS_DEALER_ENTITY.get(), pos, state, AmmoFactoryBlockEntity.Kind.ARMS_DEALER),
                    ARMS_DEALER_CONTROLLER.block().get()));
    public static final WithItem<MultiblockControllerBlock> WAR_FACTORY_CONTROLLER = GBBlocks.register("war_factory", "War Factory Controller",
            GBTab.MISSILES, p -> new MultiblockControllerBlock(p, GBSuperweapons.WAR_FACTORY_ENTITY),
            p -> p.mapColor(MapColor.COLOR_RED).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AmmoFactoryBlockEntity>> WAR_FACTORY_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("war_factory", () -> new BlockEntityType<>(
                    (pos, state) -> new AmmoFactoryBlockEntity(GBSuperweapons.WAR_FACTORY_ENTITY.get(), pos, state, AmmoFactoryBlockEntity.Kind.WAR_FACTORY),
                    WAR_FACTORY_CONTROLLER.block().get()));

    // --- ammunition ---
    public static final DeferredItem<SuperweaponAmmoItem> TOXIN_SCUD = GBItems.register("toxin_scud", "Toxin SCUD", GBTab.MISSILES,
            p -> new SuperweaponAmmoItem(p, List.of(
                    GBLang.add(GroundBurst.MODID + ".toxin_scud.fired_by", "Fired by a SCUD Storm, nine at a time"),
                    GBLang.add(GroundBurst.MODID + ".toxin_scud.effect", "A heavy blast, then toxin: a cloud and spreading residue"),
                    GBLang.add(GroundBurst.MODID + ".toxin_scud.built", "Built at an Arms Dealer"))),
            p -> p.stacksTo(ScudStormBlockEntity.CAPACITY).rarity(Rarity.RARE));
    public static final DeferredItem<SuperweaponAmmoItem> NUCLEAR_MISSILE_ROUND = GBItems.register("nuclear_missile", "Nuclear Missile",
            GBTab.MISSILES, p -> new SuperweaponAmmoItem(p, List.of(
                    GBLang.add(GroundBurst.MODID + ".nuclear_missile.fired_by", "Fired by a Nuclear Missile Silo"),
                    GBLang.add(GroundBurst.MODID + ".nuclear_missile.effect", "A nuclear blast and a radiation field 200 blocks round"),
                    GBLang.add(GroundBurst.MODID + ".nuclear_missile.built", "Built at a War Factory"))),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    /**
     * The cells of the nine SCUD Erectors round the tank, by bearing (ScudStormBlockEntity.bearing): column, row offsets.
     * Each is the cell under its erector's launch table (2.4 blocks out, past the scrap round the tank).
     */
    private static final int[][] ERECTOR_CELLS = {{1, -2}, {2, -1}, {2, 0}, {2, 2}, {0, 2}, {-2, 2}, {-2, 0}, {-2, -1}, {-1, -2}};

    /**
     * The SCUD Storm: a 15x15 pad of Launch Pad (the controller at the front middle, an Item Port and an Energy Port along
     * the front either side of it) behind an L of Adobe Wall two high down its left side and across its back, a
     * watchtower of it four high at each end; on the pad, a 3x3 Toxin Tank two high in the middle and nine SCUD Erectors
     * round it; everything else over the pad kept clear six blocks up (the SCUDs lie on it and rise over it).
     */
    public static final MultiblockDefinition SCUD_STORM = new MultiblockDefinition(GroundBurst.id("scud_storm"), scudStormLayers(),
            Map.of('P', Entry.of(() -> GBLaunch.LAUNCH_PAD.block().get()), 'A', Entry.of(() -> ADOBE_WALL.block().get()),
                    'T', Entry.of(() -> TOXIN_TANK.block().get()), 'S', Entry.of(() -> SCUD_ERECTOR.block().get()),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()), 'I', Entry.of(GBMultiblocks.ITEM_PORT.block()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> SCUD_STORM_CONTROLLER.block().get())),
            scudStormCuts());

    public static final DeferredItem<StructureSpawnerItem> SCUD_STORM_SPAWNER = GBItems.register("scud_storm_spawner",
            "Structure Spawner: SCUD Storm", GBTab.MISSILES, p -> new StructureSpawnerItem(p, () -> SCUD_STORM),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    /**
     * The Nuclear Missile Silo: the hall, 5 wide and 15 long, its walls of Fieldstone Wall two high (the controller in the
     * middle of its front wall, an Energy Port in its right wall and an Item Port in its left), a floor of Launch Pad
     * inside with the Missile Erector on it at the launch end; a 3x3 control house of Fieldstone Wall two high beside
     * the hall's front; a Gantry Frame tower nine high beside its launch end; the hall's inside and its roof's space kept
     * clear, and the missile's column over the launch end eleven blocks up.
     */
    public static final MultiblockDefinition NUCLEAR_MISSILE = new MultiblockDefinition(GroundBurst.id("nuclear_missile_silo"), nukeLayers(),
            Map.of('P', Entry.of(() -> GBLaunch.LAUNCH_PAD.block().get()), 'F', Entry.of(() -> FIELDSTONE_WALL.block().get()),
                    'M', Entry.of(() -> MISSILE_ERECTOR.block().get()), 'G', Entry.of(() -> GBLaunch.GANTRY_FRAME.block().get()),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()), 'I', Entry.of(GBMultiblocks.ITEM_PORT.block()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> NUCLEAR_MISSILE_CONTROLLER.block().get())),
            nukeCuts());

    public static final DeferredItem<StructureSpawnerItem> NUCLEAR_MISSILE_SPAWNER = GBItems.register("nuclear_missile_silo_spawner",
            "Structure Spawner: Nuclear Missile Silo", GBTab.MISSILES, p -> new StructureSpawnerItem(p, () -> NUCLEAR_MISSILE),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    /**
     * The Arms Dealer (the GLA's): a compound of Adobe Wall two high round a 7x5 yard of Launch Pad (the controller in the
     * middle of its front wall, an Item Port in its left wall and an Energy Port in its right); at the back of the yard
     * the workshop, a 7x2 block of Adobe Wall two high; the yard kept clear three high (the SCUD lies across it under the
     * hoist).
     */
    public static final MultiblockDefinition ARMS_DEALER = new MultiblockDefinition(GroundBurst.id("arms_dealer"), armsDealerLayers(),
            Map.of('P', Entry.of(() -> GBLaunch.LAUNCH_PAD.block().get()), 'A', Entry.of(() -> ADOBE_WALL.block().get()),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()), 'I', Entry.of(GBMultiblocks.ITEM_PORT.block()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> ARMS_DEALER_CONTROLLER.block().get())),
            yardCuts(1, 7, 1, 3));

    /**
     * The War Factory (China's): its octagonal hall at the back left, a 5x5 ring of Fieldstone Wall two high round a floor
     * of Launch Pad; the furnace annex at the front left, a 3x3 of it two high with its chimney six high in the corner; the
     * missile's yard down the right, three wide and eleven long, with the gantry crane's Crane Rail along its far side; a
     * floor of Launch Pad between and round them (the controller in the middle of the front row, the Energy Port and the
     * Item Port in the back row); the yard, the crane's way and the space over the hall's roofs kept clear.
     */
    public static final MultiblockDefinition WAR_FACTORY = new MultiblockDefinition(GroundBurst.id("war_factory"), warFactoryLayers(),
            Map.of('P', Entry.of(() -> GBLaunch.LAUNCH_PAD.block().get()), 'F', Entry.of(() -> FIELDSTONE_WALL.block().get()),
                    'R', Entry.of(() -> dev.groundburst.content.strategic.GBStrategic.CRANE_RAIL.block().get()),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()), 'I', Entry.of(GBMultiblocks.ITEM_PORT.block()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> WAR_FACTORY_CONTROLLER.block().get())),
            warFactoryCuts());

    public static final DeferredItem<StructureSpawnerItem> ARMS_DEALER_SPAWNER = GBItems.register("arms_dealer_spawner",
            "Structure Spawner: Arms Dealer", GBTab.MISSILES, p -> new StructureSpawnerItem(p, () -> ARMS_DEALER),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<StructureSpawnerItem> WAR_FACTORY_SPAWNER = GBItems.register("war_factory_spawner",
            "Structure Spawner: War Factory", GBTab.MISSILES, p -> new StructureSpawnerItem(p, () -> WAR_FACTORY),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    private static String[][] armsDealerLayers() {
        String[][] layers = new String[4][7];
        for (int y = 0; y < 4; y++) {
            for (int row = 0; row < 7; row++) {
                char[] line = new char[9];
                for (int col = 0; col < 9; col++) {
                    boolean wall = col == 0 || col == 8 || row == 0 || row == 6;
                    boolean workshop = !wall && row >= 4;
                    char c;
                    if (y <= 1) {
                        c = wall || workshop && y == 1 ? 'A' : y == 0 ? 'P' : '.';
                    } else {
                        c = !wall && !workshop ? '.' : ' ';
                    }
                    line[col] = c;
                }
                layers[y][row] = new String(line);
            }
        }
        layers[0][0] = replace(layers[0][0], 4, MultiblockDefinition.CONTROLLER);
        layers[0][2] = replace(replace(layers[0][2], 0, 'I'), 8, 'E');
        return layers;
    }

    private static String[][] warFactoryLayers() {
        String[][] layers = new String[7][13];
        for (int y = 0; y < 7; y++) {
            for (int row = 0; row < 13; row++) {
                char[] line = new char[9];
                for (int col = 0; col < 9; col++) {
                    boolean hall = col <= 4 && row >= 7 && row <= 11;
                    boolean hallWall = hall && (col == 0 || col == 4 || row == 7 || row == 11);
                    boolean annex = col <= 2 && row >= 1 && row <= 3;
                    boolean chimney = col == 0 && row == 1;
                    boolean yard = col >= 5 && col <= 7 && row >= 1 && row <= 11;
                    boolean rail = col == 8 && row >= 1 && row <= 11;
                    char c = ' ';
                    if (y == 0) {
                        c = hallWall || annex ? 'F' : rail ? 'R' : 'P';
                    } else if (y == 1) {
                        c = hallWall || annex ? 'F' : yard || rail ? '.' : ' ';
                    } else {
                        c = chimney && y <= 5 ? 'F' : hall && y <= 4 ? '.' : (yard && y <= 2) || (rail && y <= 5) ? '.' : ' ';
                    }
                    line[col] = c;
                }
                layers[y][row] = new String(line);
            }
        }
        layers[0][0] = replace(layers[0][0], 4, MultiblockDefinition.CONTROLLER);
        layers[0][12] = replace(replace(layers[0][12], 6, 'E'), 7, 'I');
        return layers;
    }

    /** A yard of deck (cols c0..c1, rows r0..r1) cut 14 px high on the bottom layer. */
    private static Map<Long, PartCut> yardCuts(int c0, int c1, int r0, int r1) {
        Map<Long, PartCut> cuts = new HashMap<>();
        for (int row = r0; row <= r1; row++) {
            for (int col = c0; col <= c1; col++) {
                cuts.put(MultiblockDefinition.cutKey(col, row, 0), PartCut.DECK);
            }
        }
        return cuts;
    }

    /** The War Factory's floors (all its Launch Pad but under the hall's walls and the annex) cut to the deck. */
    private static Map<Long, PartCut> warFactoryCuts() {
        Map<Long, PartCut> cuts = new HashMap<>();
        String[] floor = warFactoryLayers()[0];
        for (int row = 0; row < floor.length; row++) {
            for (int col = 0; col < floor[row].length(); col++) {
                if (floor[row].charAt(col) == 'P') {
                    cuts.put(MultiblockDefinition.cutKey(col, row, 0), PartCut.DECK);
                }
            }
        }
        return cuts;
    }

    /** The Nuclear Missile Silo's pattern: 9 columns by 15 rows, 11 layers; the controller in the hall's front wall. */
    public static final int NUKE_COLS = 9, NUKE_ROWS = 15, NUKE_LAYERS = 11, NUKE_CENTER_COL = 5;

    private static String[][] nukeLayers() {
        String[][] layers = new String[NUKE_LAYERS][NUKE_ROWS];
        for (int y = 0; y < NUKE_LAYERS; y++) {
            for (int row = 0; row < NUKE_ROWS; row++) {
                char[] line = new char[NUKE_COLS];
                for (int col = 0; col < NUKE_COLS; col++) {
                    boolean hall = col >= 3 && col <= 7;
                    boolean wall = hall && (col == 3 || col == 7 || row == 0 || row == NUKE_ROWS - 1);
                    boolean inside = col >= 4 && col <= 6 && row >= 1 && row <= NUKE_ROWS - 2;
                    boolean house = col <= 2 && row <= 2;
                    boolean tower = col == 8 && row == 12;
                    boolean column = col >= 4 && col <= 6 && row >= 11 && row <= 13;
                    char c = ' ';
                    if (y <= 1) {
                        c = wall || house ? 'F' : inside ? (y == 0 ? 'P' : '.') : tower ? 'G' : ' ';
                    } else if (y <= 3) {
                        c = hall ? '.' : house && y == 2 ? '.' : tower ? 'G' : ' ';
                    } else {
                        c = column ? '.' : tower && y <= 8 ? 'G' : ' ';
                    }
                    line[col] = c;
                }
                layers[y][row] = new String(line);
            }
        }
        layers[0][0] = replace(layers[0][0], NUKE_CENTER_COL, MultiblockDefinition.CONTROLLER);
        layers[0][2] = replace(layers[0][2], 7, 'E');
        layers[0][4] = replace(layers[0][4], 3, 'I');
        layers[1][12] = replace(layers[1][12], NUKE_CENTER_COL, 'M');
        return layers;
    }

    private static String replace(String row, int col, char c) {
        char[] a = row.toCharArray();
        a[col] = c;
        return new String(a);
    }

    /** The hall's floor cut to the deck (14 px high). */
    private static Map<Long, PartCut> nukeCuts() {
        Map<Long, PartCut> cuts = new HashMap<>();
        for (int row = 1; row < NUKE_ROWS - 1; row++) {
            for (int col = 4; col <= 6; col++) {
                cuts.put(MultiblockDefinition.cutKey(col, row, 0), PartCut.DECK);
            }
        }
        return cuts;
    }

    /** The SCUD Storm's pattern: 16 columns (the wall in the first) by 16 rows (the wall in the last), 7 layers. */
    public static final int SCUD_COLS = 16, SCUD_ROWS = 16, SCUD_LAYERS = 7, SCUD_CENTER_COL = 8, SCUD_CENTER_ROW = 7;

    private static String[][] scudStormLayers() {
        char[][][] grid = new char[SCUD_LAYERS][SCUD_ROWS][SCUD_COLS];
        for (int y = 0; y < SCUD_LAYERS; y++) {
            for (int row = 0; row < SCUD_ROWS; row++) {
                for (int col = 0; col < SCUD_COLS; col++) {
                    boolean wall = col == 0 || row == SCUD_ROWS - 1;
                    boolean tower = col == 0 && row == 0 || col == SCUD_COLS - 1 && row == SCUD_ROWS - 1;
                    char c;
                    if (wall) {
                        c = y <= 1 || tower && y <= 3 ? 'A' : ' ';
                    } else if (y == 0) {
                        c = 'P';
                    } else {
                        c = '.';
                    }
                    grid[y][row][col] = c;
                }
            }
        }
        grid[0][0][SCUD_CENTER_COL] = MultiblockDefinition.CONTROLLER;
        grid[0][0][SCUD_CENTER_COL - 3] = 'I';
        grid[0][0][SCUD_CENTER_COL + 3] = 'E';
        for (int y = 1; y <= 2; y++) {
            for (int row = SCUD_CENTER_ROW - 1; row <= SCUD_CENTER_ROW + 1; row++) {
                for (int col = SCUD_CENTER_COL - 1; col <= SCUD_CENTER_COL + 1; col++) {
                    grid[y][row][col] = 'T';
                }
            }
        }
        for (int[] cell : ERECTOR_CELLS) {
            grid[1][SCUD_CENTER_ROW + cell[1]][SCUD_CENTER_COL + cell[0]] = 'S';
        }
        String[][] layers = new String[SCUD_LAYERS][SCUD_ROWS];
        for (int y = 0; y < SCUD_LAYERS; y++) {
            for (int row = 0; row < SCUD_ROWS; row++) {
                layers[y][row] = new String(grid[y][row]);
            }
        }
        return layers;
    }

    /** The pad's cells cut to the deck (14 px high), the controller's console, the ports' housings along the front edge. */
    private static Map<Long, PartCut> scudStormCuts() {
        Map<Long, PartCut> cuts = new HashMap<>();
        for (int row = 0; row < SCUD_ROWS - 1; row++) {
            for (int col = 1; col < SCUD_COLS; col++) {
                cuts.put(MultiblockDefinition.cutKey(col, row, 0), PartCut.DECK);
            }
        }
        cuts.put(MultiblockDefinition.cutKey(SCUD_CENTER_COL, 0, 0), PartCut.CONSOLE_NORTH);
        cuts.put(MultiblockDefinition.cutKey(SCUD_CENTER_COL - 3, 0, 0), PartCut.HOUSING_SOUTH);
        cuts.put(MultiblockDefinition.cutKey(SCUD_CENTER_COL + 3, 0, 0), PartCut.HOUSING_SOUTH);
        return cuts;
    }

    // --- what JEI says (here, so data generation writes them) ---
    public static final String JEI_AMMO_TITLE = GBLang.add("jei." + GroundBurst.MODID + ".ammo_factory", "Superweapon Ammunition");
    public static final String JEI_AMMO_INFO = GBLang.add("jei." + GroundBurst.MODID + ".ammo_factory.info", "%s: %s s, %s FE/t");
    public static final String INFO_SCUD_STORM = GBLang.add(GroundBurst.MODID + ".info.scud_storm",
            "The GLA's superweapon. Build it on a 15x15 pad of Launch Pad with its controller in the middle of the front edge, a "
                    + "3x3 Toxin Tank two high in the middle, nine SCUD Erectors round it and an L of Adobe Wall (two high, a "
                    + "watchtower four high at each end) down its left side and across its back (the Holo-Projector shows it). "
                    + "Load up to nine Toxin SCUDs and power it: it charges for 5 minutes, then a Launch Computer aims, arms and "
                    + "fires it. The erectors raise the SCUDs and they go one after another, coming down within 16 blocks of the "
                    + "target: heavy blasts, toxin clouds and anthrax residue spreading over the ground.");
    public static final String INFO_NUCLEAR_MISSILE = GBLang.add(GroundBurst.MODID + ".info.nuclear_missile",
            "China's superweapon, an above-ground coffin. Build its hall (5 wide, 15 long, walls of Fieldstone Wall two high, "
                    + "the controller in the middle of the front wall) on a floor of Launch Pad with the Missile Erector at the back, "
                    + "its 3x3 control house beside the front and a Gantry Frame tower nine high beside the back (the Holo-Projector "
                    + "shows it). Load a Nuclear Missile and power it: it charges for 6 minutes, then a Launch Computer aims, arms "
                    + "and fires it. The roof opens, the missile is raised and it climbs straight out: a nuclear blast and a "
                    + "radiation field 200 blocks round.");
    public static final String INFO_ARMS_DEALER = GBLang.add(GroundBurst.MODID + ".info.arms_dealer",
            "The GLA's factory for Toxin SCUDs: an adobe compound (9x7, walls of Adobe Wall two high, the controller in the "
                    + "middle of the front wall) round a yard of Launch Pad, its workshop at the back. Load its parts and power it: "
                    + "it builds by itself, laying each SCUD out on its trestles.");
    public static final String INFO_WAR_FACTORY = GBLang.add(GroundBurst.MODID + ".info.war_factory",
            "China's factory for Nuclear Missiles (9x13): an octagonal hall and a furnace annex of Fieldstone Wall, a missile "
                    + "yard of Launch Pad with a Crane Rail along it. Load its parts and power it: it builds by itself, its gantry "
                    + "crane laying each missile out on its cradles.");
    public static final String INFO_TOXIN = GBLang.add(GroundBurst.MODID + ".info.anthrax_residue",
            "Where a toxin SCUD comes down, its toxin wells up out of the crater as a heavy green cloud and spreads over the "
                    + "ground as glowing anthrax residue. The residue poisons whatever walks through it and dries out over about "
                    + "eight minutes. A hazmat suit keeps it out.");

    private GBSuperweapons() {}

    /** Loads the registrations. */
    public static void init() {}

    public static List<MultiblockDefinition> definitions() {
        return List.of(SCUD_STORM, NUCLEAR_MISSILE, ARMS_DEALER, WAR_FACTORY);
    }

    private static WithItem<MultiblockPartBlock> part(String id, String english, MapColor color, SoundType sound) {
        return GBBlocks.register(id, english, GBTab.MISSILES, MultiblockPartBlock::new,
                p -> p.mapColor(color).strength(4f, 10f).requiresCorrectToolForDrops().sound(sound).pushReaction(PushReaction.IMMOVEABLE));
    }
}
