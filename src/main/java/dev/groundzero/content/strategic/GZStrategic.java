package dev.groundzero.content.strategic;

import java.util.Map;

import dev.groundzero.GroundZero;
import dev.groundzero.content.launch.GZLaunch;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockControllerBlock;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.MultiblockDefinition.Entry;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZBlocks;
import dev.groundzero.registry.GZBlocks.WithItem;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZLang;
import dev.groundzero.registry.GZTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Whole weapons (DESIGN.md §15.2, Phase 8 slice 8c): the Strategic Assembly Hall, a long crane hall that builds a
 * thermonuclear or cobalt warhead into an intercontinental missile all at once, for the Launch Complex; and the parts
 * only it uses.
 */
public final class GZStrategic {
    /** The hall's two long girders: its travelling crane runs along them. */
    public static final WithItem<MultiblockPartBlock> CRANE_RAIL = GZBlocks.register("crane_rail", "Crane Rail", GZTab.MISSILES,
            MultiblockPartBlock::new, p -> p.mapColor(MapColor.COLOR_YELLOW).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE).noOcclusion());
    public static final WithItem<MultiblockControllerBlock> HALL_CONTROLLER = GZBlocks.register("strategic_assembly_hall",
            "Strategic Assembly Hall", GZTab.MISSILES, p -> new MultiblockControllerBlock(p, GZStrategic.HALL_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StrategicAssemblyBlockEntity>> HALL_ENTITY =
            GZBlockEntities.BLOCK_ENTITIES.register("strategic_assembly_hall", () -> new BlockEntityType<>(StrategicAssemblyBlockEntity::new,
                    HALL_CONTROLLER.block().get()));

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

    /**
     * The Strategic Assembly Hall: a 7x13 floor of Launch Pad (the controller at the front middle; an Energy Port at the
     * back middle, an Item Port two to its left), six Gantry Frame columns four high at the corners and halfway along each
     * long side, and a Crane Rail the length of each long side on top of them; between them the hall kept clear for the
     * missile on its cradles and the crane.
     */
    public static final MultiblockDefinition HALL = new MultiblockDefinition(GroundZero.id("strategic_assembly_hall"), hallLayers(),
            Map.of('P', Entry.of(() -> GZLaunch.LAUNCH_PAD.block().get()), 'G', Entry.of(() -> GZLaunch.GANTRY_FRAME.block().get()),
                    'R', Entry.of(() -> CRANE_RAIL.block().get()), 'E', Entry.of(() -> GZMultiblocks.ENERGY_PORT.block().get()),
                    'I', Entry.of(GZMultiblocks.ITEM_PORT.block()), MultiblockDefinition.CONTROLLER, Entry.of(() -> HALL_CONTROLLER.block().get())));

    private GZStrategic() {}

    /** Loads the registrations. */
    public static void init() {
        StrategicAssembly.Problem.values();
    }

    private static String[][] hallLayers() {
        String[][] layers = new String[6][13];
        for (int row = 0; row < 13; row++) {
            layers[0][row] = row == 0 ? "PPPXPPP" : row == 12 ? "PIPEPPP" : "PPPPPPP";
            for (int y = 1; y <= 4; y++) {
                layers[y][row] = row % 6 == 0 ? "G.....G" : " ..... ";
            }
            layers[5][row] = "R.....R";
        }
        return layers;
    }

    private static DeferredItem<StrategicPart> part(String id, String english, String tip) {
        String key = GZLang.add(GroundZero.MODID + ".strategic.tip." + id, tip);
        return GZItems.register(id, english, GZTab.MISSILES, p -> new StrategicPart(p, key));
    }
}
