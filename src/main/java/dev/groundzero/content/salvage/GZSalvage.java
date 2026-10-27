package dev.groundzero.content.salvage;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZBlocks;
import dev.groundzero.registry.GZBlocks.WithItem;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * What the world's old sites hold (DESIGN.md §22, Phase 15): a broken nuclear warhead to salvage (weakly radioactive:
 * carry it in a Lead Container; the Chemical Plant leaches its core back out), and the waste drums stacked in the
 * waste storage (radioactive).
 */
public final class GZSalvage {
    public static final DeferredItem<Item> DAMAGED_WARHEAD = GZItems.simple("damaged_warhead", "Damaged Warhead", GZTab.NUCLEAR,
            p -> p.stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final WithItem<Block> WASTE_DRUM = GZBlocks.register("waste_drum", "Waste Drum", GZTab.AFTERMATH, Block::new,
            p -> p.mapColor(MapColor.COLOR_YELLOW).strength(2.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .instrument(NoteBlockInstrument.BASS));

    public static final String INFO_SALVAGE = dev.groundzero.registry.GZLang.add(GroundZero.MODID + ".info.salvage",
            "Found in the shafts of abandoned missile silos and among the wreckage of crashed missiles: a broken nuclear warhead. "
                    + "It is weakly radioactive (keep it in a Lead Container). A Chemical Plant leaches its core out with sulfuric "
                    + "acid: enriched uranium, often plutonium, and the steel of its case.");
    public static final String INFO_WASTE_DRUM = dev.groundzero.registry.GZLang.add(GroundZero.MODID + ".info.waste_drum",
            "Drums of nuclear waste, stacked in the rooms of old waste storage caves under bags of magnesium oxide. Each is "
                    + "radioactive; a stack of them reads high. Broken open, it gives its waste.");

    private GZSalvage() {}

    public static void init() {}
}
