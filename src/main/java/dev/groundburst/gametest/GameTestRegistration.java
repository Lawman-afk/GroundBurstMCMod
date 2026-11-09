package dev.groundburst.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.groundburst.GroundBurst;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * How the tests {@link GBGameTests} lists reach the game on this branch (main's registers 26.x's test functions and
 * instances): a generator makes each one a 1.21.1 test function over its structure, with its timeout, in one batch, a
 * test run alone in a batch of its own (1.21.1 batches its tests by name, one batch after another: main gives each an
 * environment of its own; docs/BACKPORT.md). Those that cannot run on 1.21.1 are left out by {@link PortSkips}, each with
 * why.
 */
@GameTestHolder(GroundBurst.MODID)
public final class GameTestRegistration {
    private GameTestRegistration() {}

    /** (The holder above registers the generator; nothing to add to the mod bus.) */
    static void register(IEventBus modBus, List<GBGameTests.TestSpec> specs) {}

    @GameTestGenerator
    public static List<TestFunction> tests() {
        List<TestFunction> out = new ArrayList<>();
        for (GBGameTests.TestSpec spec : GBGameTests.specs()) {
            if (PortSkips.skipped(spec.name())) {
                continue;
            }
            String batch = spec.alone() ? "groundburst_alone_" + spec.name() : "groundburst";
            out.add(new TestFunction(batch, spec.name(), spec.structure().toString(), Rotation.NONE, spec.maxTicks(), 0L, true, false,
                    1, 1, spec.sky(), spec.body()));
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (TestFunction f : out) {
            if (!names.add(f.testName())) {
                GroundBurst.LOGGER.warn("GameTests: {} is named twice", f.testName());
            }
        }
        GroundBurst.LOGGER.info("GameTests: {} of main's {} made here ({} left out)", out.size(), GBGameTests.specs().size(),
                GBGameTests.specs().size() - out.size());
        return out;
    }
}
