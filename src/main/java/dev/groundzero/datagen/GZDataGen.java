package dev.groundzero.datagen;

import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;

/**
 * Data generation entry point ({@code runData}). Output goes to {@code src/generated/resources}.
 * Hand-made models, textures, blockstates and item definitions come from {@code tools/}, not from here.
 */
public final class GZDataGen {
    private GZDataGen() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(GZDataGen::gather);
        modBus.addListener(GZDataGen::gatherRegistries);
    }

    private static void gather(GatherDataEvent.Client event) {
        event.createProvider(GZLanguageProvider::new);
        event.createProvider(GZSoundDefinitions::new);
        event.createBlockAndItemTags(GZTagProviders.Blocks::new, GZTagProviders.Items::new);
    }

    /** Datapack registry content: worldgen, loot tables, recipes. */
    private static void gatherRegistries(GatherDataRegistryEntriesEvent event) {
        GZWorldgenData.gather(event);
        event.lootTable(new LootTableProvider.SubProviderEntry(GZBlockLoot::new, LootContextParamSets.BLOCK));
        event.recipe(GZRecipeProvider::new);
    }
}
