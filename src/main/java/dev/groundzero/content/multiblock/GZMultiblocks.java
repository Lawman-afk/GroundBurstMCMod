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
    /** An open truss (cutout texture): it must not hide its neighbours' faces or you could see through the world. */
    public static final WithItem<MultiblockPartBlock> STEEL_FRAME = GZBlocks.register("steel_frame", "Steel Frame", GZTab.MACHINES,
            MultiblockPartBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .noOcclusion().isViewBlocking((s, l, pos, box) -> false).isSuffocating((s, l, pos) -> false).pushReaction(PushReaction.IMMOVEABLE));
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
     * the front of the bottom layer with a Fluid Port under the core (coolant in), and on top an Item Port (fuel and
     * targets in), an Energy Port (power out) and a second Item Port (products out). The controller itself connects to
     * nothing: everything goes through the ports.
     */
    public static final MultiblockDefinition FISSION_REACTOR = new MultiblockDefinition(GroundZero.id("fission_reactor"),
            new String[][] {
                    {"LXL", "LWL", "LLL"},
                    {"LGL", "G.G", "LGL"},
                    {"LGL", "G.G", "LGL"},
                    {"LUL", "LEL", "LIL"},
            },
            Map.of('L', Entry.of(() -> LEAD_LINED_CASING.block().get()), 'G', Entry.of(REACTOR_GLASS.block()),
                    'W', Entry.of(() -> FLUID_PORT.block().get()), 'U', Entry.of(ITEM_PORT.block()), 'I', Entry.of(ITEM_PORT.block()),
                    'E', Entry.of(() -> ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> GZMachines.FISSION_REACTOR.block().get())));

    /**
     * Particle Accelerator (9x9x3 ring, DESIGN.md §23.3): a hollow square ring one block wide. Bottom layer steel
     * plating; middle layer 28 magnet segments with the injector at the front midpoint, the controller beside it, the
     * target chamber at the back midpoint and an Energy Port at the west midpoint; top layer steel frame. The 7x7 inside
     * is free.
     */
    public static final MultiblockDefinition PARTICLE_ACCELERATOR = new MultiblockDefinition(GroundZero.id("particle_accelerator"),
            new String[][] {
                    ring('P'),
                    {"SSSXJSSSS", "S       S", "S       S", "S       S", "E       S", "S       S", "S       S", "S       S", "SSSSTSSSS"},
                    ring('F'),
            },
            Map.of('P', Entry.of(() -> STEEL_PLATING.block().get()), 'F', Entry.of(() -> STEEL_FRAME.block().get()),
                    'S', Entry.of(() -> MAGNET_SEGMENT.block().get()), 'J', Entry.of(() -> INJECTOR.block().get()),
                    'T', Entry.of(() -> TARGET_CHAMBER.block().get()), 'E', Entry.of(() -> ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> GZMachines.PARTICLE_ACCELERATOR.block().get())));

    /** A 9x9 hollow ring layer of one block. */
    private static String[] ring(char c) {
        String full = String.valueOf(c).repeat(9);
        String side = c + " ".repeat(7) + c;
        return new String[] {full, side, side, side, side, side, side, side, full};
    }

    /**
     * Chemical Plant (2x2x2): controller front-left with casing beside it, a Fluid Port behind it and the Item Port at
     * the back right; a roof of reactor glass. The formed model shows two windowed tanks, a manifold and a stirrer.
     */
    public static final MultiblockDefinition CHEMICAL_PLANT = new MultiblockDefinition(GroundZero.id("chemical_plant"),
            new String[][] {
                    {"XM", "FI"},
                    {"GG", "GG"},
            },
            Map.of('M', Entry.of(MACHINE_CASING.block()), 'I', Entry.of(ITEM_PORT.block()), 'G', Entry.of(REACTOR_GLASS.block()),
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
