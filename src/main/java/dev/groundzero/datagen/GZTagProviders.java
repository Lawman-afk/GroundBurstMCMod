package dev.groundzero.datagen;

import java.util.concurrent.CompletableFuture;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.part.GZParts;
import dev.groundzero.content.resource.GZResources;
import dev.groundzero.content.resource.GZResources.Ore;
import dev.groundzero.registry.GZBlocks.WithItem;
import dev.groundzero.registry.GZTags;
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
public final class GZTagProviders {
    private GZTagProviders() {}

    /** Block-item tag pairs filled for blocks and then copied to their items. */
    private static final BlockItemTagId[] COPIED = {
            Tags.BlockItems.ORES, Tags.BlockItems.ORES_IN_GROUND_STONE, Tags.BlockItems.ORES_IN_GROUND_DEEPSLATE,
            Tags.BlockItems.ORES_IN_GROUND_NETHERRACK, Tags.BlockItems.ORE_RATES_SINGULAR, Tags.BlockItems.ORE_RATES_DENSE,
            Tags.BlockItems.STORAGE_BLOCKS, Tags.BlockItems.BUDDING_BLOCKS, Tags.BlockItems.BUDS, Tags.BlockItems.CLUSTERS,
            GZTags.RADIOACTIVE,
            GZTags.common("ores/uranium"), GZTags.common("ores/lithium"), GZTags.common("ores/sulfur"), GZTags.common("ores/saltpeter"),
            GZTags.common("ores/tungsten"), GZTags.common("ores/lead"), GZTags.common("ores/rare_earth"),
            GZTags.common("storage_blocks/uranium"), GZTags.common("storage_blocks/lead"), GZTags.common("storage_blocks/tungsten"),
            GZTags.common("storage_blocks/raw_uranium"), GZTags.common("storage_blocks/raw_lithium"),
            GZTags.common("storage_blocks/raw_tungsten"), GZTags.common("storage_blocks/raw_lead"),
            GZTags.common("storage_blocks/raw_rare_earth"),
    };

