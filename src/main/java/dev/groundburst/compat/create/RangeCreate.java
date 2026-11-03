package dev.groundburst.compat.create;

import java.util.List;

import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import dev.groundburst.GroundBurst;
import dev.groundburst.compat.TestRange;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.CruiseLauncherBlockEntity;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The test range's Create contraptions (dev.groundburst.compat.TestRange): a Cruise Missile Launcher turning on a
 * mechanical bearing (a turret), a Launch Silo a mechanical piston raises out of a pit and lowers again (a lever by the
 * pit reverses it, through a redstone link to a gearshift), and a windmill at the target range to blow up. Kept apart so
 * nothing loads Create's classes unless the range is built with Create.
 */
public final class RangeCreate {
    private RangeCreate() {}

    public static final BlockPos TURRET = new BlockPos(58, 0, -10), PIT_SILO = new BlockPos(84, -11, -10);
    private static BlockPos turretMotor = BlockPos.ZERO, pistonMotor = BlockPos.ZERO, millMotor = BlockPos.ZERO, lever = BlockPos.ZERO;

    public static void build(ServerLevel level) {
        turret(level);
        pitSilo(level);
        windmill(level);
    }

    /** The motors set going (after the launchers are on the network): the turret turns, the silo rises, the mill spins. */
    public static void start(ServerLevel level) {
        motor(level, turretMotor, "facing=up", 4);
        motor(level, pistonMotor, "facing=down", 64);
        motor(level, millMotor, "facing=north", 12);
        GroundBurst.LOGGER.info("Test range: motors started");
    }

    private static void motor(ServerLevel level, BlockPos at, String facing, int rpm) {
        TestRange.set(level, at, TestRange.state("create:creative_motor", facing));
        if (level.getBlockEntity(at) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(rpm);
        }
    }

    /** A Cruise Missile Launcher on a bearing under the middle of its deck, a Creative Capacitor Bank glued beside it. */
    private static void turret(ServerLevel level) {
        List<MultiblockDefinition.Cell> cells = TestRange.structure(level, GBLaunch.CRUISE_LAUNCHER, TURRET, Direction.NORTH, true);
        if (level.getBlockEntity(TURRET) instanceof CruiseLauncherBlockEntity c) {
            TestRange.load(c, CruiseLauncherBlockEntity.SLOT_A, TestRange.cruise("he_warhead"));
            TestRange.load(c, CruiseLauncherBlockEntity.SLOT_B, TestRange.cruise("he_warhead"));
        }
        TestRange.launcher(TURRET);
        BlockPos hub = middle(cells);
        TestRange.set(level, hub.below(), TestRange.state("create:mechanical_bearing", "facing=up"));
        turretMotor = hub.below(2);
        TestRange.set(level, turretMotor, Blocks.STONE.defaultBlockState());
        CheckScenes.glue(level, min(cells, level), max(cells, level));
        TestRange.sign(level, TURRET.offset(-3, 0, 2), Direction.SOUTH, "Turret on a", "Create bearing:", "fire it from", "the computers");
    }

