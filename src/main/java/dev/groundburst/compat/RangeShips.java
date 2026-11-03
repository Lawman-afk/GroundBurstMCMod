package dev.groundburst.compat;

import static dev.groundburst.compat.TestRange.fill;
import static dev.groundburst.compat.TestRange.set;
import static dev.groundburst.compat.TestRange.state;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.CruiseLauncherBlockEntity;
import dev.groundburst.content.launch.GBLaunch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The test range's Create Aeronautics ships (Sable sub-levels): a missile boat on the lake with a Cruise Missile Launcher
 * on deck, a target boat, an airship with a Cruise Missile Launcher aboard held up by levitite under a balloon, and a
 * target airship. Each is built from blocks and made a ship with {@code /sable assemble area}.
 */
final class RangeShips {
    private RangeShips() {}

    /** A ship to be: its name, the box its blocks are in, and once made the ship (Sable's, as an object). */
    private static final class Ship {
        final String name;
        final AABB box;
        @Nullable Object made;
        @Nullable Vec3 first;

        Ship(String name, BlockPos min, BlockPos max) {
            this.name = name;
            this.box = AABB.encapsulatingFullBlocks(min, max);
        }
    }

    private static final List<Ship> SHIPS = new ArrayList<>();
    static final BlockPos BOAT_LAUNCHER = new BlockPos(-84, 1, 44), AIRSHIP_LAUNCHER = new BlockPos(41, 25, 61);

    private static final BlockState HULL = Blocks.SPRUCE_PLANKS.defaultBlockState(), DECK = Blocks.SPRUCE_PLANKS.defaultBlockState(),
            AIR = Blocks.AIR.defaultBlockState(), RAIL = Blocks.SPRUCE_FENCE.defaultBlockState(), GLASS = Blocks.GLASS_PANE.defaultBlockState();

    static void build(ServerLevel level) {
        missileBoat(level);
        targetBoat(level);
        airship(level);
        targetAirship(level);
    }

    /** Each made a ship. */
    static void assemble(ServerLevel level) {
        for (Ship s : SHIPS) {
            BlockPos a = BlockPos.containing(s.box.minX, s.box.minY, s.box.minZ), b = BlockPos.containing(s.box.maxX - 1, s.box.maxY - 1, s.box.maxZ - 1);
            TestRange.serverCommand(level, "sable assemble area " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ());
        }
    }

    /** Where each ship is, for the log (it should float, or hover, where it was made). */
    static void watch(ServerLevel level) {
        for (Ship s : SHIPS) {
            if (s.made == null) {
                s.made = dev.groundburst.compat.sable.SableScenes.shipIn(level, s.box.inflate(2));
            }
            if (s.made == null) {
                GroundBurst.LOGGER.info("Test range: {}: not a ship", s.name);
                continue;
            }
            Vec3 at = dev.groundburst.compat.sable.SableScenes.position(s.made);
            if (s.first == null) {
                s.first = at;
            }
            GroundBurst.LOGGER.info("Test range: {} at {} (moved {}), {} blocks", s.name, at, at.subtract(s.first),
                    dev.groundburst.compat.sable.SableScenes.blocks(level, s.made));
        }
    }

    /** A hull: a floor and walls of spruce round an empty inside, a deck over it, railings along its sides. */
    private static void hull(ServerLevel level, int x0, int z0, int x1, int z1, int bottom, int deck) {
        fill(level, x0, bottom, z0, x1, deck, z1, HULL);
        fill(level, x0 + 1, bottom + 1, z0 + 1, x1 - 1, deck - 1, z1 - 1, AIR);
        fill(level, x0, deck, z0, x1, deck, z1, DECK);
        fill(level, x0, deck + 1, z0, x0, deck + 1, z1, RAIL);
        fill(level, x1, deck + 1, z0, x1, deck + 1, z1, RAIL);
    }

