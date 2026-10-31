package dev.groundburst.datagen;

import java.util.concurrent.CompletableFuture;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.part.GBParts;
import dev.groundburst.content.resource.GBResources;
import dev.groundburst.content.resource.GBResources.Ore;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.BlockItemTagId;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagCopyingItemTagProvider;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.registries.DeferredItem;

/** Block and item tags: tool requirements, convention (c:) tags, and the mod's radioactive tag. */
public final class GBTagProviders {
    private GBTagProviders() {}

    /** Block-item tag pairs filled for blocks and then copied to their items. */
    private static final BlockItemTagId[] COPIED = {
            Tags.BlockItems.ORES, Tags.BlockItems.ORES_IN_GROUND_STONE, Tags.BlockItems.ORES_IN_GROUND_DEEPSLATE,
            Tags.BlockItems.ORES_IN_GROUND_NETHERRACK, Tags.BlockItems.ORE_RATES_SINGULAR, Tags.BlockItems.ORE_RATES_DENSE,
            Tags.BlockItems.STORAGE_BLOCKS, Tags.BlockItems.BUDDING_BLOCKS, Tags.BlockItems.BUDS, Tags.BlockItems.CLUSTERS,
            GBTags.RADIOACTIVE,
            GBTags.common("ores/uranium"), GBTags.common("ores/lithium"), GBTags.common("ores/sulfur"), GBTags.common("ores/saltpeter"),
            GBTags.common("ores/tungsten"), GBTags.common("ores/lead"), GBTags.common("ores/rare_earth"),
            GBTags.common("storage_blocks/uranium"), GBTags.common("storage_blocks/lead"), GBTags.common("storage_blocks/tungsten"),
            GBTags.common("storage_blocks/raw_uranium"), GBTags.common("storage_blocks/raw_lithium"),
            GBTags.common("storage_blocks/raw_tungsten"), GBTags.common("storage_blocks/raw_lead"),
            GBTags.common("storage_blocks/raw_rare_earth"),
    };

