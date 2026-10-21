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
        // Placed charges: themselves (C4: as many as were stacked).
        add(dev.groundzero.content.ordnance.GZOrdnance.C4.block().get(), createCandleDrops(dev.groundzero.content.ordnance.GZOrdnance.C4.block().get()));
        dropSelf(dev.groundzero.content.ordnance.GZOrdnance.BREACHING_CHARGE.block().get());
        dropSelf(dev.groundzero.content.ordnance.GZOrdnance.THERMITE_CHARGE.block().get());
        dropSelf(dev.groundzero.content.ordnance.GZOrdnance.TIMED_BOMB.block().get());
        dropSelf(dev.groundzero.content.ordnance.GZOrdnance.LANDMINE.block().get());
        dropSelf(dev.groundzero.content.ordnance.GZOrdnance.BOUNCING_MINE.block().get());
        dropSelf(dev.groundzero.content.ordnance.GZOrdnance.CLAYMORE.block().get());
        for (var bomb : dev.groundzero.content.ordnance.GZOrdnance.WARHEADS) {
            dropSelf(bomb.block().get());
        }
        // Launching (DESIGN.md §17).
        for (var b : java.util.List.of(dev.groundzero.content.launch.GZLaunch.LAUNCH_PAD, dev.groundzero.content.launch.GZLaunch.GANTRY_FRAME,
                dev.groundzero.content.launch.GZLaunch.GANTRY_ARM, dev.groundzero.content.launch.GZLaunch.RADAR_MAST,
                dev.groundzero.content.launch.GZLaunch.RADAR_DISH, dev.groundzero.content.launch.GZLaunch.ANTENNA)) {
            dropSelf(b.block().get());
        }
        dropSelf(dev.groundzero.content.launch.GZLaunch.SILO_CONTROLLER.block().get());
        dropSelf(dev.groundzero.content.launch.GZLaunch.COMPUTER_CONTROLLER.block().get());
        dropSelf(dev.groundzero.content.launch.GZLaunch.RADAR_CONTROLLER.block().get());
        // The Large Radar Dish drops itself from its middle only (its parts go with it).
        add(dev.groundzero.content.launch.GZLaunch.LARGE_RADAR_DISH.block().get(), createSinglePropConditionTable(
                dev.groundzero.content.launch.GZLaunch.LARGE_RADAR_DISH.block().get(), dev.groundzero.content.launch.LargeRadarDishBlock.PART,
                dev.groundzero.content.launch.LargeRadarDishBlock.Part.CENTER));
        // Corium sets as it is dug: either way you get Cooled Corium.
        dropSelf(dev.groundzero.content.nuclear.GZNuclear.COOLED_CORIUM.block().get());
        dropOther(dev.groundzero.content.nuclear.GZNuclear.CORIUM.block().get(), dev.groundzero.content.nuclear.GZNuclear.COOLED_CORIUM.item().get());
        dropSelf(GZMachines.COMBUSTION_GENERATOR.block().get());
        dropSelf(GZMachines.ORE_CRUSHER.block().get());
        GZMachines.CABLES.forEach(c -> dropSelf(c.block().get()));
        dropSelf(GZMachines.ELECTROLYTIC_SEPARATOR.block().get());
        dropSelf(GZMachines.CHEMICAL_PLANT.block().get());
        dropSelf(GZMachines.WATER_PUMP.block().get());
        dropSelf(GZMachines.CIRCUIT_FABRICATOR.block().get());
        dropSelf(GZMachines.FUSION_FUEL_SYNTHESIZER.block().get());
        dropSelf(GZMachines.CANNING_MACHINE.block().get());
        dropSelf(GZMachines.DIESEL_GENERATOR.block().get());
        dropSelf(GZMachines.RTG.block().get());
        dropSelf(GZMachines.BATTERY_CHARGER.block().get());
        // Capacitor banks keep their charge in the dropped item.
        for (var bank : GZMachines.CAPACITOR_BANKS) {
            Block block = bank.block().get();
            add(block, createSingleItemTable(block).apply(net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction
                    .copyComponentsFromBlockEntity(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                    .include(dev.groundzero.registry.GZDataComponents.ENERGY.get())));
        }
        dropSelf(dev.groundzero.content.fluid.GZFluids.FLUID_TANK.block().get());
        dropSelf(dev.groundzero.content.fluid.GZFluids.CREATIVE_FLUID_TANK.block().get());
        dropSelf(GZMultiblocks.MACHINE_CASING.block().get());
        dropSelf(GZMultiblocks.ITEM_PORT.block().get());
        dropSelf(GZMultiblocks.REACTOR_GLASS.block().get());
        dropSelf(GZMachines.FISSION_REACTOR.block().get());
        dropSelf(GZMachines.PARTICLE_ACCELERATOR.block().get());
        dropSelf(GZMachines.FUSION_REACTOR.block().get());
        dropSelf(GZMachines.SINGULARITY_REACTOR.block().get());
        for (var part : java.util.List.of(GZMultiblocks.LEAD_LINED_CASING, GZMultiblocks.STEEL_PLATING,
                GZMultiblocks.MAGNET_SEGMENT, GZMultiblocks.INJECTOR, GZMultiblocks.TARGET_CHAMBER, GZMultiblocks.TOROIDAL_FIELD_COIL,
                GZMultiblocks.VACUUM_VESSEL, GZMultiblocks.CENTRAL_SOLENOID, GZMultiblocks.CONTAINMENT_EMITTER, GZMultiblocks.CORE_CRADLE)) {
            dropSelf(part.block().get());
        }
        dropSelf(GZMultiblocks.ENERGY_PORT.block().get());
        dropSelf(GZMultiblocks.FLUID_PORT.block().get());
        for (var pipe : dev.groundzero.content.pipe.GZPipes.itemPipes()) {
            dropSelf(pipe.block().get());
        }
        for (var pipe : dev.groundzero.content.fluid.GZFluids.pipes()) {
            dropSelf(pipe.block().get());
        }
        for (var tall : java.util.List.of(GZMachines.ARC_FURNACE, GZMachines.INDUSTRIAL_PRESS, GZMachines.CRYOGENIC_CONDENSER,
                GZMachines.GAS_CENTRIFUGE, GZMachines.EXOTIC_MATTER_FABRICATOR, GZMachines.DECONTAMINATION_SHOWER)) {
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
