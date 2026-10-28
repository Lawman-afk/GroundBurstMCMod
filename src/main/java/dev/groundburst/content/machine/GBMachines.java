package dev.groundburst.content.machine;

import java.util.ArrayList;
import java.util.List;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.multiblock.MultiblockControllerBlock;
import dev.groundburst.content.power.CableBlock;
import dev.groundburst.content.power.CableNetwork;
import dev.groundburst.content.power.CableTier;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Machines, generators and cables (DESIGN.md §7–8). */
public final class GBMachines {
    public static final WithItem<MachineBlock> COMBUSTION_GENERATOR = machine("combustion_generator", "Combustion Generator",
            p -> new MachineBlock(p, GBBlockEntities.COMBUSTION_GENERATOR, true, Shapes.block()));
    public static final WithItem<MachineBlock> ORE_CRUSHER = machine("ore_crusher", "Ore Crusher",
            p -> new MachineBlock(p, GBBlockEntities.ORE_CRUSHER, true, Shapes.block()));

    public static final WithItem<TallMachineBlock> ARC_FURNACE = tallMachine("arc_furnace", "Arc Furnace",
            p -> new TallMachineBlock(p, GBBlockEntities.ARC_FURNACE, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(1, 2, 1, 15, 12.5, 15), Block.box(1, 2, 12, 9, 16, 16)),
                    Shapes.or(Block.box(4.5, 0, 4, 11.5, 15, 11), Block.box(0, 0, 12, 4, 15, 16))));
    public static final WithItem<TallMachineBlock> INDUSTRIAL_PRESS = tallMachine("industrial_press", "Industrial Press",
            p -> new TallMachineBlock(p, GBBlockEntities.INDUSTRIAL_PRESS, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 9, 16), Block.box(3.5, 9, 12, 12.5, 16, 16)),
                    Shapes.or(Block.box(3.5, 0, 12, 12.5, 16, 16), Block.box(2.5, 8, 2, 13.5, 16, 12))));

    public static final WithItem<MachineBlock> ELECTROLYTIC_SEPARATOR = machine("electrolytic_separator", "Electrolytic Separator",
            p -> new MachineBlock(p, GBBlockEntities.ELECTROLYTIC_SEPARATOR, true, Shapes.block()));
    /** Configures pipe and cable sides, turns machines, picks blocks up (see {@link WrenchItem}). */
    public static final net.neoforged.neoforge.registries.DeferredItem<WrenchItem> WRENCH = dev.groundburst.registry.GBItems.register(
            "wrench", "Wrench", GBTab.MACHINES, WrenchItem::new, p -> p.stacksTo(1));
    public static final WithItem<MachineBlock> WATER_PUMP = machine("water_pump", "Water Pump",
            p -> new MachineBlock(p, GBBlockEntities.WATER_PUMP, true, Shapes.block()));
    public static final WithItem<TallMachineBlock> CRYOGENIC_CONDENSER = tallMachine("cryogenic_condenser", "Cryogenic Condenser",
            p -> new TallMachineBlock(p, GBBlockEntities.CRYOGENIC_CONDENSER, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(2, 3, 2, 14, 16, 14)),
                    Shapes.or(Block.box(2, 0, 2, 14, 13, 14), Block.box(5, 13, 5, 11, 16, 11))));

    // Slice 5 (DESIGN.md §8): circuits, enrichment, fusion fuel, exotic matter.
    public static final WithItem<MachineBlock> CIRCUIT_FABRICATOR = machine("circuit_fabricator", "Circuit Fabricator",
            p -> new MachineBlock(p, GBBlockEntities.CIRCUIT_FABRICATOR, true, Shapes.block()));
    public static final WithItem<TallMachineBlock> GAS_CENTRIFUGE = tallMachine("gas_centrifuge", "Gas Centrifuge",
            p -> new TallMachineBlock(p, GBBlockEntities.GAS_CENTRIFUGE, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(2.5, 3, 2.5, 13.5, 7, 13.5), Block.box(4, 7, 4, 12, 16, 12)),
                    Shapes.or(Block.box(4, 0, 4, 12, 14, 12), Block.box(0, 12.4, 6.8, 16, 14.8, 9.2))));
    public static final WithItem<MachineBlock> FUSION_FUEL_SYNTHESIZER = machine("fusion_fuel_synthesizer", "Fusion Fuel Synthesizer",
            p -> new MachineBlock(p, GBBlockEntities.FUSION_FUEL_SYNTHESIZER, true, Shapes.block()));
    public static final WithItem<TallMachineBlock> EXOTIC_MATTER_FABRICATOR = tallMachine("exotic_matter_fabricator", "Exotic Matter Fabricator",
            p -> new TallMachineBlock(p, GBBlockEntities.EXOTIC_MATTER_FABRICATOR, true,
                    Block.box(0, 0, 0, 16, 16, 16),
                    Block.box(0, 0, 0, 16, 15, 16)));

    // Slice 6 (DESIGN.md §7-8): mid-game power, storage, charging, decontamination.
    public static final WithItem<MachineBlock> DIESEL_GENERATOR = machine("diesel_generator", "Diesel Generator",
            p -> new MachineBlock(p, GBBlockEntities.DIESEL_GENERATOR, true, Shapes.block()));
    public static final WithItem<MachineBlock> RTG = machine("rtg", "Radioisotope Generator",
            p -> new MachineBlock(p, GBBlockEntities.RTG, true, Shapes.block()));
    public static final WithItem<MachineBlock> BATTERY_CHARGER = machine("battery_charger", "Battery Charger",
            p -> new MachineBlock(p, GBBlockEntities.BATTERY_CHARGER, true, Block.box(0, 0, 0, 16, 9, 16)));
    /** Walk-in booth: open front (north), thin side walls, back wall, floor grate, canopy high enough to stand under. */
    public static final WithItem<TallMachineBlock> DECONTAMINATION_SHOWER = tallMachine("decontamination_shower", "Decontamination Shower",
            p -> new TallMachineBlock(p, GBBlockEntities.DECONTAMINATION_SHOWER, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 1, 16), Block.box(0, 0, 15, 16, 16, 16), Block.box(0, 0, 0, 1, 16, 16), Block.box(15, 0, 0, 16, 16, 16)),
                    Shapes.or(Block.box(0, 14.5, 0, 16, 16, 16), Block.box(0, 0, 15, 16, 16, 16), Block.box(0, 0, 0, 1, 16, 16), Block.box(15, 0, 0, 16, 16, 16))));
    /** Fills canisters from its supply tank and empties them into its catch tank (see {@link CanningMachineBlockEntity}). */
    public static final WithItem<MachineBlock> CANNING_MACHINE = machine("canning_machine", "Canning Machine",
            p -> new MachineBlock(p, GBBlockEntities.CANNING_MACHINE, true, Shapes.block()));
    public static final List<WithItem<dev.groundburst.content.power.CapacitorBankBlock>> CAPACITOR_BANKS = new ArrayList<>();

    public static final WithItem<MultiblockControllerBlock> CHEMICAL_PLANT = GBBlocks.register("chemical_plant", "Chemical Plant Controller",
            GBTab.MACHINES, p -> new MultiblockControllerBlock(p, GBBlockEntities.CHEMICAL_PLANT),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));

    // Slice 7 multiblock controllers (DESIGN.md §7, §23.3).
    public static final WithItem<MultiblockControllerBlock> FISSION_REACTOR = GBBlocks.register("fission_reactor", "Fission Reactor Controller",
            GBTab.MACHINES, p -> new MultiblockControllerBlock(p, GBBlockEntities.FISSION_REACTOR),
            p -> p.mapColor(MapColor.METAL).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<MultiblockControllerBlock> PARTICLE_ACCELERATOR = GBBlocks.register("particle_accelerator",
            "Particle Accelerator Controller", GBTab.MACHINES, p -> new MultiblockControllerBlock(p, GBBlockEntities.PARTICLE_ACCELERATOR),
            p -> p.mapColor(MapColor.METAL).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));

    // Slice 8 multiblock controllers (DESIGN.md §7, §23.3).
    public static final WithItem<MultiblockControllerBlock> FUSION_REACTOR = GBBlocks.register("fusion_reactor", "Fusion Reactor Controller",
            GBTab.MACHINES, p -> new MultiblockControllerBlock(p, GBBlockEntities.FUSION_REACTOR),
            p -> p.mapColor(MapColor.METAL).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<MultiblockControllerBlock> SINGULARITY_REACTOR = GBBlocks.register("singularity_reactor",
            "Singularity Reactor Controller", GBTab.MACHINES, p -> new MultiblockControllerBlock(p, GBBlockEntities.SINGULARITY_REACTOR),
            p -> p.mapColor(MapColor.METAL).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));

    public static final List<WithItem<CableBlock>> CABLES = new ArrayList<>();

    static {
        for (var tier : dev.groundburst.content.power.CapacitorBankBlockEntity.Tier.values()) {
            CAPACITOR_BANKS.add(GBBlocks.registerWithItem(tier.id(), tier.english(), GBTab.MACHINES,
                    p -> new dev.groundburst.content.power.CapacitorBankBlock(p, GBBlockEntities.capacitorBank(tier), tier),
                    p -> p.mapColor(MapColor.METAL).strength(3.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion(),
                    (block, props) -> new GeoBlockItem(block, props, GroundBurst.id(tier.id()))));
        }
        for (CableTier tier : CableTier.values()) {
            CABLES.add(GBBlocks.register(tier.id(), tier.english(), GBTab.MACHINES, p -> new CableBlock(p, tier),
                    p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.8f).sound(SoundType.COPPER).noOcclusion()));
        }
    }

    private GBMachines() {}

    public static void init() {
        MachineText.init();
    }

    private static WithItem<MachineBlock> machine(String name, String english, java.util.function.Function<BlockBehaviour.Properties, MachineBlock> factory) {
        return GBBlocks.registerWithItem(name, english, GBTab.MACHINES, factory,
                p -> p.mapColor(MapColor.METAL).strength(3.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion(),
                (block, props) -> new GeoBlockItem(block, props, GroundBurst.id(name)));
    }

    private static WithItem<TallMachineBlock> tallMachine(String name, String english, java.util.function.Function<BlockBehaviour.Properties, TallMachineBlock> factory) {
        return GBBlocks.registerWithItem(name, english, GBTab.MACHINES, factory,
                p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                        .pushReaction(PushReaction.IMMOVEABLE),
                (block, props) -> new GeoBlockItem(block, props, GroundBurst.id(name)));
    }

    public static void registerCableCapabilities(RegisterCapabilitiesEvent event) {
        Block[] cables = CABLES.stream().map(c -> (Block) c.block().get()).toArray(Block[]::new);
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, be, side) ->
                side != null && CableBlock.side(state, side) == CableBlock.Side.DISABLED ? null : CableNetwork.handler(level, pos, side), cables);
    }
}