    /** Common (c:) tags for our fluids so other mods' acids, fuels and gases are interchangeable with ours. */
    public static class FluidTags extends net.minecraft.data.tags.FluidTagsProvider {
        public FluidTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, GroundZero.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            for (dev.groundzero.content.fluid.ChemFluid f : dev.groundzero.content.fluid.GZFluids.all()) {
                tag(f.commonTag()).add(f.source().getKey(), f.flowing().getKey());
                if (f.isGas()) {
                    tag(Tags.Fluids.GASEOUS).add(f.source().getKey(), f.flowing().getKey());
                }
            }
        }
    }

    public static class Blocks extends BlockTagsProvider {
        public Blocks(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, GroundZero.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            for (Ore ore : GZResources.ores()) {
                var key = ore.block().block().key();
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(key);
                tag(switch (ore.tier()) {
                    case STONE -> BlockTags.NEEDS_STONE_TOOL;
                    case IRON -> BlockTags.NEEDS_IRON_TOOL;
                    case DIAMOND -> BlockTags.NEEDS_DIAMOND_TOOL;
                }).add(key);
                tag(Tags.BlockItems.ORES.block()).add(key);
                tag(GZTags.common("ores/" + ore.family()).block()).add(key);
                tag((ore.max() > 1 ? Tags.BlockItems.ORE_RATES_DENSE : Tags.BlockItems.ORE_RATES_SINGULAR).block()).add(key);
                switch (ore.host()) {
                    case STONE, SANDSTONE -> tag(Tags.BlockItems.ORES_IN_GROUND_STONE.block()).add(key);
                    case DEEPSLATE -> tag(Tags.BlockItems.ORES_IN_GROUND_DEEPSLATE.block()).add(key);
                    case NETHERRACK -> tag(Tags.BlockItems.ORES_IN_GROUND_NETHERRACK.block()).add(key);
                }
                if (ore.family().equals("uranium")) {
                    tag(GZTags.RADIOACTIVE.block()).add(key);
                }
            }
            storage(GZResources.URANIUM_BLOCK, "uranium", BlockTags.NEEDS_IRON_TOOL);
            storage(GZResources.LEAD_BLOCK, "lead", BlockTags.NEEDS_STONE_TOOL);
            storage(GZResources.TUNGSTEN_BLOCK, "tungsten", BlockTags.NEEDS_IRON_TOOL);
            storage(GZResources.RAW_URANIUM_BLOCK, "raw_uranium", BlockTags.NEEDS_IRON_TOOL);
            storage(GZResources.RAW_LITHIUM_BLOCK, "raw_lithium", BlockTags.NEEDS_STONE_TOOL);
            storage(GZResources.RAW_TUNGSTEN_BLOCK, "raw_tungsten", BlockTags.NEEDS_IRON_TOOL);
            storage(GZResources.RAW_LEAD_BLOCK, "raw_lead", BlockTags.NEEDS_STONE_TOOL);
            storage(GZResources.RAW_RARE_EARTH_BLOCK, "raw_rare_earth", BlockTags.NEEDS_IRON_TOOL);
            tag(GZTags.RADIOACTIVE.block()).add(GZResources.URANIUM_BLOCK.block().key(), GZResources.RAW_URANIUM_BLOCK.block().key());
            // What a reactor meltdown leaves: radioactive, and hard (an iron pickaxe or better).
            var corium = dev.groundzero.content.nuclear.GZNuclear.CORIUM.block().key();
            var cooledCorium = dev.groundzero.content.nuclear.GZNuclear.COOLED_CORIUM.block().key();
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(corium, cooledCorium);
            tag(BlockTags.NEEDS_IRON_TOOL).add(corium, cooledCorium);
            tag(GZTags.RADIOACTIVE.block()).add(corium, cooledCorium);

            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(GZResources.VOIDSTONE.block().key(), GZResources.BUDDING_VOIDSTONE.block().key(),
                    GZResources.SMALL_EXOTIC_BUD.block().key(), GZResources.LARGE_EXOTIC_BUD.block().key(),
                    GZResources.EXOTIC_CRYSTAL_CLUSTER.block().key());
            tag(BlockTags.NEEDS_IRON_TOOL).add(GZResources.VOIDSTONE.block().key());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(GZMachines.COMBUSTION_GENERATOR.block().key(), GZMachines.ORE_CRUSHER.block().key(),
                    GZMachines.ARC_FURNACE.block().key(), GZMachines.INDUSTRIAL_PRESS.block().key(),
                    GZMachines.ELECTROLYTIC_SEPARATOR.block().key(), GZMachines.CRYOGENIC_CONDENSER.block().key(),
                    GZMachines.CHEMICAL_PLANT.block().key(), GZMachines.WATER_PUMP.block().key(),
                    GZMachines.CIRCUIT_FABRICATOR.block().key(), GZMachines.GAS_CENTRIFUGE.block().key(),
                    GZMachines.FUSION_FUEL_SYNTHESIZER.block().key(), GZMachines.EXOTIC_MATTER_FABRICATOR.block().key(),
                    GZMachines.DIESEL_GENERATOR.block().key(), GZMachines.RTG.block().key(), GZMachines.BATTERY_CHARGER.block().key(),
                    GZMachines.DECONTAMINATION_SHOWER.block().key(), GZMachines.CANNING_MACHINE.block().key(), dev.groundzero.content.fluid.GZFluids.FLUID_TANK.block().key(),
                    dev.groundzero.content.fluid.GZFluids.CREATIVE_FLUID_TANK.block().key(),
                    GZMultiblocks.MACHINE_CASING.block().key(), GZMultiblocks.ITEM_PORT.block().key(),
                    GZMultiblocks.REACTOR_GLASS.block().key(), dev.groundzero.content.fluid.GZFluids.FLUID_PIPE.block().key(),
                    dev.groundzero.content.fluid.GZFluids.COPPER_FLUID_PIPE.block().key(),
                    dev.groundzero.content.fluid.GZFluids.PRESSURIZED_FLUID_PIPE.block().key(),
                    dev.groundzero.content.pipe.GZPipes.IRON_ITEM_PIPE.block().key(), dev.groundzero.content.pipe.GZPipes.ITEM_PIPE.block().key(),
                    dev.groundzero.content.pipe.GZPipes.PNEUMATIC_ITEM_PIPE.block().key());
            GZMachines.CABLES.forEach(c -> tag(BlockTags.MINEABLE_WITH_PICKAXE).add(c.block().key()));
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(dev.groundzero.content.assembly.GZAssembly.ASSEMBLY_BENCH.block().key());
            for (var b : java.util.List.of(dev.groundzero.content.launch.GZLaunch.LAUNCH_PAD, dev.groundzero.content.launch.GZLaunch.GANTRY_FRAME,
                    dev.groundzero.content.launch.GZLaunch.GANTRY_ARM, dev.groundzero.content.launch.GZLaunch.RADAR_MAST,
                    dev.groundzero.content.launch.GZLaunch.RADAR_DISH, dev.groundzero.content.launch.GZLaunch.ANTENNA)) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b.block().key());
            }
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(dev.groundzero.content.launch.GZLaunch.SILO_CONTROLLER.block().key(),
                    dev.groundzero.content.launch.GZLaunch.COMPUTER_CONTROLLER.block().key(),
                    dev.groundzero.content.launch.GZLaunch.RADAR_CONTROLLER.block().key(),
                    dev.groundzero.content.strategic.GZStrategic.CRANE_RAIL.block().key(),
                    dev.groundzero.content.strategic.GZStrategic.HALL_CONTROLLER.block().key(),
                    dev.groundzero.content.launch.GZLaunch.LARGE_RADAR_DISH.block().key(),
                    dev.groundzero.content.launch.GZLaunch.MISSILE_SILO_CONTROLLER.block().key());
            // The Missile Silo's concrete and hatch are bunker material: an iron pickaxe at least.
            for (var b : java.util.List.of(dev.groundzero.content.launch.GZLaunch.REINFORCED_CONCRETE, dev.groundzero.content.launch.GZLaunch.HAZARD_CONCRETE,
                    dev.groundzero.content.launch.GZLaunch.SILO_HATCH)) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b.block().key());
                tag(BlockTags.NEEDS_IRON_TOOL).add(b.block().key());
            }
            GZMachines.CAPACITOR_BANKS.forEach(b -> tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b.block().key()));
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(GZMachines.FISSION_REACTOR.block().key(), GZMachines.PARTICLE_ACCELERATOR.block().key(),
                    GZMachines.FUSION_REACTOR.block().key(), GZMachines.SINGULARITY_REACTOR.block().key(), GZMultiblocks.TOROIDAL_FIELD_COIL.block().key(),
                    GZMultiblocks.VACUUM_VESSEL.block().key(), GZMultiblocks.CENTRAL_SOLENOID.block().key(), GZMultiblocks.CONTAINMENT_EMITTER.block().key(),
                    GZMultiblocks.CORE_CRADLE.block().key(),
                    GZMultiblocks.LEAD_LINED_CASING.block().key(), GZMultiblocks.ENERGY_PORT.block().key(), GZMultiblocks.FLUID_PORT.block().key(), GZMultiblocks.STEEL_PLATING.block().key(),
                    GZMultiblocks.MAGNET_SEGMENT.block().key(), GZMultiblocks.INJECTOR.block().key(),
                    GZMultiblocks.TARGET_CHAMBER.block().key());
            tag(Tags.BlockItems.BUDDING_BLOCKS.block()).add(GZResources.BUDDING_VOIDSTONE.block().key());
            tag(Tags.BlockItems.BUDS.block()).add(GZResources.SMALL_EXOTIC_BUD.block().key(), GZResources.LARGE_EXOTIC_BUD.block().key());
            tag(Tags.BlockItems.CLUSTERS.block()).add(GZResources.EXOTIC_CRYSTAL_CLUSTER.block().key());
        }

        private void storage(WithItem<Block> block, String material, TagKey<Block> tier) {
            var key = block.block().key();
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(key);
            tag(tier).add(key);
            tag(Tags.BlockItems.STORAGE_BLOCKS.block()).add(key);
            tag(GZTags.common("storage_blocks/" + material).block()).add(key);
        }
    }

    public static class Items extends BlockTagCopyingItemTagProvider {
        public Items(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, CompletableFuture<TagsProvider.TagLookup<Block>> blockTags) {
            super(output, lookup, blockTags, GroundZero.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            for (BlockItemTagId id : COPIED) {
                copy(id);
            }
            // Hazmat suits: repaired with what they are made of; armour tags for enchanting and other mods.
            tag(dev.groundzero.content.gear.GZArmor.REPAIRS_HAZMAT).add(dev.groundzero.content.chem.GZChemicals.PLASTIC_SHEET.key());
            tag(dev.groundzero.content.gear.GZArmor.REPAIRS_LEAD_HAZMAT).add(dev.groundzero.content.part.GZParts.LEAD_PLATE.key());
            for (var set : java.util.List.of(dev.groundzero.content.gear.GZArmor.HAZMAT_SET, dev.groundzero.content.gear.GZArmor.LEAD_HAZMAT_SET)) {
                tag(net.minecraft.tags.ItemTags.HEAD_ARMOR).add(set.get(0).key());
                tag(net.minecraft.tags.ItemTags.CHEST_ARMOR).add(set.get(1).key());
                tag(net.minecraft.tags.ItemTags.LEG_ARMOR).add(set.get(2).key());
                tag(net.minecraft.tags.ItemTags.FOOT_ARMOR).add(set.get(3).key());
                for (var piece : set) {
                    tag(Tags.Items.ARMORS_HUMANOID).add(piece.key());
                }
            }
            item("raw_materials/uranium", GZResources.RAW_URANIUM, Tags.Items.RAW_MATERIALS);
            item("raw_materials/lithium", GZResources.RAW_LITHIUM, Tags.Items.RAW_MATERIALS);
            item("raw_materials/tungsten", GZResources.RAW_TUNGSTEN, Tags.Items.RAW_MATERIALS);
            item("raw_materials/lead", GZResources.RAW_LEAD, Tags.Items.RAW_MATERIALS);
            item("raw_materials/rare_earth", GZResources.RAW_RARE_EARTH, Tags.Items.RAW_MATERIALS);
            item("ingots/uranium", GZResources.URANIUM_INGOT, Tags.Items.INGOTS);
            item("ingots/lead", GZResources.LEAD_INGOT, Tags.Items.INGOTS);
            item("ingots/tungsten", GZResources.TUNGSTEN_INGOT, Tags.Items.INGOTS);
            item("dusts/sulfur", GZResources.SULFUR_DUST, Tags.Items.DUSTS);
            item("dusts/saltpeter", GZResources.SALTPETER_DUST, Tags.Items.DUSTS);
            item("dusts/uranium", GZResources.URANIUM_DUST, Tags.Items.DUSTS);
            item("dusts/lithium", GZResources.LITHIUM_DUST, Tags.Items.DUSTS);
            item("dusts/tungsten", GZResources.TUNGSTEN_DUST, Tags.Items.DUSTS);
            item("dusts/lead", GZResources.LEAD_DUST, Tags.Items.DUSTS);
            item("dusts/rare_earth", GZResources.RARE_EARTH_DUST, Tags.Items.DUSTS);
            item("dusts/cobalt", GZResources.COBALT_DUST, Tags.Items.DUSTS);
            item("dusts/iron", GZResources.IRON_DUST, Tags.Items.DUSTS);
            item("dusts/gold", GZResources.GOLD_DUST, Tags.Items.DUSTS);
            item("dusts/copper", GZResources.COPPER_DUST, Tags.Items.DUSTS);
            item("dusts/cinnabar", GZResources.CINNABAR_DUST, Tags.Items.DUSTS);
            item("gems/exotic_matter", GZResources.EXOTIC_SHARD, Tags.Items.GEMS);
            item("ingots/steel", GZParts.STEEL_INGOT, Tags.Items.INGOTS);
            item("ingots/cobalt", GZParts.COBALT_INGOT, Tags.Items.INGOTS);
            item("ingots/tungsten_carbide", GZParts.TUNGSTEN_CARBIDE_INGOT, Tags.Items.INGOTS);
            item("plates/steel", GZParts.STEEL_PLATE, GZTags.commonItem("plates"));
            item("plates/copper", GZParts.COPPER_PLATE, GZTags.commonItem("plates"));
            item("plates/lead", GZParts.LEAD_PLATE, GZTags.commonItem("plates"));
            item("plates/tungsten", GZParts.TUNGSTEN_PLATE, GZTags.commonItem("plates"));
            item("rods/steel", GZParts.STEEL_ROD, Tags.Items.RODS);
            item("rods/tungsten", GZParts.TUNGSTEN_ROD, Tags.Items.RODS);
            item("wires/copper", GZParts.COPPER_WIRE, GZTags.commonItem("wires"));
            item("wires/gold", GZParts.GOLD_WIRE, GZTags.commonItem("wires"));
            item("dusts/lithium_6", dev.groundzero.content.chem.GZChemicals.LITHIUM6_DUST, Tags.Items.DUSTS);
            item("dusts/depleted_uranium", dev.groundzero.content.nuclear.GZNuclear.DEPLETED_URANIUM_DUST, Tags.Items.DUSTS);
            item("ingots/depleted_uranium", dev.groundzero.content.nuclear.GZNuclear.DEPLETED_URANIUM_INGOT, Tags.Items.INGOTS);
            item("ingots/enriched_uranium", dev.groundzero.content.nuclear.GZNuclear.ENRICHED_URANIUM_INGOT, Tags.Items.INGOTS);
            item("nuggets/enriched_uranium", dev.groundzero.content.nuclear.GZNuclear.ENRICHED_URANIUM_NUGGET, Tags.Items.NUGGETS);
            // Circuits: Mekanism-style c:circuits/<tier> tags; the basic tier also takes the handmade board.
            item("circuits/basic", GZParts.BASIC_CIRCUIT, GZTags.commonItem("circuits"));
            item("circuits/advanced", GZParts.ADVANCED_CIRCUIT, GZTags.commonItem("circuits"));
            tag(GZTags.BASIC_CIRCUITS).add(GZParts.HANDMADE_CIRCUIT_BOARD.key()).addTag(GZTags.commonItem("circuits/basic"));
            tag(GZTags.PRESS_DIES).add(GZParts.PLATE_DIE.key(), GZParts.ROD_DIE.key(), GZParts.WIRE_DIE.key(), GZParts.CANISTER_DIE.key());
            tag(GZTags.RADIOACTIVE.item()).add(GZResources.RAW_URANIUM.key(), GZResources.URANIUM_INGOT.key(), GZResources.URANIUM_DUST.key(),
                    dev.groundzero.content.nuclear.GZNuclear.ENRICHED_URANIUM_NUGGET.key(), dev.groundzero.content.nuclear.GZNuclear.ENRICHED_URANIUM_INGOT.key(),
                    dev.groundzero.content.nuclear.GZNuclear.DEPLETED_URANIUM_DUST.key(), dev.groundzero.content.nuclear.GZNuclear.DEPLETED_URANIUM_INGOT.key(),
                    dev.groundzero.content.nuclear.GZNuclear.URANIUM_FUEL_ROD.key(), dev.groundzero.content.nuclear.GZNuclear.SPENT_FUEL_ROD.key(),
                    dev.groundzero.content.nuclear.GZNuclear.IRRADIATED_DU_TARGET_ROD.key(), dev.groundzero.content.nuclear.GZNuclear.IRRADIATED_LI6_TARGET_ROD.key(),
                    dev.groundzero.content.nuclear.GZNuclear.PLUTONIUM_NUGGET.key(), dev.groundzero.content.nuclear.GZNuclear.PLUTONIUM_INGOT.key(),
                    dev.groundzero.content.nuclear.GZNuclear.NUCLEAR_WASTE.key(), dev.groundzero.content.nuclear.GZNuclear.CORIUM.item().key(),
                    dev.groundzero.content.nuclear.GZNuclear.COOLED_CORIUM.item().key());
            item("nuggets/plutonium", dev.groundzero.content.nuclear.GZNuclear.PLUTONIUM_NUGGET, Tags.Items.NUGGETS);
            item("ingots/plutonium", dev.groundzero.content.nuclear.GZNuclear.PLUTONIUM_INGOT, Tags.Items.INGOTS);
        }

        private void item(String path, DeferredItem<?> item, TagKey<net.minecraft.world.item.Item> parent) {
            TagKey<net.minecraft.world.item.Item> specific = GZTags.commonItem(path);
            tag(specific).add(item.key());
            tag(parent).addTag(specific);
        }
    }
}
