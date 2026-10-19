package dev.groundzero.content.machine;

import java.util.ArrayList;
import java.util.List;

import dev.groundzero.GroundZero;
import dev.groundzero.content.multiblock.MultiblockControllerBlock;
import dev.groundzero.content.power.CableBlock;
import dev.groundzero.content.power.CableNetwork;
import dev.groundzero.content.power.CableTier;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZBlocks;
import dev.groundzero.registry.GZBlocks.WithItem;
import dev.groundzero.registry.GZTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Machines, generators and cables (DESIGN.md §7–8). */
public final class GZMachines {
    public static final WithItem<MachineBlock> COMBUSTION_GENERATOR = machine("combustion_generator", "Combustion Generator",
            p -> new MachineBlock(p, GZBlockEntities.COMBUSTION_GENERATOR, true, Shapes.block()));
    public static final WithItem<MachineBlock> ORE_CRUSHER = machine("ore_crusher", "Ore Crusher",
            p -> new MachineBlock(p, GZBlockEntities.ORE_CRUSHER, true, Shapes.block()));

    public static final WithItem<TallMachineBlock> ARC_FURNACE = tallMachine("arc_furnace", "Arc Furnace",
            p -> new TallMachineBlock(p, GZBlockEntities.ARC_FURNACE, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(1, 2, 1, 15, 12.5, 15), Block.box(1, 2, 12, 9, 16, 16)),
                    Shapes.or(Block.box(4.5, 0, 4, 11.5, 15, 11), Block.box(0, 0, 12, 4, 15, 16))));
    public static final WithItem<TallMachineBlock> INDUSTRIAL_PRESS = tallMachine("industrial_press", "Industrial Press",
            p -> new TallMachineBlock(p, GZBlockEntities.INDUSTRIAL_PRESS, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 9, 16), Block.box(3.5, 9, 12, 12.5, 16, 16), Block.box(1.5, 9, 1, 3, 16, 2.5),
                            Block.box(13, 9, 1, 14.5, 16, 2.5)),
                    Shapes.or(Block.box(3.5, 0, 12, 12.5, 16, 16), Block.box(2.5, 8, 2, 13.5, 16, 12), Block.box(1.5, 0, 1, 3, 7, 2.5),
                            Block.box(13, 0, 1, 14.5, 7, 2.5))));

    public static final WithItem<MachineBlock> ELECTROLYTIC_SEPARATOR = machine("electrolytic_separator", "Electrolytic Separator",
            p -> new MachineBlock(p, GZBlockEntities.ELECTROLYTIC_SEPARATOR, true, Shapes.block()));
    /** Configures pipe and cable sides, turns machines, picks blocks up (see {@link WrenchItem}). */
    public static final net.neoforged.neoforge.registries.DeferredItem<WrenchItem> WRENCH = dev.groundzero.registry.GZItems.register(
            "wrench", "Wrench", GZTab.MACHINES, WrenchItem::new, p -> p.stacksTo(1));
    public static final WithItem<MachineBlock> WATER_PUMP = machine("water_pump", "Water Pump",
            p -> new MachineBlock(p, GZBlockEntities.WATER_PUMP, true, Shapes.block()));
    public static final WithItem<TallMachineBlock> CRYOGENIC_CONDENSER = tallMachine("cryogenic_condenser", "Cryogenic Condenser",
            p -> new TallMachineBlock(p, GZBlockEntities.CRYOGENIC_CONDENSER, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(2, 3, 2, 14, 16, 14)),
                    Shapes.or(Block.box(2, 0, 2, 14, 13, 14), Block.box(5, 13, 5, 11, 16, 11))));

    // Slice 5 (DESIGN.md §8): circuits, enrichment, fusion fuel, exotic matter.
    public static final WithItem<MachineBlock> CIRCUIT_FABRICATOR = machine("circuit_fabricator", "Circuit Fabricator",
            p -> new MachineBlock(p, GZBlockEntities.CIRCUIT_FABRICATOR, true, Shapes.block()));
    public static final WithItem<TallMachineBlock> GAS_CENTRIFUGE = tallMachine("gas_centrifuge", "Gas Centrifuge",
            p -> new TallMachineBlock(p, GZBlockEntities.GAS_CENTRIFUGE, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(2.5, 3, 2.5, 13.5, 7, 13.5), Block.box(4, 7, 4, 12, 16, 12)),
                    Shapes.or(Block.box(4, 0, 4, 12, 14, 12), Block.box(0, 12.4, 6.8, 16, 14.8, 9.2))));
    public static final WithItem<MachineBlock> FUSION_FUEL_SYNTHESIZER = machine("fusion_fuel_synthesizer", "Fusion Fuel Synthesizer",
            p -> new MachineBlock(p, GZBlockEntities.FUSION_FUEL_SYNTHESIZER, true, Shapes.block()));
    public static final WithItem<TallMachineBlock> EXOTIC_MATTER_FABRICATOR = tallMachine("exotic_matter_fabricator", "Exotic Matter Fabricator",
            p -> new TallMachineBlock(p, GZBlockEntities.EXOTIC_MATTER_FABRICATOR, true,
                    Block.box(0, 0, 0, 16, 16, 16),
                    Block.box(0, 0, 0, 16, 15, 16)));

    public static final WithItem<MultiblockControllerBlock> CHEMICAL_PLANT = GZBlocks.register("chemical_plant", "Chemical Plant Controller",
            GZTab.MACHINES, p -> new MultiblockControllerBlock(p, GZBlockEntities.CHEMICAL_PLANT),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));

    public static final List<WithItem<CableBlock>> CABLES = new ArrayList<>();

    static {
        for (CableTier tier : CableTier.values()) {
            CABLES.add(GZBlocks.register(tier.id(), tier.english(), GZTab.MACHINES, p -> new CableBlock(p, tier),
                    p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.8f).sound(SoundType.COPPER).noOcclusion()));
        }
    }

    private GZMachines() {}

    public static void init() {
        MachineText.init();
    }

    private static WithItem<MachineBlock> machine(String name, String english, java.util.function.Function<BlockBehaviour.Properties, MachineBlock> factory) {
        return GZBlocks.registerWithItem(name, english, GZTab.MACHINES, factory,
                p -> p.mapColor(MapColor.METAL).strength(3.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion(),
                (block, props) -> new GeoBlockItem(block, props, GroundZero.id(name)));
    }

    private static WithItem<TallMachineBlock> tallMachine(String name, String english, java.util.function.Function<BlockBehaviour.Properties, TallMachineBlock> factory) {
        return GZBlocks.registerWithItem(name, english, GZTab.MACHINES, factory,
                p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                        .pushReaction(PushReaction.IMMOVEABLE),
                (block, props) -> new GeoBlockItem(block, props, GroundZero.id(name)));
    }

    public static void registerCableCapabilities(RegisterCapabilitiesEvent event) {
        Block[] cables = CABLES.stream().map(c -> (Block) c.block().get()).toArray(Block[]::new);
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, be, side) ->
                side != null && CableBlock.side(state, side) == CableBlock.Side.DISABLED ? null : CableNetwork.handler(level, pos, side), cables);
    }
}