    /**
     * A Launch Silo at the bottom of a pit, on a mechanical piston with ten poles under it: its motor raises it to the
     * ground; the lever by the pit powers a redstone link, whose twin by the gearshift reverses the piston.
     */
    private static void pitSilo(ServerLevel level) {
        List<MultiblockDefinition.Cell> trial = dev.groundburst.content.multiblock.StructureSpawnerItem.buildOrder(GBLaunch.SILO, PIT_SILO, Direction.NORTH);
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (MultiblockDefinition.Cell cell : trial) {
            x0 = Math.min(x0, cell.pos().getX());
            x1 = Math.max(x1, cell.pos().getX());
            z0 = Math.min(z0, cell.pos().getZ());
            z1 = Math.max(z1, cell.pos().getZ());
        }
        TestRange.fill(level, x0 - 1, PIT_SILO.getY(), z0 - 1, x1 + 1, -1, z1 + 1, Blocks.AIR.defaultBlockState());
        TestRange.fill(level, x0 - 2, PIT_SILO.getY() - 1, z0 - 2, x1 + 2, PIT_SILO.getY() - 1, z1 + 2, Blocks.STONE_BRICKS.defaultBlockState());
        for (int y = PIT_SILO.getY(); y <= -1; y++) {                       // the pit's walls
            for (int x = x0 - 2; x <= x1 + 2; x++) {
                for (int z = z0 - 2; z <= z1 + 2; z++) {
                    if (x == x0 - 2 || x == x1 + 2 || z == z0 - 2 || z == z1 + 2) {
                        TestRange.set(level, new BlockPos(x, y, z), Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }
            }
        }
        List<MultiblockDefinition.Cell> cells = TestRange.structure(level, GBLaunch.SILO, PIT_SILO, Direction.NORTH, true);
        if (level.getBlockEntity(PIT_SILO) instanceof LaunchSiloBlockEntity s) {
            TestRange.load(s, LaunchSiloBlockEntity.SLOT_MISSILE,
                    TestRange.missile("he_warhead", SizeClass.M, MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS));
        }
        TestRange.launcher(PIT_SILO);
        BlockPos head = middle(cells);
        BlockPos piston = head.below();
        // The piston takes its turn from the south (its shaft along z), from a shaft under the pit's floor, a gearbox past
        // its south wall and a shaft up to the surface: there a gearshift with the lever on it, the motor over that.
        TestRange.set(level, piston, TestRange.state("create:sticky_mechanical_piston", "facing=up", "axis_along_first=false"));
        for (int i = 1; i <= 10; i++) {
            TestRange.set(level, piston.below(i), TestRange.state("create:piston_extension_pole", "facing=up"));
        }
        int wall = z1 + 2;
        for (int z = piston.getZ() + 1; z <= wall; z++) {
            TestRange.set(level, new BlockPos(piston.getX(), piston.getY(), z), TestRange.state("create:shaft", "axis=z"));
        }
        BlockPos gearbox = new BlockPos(piston.getX(), piston.getY(), wall + 1);
        TestRange.set(level, gearbox, TestRange.state("create:gearbox", "axis=x"));
        for (int y = gearbox.getY() + 1; y <= -1; y++) {
            TestRange.set(level, new BlockPos(gearbox.getX(), y, gearbox.getZ()), TestRange.state("create:shaft", "axis=y"));
        }
        BlockPos gearshift = new BlockPos(gearbox.getX(), 0, gearbox.getZ());
        TestRange.set(level, gearshift, TestRange.state("create:gearshift", "axis=y"));
        pistonMotor = gearshift.above();
        CheckScenes.glue(level, min(cells, level), max(cells, level));
        lever = gearshift.east();
        TestRange.set(level, lever, TestRange.state("minecraft:lever", "face=wall", "facing=east"));
        TestRange.sign(level, gearshift.south(), Direction.SOUTH, "Lever: lower", "and raise the", "silo (a Create", "piston)");
    }

    /** The lever by the pit thrown, as a player throws it: the silo's piston reverses. */
    public static void throwLever(ServerLevel level) {
        BlockState state = level.getBlockState(lever);
        if (state.getBlock() instanceof net.minecraft.world.level.block.LeverBlock block) {
            block.pull(state, level, lever, null);
            GroundBurst.LOGGER.info("Test range: the pit lever thrown, now {}", level.getBlockState(lever).getValue(net.minecraft.world.level.block.LeverBlock.POWERED));
        }
    }

    /** Where the pit silo's controller is now (its piston carries it up and down), for the log. */
    public static String pitSiloState(ServerLevel level) {
        for (int y = PIT_SILO.getY(); y <= 0; y++) {
            BlockPos p = new BlockPos(PIT_SILO.getX(), y, PIT_SILO.getZ());
            if (level.getBlockEntity(p) instanceof LaunchSiloBlockEntity s) {
                var n = dev.groundburst.content.launch.LaunchNetwork.of(level).networkOf(p);
                return "standing at y " + y + ", formed " + s.isFormed() + ", missile " + !s.inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty()
                        + ", network " + (n == null ? "none" : n.code());
            }
        }
        return dev.groundburst.content.machine.Carried.at(level, PIT_SILO) != null ? "carried (moving)" : "not found";
    }

    /** A windmill at the target range: a tower, a bearing on its north face, white sails glued in a cross. */
    private static void windmill(ServerLevel level) {
        BlockPos base = TestRange.RANGE.offset(24, 0, 12);
        TestRange.fill(level, base.getX(), 0, base.getZ(), base.getX() + 2, 8, base.getZ() + 2, Blocks.STONE_BRICKS.defaultBlockState());
        BlockPos bearing = new BlockPos(base.getX() + 1, 6, base.getZ() - 1);
        TestRange.set(level, bearing, TestRange.state("create:mechanical_bearing", "facing=north"));
        millMotor = bearing.south();
        BlockPos hub = bearing.north();
        BlockState sail = Blocks.WHITE_WOOL.defaultBlockState();
        TestRange.fill(level, hub.getX() - 5, hub.getY(), hub.getZ(), hub.getX() + 5, hub.getY(), hub.getZ(), sail);
        TestRange.fill(level, hub.getX(), hub.getY() - 5, hub.getZ(), hub.getX(), hub.getY() + 5, hub.getZ(), sail);
        TestRange.set(level, hub, Blocks.SPRUCE_PLANKS.defaultBlockState());
        CheckScenes.glue(level, hub.offset(-5, -5, 0), hub.offset(5, 5, 0));
    }

    /** The deck cell nearest the middle of a structure's bottom layer. */
    private static BlockPos middle(List<MultiblockDefinition.Cell> cells) {
        double sx = 0, sz = 0;
        int n = 0, y = Integer.MAX_VALUE;
        for (MultiblockDefinition.Cell c : cells) {
            y = Math.min(y, c.pos().getY());
        }
        for (MultiblockDefinition.Cell c : cells) {
            if (c.pos().getY() == y && c.key() != '.') {
                sx += c.pos().getX() + 0.5;
                sz += c.pos().getZ() + 0.5;
                n++;
            }
        }
        double mx = sx / n, mz = sz / n;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (MultiblockDefinition.Cell c : cells) {
            if (c.pos().getY() == y && c.key() != '.') {
                double d = Math.pow(c.pos().getX() + 0.5 - mx, 2) + Math.pow(c.pos().getZ() + 0.5 - mz, 2);
                if (d < bestD) {
                    bestD = d;
                    best = c.pos();
                }
            }
        }
        return best;
    }

    /** The corners of a structure's blocks with what stands against them (its Creative Capacitor Bank). */
    private static BlockPos min(List<MultiblockDefinition.Cell> cells, ServerLevel level) {
        AABB box = box(cells);
        return BlockPos.containing(box.minX - 1, box.minY, box.minZ - 1);
    }

    private static BlockPos max(List<MultiblockDefinition.Cell> cells, ServerLevel level) {
        AABB box = box(cells);
        return BlockPos.containing(box.maxX, box.maxY - 1, box.maxZ);
    }

    private static AABB box(List<MultiblockDefinition.Cell> cells) {
        AABB box = null;
        for (MultiblockDefinition.Cell c : cells) {
            if (c.key() != '.') {
                AABB b = new AABB(c.pos());
                box = box == null ? b : box.minmax(b);
            }
        }
        return box;
    }
}
