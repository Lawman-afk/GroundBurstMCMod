package dev.groundburst.content.assembler;

import java.util.List;
import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockControllerBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockDefinition.Entry;
import dev.groundburst.content.multiblock.PartCut;
import dev.groundburst.content.multiblock.StructureSpawnerItem;
import dev.groundburst.content.strategic.GBStrategic;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The missile assemblers (DESIGN.md §10.4): one multiblock for each class of missile below the Strategic Assembly
 * Hall's, each building the missiles of its own class's warheads. Built of the launch site's blocks (Launch Pad decks,
 * Gantry Frame, Crane Rail) round a controller, with an Energy Port and an Item Port in the deck.
 */
public final class GBAssemblers {
    public static final WithItem<MultiblockControllerBlock> LIGHT_CONTROLLER = controller(AssemblerClass.LIGHT, () -> GBAssemblers.LIGHT_ENTITY);
    public static final WithItem<MultiblockControllerBlock> MEDIUM_CONTROLLER = controller(AssemblerClass.MEDIUM, () -> GBAssemblers.MEDIUM_ENTITY);
    public static final WithItem<MultiblockControllerBlock> HEAVY_CONTROLLER = controller(AssemblerClass.HEAVY, () -> GBAssemblers.HEAVY_ENTITY);
    public static final WithItem<MultiblockControllerBlock> STRATEGIC_CONTROLLER = controller(AssemblerClass.STRATEGIC,
            () -> GBAssemblers.STRATEGIC_ENTITY);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileAssemblerBlockEntity>> LIGHT_ENTITY =
            entity(AssemblerClass.LIGHT, () -> GBAssemblers.LIGHT_ENTITY, LIGHT_CONTROLLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileAssemblerBlockEntity>> MEDIUM_ENTITY =
            entity(AssemblerClass.MEDIUM, () -> GBAssemblers.MEDIUM_ENTITY, MEDIUM_CONTROLLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileAssemblerBlockEntity>> HEAVY_ENTITY =
            entity(AssemblerClass.HEAVY, () -> GBAssemblers.HEAVY_ENTITY, HEAVY_CONTROLLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileAssemblerBlockEntity>> STRATEGIC_ENTITY =
            entity(AssemblerClass.STRATEGIC, () -> GBAssemblers.STRATEGIC_ENTITY, STRATEGIC_CONTROLLER);

    /**
     * The deck's cuts: the Launch Pad decks and the Item Port 14 px high, the controller's console, the Energy Port's
     * housing along the outer edge of its cell (the back, or {@code h} the right-hand side).
     */
    private static final Map<Character, PartCut> DECK = Map.of('D', PartCut.DECK, 'C', PartCut.CONSOLE_NORTH, 'H', PartCut.HOUSING_NORTH,
            'h', PartCut.HOUSING_WEST);

    /**
     * The Light Missile Assembler: a 3x6 deck of Launch Pad (the controller at the front middle, the Item Port and Energy
     * Port either side of the fifth row); an A-frame of Gantry Frame at each end, two legs a side leaning in to the Crane
     * Rail hoist beam that runs the deck's length three blocks up; the missile's bay under the beam kept clear.
     */
    public static final MultiblockDefinition LIGHT = new MultiblockDefinition(GroundBurst.id(AssemblerClass.LIGHT.id),
            new String[][] {
                    {"PXP", "PPP", "PPP", "PPP", "IPE", "PPP"},
                    {"F F", " . ", " . ", " . ", " . ", "F.F"},
                    {"F F", " . ", " . ", " . ", " . ", "F F"},
                    {" R ", " R ", " R ", " R ", " R ", " R "},
            },
            legend(LIGHT_CONTROLLER),
            MultiblockDefinition.layerCuts(0, new String[] {"DCD", "DDD", "DDD", "DDD", "DDh", "DDD"}, DECK));

    /**
     * The Medium Missile Assembler: a 5x7 deck of Launch Pad (the controller at the front middle, the Item Port and
     * Energy Port in the back row); the slewing jib crane's post, Gantry Frame four high, in the middle of the right
     * side; the missile's bay down the middle three columns kept clear two blocks up.
     */
    public static final MultiblockDefinition MEDIUM = new MultiblockDefinition(GroundBurst.id(AssemblerClass.MEDIUM.id),
            new String[][] {
                    {"PPXPP", "PPPPP", "PPPPP", "PPPPP", "PPPPP", "PPPPP", "PIPEP"},
                    {" ... ", " ... ", " ... ", " ...F", " ... ", " ... ", " ... "},
                    {" ... ", " ... ", " ... ", " ...F", " ... ", " ... ", " ... "},
                    {"     ", "     ", "     ", "    F", "     ", "     ", "     "},
                    {"     ", "     ", "     ", "    F", "     ", "     ", "     "},
            },
            legend(MEDIUM_CONTROLLER),
            MultiblockDefinition.layerCuts(0, new String[] {"DDCDD", "DDDDD", "DDDDD", "DDDDD", "DDDDD", "DDDDD", "DDDHD"}, DECK));

    /**
     * The Heavy Missile Assembler: a 5x9 deck, its outer columns Crane Rail (the gantry crane's track) and the rest
     * Launch Pad (the controller at the front middle, the Item Port and Energy Port in the back row); the crane parked
     * over the back row, a Gantry Frame leg four high on each rail and a Crane Rail bridge across the top; everything
     * over the deck in front of it kept clear for the crane to run and the missile to lie.
     */
    public static final MultiblockDefinition HEAVY = new MultiblockDefinition(GroundBurst.id(AssemblerClass.HEAVY.id), heavyLayers(),
            legend(HEAVY_CONTROLLER),
            MultiblockDefinition.layerCuts(0, new String[] {"DDCDD", "DDDDD", "DDDDD", "DDDDD", "DDDDD", "DDDDD", "DDDDD", "DDDDD", "DDDHD"},
                    DECK));

    /**
     * The Strategic Missile Assembler: a 5x5 deck of Launch Pad (the controller at the front middle, the Item Port and
     * Energy Port in the back row); a Gantry Frame column eleven high at each corner, carrying a Crane Rail girder down
     * each side and one across the middle (the hoist's beam) on top; the tower's middle kept clear for the missile
     * standing in it and the work platforms that fold in round it.
     */
    public static final MultiblockDefinition STRATEGIC = new MultiblockDefinition(GroundBurst.id(AssemblerClass.STRATEGIC.id), strategicLayers(),
            legend(STRATEGIC_CONTROLLER),
            MultiblockDefinition.layerCuts(0, new String[] {"DDCDD", "DDDDD", "DDDDD", "DDDDD", "DDDHD"}, DECK));

    // --- creative: the Structure Spawners ---
    public static final DeferredItem<StructureSpawnerItem> LIGHT_SPAWNER = spawner(AssemblerClass.LIGHT, () -> LIGHT);
    public static final DeferredItem<StructureSpawnerItem> MEDIUM_SPAWNER = spawner(AssemblerClass.MEDIUM, () -> MEDIUM);
    public static final DeferredItem<StructureSpawnerItem> HEAVY_SPAWNER = spawner(AssemblerClass.HEAVY, () -> HEAVY);
    public static final DeferredItem<StructureSpawnerItem> STRATEGIC_SPAWNER = spawner(AssemblerClass.STRATEGIC, () -> STRATEGIC);
    /** (The hall had none: it gets one alongside.) */
    public static final DeferredItem<StructureSpawnerItem> HALL_SPAWNER = GBItems.register("strategic_assembly_hall_spawner",
            "Structure Spawner: Strategic Assembly Hall", GBTab.MISSILES, p -> new StructureSpawnerItem(p, () -> GBStrategic.HALL),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    private GBAssemblers() {}

    /** Loads the registrations. */
    public static void init() {
        AssemblerClass.values();
    }

    public static List<MultiblockDefinition> definitions() {
        return List.of(LIGHT, MEDIUM, HEAVY, STRATEGIC);
    }

    public static MultiblockDefinition definition(AssemblerClass c) {
        return switch (c) {
            case LIGHT -> LIGHT;
            case MEDIUM -> MEDIUM;
            case HEAVY -> HEAVY;
            case STRATEGIC -> STRATEGIC;
        };
    }

    public static WithItem<MultiblockControllerBlock> controller(AssemblerClass c) {
        return switch (c) {
            case LIGHT -> LIGHT_CONTROLLER;
            case MEDIUM -> MEDIUM_CONTROLLER;
            case HEAVY -> HEAVY_CONTROLLER;
            case STRATEGIC -> STRATEGIC_CONTROLLER;
        };
    }

    public static BlockEntityType<MissileAssemblerBlockEntity> entity(AssemblerClass c) {
        return (switch (c) {
            case LIGHT -> LIGHT_ENTITY;
            case MEDIUM -> MEDIUM_ENTITY;
            case HEAVY -> HEAVY_ENTITY;
            case STRATEGIC -> STRATEGIC_ENTITY;
        }).get();
    }

    private static String[][] heavyLayers() {
        String[][] layers = new String[6][9];
        for (int row = 0; row < 9; row++) {
            layers[0][row] = row == 0 ? "RPXPR" : row == 8 ? "RIPER" : "RPPPR";
            for (int y = 1; y <= 4; y++) {
                layers[y][row] = row == 8 ? "F...F" : ".....";
            }
            layers[5][row] = row == 8 ? "RRRRR" : ".....";
        }
        return layers;
    }

    private static String[][] strategicLayers() {
        String[][] layers = new String[13][];
        layers[0] = new String[] {"PPXPP", "PPPPP", "PPPPP", "PPPPP", "PIPEP"};
        for (int y = 1; y <= 11; y++) {
            layers[y] = new String[] {"F   F", " ... ", " ... ", " ... ", "F   F"};
        }
        layers[12] = new String[] {"R   R", "R   R", "RRRRR", "R   R", "R   R"};
        return layers;
    }

    /** Every assembler's legend: Launch Pad, Gantry Frame, Crane Rail, the two ports and its own controller. */
    private static Map<Character, Entry> legend(WithItem<MultiblockControllerBlock> controller) {
        return Map.of('P', Entry.of(() -> GBLaunch.LAUNCH_PAD.block().get()), 'F', Entry.of(() -> GBLaunch.GANTRY_FRAME.block().get()),
                'R', Entry.of(() -> GBStrategic.CRANE_RAIL.block().get()), 'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()),
                'I', Entry.of(GBMultiblocks.ITEM_PORT.block()), MultiblockDefinition.CONTROLLER, Entry.of(() -> controller.block().get()));
    }

    private static WithItem<MultiblockControllerBlock> controller(AssemblerClass c,
            java.util.function.Supplier<DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileAssemblerBlockEntity>>> type) {
        return GBBlocks.register(c.id, c.english, GBTab.MISSILES, p -> new MultiblockControllerBlock(p, () -> type.get().get()),
                p -> p.mapColor(MapColor.METAL).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                        .pushReaction(PushReaction.IMMOVEABLE));
    }

    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileAssemblerBlockEntity>> entity(AssemblerClass c,
            java.util.function.Supplier<DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileAssemblerBlockEntity>>> self,
            WithItem<MultiblockControllerBlock> block) {
        return GBBlockEntities.BLOCK_ENTITIES.register(c.id, () -> new BlockEntityType<>(
                (pos, state) -> new MissileAssemblerBlockEntity(self.get().get(), pos, state, c), block.block().get()));
    }

    private static DeferredItem<StructureSpawnerItem> spawner(AssemblerClass c, java.util.function.Supplier<MultiblockDefinition> definition) {
        return GBItems.register(c.id + "_spawner", "Structure Spawner: " + c.english, GBTab.MISSILES, p -> new StructureSpawnerItem(p, definition),
                p -> p.stacksTo(1).rarity(Rarity.EPIC));
    }
}
