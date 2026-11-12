package dev.groundburst.gametest;

import java.util.List;

import dev.groundburst.blast.TerrainTable;
import dev.groundburst.content.fallout.AshLayerBlock;
import dev.groundburst.content.fallout.FalloutAshBlock;
import dev.groundburst.content.fallout.FalloutPlume;
import dev.groundburst.content.fallout.GBFallout;
import dev.groundburst.content.fallout.Plumes;
import dev.groundburst.content.fallout.Wind;
import dev.groundburst.content.fallout.Withering;
import dev.groundburst.content.radiation.RadiationSource;
import dev.groundburst.content.radiation.RadiationSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Slice 12b (DESIGN.md §21.8): the wind, fallout plumes and the ash they lay, the nuclear terrain table and the crater's
 * lining, plants dying in strong fields, dead grass under cover.
 */
final class FalloutTests {
    private FalloutTests() {}

    static void register() {
        GBGameTests.test("wind_blows_the_way_it_heads", GBGameTests.FLOOR_16, 20, FalloutTests::wind);
        // Open sky: ash settles on whatever is uppermost, and an ordinary plot has a lid.
        GBGameTests.testOpenSky("plume_drifts_downwind_laying_fallout", GBGameTests.FLOOR_16, 20, FalloutTests::plume);
        GBGameTests.testOpenSky("fallout_ash_settles_and_weathers", GBGameTests.FLOOR_16, 20, FalloutTests::ash);
        GBGameTests.test("nuclear_table_burns_and_lines_the_ground", GBGameTests.FLOOR_16, 20, FalloutTests::table);
        GBGameTests.test("terrain_tables_come_from_data", GBGameTests.FLOOR_16, 20, FalloutTests::tablesFromData);
        GBGameTests.test("terrain_swap_rules_fall_through", GBGameTests.FLOOR_16, 20, FalloutTests::swapRules);
        GBGameTests.test("black_rain_where_the_land_is_irradiated", GBGameTests.FLOOR_16, 20, FalloutTests::blackRainWhere);
        GBGameTests.testOpenSky("black_rain_kills_plants_and_burns", GBGameTests.FLOOR_16, 40, FalloutTests::blackRainHarm);
        GBGameTests.testOpenSky("strong_fields_wither_plants", GBGameTests.FLOOR_16, 20, FalloutTests::wither);
        GBGameTests.test("dead_grass_under_cover_turns_to_dirt", GBGameTests.FLOOR_16, 20, FalloutTests::covered);
        GBGameTests.testOpenSky("nuclear_blast_glasses_the_sand_round_it", GBGameTests.FLOOR_16, 200, FalloutTests::glassed);
        // Three craters (each blast has a seed of its own): a lone block survived now and then before the floor sweep.
        for (int k = 1; k <= 3; k++) {
            int shift = k - 2;                             // (each its own function: the registry takes a function once)
            GBGameTests.testOpenSky("crater_leaves_no_lone_blocks_standing_" + k, GBGameTests.FLOOR_16, 200, h -> noLoneBlocks(h, shift));
        }
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /** Heading 0 blows south (+z), a quarter turn east (+x); the vector is as long as the speed. */
    private static void wind(GameTestHelper helper) {
        Vec3 south = Wind.vector(0, 3), east = Wind.vector((float) Math.PI / 2, 2);
        helper.assertTrue(south.z > 2.99 && Math.abs(south.x) < 1e-6, "heading 0 is south: " + south);
        helper.assertTrue(east.x > 1.99 && Math.abs(east.z) < 1e-6, "a quarter turn is east: " + east);
        helper.succeed();
    }

    /**
     * A plume carried east 2 blocks a second along the plot: it lays sources as it goes, each further east, weaker and
     * with the burst's half-life, lets ash fall under it, and is spent at its reach.
     */
    private static void plume(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 8.5));
        int halfLife = Plumes.halfLife(40_000, 720_000);
        FalloutPlume plume = new FalloutPlume(start.x, start.y, start.z, 2, 11, 0, 0, 40_000, halfLife, 30, helper.getLevel().getGameTime(), false);
        long now = level.getGameTime();
        List<RadiationSource> before = RadiationSources.of(level).sources();
        for (int s = 0; s < 12 && !plume.spent(); s++) {
            plume = Plumes.step(level, plume, new Vec3(2, 0, 0), level.getRandom(), now);
        }
        List<RadiationSource> laid = RadiationSources.of(level).sources().stream().filter(z -> !before.contains(z))
                .filter(z -> Math.abs(z.center().z - start.z) < 2 && z.center().x > start.x).sorted((a, b) -> Double.compare(a.center().x, b.center().x))
                .toList();
        for (RadiationSource z : laid) {
            RadiationSources.of(level).removeNear(z.center(), 0.01);
        }
        helper.assertTrue(plume.spent(), "spent at its reach: " + plume);
        helper.assertTrue(laid.size() >= 3, "it laid its fallout as it went: " + laid);
        for (int i = 0; i < laid.size(); i++) {
            helper.assertTrue(laid.get(i).halfLife() == halfLife, "the burst's half-life: " + laid.get(i));
            helper.assertTrue(i == 0 || laid.get(i).peak() < laid.get(i - 1).peak(), "weaker downwind: " + laid);
        }
        int ash = 0;
        for (BlockPos p : BlockPos.betweenClosed(0, 1, 4, 15, 1, 12)) {
            if (helper.getBlockState(p).is(GBFallout.FALLOUT_ASH.block().get())) {
                ash++;
            }
        }
        helper.assertTrue(ash > 0, "Fallout Ash under its path");
        helper.succeed();
    }

    /** Ash settles a layer at a time, on ground but not in the air over water; Fallout Ash weathers into plain ash. */
    private static void ash(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        BlockPos rel = new BlockPos(4, 1, 4);
        BlockPos at = helper.absolutePos(rel);
        Plumes.settle(level, at.getX(), at.getZ());
        helper.assertBlockProperty(rel, AshLayerBlock.LAYERS, 1);
        Plumes.settle(level, at.getX(), at.getZ());
        helper.assertBlockProperty(rel, AshLayerBlock.LAYERS, 2);
        BlockState ash = level.getBlockState(at);
        ((FalloutAshBlock) ash.getBlock()).weather(ash, level, at);
        helper.assertBlockPresent(GBFallout.ASH.block().get(), rel);
        helper.assertBlockProperty(rel, AshLayerBlock.LAYERS, 2);
        helper.setBlock(new BlockPos(10, 0, 10), Blocks.WATER);
        BlockPos wet = helper.absolutePos(new BlockPos(10, 1, 10));
        Plumes.settle(level, wet.getX(), wet.getZ());
        helper.assertBlockNotPresent(GBFallout.FALLOUT_ASH.block().get(), new BlockPos(10, 1, 10));
        helper.succeed();
    }

    /** The nuclear table: earth, stone, sand and wood burnt; the crater's wall slag, trinitite and irradiated soil. */
    private static void table(GameTestHelper helper) {
        TerrainTable nuclear = TerrainTable.byId(TerrainTable.NUCLEAR), conventional = TerrainTable.byId(TerrainTable.CONVENTIONAL);
        helper.assertTrue(nuclear.scorched(Blocks.GRASS_BLOCK.defaultBlockState()).is(GBFallout.SCORCHED_EARTH.block().get()), "grass to scorched earth");
        helper.assertTrue(nuclear.scorched(Blocks.STONE.defaultBlockState()).is(GBFallout.SCORCHED_STONE.block().get()), "stone to scorched stone");
        helper.assertTrue(nuclear.scorched(Blocks.SAND.defaultBlockState()).is(GBFallout.GLASSED_SAND.block().get()), "sand glassed");
        helper.assertTrue(nuclear.scorched(Blocks.SANDSTONE.defaultBlockState()).is(GBFallout.GLASSED_SAND.block().get()), "sandstone glassed");
        helper.assertTrue(nuclear.scorched(Blocks.DEEPSLATE.defaultBlockState()).is(GBFallout.SCORCHED_STONE.block().get()), "deepslate scorched");
        BlockState log = nuclear.scorched(Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        helper.assertTrue(log.is(GBFallout.CHARRED_LOG.block().get()) && log.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "a log charred, lying as it lay");
        helper.assertTrue(nuclear.scorched(Blocks.OAK_WOOD.defaultBlockState()).is(GBFallout.CHARRED_WOOD.block().get()), "wood charred");
        helper.assertTrue(nuclear.scorched(Blocks.SPRUCE_PLANKS.defaultBlockState()).is(GBFallout.CHARRED_PLANKS.block().get()), "planks charred");
        helper.assertTrue(conventional.scorched(Blocks.GRASS_BLOCK.defaultBlockState()).is(Blocks.DIRT), "a conventional blast only burns grass to dirt");
        helper.assertTrue(nuclear.lining(Blocks.STONE.defaultBlockState()).is(GBFallout.SLAG.block().get()), "rock melts to slag");
        helper.assertTrue(nuclear.lining(Blocks.SAND.defaultBlockState()).is(GBFallout.TRINITITE.block().get()), "sand fuses to trinitite");
        helper.assertTrue(nuclear.lining(Blocks.SANDSTONE.defaultBlockState()).is(GBFallout.TRINITITE.block().get()), "and sandstone");
        helper.assertTrue(nuclear.lining(Blocks.DIRT.defaultBlockState()).is(GBFallout.IRRADIATED_SOIL.block().get()), "earth irradiated");
        helper.assertTrue(conventional.lining(Blocks.STONE.defaultBlockState()) == null, "a conventional crater is not lined");
        helper.succeed();
    }

    /**
     * The terrain tables are the server's data (DESIGN.md §21.10): the three of them loaded from their files, the shock's
     * blocks and the scorching and lining as the code before them did, ice melted only by a nuclear blast, an unknown id
     * read as the conventional table; the far rings' rules there for the nuclear table alone.
     */
    private static void tablesFromData(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().lookupOrThrow(TerrainTable.REGISTRY);
        helper.assertTrue(registry.containsKey(TerrainTable.NUCLEAR) && registry.containsKey(TerrainTable.CONVENTIONAL)
                && registry.containsKey(TerrainTable.NAVAL), "the three tables are in the server's data");
        TerrainTable nuclear = TerrainTable.byId(TerrainTable.NUCLEAR), conventional = TerrainTable.byId(TerrainTable.CONVENTIONAL);
        helper.assertTrue(nuclear == registry.getValue(TerrainTable.NUCLEAR), "the table in use is the server's");
        helper.assertTrue(conventional.blowsOut(Blocks.GLASS.defaultBlockState()) && conventional.blowsOut(Blocks.OAK_LEAVES.defaultBlockState())
                && conventional.blowsOut(Blocks.SHORT_GRASS.defaultBlockState()) && conventional.blowsOut(Blocks.TORCH.defaultBlockState())
                && !conventional.blowsOut(Blocks.STONE.defaultBlockState()) && !conventional.blowsOut(Blocks.WATER.defaultBlockState()),
                "the shock blows out glass, leaves, plants and torches, not stone or water");
        helper.assertTrue(nuclear.scorched(Blocks.ICE.defaultBlockState()).is(Blocks.WATER) && conventional.scorched(Blocks.ICE.defaultBlockState()) == null,
                "a nuclear fireball melts ice, a conventional one leaves it");
        helper.assertTrue(conventional.scorched(Blocks.SNOW_BLOCK.defaultBlockState()).isAir() && conventional.scorched(Blocks.FARMLAND
                .defaultBlockState()).is(Blocks.DIRT), "snow gone, fields to dirt");
        helper.assertTrue(nuclear.scorched(GBFallout.CHARRED_PLANKS.block().get().defaultBlockState()) == null
                && nuclear.scorched(GBFallout.CHARRED_LOG.block().get().defaultBlockState()) == null, "nothing burns twice");
        helper.assertTrue(TerrainTable.byId(net.minecraft.resources.Identifier.fromNamespaceAndPath("somepack", "none")) == conventional,
                "a table no data has reads as the conventional one");
        helper.assertTrue(!nuclear.rules(TerrainTable.Band.CHARRED).isEmpty() && !nuclear.rules(TerrainTable.Band.WITHERED).isEmpty()
                && conventional.rules(TerrainTable.Band.CHARRED).isEmpty(), "the far rings are the nuclear table's");
        var random = net.minecraft.util.RandomSource.create(7);
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        boolean opened = false;
        for (int i = 0; i < 40 && !opened; i++) {
            BlockState out = nuclear.convert(TerrainTable.Band.BROKEN, door, random, 0, 1);
            opened = out != null && out.is(Blocks.OAK_DOOR) && out.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
        }
        helper.assertTrue(opened, "the broken ring blows a door open (the same door, opened)");
        helper.succeed();
    }

    /**
     * Swap rules (DESIGN.md §21.10): a rule whose chance fails passes the block to the next (a rare ore at 5% over the
     * trinitite), a weighted choice, keep, a pattern with a tag, properties carried over, a fading rule nothing at the
     * band's edge; and the server's lines read or refused.
     */
    private static void swapRules(GameTestHelper helper) {
        var lining = java.util.List.of(
                dev.groundburst.blast.TerrainRule.of(0.05f, "minecraft:gold_ore", "#minecraft:sand"),
                dev.groundburst.blast.TerrainRule.of("groundburst:trinitite", "#minecraft:sand"),
                dev.groundburst.blast.TerrainRule.of("keep", "minecraft:stone"),
                dev.groundburst.blast.TerrainRule.of("groundburst:slag", "#minecraft:base_stone_overworld"));
        var scorch = java.util.List.of(
                dev.groundburst.blast.TerrainRule.of("minecraft:dirt*3|minecraft:coarse_dirt*1", "minecraft:grass_block"),
                dev.groundburst.blast.TerrainRule.of("minecraft:stripped_birch_log", "#minecraft:logs&*:oak_*"),
                new dev.groundburst.blast.TerrainRule(java.util.List.of("minecraft:dirt"), "minecraft:gravel", 1, true));
        TerrainTable t = new TerrainTable(true, false, 0, java.util.Map.of(TerrainTable.Band.LINING, lining, TerrainTable.Band.SCORCH, scorch));
        var random = net.minecraft.util.RandomSource.create(42);
        int gold = 0, trinitite = 0, dirt = 0, coarse = 0;
        for (int i = 0; i < 4000; i++) {
            BlockState out = t.lining(Blocks.SAND.defaultBlockState(), random, 0);
            if (out.is(Blocks.GOLD_ORE)) {
                gold++;
            } else if (out.is(GBFallout.TRINITITE.block().get())) {
                trinitite++;
            }
            BlockState g = t.scorched(Blocks.GRASS_BLOCK.defaultBlockState(), random);
            if (g.is(Blocks.DIRT)) {
                dirt++;
            } else if (g.is(Blocks.COARSE_DIRT)) {
                coarse++;
            }
        }
        helper.assertTrue(gold > 120 && gold < 280 && gold + trinitite == 4000, "5% gold, the rest trinitite: " + gold + " / " + trinitite);
        helper.assertTrue(coarse > 800 && coarse < 1200 && dirt + coarse == 4000, "three to one: " + dirt + " / " + coarse);
        helper.assertTrue(t.lining(Blocks.STONE.defaultBlockState(), random, 0) == null, "keep: stone left as it is, the slag rule not asked");
        helper.assertTrue(t.lining(Blocks.GRANITE.defaultBlockState(), random, 0).is(GBFallout.SLAG.block().get()), "granite to slag");
        BlockState log = t.scorched(Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z), random);
        helper.assertTrue(log != null && log.is(Blocks.STRIPPED_BIRCH_LOG) && log.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Z,
                "a tag and a pattern together, the axis carried over");
        helper.assertTrue(t.scorched(Blocks.SPRUCE_LOG.defaultBlockState(), random) == null, "not an oak: the pattern leaves it");
        boolean edge = false;
        for (int i = 0; i < 200; i++) {
            edge |= t.convert(TerrainTable.Band.SCORCH, Blocks.DIRT.defaultBlockState(), random, 1, 1) != null;
        }
        helper.assertTrue(!edge && t.convert(TerrainTable.Band.SCORCH, Blocks.DIRT.defaultBlockState(), random, 0, 1) != null,
                "a fading rule: all of it at the band's inner edge, nothing at its outer");
        helper.assertTrue(dev.groundburst.blast.TerrainRules.check("nuclear lining #minecraft:sand -> mymod:rare_ore 5%") == null
                && dev.groundburst.blast.TerrainRules.check("* broken minecraft:glass -> air 60% fade") == null
                && dev.groundburst.blast.TerrainRules.check("nuclear scorch,charred #minecraft:logs, #minecraft:planks -> mymod:burnt") == null
                && dev.groundburst.blast.TerrainRules.check("nuclear lining #minecraft:sand mymod:rare_ore") != null
                && dev.groundburst.blast.TerrainRules.check("nuclear deep #minecraft:sand -> air") != null
                && dev.groundburst.blast.TerrainRules.check("nuclear lining #minecraft:sand -> air 150%") != null, "the server's lines checked");
        helper.succeed();
    }

    /**
     * Where rain falls black, and how black (DESIGN.md §21.10): a strong field at the ground black, a weaker one grey, a
     * weak one plain; under a plume; a ground burst's own rain only in its window (from 40 s for six minutes) and only
     * under its cloud, and never from a plume that does not rain.
     */
    private static void blackRainWhere(GameTestHelper helper) {
        var level = helper.getLevel();
        long now = level.getGameTime();
        Vec3 c = helper.absoluteVec(new Vec3(8, 1, 8));
        // A source whose peak at its centre is 40 000 RAD/h: hundreds at 60 blocks out of 100, a few near its edge.
        RadiationSource strong = new RadiationSource(c, 100, 40_000, 100_000, now);
        float black = dev.groundburst.content.fallout.BlackRain.strength(level, List.of(strong), List.of(), c.x, c.y, c.z, now);
        float edge = dev.groundburst.content.fallout.BlackRain.strength(level, List.of(strong), List.of(), c.x + 99.5, c.y, c.z, now);
        float none = dev.groundburst.content.fallout.BlackRain.strength(level, List.of(strong), List.of(), c.x + 150, c.y, c.z, now);
        helper.assertTrue(black == 1, "black where the field is strong: " + black);
        helper.assertTrue(edge == 0 || edge >= 0.25f && edge < 1, "grey to nothing at the field's edge: " + edge);
        helper.assertTrue(none == 0, "plain rain outside it: " + none);
        Plumes.Cloud under = new Plumes.Cloud((float) c.x, (float) c.y, (float) c.z, 30, 0.5f, 100, true);
        float plume = dev.groundburst.content.fallout.BlackRain.strength(level, List.of(), List.of(under), c.x + 10, c.y, c.z, now);
        helper.assertTrue(Math.abs(plume - (0.3f + 0.7f * 0.5f)) < 1e-4, "black under a plume, by its strength: " + plume);
        helper.assertTrue(dev.groundburst.content.fallout.BlackRain.ownRain(List.of(under), c.x + 10, c.z) > 0.3f, "its own rain falls now");
        helper.assertTrue(dev.groundburst.content.fallout.BlackRain.ownRain(List.of(under), c.x + 40, c.z) == 0, "but only under it");
        Plumes.Cloud young = new Plumes.Cloud((float) c.x, (float) c.y, (float) c.z, 30, 1, 10, true);
        Plumes.Cloud old = new Plumes.Cloud((float) c.x, (float) c.y, (float) c.z, 30, 0.2f, 450, true);
        Plumes.Cloud dry = new Plumes.Cloud((float) c.x, (float) c.y, (float) c.z, 30, 1, 100, false);
        helper.assertTrue(dev.groundburst.content.fallout.BlackRain.ownRain(List.of(young, old, dry), c.x, c.z) == 0,
                "not before 40 s, not after its six minutes, and never from a plume that does not rain");
        helper.succeed();
    }

    /**
     * What black rain does (DESIGN.md §21.10): a column under it rots from the top (leaves dead on the branch, the grass
     * under it), crops wither, copper weathers a step; out in it a pig is burnt by the acid, a husk's iron helmet
     * corrodes, and a husk in a full hazmat suit is not burnt.
     */
    private static void blackRainHarm(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        var random = net.minecraft.util.RandomSource.create(3);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.WHEAT);
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.COPPER_BLOCK);
        for (int[] at : new int[][] {{3, 3}, {5, 5}, {7, 7}}) {
            BlockPos abs = helper.absolutePos(new BlockPos(at[0], 1, at[1]));
            dev.groundburst.content.fallout.BlackRain.rot(level, abs.getX(), abs.getZ(), random);
        }
        helper.assertBlockPresent(GBFallout.DEAD_LEAVES.block().get(), new BlockPos(3, 1, 3));
        helper.assertBlockPresent(GBFallout.WITHERED_CROPS.block().get(), new BlockPos(5, 1, 5));
        helper.assertBlockPresent(Blocks.EXPOSED_COPPER, new BlockPos(7, 1, 7));
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, new BlockPos(10, 1, 10));
        var suited = helper.spawn(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(12, 1, 12));
        suited.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(dev.groundburst.content.gear.GBArmor.HAZMAT_HOOD.get()));
        suited.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(dev.groundburst.content.gear.GBArmor.HAZMAT_COAT.get()));
        suited.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(dev.groundburst.content.gear.GBArmor.HAZMAT_TROUSERS.get()));
        suited.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(dev.groundburst.content.gear.GBArmor.HAZMAT_BOOTS.get()));
        var helmeted = helper.spawn(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(2, 1, 12));
        net.minecraft.world.item.ItemStack helmet = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET);
        helmeted.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
        float suitWas = suited.getHealth();
        for (int i = 0; i < 300; i++) {
            pig.invulnerableTime = 0;
            suited.invulnerableTime = 0;
            dev.groundburst.content.fallout.BlackRain.soak(level, pig, 1, random);
            dev.groundburst.content.fallout.BlackRain.soak(level, suited, 1, random);
            dev.groundburst.content.fallout.BlackRain.soak(level, helmeted, 1, random);
            pig.setHealth(pig.getMaxHealth());              // (kept alive: it is the burns that are counted)
        }
        helper.assertTrue(pig.getLastDamageSource() != null && pig.getLastDamageSource().is(dev.groundburst.content.fluid.ChemLiquidBlock.ACID),
                "the acid burns a pig out in it");
        helper.assertTrue(suited.getHealth() >= suitWas && suited.getLastDamageSource() == null, "a full hazmat suit keeps it off");
        helper.assertTrue(helmeted.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getDamageValue() > 0, "an iron helmet corrodes");
        helper.succeed();
    }

    /**
     * Columns in a strong field die from the top down: grass and its grass block, leaves on the branch over a trunk that
     * stands, crops on farmland; a strong source kills plants within its reach, a weak one nowhere.
     */
    private static void wither(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.SHORT_GRASS);
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(6, 2, 6), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        helper.setBlock(new BlockPos(9, 0, 9), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(9, 1, 9), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        for (BlockPos rel : List.of(new BlockPos(3, 0, 3), new BlockPos(6, 0, 6), new BlockPos(9, 0, 9))) {
            BlockPos abs = helper.absolutePos(rel);
            Withering.wither(level, abs.getX(), abs.getZ(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        helper.assertBlockPresent(GBFallout.DEAD_GRASS_BLOCK.block().get(), new BlockPos(3, 0, 3));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
        helper.assertBlockPresent(GBFallout.DEAD_LEAVES.block().get(), new BlockPos(6, 2, 6));
        helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(6, 1, 6));
        helper.assertBlockPresent(GBFallout.WITHERED_CROPS.block().get(), new BlockPos(9, 1, 9));
        long now = level.getGameTime();
        Vec3 c = helper.absoluteVec(new Vec3(8, 1, 8));
        double strong = Withering.reach(new RadiationSource(c, 100, 60_000, 4_000, now), now);
        double weak = Withering.reach(new RadiationSource(c, 100, 800, 4_000, now), now);
        helper.assertTrue(strong > 80 && strong < 100, "60 000 RAD/h kills plants out to 1 000 RAD/h: " + strong);
        helper.assertTrue(weak == 0, "800 RAD/h kills none");
        helper.succeed();
    }

    /**
     * A tactical warhead at a tenth of its size on sand: the sand its fireball sees round the crater is glassed, and the
     * crater's sandy wall fused to trinitite.
     */
    private static void glassed(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.SANDSTONE);
        }
        for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 15, 2, 15)) {
            helper.setBlock(p, Blocks.SAND);
        }
        var level = helper.getLevel();
        BlockPos burst = helper.absolutePos(new BlockPos(8, 3, 8));
        float scale = dev.groundburst.content.ordnance.charge.Charges.bombScale;
        dev.groundburst.content.ordnance.charge.Charges.bombScale = 0.1f;
        dev.groundburst.content.ordnance.charge.Charges.impact(level, dev.groundburst.content.ordnance.charge.ChargeKind.TACTICAL_NUCLEAR_WARHEAD,
                burst, null);
        dev.groundburst.content.ordnance.charge.Charges.bombScale = scale;
        Vec3 centre = Vec3.atCenterOf(burst);
        helper.succeedWhen(() -> {
            int glassed = 0, trinitite = 0, sand = 0;
            for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 3, 15)) {
                BlockState s = helper.getBlockState(p);
                glassed += s.is(GBFallout.GLASSED_SAND.block().get()) ? 1 : 0;
                trinitite += s.is(GBFallout.TRINITITE.block().get()) ? 1 : 0;
                sand += s.is(Blocks.SAND) ? 1 : 0;
            }
            helper.assertTrue(glassed > 0, "glassed sand round the crater (" + glassed + " glassed, " + trinitite + " trinitite, " + sand + " sand)");
            helper.assertTrue(trinitite > 0, "trinitite in its wall (" + glassed + " glassed, " + trinitite + " trinitite, " + sand + " sand)");
            RadiationSources.of(level).removeNear(centre, 2);
            Plumes.of(level).removeNear(centre, 2);
        });
    }

    /**
     * A tactical warhead at a seventh of its size in solid stone: well inside its crater no full block is left standing
     * alone (nothing solid beside it or over it), where a ray that ran out short, or none came, would have left one.
     */
    private static void noLoneBlocks(GameTestHelper helper, int shift) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 4, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
        var level = helper.getLevel();
        BlockPos burst = helper.absolutePos(new BlockPos(8 + shift, 5, 8));
        float scale = dev.groundburst.content.ordnance.charge.Charges.bombScale;
        dev.groundburst.content.ordnance.charge.Charges.bombScale = 0.14f;
        dev.groundburst.content.ordnance.charge.Charges.impact(level, dev.groundburst.content.ordnance.charge.ChargeKind.TACTICAL_NUCLEAR_WARHEAD,
                burst, null);
        dev.groundburst.content.ordnance.charge.Charges.bombScale = scale;
        Vec3 centre = Vec3.atCenterOf(burst);
        helper.runAfterDelay(100, () -> {                                   // long after so small a blast has finished
            int lone = 0;
            StringBuilder which = new StringBuilder();
            for (BlockPos p : BlockPos.betweenClosed(2, 1, 2, 13, 5, 13)) {
                BlockPos abs = helper.absolutePos(p);
                if (Math.hypot(abs.getX() + 0.5 - centre.x, abs.getZ() + 0.5 - centre.z) > 4
                        || !helper.getBlockState(p).isCollisionShapeFullBlock(level, abs)) {
                    continue;                              // the bowl (its ragged rim takes ejecta); full blocks (not fire or ash)
                }
                boolean alone = true;
                for (Direction d : Direction.values()) {
                    alone &= d == Direction.DOWN || !level.getBlockState(abs.relative(d)).isSolid();
                }
                if (alone) {
                    lone++;
                    which.append(' ').append(helper.getBlockState(p).getBlock().getDescriptionId()).append('@').append(p.toShortString());
                }
            }
            RadiationSources.of(level).removeNear(centre, 2);
            Plumes.of(level).removeNear(centre, 2);
            helper.assertTrue(lone == 0, "no lone block in the crater: " + lone + which);
            helper.succeed();
        });
    }

    /** Dead grass covered over turns to dirt, as grass does. */
    private static void covered(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(new BlockPos(5, 0, 5), GBFallout.DEAD_GRASS_BLOCK.block().get());
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.STONE);
        BlockPos at = helper.absolutePos(new BlockPos(5, 0, 5));
        var level = helper.getLevel();
        level.getBlockState(at).randomTick(level, at, level.getRandom());
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(5, 0, 5));
        helper.succeed();
    }
}
