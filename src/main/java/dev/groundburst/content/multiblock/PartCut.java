package dev.groundburst.content.multiblock;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What a formed part block has cut away from its full cube, so its outline and collision follow the formed model: a
 * corner on a 45-degree slope (the Particle Accelerator's octagonal corners), or a shape you can walk on (the Launch
 * Complex's deck and its flame trench, the Radar Array's deck with its railings; after slice 9b, the user's request).
 * Only formed parts are cut; the controller takes one too.
 *
 * <p>The walkable shapes are written for a structure whose controller faces north (as patterns are) and come turned
 * four ways: {@code _NORTH} as written, {@code _EAST} turned for a controller facing east, and so on. A pattern names
 * the north one; {@link #rotated} turns it with the structure.
 */
public enum PartCut implements StringRepresentable {
    NONE, NORTH_WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST,
    /** The Launch Complex's deck: a slab 14 px high. */
    DECK,
    /** The flame trench: a floor 2 px high between strips of deck along its two sides, running west to east. */
    TRENCH_NORTH, TRENCH_EAST, TRENCH_SOUTH, TRENCH_WEST,
    /** The deflector under the missile: the trench's floor rising east in three steps (5, 8, 11 px) toward the deck. */
    STEPS_NORTH, STEPS_EAST, STEPS_SOUTH, STEPS_WEST,
    /** The complex's controller: the deck with its console standing on the front half. */
    CONSOLE_NORTH, CONSOLE_EAST, CONSOLE_SOUTH, CONSOLE_WEST,
    /** The complex's Energy Port: the deck with the port's housing along the back. */
    HOUSING_NORTH, HOUSING_EAST, HOUSING_SOUTH, HOUSING_WEST,
    /** The Radar Array's deck (a slab 6 px high) with a railing along its west side. */
    RAIL_NORTH, RAIL_EAST, RAIL_SOUTH, RAIL_WEST,
    /** The radar deck with railings along its west and south sides (a back corner). */
    CORNER_RAIL_NORTH, CORNER_RAIL_EAST, CORNER_RAIL_SOUTH, CORNER_RAIL_WEST,
    /** The radar deck's middle, the mast's foot standing on it. */
    MAST,
    /** The radar's controller: the deck with its cabinet on the front half. */
    CABINET_NORTH, CABINET_EAST, CABINET_SOUTH, CABINET_WEST,
    /** The radar's Energy Port: the deck with its power box and the back railing. */
    POWER_BOX_NORTH, POWER_BOX_EAST, POWER_BOX_SOUTH, POWER_BOX_WEST;

    /**
     * Legs of the cut-away triangle in pixels. A ring one block wide whose 45-degree side runs through a staircase of
     * cells has its edges 1.5 - √½ blocks in from the stair corners (half the ring's width, measured across the slope).
     */
    public static final double LEG = 16 * (1.5 - Math.sqrt(0.5));
    /** The slope is built from slices this many pixels thick. */
    private static final int STEP = 2;
    /** Deck heights (pixels): the Launch Complex's pad, the Radar Array's deck. */
    private static final double DECK_TOP = 14, RADAR_TOP = 6;
    private static final Map<PartCut, VoxelShape> SHAPES = new EnumMap<>(PartCut.class);
    private static final Direction[] TURNS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    static {
        for (PartCut cut : values()) {
            SHAPES.put(cut, cut == NONE ? Shapes.block() : cut.isCorner() ? build(cut) : walkable(cut));
        }
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The block shape with this cut. */
    public VoxelShape shape() {
        return SHAPES.get(this);
    }

    private boolean isCorner() {
        return this == NORTH_WEST || this == NORTH_EAST || this == SOUTH_EAST || this == SOUTH_WEST;
    }

    /** The corner between two perpendicular horizontal directions (in either order). */
    public static PartCut of(Direction a, Direction b) {
        boolean north = a == Direction.NORTH || b == Direction.NORTH;
        boolean south = a == Direction.SOUTH || b == Direction.SOUTH;
        boolean west = a == Direction.WEST || b == Direction.WEST;
        boolean east = a == Direction.EAST || b == Direction.EAST;
        if (north && west) {
            return NORTH_WEST;
        }
        if (north && east) {
            return NORTH_EAST;
        }
        if (south && east) {
            return SOUTH_EAST;
        }
        if (south && west) {
            return SOUTH_WEST;
        }
        throw new IllegalArgumentException(a + " and " + b + " do not make a corner");
    }

    /**
     * This cut (written for a pattern facing north) for a structure whose controller faces {@code facing}: the
     * pattern's north is the controller's front and its west the controller's left, as in MultiblockDefinition.
     */
    public PartCut rotated(Direction facing) {
        if (this == NONE || this == DECK || this == MAST) {
            return this;
        }
        if (isCorner()) {
            boolean north = this == NORTH_WEST || this == NORTH_EAST;
            boolean west = this == NORTH_WEST || this == SOUTH_WEST;
            return of(north ? facing : facing.getOpposite(), west ? facing.getCounterClockWise() : facing.getClockWise());
        }
        int turns = (turnOf(this) + facing.get2DDataValue() + 2) % 4;     // NORTH's 2D value is 2: no turn
        return valueOf(kind(this) + "_" + TURNS[turns].getSerializedName().toUpperCase(java.util.Locale.ROOT));
    }

    /** "STEPS" for STEPS_EAST. */
    private static String kind(PartCut cut) {
        String n = cut.name();
        return n.substring(0, n.lastIndexOf('_'));
    }

    /** How many quarter turns clockwise from north (0 for _NORTH, 1 for _EAST...). */
    private static int turnOf(PartCut cut) {
        String n = cut.name();
        Direction d = Direction.byName(n.substring(n.lastIndexOf('_') + 1).toLowerCase(java.util.Locale.ROOT));
        return (d.get2DDataValue() + 2) % 4;
    }

    /** Slices across the block, each running from the slope to the far side. */
    private static VoxelShape build(PartCut cut) {
        boolean north = cut == NORTH_WEST || cut == NORTH_EAST;
        boolean west = cut == NORTH_WEST || cut == SOUTH_WEST;
        VoxelShape shape = Shapes.empty();
        for (int v = 0; v < 16; v += STEP) {
            // Distance into the block from the cut corner's z side, at the middle of the slice.
            double keepFrom = Math.max(0, Math.min(16, LEG - (v + STEP / 2.0)));
            double z0 = north ? v : 16 - v - STEP;
            double x0 = west ? keepFrom : 0;
            double x1 = west ? 16 : 16 - keepFrom;
            if (x1 > x0) {
                shape = Shapes.or(shape, Block.box(x0, 0, z0, x1, 16, z0 + STEP));
            }
        }
        return shape.optimize();
    }

    /** A walkable shape: its boxes as written for north (pixels: x0, y0, z0, x1, y1, z1), turned to its direction. */
    private static VoxelShape walkable(PartCut cut) {
        int turns = (cut == DECK || cut == MAST) ? 0 : turnOf(cut);
        VoxelShape shape = Shapes.empty();
        for (double[] b : boxes(cut == DECK || cut == MAST ? cut.name() : kind(cut))) {
            double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
            for (int t = 0; t < turns; t++) {                       // a quarter turn clockwise, seen from above
                double nx0 = 16 - z1, nx1 = 16 - z0;
                z0 = x0;
                z1 = x1;
                x0 = nx0;
                x1 = nx1;
            }
            shape = Shapes.or(shape, Block.box(x0, b[1], z0, x1, b[4], z1));
        }
        return shape.optimize();
    }

    /** Each walkable shape's boxes for a structure facing north (+x east, +z south, as the formed models are drawn). */
    private static List<double[]> boxes(String kind) {
        double d = DECK_TOP, r = RADAR_TOP;
        return switch (kind) {
            case "DECK" -> List.of(new double[] {0, 0, 0, 16, d, 16});
            // The trench (model z 26-38 in a row of z 24-40) between strips of deck, its floor 2 px up.
            case "TRENCH" -> List.of(new double[] {0, 0, 0, 16, d, 2}, new double[] {0, 0, 14, 16, d, 16},
                    new double[] {0, 0, 2, 16, 2, 14});
            // The deflector's wedges (launch.py: 2-5 across the cell, 5-8 from 4 px in, 8-11 from 8 px in).
            case "STEPS" -> List.of(new double[] {0, 0, 0, 16, d, 2}, new double[] {0, 0, 14, 16, d, 16},
                    new double[] {0, 0, 2, 4, 5, 14}, new double[] {4, 0, 2, 8, 8, 14}, new double[] {8, 0, 2, 16, 11, 14});
            // The console (x -5..5, z -7..-2 of the controller's cell) up to the block's top.
            case "CONSOLE" -> List.of(new double[] {0, 0, 0, 16, d, 16}, new double[] {3, d, 1, 13, 16, 6});
            // The port's housing (x -5..5, z 67..72: the back 5 px of its cell).
            case "HOUSING" -> List.of(new double[] {0, 0, 0, 16, d, 16}, new double[] {3, d, 11, 13, 16, 16});
            // The radar's railing: posts and a top rail 1 px wide, 7 px over the deck, 2 px in from the deck's edge.
            case "RAIL" -> List.of(new double[] {0, 0, 0, 16, r, 16}, new double[] {2, r, 0, 3, 13, 16});
            case "CORNER_RAIL" -> List.of(new double[] {0, 0, 0, 16, r, 16}, new double[] {2, r, 0, 3, 13, 14},
                    new double[] {2, r, 13, 16, 13, 14});
            // The mast's four posts and their bracing, 12 px square.
            case "MAST" -> List.of(new double[] {0, 0, 0, 16, r, 16}, new double[] {2, r, 2, 14, 16, 14});
            // The cabinet (x -7..7, z -6..0: the front half of its cell).
            case "CABINET" -> List.of(new double[] {0, 0, 0, 16, r, 16}, new double[] {1, r, 2, 15, 16, 8});
            // The power box (x -5..5, z 31..36) and the back railing behind it.
            case "POWER_BOX" -> List.of(new double[] {0, 0, 0, 16, r, 16}, new double[] {3, r, 7, 13, 15, 12},
                    new double[] {0, r, 13, 16, 13, 14});
            default -> throw new IllegalStateException("no shape for " + kind);
        };
    }
}