    /** A cabin: spruce walls with glass, a slab roof. */
    private static void cabin(ServerLevel level, int x0, int y, int z0, int x1, int z1) {
        fill(level, x0, y, z0, x1, y + 2, z1, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        fill(level, x0 + 1, y, z0 + 1, x1 - 1, y + 2, z1 - 1, AIR);
        fill(level, x0 + 1, y + 1, z0, x1 - 1, y + 1, z0, GLASS);
        fill(level, x0, y + 3, z0, x1, y + 3, z1, Blocks.SPRUCE_SLAB.defaultBlockState());
    }

    /** The missile boat: a 6x16 hull on the lake, a Cruise Missile Launcher on its deck (aimed off the bow), a bridge aft. */
    private static void missileBoat(ServerLevel level) {
        int x0 = -86, x1 = -81, z0 = 40, z1 = 55;
        hull(level, x0, z0, x1, z1, -4, 0);
        TestRange.structure(level, GBLaunch.CRUISE_LAUNCHER, BOAT_LAUNCHER, Direction.NORTH, true);
        if (TestRange.cruiseAt(level, BOAT_LAUNCHER) instanceof CruiseLauncherBlockEntity c) {
            TestRange.load(c, CruiseLauncherBlockEntity.SLOT_A, TestRange.cruise("he_warhead"));
            TestRange.load(c, CruiseLauncherBlockEntity.SLOT_B, TestRange.cruise("napalm_warhead"));
        }
        TestRange.launcher(BOAT_LAUNCHER);
        cabin(level, x0 + 1, 1, 51, x1 - 1, 54);
        SHIPS.add(new Ship("the missile boat", new BlockPos(x0, -4, z0), new BlockPos(x1, 5, z1)));
        TestRange.sign(level, new BlockPos(-44, 0, 2), Direction.SOUTH, "W: missile boat", "(a ship: fire it", "from the", "computers)");
    }

    /** A target boat: a 5x11 hull with a cabin. */
    private static void targetBoat(ServerLevel level) {
        int x0 = -76, x1 = -72, z0 = 80, z1 = 90;
        hull(level, x0, z0, x1, z1, -3, 0);
        cabin(level, x0 + 1, 1, 84, x1 - 1, 88);
        fill(level, x0 + 2, 5, 86, x0 + 2, 7, 86, Blocks.SPRUCE_FENCE.defaultBlockState());
        SHIPS.add(new Ship("the target boat", new BlockPos(x0, -3, z0), new BlockPos(x1, 8, z1)));
    }

    /**
     * The airship: a 7x21 spruce deck 24 over the airfield with a Cruise Missile Launcher on its bow, levitite under its
     * keel to hold it up, a balloon of white envelope over it on four posts.
     */
    private static void airship(ServerLevel level) {
        int x0 = 38, x1 = 44, z0 = 60, z1 = 80, deck = 24;
        fill(level, x0, deck, z0, x1, deck, z1, DECK);
        fill(level, x0, deck + 1, z0, x0, deck + 1, z1, RAIL);
        fill(level, x1, deck + 1, z0, x1, deck + 1, z1, RAIL);
        fill(level, x0 + 1, deck - 1, z0 + 2, x1 - 1, deck - 1, z1 - 2, Blocks.SPRUCE_PLANKS.defaultBlockState());   // the keel
        fill(level, x0 + 2, deck - 1, z0 + 3, x1 - 2, deck - 1, z1 - 3, state("aeronautics:levitite"));
        TestRange.structure(level, GBLaunch.CRUISE_LAUNCHER, AIRSHIP_LAUNCHER, Direction.NORTH, true);
        if (TestRange.cruiseAt(level, AIRSHIP_LAUNCHER) instanceof CruiseLauncherBlockEntity c) {
            TestRange.load(c, CruiseLauncherBlockEntity.SLOT_A, TestRange.cruise("he_warhead"));
            TestRange.load(c, CruiseLauncherBlockEntity.SLOT_B, TestRange.cruise("cluster_warhead"));
        }
        TestRange.launcher(AIRSHIP_LAUNCHER);
        cabin(level, x0 + 1, deck + 1, 74, x1 - 1, 78);
        // The balloon: a hollow envelope 9 wide, 7 high and 21 long, its edges rounded off, on four spruce posts.
        int bx0 = x0 - 1, bx1 = x1 + 1, by0 = deck + 7, by1 = deck + 13, bz0 = z0, bz1 = z1;
        BlockState envelope = state("aeronautics:white_envelope");
        for (BlockPos p : BlockPos.betweenClosed(bx0, by0, bz0, bx1, by1, bz1)) {
            boolean xEdge = p.getX() == bx0 || p.getX() == bx1, yEdge = p.getY() == by0 || p.getY() == by1, zEdge = p.getZ() == bz0 || p.getZ() == bz1;
            int edges = (xEdge ? 1 : 0) + (yEdge ? 1 : 0) + (zEdge ? 1 : 0);
            if (edges == 1) {
                set(level, p, envelope);                          // a shell, its edges and corners left off: rounded
            }
        }
        fill(level, bx0 + 1, (by0 + by1) / 2, bz0 + 1, bx1 - 1, (by0 + by1) / 2, bz0 + 1, state("aeronautics:red_envelope"));
        for (int[] post : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            fill(level, post[0], deck + 1, post[1], post[0], by0 - 1, post[1], RAIL);
        }
        SHIPS.add(new Ship("the airship", new BlockPos(bx0, deck - 1, bz0), new BlockPos(bx1, by1, bz1)));
        TestRange.sign(level, new BlockPos(4, 0, 38), Direction.SOUTH, "Airship overhead", "(a ship: fire its", "launcher from", "the computers)");
    }

    /** A target airship: a 5x11 gondola under a 7x5x13 balloon, levitite in its keel, hovering south of the airfield. */
    private static void targetAirship(ServerLevel level) {
        int x0 = 55, x1 = 59, z0 = 125, z1 = 135, deck = 32;
        fill(level, x0, deck, z0, x1, deck, z1, DECK);
        fill(level, x0 + 1, deck + 1, z0 + 2, x1 - 1, deck + 2, z1 - 2, Blocks.SPRUCE_PLANKS.defaultBlockState());
        fill(level, x0 + 1, deck - 1, z0 + 2, x1 - 1, deck - 1, z1 - 2, state("aeronautics:levitite"));
        int bx0 = x0 - 1, bx1 = x1 + 1, by0 = deck + 3, by1 = deck + 7, bz0 = z0 - 1, bz1 = z1 + 1;
        BlockState envelope = state("aeronautics:gray_envelope");
        for (BlockPos p : BlockPos.betweenClosed(bx0, by0, bz0, bx1, by1, bz1)) {
            boolean xEdge = p.getX() == bx0 || p.getX() == bx1, yEdge = p.getY() == by0 || p.getY() == by1, zEdge = p.getZ() == bz0 || p.getZ() == bz1;
            if ((xEdge ? 1 : 0) + (yEdge ? 1 : 0) + (zEdge ? 1 : 0) == 1) {
                set(level, p, envelope);
            }
        }
        SHIPS.add(new Ship("the target airship", new BlockPos(bx0, deck - 1, bz0), new BlockPos(bx1, by1, bz1)));
    }
}
