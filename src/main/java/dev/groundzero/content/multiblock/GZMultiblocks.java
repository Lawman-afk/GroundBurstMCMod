package dev.groundzero.content.multiblock;

import java.util.Map;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.multiblock.MultiblockDefinition.Entry;
import dev.groundzero.registry.GZBlocks;
import dev.groundzero.registry.GZBlocks.WithItem;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZTab;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredItem;

/** Structural blocks and multiblock patterns (DESIGN.md §23). */
public final class GZMultiblocks {
    public static final WithItem<MultiblockPartBlock> MACHINE_CASING = GZBlocks.register("machine_casing", "Machine Casing", GZTab.MACHINES,
            MultiblockPartBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<GlassPartBlock> REACTOR_GLASS = GZBlocks.register("reactor_glass", "Reactor Glass", GZTab.MACHINES,
            GlassPartBlock::new, p -> p.mapColor(MapColor.COLOR_GRAY).strength(3f, 12f).requiresCorrectToolForDrops().sound(SoundType.GLASS)
                    .noOcclusion().isViewBlocking((s, l, pos, box) -> false).isSuffocating((s, l, pos) -> false).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<PortBlock> ITEM_PORT = GZBlocks.register("item_port", "Item Port", GZTab.MACHINES,
            PortBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));

    // Slice 7 structural blocks.
    public static final WithItem<MultiblockPartBlock> LEAD_LINED_CASING = part("lead_lined_casing", "Lead-Lined Casing", MapColor.COLOR_GRAY);
    public static final WithItem<FluidPortBlock> FLUID_PORT = GZBlocks.register("fluid_port", "Fluid Port", GZTab.MACHINES,
            FluidPortBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<EnergyPortBlock> ENERGY_PORT = GZBlocks.register("energy_port", "Energy Port", GZTab.MACHINES,
            EnergyPortBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<MultiblockPartBlock> STEEL_PLATING = part("steel_plating", "Steel Plating", MapColor.METAL);
    public static final WithItem<MultiblockPartBlock> MAGNET_SEGMENT = part("magnet_segment", "Accelerator Magnet Segment", MapColor.COLOR_RED);
    public static final WithItem<MultiblockPartBlock> INJECTOR = part("accelerator_injector", "Accelerator Injector", MapColor.COLOR_LIGHT_BLUE);
    public static final WithItem<MultiblockPartBlock> TARGET_CHAMBER = part("accelerator_target_chamber", "Accelerator Target Chamber",
            MapColor.COLOR_PURPLE);

    private static WithItem<MultiblockPartBlock> part(String id, String english, MapColor color) {
        return GZBlocks.register(id, english, GZTab.MACHINES, MultiblockPartBlock::new,
                p -> p.mapColor(color).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    }

    /**
     * Fission Reactor (3x3x4, DESIGN.md §23.3): lead-lined casing round a core shaft two blocks tall (required air,
     * the formed model draws the fuel bundle there), reactor glass windows on all four sides of it, the controller at
     * the front of the bottom layer and a Fluid Port at the back of it (coolant in), and on top an Item Port (fuel and
     * targets in), an Energy Port (power out) and a second Item Port (products out). The controller itself connects to
     * nothing: everything goes through the ports.
     */
    public static final MultiblockDefinition FISSION_REACTOR = new MultiblockDefinition(GroundZero.id("fission_reactor"),
            new String[][] {
                    {"LXL", "LLL", "LWL"},
                    {"LGL", "G.G", "LGL"},
                    {"LGL", "G.G", "LGL"},
                    {"LUL", "LEL", "LIL"},
            },
            Map.of('L', Entry.of(() -> LEAD_LINED_CASING.block().get()), 'G', Entry.of(REACTOR_GLASS.block()),
                    'W', Entry.of(() -> FLUID_PORT.block().get()), 'U', Entry.of(ITEM_PORT.block()), 'I', Entry.of(ITEM_PORT.block()),
                    'E', Entry.of(() -> ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> GZMachines.FISSION_REACTOR.block().get())));

    /**
     * Particle Accelerator (DESIGN.md §23.3): a two-layer octagonal ring 9 blocks across, one block wide: three straight
     * cells on each side and a five-cell staircase at each corner, 32 cells a layer. Bottom layer steel plating; top
     * layer magnet segments with the controller at the front centre, the injector beside it, the target chamber at the
     * back centre, an Energy Port in the middle of one side and an Item Port in the middle of the other. The inside and
     * the corners outside the octagon are free. The formed model's beam line keeps inside these cells, so every part
     * you see is a part you can click.
     */
    public static final MultiblockDefinition PARTICLE_ACCELERATOR = new MultiblockDefinition(GroundZero.id("particle_accelerator"),
            new String[][] {
                    octagon('P', "PPPPP", "PPPPP"),
                    octagon('S', "SSXJS", "SSTSS"),
            },
            Map.of('P', Entry.of(() -> STEEL_PLATING.block().get()),
                    'S', Entry.of(() -> MAGNET_SEGMENT.block().get()), 'J', Entry.of(() -> INJECTOR.block().get()),
                    'T', Entry.of(() -> TARGET_CHAMBER.block().get()), 'E', Entry.of(() -> ENERGY_PORT.block().get()),
                    'I', Entry.of(ITEM_PORT.block()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> GZMachines.PARTICLE_ACCELERATOR.block().get())));

    /**
     * One layer of the accelerator's 9x9 stepped octagon (rows front first) made of {@code c}, with the front and back
     * straight rows given (five cells each, the middle three being the straight side) and the Energy and Item Ports in
     * the middle of the left and right sides when {@code c} is the magnet layer.
     */
    private static String[] octagon(char c, String front, String back) {
        String two = String.valueOf(c).repeat(2);
        String mid = c == 'S' ? "E       I" : c + " ".repeat(7) + c;
        String side = c + " ".repeat(7) + c;
        return new String[] {"  " + front + "  ", " " + two + "   " + two + " ", two + "     " + two, side, mid, side,
                two + "     " + two, " " + two + "   " + two + " ", "  " + back + "  "};
    }

    /**
     * Chemical Plant (2x2x2): controller front-left with an Energy Port beside it, a Fluid Port behind it and the Item
     * Port at the back right; a roof of reactor glass. The controller connects to nothing: everything goes through the
     * ports. The formed model shows two windowed tanks, a manifold and a stirrer.
     */
    public static final MultiblockDefinition CHEMICAL_PLANT = new MultiblockDefinition(GroundZero.id("chemical_plant"),
            new String[][] {
                    {"XE", "FI"},
                    {"GG", "GG"},
            },
            Map.of('E', Entry.of(() -> ENERGY_PORT.block().get()), 'I', Entry.of(ITEM_PORT.block()), 'G', Entry.of(REACTOR_GLASS.block()),
                    'F', Entry.of(() -> FLUID_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> GZMachines.CHEMICAL_PLANT.block().get())));

    public static final DeferredItem<HoloProjectorItem> HOLO_PROJECTOR = GZItems.register("holo_projector", "Holo-Projector", GZTab.MACHINES,
            HoloProjectorItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<StructureSpawnerItem> CHEMICAL_PLANT_SPAWNER = GZItems.register("chemical_plant_spawner",
            "Structure Spawner: Chemical Plant", GZTab.MACHINES, p -> new StructureSpawnerItem(p, () -> CHEMICAL_PLANT),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    public static final DeferredItem<StructureSpawnerItem> FISSION_REACTOR_SPAWNER = GZItems.register("fission_reactor_spawner",
            "Structure Spawner: Fission Reactor", GZTab.MACHINES, p -> new StructureSpawnerItem(p, () -> FISSION_REACTOR),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<StructureSpawnerItem> PARTICLE_ACCELERATOR_SPAWNER = GZItems.register("particle_accelerator_spawner",
            "Structure Spawner: Particle Accelerator", GZTab.MACHINES, p -> new StructureSpawnerItem(p, () -> PARTICLE_ACCELERATOR),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    private GZMultiblocks() {}

    public static void init() {}
}