    /** Common (c:) tags for our fluids so other mods' acids, fuels and gases are interchangeable with ours. */
    public static class FluidTags extends net.minecraft.data.tags.FluidTagsProvider {
        public FluidTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, GroundBurst.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            for (dev.groundburst.content.fluid.ChemFluid f : dev.groundburst.content.fluid.GBFluids.all()) {
                tag(f.commonTag()).add(f.source().getKey(), f.flowing().getKey());
                if (f.isGas()) {
                    tag(Tags.Fluids.GASEOUS).add(f.source().getKey(), f.flowing().getKey());
                }
            }
        }
    }

    public static class Blocks extends BlockTagsProvider {
        public Blocks(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, GroundBurst.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            // The Construction set: sandbags dug with a shovel, the rest with a pickaxe; stairs and slabs as vanilla's.
            for (var b : dev.groundburst.content.construction.GBConstruction.ALL) {
                boolean sand = b == dev.groundburst.content.construction.GBConstruction.SANDBAGS || b == dev.groundburst.content.construction.GBConstruction.SANDBAG_SLAB;
                tag(sand ? BlockTags.MINEABLE_WITH_SHOVEL : BlockTags.MINEABLE_WITH_PICKAXE).add(b.block().key());
            }
            dev.groundburst.content.construction.GBConstruction.DOORS.forEach(b -> tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b.block().key()));
            dev.groundburst.content.construction.GBConstruction.STAIRS.forEach(b -> tag(BlockTags.STAIRS).add(b.block().key()));
            dev.groundburst.content.construction.GBConstruction.SLABS.forEach(b -> tag(BlockTags.SLABS).add(b.block().key()));
            for (Ore ore : GBResources.ores()) {
                var key = ore.block().block().key();
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(key);
                tag(switch (ore.tier()) {
                    case STONE -> BlockTags.NEEDS_STONE_TOOL;
                    case IRON -> BlockTags.NEEDS_IRON_TOOL;
                    case DIAMOND -> BlockTags.NEEDS_DIAMOND_TOOL;
                }).add(key);
                tag(Tags.BlockItems.ORES.block()).add(key);
                tag(GBTags.common("ores/" + ore.family()).block()).add(key);
                tag((ore.max() > 1 ? Tags.BlockItems.ORE_RATES_DENSE : Tags.BlockItems.ORE_RATES_SINGULAR).block()).add(key);
                switch (ore.host()) {
                    case STONE, SANDSTONE -> tag(Tags.BlockItems.ORES_IN_GROUND_STONE.block()).add(key);
                    case DEEPSLATE -> tag(Tags.BlockItems.ORES_IN_GROUND_DEEPSLATE.block()).add(key);
                    case NETHERRACK -> tag(Tags.BlockItems.ORES_IN_GROUND_NETHERRACK.block()).add(key);
                }
                if (ore.family().equals("uranium")) {
                    tag(GBTags.RADIOACTIVE.block()).add(key);
                }
            }
            storage(GBResources.URANIUM_BLOCK, "uranium", BlockTags.NEEDS_IRON_TOOL);
            storage(GBResources.LEAD_BLOCK, "lead", BlockTags.NEEDS_STONE_TOOL);
            storage(GBResources.TUNGSTEN_BLOCK, "tungsten", BlockTags.NEEDS_IRON_TOOL);
            storage(GBResources.RAW_URANIUM_BLOCK, "raw_uranium", BlockTags.NEEDS_IRON_TOOL);
            storage(GBResources.RAW_LITHIUM_BLOCK, "raw_lithium", BlockTags.NEEDS_STONE_TOOL);
            storage(GBResources.RAW_TUNGSTEN_BLOCK, "raw_tungsten", BlockTags.NEEDS_IRON_TOOL);
            storage(GBResources.RAW_LEAD_BLOCK, "raw_lead", BlockTags.NEEDS_STONE_TOOL);
            storage(GBResources.RAW_RARE_EARTH_BLOCK, "raw_rare_earth", BlockTags.NEEDS_IRON_TOOL);
            tag(GBTags.RADIOACTIVE.block()).add(GBResources.URANIUM_BLOCK.block().key(), GBResources.RAW_URANIUM_BLOCK.block().key());
            // What a reactor meltdown leaves: radioactive, and hard (an iron pickaxe or better).
            var corium = dev.groundburst.content.nuclear.GBNuclear.CORIUM.block().key();
            var cooledCorium = dev.groundburst.content.nuclear.GBNuclear.COOLED_CORIUM.block().key();
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(corium, cooledCorium);
            tag(BlockTags.NEEDS_IRON_TOOL).add(corium, cooledCorium);
            tag(GBTags.RADIOACTIVE.block()).add(corium, cooledCorium);

            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(GBResources.VOIDSTONE.block().key(), GBResources.BUDDING_VOIDSTONE.block().key(),
                    GBResources.SMALL_EXOTIC_BUD.block().key(), GBResources.LARGE_EXOTIC_BUD.block().key(),
                    GBResources.EXOTIC_CRYSTAL_CLUSTER.block().key());
            tag(BlockTags.NEEDS_IRON_TOOL).add(GBResources.VOIDSTONE.block().key());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(GBMachines.COMBUSTION_GENERATOR.block().key(), GBMachines.ORE_CRUSHER.block().key(),
                    GBMachines.ARC_FURNACE.block().key(), GBMachines.INDUSTRIAL_PRESS.block().key(),
                    GBMachines.ELECTROLYTIC_SEPARATOR.block().key(), GBMachines.CRYOGENIC_CONDENSER.block().key(),
                    GBMachines.CHEMICAL_PLANT.block().key(), GBMachines.WATER_PUMP.block().key(),
                    GBMachines.CIRCUIT_FABRICATOR.block().key(), GBMachines.GAS_CENTRIFUGE.block().key(),
                    GBMachines.FUSION_FUEL_SYNTHESIZER.block().key(), GBMachines.EXOTIC_MATTER_FABRICATOR.block().key(),
                    GBMachines.DIESEL_GENERATOR.block().key(), GBMachines.RTG.block().key(), GBMachines.BATTERY_CHARGER.block().key(),
                    GBMachines.DECONTAMINATION_SHOWER.block().key(), GBMachines.CANNING_MACHINE.block().key(), dev.groundburst.content.fluid.GBFluids.FLUID_TANK.block().key(),
                    dev.groundburst.content.fluid.GBFluids.CREATIVE_FLUID_TANK.block().key(),
                    GBMultiblocks.MACHINE_CASING.block().key(), GBMultiblocks.ITEM_PORT.block().key(),
                    GBMultiblocks.REACTOR_GLASS.block().key(), dev.groundburst.content.fluid.GBFluids.FLUID_PIPE.block().key(),
                    dev.groundburst.content.fluid.GBFluids.COPPER_FLUID_PIPE.block().key(),
                    dev.groundburst.content.fluid.GBFluids.PRESSURIZED_FLUID_PIPE.block().key(),
                    dev.groundburst.content.pipe.GBPipes.IRON_ITEM_PIPE.block().key(), dev.groundburst.content.pipe.GBPipes.ITEM_PIPE.block().key(),
                    dev.groundburst.content.pipe.GBPipes.PNEUMATIC_ITEM_PIPE.block().key());
            GBMachines.CABLES.forEach(c -> tag(BlockTags.MINEABLE_WITH_PICKAXE).add(c.block().key()));
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(dev.groundburst.content.assembly.GBAssembly.ASSEMBLY_BENCH.block().key());
            for (var b : java.util.List.of(dev.groundburst.content.launch.GBLaunch.LAUNCH_PAD, dev.groundburst.content.launch.GBLaunch.GANTRY_FRAME,
                    dev.groundburst.content.launch.GBLaunch.GANTRY_ARM, dev.groundburst.content.launch.GBLaunch.RADAR_MAST,
                    dev.groundburst.content.launch.GBLaunch.RADAR_DISH, dev.groundburst.content.launch.GBLaunch.ANTENNA)) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b.block().key());
            }
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(dev.groundburst.content.launch.GBLaunch.SILO_CONTROLLER.block().key(),
                    dev.groundburst.content.launch.GBLaunch.COMPUTER_CONTROLLER.block().key(),
                    dev.groundburst.content.launch.GBLaunch.STATION_CONTROLLER.block().key(),
                    dev.groundburst.content.launch.GBLaunch.RADAR_CONTROLLER.block().key(),
                    dev.groundburst.content.strategic.GBStrategic.CRANE_RAIL.block().key(),
                    dev.groundburst.content.strategic.GBStrategic.HALL_CONTROLLER.block().key(),
                    dev.groundburst.content.strategic.GBStrategic.SPECIAL_CONTROLLER.block().key(),
                    dev.groundburst.content.defence.GBDefence.BATTERY.block().key(),
                    dev.groundburst.content.defence.GBDefence.COMPLEX_CONTROLLER.block().key(),
                    dev.groundburst.content.defence.GBDefence.LAUNCH_CELL.block().key(),
                    dev.groundburst.content.defence.GBDefence.PHASED_ARRAY.block().key(),
                    dev.groundburst.content.launch.GBLaunch.LARGE_RADAR_DISH.block().key(),
                    dev.groundburst.content.launch.GBLaunch.MISSILE_SILO_CONTROLLER.block().key(),
                    dev.groundburst.content.launch.GBLaunch.CRUISE_CONTROLLER.block().key(),
                    dev.groundburst.content.launch.GBLaunch.LAUNCH_BOX.block().key());
            for (var c : dev.groundburst.content.assembler.AssemblerClass.values()) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(dev.groundburst.content.assembler.GBAssemblers.controller(c).block().key());
            }
            dev.groundburst.content.alarm.GBAlarms.all().forEach(b -> tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b.block().key()));
            for (var b : java.util.List.of(dev.groundburst.content.superweapon.GBSuperweapons.ADOBE_WALL, dev.groundburst.content.superweapon.GBSuperweapons.TOXIN_TANK, dev.groundburst.content.superweapon.GBSuperweapons.SCUD_ERECTOR, dev.groundburst.content.superweapon.GBSuperweapons.SCUD_STORM_CONTROLLER,
                    dev.groundburst.content.superweapon.GBSuperweapons.FIELDSTONE_WALL, dev.groundburst.content.superweapon.GBSuperweapons.MISSILE_ERECTOR, dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE_CONTROLLER, dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER_CONTROLLER,
                    dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY_CONTROLLER)) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b.block().key());
            }
            // The Missile Silo's concrete and hatch are bunker material: an iron pickaxe at least.
            for (var b : java.util.List.of(dev.groundburst.content.launch.GBLaunch.REINFORCED_CONCRETE, dev.groundburst.content.launch.GBLaunch.HAZARD_CONCRETE,
                    dev.groundburst.content.launch.GBLaunch.SILO_HATCH)) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b.block().key());
                tag(BlockTags.NEEDS_IRON_TOOL).add(b.block().key());
            }
            GBMachines.CAPACITOR_BANKS.forEach(b -> tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b.block().key()));
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(GBMachines.FISSION_REACTOR.block().key(), GBMachines.PARTICLE_ACCELERATOR.block().key(),
                    GBMachines.FUSION_REACTOR.block().key(), GBMachines.SINGULARITY_REACTOR.block().key(), GBMultiblocks.TOROIDAL_FIELD_COIL.block().key(),
                    GBMultiblocks.VACUUM_VESSEL.block().key(), GBMultiblocks.CENTRAL_SOLENOID.block().key(), GBMultiblocks.CONTAINMENT_EMITTER.block().key(),
                    GBMultiblocks.CORE_CRADLE.block().key(),
                    GBMultiblocks.LEAD_LINED_CASING.block().key(), GBMultiblocks.ENERGY_PORT.block().key(), GBMultiblocks.FLUID_PORT.block().key(), GBMultiblocks.STEEL_PLATING.block().key(),
                    GBMultiblocks.FUEL_CHANNEL.block().key(),
                    GBMultiblocks.MAGNET_SEGMENT.block().key(), GBMultiblocks.INJECTOR.block().key(),
                    GBMultiblocks.TARGET_CHAMBER.block().key());
            tag(Tags.BlockItems.BUDDING_BLOCKS.block()).add(GBResources.BUDDING_VOIDSTONE.block().key());
            tag(Tags.BlockItems.BUDS.block()).add(GBResources.SMALL_EXOTIC_BUD.block().key(), GBResources.LARGE_EXOTIC_BUD.block().key());
            tag(Tags.BlockItems.CLUSTERS.block()).add(GBResources.EXOTIC_CRYSTAL_CLUSTER.block().key());

            // The aftermath (slice 12b): the tools that break it; charred planks are planks; dead grass is a grass block.
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(dev.groundburst.content.fallout.GBFallout.TRINITITE.block().key(), dev.groundburst.content.fallout.GBFallout.GLASSED_SAND.block().key(),
                    dev.groundburst.content.fallout.GBFallout.SLAG.block().key(), dev.groundburst.content.fallout.GBFallout.SCORCHED_STONE.block().key());
            tag(BlockTags.MINEABLE_WITH_SHOVEL).add(dev.groundburst.content.fallout.GBFallout.SCORCHED_EARTH.block().key(), dev.groundburst.content.fallout.GBFallout.IRRADIATED_SOIL.block().key(),
                    dev.groundburst.content.fallout.GBFallout.ASH.block().key(), dev.groundburst.content.fallout.GBFallout.FALLOUT_ASH.block().key(), dev.groundburst.content.fallout.GBFallout.DEAD_GRASS_BLOCK.block().key());
            tag(BlockTags.MINEABLE_WITH_AXE).add(dev.groundburst.content.fallout.GBFallout.CHARRED_LOG.block().key(), dev.groundburst.content.fallout.GBFallout.CHARRED_WOOD.block().key(),
                    dev.groundburst.content.fallout.GBFallout.CHARRED_PLANKS.block().key());
            tag(BlockTags.MINEABLE_WITH_HOE).add(dev.groundburst.content.fallout.GBFallout.DEAD_LEAVES.block().key());
            tag(BlockTags.PLANKS).add(dev.groundburst.content.fallout.GBFallout.CHARRED_PLANKS.block().key());
            tag(BlockTags.GRASS_BLOCKS).add(dev.groundburst.content.fallout.GBFallout.DEAD_GRASS_BLOCK.block().key(), dev.groundburst.content.fallout.GBFallout.FROSTED_GRASS_BLOCK.block().key());
            // Slice 12e: frozen and toxic ground.
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(dev.groundburst.content.fallout.GBFallout.FROSTED_STONE.block().key(), dev.groundburst.content.fallout.GBFallout.CRYO_CRYSTAL.block().key());
            tag(BlockTags.MINEABLE_WITH_SHOVEL).add(dev.groundburst.content.fallout.GBFallout.FROSTED_GRASS_BLOCK.block().key(), dev.groundburst.content.fallout.GBFallout.TOXIC_SOIL.block().key());
            tag(BlockTags.DIRT).add(dev.groundburst.content.fallout.GBFallout.TOXIC_SOIL.block().key());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(dev.groundburst.content.salvage.GBSalvage.WASTE_DRUM.block().key());
            tag(BlockTags.NEEDS_STONE_TOOL).add(dev.groundburst.content.salvage.GBSalvage.WASTE_DRUM.block().key());

            // Radiation shielding (DESIGN.md §21.8): lead stops half of it in a fifth of a block, dense blocks in half one.
            tag(dev.groundburst.content.radiation.GBRadiation.SHIELD_LEAD).add(GBResources.LEAD_BLOCK.block().key(),
                    GBResources.RAW_LEAD_BLOCK.block().key(), GBMultiblocks.LEAD_LINED_CASING.block().key());
            tag(dev.groundburst.content.radiation.GBRadiation.SHIELD_DENSE).add(vanilla(net.minecraft.world.level.block.Blocks.OBSIDIAN),
                    vanilla(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN), vanilla(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE),
                    vanilla(net.minecraft.world.level.block.Blocks.IRON_BLOCK), vanilla(net.minecraft.world.level.block.Blocks.GOLD_BLOCK),
                    vanilla(net.minecraft.world.level.block.Blocks.NETHERITE_BLOCK), vanilla(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS),
                    GBResources.TUNGSTEN_BLOCK.block().key(), GBResources.URANIUM_BLOCK.block().key(),
                    dev.groundburst.content.launch.GBLaunch.REINFORCED_CONCRETE.block().key(),
                    dev.groundburst.content.launch.GBLaunch.HAZARD_CONCRETE.block().key())
                    .addTag(Tags.BlockItems.CONCRETES.block());
        }

        private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.Block> vanilla(net.minecraft.world.level.block.Block block) {
            return block.builtInRegistryHolder().key();
        }

        private void storage(WithItem<Block> block, String material, TagKey<Block> tier) {
            var key = block.block().key();
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(key);
            tag(tier).add(key);
            tag(Tags.BlockItems.STORAGE_BLOCKS.block()).add(key);
            tag(GBTags.common("storage_blocks/" + material).block()).add(key);
        }
    }

    public static class Items extends BlockTagCopyingItemTagProvider {
        public Items(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, CompletableFuture<TagsProvider.TagLookup<Block>> blockTags) {
            super(output, lookup, blockTags, GroundBurst.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            for (BlockItemTagId id : COPIED) {
                copy(id);
            }
            // Hazmat suits: repaired with what they are made of; armour tags for enchanting and other mods.
            tag(dev.groundburst.content.gear.GBArmor.REPAIRS_HAZMAT).add(dev.groundburst.content.chem.GBChemicals.PLASTIC_SHEET.key());
            tag(dev.groundburst.content.gear.GBArmor.REPAIRS_LEAD_HAZMAT).add(dev.groundburst.content.part.GBParts.LEAD_PLATE.key());
            for (var set : java.util.List.of(dev.groundburst.content.gear.GBArmor.HAZMAT_SET, dev.groundburst.content.gear.GBArmor.LEAD_HAZMAT_SET)) {
                tag(net.minecraft.tags.ItemTags.HEAD_ARMOR).add(set.get(0).key());
                tag(net.minecraft.tags.ItemTags.CHEST_ARMOR).add(set.get(1).key());
                tag(net.minecraft.tags.ItemTags.LEG_ARMOR).add(set.get(2).key());
                tag(net.minecraft.tags.ItemTags.FOOT_ARMOR).add(set.get(3).key());
                for (var piece : set) {
                    tag(Tags.Items.ARMORS_HUMANOID).add(piece.key());
                }
            }
            tag(net.minecraft.tags.ItemTags.PLANKS).add(dev.groundburst.content.fallout.GBFallout.CHARRED_PLANKS.item().key());
            item("raw_materials/uranium", GBResources.RAW_URANIUM, Tags.Items.RAW_MATERIALS);
            item("raw_materials/lithium", GBResources.RAW_LITHIUM, Tags.Items.RAW_MATERIALS);
            item("raw_materials/tungsten", GBResources.RAW_TUNGSTEN, Tags.Items.RAW_MATERIALS);
            item("raw_materials/lead", GBResources.RAW_LEAD, Tags.Items.RAW_MATERIALS);
            item("raw_materials/rare_earth", GBResources.RAW_RARE_EARTH, Tags.Items.RAW_MATERIALS);
            item("ingots/uranium", GBResources.URANIUM_INGOT, Tags.Items.INGOTS);
            item("ingots/lead", GBResources.LEAD_INGOT, Tags.Items.INGOTS);
            item("ingots/tungsten", GBResources.TUNGSTEN_INGOT, Tags.Items.INGOTS);
            item("dusts/sulfur", GBResources.SULFUR_DUST, Tags.Items.DUSTS);
            item("dusts/saltpeter", GBResources.SALTPETER_DUST, Tags.Items.DUSTS);
            item("dusts/uranium", GBResources.URANIUM_DUST, Tags.Items.DUSTS);
            item("dusts/lithium", GBResources.LITHIUM_DUST, Tags.Items.DUSTS);
            item("dusts/tungsten", GBResources.TUNGSTEN_DUST, Tags.Items.DUSTS);
            item("dusts/lead", GBResources.LEAD_DUST, Tags.Items.DUSTS);
            item("dusts/rare_earth", GBResources.RARE_EARTH_DUST, Tags.Items.DUSTS);
            item("dusts/cobalt", GBResources.COBALT_DUST, Tags.Items.DUSTS);
            item("dusts/iron", GBResources.IRON_DUST, Tags.Items.DUSTS);
            item("dusts/gold", GBResources.GOLD_DUST, Tags.Items.DUSTS);
            item("dusts/copper", GBResources.COPPER_DUST, Tags.Items.DUSTS);
            item("dusts/cinnabar", GBResources.CINNABAR_DUST, Tags.Items.DUSTS);
            item("gems/exotic_matter", GBResources.EXOTIC_SHARD, Tags.Items.GEMS);
            item("ingots/steel", GBParts.STEEL_INGOT, Tags.Items.INGOTS);
            item("ingots/cobalt", GBParts.COBALT_INGOT, Tags.Items.INGOTS);
            item("ingots/tungsten_carbide", GBParts.TUNGSTEN_CARBIDE_INGOT, Tags.Items.INGOTS);
            item("plates/steel", GBParts.STEEL_PLATE, GBTags.commonItem("plates"));
            item("plates/copper", GBParts.COPPER_PLATE, GBTags.commonItem("plates"));
            item("plates/lead", GBParts.LEAD_PLATE, GBTags.commonItem("plates"));
            item("plates/tungsten", GBParts.TUNGSTEN_PLATE, GBTags.commonItem("plates"));
            item("rods/steel", GBParts.STEEL_ROD, Tags.Items.RODS);
            item("rods/tungsten", GBParts.TUNGSTEN_ROD, Tags.Items.RODS);
            item("wires/copper", GBParts.COPPER_WIRE, GBTags.commonItem("wires"));
            item("wires/gold", GBParts.GOLD_WIRE, GBTags.commonItem("wires"));
            item("dusts/lithium_6", dev.groundburst.content.chem.GBChemicals.LITHIUM6_DUST, Tags.Items.DUSTS);
            item("dusts/depleted_uranium", dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_DUST, Tags.Items.DUSTS);
            item("ingots/depleted_uranium", dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_INGOT, Tags.Items.INGOTS);
            item("ingots/enriched_uranium", dev.groundburst.content.nuclear.GBNuclear.ENRICHED_URANIUM_INGOT, Tags.Items.INGOTS);
            item("nuggets/enriched_uranium", dev.groundburst.content.nuclear.GBNuclear.ENRICHED_URANIUM_NUGGET, Tags.Items.NUGGETS);
            // Circuits: Mekanism-style c:circuits/<tier> tags; the basic tier also takes the handmade board.
            item("circuits/basic", GBParts.BASIC_CIRCUIT, GBTags.commonItem("circuits"));
            item("circuits/advanced", GBParts.ADVANCED_CIRCUIT, GBTags.commonItem("circuits"));
            tag(GBTags.BASIC_CIRCUITS).add(GBParts.HANDMADE_CIRCUIT_BOARD.key()).addTag(GBTags.commonItem("circuits/basic"));
            tag(GBTags.PRESS_DIES).add(GBParts.PLATE_DIE.key(), GBParts.ROD_DIE.key(), GBParts.WIRE_DIE.key(), GBParts.CANISTER_DIE.key());
            tag(GBTags.RADIOACTIVE.item()).add(GBResources.RAW_URANIUM.key(), GBResources.URANIUM_INGOT.key(), GBResources.URANIUM_DUST.key(),
                    dev.groundburst.content.nuclear.GBNuclear.ENRICHED_URANIUM_NUGGET.key(), dev.groundburst.content.nuclear.GBNuclear.ENRICHED_URANIUM_INGOT.key(),
                    dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_DUST.key(), dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_INGOT.key(),
                    dev.groundburst.content.nuclear.GBNuclear.URANIUM_FUEL_ROD.key(), dev.groundburst.content.nuclear.GBNuclear.SPENT_FUEL_ROD.key(),
                    dev.groundburst.content.nuclear.GBNuclear.IRRADIATED_DU_TARGET_ROD.key(), dev.groundburst.content.nuclear.GBNuclear.IRRADIATED_LI6_TARGET_ROD.key(),
                    dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_NUGGET.key(), dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_INGOT.key(),
                    dev.groundburst.content.nuclear.GBNuclear.NUCLEAR_WASTE.key(), dev.groundburst.content.nuclear.GBNuclear.CORIUM.item().key(),
                    dev.groundburst.content.nuclear.GBNuclear.COOLED_CORIUM.item().key());
            item("nuggets/plutonium", dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_NUGGET, Tags.Items.NUGGETS);
            item("ingots/plutonium", dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_INGOT, Tags.Items.INGOTS);
        }

        private void item(String path, DeferredItem<?> item, TagKey<net.minecraft.world.item.Item> parent) {
            TagKey<net.minecraft.world.item.Item> specific = GBTags.commonItem(path);
            tag(specific).add(item.key());
            tag(parent).addTag(specific);
        }
    }
}
