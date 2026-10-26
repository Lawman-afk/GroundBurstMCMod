package dev.groundzero.datagen;

import java.util.concurrent.CompletableFuture;

import dev.groundzero.content.nuclear.GZNuclear;
import dev.groundzero.content.radiation.GZRadiation;
import dev.groundzero.content.radiation.GZRadiation.Radioactivity;
import dev.groundzero.content.resource.GZResources;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.DataMapProvider;

/**
 * What is radioactive and how strongly (DESIGN.md §21.8): the {@code groundzero:radioactivity} data maps. A block's
 * value is its dose rate a block away, an item's its dose rate per item carried (RAD/h). Natural uranium barely clicks
 * a Geiger counter; plutonium, spent fuel and waste are why the Lead Container exists; corium kills.
 */
public final class GZRadiationData extends DataMapProvider {
    public GZRadiationData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void gather(HolderLookup.Provider provider) {
        var blocks = builder(GZRadiation.BLOCK_RADIOACTIVITY);
        block(blocks, GZResources.URANIUM_ORE.block().key(), 0.5f);
        block(blocks, GZResources.DEEPSLATE_URANIUM_ORE.block().key(), 0.5f);
        block(blocks, GZResources.RAW_URANIUM_BLOCK.block().key(), 5f);
        block(blocks, GZResources.URANIUM_BLOCK.block().key(), 8f);
        block(blocks, GZNuclear.CORIUM.block().key(), 2_000f);           // fresh from the core, still glowing
        block(blocks, GZNuclear.COOLED_CORIUM.block().key(), 300f);

        var items = builder(GZRadiation.ITEM_RADIOACTIVITY);
        item(items, GZResources.URANIUM_ORE.item().key(), 0.05f);
        item(items, GZResources.DEEPSLATE_URANIUM_ORE.item().key(), 0.05f);
        item(items, GZResources.RAW_URANIUM.key(), 0.05f);
        item(items, GZResources.URANIUM_DUST.key(), 0.08f);
        item(items, GZResources.URANIUM_INGOT.key(), 0.08f);
        item(items, GZResources.RAW_URANIUM_BLOCK.item().key(), 0.5f);
        item(items, GZResources.URANIUM_BLOCK.item().key(), 0.8f);
        item(items, GZNuclear.ENRICHED_URANIUM_NUGGET.key(), 0.05f);
        item(items, GZNuclear.ENRICHED_URANIUM_INGOT.key(), 0.4f);
        item(items, GZNuclear.DEPLETED_URANIUM_DUST.key(), 0.02f);
        item(items, GZNuclear.DEPLETED_URANIUM_INGOT.key(), 0.03f);
        item(items, GZNuclear.URANIUM_FUEL_ROD.key(), 1.5f);
        item(items, GZNuclear.SPENT_FUEL_ROD.key(), 60f);                // fission products: the hottest thing you can hold
        item(items, GZNuclear.IRRADIATED_DU_TARGET_ROD.key(), 25f);
        item(items, GZNuclear.IRRADIATED_LI6_TARGET_ROD.key(), 8f);
        item(items, GZNuclear.PLUTONIUM_NUGGET.key(), 1.5f);
        item(items, GZNuclear.PLUTONIUM_INGOT.key(), 12f);
        item(items, GZNuclear.NUCLEAR_WASTE.key(), 20f);
        item(items, GZNuclear.CORIUM.item().key(), 200f);
        item(items, GZNuclear.COOLED_CORIUM.item().key(), 30f);
    }

    private static void block(Builder<Radioactivity, Block> b, ResourceKey<Block> key, float rate) {
        b.add(key, new Radioactivity(rate), false);
    }

    private static void item(Builder<Radioactivity, Item> b, ResourceKey<Item> key, float rate) {
        b.add(key, new Radioactivity(rate), false);
    }

    @Override
    public String getName() {
        return "Ground Zero radioactivity";
    }
}
