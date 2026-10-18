package dev.groundzero.content.machine;

import java.util.ArrayList;
import java.util.List;

import dev.groundzero.GroundZero;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Machines, generators and cables (DESIGN.md §7–8). */
public final class GZMachines {
    public static final WithItem<MachineBlock> COMBUSTION_GENERATOR = machine("combustion_generator", "Combustion Generator",
            p -> new MachineBlock(p, GZBlockEntities.COMBUSTION_GENERATOR, true, Shapes.block()));
    public static final WithItem<MachineBlock> ORE_CRUSHER = machine("ore_crusher", "Ore Crusher",
            p -> new MachineBlock(p, GZBlockEntities.ORE_CRUSHER, true, Shapes.block()));

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

    public static void registerCableCapabilities(RegisterCapabilitiesEvent event) {
        Block[] cables = CABLES.stream().map(c -> (Block) c.block().get()).toArray(Block[]::new);
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, be, side) -> CableNetwork.handler(level, pos, side), cables);
    }
}
