package dev.groundburst.port;

import dev.groundburst.GroundBurst;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * This branch's one GameTest (main's own are not ported yet): the game-test server only starts with a test to run, and
 * starting it loads everything of the mod's on 1.21.1, its registries and its data (recipes, tags, loot tables, world
 * generation), with every problem in the log.
 */
@GameTestHolder(GroundBurst.MODID)
@PrefixGameTestTemplate(false)
public final class SmokeTest {
    private SmokeTest() {}

    @GameTest(template = "gametest/floor_16")
    public static void loads(GameTestHelper helper) {
        helper.succeed();
    }
}
