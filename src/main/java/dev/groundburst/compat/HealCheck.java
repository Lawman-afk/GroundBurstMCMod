package dev.groundburst.compat;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBServerConfig.HealPlayerBlocks;
import dev.groundburst.heal.HealSettings;
import dev.groundburst.heal.Healer;
import dev.groundburst.heal.Scars;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Create check's healing scene ({@link CreateCheck}): another mod's machines come back working (main's DESIGN.md
 * §21.9). A creative motor turns a row of shafts; a weapon takes three out of the middle where healing is on, and the far
 * end stops; healing puts them back, and the far end turns again at the speed it had.
 */
final class HealCheck {
    static final int START = 1700, END = 1800;
    private static final int Y = -60, Z = -80, MOTOR_X = -60, FIRST = -59, LAST = -52, CUT_FROM = -57, CUT_TO = -55;
    private static final AABB AREA = new AABB(-64, -63, -84, -46, -54, -76);
    private static final HealSettings SETTINGS = HealSettings.forTests(HealPlayerBlocks.KEEP, 0);
    private static float turning;

    private HealCheck() {}

    static void tick(Minecraft mc, int ticks) {
        switch (ticks) {
            case START -> CreateCheck.look(mc, new Vec3(-55.5, -56, -72.5), new Vec3(-55.5, -60, -79.5));
            case START + 5 -> CreateCheck.onServer(mc, level -> {
                HealSettings.testArea(level, AREA, SETTINGS);
                level.setBlockAndUpdate(new BlockPos(MOTOR_X, Y, Z), block("create:creative_motor").setValue(BlockStateProperties.FACING, Direction.EAST));
                for (int x = FIRST; x <= LAST; x++) {
                    level.setBlockAndUpdate(new BlockPos(x, Y, Z), shaft());
                }
            });
            case START + 30 -> CreateCheck.onServer(mc, level -> {
                turning = speed(level, LAST);
                CreateCheck.check(turning != 0, "a creative motor turns a row of shafts (the far end at " + turning + " rpm)");
            });
            case START + 31 -> CreateCheck.onServer(mc, level -> {
                Scars.Recorder weapon = Scars.recorder(level, new Vec3(-55.5, Y + 0.5, Z + 0.5), Scars.BREACH, HealSettings.Kind.CONVENTIONAL);
                for (int x = CUT_FROM; x <= CUT_TO; x++) {
                    weapon.remove(new BlockPos(x, Y, Z));
                }
            });
            case START + 50 -> CreateCheck.onServer(mc, level -> GroundBurst.LOGGER.info(
                    "Create check: a weapon took three shafts out of the middle; the far end turns at {} rpm", speed(level, LAST)));
            case START + 51 -> CreateCheck.onServer(mc, level -> {
                Healer.of(level).healNow(new BoundingBox((int) AREA.minX, (int) AREA.minY, (int) AREA.minZ,
                        (int) AREA.maxX, (int) AREA.maxY, (int) AREA.maxZ), SETTINGS);
                HealSettings.endTestArea(level, AREA);
            });
            case START + 90 -> CreateCheck.onServer(mc, level -> {
                boolean back = true;
                for (int x = CUT_FROM; x <= CUT_TO; x++) {
                    back &= level.getBlockState(new BlockPos(x, Y, Z)) == shaft();
                }
                float now = speed(level, LAST);
                CreateCheck.check(back && now == turning, "healing put the shafts back (" + back + ") and the far end turns again at "
                        + now + " rpm (" + turning + " before)");
            });
            case START + 91 -> CreateCheck.shot(mc, "healed_shafts");
            default -> { }
        }
    }

    /** A shaft's speed in rpm, 0 if there is none (read by name: Create's kinetic block entity is a Ponder type too, not compiled against). */
    private static float speed(ServerLevel level, int x) {
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(new BlockPos(x, Y, Z));
        if (be == null) {
            return 0;
        }
        try {
            return ((Number) be.getClass().getMethod("getSpeed").invoke(be)).floatValue();
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    private static BlockState shaft() {
        return block("create:shaft").setValue(BlockStateProperties.AXIS, Direction.Axis.X);
    }

    private static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation(id)).defaultBlockState();
    }
}
