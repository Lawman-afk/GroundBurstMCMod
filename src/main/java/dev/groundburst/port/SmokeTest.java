package dev.groundburst.port;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.ParticleBeams;
import dev.groundburst.content.fluid.GBFluids;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.pipe.PipeBlock;
import dev.groundburst.content.power.CableBlock;
import dev.groundburst.port.transfer.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * This branch's GameTests (main's own are not ported yet): the game-test server only starts with a test to run, and
 * starting it loads everything of the mod's on 1.21.1, its registries and its data (recipes, tags, loot tables, world
 * generation), with every problem in the log. A few more check what the port itself carries.
 */
@GameTestHolder(GroundBurst.MODID)
@PrefixGameTestTemplate(false)
public final class SmokeTest {
    private SmokeTest() {}

    @GameTest(template = "gametest/floor_16")
    public static void loads(GameTestHelper helper) {
        helper.succeed();
    }

    /** Main's particle_beam_melts_a_trench, in short: a beam parked on grass over stone eats a trench five deep. */
    @GameTest(template = "gametest/floor_16", timeoutTicks = 240)
    public static void uplinkBeamCarves(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 15, 6, 15)) {
            helper.setBlock(p, p.getY() == 6 ? Blocks.GRASS_BLOCK : p.getY() == 5 ? Blocks.DIRT : Blocks.STONE);
        }
        ParticleBeams.burnOverride = 80;
        int id;
        try {
            id = ParticleBeams.strike(helper.getLevel(), helper.absolutePos(new BlockPos(8, 7, 8)), null, false);
        } finally {
            ParticleBeams.burnOverride = 0;
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(!ParticleBeams.running(helper.getLevel(), id), "the beam has burned out");
            for (int y = 2; y <= 6; y++) {
                BlockState s = helper.getBlockState(new BlockPos(8, y, 8));
                helper.assertTrue(s.isAir(), "the trench eaten down to y 2: " + s + " at y " + y);
            }
        });
    }

    /**
     * Main's heal_puts_a_blast_back_exactly, in short (DESIGN.md §21.9): ground, grass, a chest of diamonds and stairs
     * facing east, blown up and healed back block for block, the chest with its diamonds, nothing dropped meanwhile.
     */
    @GameTest(template = "gametest/floor_16")
    public static void healsABlastBack(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        for (BlockPos p : BlockPos.betweenClosed(1, 1, 1, 15, 3, 15)) {
            helper.setBlock(p, p.getY() == 3 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        }
        BlockPos middle = new BlockPos(8, 4, 8), chest = middle.east();
        helper.setBlock(chest, Blocks.CHEST);
        ((net.minecraft.world.Container) helper.getBlockEntity(chest)).setItem(4, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 37));
        helper.setBlock(middle.west(), Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.EAST));
        net.minecraft.world.phys.AABB bounds = ((dev.groundburst.legacy.mixin.GameTestHelperAccess) helper).groundburst$bounds(), area = bounds.inflate(2);
        java.util.Map<BlockPos, BlockState> before = new java.util.HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX - 1, bounds.maxY - 1, bounds.maxZ - 1))) {
            before.put(p.immutable(), level.getBlockState(p));
        }
        dev.groundburst.heal.HealSettings s = dev.groundburst.heal.HealSettings.forTests(
                dev.groundburst.config.GBServerConfig.HealPlayerBlocks.KEEP, 0);
        dev.groundburst.heal.HealSettings.testArea(level, area, s);
        try {
            dev.groundburst.blast.Blasts.detonate(level, helper.absoluteVec(Vec3.atCenterOf(middle)),
                    dev.groundburst.blast.Blasts.profile("test/small"), null);
            helper.assertTrue(level.getBlockState(helper.absolutePos(chest)).isAir(), "the blast took the chest");
            helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).isEmpty(),
                    "nothing dropped: it will all come back");
            dev.groundburst.heal.Healer.of(level).healNow(net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
                    BlockPos.containing(area.minX, area.minY, area.minZ), BlockPos.containing(area.maxX - 1e-3, area.maxY - 1e-3, area.maxZ - 1e-3)), s);
        } finally {
            dev.groundburst.heal.HealSettings.endTestArea(level, area);
        }
        for (java.util.Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
            BlockState now = level.getBlockState(e.getKey());
            helper.assertTrue(now == e.getValue(), "back as it was at " + helper.relativePos(e.getKey()) + ": " + now + ", was " + e.getValue());
        }
        ItemStack held = ((net.minecraft.world.Container) helper.getBlockEntity(chest)).getItem(4);
        helper.assertTrue(held.is(net.minecraft.world.item.Items.DIAMOND) && held.getCount() == 37, "the chest has its diamonds again: " + held);
        helper.succeed();
    }

    /**
     * Main's heal_puts_a_creeper_hole_back and heal_leaves_tnt_an_explosion_lit, in short (DESIGN.md §21.9): a creeper's
     * explosion (the game's own) over dirt, grass, a chest of diamonds and a wall with an item frame on it, healed back
     * block for block, nothing dropped meanwhile, the chest with its diamonds and the frame hung again with its emerald;
     * a block of TNT the explosion lit goes off and is not put back.
     */
    @GameTest(template = "gametest/floor_16")
    public static void healsTheGamesExplosions(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        for (BlockPos p : BlockPos.betweenClosed(1, 1, 1, 15, 3, 15)) {
            helper.setBlock(p, p.getY() == 3 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        }
        BlockPos middle = new BlockPos(8, 4, 8), chest = middle.east(), tnt = middle.offset(-2, 0, 0);
        helper.setBlock(chest, Blocks.CHEST);
        ((net.minecraft.world.Container) helper.getBlockEntity(chest)).setItem(4, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 37));
        for (BlockPos p : BlockPos.betweenClosed(6, 4, 11, 10, 6, 11)) {
            helper.setBlock(p, Blocks.STONE_BRICKS);
        }
        var frame = new net.minecraft.world.entity.decoration.ItemFrame(level, helper.absolutePos(new BlockPos(8, 5, 10)), Direction.NORTH);
        frame.setItem(new ItemStack(net.minecraft.world.item.Items.EMERALD));
        helper.assertTrue(frame.survives() && level.addFreshEntity(frame), "the frame hangs on its wall");
        net.minecraft.world.phys.AABB bounds = ((dev.groundburst.legacy.mixin.GameTestHelperAccess) helper).groundburst$bounds(), area = bounds.inflate(2);
        java.util.Map<BlockPos, BlockState> before = new java.util.HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX - 1, bounds.maxY - 1, bounds.maxZ - 1))) {
            before.put(p.immutable(), level.getBlockState(p));
        }
        helper.setBlock(tnt, Blocks.TNT);                  // (after the picture of the plot: lit, it is not put back)
        dev.groundburst.heal.HealSettings s = dev.groundburst.heal.HealSettings.forTests(
                dev.groundburst.config.GBServerConfig.HealPlayerBlocks.KEEP, 0);
        dev.groundburst.heal.HealSettings.testArea(level, area, s);
        try {
            Vec3 at = helper.absoluteVec(Vec3.atCenterOf(middle));
            var creeper = new net.minecraft.world.entity.monster.Creeper(net.minecraft.world.entity.EntityType.CREEPER, level);
            creeper.setPos(at);
            level.explode(creeper, at.x, at.y, at.z, 3.0F, net.minecraft.world.level.Level.ExplosionInteraction.MOB);
            helper.assertTrue(level.getBlockState(helper.absolutePos(chest)).isAir(), "the creeper took the chest");
            helper.assertTrue(frame.isRemoved(), "and the frame off its wall");
            var lit = level.getEntitiesOfClass(net.minecraft.world.entity.item.PrimedTnt.class, area);
            lit.forEach(net.minecraft.world.entity.Entity::discard);
            helper.assertTrue(lit.size() == 1 && !dev.groundburst.heal.Scars.remembered(level, helper.absolutePos(tnt)),
                    "the TNT beside it was lit, and is not remembered (" + lit.size() + ")");
            helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).isEmpty()
                    && level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, area).isEmpty(), "nothing dropped: it will all come back");
            dev.groundburst.heal.Healer.of(level).healNow(net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
                    BlockPos.containing(area.minX, area.minY, area.minZ), BlockPos.containing(area.maxX - 1e-3, area.maxY - 1e-3, area.maxZ - 1e-3)), s);
        } finally {
            dev.groundburst.heal.HealSettings.endTestArea(level, area);
        }
        for (java.util.Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
            BlockState now = level.getBlockState(e.getKey());
            helper.assertTrue(now == e.getValue(), "back as it was at " + helper.relativePos(e.getKey()) + ": " + now + ", was " + e.getValue());
        }
        ItemStack held = ((net.minecraft.world.Container) helper.getBlockEntity(chest)).getItem(4);
        helper.assertTrue(held.is(net.minecraft.world.item.Items.DIAMOND) && held.getCount() == 37, "the chest has its diamonds again: " + held);
        var frames = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, area);
        helper.assertTrue(frames.size() == 1 && dev.groundburst.legacy.J21.first(frames).getItem().is(net.minecraft.world.item.Items.EMERALD),
                "the frame hangs again with its emerald: " + frames);
        helper.succeed();
    }

    /** Transactions are each thread's own (the client's and the server's run side by side in one game). */
    @GameTest(template = "gametest/floor_16")
    public static void transactionsPerThread(GameTestHelper helper) throws InterruptedException {
        boolean[] opened = {false};
        try (Transaction outer = Transaction.openRoot()) {
            Thread other = new Thread(() -> {
                try (Transaction t = Transaction.openRoot()) {
                    opened[0] = true;
                }
            });
            other.start();
            other.join();
        }
        helper.assertTrue(opened[0], "another thread opens its own root transaction while one is open here");
        helper.succeed();
    }

    /**
     * Every site's template (and the GameTests' floor) holds its blocks: main saves them with 26.x's palette names, which
     * 1.21.1 read as air, so the world's sites came out empty until the port renamed them (docs/BACKPORT.md).
     */
    @GameTest(template = "gametest/floor_16")
    public static void sitesHaveTheirBlocks(GameTestHelper helper) throws ReflectiveOperationException {
        var manager = helper.getLevel().getStructureManager();
        java.lang.reflect.Field palettes = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.class.getDeclaredField("palettes");
        palettes.setAccessible(true);
        for (String name : java.util.List.of("gametest/floor_16", "gametest/stone_12", "site/bunker_below", "site/bunker_surface",
                "site/crashed_missile", "site/radar_outpost", "site/research_station", "site/silo_below_collapsed", "site/silo_below_flooded",
                "site/silo_below_intact", "site/silo_surface_collapsed", "site/silo_surface_flooded", "site/silo_surface_intact",
                "site/test_room", "site/test_site_tower", "site/test_site_zero", "site/waste_storage")) {
            var template = manager.get(new net.minecraft.resources.ResourceLocation(GroundBurst.MODID, name));
            helper.assertTrue(template.isPresent(), "the template " + name + " loads");
            int solid = 0, total = 0;
            for (Object palette : (java.util.List<?>) palettes.get(template.get())) {
                for (var info : ((net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette) palette).blocks()) {
                    total++;
                    if (!info.state().isAir()) {
                        solid++;
                    }
                }
            }
            helper.assertTrue(solid > 0, name + " has its blocks: " + solid + " of " + total + " not air");
        }
        helper.succeed();
    }

    /** The Wrench on a pipe joined to the pipe beside it, then on a cable joined to one: each run split on both, then joined again. */
    @GameTest(template = "gametest/floor_16")
    public static void wrenchSplitsRuns(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack wrench = new ItemStack(GBMachines.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        BlockPos a = new BlockPos(4, 2, 4), b = a.east(), c = new BlockPos(4, 2, 8), d = c.east();
        helper.setBlock(a, GBFluids.FLUID_PIPE.block().get());
        helper.setBlock(b, GBFluids.FLUID_PIPE.block().get());
        helper.setBlock(c, GBMachines.CABLES.get(0).block().get());
        helper.setBlock(d, GBMachines.CABLES.get(0).block().get());
        helper.assertTrue(pipe(helper, a, b) == PipeBlock.Mode.NORMAL, "two pipes side by side join: " + pipe(helper, a, b));
        helper.assertTrue(cable(helper, c, d) == CableBlock.Side.CONNECTED, "two cables side by side join: " + cable(helper, c, d));
        wrench(helper, player, wrench, a);
        helper.assertTrue(pipe(helper, a, b) == PipeBlock.Mode.DISABLED && pipe(helper, b, a) == PipeBlock.Mode.DISABLED,
                "wrenched, the pipes split: " + pipe(helper, a, b) + ", " + pipe(helper, b, a));
        wrench(helper, player, wrench, a);
        helper.assertTrue(pipe(helper, a, b) == PipeBlock.Mode.NORMAL && pipe(helper, b, a) == PipeBlock.Mode.NORMAL,
                "wrenched again, joined: " + pipe(helper, a, b) + ", " + pipe(helper, b, a));
        wrench(helper, player, wrench, c);
        helper.assertTrue(cable(helper, c, d) == CableBlock.Side.DISABLED && cable(helper, d, c) == CableBlock.Side.DISABLED,
                "wrenched, the cables split: " + cable(helper, c, d) + ", " + cable(helper, d, c));
        wrench(helper, player, wrench, c);
        helper.assertTrue(cable(helper, c, d) == CableBlock.Side.CONNECTED && cable(helper, d, c) == CableBlock.Side.CONNECTED,
                "wrenched again, joined: " + cable(helper, c, d) + ", " + cable(helper, d, c));
        helper.succeed();
    }

    /** A right click with the wrench on {@code at}'s east face. */
    private static void wrench(GameTestHelper helper, Player player, ItemStack wrench, BlockPos at) {
        BlockPos abs = helper.absolutePos(at);
        wrench.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(abs.getX() + 1.0, abs.getY() + 0.5, abs.getZ() + 0.5), Direction.EAST, abs, false)));
    }

    private static PipeBlock.Mode pipe(GameTestHelper helper, BlockPos at, BlockPos toward) {
        return PipeBlock.mode(helper.getBlockState(at), Direction.fromDelta(toward.getX() - at.getX(), 0, toward.getZ() - at.getZ()));
    }

    private static CableBlock.Side cable(GameTestHelper helper, BlockPos at, BlockPos toward) {
        return CableBlock.side(helper.getBlockState(at), Direction.fromDelta(toward.getX() - at.getX(), 0, toward.getZ() - at.getZ()));
    }
}
