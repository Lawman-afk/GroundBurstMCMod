package dev.groundburst.content.fluid;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * How fluid tank blocks join up (DESIGN.md §6.4). Touching tank blocks become one tank when together they fill a box
 * with a square footprint up to {@value #MAX_WIDTH} x {@value #MAX_WIDTH} and up to {@value #MAX_HEIGHT} high, and
 * their contents are the same fluid (or empty). Otherwise a new block stays a tank of its own, so a bigger tank is
 * built block by block and merges the moment the box is complete. Breaking a block splits the tank; whatever is
 * left re-forms as a box if it still is one, keeping its share of the fluid (the broken block's share is lost).
 */
public final class FluidTankStructure {
    public static final int MAX_WIDTH = 3, MAX_HEIGHT = 5;
    private static final int MAX_SEARCH = MAX_WIDTH * MAX_WIDTH * MAX_HEIGHT + 1;

    private FluidTankStructure() {}

    /** Tank blocks connected to {@code start} (not crossing {@code excluded}), up to one more than the largest tank. */
    static Set<BlockPos> component(ServerLevel level, BlockPos start, @Nullable BlockPos excluded) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && seen.size() <= MAX_SEARCH) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!n.equals(excluded) && !seen.contains(n) && level.getBlockState(n).getBlock() instanceof FluidTankBlock) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        return seen;
    }

    /** The box the blocks exactly fill ({min corner, width, height}), or null if they are not a valid tank shape. */
    static @Nullable Object[] box(Set<BlockPos> blocks) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : blocks) {
            x0 = Math.min(x0, p.getX());
            y0 = Math.min(y0, p.getY());
            z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX());
            y1 = Math.max(y1, p.getY());
            z1 = Math.max(z1, p.getZ());
        }
        int w = x1 - x0 + 1, d = z1 - z0 + 1, h = y1 - y0 + 1;
        if (w != d || w > MAX_WIDTH || h > MAX_HEIGHT || blocks.size() != w * d * h) {
            return null;
        }
        return new Object[] {new BlockPos(x0, y0, z0), w, h};
    }

    /** Re-checks the tank blocks around a newly placed block and merges them if they now make a box. */
    public static void reform(ServerLevel level, BlockPos pos) {
        Set<BlockPos> blocks = component(level, pos, null);
        Object[] box = blocks.size() <= MAX_SEARCH - 1 ? box(blocks) : null;
        FluidStack contents = box == null ? null : combinedContents(level, blocks);
        if (box != null && contents != null) {
            form(level, blocks, (BlockPos) box[0], (int) box[1], (int) box[2], contents);
        } else if (level.getBlockEntity(pos) instanceof FluidTankBlockEntity be && be.isController() && be.width() == 1 && be.height() == 1) {
            form(level, Set.of(pos.immutable()), pos.immutable(), 1, 1, be.tank().fluid().copy());
        }
    }

    /** All the fluid held by the tanks among these blocks, or null if they hold different fluids (they can't merge). */
    private static @Nullable FluidStack combinedContents(ServerLevel level, Set<BlockPos> blocks) {
        Map<BlockPos, FluidTankBlockEntity> controllers = new LinkedHashMap<>();
        for (BlockPos p : blocks) {
            if (level.getBlockEntity(p) instanceof FluidTankBlockEntity be) {
                FluidTankBlockEntity c = be.controllerEntity();
                controllers.put(c.getBlockPos(), c);
            }
        }
        FluidStack total = FluidStack.EMPTY;
        for (FluidTankBlockEntity c : controllers.values()) {
            FluidStack f = c.tank().fluid();
            if (f.isEmpty()) {
                continue;
            }
            if (total.isEmpty()) {
                total = f.copy();
            } else if (FluidStack.isSameFluidSameComponents(total, f)) {
                total.grow(f.getAmount());
            } else {
                return null;
            }
        }
        return total;
    }

    /** Makes the blocks one tank: the min corner holds the fluid, every block shows the connected frame. */
    static void form(ServerLevel level, Set<BlockPos> blocks, BlockPos min, int width, int height, FluidStack contents) {
        for (BlockPos p : blocks) {
            if (level.getBlockEntity(p) instanceof FluidTankBlockEntity be) {
                be.join(p.equals(min) ? null : min, width, height, contents);
            }
            BlockState state = level.getBlockState(p);
            BlockState updated = state;
            for (Direction d : Direction.values()) {
                updated = updated.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), blocks.contains(p.relative(d)));
            }
            if (updated != state) {
                level.setBlock(p, updated, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** A block of a tank is being removed: split the rest, sharing out the fluid, and re-form what still makes a box. */
    static void detach(ServerLevel level, BlockPos removed, FluidTankBlockEntity be) {
        FluidTankBlockEntity controller = be.controllerEntity();
        int w = controller.width(), h = controller.height();
        if (w == 1 && h == 1) {
            return;
        }
        BlockPos min = controller.getBlockPos();
        FluidStack total = controller.tank().fluid().copy();
        int blocks = w * w * h;
        Set<BlockPos> rest = new HashSet<>();
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                for (int dz = 0; dz < w; dz++) {
                    BlockPos p = min.offset(dx, dy, dz);
                    if (!p.equals(removed) && level.getBlockEntity(p) instanceof FluidTankBlockEntity) {
                        rest.add(p);
                    }
                }
            }
        }
        // Each remaining block becomes a tank of its own with its share...
        int share = total.isEmpty() ? 0 : total.getAmount() / blocks;
        for (BlockPos p : rest) {
            form(level, Set.of(p), p, 1, 1, share > 0 ? total.copyWithAmount(share) : FluidStack.EMPTY);
        }
        // ...then each connected group that still makes a box merges again.
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos p : rest) {
            if (done.contains(p)) {
                continue;
            }
            Set<BlockPos> group = component(level, p, removed);
            done.addAll(group);
            Object[] box = box(group);
            FluidStack contents = box == null ? null : combinedContents(level, group);
            if (box != null && contents != null && group.size() > 1) {
                form(level, group, (BlockPos) box[0], (int) box[1], (int) box[2], contents);
            }
        }
    }
}
