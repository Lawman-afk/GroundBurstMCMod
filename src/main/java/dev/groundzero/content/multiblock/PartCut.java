package dev.groundzero.content.multiblock;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Which corner of a formed part block is cut away on a 45-degree slope, so the block's outline and collision follow a
 * structure's sloped edge (the Particle Accelerator's octagonal corners). Only formed parts are cut.
 */
public enum PartCut implements StringRepresentable {
    NONE, NORTH_WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST;

    /**
     * Legs of the cut-away triangle in pixels. A ring one block wide whose 45-degree side runs through a staircase of
     * cells has its edges 1.5 - √½ blocks in from the stair corners (half the ring's width, measured across the slope).
     */
    public static final double LEG = 16 * (1.5 - Math.sqrt(0.5));
    /** The slope is built from slices this many pixels thick. */
    private static final int STEP = 2;
    private static final Map<PartCut, VoxelShape> SHAPES = new EnumMap<>(PartCut.class);

    static {
        for (PartCut cut : values()) {
            SHAPES.put(cut, cut == NONE ? Shapes.block() : build(cut));
        }
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The block shape with this corner cut away. */
    public VoxelShape shape() {
        return SHAPES.get(this);
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
     * This corner (written for a pattern facing north) for a structure whose controller faces {@code facing}: the
     * pattern's north is the controller's front and its west the controller's left, as in MultiblockDefinition.
     */
    public PartCut rotated(Direction facing) {
        if (this == NONE) {
            return NONE;
        }
        boolean north = this == NORTH_WEST || this == NORTH_EAST;
        boolean west = this == NORTH_WEST || this == SOUTH_WEST;
        return of(north ? facing : facing.getOpposite(), west ? facing.getCounterClockWise() : facing.getClockWise());
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
}
