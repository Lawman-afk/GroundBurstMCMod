package dev.groundburst.gametest;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.groundburst.blast.Blasts;
import dev.groundburst.config.GBServerConfig.HealPlayerBlocks;
import dev.groundburst.config.GBServerConfig.HealSchedule;
import dev.groundburst.heal.ChunkScars;
import dev.groundburst.heal.Explosions;
import dev.groundburst.heal.HealSettings;
import dev.groundburst.heal.HealSettings.Kind;
import dev.groundburst.heal.Healer;
import dev.groundburst.heal.Scars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Healing (DESIGN.md §21.9): a blast's damage comes back block for block (a chest with its contents, stairs the way
 * they faced, the grass the scorch took), drops nothing meanwhile, keeps or drops what players built over it, survives
 * saving, waits as the server says, never brings back an explosive, thaws a freeze, turns the wasteland's biome back,
 * hangs a knocked-down item frame again and remembers sand that falls next to it; with healing off nothing is
 * remembered. The game's own explosions (a creeper's, TNT's, a bed's) and other mods' heal the same, by the switch for
 * what set them off; TNT an explosion lights is never put back. Each test marks its own plot out ({@link HealSettings#testArea}) and heals it itself.
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
        GBGameTests.test("heal_wakes_cables_and_machines", GBGameTests.FLOOR_16, 600, HealTests::cables);
        GBGameTests.test("heal_wakes_pipes", GBGameTests.FLOOR_16, 200, HealTests::pipes);
        GBGameTests.test("heal_puts_a_creeper_hole_back", GBGameTests.FLOOR_16, 20, HealTests::creeper);
        GBGameTests.test("heal_leaves_tnt_an_explosion_lit", GBGameTests.FLOOR_16, 20, HealTests::tntLit);
        GBGameTests.test("heal_explosions_by_what_set_them_off", GBGameTests.FLOOR_16, 20, HealTests::explosionSwitches);
        GBGameTests.test("heal_other_mods_explosions", GBGameTests.FLOOR_16, 20, HealTests::otherMods);
        GBGameTests.test("heal_off_leaves_explosions_alone", GBGameTests.FLOOR_16, 20, HealTests::explosionsOff);
        GBGameTests.test("heal_spilt_contents_do_not_come_back", GBGameTests.FLOOR_16, 20, HealTests::spilt);
        GBGameTests.test("heal_a_chest_half_left_standing_keeps_its_contents", GBGameTests.FLOOR_16, 20, HealTests::chestHalf);
    }

    private static final BlockPos C = new BlockPos(8, 4, 8);

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            helper.setBlock(p, block);
        }
    }

    /** The plot and a little round it: damage there is remembered with the test's own settings. */
    private static AABB area(GameTestHelper helper) {
        return ((dev.groundburst.legacy.mixin.GameTestHelperAccess) helper).groundburst$bounds().inflate(2);
    }

    private static BoundingBox box(GameTestHelper helper) {
        AABB a = area(helper);
        return BoundingBox.fromCorners(BlockPos.containing(a.minX, a.minY, a.minZ), BlockPos.containing(a.maxX - 1e-3, a.maxY - 1e-3, a.maxZ - 1e-3));
    }

    /** Every block in the plot as it is now (absolute positions). */
    private static Map<BlockPos, BlockState> snapshot(GameTestHelper helper) {
        Map<BlockPos, BlockState> out = new HashMap<>();
        AABB b = ((dev.groundburst.legacy.mixin.GameTestHelperAccess) helper).groundburst$bounds();
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
        ((Container) ((BlockEntity) helper.getBlockEntity(chest))).setItem(4, new ItemStack(Items.DIAMOND, 37));
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
        ItemStack held = ((Container) ((BlockEntity) helper.getBlockEntity(chest))).getItem(4);
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
        ((Container) ((BlockEntity) helper.getBlockEntity(C))).setItem(0, new ItemStack(Items.EMERALD, 5));
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
        helper.assertTrue(level.getBiome(middle).is(was), "and turned back to " + was.location());
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
        helper.assertTrue(frames.size() == 1 && dev.groundburst.legacy.J21.first(frames).getItem().is(Items.DIAMOND), "it hangs again with its diamond: " + frames);
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
     * for good, nor for one nobody is near enough to see burn out (it goes out).
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
        // Fire burns only within the game rule's reach of a player (none here): as if there were one, then as it is.
        boolean ticks = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
        boolean waits, out;
        try {
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
            waits = Healer.of(level).fireNear(chunk, false);
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
            out = !Healer.of(level).fireNear(chunk, false) && level.getBlockState(helper.absolutePos(burning)).isAir();
        } finally {
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(ticks, level.getServer());
        }
        helper.assertTrue(waits, "a fire burns by the damage: the chunk waits");
        helper.assertTrue(out, "a fire nobody is near enough to see burn out is no reason to wait: it goes out");
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

    /**
     * Machines and cables come back working: a generator's cable run to a crusher, cut by a weapon (the crusher with it)
     * and pushed into while broken, heals and carries power again, the crusher's side settings as they were.
     */
    private static void cables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HealSettings s = HealSettings.forTests(HealPlayerBlocks.KEEP, 0);
        HealSettings.testArea(level, area(helper), s);
        BlockPos gen = new BlockPos(2, 1, 4), crusherPos = new BlockPos(8, 1, 4);
        helper.setBlock(gen, dev.groundburst.content.machine.GBMachines.COMBUSTION_GENERATOR.block().get());
        for (int x = 3; x <= 7; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), dev.groundburst.content.machine.GBMachines.CABLES.get(0).block().get());
        }
        helper.setBlock(crusherPos, dev.groundburst.content.machine.GBMachines.ORE_CRUSHER.block().get());
        ((dev.groundburst.content.machine.OreCrusherBlockEntity) helper.getBlockEntity(crusherPos)).changeSides(c -> {
            while (c.items(dev.groundburst.content.machine.SideConfig.Face.TOP) != dev.groundburst.content.machine.SideConfig.ItemMode.NONE) {
                c.cycleItems(dev.groundburst.content.machine.SideConfig.Face.TOP, true);
            }
        });
        ((dev.groundburst.content.power.CombustionGeneratorBlockEntity) helper.getBlockEntity(gen)).inventory().setItem(0, new ItemStack(Items.COAL, 4));
        Scars.Recorder weapon = Scars.recorder(level, helper.absoluteVec(Vec3.atCenterOf(new BlockPos(5, 1, 4))), Scars.BREACH, HealSettings.Kind.CONVENTIONAL);
        for (int x = 4; x <= 6; x++) {
            weapon.remove(helper.absolutePos(new BlockPos(x, 1, 4)));
        }
        weapon.remove(helper.absolutePos(crusherPos));
        helper.runAfterDelay(10, () -> {                   // the generator pushes into the broken run meanwhile
            Healer.of(level).healNow(box(helper), s);
            HealSettings.endTestArea(level, area(helper));
            var crusher = ((dev.groundburst.content.machine.OreCrusherBlockEntity) helper.getBlockEntity(crusherPos));
            helper.assertTrue(crusher.sides().items(dev.groundburst.content.machine.SideConfig.Face.TOP)
                    == dev.groundburst.content.machine.SideConfig.ItemMode.NONE, "the crusher's top face is set as it was");
            crusher.inventory().setItem(0, new ItemStack(Items.RAW_COPPER, 2));
            helper.succeedWhen(() -> {
                ItemStack out = crusher.inventory().getItem(1);
                helper.assertTrue(out.is(dev.groundburst.content.resource.GBResources.COPPER_DUST.get()) && out.getCount() == 4,
                        "the healed cables power the crusher again: " + out);
            });
        });
    }

    /** A pipe run pulling from a chest, cut by a weapon (its pulling end with it), heals pulling again and carries items. */
    private static void pipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HealSettings s = HealSettings.forTests(HealPlayerBlocks.KEEP, 0);
        HealSettings.testArea(level, area(helper), s);
        BlockPos from = new BlockPos(1, 1, 2), to = new BlockPos(6, 1, 2);
        helper.setBlock(from, Blocks.CHEST);
        helper.setBlock(to, Blocks.CHEST);
        for (int x = 2; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), dev.groundburst.content.pipe.GBPipes.ITEM_PIPE.block().get());
        }
        BlockPos first = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockState pulling = level.getBlockState(first).setValue(dev.groundburst.content.pipe.PipeBlock.SIDES.get(Direction.WEST),
                dev.groundburst.content.pipe.PipeBlock.Mode.PULL);
        level.setBlock(first, pulling, Block.UPDATE_ALL);
        dev.groundburst.content.pipe.PipeBlock.changed(level, first, pulling);
        Scars.Recorder weapon = Scars.recorder(level, helper.absoluteVec(Vec3.atCenterOf(new BlockPos(3, 1, 2))), Scars.BREACH, HealSettings.Kind.CONVENTIONAL);
        for (int x = 2; x <= 4; x++) {
            weapon.remove(helper.absolutePos(new BlockPos(x, 1, 2)));
        }
        helper.runAfterDelay(10, () -> {
            Healer.of(level).healNow(box(helper), s);
            HealSettings.endTestArea(level, area(helper));
            helper.assertTrue(dev.groundburst.content.pipe.PipeBlock.mode(level.getBlockState(first), Direction.WEST)
                    == dev.groundburst.content.pipe.PipeBlock.Mode.PULL, "the first pipe pulls from the chest again, as it was set");
            ((Container) ((BlockEntity) helper.getBlockEntity(from))).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
            helper.succeedWhen(() -> {
                Container far = (Container) ((BlockEntity) helper.getBlockEntity(to));
                int n = 0;
                for (int i = 0; i < far.getContainerSize(); i++) {
                    n += far.getItem(i).getCount();
                }
                helper.assertTrue(n == 16, "the healed pipes carry everything to the far chest: " + n);
            });
        });
    }

    /** A creeper's explosion at the plot's middle (the creeper is not in the world: only its explosion is). */
    private static void creeperAt(GameTestHelper helper, Vec3 at) {
        Creeper creeper = new Creeper(EntityType.CREEPER, helper.getLevel());
        creeper.setPos(at);
        helper.getLevel().explode(creeper, at.x, at.y, at.z, 3.0F, Level.ExplosionInteraction.MOB);
    }

    private static void clearDrops(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).forEach(Entity::discard);
    }

    /**
     * A creeper's explosion (the game's own): dirt, grass, a chest of diamonds, stairs, a wall with a torch behind it and
     * an item frame on it, blown up and put back exactly; nothing dropped, the chest with its diamonds, the frame hung again.
     */
    private static void creeper(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 1, 1, 1, 15, 2, 15, Blocks.DIRT);
        fill(helper, 1, 3, 1, 15, 3, 15, Blocks.GRASS_BLOCK);
        BlockPos chest = C.offset(1, 0, 0);
        helper.setBlock(chest, Blocks.CHEST);
        ((Container) ((BlockEntity) helper.getBlockEntity(chest))).setItem(4, new ItemStack(Items.DIAMOND, 37));
        helper.setBlock(C.offset(-1, 0, 0), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        fill(helper, 6, 4, 11, 10, 6, 11, Blocks.STONE_BRICKS);
        helper.setBlock(new BlockPos(8, 5, 12), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        ItemFrame frame = new ItemFrame(level, helper.absolutePos(new BlockPos(8, 5, 10)), Direction.NORTH);
        frame.setItem(new ItemStack(Items.EMERALD));
        helper.assertTrue(frame.survives() && level.addFreshEntity(frame), "the frame hangs on the wall");
        Map<BlockPos, BlockState> before = snapshot(helper);
        HealSettings s = HealSettings.forTests(HealPlayerBlocks.KEEP, 0);
        HealSettings.testArea(level, area(helper), s);
        creeperAt(helper, helper.absoluteVec(Vec3.atCenterOf(C)));
        helper.assertTrue(level.getBlockState(helper.absolutePos(chest)).isAir() && remembered(helper, chest), "the creeper took the chest: remembered");
        helper.assertTrue(frame.isRemoved(), "and the frame off its wall");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area(helper)).isEmpty()
                && level.getEntitiesOfClass(ExperienceOrb.class, area(helper)).isEmpty(), "nothing dropped: it will all come back");
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        String wrong = differs(helper, before);
        helper.assertTrue(wrong == null, "every block back as it was: " + wrong);
        ItemStack held = ((Container) ((BlockEntity) helper.getBlockEntity(chest))).getItem(4);
        helper.assertTrue(held.is(Items.DIAMOND) && held.getCount() == 37, "the chest has its diamonds again: " + held);
        List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, area(helper));
        helper.assertTrue(frames.size() == 1 && dev.groundburst.legacy.J21.first(frames).getItem().is(Items.EMERALD), "the frame hangs again with its emerald: " + frames);
        helper.succeed();
    }

    /** TNT an explosion lights goes off, so it is not put back (it would be had twice); the stone round it is. */
    private static void tntLit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 2, 1, 2, 14, 3, 14, Blocks.STONE);
        BlockPos tnt = C.offset(2, -1, 0), below = C.below();
        helper.setBlock(tnt, Blocks.TNT);
        HealSettings s = HealSettings.forTests(HealPlayerBlocks.KEEP, 0);
        HealSettings.testArea(level, area(helper), s);
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(C));
        PrimedTnt primed = new PrimedTnt(level, at.x, at.y, at.z, null);
        level.explode(primed, at.x, at.y, at.z, 4.0F, Level.ExplosionInteraction.TNT);
        List<PrimedTnt> lit = level.getEntitiesOfClass(PrimedTnt.class, area(helper));
        lit.forEach(Entity::discard);                      // (before it goes off in its turn)
        helper.assertTrue(lit.size() == 1, "the TNT beside it was lit (" + lit.size() + ")");
        helper.assertTrue(level.getBlockState(helper.absolutePos(tnt)).isAir() && !remembered(helper, tnt), "and is not remembered");
        helper.assertTrue(remembered(helper, below), "the stone it blew is");
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        helper.assertTrue(level.getBlockState(helper.absolutePos(below)).is(Blocks.STONE), "the stone is back");
        helper.assertTrue(level.getBlockState(helper.absolutePos(tnt)).isAir(), "the TNT is not");
        helper.succeed();
    }

    /**
     * Which switch an explosion heals under, by what set it off; one switched off, or left out by its id, remembers
     * nothing; a bed's (no entity, the bad respawn point's damage) heals as a bed's.
     */
    private static void explosionSwitches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<String, Kind> kinds = new java.util.LinkedHashMap<>();
        kinds.put("minecraft:creeper", Kind.CREEPER);
        kinds.put("minecraft:tnt", Kind.TNT);
        kinds.put("minecraft:tnt_minecart", Kind.TNT);
        kinds.put("minecraft:end_crystal", Kind.END_CRYSTAL);
        kinds.put("minecraft:fireball", Kind.FIREBALL);
        kinds.put("minecraft:wither", Kind.WITHER);
        kinds.put("minecraft:wither_skull", Kind.WITHER);
        kinds.put("minecraft:bad_respawn_point", Kind.BED);
        kinds.put("minecraft:explosion", Kind.OTHER_MOD);
        kinds.put("minecraft:player", Kind.OTHER_MOD);
        kinds.put("somemod:rocket", Kind.OTHER_MOD);
        for (Map.Entry<String, Kind> e : kinds.entrySet()) {
            Kind k = Explosions.kind(new ResourceLocation(e.getKey()));
            helper.assertTrue(k == e.getValue(), e.getKey() + " heals as " + e.getValue() + ", not " + k);
        }
        HealSettings all = HealSettings.forTests(HealPlayerBlocks.KEEP, 0);
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(C));
        BlockPos below = C.below();
        fill(helper, 2, 1, 2, 14, 3, 14, Blocks.STONE);
        HealSettings.testArea(level, area(helper), all.withKinds(EnumSet.complementOf(EnumSet.of(Kind.CREEPER))));
        creeperAt(helper, at);
        helper.assertTrue(level.getBlockState(helper.absolutePos(below)).isAir() && !remembered(helper, below), "creepers off: not remembered");
        clearDrops(helper);
        fill(helper, 2, 1, 2, 14, 3, 14, Blocks.STONE);
        HealSettings.testArea(level, area(helper), all.withExcluded(Set.of(), List.of(), List.of("minecraft:creep*")));
        creeperAt(helper, at);
        helper.assertTrue(level.getBlockState(helper.absolutePos(below)).isAir() && !remembered(helper, below), "left out by its id: not remembered");
        clearDrops(helper);
        fill(helper, 2, 1, 2, 14, 3, 14, Blocks.STONE);
        HealSettings beds = all.withKinds(EnumSet.of(Kind.BED));
        HealSettings.testArea(level, area(helper), beds);
        level.explode(null, level.damageSources().badRespawnPointExplosion(at), null, at.x, at.y, at.z, 3.0F, false, Level.ExplosionInteraction.BLOCK);
        helper.assertTrue(level.getBlockState(helper.absolutePos(below)).isAir() && remembered(helper, below), "a bed's explosion is remembered");
        Healer.of(level).healNow(box(helper), beds);
        HealSettings.endTestArea(level, area(helper));
        helper.assertTrue(level.getBlockState(helper.absolutePos(below)).is(Blocks.STONE), "and heals");
        helper.succeed();
    }

    /**
     * Another mod's explosion (its own explosion class, nothing behind it) heals under Other mods' explosions: a barrel of
     * emeralds back with them, nothing spilt; and with that switch off it is not remembered.
     */
    private static void otherMods(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 2, 1, 2, 14, 3, 14, Blocks.STONE);
        BlockPos barrel = C.offset(1, -1, 0), below = C.below();
        helper.setBlock(barrel, Blocks.BARREL);
        ((Container) ((BlockEntity) helper.getBlockEntity(barrel))).setItem(0, new ItemStack(Items.EMERALD, 5));
        Map<BlockPos, BlockState> before = snapshot(helper);
        HealSettings mods = HealSettings.forTests(HealPlayerBlocks.KEEP, 0).withKinds(EnumSet.of(Kind.OTHER_MOD));
        HealSettings.testArea(level, area(helper), mods);
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(C));
        Explosion theirs = new Explosion(level, null, null, null, at.x, at.y, at.z, 3.0F, false, Explosion.BlockInteraction.DESTROY) {};
        helper.assertTrue(Explosions.source(theirs).equals(new ResourceLocation("explosion")), "known by its damage: " + Explosions.source(theirs));
        theirs.explode();
        theirs.finalizeExplosion(false);
        helper.assertTrue(level.getBlockState(helper.absolutePos(barrel)).isAir() && remembered(helper, barrel), "the barrel went: remembered");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area(helper)).isEmpty(), "nothing dropped or spilt");
        Healer.of(level).healNow(box(helper), mods);
        String wrong = differs(helper, before);
        helper.assertTrue(wrong == null, "every block back as it was: " + wrong);
        ItemStack held = ((Container) ((BlockEntity) helper.getBlockEntity(barrel))).getItem(0);
        helper.assertTrue(held.is(Items.EMERALD) && held.getCount() == 5, "the barrel has its emeralds again: " + held);
        HealSettings.testArea(level, area(helper), HealSettings.forTests(HealPlayerBlocks.KEEP, 0).withKinds(EnumSet.complementOf(EnumSet.of(Kind.OTHER_MOD))));
        level.explode(null, at.x, at.y, at.z, 3.0F, Level.ExplosionInteraction.BLOCK);
        HealSettings.endTestArea(level, area(helper));
        helper.assertTrue(level.getBlockState(helper.absolutePos(below)).isAir() && !remembered(helper, below), "other mods' off: not remembered");
        clearDrops(helper);
        helper.succeed();
    }

    /** With healing off (the default) the game's explosions are as they always were: nothing remembered, a chest's contents spilt. */
    private static void explosionsOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 2, 1, 2, 14, 3, 14, Blocks.STONE);
        BlockPos chest = C.offset(1, -1, 0);
        helper.setBlock(chest, Blocks.CHEST);
        ((Container) ((BlockEntity) helper.getBlockEntity(chest))).setItem(0, new ItemStack(Items.DIAMOND, 3));
        creeperAt(helper, helper.absoluteVec(Vec3.atCenterOf(C)));
        helper.assertTrue(level.getBlockState(helper.absolutePos(chest)).isAir() && !remembered(helper, chest), "nothing remembered");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area(helper)).stream().anyMatch(i -> i.getItem().is(Items.DIAMOND)),
                "the chest's diamonds spilt, as ever");
        clearDrops(helper);
        helper.succeed();
    }

    /**
     * With Healing blocks drop items too on, a chest a creeper blows spills its diamonds, as the game's explosions do, and
     * comes back empty: nothing is had twice.
     */
    private static void spilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 2, 1, 2, 14, 3, 14, Blocks.STONE);
        BlockPos chest = C.offset(1, -1, 0);
        helper.setBlock(chest, Blocks.CHEST);
        ((Container) ((BlockEntity) helper.getBlockEntity(chest))).setItem(0, new ItemStack(Items.DIAMOND, 3));
        HealSettings s = HealSettings.forTests(HealPlayerBlocks.KEEP, 0).withDrops(true);
        HealSettings.testArea(level, area(helper), s);
        creeperAt(helper, helper.absoluteVec(Vec3.atCenterOf(C)));
        helper.assertTrue(level.getBlockState(helper.absolutePos(chest)).isAir() && remembered(helper, chest), "the creeper took the chest: remembered");
        int spilt = level.getEntitiesOfClass(ItemEntity.class, area(helper)).stream()
                .filter(i -> i.getItem().is(Items.DIAMOND)).mapToInt(i -> i.getItem().getCount()).sum();
        helper.assertTrue(spilt == 3, "its diamonds spilt, drops being on (" + spilt + ")");
        clearDrops(helper);
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        helper.assertTrue(level.getBlockState(helper.absolutePos(chest)).is(Blocks.CHEST), "the chest is back");
        Container back = (Container) ((BlockEntity) helper.getBlockEntity(chest));
        helper.assertTrue(back.isEmpty(), "empty, what spilt not had twice: " + back.getItem(0));
        helper.succeed();
    }

    /**
     * A double chest whose one half a breaching charge takes: the half left standing (single now) keeps what it holds
     * when the damage heals, even what was taken out of it or put in since; and when more damage takes it too, it comes
     * back with what it held then, not what it held the first time.
     */
    private static void chestHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, 2, 1, 2, 14, 3, 14, Blocks.STONE);
        BlockPos left = C.offset(0, -1, 0), right = left.east();   // (facing north, a left half's other half is east of it)
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH);
        helper.setBlock(left, chest.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT));
        helper.setBlock(right, chest.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        ((Container) ((BlockEntity) helper.getBlockEntity(left))).setItem(0, new ItemStack(Items.DIAMOND, 5));
        ((Container) ((BlockEntity) helper.getBlockEntity(right))).setItem(0, new ItemStack(Items.EMERALD, 7));
        BlockState leftWas = level.getBlockState(helper.absolutePos(left)), rightWas = level.getBlockState(helper.absolutePos(right));
        HealSettings s = HealSettings.forTests(HealPlayerBlocks.KEEP, 0);
        HealSettings.testArea(level, area(helper), s);
        Scars.Recorder charge = Scars.recorder(level, helper.absoluteVec(Vec3.atCenterOf(left)), Scars.BREACH, Kind.CONVENTIONAL);
        charge.remove(helper.absolutePos(left));
        helper.assertTrue(level.getBlockState(helper.absolutePos(right)).getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                == net.minecraft.world.level.block.state.properties.ChestType.SINGLE && remembered(helper, right), "the half left is single now: remembered");
        Container standing = (Container) ((BlockEntity) helper.getBlockEntity(right));
        standing.setItem(0, ItemStack.EMPTY);              // (a player takes the emeralds out, and puts gold in)
        standing.setItem(3, new ItemStack(Items.GOLD_INGOT, 2));
        Healer.of(level).healNow(box(helper), s);
        helper.assertTrue(level.getBlockState(helper.absolutePos(left)) == leftWas && level.getBlockState(helper.absolutePos(right)) == rightWas,
                "a double chest again");
        ItemStack diamonds = ((Container) ((BlockEntity) helper.getBlockEntity(left))).getItem(0);
        helper.assertTrue(diamonds.is(Items.DIAMOND) && diamonds.getCount() == 5, "the half taken has its diamonds: " + diamonds);
        Container kept = (Container) ((BlockEntity) helper.getBlockEntity(right));
        helper.assertTrue(kept.getItem(0).isEmpty() && kept.getItem(3).is(Items.GOLD_INGOT) && kept.getItem(3).getCount() == 2,
                "the half left keeps what it holds now, no emeralds had twice and the gold not lost: " + kept.getItem(0) + ", " + kept.getItem(3));
        Scars.Recorder again = Scars.recorder(level, helper.absoluteVec(Vec3.atCenterOf(left)), Scars.BREACH, Kind.CONVENTIONAL);
        again.remove(helper.absolutePos(left));
        ((Container) ((BlockEntity) helper.getBlockEntity(right))).setItem(3, new ItemStack(Items.IRON_INGOT, 4));
        again.remove(helper.absolutePos(right));           // (more damage takes the half left standing too)
        helper.assertTrue(level.getBlockState(helper.absolutePos(right)).isAir(), "then the other half goes too");
        Healer.of(level).healNow(box(helper), s);
        HealSettings.endTestArea(level, area(helper));
        ItemStack iron = ((Container) ((BlockEntity) helper.getBlockEntity(right))).getItem(3);
        helper.assertTrue(iron.is(Items.IRON_INGOT) && iron.getCount() == 4, "it comes back with what it held when it went: " + iron);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area(helper)).isEmpty(), "and nothing dropped");
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
