package dev.groundburst.world.structure;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;

/**
 * Weathering a site's template gets as it is placed, so no two sites of a kind look the same (the templates carry their
 * own, designed, wear: DESIGN.md §22). A random share of some blocks turns:
 * <ul>
 * <li>{@code abandoned}: decades left to the weather: stone bricks crack and moss over, cobblestone mosses, glass
 * breaks out of its frames, iron bars rust through, torches and lanterns are long gone, planks rot to nothing, a light
 * gray concrete wall stains;</li>
 * <li>{@code end}: the End's dry vacuum: only glass breaks, and the lights are out;</li>
 * <li>{@code none}.</li>
 * </ul>
 */
public final class Weathering {
    public static final String NONE = "none", ABANDONED = "abandoned", END = "end";

    private Weathering() {}

    static List<StructureProcessor> processors(String kind) {
        List<ProcessorRule> rules = new ArrayList<>();
        switch (kind) {
            case ABANDONED -> {
                turn(rules, Blocks.STONE_BRICKS, 0.22f, Blocks.CRACKED_STONE_BRICKS);
                turn(rules, Blocks.STONE_BRICKS, 0.10f, Blocks.MOSSY_STONE_BRICKS);
                turn(rules, Blocks.COBBLESTONE, 0.12f, Blocks.MOSSY_COBBLESTONE);
                turn(rules, Blocks.GLASS_PANE, 0.35f, Blocks.AIR);
                turn(rules, Blocks.GLASS, 0.25f, Blocks.AIR);
                turn(rules, Blocks.IRON_BARS, 0.12f, Blocks.AIR);
                turn(rules, Blocks.TORCH, 0.7f, Blocks.AIR);
                turn(rules, Blocks.WALL_TORCH, 0.7f, Blocks.AIR);
                turn(rules, Blocks.LANTERN, 0.4f, Blocks.AIR);
                turn(rules, Blocks.SPRUCE_PLANKS, 0.06f, Blocks.AIR);
                turn(rules, Blocks.OAK_PLANKS, 0.06f, Blocks.AIR);
                turn(rules, Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY), 0.08f, Blocks.CONCRETE_POWDER.pick(DyeColor.LIGHT_GRAY));
                turn(rules, Blocks.CONCRETE.pick(DyeColor.GRAY), 0.06f, Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN));
            }
            case END -> {
                turn(rules, Blocks.GLASS_PANE, 0.3f, Blocks.AIR);
                turn(rules, Blocks.GLASS, 0.2f, Blocks.AIR);
                turn(rules, Blocks.END_ROD, 0.3f, Blocks.AIR);
            }
            default -> {
            }
        }
        return rules.isEmpty() ? List.of() : List.of(new RuleProcessor(rules));
    }

    private static void turn(List<ProcessorRule> rules, Block from, float share, Block to) {
        BlockState out = to.defaultBlockState();
        rules.add(new ProcessorRule(new RandomBlockMatchTest(from, share), AlwaysTrueTest.INSTANCE, out));
    }
}
