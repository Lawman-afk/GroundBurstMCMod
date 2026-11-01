package dev.groundburst.content.launch;

import java.util.Map;
import java.util.function.Supplier;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockControllerBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockDefinition.Entry;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.content.multiblock.PartCut;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBSounds;
import dev.groundburst.registry.GBTab;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Launching (DESIGN.md §17, a basic launch site built ahead of Phase 9): the gantry Launch Silo, the Launch Computer
 * with its radar, their component blocks, the Basic Missile and the missile in flight; the Launch Complex (Phase 8);
 * the Missile Silo dug into the ground, its concrete and its hatch (Phase 9 slice 9b).
 */
public final class GBLaunch {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(GroundBurst.MODID);

    // --- component blocks (used to build the two structures) ---
    public static final WithItem<MultiblockPartBlock> LAUNCH_PAD = part("launch_pad", "Launch Pad", MapColor.COLOR_GRAY, SoundType.STONE, false);
    public static final WithItem<MultiblockPartBlock> GANTRY_FRAME = part("gantry_frame", "Gantry Frame", MapColor.COLOR_RED, SoundType.METAL, true);
    public static final WithItem<MultiblockPartBlock> GANTRY_ARM = part("gantry_arm", "Gantry Arm", MapColor.COLOR_RED, SoundType.METAL, true);
    public static final WithItem<MultiblockPartBlock> RADAR_MAST = part("radar_mast", "Radar Mast", MapColor.METAL, SoundType.METAL, true);
    public static final WithItem<MultiblockPartBlock> RADAR_DISH = part("radar_dish", "Radar Dish", MapColor.METAL, SoundType.METAL, true);
    /** The Radar Array's crown: one item that places a 3x3 dish. */
    public static final WithItem<LargeRadarDishBlock> LARGE_RADAR_DISH = GBBlocks.register("large_radar_dish", "Large Radar Dish", GBTab.MISSILES,
            LargeRadarDishBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE).noOcclusion());
    public static final WithItem<MultiblockPartBlock> ANTENNA = part("antenna", "Antenna", MapColor.METAL, SoundType.METAL, true);
    /** The Launch Complex's flame trench: the steel wedge under the missile that turns its exhaust out of the trench. */
    public static final WithItem<MultiblockPartBlock> FLAME_DEFLECTOR = part("flame_deflector", "Flame Deflector", MapColor.COLOR_GRAY, SoundType.METAL, false);
    /**
     * The Missile Silo's walls (slice 9b), and a building block in its own right: concrete that shrugs off blasts like
     * obsidian. Not a part: it stays as it is in the silo's shaft.
     */
    public static final WithItem<net.minecraft.world.level.block.Block> REINFORCED_CONCRETE = GBBlocks.register("reinforced_concrete",
            "Reinforced Concrete", GBTab.MISSILES, net.minecraft.world.level.block.Block::new,
            p -> p.mapColor(MapColor.STONE).strength(15f, 1200f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    /** The same striped yellow and black: the Missile Silo's top ring (a part, hidden while formed). */
    public static final WithItem<MultiblockPartBlock> HAZARD_CONCRETE = GBBlocks.register("hazard_concrete", "Hazard Concrete", GBTab.MISSILES,
            MultiblockPartBlock::new, p -> p.mapColor(MapColor.COLOR_YELLOW).strength(15f, 1200f).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE).pushReaction(PushReaction.IMMOVEABLE));
    /**
     * The Cruise Missile Launcher's boxes (slice 9c): an armored box a cruise missile is fired from, two side by side on
     * its back row (a part, hidden while formed: the model draws the boxes).
     */
    public static final WithItem<MultiblockPartBlock> LAUNCH_BOX = part("launch_box", "Launch Box", MapColor.COLOR_GREEN, SoundType.METAL, true);
    /** The Missile Silo's lid: one item that places a 3x3 hatch. */
    public static final WithItem<SiloHatchBlock> SILO_HATCH = GBBlocks.register("silo_hatch", "Silo Hatch", GBTab.MISSILES, SiloHatchBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(15f, 1200f).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK)
                    .pushReaction(PushReaction.IMMOVEABLE));

    // --- controllers ---
    public static final WithItem<MultiblockControllerBlock> SILO_CONTROLLER = GBBlocks.register("launch_silo", "Launch Silo Controller",
            GBTab.MISSILES, p -> new MultiblockControllerBlock(p, GBLaunch.SILO_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<MultiblockControllerBlock> COMPLEX_CONTROLLER = GBBlocks.register("launch_complex", "Launch Complex Controller",
            GBTab.MISSILES, p -> new MultiblockControllerBlock(p, GBLaunch.COMPLEX_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<MultiblockControllerBlock> MISSILE_SILO_CONTROLLER = GBBlocks.register("missile_silo", "Missile Silo Controller",
            GBTab.MISSILES, p -> new MultiblockControllerBlock(p, GBLaunch.MISSILE_SILO_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(5f, 1200f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<MultiblockControllerBlock> CRUISE_CONTROLLER = GBBlocks.register("cruise_launcher", "Cruise Missile Launcher Controller",
            GBTab.MISSILES, p -> new MultiblockControllerBlock(p, GBLaunch.CRUISE_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<LaunchComputerBlock> COMPUTER_CONTROLLER = GBBlocks.register("launch_computer", "Launch Computer",
            GBTab.MISSILES, p -> new LaunchComputerBlock(p, GBLaunch.COMPUTER_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<RadarArrayBlock> RADAR_CONTROLLER = GBBlocks.register("radar_array", "Radar Array",
            GBTab.MISSILES, p -> new RadarArrayBlock(p, GBLaunch.RADAR_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    /** The Launch Control Station (DESIGN.md §17.12): its network's launchers anywhere in the dimension. */
    public static final WithItem<LaunchStationBlock> STATION_CONTROLLER = GBBlocks.register("launch_control_station", "Launch Control Station",
            GBTab.MISSILES, p -> new LaunchStationBlock(p, GBLaunch.STATION_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchStationBlockEntity>> STATION_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("launch_control_station", () -> new BlockEntityType<>(LaunchStationBlockEntity::new,
                    STATION_CONTROLLER.block().get()));
    /** The Laser Designator (DESIGN.md §17.13): lase a spot for laser-guided missiles, or call fire on it. */
    public static final DeferredItem<LaserDesignatorItem> LASER_DESIGNATOR = GBItems.register("laser_designator", "Laser Designator",
            GBTab.MISSILES, LaserDesignatorItem::new);
    /** A station's network's codes: link launchers and radars with them (DESIGN.md §17.12). */
    public static final DeferredItem<LaunchCodesItem> LAUNCH_CODES = GBItems.register("launch_codes", "Launch Codes", GBTab.MISSILES,
            LaunchCodesItem::new, p -> p.stacksTo(16));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarArrayBlockEntity>> RADAR_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("radar_array", () -> new BlockEntityType<>(RadarArrayBlockEntity::new,
                    RADAR_CONTROLLER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchSiloBlockEntity>> SILO_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("launch_silo", () -> new BlockEntityType<>(LaunchSiloBlockEntity::new, SILO_CONTROLLER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchComplexBlockEntity>> COMPLEX_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("launch_complex", () -> new BlockEntityType<>(LaunchComplexBlockEntity::new,
                    COMPLEX_CONTROLLER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileSiloBlockEntity>> MISSILE_SILO_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("missile_silo", () -> new BlockEntityType<>(MissileSiloBlockEntity::new,
                    MISSILE_SILO_CONTROLLER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CruiseLauncherBlockEntity>> CRUISE_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("cruise_launcher", () -> new BlockEntityType<>(CruiseLauncherBlockEntity::new,
                    CRUISE_CONTROLLER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchComputerBlockEntity>> COMPUTER_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("launch_computer", () -> new BlockEntityType<>(LaunchComputerBlockEntity::new,
                    COMPUTER_CONTROLLER.block().get()));

    // --- the missile ---
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> MISSILE = ENTITIES.registerEntityType("missile", MissileEntity::new,
            MobCategory.MISC, b -> b.sized(0.9f, 0.9f).clientTrackingRange(32).updateInterval(1));

    public static final Supplier<SoundEvent> SIREN = GBSounds.LAUNCH_SIREN;
    public static final Supplier<SoundEvent> MISSILE_LAUNCH = GBSounds.MISSILE_LAUNCH;
    public static final Supplier<SoundEvent> MISSILE_ENGINE = GBSounds.MISSILE_ENGINE;
    public static final Supplier<SoundEvent> CRUISE_LAUNCH = GBSounds.CRUISE_LAUNCH;

    /**
     * Wakes a Launch Control Station's member for its launch (its structure's chunks, owned by its controller's
     * position); dropped as the world loads (orders still waiting set them again).
     */
    public static final TicketController NETWORK_TICKETS = new TicketController(GroundBurst.id("launch_network"),
            (level, tickets) -> LaunchNetwork.dropTickets(tickets));

    /** Keeps a missile's chunk, and its target's, loaded while it flies. */
    public static final TicketController TICKETS = new TicketController(GroundBurst.id("missile"), (level, tickets) -> {
        for (var owner : java.util.List.copyOf(tickets.getEntityTickets().keySet())) {
            tickets.removeAllTickets(owner);
        }
    });

    /**
     * The Launch Silo (gantry type): a 3x3 Launch Pad with the controller at the front middle and an Energy Port at the
     * back middle; a Gantry Frame tower 9 tall beside the pad's middle row, its Gantry Arm reaching in at the sixth
     * layer; the missile's column over the pad's middle kept clear 6 blocks up.
     */
    public static final MultiblockDefinition SILO = new MultiblockDefinition(GroundBurst.id("launch_silo"),
            new String[][] {
                    {"PXP ", "PPPG", "PEP "},
                    {"    ", " . G", "    "},
                    {"    ", " . G", "    "},
                    {"    ", " . G", "    "},
                    {"    ", " . G", "    "},
                    {"    ", " . G", "    "},
                    {"    ", " .AG", "    "},
                    {"    ", "   G", "    "},
                    {"    ", "   G", "    "},
            },
            Map.of('P', Entry.of(() -> LAUNCH_PAD.block().get()), 'G', Entry.of(() -> GANTRY_FRAME.block().get()),
                    'A', Entry.of(() -> GANTRY_ARM.block().get()), 'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> SILO_CONTROLLER.block().get())));

    /**
     * The Launch Complex (Phase 8): a 5x5 deck of Launch Pad with the controller at the front middle, an Energy Port at
     * the back middle and the Flame Deflector at its middle, under the missile; a Gantry Frame service tower two cells
     * deep and 13 high beside the deck's middle and back rows, with Gantry Arms (the umbilicals) reaching in at the
     * fifth and ninth layers; the missile's column over the deflector kept clear 11 blocks up. Formed, the deck's
     * blocks take the model's shape (after slice 9b, the user's request): a deck 14 px high you can walk on, the flame
     * trench down its west side and the deflector's steps rising out of it under the missile.
     */
    public static final MultiblockDefinition COMPLEX = new MultiblockDefinition(GroundBurst.id("launch_complex"),
            new String[][] {
                    {"PPXPP ", "PPPPP ", "PPFPPG", "PPPPPG", "PPEPP "},
                    {"      ", "      ", "  .  G", "     G", "      "},
                    {"      ", "      ", "  .  G", "     G", "      "},
                    {"      ", "      ", "  .  G", "     G", "      "},
                    {"      ", "      ", "  . AG", "     G", "      "},
                    {"      ", "      ", "  .  G", "     G", "      "},
                    {"      ", "      ", "  .  G", "     G", "      "},
                    {"      ", "      ", "  .  G", "     G", "      "},
                    {"      ", "      ", "  . AG", "     G", "      "},
                    {"      ", "      ", "  .  G", "     G", "      "},
                    {"      ", "      ", "  .  G", "     G", "      "},
                    {"      ", "      ", "  .  G", "     G", "      "},
                    {"      ", "      ", "     G", "     G", "      "},
            },
            Map.of('P', Entry.of(() -> LAUNCH_PAD.block().get()), 'G', Entry.of(() -> GANTRY_FRAME.block().get()),
                    'A', Entry.of(() -> GANTRY_ARM.block().get()), 'F', Entry.of(() -> FLAME_DEFLECTOR.block().get()),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> COMPLEX_CONTROLLER.block().get())),
            MultiblockDefinition.layerCuts(0, new String[] {"DDCDD", "DDDDD", "TTSDD", "DDDDD", "DDHDD"},
                    Map.of('D', PartCut.DECK, 'T', PartCut.TRENCH_NORTH, 'S', PartCut.STEPS_NORTH, 'C', PartCut.CONSOLE_NORTH,
                            'H', PartCut.HOUSING_NORTH)));

    /**
     * The Missile Silo (Phase 9 slice 9b), dug into the ground: a floor of Reinforced Concrete with the Flame Deflector
     * in its middle; twelve rings of Reinforced Concrete round a 3x3 shaft that must be clear (the missile stands in
     * it); at the top, level with the ground, a ring of Hazard Concrete round a Silo Hatch over the shaft, with the
     * controller in the middle of the ring's front and an Energy Port in the middle of its back.
     */
    public static final MultiblockDefinition MISSILE_SILO = new MultiblockDefinition(GroundBurst.id("missile_silo"), siloLayers(),
            Map.of('R', Entry.of(() -> REINFORCED_CONCRETE.block().get()), 'H', Entry.of(() -> HAZARD_CONCRETE.block().get()),
                    'F', Entry.of(() -> FLAME_DEFLECTOR.block().get()), 'O', hatch(SiloHatchBlock.Part.CENTER), 'o', hatch(SiloHatchBlock.Part.EDGE),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> MISSILE_SILO_CONTROLLER.block().get())));

    private static String[][] siloLayers() {
        String[][] layers = new String[14][];
        layers[0] = new String[] {"RRRRR", "RRRRR", "RRFRR", "RRRRR", "RRRRR"};
        for (int y = 1; y <= 12; y++) {
            layers[y] = new String[] {"RRRRR", "R...R", "R...R", "R...R", "RRRRR"};
        }
        layers[13] = new String[] {"HHXHH", "HoooH", "HoOoH", "HoooH", "HHEHH"};
        return layers;
    }

    /**
     * The Cruise Missile Launcher (slice 9c, DESIGN.md §17.11): a 2x6 deck of Launch Pad, the controller and an Energy
     * Port side by side in its front row; on its back row two Launch Boxes (the boxes' hinged ends); clear over the deck
     * where the boxes lie and sweep as they rise to 45 degrees (tools/assets/cruise_launcher.py: two blocks over all of
     * it, then higher toward the front, five blocks up over its second and third rows). Formed, the deck's blocks are the
     * model's deck, 14 px high, to walk on.
     */
    public static final MultiblockDefinition CRUISE_LAUNCHER = new MultiblockDefinition(GroundBurst.id("cruise_launcher"),
            new String[][] {
                    {"XE", "PP", "PP", "PP", "PP", "PP"},
                    {"..", "..", "..", "..", "..", "BB"},
                    {"..", "..", "..", "..", "..", ".."},
                    {"..", "..", "..", "..", "..", "  "},
                    {"..", "..", "..", "..", "  ", "  "},
                    {"  ", "..", "..", "  ", "  ", "  "},
            },
            Map.of('P', Entry.of(() -> LAUNCH_PAD.block().get()), 'B', Entry.of(() -> LAUNCH_BOX.block().get()),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> CRUISE_CONTROLLER.block().get())),
            MultiblockDefinition.layerCuts(0, new String[] {"DD", "DD", "DD", "DD", "DD", "DD"}, Map.of('D', PartCut.DECK)));

    /** JEI: how the Cruise Missile Launcher is built and works, and what the Cruise Engine makes (DESIGN.md §17.11). */
    public static final String INFO_CRUISE_LAUNCHER = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".info.cruise_launcher",
            "Fires cruise missiles, and only those. Build a 2x6 deck of Launch Pad with the controller and an Energy Port side "
                    + "by side in its front row and two Launch Boxes on its back row; keep the space over the deck clear where its "
                    + "boxes lie and rise (the Holo-Projector shows it). Load two cruise missiles and power it (60,000 FE a "
                    + "missile); a Launch Computer aims, arms and fires it. Its rams raise the boxes during a 10 s countdown, then "
                    + "the two missiles go 3 s apart at the same target, each bursting its box's cover.");
    public static final String INFO_CRUISE_ENGINE = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".info.cruise_engine",
            "A missile assembled with a Cruise Engine (Light and Medium missiles) is a cruise missile, winged and painted as its "
                    + "warhead's own: a booster lights it out of its box, then its jet carries it 40 blocks over the ground, "
                    + "following the ground's rise and fall, for up to 1,500 blocks; short of the target it pops up and dives on "
                    + "it. Only the Cruise Missile Launcher fires it.");

    /** Creative: places a whole Cruise Missile Launcher where you click. */
    public static final net.neoforged.neoforge.registries.DeferredItem<dev.groundburst.content.multiblock.StructureSpawnerItem> CRUISE_SPAWNER =
            GBItems.register("cruise_launcher_spawner", "Structure Spawner: Cruise Missile Launcher", GBTab.MISSILES,
                    p -> new dev.groundburst.content.multiblock.StructureSpawnerItem(p, () -> CRUISE_LAUNCHER),
                    p -> p.stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));

    /** A cell of the Silo Hatch: its middle, or one of the parts round it. */
    private static Entry hatch(SiloHatchBlock.Part part) {
        return new Entry(s -> s.is(SILO_HATCH.block().get()) && s.getValue(SiloHatchBlock.PART) == part,
                () -> SILO_HATCH.block().get().defaultBlockState().setValue(SiloHatchBlock.PART, part));
    }

    /**
     * The Launch Computer: the terminal (controller) with an Energy Port beside it and an Antenna on top; behind it a
     * Radar Mast three high with the Radar Dish on top.
     */
    public static final MultiblockDefinition COMPUTER = new MultiblockDefinition(GroundBurst.id("launch_computer"),
            new String[][] {
                    {"XE", "M "},
                    {"N ", "M "},
                    {"  ", "M "},
                    {"  ", "D "},
            },
            Map.of('M', Entry.of(() -> RADAR_MAST.block().get()), 'D', Entry.of(() -> RADAR_DISH.block().get()),
                    'N', Entry.of(() -> ANTENNA.block().get()), 'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> COMPUTER_CONTROLLER.block().get())));

    /**
     * The Radar Array: a 3x3 deck of Machine Casing with the controller at the front middle and an Energy Port at the
     * back middle; a Radar Mast three high from the deck's middle; on top a Large Radar Dish (its middle over the mast;
     * formed, it turns). Formed, the deck's blocks are the model's deck, 6 px high, with its railings, the cabinet,
     * the power box and the mast's foot (after slice 9b, the user's request).
     */
    public static final MultiblockDefinition RADAR = new MultiblockDefinition(GroundBurst.id("radar_array"),
            new String[][] {
                    {"CXC", "CCC", "CEC"},
                    {"   ", " M ", "   "},
                    {"   ", " M ", "   "},
                    {"   ", " M ", "   "},
                    {"ddd", "dDd", "ddd"},
            },
            Map.of('C', Entry.of(() -> GBMultiblocks.MACHINE_CASING.block().get()), 'M', Entry.of(() -> RADAR_MAST.block().get()),
                    'D', largeDish(LargeRadarDishBlock.Part.CENTER), 'd', largeDish(LargeRadarDishBlock.Part.EDGE),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> RADAR_CONTROLLER.block().get())),
            // The railing runs down the west and east sides and across the back (RAIL_SOUTH is the west one turned
            // round: the east side; CORNER_RAIL_WEST the back-left corner turned: the back-right).
            MultiblockDefinition.layerCuts(0, new String[] {"wcs", "wms", "LPR"},
                    Map.of('w', PartCut.RAIL_NORTH, 's', PartCut.RAIL_SOUTH, 'c', PartCut.CABINET_NORTH, 'm', PartCut.MAST,
                            'L', PartCut.CORNER_RAIL_NORTH, 'R', PartCut.CORNER_RAIL_WEST, 'P', PartCut.POWER_BOX_NORTH)));

    /** A cell of the Large Radar Dish: its middle, or one of the parts round it. */
    private static Entry largeDish(LargeRadarDishBlock.Part part) {
        return new Entry(s -> s.is(LARGE_RADAR_DISH.block().get()) && s.getValue(LargeRadarDishBlock.PART) == part,
                () -> LARGE_RADAR_DISH.block().get().defaultBlockState().setValue(LargeRadarDishBlock.PART, part));
    }

    /**
     * The Launch Control Station: the desk across the front (Machine Casing, the controller in the middle, Machine
     * Casing); behind it the instrument bank, Machine Casing two high with the Energy Port in the middle of its bottom
     * row; an Antenna on top of the bank's middle (the network's uplink).
     */
    public static final MultiblockDefinition STATION = new MultiblockDefinition(GroundBurst.id("launch_control_station"),
            new String[][] {
                    {"CXC", "CEC"},
                    {"   ", "CCC"},
                    {"   ", " N "},
            },
            Map.of('C', Entry.of(() -> GBMultiblocks.MACHINE_CASING.block().get()), 'N', Entry.of(() -> ANTENNA.block().get()),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> STATION_CONTROLLER.block().get())));

    /** JEI's page for the Laser Designator and laser guidance. */
    public static final String INFO_DESIGNATOR = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".jei.info.laser_designator",
            "Hold use to lase: a red beam and spot up to 256 blocks off, seen by everyone, zoomed in your sight with the range "
                    + "and coordinates. Missiles with a Laser Guidance Unit home on the spot as they come down, if it is within 48 "
                    + "blocks (and 5% of their flight) of where they were aimed, from a designator on their launcher's network (or "
                    + "yours, from a launcher on none). Use it on a Launch Control Station to join its network: the station's map "
                    + "shows the spot and its To laser button aims at it. Sneak-use for call for fire: hold the spot steady for two "
                    + "seconds and every armed launcher of the network that reaches it fires on it. 40,000 FE (100 s of lasing); "
                    + "charge it in a Battery Charger. Smoke breaks the beam; an EMP knocks it out.");

    /** JEI's page for the Launch Control Station and its codes. */
    public static final String INFO_STATION = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".jei.info.launch_control_station",
            "The Launch Control Station runs your launchers anywhere in the dimension, loaded or not. Build it 3 wide, 2 deep "
                    + "and 3 high: a front row of Machine Casing with the controller in the middle (the desk); behind it Machine "
                    + "Casing two high with the Energy Port in the middle of the bottom row (the instrument bank); an Antenna on top "
                    + "of the bank's middle. Take Launch Codes from it (use them on the station), then use them on each launcher's "
                    + "controller or any part of it (Launch Silo, Launch Complex, Missile Silo, Cruise Missile Launcher, SCUD Storm, "
                    + "Nuclear Missile) and on Radar Arrays to link them; sneak to unlink. Its map shows the whole dimension: pick "
                    + "launchers, aim them (several take a pattern), ARM and LAUNCH, at once or timed so they all land together. "
                    + "Unloaded launchers are woken to launch; they need their energy stored. 40 FE/t.");

    /** Creative: places a whole Launch Control Station. */
    public static final DeferredItem<dev.groundburst.content.multiblock.StructureSpawnerItem> STATION_SPAWNER =
            GBItems.register("launch_control_station_spawner", "Structure Spawner: Launch Control Station", GBTab.MISSILES,
                    p -> new dev.groundburst.content.multiblock.StructureSpawnerItem(p, () -> STATION),
                    p -> p.stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));

    /** Creative: places a whole Missile Silo dug into the ground where you click (its top level with what you clicked). */
    public static final net.neoforged.neoforge.registries.DeferredItem<dev.groundburst.content.multiblock.StructureSpawnerItem> MISSILE_SILO_SPAWNER =
            GBItems.register("missile_silo_spawner", "Structure Spawner: Missile Silo", GBTab.MISSILES,
                    p -> new dev.groundburst.content.multiblock.StructureSpawnerItem(p, () -> MISSILE_SILO, true),
                    p -> p.stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));

    private GBLaunch() {}

    public static void init() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent event) -> {
            event.register(TICKETS);
            event.register(NETWORK_TICKETS);
        });
        // The Laser Designator's charge, filled by the Battery Charger (or any mod's charger) like a Battery's.
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> event.registerItem(
                net.neoforged.neoforge.capabilities.Capabilities.Energy.ITEM,
                (stack, access) -> new net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler(access,
                        dev.groundburst.registry.GBDataComponents.ENERGY.get(), LaserDesignatorItem.CAPACITY, LaserDesignatorItem.TRANSFER),
                LASER_DESIGNATOR.get()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            Launches.clear();
            Designators.clear();
            LaserDesignatorItem.clear();
            dev.groundburst.content.defence.Interceptors.clear();
        });
        // Missiles in the air move on every tick, loaded or not (Flights).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                Flights.of(level).tick(level);
                dev.groundburst.content.defence.Interceptors.tick(level);   // after the missiles: they meet them where they are
                TerrainSurvey.of(level).tick(level);
                LaunchNetwork.of(level).tick(level);          // the stations' networks: orders, waking, missiles followed
            }
        });
        // The terrain survey hears about chunks coming and going (it maps those near a Launch Computer).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                TerrainSurvey.of(level).chunkLoaded(e.getChunk().getPos().pack());
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Unload e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                    && e.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
                TerrainSurvey.of(level).chunkUnloading(level, chunk);
            }
        });
    }

    private static WithItem<MultiblockPartBlock> part(String id, String english, MapColor color, SoundType sound, boolean see) {
        return GBBlocks.register(id, english, GBTab.MISSILES, MultiblockPartBlock::new, p -> {
            p.mapColor(color).strength(4f, 8f).requiresCorrectToolForDrops().sound(sound).pushReaction(PushReaction.IMMOVEABLE);
            return see ? p.noOcclusion() : p;
        });
    }
}
