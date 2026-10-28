package dev.groundburst.datagen;

import java.util.Set;

import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.content.resource.GBResources;
import dev.groundburst.content.resource.GBResources.Ore;
import dev.groundburst.registry.GBBlocks;
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
public class GBBlockLoot extends BlockLootSubProvider {
    public GBBlockLoot(LootTableSubProvider.Context context) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), context);
    }

    @Override
    protected void generate() {
        for (Ore ore : GBResources.ores()) {
            Block block = ore.block().block().get();
            if (ore.max() > 1) {
                add(block, multiDrop(block, ore.drop().get(), ore.min(), ore.max()));
            } else {
                add(block, createOreDrop(block, ore.drop().get()));
            }
        }
        for (var b : java.util.List.of(GBResources.URANIUM_BLOCK, GBResources.LEAD_BLOCK, GBResources.TUNGSTEN_BLOCK,
                GBResources.RAW_URANIUM_BLOCK, GBResources.RAW_LITHIUM_BLOCK, GBResources.RAW_TUNGSTEN_BLOCK,
                GBResources.RAW_LEAD_BLOCK, GBResources.RAW_RARE_EARTH_BLOCK, GBResources.VOIDSTONE)) {
            dropSelf(b.block().get());
        }
        // Placed charges: themselves (C4: as many as were stacked).
        add(dev.groundburst.content.ordnance.GBOrdnance.C4.block().get(), createCandleDrops(dev.groundburst.content.ordnance.GBOrdnance.C4.block().get()));
        dropSelf(dev.groundburst.content.ordnance.GBOrdnance.BREACHING_CHARGE.block().get());
        dropSelf(dev.groundburst.content.ordnance.GBOrdnance.THERMITE_CHARGE.block().get());
        dropSelf(dev.groundburst.content.ordnance.GBOrdnance.TIMED_BOMB.block().get());
        dropSelf(dev.groundburst.content.ordnance.GBOrdnance.LANDMINE.block().get());
        dropSelf(dev.groundburst.content.ordnance.GBOrdnance.BOUNCING_MINE.block().get());
        dropSelf(dev.groundburst.content.ordnance.GBOrdnance.CLAYMORE.block().get());
        for (var bomb : dev.groundburst.content.ordnance.GBOrdnance.WARHEADS) {
            Block block = bomb.block().get();
            add(block, createSingleItemTable(block).apply(net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction
                    .copyComponentsFromBlockEntity(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                    .include(dev.groundburst.registry.GBDataComponents.FUZE.get())));
        }
        var bench = dev.groundburst.content.assembly.GBAssembly.ASSEMBLY_BENCH.block().get();
        add(bench, createSinglePropConditionTable(bench, dev.groundburst.content.machine.WideMachineBlock.PART,
                dev.groundburst.content.machine.WideMachineBlock.Part.MAIN));
        // Launching (DESIGN.md §17).
        for (var b : java.util.List.of(dev.groundburst.content.launch.GBLaunch.LAUNCH_PAD, dev.groundburst.content.launch.GBLaunch.GANTRY_FRAME,
                dev.groundburst.content.launch.GBLaunch.GANTRY_ARM, dev.groundburst.content.launch.GBLaunch.RADAR_MAST,
                dev.groundburst.content.launch.GBLaunch.RADAR_DISH, dev.groundburst.content.launch.GBLaunch.ANTENNA,
                dev.groundburst.content.launch.GBLaunch.FLAME_DEFLECTOR)) {
            dropSelf(b.block().get());
        }
        dropSelf(dev.groundburst.content.launch.GBLaunch.SILO_CONTROLLER.block().get());
        dropSelf(dev.groundburst.content.launch.GBLaunch.COMPLEX_CONTROLLER.block().get());
        dropSelf(dev.groundburst.content.launch.GBLaunch.MISSILE_SILO_CONTROLLER.block().get());
        dropSelf(dev.groundburst.content.launch.GBLaunch.CRUISE_CONTROLLER.block().get());
        dropSelf(dev.groundburst.content.launch.GBLaunch.LAUNCH_BOX.block().get());
        dropSelf(dev.groundburst.content.launch.GBLaunch.REINFORCED_CONCRETE.block().get());
        dropSelf(dev.groundburst.content.launch.GBLaunch.HAZARD_CONCRETE.block().get());
        // The Silo Hatch, like the Large Radar Dish, drops itself from its middle only.
        add(dev.groundburst.content.launch.GBLaunch.SILO_HATCH.block().get(), createSinglePropConditionTable(
                dev.groundburst.content.launch.GBLaunch.SILO_HATCH.block().get(), dev.groundburst.content.launch.SiloHatchBlock.PART,
                dev.groundburst.content.launch.SiloHatchBlock.Part.CENTER));
        dropSelf(dev.groundburst.content.launch.GBLaunch.COMPUTER_CONTROLLER.block().get());
        dropSelf(dev.groundburst.content.launch.GBLaunch.RADAR_CONTROLLER.block().get());
        dropSelf(dev.groundburst.content.strategic.GBStrategic.CRANE_RAIL.block().get());
        dropSelf(dev.groundburst.content.strategic.GBStrategic.HALL_CONTROLLER.block().get());
        for (var c : dev.groundburst.content.assembler.AssemblerClass.values()) {
            dropSelf(dev.groundburst.content.assembler.GBAssemblers.controller(c).block().get());
        }
        // The superweapons (DESIGN.md §19.5).
        for (var b : java.util.List.of(dev.groundburst.content.superweapon.GBSuperweapons.ADOBE_WALL, dev.groundburst.content.superweapon.GBSuperweapons.TOXIN_TANK, dev.groundburst.content.superweapon.GBSuperweapons.SCUD_ERECTOR, dev.groundburst.content.superweapon.GBSuperweapons.SCUD_STORM_CONTROLLER,
                dev.groundburst.content.superweapon.GBSuperweapons.FIELDSTONE_WALL, dev.groundburst.content.superweapon.GBSuperweapons.MISSILE_ERECTOR, dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE_CONTROLLER, dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER_CONTROLLER,
                dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY_CONTROLLER)) {
            dropSelf(b.block().get());
        }
        // The Large Radar Dish drops itself from its middle only (its parts go with it).
        add(dev.groundburst.content.launch.GBLaunch.LARGE_RADAR_DISH.block().get(), createSinglePropConditionTable(
                dev.groundburst.content.launch.GBLaunch.LARGE_RADAR_DISH.block().get(), dev.groundburst.content.launch.LargeRadarDishBlock.PART,
                dev.groundburst.content.launch.LargeRadarDishBlock.Part.CENTER));
        // Corium sets as it is dug: either way you get Cooled Corium.
        dropSelf(dev.groundburst.content.nuclear.GBNuclear.COOLED_CORIUM.block().get());
        dropOther(dev.groundburst.content.nuclear.GBNuclear.CORIUM.block().get(), dev.groundburst.content.nuclear.GBNuclear.COOLED_CORIUM.item().get());
        dropSelf(GBMachines.COMBUSTION_GENERATOR.block().get());
        dropSelf(GBMachines.ORE_CRUSHER.block().get());
        GBMachines.CABLES.forEach(c -> dropSelf(c.block().get()));
        dropSelf(GBMachines.ELECTROLYTIC_SEPARATOR.block().get());
        dropSelf(GBMachines.CHEMICAL_PLANT.block().get());
        dropSelf(GBMachines.WATER_PUMP.block().get());
        dropSelf(GBMachines.CIRCUIT_FABRICATOR.block().get());
        dropSelf(GBMachines.FUSION_FUEL_SYNTHESIZER.block().get());
        dropSelf(GBMachines.CANNING_MACHINE.block().get());
        dropSelf(GBMachines.DIESEL_GENERATOR.block().get());
        dropSelf(GBMachines.RTG.block().get());
        dropSelf(GBMachines.BATTERY_CHARGER.block().get());
        // Capacitor banks keep their charge in the dropped item.
        for (var bank : GBMachines.CAPACITOR_BANKS) {
            Block block = bank.block().get();
            add(block, createSingleItemTable(block).apply(net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction
                    .copyComponentsFromBlockEntity(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                    .include(dev.groundburst.registry.GBDataComponents.ENERGY.get())));
        }
        dropSelf(dev.groundburst.content.fluid.GBFluids.FLUID_TANK.block().get());
        dropSelf(dev.groundburst.content.fluid.GBFluids.CREATIVE_FLUID_TANK.block().get());
        dropSelf(GBMultiblocks.MACHINE_CASING.block().get());
        dropSelf(GBMultiblocks.ITEM_PORT.block().get());
        dropSelf(GBMultiblocks.REACTOR_GLASS.block().get());
        dropSelf(GBMachines.FISSION_REACTOR.block().get());
        dropSelf(GBMachines.PARTICLE_ACCELERATOR.block().get());
        dropSelf(GBMachines.FUSION_REACTOR.block().get());
        dropSelf(GBMachines.SINGULARITY_REACTOR.block().get());
        for (var part : java.util.List.of(GBMultiblocks.LEAD_LINED_CASING, GBMultiblocks.STEEL_PLATING,
                GBMultiblocks.FUEL_CHANNEL,
                GBMultiblocks.MAGNET_SEGMENT, GBMultiblocks.INJECTOR, GBMultiblocks.TARGET_CHAMBER, GBMultiblocks.TOROIDAL_FIELD_COIL,
                GBMultiblocks.VACUUM_VESSEL, GBMultiblocks.CENTRAL_SOLENOID, GBMultiblocks.CONTAINMENT_EMITTER, GBMultiblocks.CORE_CRADLE)) {
            dropSelf(part.block().get());
        }
        dropSelf(GBMultiblocks.ENERGY_PORT.block().get());
        dropSelf(GBMultiblocks.FLUID_PORT.block().get());
        for (var pipe : dev.groundburst.content.pipe.GBPipes.itemPipes()) {
            dropSelf(pipe.block().get());
        }
        for (var pipe : dev.groundburst.content.fluid.GBFluids.pipes()) {
            dropSelf(pipe.block().get());
        }
        for (var tall : java.util.List.of(GBMachines.ARC_FURNACE, GBMachines.INDUSTRIAL_PRESS, GBMachines.CRYOGENIC_CONDENSER,
                GBMachines.GAS_CENTRIFUGE, GBMachines.EXOTIC_MATTER_FABRICATOR, GBMachines.DECONTAMINATION_SHOWER)) {
            add(tall.block().get(), createSinglePropConditionTable(tall.block().get(), TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        }
        add(GBResources.BUDDING_VOIDSTONE.block().get(), noDrop());
        // The aftermath (slice 12b): the ground drops itself; ash is dust; dead grass gives dirt, dead leaves only to
        // shears or silk touch, withered crops nothing.
        for (var b : java.util.List.of(dev.groundburst.content.fallout.GBFallout.TRINITITE, dev.groundburst.content.fallout.GBFallout.GLASSED_SAND, dev.groundburst.content.fallout.GBFallout.SLAG, dev.groundburst.content.fallout.GBFallout.SCORCHED_EARTH, dev.groundburst.content.fallout.GBFallout.SCORCHED_STONE,
                dev.groundburst.content.fallout.GBFallout.IRRADIATED_SOIL, dev.groundburst.content.fallout.GBFallout.CHARRED_PLANKS)) {
            dropSelf(b.block().get());
        }
        // The silo doors: only the middle drops the door (breaking any block takes the whole).
        for (var d : dev.groundburst.content.construction.GBConstruction.DOORS) {
            var door = d.block().get();
            add(door, net.minecraft.world.level.storage.loot.LootTable.lootTable().withPool(applyExplosionCondition(door,
                    net.minecraft.world.level.storage.loot.LootPool.lootPool()
                            .setRolls(net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.exactly(1))
                            .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(door).when(
                                    net.minecraft.world.level.storage.loot.predicates.MatchBlock.blockMatches(this.blocks, door,
                                            net.minecraft.advancements.predicates.StatePropertiesPredicate.Builder.properties()
                                                    .hasProperty(dev.groundburst.content.construction.SiloDoorBlock.CENTER, true)))))));
        }
        // The Construction set: each drops itself (a doubled slab two).
        for (var b : dev.groundburst.content.construction.GBConstruction.ALL) {
            if (b.block().get() instanceof net.minecraft.world.level.block.SlabBlock slab) {
                add(slab, createSlabItemTable(slab));
            } else {
                dropSelf(b.block().get());
            }
        }
        dropSelf(dev.groundburst.content.fallout.GBFallout.CHARRED_LOG.block().get());
        dropSelf(dev.groundburst.content.fallout.GBFallout.CHARRED_WOOD.block().get());
        add(dev.groundburst.content.fallout.GBFallout.ASH.block().get(), noDrop());
        add(dev.groundburst.content.fallout.GBFallout.FALLOUT_ASH.block().get(), noDrop());
        add(dev.groundburst.content.fallout.GBFallout.WITHERED_CROPS.block().get(), noDrop());
        add(dev.groundburst.content.fallout.GBFallout.DEAD_GRASS_BLOCK.block().get(), createSingleItemTableWithSilkTouch(dev.groundburst.content.fallout.GBFallout.DEAD_GRASS_BLOCK.block().get(),
                net.minecraft.world.item.Items.DIRT));
        add(dev.groundburst.content.fallout.GBFallout.DEAD_LEAVES.block().get(), createShearsOrSilkTouchOnlyDrop(dev.groundburst.content.fallout.GBFallout.DEAD_LEAVES.block().get()));
        // Slice 12e: frozen ground gives what it was (cobblestone, dirt) but to silk touch; frozen plants nothing; crystals
        // only to silk touch; toxic soil coarse dirt.
        add(dev.groundburst.content.fallout.GBFallout.FROSTED_STONE.block().get(), createSingleItemTableWithSilkTouch(dev.groundburst.content.fallout.GBFallout.FROSTED_STONE.block().get(), net.minecraft.world.item.Items.COBBLESTONE));
        add(dev.groundburst.content.fallout.GBFallout.FROSTED_GRASS_BLOCK.block().get(), createSingleItemTableWithSilkTouch(dev.groundburst.content.fallout.GBFallout.FROSTED_GRASS_BLOCK.block().get(), net.minecraft.world.item.Items.DIRT));
        add(dev.groundburst.content.fallout.GBFallout.FROZEN_PLANT.block().get(), noDrop());
        // Phase 15: a waste drum broken open gives its waste (silk touch: the drum).
        add(dev.groundburst.content.salvage.GBSalvage.WASTE_DRUM.block().get(), createSingleItemTableWithSilkTouch(dev.groundburst.content.salvage.GBSalvage.WASTE_DRUM.block().get(),
                dev.groundburst.content.nuclear.GBNuclear.NUCLEAR_WASTE.get(), net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.between(1, 3)));
        add(dev.groundburst.content.fallout.GBFallout.CRYO_CRYSTAL.block().get(), createSilkTouchOnlyTable(dev.groundburst.content.fallout.GBFallout.CRYO_CRYSTAL.block().get()));
        add(dev.groundburst.content.fallout.GBFallout.TOXIC_SOIL.block().get(), createSingleItemTableWithSilkTouch(dev.groundburst.content.fallout.GBFallout.TOXIC_SOIL.block().get(), net.minecraft.world.item.Items.COARSE_DIRT));
        add(GBResources.SMALL_EXOTIC_BUD.block().get(), createSilkTouchOnlyTable(GBResources.SMALL_EXOTIC_BUD.block().get()));
        add(GBResources.LARGE_EXOTIC_BUD.block().get(), createSilkTouchOnlyTable(GBResources.LARGE_EXOTIC_BUD.block().get()));
        Block cluster = GBResources.EXOTIC_CRYSTAL_CLUSTER.block().get();
        add(cluster, multiDrop(cluster, GBResources.EXOTIC_SHARD.get(), 1, 3));
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
        return GBBlocks.BLOCKS.getEntries().stream().map(h -> (Block) h.get())::iterator;
    }
}
