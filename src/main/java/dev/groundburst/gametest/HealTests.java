package dev.groundburst.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.groundburst.blast.Blasts;
import dev.groundburst.config.GBServerConfig.HealPlayerBlocks;
import dev.groundburst.config.GBServerConfig.HealSchedule;
import dev.groundburst.heal.ChunkScars;
import dev.groundburst.heal.HealSettings;
import dev.groundburst.heal.Healer;
import dev.groundburst.heal.Scars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Healing (DESIGN.md §21.9): a blast's damage comes back block for block (a chest with its contents, stairs the way
 * they faced, the grass the scorch took), drops nothing meanwhile, keeps or drops what players built over it, survives
 * saving, waits as the server says, never brings back an explosive, thaws a freeze, turns the wasteland's biome back,
 * hangs a knocked-down item frame again and remembers sand that falls next to it; with healing off nothing is
 * remembered. Each test marks its own plot out ({@link HealSettings#testArea}) and heals it itself.
 */
final class HealTests {
    private HealTests() {}

    static void register() {
        GBGameTests.test("heal_puts_a_blast_back_exactly", GBGameTests.FLOOR_16, 20, HealTests::exactly);
        GBGameTests.test("heal_keeps_what_players_built", GBGameTests.FLOOR_16, 20, HealTests::keepsPlayers);
        GBGameTests.test("heal_drops_what_players_built", GBGameTests.FLOOR_16, 20, HealTests::dropsPlayers);
        GBGameTests.test("heal_record_survives_saving", GBGameTests.FLOOR_16, 20, HealTests::saving);
        GBGameTests.test("heal_waits_as_the_server_says", GBGameTests.FLOOR_16, 20, HealTests::schedule);
        GBGameTests.test("heal_leaves_explosives_and_exclusions", GBGameTests.FLOOR_16, 20, HealTests::exclusions);
        GBGameTests.test("heal_thaws_a_freeze", GBGameTests.FLOOR_16, 20, HealTests::freeze);
        GBGameTests.test("heal_turns_the_wasteland_back", GBGameTests.FLOOR_16, 20, HealTests::biomes);
        GBGameTests.test("heal_hangs_an_item_frame_again", GBGameTests.FLOOR_16, 20, HealTests::decorations);
        GBGameTests.test("heal_remembers_falling_sand", GBGameTests.FLOOR_16, 20, HealTests::fallingSand);
        GBGameTests.test("heal_off_remembers_nothing", GBGameTests.FLOOR_16, 20, HealTests::off);
        GBGameTests.test("heal_never_brings_fire_back", GBGameTests.FLOOR_16, 20, HealTests::fire);
    }

    private static final BlockPos C = new BlockPos(8, 4, 8);

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            helper.setBlock(p, block);
        }
    }

    /** The plot and a little round it: damage there is remembered with the test's own settings. */
    private static AABB area(GameTestHelper helper) {
        return helper.getBounds().inflate(2);
    }

    private static BoundingBox box(GameTestHelper helper) {
        AABB a = area(helper);
        return BoundingBox.fromCorners(BlockPos.containing(a.minX, a.minY, a.minZ), BlockPos.containing(a.maxX - 1e-3, a.maxY - 1e-3, a.maxZ - 1e-3));
    }

    /** Every block in the plot as it is now (absolute positions). */
    private static Map<BlockPos, BlockState> snapshot(GameTestHelper helper) {
        Map<BlockPos, BlockState> out = new HashMap<>();
        AABB b = helper.getBounds();
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(b.minX, b.minY, b.minZ), BlockPos.containing(b.maxX - 1, b.maxY - 1, b.maxZ - 1))) {
            out.put(p.immutable(), helper.getLevel().getBlockState(p));
        }
        return out;
    }

    /** The first block (absolute) that is not as it was in {@code before}, described, or null. */
    private static String differs(GameTestHelper helper, Map<BlockPos, BlockState> before) {
        for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
            BlockState now = helper.getLevel().getBlockState(e.getKey());
            if (now != e.getValue()) {
                return helper.relativePos(e.getKey()) + " is " + now + ", was " + e.getValue();
            }
        }
        return null;
    }

    /** Sets off a small blast at the plot's middle with healing on there; the settings it healed with. */
    private static HealSettings blast(GameTestHelper helper, HealSettings s) {
        HealSettings.testArea(helper.getLevel(), area(helper), s);
        Blasts.detonate(helper.getLevel(), helper.absoluteVec(Vec3.atCenterOf(C)), Blasts.profile("test/small"), null);
        return s;
    }

    private static boolean remembered(GameTestHelper helper, BlockPos rel) {
        return Scars.remembered(helper.getLevel(), helper.absolutePos(rel));
    }

    /** Ground, grass, a chest of diamonds, stairs facing east, glass and a fence: blown up and put back exactly, nothing dropped. */
    private static void exactly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 1, 1, 1, 15, 2, 15, Blocks.DIRT);
        fill(helper, 1, 3, 1, 15, 3, 15, Blocks.GRASS_BLOCK);
        BlockPos chest = C.offset(1, 0, 0);
        helper.setBlock(chest, Blocks.CHEST);
        ((Container) helper.getBlockEntity(chest, BlockEntity.class)).setItem(4, new ItemStack(Items.DIAMOND, 37));
        helper.setBlock(C.offset(-1, 0, 0), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        helper.setBlock(C.offset(0, 0, 2), Blocks.GLASS);
        helper.setBlock(C.offset(2, 0, -1), Blocks.OAK_FENCE);
        Map<BlockPos, BlockState> before = snapshot(helper);
        HealSettings s = blast(helper, HealSettings.forTests(HealPlayerBlocks.KEEP, 0));
        helper.assertTrue(level.getBlockState(helper.absolutePos(chest)).isAir(), "the blast took the chest");
        helper.assertTrue(differs(helper, before) != null, "the blast changed the plot");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area(helper)).isEmpty(), "nothing dropped: it will all come back");
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        String wrong = differs(helper, before);
        helper.assertTrue(wrong == null, "every block back as it was: " + wrong);
        ItemStack held = ((Container) helper.getBlockEntity(chest, BlockEntity.class)).getItem(4);
        helper.assertTrue(held.is(Items.DIAMOND) && held.getCount() == 37, "the chest has its diamonds again: " + held);
        helper.assertTrue(!remembered(helper, chest) && !remembered(helper, C), "and nothing is remembered any more");
        helper.succeed();
    }

    /** KEEP: a player's block in the crater stays, and so does the hole round it (radius 1) and over it; the rest heals. */
    private static void keepsPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 2, 1, 2, 14, 7, 14, Blocks.STONE);
        HealSettings s = blast(helper, HealSettings.forTests(HealPlayerBlocks.KEEP, 1));
        helper.assertTrue(level.getBlockState(helper.absolutePos(C)).isAir() && remembered(helper, C), "the middle was blown out");
        // Somewhere the blast took out of the stone outside the radius kept round the middle (its crater is ragged).
        BlockPos far = null;
        for (BlockPos p : BlockPos.betweenClosed(C.offset(-3, -3, -3), C.offset(3, 0, 3))) {
            if (p.distSqr(C) > 2 && remembered(helper, p) && level.getBlockState(helper.absolutePos(p)).isAir()) {
                far = p.immutable();
                break;
            }
        }
        helper.assertTrue(far != null, "the blast took stone further out");
        level.setBlock(helper.absolutePos(C), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        Scars.claim(level, helper.absolutePos(C));
        helper.assertTrue(!remembered(helper, C) && !remembered(helper, C.east()) && !remembered(helper, C.above(2)),
                "the player's block, the hole beside it and the air over it are left as they are");
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        helper.assertTrue(level.getBlockState(helper.absolutePos(C)).is(Blocks.OAK_PLANKS), "the planks stay");
        helper.assertTrue(level.getBlockState(helper.absolutePos(C.above())).isAir(), "nothing heals over them");
        helper.assertTrue(level.getBlockState(helper.absolutePos(far)).is(Blocks.STONE), "the stone two blocks off heals");
        helper.succeed();
    }

    /** DROP: a player's block in the crater pops off as an item and the stone heals there. */
    private static void dropsPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 2, 1, 2, 14, 7, 14, Blocks.STONE);
        HealSettings s = blast(helper, HealSettings.forTests(HealPlayerBlocks.DROP, 0));
        level.setBlock(helper.absolutePos(C), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        Scars.claim(level, helper.absolutePos(C));
        helper.assertTrue(remembered(helper, C), "still remembered, marked as the player's");
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        helper.assertTrue(level.getBlockState(helper.absolutePos(C)).is(Blocks.STONE), "the stone is back");
        boolean dropped = level.getEntitiesOfClass(ItemEntity.class, area(helper)).stream().anyMatch(i -> i.getItem().is(Items.OAK_PLANKS));
        helper.assertTrue(dropped, "the planks were dropped for the player");
        helper.succeed();
    }

    /** A chunk's record (a chest with diamonds among stone) reads back from its saved form the same. */
    private static void saving(GameTestHelper helper) {
        fill(helper, 2, 1, 2, 14, 7, 14, Blocks.STONE);
        helper.setBlock(C, Blocks.BARREL);
        ((Container) helper.getBlockEntity(C, BlockEntity.class)).setItem(0, new ItemStack(Items.EMERALD, 5));
        blast(helper, HealSettings.forTests(HealPlayerBlocks.KEEP, 0));
        String wrong = ChunkScars.checkSaving(helper.getLevel(), helper.absolutePos(C));
        Healer.of(helper.getLevel()).healNow(box(helper), HealSettings.forTests(HealPlayerBlocks.KEEP, 0));
        HealSettings.endTestArea(helper.getLevel(), area(helper));
        helper.assertTrue(wrong == null, "the record reads back the same: " + wrong);
        helper.succeed();
    }

    /** The wait, its random share, the morning and the command-only schedule. */
    private static void schedule(GameTestHelper helper) {
        HealSettings base = HealSettings.forTests(HealPlayerBlocks.KEEP, 0);
        HealSettings wait = base.withSchedule(true, HealSchedule.AFTER_WAIT, 100, 40);
        helper.assertFalse(Healer.dueAt(wait, 0, 0, 99, -1, -1), "not before the wait");
        helper.assertTrue(Healer.dueAt(wait, 0, 0, 100, -1, -1), "once the wait is over");
        helper.assertFalse(Healer.dueAt(wait, 0, 0.5f, 119, -1, -1), "a chunk's random share of 40 ticks: half of it more");
        helper.assertTrue(Healer.dueAt(wait, 0, 0.5f, 120, -1, -1), "and then it heals");
        helper.assertFalse(Healer.dueAt(base.withSchedule(false, HealSchedule.AFTER_WAIT, 0, 0), 0, 0, 1_000, -1, -1), "never while off");
        HealSettings morning = base.withSchedule(true, HealSchedule.MORNING, 20, 0);
        helper.assertFalse(Healer.dueAt(morning, 0, 0, 10, -1, 3), "the morning schedule waits for its wait too");
        helper.assertFalse(Healer.dueAt(morning, 0, 0, 30, 3, 3), "waited on day 3: not until the sun comes up");
        helper.assertTrue(Healer.dueAt(morning, 0, 0, 30_000, 3, 4), "sunrise on day 4: it heals");
        helper.assertTrue(Healer.dueAt(morning, 0, 0, 30, -1, -1), "where there is no day and night, the wait alone");
        helper.assertFalse(Healer.dueAt(base.withSchedule(true, HealSchedule.COMMAND_ONLY, 0, 0), 0, 0, 1_000_000, 3, 9), "never by itself");
        helper.succeed();
    }

    /** The mod's explosives never come back; blocks and tags the server leaves out neither; and nor does a weapon left out. */
    private static void exclusions(GameTestHelper helper) {
        HealSettings s = HealSettings.forTests(HealPlayerBlocks.KEEP, 0).withExcluded(Set.of(Blocks.GLASS), List.of(BlockTags.LOGS),
                List.of("groundburst:test/sm*"));
        helper.assertFalse(s.keeps(dev.groundburst.content.ordnance.GBOrdnance.C4.block().get().defaultBlockState()), "C4 never heals");
        helper.assertFalse(s.keeps(Blocks.GLASS.defaultBlockState()), "glass is left out");
        helper.assertFalse(s.keeps(Blocks.OAK_LOG.defaultBlockState()), "logs (by their tag) are left out");
        helper.assertTrue(s.keeps(Blocks.STONE.defaultBlockState()), "stone heals");
        helper.assertTrue(s.keeps(Blocks.TNT.defaultBlockState()), "TNT a blast deleted heals (it never went off)");
        ServerLevel level = helper.getLevel();
        helper.assertFalse(s.heals(HealSettings.Kind.CONVENTIONAL, dev.groundburst.GroundBurst.id("test/small"), level), "test/small is left out by its pattern");
        helper.assertTrue(s.heals(HealSettings.Kind.CONVENTIONAL, dev.groundburst.GroundBurst.id("test/medium"), level), "test/medium is not");
        helper.assertTrue(Scars.kindOf(dev.groundburst.GroundBurst.id("tsar_warhead"), true) == HealSettings.Kind.NUCLEAR, "the Tsar is nuclear");
        helper.assertTrue(Scars.kindOf(dev.groundburst.GroundBurst.id("toxic_waste_warhead"), false) == HealSettings.Kind.CHEMICAL, "toxic waste is chemical");
        // A blast left out by its weapon remembers nothing.
        fill(helper, 2, 1, 2, 14, 7, 14, Blocks.STONE);
        blast(helper, s);
        HealSettings.endTestArea(level, area(helper));
        helper.assertTrue(level.getBlockState(helper.absolutePos(C)).isAir() && !remembered(helper, C), "its damage is not remembered");
        helper.succeed();
    }

    /** A burst of cold over a pond and grass: thawed back exactly (the water still water, the grass bare, no snow). */
    private static void freeze(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 1, 1, 1, 15, 2, 15, Blocks.DIRT);
        fill(helper, 1, 3, 1, 15, 3, 15, Blocks.GRASS_BLOCK);
        fill(helper, 6, 3, 6, 9, 3, 9, Blocks.WATER);
        helper.setBlock(C.offset(3, 0, 3), Blocks.POPPY);
        Map<BlockPos, BlockState> before = snapshot(helper);
        HealSettings s = HealSettings.forTests(HealPlayerBlocks.KEEP, 0);
        HealSettings.testArea(level, area(helper), s);
        dev.groundburst.content.effect.Frost.freeze(level, helper.absoluteVec(new Vec3(8, 4, 8)), 5, 40, null);
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(7, 3, 7))).is(BlockTags.ICE), "the pond froze");
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        String wrong = differs(helper, before);
        helper.assertTrue(wrong == null, "thawed back as it was: " + wrong);
        helper.succeed();
    }

    /** The wasteland's biome painted over the plot turns back to what it was. */
    private static void biomes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos middle = helper.absolutePos(new BlockPos(8, 1, 8));
        var was = level.getBiome(middle).unwrapKey().orElseThrow();
        HealSettings s = HealSettings.forTests(HealPlayerBlocks.KEEP, 0);
        HealSettings.testArea(level, area(helper), s);
        Vec3 at = helper.absoluteVec(new Vec3(8, 1, 8));
        dev.groundburst.content.fallout.Wasteland.paintNow(level, at, 5,
                Scars.recorder(level, at, dev.groundburst.GroundBurst.id("nuclear_missile_warhead"), HealSettings.Kind.NUCLEAR));
        helper.assertTrue(level.getBiome(middle).is(dev.groundburst.content.fallout.Wasteland.BIOME), "the wasteland is painted");
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        helper.assertTrue(level.getBiome(middle).is(was), "and turned back to " + was.identifier());
        helper.succeed();
    }

    /** An item frame with a diamond in it, knocked off its wall: taken down whole, no drop, and hung again. */
    private static void decorations(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wall = C.offset(0, 0, -4), at = C.offset(0, 0, -3);
        helper.setBlock(wall, Blocks.STONE);
        ItemFrame frame = new ItemFrame(level, helper.absolutePos(at), Direction.SOUTH);
        frame.setItem(new ItemStack(Items.DIAMOND));
        helper.assertTrue(frame.survives() && level.addFreshEntity(frame), "the frame hangs on its wall");
        HealSettings s = blast(helper, HealSettings.forTests(HealPlayerBlocks.KEEP, 0));
        helper.assertTrue(frame.isRemoved(), "the blast took it down");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area(helper)).isEmpty(), "and nothing dropped");
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, area(helper));
        helper.assertTrue(frames.size() == 1 && frames.getFirst().getItem().is(Items.DIAMOND), "it hangs again with its diamond: " + frames);
        helper.succeed();
    }

    /** Sand left hanging by the damage falls: where it fell from is remembered, to heal with the rest. */
    private static void fallingSand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 2, 1, 2, 14, 4, 14, Blocks.STONE);
        BlockPos sand = C.offset(0, 2, 0);
        HealSettings s = blast(helper, HealSettings.forTests(HealPlayerBlocks.KEEP, 0));
        level.setBlock(helper.absolutePos(sand), Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
        FallingBlockEntity falling = FallingBlockEntity.fall(level, helper.absolutePos(sand), Blocks.SAND.defaultBlockState());
        helper.assertTrue(remembered(helper, sand), "where the sand fell from is remembered");
        falling.discard();
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        helper.assertTrue(level.getBlockState(helper.absolutePos(sand)).is(Blocks.SAND), "the sand is back where it was");
        helper.succeed();
    }

    /**
     * A fire is never put back: a cell remembered while it burns comes back as the air it burnt in. A chunk waits while a
     * fire that will go out burns by its damage (what heals would burn again), but not for one on netherrack, which burns
     * for good.
     */
    private static void fire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HealSettings s = HealSettings.forTests(HealPlayerBlocks.KEEP, 0);
        HealSettings.testArea(level, area(helper), s);
        BlockPos burning = new BlockPos(8, 1, 8);
        helper.setBlock(burning, Blocks.FIRE);
        helper.assertTrue(level.getBlockState(helper.absolutePos(burning)).is(Blocks.FIRE), "a fire burns on the floor");
        Scars.Recorder recorder = Scars.recorder(level, helper.absoluteVec(Vec3.atCenterOf(burning)), Scars.INCENDIARY, HealSettings.Kind.CONVENTIONAL);
        helper.assertTrue(recorder.record(helper.absolutePos(burning)), "the burning cell is remembered");
        var chunk = level.getChunkAt(helper.absolutePos(burning));
        helper.assertTrue(Healer.of(level).fireNear(chunk, false), "a fire burns by the damage: the chunk waits");
        BlockPos hearth = new BlockPos(11, 1, 8);
        helper.setBlock(hearth, Blocks.NETHERRACK);
        helper.setBlock(hearth.above(), Blocks.FIRE);
        Healer.of(level).healNow(box(helper), s);
        helper.assertTrue(level.getBlockState(helper.absolutePos(burning)).isAir(), "the fire is not put back: the air it burnt in is");
        helper.assertTrue(!Healer.of(level).fireNear(chunk, false) || !remembered(helper, burning), "and nothing waits for it");
        recorder.record(helper.absolutePos(new BlockPos(5, 1, 5)));
        helper.assertTrue(level.getBlockState(helper.absolutePos(hearth.above())).is(Blocks.FIRE), "the hearth burns on");
        helper.assertTrue(!Healer.of(level).fireNear(chunk, false), "a fire on netherrack is no reason to wait");
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        helper.succeed();
    }

    /** With healing off (the default) a blast remembers nothing, and a chest it takes is gone with its contents as before. */
    private static void off(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 2, 1, 2, 14, 7, 14, Blocks.STONE);
        helper.setBlock(C.offset(1, 0, 0), Blocks.CHEST);
        Blasts.detonate(level, helper.absoluteVec(Vec3.atCenterOf(C)), Blasts.profile("test/small"), null);
        helper.assertTrue(level.getBlockState(helper.absolutePos(C)).isAir() && !remembered(helper, C), "nothing is remembered");
        helper.assertTrue(!remembered(helper, C.offset(1, 0, 0)), "not the chest either");
        helper.succeed();
    }
}
