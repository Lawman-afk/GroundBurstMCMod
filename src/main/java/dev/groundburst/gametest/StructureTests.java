package dev.groundburst.gametest;

import java.util.List;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.gear.GBArmor;
import dev.groundburst.world.structure.GBStructures;
import dev.groundburst.world.structure.SitePiece;
import dev.groundburst.world.structure.SiteStructure;
import dev.groundburst.world.structure.Weathering;
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
        GBGameTests.test("site_templates_load", GBGameTests.FLOOR_16, 20, StructureTests::templates);
        GBGameTests.test("site_structures_and_sets_registered", GBGameTests.FLOOR_16, 20, StructureTests::registered);
        GBGameTests.test("site_loot_tables_roll", GBGameTests.FLOOR_16, 20, StructureTests::loot);
        GBGameTests.test("site_piece_sets_loot_and_stands", GBGameTests.FLOOR_16, 20, StructureTests::piece);
        GBGameTests.test("site_parts_meet_the_ground_their_way", GBGameTests.FLOOR_16, 20, StructureTests::ground);
    }

    private static void templates(GameTestHelper helper) {
        var manager = helper.getLevel().getStructureTemplateManager();
        for (String name : TEMPLATES) {
            var t = manager.get(GroundBurst.id("site/" + name));
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
        for (String name : GBStructures.NAMES) {
            var s = structures.get(GBStructures.structure(name));
            helper.assertTrue(s.isPresent() && s.get().value() instanceof SiteStructure, "structure " + name);
            helper.assertTrue(sets.get(GBStructures.set(name)).isPresent(), "structure set " + name);
            helper.assertTrue(biomes.get(GBStructures.biomes(name)).map(h -> h.size() > 0).orElse(false), "biomes for " + name);
        }
        helper.succeed();
    }

    private static void loot(GameTestHelper helper) {
        var level = helper.getLevel();
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                .create(LootContextParamSets.CHEST);
        for (String name : LOOT) {
            var table = level.getServer().reloadableRegistries().getLootTable(GBStructures.loot(name));
            int items = 0;
            for (int i = 0; i < 6; i++) {
                items += table.getRandomItems(params).stream().mapToInt(ItemStack::getCount).sum();
            }
            helper.assertTrue(items > 0, "loot " + name + " rolls something");
        }
        helper.succeed();
    }

    /**
     * How the ground meets a site's parts (found on a generated world by the world survey): a part sunk below the site's
     * ground (a crater, a furrow) has the land smoothed to its rim, not its bottom; rock closed round a part underground
     * stops 13 blocks under the ground, so it is never heaped over the site; a part deep down keeps all of its own.
     */
    private static void ground(GameTestHelper helper) {
        var templates = helper.getLevel().getStructureTemplateManager();
        BlockPos origin = new BlockPos(0, 70, 0);
        var sunk = new SiteStructure.Part(GroundBurst.id("site/test_room"), new BlockPos(0, -3, 0), TerrainAdjustment.BEARD_THIN, 1f, Weathering.NONE);
        SitePiece crater = new SitePiece(templates, sunk, origin.offset(sunk.offset()), Rotation.NONE, origin.getY());
        // Its rim is 3 up from its bottom; the beardifier wants the first air above the land, one more.
        helper.assertTrue(crater.getGroundLevelDelta() == 4, "a sunk part meets the ground at its rim (" + crater.getGroundLevelDelta() + ")");
        var under = new SiteStructure.Part(GroundBurst.id("site/test_room"), new BlockPos(0, -6, 0), TerrainAdjustment.ENCAPSULATE, 1f, Weathering.NONE);
        SitePiece rooms = new SitePiece(templates, under, origin.offset(under.offset()), Rotation.NONE, origin.getY());
        helper.assertTrue(rooms.getBeardifierBox().maxY() <= origin.getY() - 13 || rooms.getBeardifierBox().maxY() == rooms.getBoundingBox().minY(),
                "rock round rooms just under the ground stops short of it (" + rooms.getBeardifierBox() + ")");
        var deep = new SiteStructure.Part(GroundBurst.id("site/test_room"), new BlockPos(0, -40, 0), TerrainAdjustment.ENCAPSULATE, 1f, Weathering.NONE);
        SitePiece store = new SitePiece(templates, deep, origin.offset(deep.offset()), Rotation.NONE, origin.getY());
        helper.assertTrue(store.getBeardifierBox().equals(store.getBoundingBox()), "rooms deep down are closed in all round");
        helper.succeed();
    }

    private static void piece(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(5, 1, 5));
        var part = new SiteStructure.Part(GroundBurst.id("site/test_room"), BlockPos.ZERO, TerrainAdjustment.NONE, 1f, Weathering.NONE);
        SitePiece p = new SitePiece(level.getStructureTemplateManager(), part, corner, Rotation.NONE, corner.getY());
        BoundingBox box = p.getBoundingBox();
        p.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), box,
                ChunkPos.containing(corner), corner);
        var chest = level.getBlockEntity(corner.offset(1, 1, 1));
        helper.assertTrue(chest instanceof ChestBlockEntity c && c.getLootTable() != null
                && c.getLootTable().equals(GBStructures.loot("waste_storage")), "the chest keeps its loot table");
        var stands = level.getEntitiesOfClass(ArmorStand.class, new AABB(corner).inflate(6));
        helper.assertTrue(stands.size() == 1, "the marker became a stand (" + stands.size() + ")");
        ArmorStand stand = stands.getFirst();
        int worn = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            worn += GBArmor.HAZMAT_SET.stream().anyMatch(i -> stand.getItemBySlot(slot).is(i.get())) ? 1 : 0;
        }
        helper.assertTrue(worn >= 1, "in a hazmat suit (" + worn + " pieces)");
        helper.assertFalse(level.getBlockState(corner.offset(3, 1, 3)).is(net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK),
                "and the marker is gone");
        stand.discard();
        helper.succeed();
    }
}
