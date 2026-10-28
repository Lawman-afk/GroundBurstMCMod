package dev.groundburst.datagen;

import java.util.concurrent.CompletableFuture;

import dev.groundburst.content.nuclear.GBNuclear;
import dev.groundburst.content.radiation.GBRadiation;
import dev.groundburst.content.radiation.GBRadiation.Radioactivity;
import dev.groundburst.content.resource.GBResources;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.DataMapProvider;

/**
 * What is radioactive and how strongly (DESIGN.md §21.8): the {@code groundburst:radioactivity} data maps. A block's
 * value is its dose rate a block away, an item's its dose rate per item carried (RAD/h). Set for play, not physics: a
 * stack of uranium ingots carried for half an hour brings sickness, a spent fuel rod in three minutes, a stack of
 * nuclear waste kills in two; that is what the Lead Container is for. Corium kills up close.
 */
public final class GBRadiationData extends DataMapProvider {
    public GBRadiationData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void gather(HolderLookup.Provider provider) {
        var blocks = builder(GBRadiation.BLOCK_RADIOACTIVITY);
        block(blocks, GBResources.URANIUM_ORE.block().key(), 4f);
        block(blocks, GBResources.DEEPSLATE_URANIUM_ORE.block().key(), 4f);
        block(blocks, GBResources.RAW_URANIUM_BLOCK.block().key(), 30f);
        block(blocks, GBResources.URANIUM_BLOCK.block().key(), 50f);
        block(blocks, dev.groundburst.content.fallout.GBFallout.TRINITITE.block().key(), 3f);       // glass fused round a crater, laced with fission products
        block(blocks, dev.groundburst.content.fallout.GBFallout.IRRADIATED_SOIL.block().key(), 6f);
        block(blocks, dev.groundburst.content.fallout.GBFallout.FALLOUT_ASH.block().key(), 1f);      // a carpet of it reads about 10 RAD/h
        block(blocks, dev.groundburst.content.salvage.GBSalvage.WASTE_DRUM.block().key(), 8f);           // a stack of drums reads high: the waste store's rooms
        block(blocks, GBNuclear.CORIUM.block().key(), 20_000f);           // fresh from the core, still glowing
        block(blocks, GBNuclear.COOLED_CORIUM.block().key(), 3_000f);

        var items = builder(GBRadiation.ITEM_RADIOACTIVITY);
        item(items, GBResources.URANIUM_ORE.item().key(), 2f);
        item(items, GBResources.DEEPSLATE_URANIUM_ORE.item().key(), 2f);
        item(items, GBResources.RAW_URANIUM.key(), 3f);
        item(items, GBResources.URANIUM_DUST.key(), 4f);
        item(items, GBResources.URANIUM_INGOT.key(), 4f);
        item(items, GBResources.RAW_URANIUM_BLOCK.item().key(), 27f);
        item(items, GBResources.URANIUM_BLOCK.item().key(), 36f);
        item(items, GBNuclear.ENRICHED_URANIUM_NUGGET.key(), 3f);
        item(items, GBNuclear.ENRICHED_URANIUM_INGOT.key(), 25f);
        item(items, GBNuclear.DEPLETED_URANIUM_DUST.key(), 1.5f);
        item(items, GBNuclear.DEPLETED_URANIUM_INGOT.key(), 2f);
        item(items, GBNuclear.URANIUM_FUEL_ROD.key(), 60f);
        item(items, GBNuclear.SPENT_FUEL_ROD.key(), 2_000f);                // fission products: the hottest thing you can hold
        item(items, GBNuclear.IRRADIATED_DU_TARGET_ROD.key(), 800f);
        item(items, GBNuclear.IRRADIATED_LI6_TARGET_ROD.key(), 250f);
        item(items, GBNuclear.PLUTONIUM_NUGGET.key(), 25f);
        item(items, GBNuclear.PLUTONIUM_INGOT.key(), 220f);
        item(items, GBNuclear.NUCLEAR_WASTE.key(), 500f);
        item(items, dev.groundburst.content.fallout.GBFallout.TRINITITE.item().key(), 1f);
        item(items, dev.groundburst.content.fallout.GBFallout.IRRADIATED_SOIL.item().key(), 2f);
        item(items, dev.groundburst.content.salvage.GBSalvage.DAMAGED_WARHEAD.getKey(), 40f);              // carry it in a Lead Container
        item(items, dev.groundburst.content.salvage.GBSalvage.WASTE_DRUM.item().key(), 6f);
        item(items, GBNuclear.CORIUM.item().key(), 5_000f);
        item(items, GBNuclear.COOLED_CORIUM.item().key(), 800f);
    }

    private static void block(Builder<Radioactivity, Block> b, ResourceKey<Block> key, float rate) {
        b.add(key, new Radioactivity(rate), false);
    }

    private static void item(Builder<Radioactivity, Item> b, ResourceKey<Item> key, float rate) {
        b.add(key, new Radioactivity(rate), false);
    }

    @Override
    public String getName() {
        return "Ground Burst radioactivity";
    }
}
