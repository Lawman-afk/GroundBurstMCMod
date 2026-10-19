package dev.groundzero.datagen;

import java.util.Set;

import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.machine.TallMachineBlock;
import dev.groundzero.content.resource.GZResources;
import dev.groundzero.content.resource.GZResources.Ore;
import dev.groundzero.registry.GZBlocks;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.item.Item;

/** Block loot tables. Every registered block must get one (vanilla's provider checks this). */
public class GZBlockLoot extends BlockLootSubProvider {
    public GZBlockLoot(LootTableSubProvider.Context context) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), context);
    }

    @Override
    protected void generate() {
        for (Ore ore : GZResources.ores()) {
            Block block = ore.block().block().get();
            if (ore.max() > 1) {
                add(block, multiDrop(block, ore.drop().get(), ore.min(), ore.max()));
            } else {
                add(block, createOreDrop(block, ore.drop().get()));
            }
        }
        for (var b : java.util.List.of(GZResources.URANIUM_BLOCK, GZResources.LEAD_BLOCK, GZResources.TUNGSTEN_BLOCK,
                GZResources.RAW_URANIUM_BLOCK, GZResources.RAW_LITHIUM_BLOCK, GZResources.RAW_TUNGSTEN_BLOCK,
                GZResources.RAW_LEAD_BLOCK, GZResources.RAW_RARE_EARTH_BLOCK, GZResources.VOIDSTONE)) {
            dropSelf(b.block().get());
        }
        dropSelf(GZMachines.COMBUSTION_GENERATOR.block().get());
        dropSelf(GZMachines.ORE_CRUSHER.block().get());
        GZMachines.CABLES.forEach(c -> dropSelf(c.block().get()));
        dropSelf(GZMachines.ELECTROLYTIC_SEPARATOR.block().get());
        dropSelf(GZMachines.CHEMICAL_PLANT.block().get());
        dropSelf(GZMachines.WATER_PUMP.block().get());
        dropSelf(GZMachines.CIRCUIT_FABRICATOR.block().get());
        dropSelf(GZMachines.FUSION_FUEL_SYNTHESIZER.block().get());
        dropSelf(dev.groundzero.content.fluid.GZFluids.FLUID_TANK.block().get());
        dropSelf(GZMultiblocks.MACHINE_CASING.block().get());
        dropSelf(GZMultiblocks.ITEM_PORT.block().get());
        dropSelf(GZMultiblocks.REACTOR_GLASS.block().get());
        for (var pipe : dev.groundzero.content.fluid.GZFluids.pipes()) {
            dropSelf(pipe.block().get());
        }
        for (var tall : java.util.List.of(GZMachines.ARC_FURNACE, GZMachines.INDUSTRIAL_PRESS, GZMachines.CRYOGENIC_CONDENSER,
                GZMachines.GAS_CENTRIFUGE, GZMachines.EXOTIC_MATTER_FABRICATOR)) {
            add(tall.block().get(), createSinglePropConditionTable(tall.block().get(), TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        }
        add(GZResources.BUDDING_VOIDSTONE.block().get(), noDrop());
        add(GZResources.SMALL_EXOTIC_BUD.block().get(), createSilkTouchOnlyTable(GZResources.SMALL_EXOTIC_BUD.block().get()));
        add(GZResources.LARGE_EXOTIC_BUD.block().get(), createSilkTouchOnlyTable(GZResources.LARGE_EXOTIC_BUD.block().get()));
        Block cluster = GZResources.EXOTIC_CRYSTAL_CLUSTER.block().get();
        add(cluster, multiDrop(cluster, GZResources.EXOTIC_SHARD.get(), 1, 3));
    }

    /** Powder / gem style: min..max items, Fortune adds more, Silk Touch keeps the block. */
    private LootTable.Builder multiDrop(Block block, Item drop, int min, int max) {
        return createSilkTouchDispatchTable(block, (LootPoolEntryContainer.Builder<?>) applyExplosionDecay(block,
                LootItem.lootTableItem(drop)
                        .apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max)))
                        .apply(ApplyBonusCount.addOreBonusCount(this.enchantments.getOrThrow(Enchantments.FORTUNE)))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return GZBlocks.BLOCKS.getEntries().stream().map(h -> (Block) h.get())::iterator;
    }
}
