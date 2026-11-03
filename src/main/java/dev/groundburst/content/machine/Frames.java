package dev.groundburst.content.machine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Finds the {@link Frame} a block stands in (DESIGN.md §17.16). Ground Burst knows only the world; another mod's support
 * (the 1.21.1 build's Sable support) adds a {@link Finder} for the blocks it moves. A carried block entity has its own
 * ({@link MachineBlockEntity#frame()}).
 */
public final class Frames {
    /** Something that knows which blocks stand in a frame of their own. */
    @FunctionalInterface
    public interface Finder {
        /** The frame the block at {@code pos} stands in, or null if it stands in the world. */
        @Nullable Frame find(Level level, BlockPos pos);
    }

    private static final List<Finder> FINDERS = new CopyOnWriteArrayList<>();

    private Frames() {}

    public static void addFinder(Finder finder) {
        FINDERS.add(finder);
    }

    /** The frame the block at {@code pos} stands in (the world's but for blocks another mod moves). */
    public static Frame at(@Nullable Level level, BlockPos pos) {
        if (level == null || FINDERS.isEmpty()) {
            return Frame.WORLD;
        }
        for (Finder finder : FINDERS) {
            Frame frame = finder.find(level, pos);
            if (frame != null) {
                return frame;
            }
        }
        return Frame.WORLD;
    }

    /** A point in the world, for one that may be in a block's frame (a point of a ship's plot: where it is now). */
    public static Vec3 toWorld(@Nullable Level level, Vec3 pos) {
        return FINDERS.isEmpty() ? pos : at(level, BlockPos.containing(pos)).toWorld(pos);
    }

    /** The block in the world a block of a frame is over. */
    public static BlockPos toWorld(@Nullable Level level, BlockPos pos) {
        return FINDERS.isEmpty() ? pos : at(level, pos).toWorld(pos);
    }
}
