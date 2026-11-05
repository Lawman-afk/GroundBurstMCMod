package dev.groundburst.content.naval;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The water the naval weapons work in (DESIGN.md §14.6): its surface over a point, and the bottom under one. */
public final class Water {
    private Water() {}

    public static boolean water(Level level, BlockPos at) {
        return level.isLoaded(at) && level.getFluidState(at).is(FluidTags.WATER);
    }

    /** The surface of the water a point is in: the top of the highest water in the column over it; NaN if it is not in water. */
    public static double surface(Level level, Vec3 at) {
        BlockPos.MutableBlockPos p = BlockPos.containing(at).mutable();
        if (!water(level, p)) {
            return Double.NaN;
        }
        for (int i = 0; i < 384 && water(level, p.above()); i++) {
            p.move(Direction.UP);
        }
        return p.getY() + level.getFluidState(p).getHeight(level, p);
    }

    /** The top of the first block with a shape to stand on at or under {@code at}, within {@code reach} blocks down; NaN if none. */
    public static double floor(Level level, Vec3 at, int reach) {
        BlockPos.MutableBlockPos p = BlockPos.containing(at).mutable();
        for (int i = 0; i <= reach; i++) {
            if (!level.isLoaded(p)) {
                return Double.NaN;
            }
            BlockState s = level.getBlockState(p);
            VoxelShape shape = s.getCollisionShape(level, p);
            if (!shape.isEmpty()) {
                double top = p.getY() + shape.max(Direction.Axis.Y);
                if (top <= at.y + 1e-6) {
                    return top;
                }
            }
            p.move(Direction.DOWN);
        }
        return Double.NaN;
    }
}
