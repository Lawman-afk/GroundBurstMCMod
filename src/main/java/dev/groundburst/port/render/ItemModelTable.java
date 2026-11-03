package dev.groundburst.port.render;

import java.util.List;
import java.util.Map;

/**
 * Written by tools/backport/port.py from main's item model definitions (assets/&lt;ns&gt;/items): what this branch's
 * item models need from code ({@link ItemModels}). Not edited by hand.
 */
final class ItemModelTable {
    private ItemModelTable() {}

    /** Items tinted a constant colour, ARGB per tint index. */
    static final Map<String, int[]> TINTS = Map.ofEntries(
            Map.entry("groundburst:dead_grass_block", new int[] {0xFF9C8F62}),
            Map.entry("groundburst:frosted_grass_block", new int[] {0xFFB4D0D6}),
            Map.entry("groundburst:frozen_plant", new int[] {0xFFA8CFE2}));

    /** The item properties the models' overrides read, and the items that have them. */
    static final Map<String, List<String>> PROPERTIES = Map.ofEntries(
            Map.entry("groundburst:dose", List.of("groundburst:dosimeter_badge")),
            Map.entry("groundburst:using_item", List.of("groundburst:laser_designator")));
}
