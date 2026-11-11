package dev.groundburst.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.groundburst.GroundBurst;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * How the tests {@link GBGameTests} lists reach the game on this branch (main's registers 26.x's test functions and
 * instances): a generator makes each one a 1.21.1 test function over its structure, with its timeout, in one batch
 * (docs/BACKPORT.md). Those that cannot run on 1.21.1 are left out by {@link PortSkips}, each with why.
 */
@GameTestHolder(GroundBurst.MODID)
public final class GameTestRegistration {
    private GameTestRegistration() {}

    /**
     * 1.21's walls round a test as it starts (StructureUtils.encaseStructure): barriers just outside its
     * structure, and a roof of them over it unless it has the sky. 1.20.1's tests stand open to their neighbours.
     */
    private static void encase(net.minecraft.gametest.framework.GameTestHelper helper, boolean sky) {
        net.minecraft.world.phys.AABB bounds = ((dev.groundburst.legacy.mixin.GameTestHelperAccess) helper).groundburst$bounds();
        // (1.20.1's bounds run from the structure block, a block under the structure, a size long: the size is all.)
        int sx = (int) Math.round(bounds.maxX - bounds.minX) + 1;
        int sy = (int) Math.round(bounds.maxY - bounds.minY) + 1;
        int sz = (int) Math.round(bounds.maxZ - bounds.minZ) + 1;
        net.minecraft.core.BlockPos corner = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        net.minecraft.core.BlockPos lo = corner.offset(-1, 0, -1), hi = corner.offset(sx, sy, sz);
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(lo, hi)) {
            boolean wall = p.getX() == lo.getX() || p.getX() == hi.getX() || p.getZ() == lo.getZ() || p.getZ() == hi.getZ();
            if (wall || !sky && p.getY() == hi.getY()) {
                helper.getLevel().setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState());
            }
        }
    }

    /** (The holder above registers the generator; nothing to add to the mod bus.) */
    static void register(IEventBus modBus, List<GBGameTests.TestSpec> specs) {}

    @GameTestGenerator
    public static List<TestFunction> tests() {
        List<TestFunction> out = new ArrayList<>();
        for (GBGameTests.TestSpec spec : GBGameTests.specs()) {
            String only = System.getProperty("groundburst.onlyTests", "");
            if (!only.isBlank() && !java.util.Arrays.asList(only.split(",")).contains(spec.name())) {
                continue;
            }
            if (PortSkips.skipped(spec.name())) {
                continue;
            }
            out.add(new TestFunction("groundburst", spec.name(), spec.structure().toString(), Rotation.NONE, spec.maxTicks(), 0L, true, 1, 1, helper -> {
                encase(helper, spec.sky());
                spec.body().accept(helper);
            }));
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (TestFunction f : out) {
            if (!names.add(f.getTestName())) {
                GroundBurst.LOGGER.warn("GameTests: {} is named twice", f.getTestName());
            }
        }
        GroundBurst.LOGGER.info("GameTests: {} of main's {} made here ({} left out)", out.size(), GBGameTests.specs().size(),
                GBGameTests.specs().size() - out.size());
        return out;
    }
}
