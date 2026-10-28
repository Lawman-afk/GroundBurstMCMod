package dev.groundburst.datagen;

import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;

/**
 * Data generation entry point ({@code runData}). Output goes to {@code src/generated/resources}.
 * Hand-made models, textures, blockstates and item definitions come from {@code tools/}, not from here.
 */
public final class GBDataGen {
    private GBDataGen() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(GBDataGen::gather);
        modBus.addListener(GBDataGen::gatherRegistries);
    }

    private static void gather(GatherDataEvent.Client event) {
        event.createProvider(GBLanguageProvider::new);
        event.createProvider(GBSoundDefinitions::new);
        event.createBlockAndItemTags(GBTagProviders.Blocks::new, GBTagProviders.Items::new);
        event.createProvider(GBTagProviders.FluidTags::new);
        event.createProvider(GBBlastData.DamageTags::new);
        event.createProvider(GBRadiationData::new);
    }

    /** Datapack registry content: worldgen, loot tables, recipes. */
    private static void gatherRegistries(GatherDataRegistryEntriesEvent event) {
        GBWorldgenData.gather(event);
        GBBlastData.gather(event);
        event.lootTable(new LootTableProvider.SubProviderEntry(GBBlockLoot::new, LootContextParamSets.BLOCK));
        event.lootTable(new LootTableProvider.SubProviderEntry(GBChestLoot::new, LootContextParamSets.CHEST));
        event.recipe(GBRecipeProvider::new);
    }
}
