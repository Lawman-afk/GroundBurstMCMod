package dev.groundzero.content.machine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockDefinition.Mismatch;
import dev.groundzero.registry.GZLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A Fission Reactor's box (DESIGN.md §7, §23.3): a hollow box from 3x3x3 to 6x6x6 blocks outside. Its corners are
 * Lead-Lined Casing; its edges casing or ports; its faces casing, Reactor Glass or ports; the controller stands in a
 * face or an edge. Inside, Fuel Channels stand in columns the full height of the core, one fuel rod a column (up to
 * 16, in a 4x4 core); the rest of the core is left empty, for the coolant.
 *
 * <p>Positions are worked out in the controller's frame, as patterns are: column c to the pattern's right
 * ({@code facing.getClockWise()}), layer l up, row r back into the reactor ({@code facing.getOpposite()}). The
 * controller is (0, 0, 0), in the front wall (row 0). The box spans columns {@code col0} to {@link #col1()}, layers
 * {@code layer0} to {@link #layer1()} and rows 0 to {@code depth - 1}.
 *
 * <p>The GUI shows the core as a 4x4 map seen from above, the controller's wall at the bottom and the left as you face
 * the controller on the left; a smaller core sits in the middle of it. Map cell k is fuel slot k ({@link #slotOf}).
 *
 * @param channels which map cells are fuel channels, one bit each
 */
public record FissionCore(BlockPos controller, Direction facing, int col0, int layer0, int width, int height, int depth, int channels) {
    public static final int MIN = 3, MAX = 6, GRID = 4;

    public static final String TOO_BIG = GZLang.add(GroundZero.MODID + ".reactor.too_big",
            "Reactor too big: %s x %s x %s outside, 6 x 6 x 6 at most. Is another reactor block touching it?");
    public static final String NO_CHANNELS = GZLang.add(GroundZero.MODID + ".reactor.no_channels",
            "Reactor incomplete: stand at least one column of Fuel Channels in the core, floor to roof.");
    public static final String CORNER = GZLang.add(GroundZero.MODID + ".reactor.corner",
            "The controller cannot be a corner: put it in a wall or an edge of the reactor.");

    public int col1() {
        return this.col0 + this.width - 1;
    }

    public int layer1() {
        return this.layer0 + this.height - 1;
    }

    /** The core's size (inside the walls): across, up, back. */
    public int coreWidth() {
        return this.width - 2;
    }

    public int coreHeight() {
        return this.height - 2;
    }

    public int coreDepth() {
        return this.depth - 2;
    }

    public BlockPos pos(int c, int l, int r) {
        return pos(this.controller, this.facing, c, l, r);
    }

    static BlockPos pos(BlockPos controller, Direction facing, int c, int l, int r) {
        return controller.relative(facing.getClockWise(), c).above(l).relative(facing.getOpposite(), r);
    }

    /** {c, l, r} of a world position in the controller's frame. */
    static int[] local(BlockPos controller, Direction facing, BlockPos p) {
        int dx = p.getX() - controller.getX(), dz = p.getZ() - controller.getZ();
        Direction right = facing.getClockWise(), back = facing.getOpposite();
        return new int[] {dx * right.getStepX() + dz * right.getStepZ(), p.getY() - controller.getY(), dx * back.getStepX() + dz * back.getStepZ()};
    }

    public boolean contains(BlockPos p) {
        int[] q = local(this.controller, this.facing, p);
        return q[0] >= this.col0 && q[0] <= col1() && q[1] >= this.layer0 && q[1] <= layer1() && q[2] >= 0 && q[2] < this.depth;
    }

    /** Whether a reactor this controller could make might reach {@code p} (any size, before it is built). */
    static boolean inReach(BlockPos controller, Direction facing, BlockPos p) {
        int[] q = local(controller, facing, p);
        return Math.abs(q[0]) < MAX && Math.abs(q[1]) < MAX && q[2] >= 0 && q[2] < MAX;
    }

    /** The map cell (fuel slot) of the core column at (c, r). */
    public int slotOf(int c, int r) {
        int u = col1() - 1 - c, v = this.depth - 2 - r;
        return (offsetZ() + v) * GRID + offsetX() + u;
    }

    private int offsetX() {
        return (GRID - coreWidth()) / 2;
    }

    private int offsetZ() {
        return (GRID - coreDepth()) / 2;
    }

    /** Whether map cell {@code slot} lies inside the core (a channel or a coolant column). */
    public boolean inCore(int slot) {
        int u = slot % GRID - offsetX(), v = slot / GRID - offsetZ();
        return u >= 0 && u < coreWidth() && v >= 0 && v < coreDepth();
    }

    /** The core column under map cell {@code slot}, bottom to top (empty if the cell is outside the core). */
    public List<BlockPos> column(int slot) {
        if (!inCore(slot)) {
            return List.of();
        }
        int c = col1() - 1 - (slot % GRID - offsetX()), r = this.depth - 2 - (slot / GRID - offsetZ());
        List<BlockPos> out = new ArrayList<>(coreHeight());
        for (int l = this.layer0 + 1; l < layer1(); l++) {
            out.add(pos(c, l, r));
        }
        return out;
    }

    public boolean channel(int slot) {
        return (this.channels >> slot & 1) != 0;
    }

    public int channelCount() {
        return Integer.bitCount(this.channels);
    }

    /** Every block of the box, walls and core. */
    public List<BlockPos> cells() {
        List<BlockPos> out = new ArrayList<>(this.width * this.height * this.depth);
        for (int l = this.layer0; l <= layer1(); l++) {
            for (int r = 0; r < this.depth; r++) {
                for (int c = this.col0; c <= col1(); c++) {
                    out.add(pos(c, l, r));
                }
            }
        }
        return out;
    }

    /** The walls only (corners, edges, faces), controller included. */
    public List<BlockPos> shell() {
        List<BlockPos> out = new ArrayList<>();
        for (int l = this.layer0; l <= layer1(); l++) {
            for (int r = 0; r < this.depth; r++) {
                for (int c = this.col0; c <= col1(); c++) {
                    if (bounds(c, l, r) > 0) {
                        out.add(pos(c, l, r));
                    }
                }
            }
        }
        return out;
    }

    /** How many of the box's sides a cell lies on: 0 in the core, 1 a face, 2 an edge, 3 a corner. */
    private int bounds(int c, int l, int r) {
        return (c == this.col0 || c == col1() ? 1 : 0) + (l == this.layer0 || l == layer1() ? 1 : 0) + (r == 0 || r == this.depth - 1 ? 1 : 0);
    }

    /** The middle of the box, in world coordinates. */
    public Vec3 centre() {
        Vec3 a = Vec3.atCenterOf(pos(this.col0, this.layer0, 0)), b = Vec3.atCenterOf(pos(col1(), layer1(), this.depth - 1));
        return a.add(b).scale(0.5);
    }

    /** The world box the reactor fills (for rendering and culling). */
    public net.minecraft.world.phys.AABB bounds() {
        BlockPos a = pos(this.col0, this.layer0, 0), b = pos(col1(), layer1(), this.depth - 1);
        return new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(a), Vec3.atLowerCornerOf(a).add(1, 1, 1))
                .minmax(new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(b), Vec3.atLowerCornerOf(b).add(1, 1, 1)));
    }

    // --- checking a build ----------------------------------------------------------------------------------------------------
    /**
     * What a check found: the reactor if it is complete (else null), the box it was checked as (what the player seems to
     * be building), what is missing or wrong there, and anything else that stops it (too big, an empty core).
     */
    public record Scan(@Nullable FissionCore core, FissionCore box, List<Mismatch> mismatches, @Nullable Component problem) {}

    private static boolean casing(BlockState s) {
        return s.is(GZMultiblocks.LEAD_LINED_CASING.block().get());
    }

    private static boolean port(BlockState s) {
        return s.is(GZMultiblocks.ITEM_PORT.block().get()) || s.is(GZMultiblocks.FLUID_PORT.block().get()) || s.is(GZMultiblocks.ENERGY_PORT.block().get());
    }

    private static boolean glass(BlockState s) {
        return s.is(GZMultiblocks.REACTOR_GLASS.block().get());
    }

    private static boolean channel(BlockState s) {
        return s.is(GZMultiblocks.FUEL_CHANNEL.block().get());
    }

    /** Anything a reactor's walls may be made of (another controller too, so a wrong one is found where it stands). */
    private static boolean wall(BlockState s) {
        return casing(s) || glass(s) || port(s) || s.is(GZMachines.FISSION_REACTOR.block().get());
    }

    /** Wall blocks in a row from {@code start} (not counted) along {@code dir}, up to one more than a reactor can be. */
    private static int walk(Level level, BlockPos start, Direction dir) {
        int n = 0;
        while (n < MAX && wall(level.getBlockState(start.relative(dir, n + 1)))) {
            n++;
        }
        return n;
    }

    /**
     * Checks the reactor round a controller: finds the box from the walls built so far (along the controller's row and
     * column, then back from the front corners), then every block of it. Where nothing is built yet along a side, it
     * assumes the example's size there (four, the controller second from the left as the pattern has it, at the bottom).
     */
    public static Scan scan(Level level, BlockPos controller, Direction facing) {
        Direction right = facing.getClockWise(), back = facing.getOpposite();
        int left = walk(level, controller, right.getOpposite()), rightN = walk(level, controller, right);
        int down = walk(level, controller, Direction.DOWN), up = walk(level, controller, Direction.UP);
        int col0 = -left, width = left + rightN + 1, layer0 = -down, height = down + up + 1;
        if (width < MIN) {
            col0 = left > 0 ? -left : -1;
            width = 4;
        }
        if (height < MIN) {
            layer0 = -down;
            height = 4;
        }
        int col1 = col0 + width - 1, layer1 = layer0 + height - 1;
        int deep = walk(level, controller, back);
        for (int[] corner : new int[][] {{col0, layer0}, {col1, layer0}, {col0, layer1}, {col1, layer1}}) {
            BlockPos at = pos(controller, facing, corner[0], corner[1], 0);
            if (wall(level.getBlockState(at))) {
                deep = Math.max(deep, walk(level, at, back));
            }
        }
        int depth = deep + 1 < MIN ? 4 : deep + 1;
        FissionCore box = new FissionCore(controller, facing, col0, layer0, width, height, depth, 0);
        if (width > MAX || height > MAX || depth > MAX) {
            return new Scan(null, box, List.of(), Component.translatable(TOO_BIG, width, height, depth));
        }
        List<Mismatch> wrong = new ArrayList<>();
        Component problem = null;
        int channels = 0;
        for (int r = 1; r < depth - 1; r++) {
            for (int c = col0 + 1; c < col1; c++) {
                int found = 0;
                List<BlockPos> empty = new ArrayList<>();
                for (int l = layer0 + 1; l < layer1; l++) {
                    BlockPos at = pos(controller, facing, c, l, r);
                    BlockState s = level.getBlockState(at);
                    if (channel(s)) {
                        found++;
                    } else if (s.isAir()) {
                        empty.add(at);
                    } else {
                        wrong.add(new Mismatch(at, 'C', false, l - layer0));
                    }
                }
                if (found == height - 2) {
                    channels |= 1 << box.slotOf(c, r);
                } else if (found > 0) {
                    for (BlockPos at : empty) {           // a channel column must run floor to roof
                        wrong.add(new Mismatch(at, 'C', true, at.getY() - controller.getY() - layer0));
                    }
                }
            }
        }
        for (int l = layer0; l <= layer1; l++) {
            for (int r = 0; r < depth; r++) {
                for (int c = col0; c <= col1; c++) {
                    int sides = box.bounds(c, l, r);
                    if (sides == 0) {
                        continue;
                    }
                    if (c == 0 && l == 0 && r == 0) {
                        if (sides == 3) {
                            problem = Component.translatable(CORNER);
                        }
                        continue;
                    }
                    BlockPos at = pos(controller, facing, c, l, r);
                    BlockState s = level.getBlockState(at);
                    boolean ok = sides == 3 ? casing(s) : sides == 2 ? casing(s) || port(s) : casing(s) || glass(s) || port(s);
                    if (!ok) {
                        wrong.add(new Mismatch(at, 'L', s.isAir(), l - layer0));
                    }
                }
            }
        }
        if (problem == null && wrong.isEmpty() && channels == 0) {
            problem = Component.translatable(NO_CHANNELS);
        }
        FissionCore checked = new FissionCore(controller, facing, col0, layer0, width, height, depth, channels);
        return new Scan(problem == null && wrong.isEmpty() ? checked : null, checked, wrong, problem);
    }
}
