package dev.groundzero.gametest;

import java.util.List;

import dev.groundzero.GroundZero;
import dev.groundzero.content.gear.GZArmor;
import dev.groundzero.world.structure.GZStructures;
import dev.groundzero.world.structure.SitePiece;
import dev.groundzero.world.structure.SiteStructure;
import dev.groundzero.world.structure.Weathering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 15 (DESIGN.md §22): the sites' templates load, their structures, sets and biome tags are registered, every
 * chest loot table rolls something, and a site piece places its template with its chest's loot table set and its
 * data marker turned into a suited stand.
 */
final class StructureTests {
    static final List<String> TEMPLATES = List.of("silo_surface_intact", "silo_surface_flooded", "silo_surface_collapsed",
            "silo_below_intact", "silo_below_flooded", "silo_below_collapsed", "bunker_surface", "bunker_below", "radar_outpost",
            "test_site_zero", "test_site_bunker", "crashed_missile", "waste_storage", "research_station");
    static final List<String> LOOT = List.of("silo_control", "silo_shaft", "bunker_armory", "bunker_stores", "radar_outpost", "test_site",
            "crashed_missile", "waste_storage", "research_station");

    private StructureTests() {}

    static void register() {
        GZGameTests.test("site_templates_load", GZGameTests.FLOOR_16, 20, StructureTests::templates);
        GZGameTests.test("site_structures_and_sets_registered", GZGameTests.FLOOR_16, 20, StructureTests::registered);
        GZGameTests.test("site_loot_tables_roll", GZGameTests.FLOOR_16, 20, StructureTests::loot);
        GZGameTests.test("site_piece_sets_loot_and_stands", GZGameTests.FLOOR_16, 20, StructureTests::piece);
    }

    private static void templates(GameTestHelper helper) {
        var manager = helper.getLevel().getStructureTemplateManager();
        for (String name : TEMPLATES) {
            var t = manager.get(GroundZero.id("site/" + name));
            helper.assertTrue(t.isPresent(), "template " + name + " loads");
            helper.assertTrue(t.get().getSize().getX() > 4 && t.get().getSize().getY() > 3, "template " + name + " has size");
        }
        helper.succeed();
    }

    private static void registered(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var structures = access.lookupOrThrow(Registries.STRUCTURE);
        var sets = access.lookupOrThrow(Registries.STRUCTURE_SET);
        var biomes = access.lookupOrThrow(Registries.BIOME);
        for (String name : GZStructures.NAMES) {
            var s = structures.get(GZStructures.structure(name));
            helper.assertTrue(s.isPresent() && s.get().value() instanceof SiteStructure, "structure " + name);
            helper.assertTrue(sets.get(GZStructures.set(name)).isPresent(), "structure set " + name);
            helper.assertTrue(biomes.get(GZStructures.biomes(name)).map(h -> h.size() > 0).orElse(false), "biomes for " + name);
        }
        helper.succeed();
    }

    private static void loot(GameTestHelper helper) {
        var level = helper.getLevel();
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                .create(LootContextParamSets.CHEST);
        for (String name : LOOT) {
            var table = level.getServer().reloadableRegistries().getLootTable(GZStructures.loot(name));
            int items = 0;
            for (int i = 0; i < 6; i++) {
                items += table.getRandomItems(params).stream().mapToInt(ItemStack::getCount).sum();
            }
            helper.assertTrue(items > 0, "loot " + name + " rolls something");
        }
        helper.succeed();
    }

    private static void piece(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(5, 1, 5));
        var part = new SiteStructure.Part(GroundZero.id("site/test_room"), BlockPos.ZERO, TerrainAdjustment.NONE, 1f, Weathering.NONE);
        SitePiece p = new SitePiece(level.getStructureTemplateManager(), part, corner, Rotation.NONE);
        BoundingBox box = p.getBoundingBox();
        p.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), box,
                ChunkPos.containing(corner), corner);
        var chest = level.getBlockEntity(corner.offset(1, 1, 1));
        helper.assertTrue(chest instanceof ChestBlockEntity c && c.getLootTable() != null
                && c.getLootTable().equals(GZStructures.loot("waste_storage")), "the chest keeps its loot table");
        var stands = level.getEntitiesOfClass(ArmorStand.class, new AABB(corner).inflate(6));
        helper.assertTrue(stands.size() == 1, "the marker became a stand (" + stands.size() + ")");
        ArmorStand stand = stands.getFirst();
        int worn = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            worn += GZArmor.HAZMAT_SET.stream().anyMatch(i -> stand.getItemBySlot(slot).is(i.get())) ? 1 : 0;
        }
        helper.assertTrue(worn >= 1, "in a hazmat suit (" + worn + " pieces)");
        helper.assertFalse(level.getBlockState(corner.offset(3, 1, 3)).is(net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK),
                "and the marker is gone");
        stand.discard();
        helper.succeed();
    }
}
