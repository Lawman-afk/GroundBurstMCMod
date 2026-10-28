package dev.groundburst.content.radiation;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * How blocks stop radiation (DESIGN.md §21.8). Each block has a half-value layer, the thickness of it that stops half
 * of what passes through: lead {@link #LEAD}, dense blocks (obsidian, concrete, metal blocks; the
 * {@code groundburst:radiation_shielding/dense} tag) {@link #DENSE}, any other full solid block {@link #SOLID}, liquids
 * {@link #LIQUID}, wood {@link #WOOD}, glass {@link #GLASS}; anything that is not a full block stops nothing. Only
 * loaded blocks are read (an unloaded one counts as open air).
 */
public final class Shielding {
    /** Half-value layers, blocks. */
    public static final float LEAD = 0.2f, DENSE = 0.5f, SOLID = 1f, LIQUID = 1.5f, WOOD = 2f, GLASS = 4f;
    /** How far each of the six rays measuring a body's cover reaches, blocks. */
    public static final int RAY = 8;

    private Shielding() {}

    /** One block's half-value layer, blocks (0: it stops nothing). */
    public static float halfValue(BlockState s, BlockGetter level, BlockPos pos) {
        if (s.isAir()) {
            return 0;
        }
        if (s.is(GBRadiation.SHIELD_LEAD)) {
            return LEAD;
        }
        if (s.is(GBRadiation.SHIELD_DENSE)) {
            return DENSE;
        }
        if (s.getBlock() instanceof LiquidBlock) {
            return LIQUID;
        }
        if (s.is(Tags.BlockItems.GLASS_BLOCKS.block())) {
            return GLASS;
        }
        if (s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS)) {
            return WOOD;
        }
        if (s.isCollisionShapeFullBlock(level, pos)) {
            return SOLID;
        }
        return s.getFluidState().isEmpty() ? 0 : LIQUID;          // something waterlogged: its water
    }

    /** The share of radiation getting through {@code length} blocks of {@code s}. */
    public static double through(BlockState s, BlockGetter level, BlockPos pos, double length) {
        float hvl = halfValue(s, level, pos);
        return hvl <= 0 ? 1 : Math.pow(0.5, length / hvl);
    }

    /**
     * The share of radiation from {@code from} that reaches {@code to} through the blocks on the line between, each
     * taking its share by the length of the line inside it (a voxel walk). {@code skip} (the emitting block) stops none.
     */
    public static double line(Level level, Vec3 from, Vec3 to, @Nullable BlockPos skip) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6) {
            return 1;
        }
        int x = Mth.floor(from.x), y = Mth.floor(from.y), z = Mth.floor(from.z);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sy = dy > 0 ? 1 : dy < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        // Fractions of the line (0 to 1) at which it next crosses a block face on each axis, and between crossings.
        double nextX = sx == 0 ? Double.MAX_VALUE : (sx > 0 ? x + 1 - from.x : from.x - x) / Math.abs(dx);
        double nextY = sy == 0 ? Double.MAX_VALUE : (sy > 0 ? y + 1 - from.y : from.y - y) / Math.abs(dy);
        double nextZ = sz == 0 ? Double.MAX_VALUE : (sz > 0 ? z + 1 - from.z : from.z - z) / Math.abs(dz);
        double stepX = sx == 0 ? Double.MAX_VALUE : 1 / Math.abs(dx);
        double stepY = sy == 0 ? Double.MAX_VALUE : 1 / Math.abs(dy);
        double stepZ = sz == 0 ? Double.MAX_VALUE : 1 / Math.abs(dz);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        double t = 0, share = 1;
        for (int guard = 0; guard < 256; guard++) {
            double next = Math.min(1, Math.min(nextX, Math.min(nextY, nextZ)));
            at.set(x, y, z);
            if ((skip == null || !skip.equals(at)) && level.isLoaded(at)) {
                share *= through(level.getBlockState(at), level, at, (next - t) * length);
                if (share < 1e-4) {
                    return 0;
                }
            }
            if (next >= 1) {
                break;
            }
            t = next;
            if (nextX <= nextY && nextX <= nextZ) {
                x += sx;
                nextX += stepX;
            } else if (nextY <= nextZ) {
                y += sy;
                nextY += stepY;
            } else {
                z += sz;
                nextZ += stepZ;
            }
        }
        return share;
    }

    /**
     * How much of the radiation all round reaches {@code eye}: the mean through six rays of {@link #RAY} blocks along
     * the axes, scaled so a body standing on open ground (whose ray down the ground stops) reads 1. A one-block stone
     * hut halves it; a bunker under the ground stops it.
     */
    public static double ambient(Level level, Vec3 eye) {
        return ambient(level, eye, RAY);
    }

    /** {@link #ambient(Level, Vec3)} with rays of {@code ray} blocks (GameTests keep them inside their plot). */
    public static double ambient(Level level, Vec3 eye, int ray) {
        BlockPos start = BlockPos.containing(eye);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        double sum = 0;
        for (Direction d : Direction.values()) {
            double share = 1;
            at.set(start);
            for (int i = 0; i < ray && share > 1e-3; i++) {
                at.move(d);
                if (!level.isLoaded(at)) {
                    break;
                }
                share *= through(level.getBlockState(at), level, at, 1);
            }
            sum += share;
        }
        return Math.min(1, sum / 5);
    }
}
