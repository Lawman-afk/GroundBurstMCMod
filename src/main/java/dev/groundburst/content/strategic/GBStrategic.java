package dev.groundburst.content.strategic;

import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockControllerBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockDefinition.Entry;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Whole weapons (DESIGN.md §15.2, Phase 8 slice 8c): the ICBM Assembly Hall, a long crane hall that builds a
 * thermonuclear or cobalt warhead into an intercontinental missile all at once, for the Launch Complex; and the parts
 * only it uses. And (DESIGN.md §16.15) the Special Weapons Assembler, a containment bay that builds the exotic weapons'
 * intercontinental missiles from their cores.
 */
public final class GBStrategic {
    /** The hall's two long girders: its travelling crane runs along them. */
    public static final WithItem<MultiblockPartBlock> CRANE_RAIL = GBBlocks.register("crane_rail", "Crane Rail", GBTab.MISSILES,
            MultiblockPartBlock::new, p -> p.mapColor(MapColor.COLOR_YELLOW).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE).noOcclusion());
    public static final WithItem<MultiblockControllerBlock> HALL_CONTROLLER = GBBlocks.register("strategic_assembly_hall",
            "ICBM Assembly Hall", GBTab.MISSILES, p -> new MultiblockControllerBlock(p, GBStrategic.HALL_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StrategicAssemblyBlockEntity>> HALL_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("strategic_assembly_hall", () -> new BlockEntityType<>(StrategicAssemblyBlockEntity::new,
                    HALL_CONTROLLER.block().get()));
    /** The Special Weapons Assembler (DESIGN.md §16.15): the exotic weapons' own assembler, a containment bay. */
    public static final WithItem<MultiblockControllerBlock> SPECIAL_CONTROLLER = GBBlocks.register("special_weapons_assembler",
            "Special Weapons Assembler", GBTab.MISSILES, p -> new MultiblockControllerBlock(p, GBStrategic.SPECIAL_ENTITY),
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpecialWeaponsBlockEntity>> SPECIAL_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("special_weapons_assembler", () -> new BlockEntityType<>(SpecialWeaponsBlockEntity::new,
                    SPECIAL_CONTROLLER.block().get()));

    // --- the parts only the hall uses ---
    public static final DeferredItem<StrategicPart> FUSION_SECONDARY = part("fusion_secondary", "Fusion Secondary",
            "The second stage: lithium deuteride round a plutonium spark plug, in a uranium tamper");
    public static final DeferredItem<StrategicPart> COBALT_JACKET = part("cobalt_jacket", "Cobalt-59 Jacket",
            "Optional: wrapped round the secondary, it salts the fallout with cobalt");
    public static final DeferredItem<StrategicPart> INTERCONTINENTAL_BOOSTER = part("intercontinental_booster", "Intercontinental Booster",
            "Three solid-fuel stages: 3,000 blocks");
    /** Slice 8d: the Tsar's third stage. */
    public static final DeferredItem<StrategicPart> TERTIARY_STAGE = part("tertiary_stage", "Tertiary Stage",
            "In place of a jacket: a third fusion stage in a lead radiation channel, for a Tsar Mega-bomb");
    /** The Tsar's primary (after the user's look at 9b): one boosted core is too small a spark for three stages. */
    public static final DeferredItem<StrategicPart> TWIN_BOOSTED_CORE = part("twin_boosted_core", "Twin Boosted Core",
            "The Tsar's primary: two boosted fission cores fired together");

    /**
     * The ICBM Assembly Hall: a 7x13 floor of Launch Pad (the controller at the front middle; an Energy Port at the
     * back middle, an Item Port two to its left), six Gantry Frame columns four high at the corners and halfway along each
     * long side, and a Crane Rail the length of each long side on top of them; between them the hall kept clear for the
     * missile on its cradles and the crane.
     */
    public static final MultiblockDefinition HALL = new MultiblockDefinition(GroundBurst.id("strategic_assembly_hall"), hallLayers(),
            Map.of('P', Entry.of(() -> GBLaunch.LAUNCH_PAD.block().get()), 'G', Entry.of(() -> GBLaunch.GANTRY_FRAME.block().get()),
                    'R', Entry.of(() -> CRANE_RAIL.block().get()), 'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()),
                    'I', Entry.of(GBMultiblocks.ITEM_PORT.block()), MultiblockDefinition.CONTROLLER, Entry.of(() -> HALL_CONTROLLER.block().get())));

    /** The rows (from the front) the Special Weapons Assembler's containment rings stand across. */
    public static final int[] RING_ROWS = {3, 6, 9};
    /**
     * The Special Weapons Assembler (DESIGN.md §16.15): a 7x13 floor of Launch Pad as the hall's (the controller at the
     * front middle; an Energy Port at the back middle, an Item Port two to its left) and three containment rings across
     * it, at the fourth, seventh and tenth rows: each a square of eight Toroidal Field Coils three blocks wide and high
     * round the missile's bay, its middle kept clear. Between the rings the bay is kept clear five wide and three high for
     * the missile on its cradles.
     */
    public static final MultiblockDefinition SPECIAL = new MultiblockDefinition(GroundBurst.id("special_weapons_assembler"), specialLayers(),
            Map.of('P', Entry.of(() -> GBLaunch.LAUNCH_PAD.block().get()), 'T', Entry.of(() -> GBMultiblocks.TOROIDAL_FIELD_COIL.block().get()),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()), 'I', Entry.of(GBMultiblocks.ITEM_PORT.block()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> SPECIAL_CONTROLLER.block().get())));
    private GBStrategic() {}

    /** Loads the registrations. */
    public static void init() {
        StrategicAssembly.Problem.values();
        SpecialWeapons.Problem.values();
    }

    private static String[][] specialLayers() {
        String[][] layers = new String[4][13];
        for (int row = 0; row < 13; row++) {
            layers[0][row] = row == 0 ? "PPPXPPP" : row == 12 ? "PIPEPPP" : "PPPPPPP";
            final int at = row;
            boolean ring = java.util.Arrays.stream(RING_ROWS).anyMatch(r -> r == at);
            // (the block over the Item Port, in the back row, is free: a pipe may stand on it)
            String bay = row == 12 ? "  .... " : " ..... ";
            layers[1][row] = ring ? " .TTT. " : bay;
            layers[2][row] = ring ? " .T.T. " : bay;
            layers[3][row] = ring ? " .TTT. " : bay;
        }
        return layers;
    }

    private static String[][] hallLayers() {
        String[][] layers = new String[6][13];
        for (int row = 0; row < 13; row++) {
            layers[0][row] = row == 0 ? "PPPXPPP" : row == 12 ? "PIPEPPP" : "PPPPPPP";
            // (the column over the Item Port, in the back row, is free: a pipe may stand on it)
            for (int y = 1; y <= 4; y++) {
                layers[y][row] = row == 12 ? "G ....G" : row % 6 == 0 ? "G.....G" : " ..... ";
            }
            layers[5][row] = row == 12 ? "R ....R" : "R.....R";
        }
        return layers;
    }

    private static DeferredItem<StrategicPart> part(String id, String english, String tip) {
        String key = GBLang.add(GroundBurst.MODID + ".strategic.tip." + id, tip);
        return GBItems.register(id, english, GBTab.MISSILES, p -> new StrategicPart(p, key));
    }
}
