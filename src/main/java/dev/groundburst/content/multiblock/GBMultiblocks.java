package dev.groundburst.content.multiblock;

import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.multiblock.MultiblockDefinition.Entry;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredItem;

/** Structural blocks and multiblock patterns (DESIGN.md §23). */
public final class GBMultiblocks {
    public static final WithItem<MultiblockPartBlock> MACHINE_CASING = GBBlocks.register("machine_casing", "Machine Casing", GBTab.MACHINES,
            MultiblockPartBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<GlassPartBlock> REACTOR_GLASS = GBBlocks.register("reactor_glass", "Reactor Glass", GBTab.MACHINES,
            GlassPartBlock::new, p -> p.mapColor(MapColor.COLOR_GRAY).strength(3f, 12f).requiresCorrectToolForDrops().sound(SoundType.GLASS)
                    .noOcclusion().isViewBlocking((s, l, pos, box) -> false).isSuffocating((s, l, pos) -> false).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<PortBlock> ITEM_PORT = GBBlocks.register("item_port", "Item Port", GBTab.MACHINES,
            PortBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));

    // Slice 7 structural blocks.
    public static final WithItem<MultiblockPartBlock> LEAD_LINED_CASING = part("lead_lined_casing", "Lead-Lined Casing", MapColor.COLOR_GRAY);
    public static final WithItem<FluidPortBlock> FLUID_PORT = GBBlocks.register("fluid_port", "Fluid Port", GBTab.MACHINES,
            FluidPortBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<EnergyPortBlock> ENERGY_PORT = GBBlocks.register("energy_port", "Energy Port", GBTab.MACHINES,
            EnergyPortBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));
    /** Stood anywhere in a Fission Reactor's core, one fuel rod a channel (DESIGN.md §7.2). */
    public static final WithItem<FuelChannelBlock> FUEL_CHANNEL = GBBlocks.register("fuel_channel", "Fuel Channel", GBTab.MACHINES,
            FuelChannelBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .noOcclusion().isViewBlocking((s, l, pos, box) -> false).isSuffocating((s, l, pos) -> false).pushReaction(PushReaction.IMMOVEABLE)
                    .lightLevel(s -> s.getValue(FuelChannelBlock.LIT) ? 10 : 0));
    public static final WithItem<MultiblockPartBlock> STEEL_PLATING = part("steel_plating", "Steel Plating", MapColor.METAL);
    public static final WithItem<MultiblockPartBlock> MAGNET_SEGMENT = part("magnet_segment", "Accelerator Magnet Segment", MapColor.COLOR_RED);
    public static final WithItem<MultiblockPartBlock> INJECTOR = part("accelerator_injector", "Accelerator Injector", MapColor.COLOR_LIGHT_BLUE);
    public static final WithItem<MultiblockPartBlock> TARGET_CHAMBER = part("accelerator_target_chamber", "Accelerator Target Chamber",
            MapColor.COLOR_PURPLE);

    // Slice 8 structural blocks.
    public static final WithItem<MultiblockPartBlock> TOROIDAL_FIELD_COIL = part("toroidal_field_coil", "Toroidal Field Coil", MapColor.COLOR_ORANGE);
    public static final WithItem<MultiblockPartBlock> VACUUM_VESSEL = part("vacuum_vessel_casing", "Vacuum Vessel Casing", MapColor.METAL);
    public static final WithItem<MultiblockPartBlock> CENTRAL_SOLENOID = part("central_solenoid", "Central Solenoid", MapColor.COLOR_ORANGE);
    public static final WithItem<MultiblockPartBlock> CONTAINMENT_EMITTER = part("containment_emitter", "Containment Emitter", MapColor.COLOR_PURPLE);
    public static final WithItem<MultiblockPartBlock> CORE_CRADLE = part("core_cradle", "Core Cradle", MapColor.COLOR_PURPLE);

    private static WithItem<MultiblockPartBlock> part(String id, String english, MapColor color) {
        return GBBlocks.register(id, english, GBTab.MACHINES, MultiblockPartBlock::new,
                p -> p.mapColor(color).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    }

    /**
     * Fission Reactor (DESIGN.md §7.2, §23.3): any hollow box of lead-lined casing from 3x3x3 to 6x6x6 with Fuel Channels
     * in its core ({@code FissionCore} has the rules). This is the example the Field Manual shows and the Structure
     * Spawner and the GameTests build, laid out as the ghost preview plans a 4x4x4: the controller at the front of the
     * bottom layer, reactor glass windows round the core, the Fluid Port low at the back of the left wall, the Energy
     * Port low in the back wall, the Item Port in the roof over the front of the core; its 2x2x2 core eight Fuel
     * Channels (eight rods: 600 FE a tick). The controller itself connects to nothing: everything goes through the ports.
     * 'L', 'G', 'C' and the ports' letters are also what the ghost preview shows for any size of reactor.
     */
    public static final MultiblockDefinition FISSION_REACTOR = new MultiblockDefinition(GroundBurst.id("fission_reactor"),
            new String[][] {
                    {"LXLL", "LLLL", "LLLL", "LLLL"},
                    {"LGGL", "GCCG", "WCCG", "LEGL"},
                    {"LGGL", "GCCG", "GCCG", "LGGL"},
                    {"LLLL", "LLUL", "LLLL", "LLLL"},
            },
            Map.of('L', Entry.of(() -> LEAD_LINED_CASING.block().get()), 'G', Entry.of(REACTOR_GLASS.block()),
                    'W', Entry.of(() -> FLUID_PORT.block().get()), 'U', Entry.of(ITEM_PORT.block()),
                    'E', Entry.of(() -> ENERGY_PORT.block().get()), 'C', Entry.of(() -> FUEL_CHANNEL.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> GBMachines.FISSION_REACTOR.block().get())));

    /**
     * Particle Accelerator (DESIGN.md §23.3): a two-layer octagonal ring 9 blocks across, one block wide: three straight
     * cells on each side and a five-cell staircase at each corner, 32 cells a layer. Bottom layer steel plating; top
     * layer magnet segments with the controller at the front centre, the injector beside it, the target chamber at the
     * back centre, an Energy Port in the middle of one side and an Item Port in the middle of the other. The inside and
     * the corners outside the octagon are free. The formed model's beam line keeps inside these cells, and the formed
     * corner blocks are cut to the plinth's slopes, so every part you see is a part you can click.
     */
    public static final MultiblockDefinition PARTICLE_ACCELERATOR = new MultiblockDefinition(GroundBurst.id("particle_accelerator"),
            new String[][] {
                    octagon('P', "PPPPP", "PPPPP"),
                    octagon('S', "SSXJS", "SSTSS"),
            },
            Map.of('P', Entry.of(() -> STEEL_PLATING.block().get()),
                    'S', Entry.of(() -> MAGNET_SEGMENT.block().get()), 'J', Entry.of(() -> INJECTOR.block().get()),
                    'T', Entry.of(() -> TARGET_CHAMBER.block().get()), 'E', Entry.of(() -> ENERGY_PORT.block().get()),
                    'I', Entry.of(ITEM_PORT.block()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> GBMachines.PARTICLE_ACCELERATOR.block().get())),
            octagonCuts(9));

    /**
     * The sloped corners of a stepped octagon {@code size} cells across (the accelerator, the fusion reactor): in each
     * corner staircase the three outer cells lose their outer corner and the two inner cells their inner corner, so the
     * formed blocks follow the octagonal ring.
     */
    private static Map<Long, PartCut> octagonCuts(int size) {
        int far = size - 1;
        Map<Long, PartCut> cuts = new java.util.HashMap<>();
        int[][] outer = {{2, 0}, {1, 1}, {0, 2}}, inner = {{2, 1}, {1, 2}};
        PartCut[][] corners = {
                {PartCut.NORTH_WEST, PartCut.SOUTH_EAST}, {PartCut.NORTH_EAST, PartCut.SOUTH_WEST},
                {PartCut.SOUTH_WEST, PartCut.NORTH_EAST}, {PartCut.SOUTH_EAST, PartCut.NORTH_WEST}};
        for (int q = 0; q < 4; q++) {
            boolean flipX = q == 1 || q == 3, flipZ = q >= 2;
            for (int[] c : outer) {
                cuts.put(MultiblockDefinition.cutKey(flipX ? far - c[0] : c[0], flipZ ? far - c[1] : c[1]), corners[q][0]);
            }
            for (int[] c : inner) {
                cuts.put(MultiblockDefinition.cutKey(flipX ? far - c[0] : c[0], flipZ ? far - c[1] : c[1]), corners[q][1]);
            }
        }
        return Map.copyOf(cuts);
    }

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
    public static final MultiblockDefinition CHEMICAL_PLANT = new MultiblockDefinition(GroundBurst.id("chemical_plant"),
            new String[][] {
                    {"XE", "FI"},
                    {"GG", "GG"},
            },
            Map.of('E', Entry.of(() -> ENERGY_PORT.block().get()), 'I', Entry.of(ITEM_PORT.block()), 'G', Entry.of(REACTOR_GLASS.block()),
                    'F', Entry.of(() -> FLUID_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> GBMachines.CHEMICAL_PLANT.block().get())));

    /**
     * Fusion Reactor (DESIGN.md §23.3): a three-layer octagonal ring 9 blocks across, the Particle Accelerator's
     * footprint (three straight cells on each side, a five-cell staircase at each corner, 32 cells a layer), round a
     * central solenoid column. Bottom layer machine casing with the controller at the front centre, a Fluid Port in the
     * middle of the right side and an Energy Port at the back; middle layer
     * toroidal field coils with reactor glass viewports in the middle of each side; top layer vacuum vessel casing. The
     * rest of the inside is free. Corner cells are cut to the ring's slopes once formed.
     */
    public static final MultiblockDefinition FUSION_REACTOR = new MultiblockDefinition(GroundBurst.id("fusion_reactor"),
            new String[][] {
                    {"  MMXMM  ", " MM   MM ", "MM     MM", "M       M", "M   S   F", "M       M", "MM     MM", " MM   MM ", "  MMEMM  "},
                    {"  TTGTT  ", " TT   TT ", "TT     TT", "T       T", "G   S   G", "T       T", "TT     TT", " TT   TT ", "  TTGTT  "},
                    {"  VVVVV  ", " VV   VV ", "VV     VV", "V       V", "V   S   V", "V       V", "VV     VV", " VV   VV ", "  VVVVV  "},
            },
            Map.ofEntries(Map.entry('M', Entry.of(MACHINE_CASING.block())), Map.entry('T', Entry.of(() -> TOROIDAL_FIELD_COIL.block().get())),
                    Map.entry('V', Entry.of(() -> VACUUM_VESSEL.block().get())), Map.entry('S', Entry.of(() -> CENTRAL_SOLENOID.block().get())),
                    Map.entry('G', Entry.of(REACTOR_GLASS.block())),
                    Map.entry('F', Entry.of(() -> FLUID_PORT.block().get())), Map.entry('E', Entry.of(() -> ENERGY_PORT.block().get())),
                    Map.entry(MultiblockDefinition.CONTROLLER, Entry.of(() -> GBMachines.FUSION_REACTOR.block().get()))),
            octagonCuts(9));

    /**
     * Singularity Reactor (DESIGN.md §23.3): a 5x5x5 cage. Machine casing floor with the controller in the middle of
     * the front, an Item Port on the left and an Energy Port on the right; any other floor block may be an Item Port
     * too, to feed it faster; steel plating posts up the four corners;
     * reactor glass walls with a containment emitter in the middle of each; a machine casing roof with an emitter in
     * its centre; and inside, an empty 3x3x3 chamber (required air) with the core cradle floating at its centre.
     */
    public static final MultiblockDefinition SINGULARITY_REACTOR = new MultiblockDefinition(GroundBurst.id("singularity_reactor"),
            new String[][] {
                    {"BBXBB", "BBBBB", "IBBBE", "BBBBB", "BBBBB"},
                    {"PGGGP", "G...G", "G...G", "G...G", "PGGGP"},
                    {"PGQGP", "G...G", "Q.K.Q", "G...G", "PGQGP"},
                    {"PGGGP", "G...G", "G...G", "G...G", "PGGGP"},
                    {"MMMMM", "MMMMM", "MMQMM", "MMMMM", "MMMMM"},
            },
            Map.of('M', Entry.of(MACHINE_CASING.block()), 'P', Entry.of(() -> STEEL_PLATING.block().get()), 'G', Entry.of(REACTOR_GLASS.block()),
                    'B', new Entry(s -> s.is(MACHINE_CASING.block().get()) || s.is(ITEM_PORT.block().get()),
                            () -> MACHINE_CASING.block().get().defaultBlockState()),
                    'Q', Entry.of(() -> CONTAINMENT_EMITTER.block().get()), 'K', Entry.of(() -> CORE_CRADLE.block().get()),
                    'I', Entry.of(ITEM_PORT.block()), 'E', Entry.of(() -> ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> GBMachines.SINGULARITY_REACTOR.block().get())));

    /** Every structure, for lookups by id (the Field Manual's structure pages). */
    public static java.util.List<MultiblockDefinition> all() {
        return java.util.List.of(CHEMICAL_PLANT, FISSION_REACTOR, PARTICLE_ACCELERATOR, FUSION_REACTOR, SINGULARITY_REACTOR,
                dev.groundburst.content.launch.GBLaunch.SILO, dev.groundburst.content.launch.GBLaunch.COMPLEX, dev.groundburst.content.launch.GBLaunch.COMPUTER,
                dev.groundburst.content.launch.GBLaunch.RADAR, dev.groundburst.content.strategic.GBStrategic.HALL,
                dev.groundburst.content.strategic.GBStrategic.SPECIAL,
                dev.groundburst.content.launch.GBLaunch.MISSILE_SILO, dev.groundburst.content.launch.GBLaunch.CRUISE_LAUNCHER,
                dev.groundburst.content.assembler.GBAssemblers.LIGHT,
                dev.groundburst.content.assembler.GBAssemblers.MEDIUM, dev.groundburst.content.assembler.GBAssemblers.HEAVY,
                dev.groundburst.content.assembler.GBAssemblers.STRATEGIC, dev.groundburst.content.superweapon.GBSuperweapons.SCUD_STORM,
                dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE, dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER, dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY);
    }

    public static @org.jspecify.annotations.Nullable MultiblockDefinition byId(net.minecraft.resources.Identifier id) {
        for (MultiblockDefinition def : all()) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    public static final DeferredItem<HoloProjectorItem> HOLO_PROJECTOR = GBItems.register("holo_projector", "Holo-Projector", GBTab.MACHINES,
            HoloProjectorItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<StructureSpawnerItem> CHEMICAL_PLANT_SPAWNER = GBItems.register("chemical_plant_spawner",
            "Structure Spawner: Chemical Plant", GBTab.MACHINES, p -> new StructureSpawnerItem(p, () -> CHEMICAL_PLANT),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    public static final DeferredItem<StructureSpawnerItem> FISSION_REACTOR_SPAWNER = GBItems.register("fission_reactor_spawner",
            "Structure Spawner: Fission Reactor", GBTab.MACHINES, p -> new StructureSpawnerItem(p, () -> FISSION_REACTOR),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<StructureSpawnerItem> PARTICLE_ACCELERATOR_SPAWNER = GBItems.register("particle_accelerator_spawner",
            "Structure Spawner: Particle Accelerator", GBTab.MACHINES, p -> new StructureSpawnerItem(p, () -> PARTICLE_ACCELERATOR),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    public static final DeferredItem<StructureSpawnerItem> FUSION_REACTOR_SPAWNER = GBItems.register("fusion_reactor_spawner",
            "Structure Spawner: Fusion Reactor", GBTab.MACHINES, p -> new StructureSpawnerItem(p, () -> FUSION_REACTOR),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<StructureSpawnerItem> SINGULARITY_REACTOR_SPAWNER = GBItems.register("singularity_reactor_spawner",
            "Structure Spawner: Singularity Reactor", GBTab.MACHINES, p -> new StructureSpawnerItem(p, () -> SINGULARITY_REACTOR),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    private GBMultiblocks() {}

    public static void init() {}
}
