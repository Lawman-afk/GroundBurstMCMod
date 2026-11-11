package dev.groundburst.content.command;

import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.machine.GeoBlockItem;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockDefinition.Entry;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The war room (DESIGN.md §17.17): the Seismograph, the War Room Display (and its Display Panels) and the Dead Hand (and
 * its Command Rocket), on the Launch Control Station's networks; and the ground carrying every explosion to them
 * ({@link Seismic}: the game's own and other mods' explosions are heard here).
 */
public final class GBCommand {
    // --- the Seismograph --------------------------------------------------------------------------------------------------
    public static final WithItem<SeismographBlock> SEISMOGRAPH = GBBlocks.registerWithItem("seismograph", "Seismograph", GBTab.MISSILES,
            p -> new SeismographBlock(p, GBCommand.SEISMOGRAPH_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(3.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion(),
            (block, props) -> new GeoBlockItem(block, props, GroundBurst.id("seismograph")));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeismographBlockEntity>> SEISMOGRAPH_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("seismograph", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(SeismographBlockEntity::new,
                    SEISMOGRAPH.block().get()).build(null));

    // --- the War Room Display ---------------------------------------------------------------------------------------------
    /** Its screen's blocks: a dark glass panel in a steel bezel (a part: formed, the display's model draws one big screen). */
    public static final WithItem<MultiblockPartBlock> DISPLAY_PANEL = GBBlocks.register("display_panel", "Display Panel", GBTab.MISSILES,
            MultiblockPartBlock::new, p -> p.mapColor(MapColor.COLOR_BLACK).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK));
    public static final WithItem<WarRoomDisplayBlock> WAR_ROOM_CONTROLLER = GBBlocks.register("war_room_display", "War Room Display",
            GBTab.MISSILES, p -> new WarRoomDisplayBlock(p, GBCommand.WAR_ROOM_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.BLOCK));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarRoomDisplayBlockEntity>> WAR_ROOM_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("war_room_display", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(WarRoomDisplayBlockEntity::new,
                    WAR_ROOM_CONTROLLER.block().get()).build(null));

    /**
     * The War Room Display: seven wide and five high, one deep, against a wall: a bottom row of Machine Casing with the
     * controller in its middle and an Energy Port two to its right (as you face it), three rows of seven Display Panels, a
     * row of Machine Casing on top.
     */
    public static final MultiblockDefinition WAR_ROOM = new MultiblockDefinition(GroundBurst.id("war_room_display"),
            new String[][] {
                    {"CECXCCC"},
                    {"SSSSSSS"},
                    {"SSSSSSS"},
                    {"SSSSSSS"},
                    {"CCCCCCC"},
            },
            Map.of('C', Entry.of(() -> GBMultiblocks.MACHINE_CASING.block().get()), 'S', Entry.of(() -> DISPLAY_PANEL.block().get()),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> WAR_ROOM_CONTROLLER.block().get())));
    public static final DeferredItem<StructureSpawnerItem> WAR_ROOM_SPAWNER = GBItems.register("war_room_display_spawner",
            "Structure Spawner: War Room Display", GBTab.MISSILES, p -> new StructureSpawnerItem(p, () -> WAR_ROOM),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    // --- the Dead Hand ----------------------------------------------------------------------------------------------------
    /** Its controller: the console, hardened as its shell is. */
    public static final WithItem<DeadHandBlock> DEAD_HAND_CONTROLLER = GBBlocks.register("dead_hand", "Dead Hand", GBTab.MISSILES,
            p -> new DeadHandBlock(p, GBCommand.DEAD_HAND_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(5f, 1200f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.BLOCK));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeadHandBlockEntity>> DEAD_HAND_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("dead_hand", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(DeadHandBlockEntity::new,
                    DEAD_HAND_CONTROLLER.block().get()).build(null));
    /** Its round: a small rocket carrying a transmitter. */
    public static final DeferredItem<CommandRocketItem> COMMAND_ROCKET = GBItems.register("command_rocket", "Command Rocket", GBTab.MISSILES,
            CommandRocketItem::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    /**
     * The Dead Hand: 3 wide, 2 deep and 2 high of Hazard Concrete, the controller in the middle of the front, an Energy
     * Port in the middle of the back row's bottom, two Missile Canisters standing in the back-right column (the command
     * rocket's tube), an Antenna on the roof's front-left corner (the sensor mast).
     */
    public static final MultiblockDefinition DEAD_HAND = new MultiblockDefinition(GroundBurst.id("dead_hand"),
            new String[][] {
                    {"HXH", "KEH"},
                    {"HHH", "KHH"},
                    {"  N", "   "},
            },
            Map.of('H', Entry.of(() -> GBLaunch.HAZARD_CONCRETE.block().get()), 'K', Entry.of(() -> GBLaunch.MISSILE_CANISTER.block().get()),
                    'N', Entry.of(() -> GBLaunch.ANTENNA.block().get()), 'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> DEAD_HAND_CONTROLLER.block().get())));
    public static final DeferredItem<StructureSpawnerItem> DEAD_HAND_SPAWNER = GBItems.register("dead_hand_spawner",
            "Structure Spawner: Dead Hand", GBTab.MISSILES, p -> new StructureSpawnerItem(p, () -> DEAD_HAND),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    // --- JEI's pages -------------------------------------------------------------------------------------------------------
    public static final String INFO_SEISMOGRAPH = GBLang.add(GroundBurst.MODID + ".jei.info.seismograph",
            "The Seismograph records every blast it feels: its magnitude, how far off it was and which way, and the yield worked back "
                    + "from the magnitude, as one station can tell them. A grenade is felt within about 50 blocks, a tactical nuke within "
                    + "3 km, anything thermonuclear anywhere. Its screen is the drum's paper and its record; set what it records, an "
                    + "alarm (a Blast alert on its alarm channel) and its channel. A comparator reads the last arrival's magnitude. Link it "
                    + "to a Launch Control Station's network with Launch Codes: blasts two or more of its seismographs feel are fixed on "
                    + "the station's map and the War Room Display. 10 FE/t.");
    public static final String INFO_WAR_ROOM = GBLang.add(GroundBurst.MODID + ".jei.info.war_room_display",
            "The War Room Display draws a launch network's whole picture on a wall screen seven blocks wide for the room: the "
                    + "terrain, the members, every missile its launchers fired and every one its radars track, impacts, the blasts its "
                    + "seismographs felt, the counts, the log and the alert condition. Build it one block deep against a wall: a bottom "
                    + "row of seven Machine Casing with the controller in the middle and an Energy Port two to its right, three rows of "
                    + "seven Display Panels, a row of Machine Casing on top. Use Launch Codes on it to show their network; click it to "
                    + "watch its board up close (drag to look round, scroll to zoom), set what the wall shows, or put your view on the "
                    + "wall. 60 FE/t.");
    public static final String INFO_DEAD_HAND = GBLang.add(GroundBurst.MODID + ".jei.info.dead_hand",
            "The Dead Hand retaliates when a strike lands. Build it of Hazard Concrete 3 wide, 2 deep and 2 high: the controller in "
                    + "the middle of the front, an Energy Port in the middle of the back row's bottom, two Missile Canisters standing in "
                    + "the back-right column, an Antenna on the roof's front-left corner. Link it with Launch Codes, load a Command "
                    + "Rocket and arm it at its console (both keys). A strike of its magnitude within its radius that its network did not "
                    + "fire puts it in alert and readies every launcher of the network: aimed (at its own aim, or back where the strike "
                    + "came from), armed and counted down to wait at its last second. HOLD (or redstone into it) stands them all down. At "
                    + "zero, if no station of its network answers (or on any strike), its rocket flies a course over the network's "
                    + "launchers, and each fires as its signal reaches it (256 blocks round it, more as it climbs, to 480). An EMP does "
                    + "not stop it. 80 FE/t.");

    private GBCommand() {}

    /** The ground hears the game's own explosions and other mods' (NeoForge's explosion event). */
    public static void init() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ExplosionEvent.Detonate e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                Seismic.explosion(level, e.getExplosion().center(), e.getExplosion().radius());
            }
        });
    }
}
