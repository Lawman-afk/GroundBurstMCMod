package dev.groundzero.datagen;

import java.util.concurrent.CompletableFuture;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.GZMachines;
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

            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(GZResources.VOIDSTONE.block().key(), GZResources.BUDDING_VOIDSTONE.block().key(),
                    GZResources.SMALL_EXOTIC_BUD.block().key(), GZResources.LARGE_EXOTIC_BUD.block().key(),
                    GZResources.EXOTIC_CRYSTAL_CLUSTER.block().key());
            tag(BlockTags.NEEDS_IRON_TOOL).add(GZResources.VOIDSTONE.block().key());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(GZMachines.COMBUSTION_GENERATOR.block().key(), GZMachines.ORE_CRUSHER.block().key(),
                    GZMachines.ARC_FURNACE.block().key(), GZMachines.INDUSTRIAL_PRESS.block().key(),
                    GZMachines.ELECTROLYTIC_SEPARATOR.block().key(), GZMachines.CRYOGENIC_CONDENSER.block().key());
            GZMachines.CABLES.forEach(c -> tag(BlockTags.MINEABLE_WITH_PICKAXE).add(c.block().key()));
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
            tag(GZTags.BASIC_CIRCUITS).add(GZParts.HANDMADE_CIRCUIT_BOARD.key());
            tag(GZTags.PRESS_DIES).add(GZParts.PLATE_DIE.key(), GZParts.ROD_DIE.key(), GZParts.WIRE_DIE.key(), GZParts.CANISTER_DIE.key());
            tag(GZTags.RADIOACTIVE.item()).add(GZResources.RAW_URANIUM.key(), GZResources.URANIUM_INGOT.key(), GZResources.URANIUM_DUST.key());
        }

        private void item(String path, DeferredItem<?> item, TagKey<net.minecraft.world.item.Item> parent) {
            TagKey<net.minecraft.world.item.Item> specific = GZTags.commonItem(path);
            tag(specific).add(item.key());
            tag(parent).addTag(specific);
        }
    }
}
