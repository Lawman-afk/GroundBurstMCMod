package dev.groundburst.content.machine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockDefinition.Mismatch;
import dev.groundburst.registry.GBLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Fission Reactor's box (DESIGN.md §7.2, §23.3): a hollow box from 3x3x3 to 6x6x6 blocks outside. Its corners and
 * edges are Lead-Lined Casing; its walls (the faces) casing or Reactor Glass, with exactly one Item Port, one Fluid Port
 * and one Energy Port among them; the controller stands in a wall or in a bottom edge. Inside, every Fuel Channel holds
 * one fuel rod (up to 64, in a 4x4x4 core); the rest of the core is left empty, for the coolant.
 *
 * <p>Positions are worked out in the controller's frame, as patterns are: column c to the pattern's right
 * ({@code facing.getClockWise()}), layer l up, row r back into the reactor ({@code facing.getOpposite()}). The
 * controller is (0, 0, 0), in the front wall (row 0). The box spans columns {@code col0} to {@link #col1()}, layers
 * {@code layer0} to {@link #layer1()} and rows 0 to {@code depth - 1}.
 *
 * <p>The GUI shows the core a layer at a time as a 4x4 map seen from above, the controller's wall at the bottom and
 * the left as you face the controller on the left; a smaller core sits in the middle of the map, from its bottom
 * layer up. Map cell k of layer y is fuel slot {@code 16 y + k} ({@link #slotOf}).
 *
 * @param channels which cells of the 4x4x4 map are fuel channels, one bit each (bit = slot)
 */
public record FissionCore(BlockPos controller, Direction facing, int col0, int layer0, int width, int height, int depth, long channels) {
    public static final int MIN = 3, MAX = 6, GRID = 4, LAYER = GRID * GRID, CELLS = LAYER * GRID;

    public static final String TOO_BIG = GBLang.add(GroundBurst.MODID + ".reactor.too_big",
            "Reactor too big: %s x %s x %s outside, 6 x 6 x 6 at most. Is another reactor block touching it?");
    public static final String NO_CHANNELS = GBLang.add(GroundBurst.MODID + ".reactor.no_channels",
            "Reactor incomplete: put at least one Fuel Channel in the core.");
    public static final String CORNER = GBLang.add(GroundBurst.MODID + ".reactor.corner",
            "The controller must be in a wall or a bottom edge of the reactor.");
    public static final String NEEDS_PORT = GBLang.add(GroundBurst.MODID + ".reactor.needs_port",
            "Reactor incomplete: it needs one %s in a wall.");

    /** What a cell of the box is. */
    public enum Kind { CORE, FACE, EDGE, CORNER }

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

    public int coreVolume() {
        return coreWidth() * coreHeight() * coreDepth();
    }

    public BlockPos pos(int c, int l, int r) {
        return pos(this.controller, this.facing, c, l, r);
    }

    static BlockPos pos(BlockPos controller, Direction facing, int c, int l, int r) {
        return controller.relative(facing.getClockWise(), c).above(l).relative(facing.getOpposite(), r);
    }

    /** {c, l, r} of a world position in the controller's frame. */
    public static int[] local(BlockPos controller, Direction facing, BlockPos p) {
        int dx = p.getX() - controller.getX(), dz = p.getZ() - controller.getZ();
        Direction right = facing.getClockWise(), back = facing.getOpposite();
        return new int[] {dx * right.getStepX() + dz * right.getStepZ(), p.getY() - controller.getY(), dx * back.getStepX() + dz * back.getStepZ()};
    }

    public int[] local(BlockPos p) {
        return local(this.controller, this.facing, p);
    }

    public boolean contains(BlockPos p) {
        int[] q = local(p);
        return q[0] >= this.col0 && q[0] <= col1() && q[1] >= this.layer0 && q[1] <= layer1() && q[2] >= 0 && q[2] < this.depth;
    }

    /** Whether a reactor this controller could make might reach {@code p} (any size, before it is built). */
    static boolean inReach(BlockPos controller, Direction facing, BlockPos p) {
        int[] q = local(controller, facing, p);
        return Math.abs(q[0]) < MAX && Math.abs(q[1]) < MAX && q[2] >= 0 && q[2] < MAX;
    }

    /** How many of the box's sides a cell lies on: 0 in the core, 1 a wall, 2 an edge, 3 a corner. */
    public int sides(int c, int l, int r) {
        return (c == this.col0 || c == col1() ? 1 : 0) + (l == this.layer0 || l == layer1() ? 1 : 0) + (r == 0 || r == this.depth - 1 ? 1 : 0);
    }

    public Kind kind(int c, int l, int r) {
        return Kind.values()[sides(c, l, r)];
    }

    /** The world directions a shell cell faces out of the box (up/down first, then the horizontal ones). */
    public List<Direction> outward(int c, int l, int r) {
        List<Direction> out = new ArrayList<>(3);
        if (l == this.layer0) {
            out.add(Direction.DOWN);
        } else if (l == layer1()) {
            out.add(Direction.UP);
        }
        if (r == 0) {
            out.add(this.facing);
        } else if (r == this.depth - 1) {
            out.add(this.facing.getOpposite());
        }
        if (c == this.col0) {
            out.add(this.facing.getCounterClockWise());
        } else if (c == col1()) {
            out.add(this.facing.getClockWise());
        }
        return out;
    }

    private int offsetX() {
        return (GRID - coreWidth()) / 2;
    }

    private int offsetZ() {
        return (GRID - coreDepth()) / 2;
    }

    /** The fuel slot of the core cell at (c, l, r). */
    public int slotOf(int c, int l, int r) {
        int u = col1() - 1 - c, v = this.depth - 2 - r, y = l - this.layer0 - 1;
        return y * LAYER + (offsetZ() + v) * GRID + offsetX() + u;
    }

    /** Whether fuel slot {@code slot} is a cell of the core (a channel or coolant). */
    public boolean inCore(int slot) {
        int y = slot / LAYER, cell = slot % LAYER;
        int u = cell % GRID - offsetX(), v = cell / GRID - offsetZ();
        return y < coreHeight() && u >= 0 && u < coreWidth() && v >= 0 && v < coreDepth();
    }

    /** The world position of fuel slot {@code slot}'s core cell (null outside the core). */
    public @Nullable BlockPos cellOf(int slot) {
        if (!inCore(slot)) {
            return null;
        }
        int y = slot / LAYER, cell = slot % LAYER;
        int c = col1() - 1 - (cell % GRID - offsetX()), r = this.depth - 2 - (cell / GRID - offsetZ());
        return pos(c, this.layer0 + 1 + y, r);
    }

    public boolean channel(int slot) {
        return (this.channels >>> slot & 1L) != 0;
    }

    public int channelCount() {
        return Long.bitCount(this.channels);
    }

    /** Whether any cell of core column (c, r) is a channel (the head carries a rod drive over it). */
    public boolean columnHasChannel(int c, int r) {
        for (int l = this.layer0 + 1; l < layer1(); l++) {
            if (channel(slotOf(c, l, r))) {
                return true;
            }
        }
        return false;
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
                    if (sides(c, l, r) > 0) {
                        out.add(pos(c, l, r));
                    }
                }
            }
        }
        return out;
    }

    /** The middle of the box, in world coordinates. */
    public Vec3 centre() {
        Vec3 a = Vec3.atCenterOf(pos(this.col0, this.layer0, 0)), b = Vec3.atCenterOf(pos(col1(), layer1(), this.depth - 1));
        return a.add(b).scale(0.5);
    }

    /** The world box the reactor fills (for rendering and culling). */
    public AABB bounds() {
        BlockPos a = pos(this.col0, this.layer0, 0), b = pos(col1(), layer1(), this.depth - 1);
        return new AABB(Vec3.atLowerCornerOf(a), Vec3.atLowerCornerOf(a).add(1, 1, 1))
                .minmax(new AABB(Vec3.atLowerCornerOf(b), Vec3.atLowerCornerOf(b).add(1, 1, 1)));
    }

    // --- what goes where ---------------------------------------------------------------------------------------------------
    static boolean casing(BlockState s) {
        return s.is(GBMultiblocks.LEAD_LINED_CASING.block().get());
    }

    public static boolean glass(BlockState s) {
        return s.is(GBMultiblocks.REACTOR_GLASS.block().get());
    }

    static boolean channel(BlockState s) {
        return s.is(GBMultiblocks.FUEL_CHANNEL.block().get());
    }

    /** The port a block is: 0 Item, 1 Fluid, 2 Energy, or -1. */
    public static int port(BlockState s) {
        return s.is(GBMultiblocks.ITEM_PORT.block().get()) ? 0 : s.is(GBMultiblocks.FLUID_PORT.block().get()) ? 1
                : s.is(GBMultiblocks.ENERGY_PORT.block().get()) ? 2 : -1;
    }

    static Block portBlock(int kind) {
        return kind == 0 ? GBMultiblocks.ITEM_PORT.block().get() : kind == 1 ? GBMultiblocks.FLUID_PORT.block().get()
                : GBMultiblocks.ENERGY_PORT.block().get();
    }

    static String portName(int kind) {
        return portBlock(kind).getDescriptionId();
    }

    /** Anything a reactor's walls may be made of (another controller too, so a wrong one is found where it stands). */
    private static boolean wall(BlockState s) {
        return casing(s) || glass(s) || port(s) >= 0 || s.is(GBMachines.FISSION_REACTOR.block().get());
    }

    /** Wall blocks in a row from {@code start} (not counted) along {@code dir}, up to one more than a reactor can be. */
    private static int walk(Level level, BlockPos start, Direction dir) {
        int n = 0;
        while (n < MAX && wall(level.getBlockState(start.relative(dir, n + 1)))) {
            n++;
        }
        return n;
    }

    // --- checking a build ----------------------------------------------------------------------------------------------------
    /**
     * What a check found: the reactor if it is complete (else null), the box it was checked as, what is wrong there, and
     * anything else that stops it (too big, a port missing, an empty core). {@code ports} holds the Item, Fluid and Energy
     * Ports' places (null where there is none).
     */
    public record Scan(@Nullable FissionCore core, FissionCore box, List<Mismatch> wrong, @Nullable Component problem, BlockPos[] ports) {}

    /**
     * Checks the reactor round a controller: finds the box from the walls built (along the controller's row and column,
     * then back from the front corners), then every block of it.
     */
    public static Scan scan(Level level, BlockPos controller, Direction facing) {
        Direction right = facing.getClockWise(), back = facing.getOpposite();
        int left = walk(level, controller, right.getOpposite()), rightN = walk(level, controller, right);
        int down = walk(level, controller, Direction.DOWN), up = walk(level, controller, Direction.UP);
        int col0 = -left, width = left + rightN + 1, layer0 = -down, height = down + up + 1;
        int col1 = col0 + width - 1, layer1 = layer0 + height - 1;
        int deep = walk(level, controller, back);
        for (int[] corner : new int[][] {{col0, layer0}, {col1, layer0}, {col0, layer1}, {col1, layer1}}) {
            BlockPos at = pos(controller, facing, corner[0], corner[1], 0);
            if (wall(level.getBlockState(at))) {
                deep = Math.max(deep, walk(level, at, back));
            }
        }
        FissionCore box = new FissionCore(controller, facing, col0, layer0, width, height, deep + 1, 0);
        if (width > MAX || height > MAX || box.depth > MAX) {
            return new Scan(null, box, List.of(), Component.translatable(TOO_BIG, width, height, box.depth), new BlockPos[3]);
        }
        if (width < MIN || height < MIN || box.depth < MIN) {
            return new Scan(null, box, List.of(), null, new BlockPos[3]);     // not a box yet
        }
        return check(level, box);
    }

    /** Checks every block of a box against the rules (a scan's box, or a planned one). */
    static Scan check(Level level, FissionCore box) {
        List<Mismatch> wrong = new ArrayList<>();
        Component problem = null;
        long channels = 0;
        BlockPos[] ports = new BlockPos[3];
        for (int l = box.layer0; l <= box.layer1(); l++) {
            for (int r = 0; r < box.depth; r++) {
                for (int c = box.col0; c <= box.col1(); c++) {
                    BlockPos at = box.pos(c, l, r);
                    BlockState s = level.getBlockState(at);
                    Kind kind = box.kind(c, l, r);
                    int layer = l - box.layer0;
                    if (kind == Kind.CORE) {
                        if (channel(s)) {
                            channels |= 1L << box.slotOf(c, l, r);
                        } else if (!s.isAir()) {
                            wrong.add(new Mismatch(at, 'C', false, layer));
                        }
                        continue;
                    }
                    if (at.equals(box.controller)) {
                        if (kind == Kind.CORNER || (kind == Kind.EDGE && l != box.layer0)) {
                            problem = Component.translatable(CORNER);
                        }
                        continue;
                    }
                    int p = port(s);
                    boolean ok;
                    if (kind == Kind.FACE) {
                        ok = casing(s) || glass(s) || p >= 0 && ports[p] == null;
                        if (p >= 0 && ports[p] == null) {
                            ports[p] = at;
                        }
                    } else {
                        ok = casing(s);
                    }
                    if (!ok) {
                        wrong.add(new Mismatch(at, 'L', s.isAir(), layer));
                    }
                }
            }
        }
        if (problem == null && wrong.isEmpty()) {
            for (int p = 0; p < 3 && problem == null; p++) {
                if (ports[p] == null) {
                    problem = Component.translatable(NEEDS_PORT, Component.translatable(portName(p)));
                }
            }
        }
        if (problem == null && wrong.isEmpty() && channels == 0) {
            problem = Component.translatable(NO_CHANNELS);
        }
        FissionCore checked = new FissionCore(box.controller, box.facing, box.col0, box.layer0, box.width, box.height, box.depth, channels);
        return new Scan(problem == null && wrong.isEmpty() ? checked : null, checked, wrong, problem, ports);
    }

    // --- the plan the ghost preview shows -------------------------------------------------------------------------------------
    /**
     * The reactor a controller plans to be (the ghost preview's): {@code w} x {@code h} x {@code d} outside, the
     * controller in the middle of the bottom edge of its front wall (left of the middle when the width is even).
     */
    public static FissionCore plan(BlockPos controller, Direction facing, int w, int h, int d) {
        return new FissionCore(controller, facing, -((w - 1) / 2), 0, w, h, d, 0);
    }

    /**
     * The block the plan suggests for a cell: lead-lined casing on the corners, edges, roof and floor, reactor glass
     * in the side walls, Fuel Channels filling the core, and the ports where {@link #suggestedPort} puts them.
     */
    public BlockState suggested(int c, int l, int r) {
        Kind kind = kind(c, l, r);
        if (kind == Kind.CORE) {
            return GBMultiblocks.FUEL_CHANNEL.block().get().defaultBlockState();
        }
        for (int p = 0; p < 3; p++) {
            int[] at = suggestedPort(p);
            if (at[0] == c && at[1] == l && at[2] == r) {
                return portBlock(p).defaultBlockState();
            }
        }
        if (kind == Kind.FACE && l != this.layer0 && l != layer1()) {
            return GBMultiblocks.REACTOR_GLASS.block().get().defaultBlockState();
        }
        return GBMultiblocks.LEAD_LINED_CASING.block().get().defaultBlockState();
    }

    /**
     * Where the plan puts each port ({c, l, r}): the Item Port in the roof over the front of the core, the Energy Port
     * low in the middle of the back wall, the Fluid Port low at the back of the left wall (as the pattern has left).
     */
    public int[] suggestedPort(int kind) {
        int midC = this.col0 + this.width / 2;
        return switch (kind) {
            case 0 -> new int[] {midC, layer1(), 1};
            case 1 -> new int[] {this.col0, this.layer0 + 1, this.depth - 2};
            default -> new int[] {this.col0 + (this.width - 1) / 2, this.layer0 + 1, this.depth - 1};
        };
    }

    /**
     * What the ghost preview shows for this plan: a ghost of the suggested block on every empty cell, a red outline round
     * every block that cannot go where it stands. Valid blocks other than the suggestion (casing for glass, a port in
     * another wall, coolant left in the core) are not marked; a port's suggested place takes casing or glass once that
     * port stands elsewhere.
     */
    public List<Mismatch> planMismatches(Level level) {
        List<Mismatch> out = new ArrayList<>();
        Scan s = check(level, this);
        java.util.Set<BlockPos> bad = new java.util.HashSet<>();
        for (Mismatch m : s.wrong()) {
            if (!m.empty()) {
                out.add(m);
                bad.add(m.pos());
            }
        }
        for (int l = this.layer0; l <= layer1(); l++) {
            for (int r = 0; r < this.depth; r++) {
                for (int c = this.col0; c <= col1(); c++) {
                    BlockPos at = pos(c, l, r);
                    if (at.equals(this.controller) || bad.contains(at) || !level.getBlockState(at).isAir()) {
                        continue;
                    }
                    BlockState want = suggested(c, l, r);
                    int p = port(want);
                    if (p >= 0 && s.ports()[p] != null) {
                        want = kind(c, l, r) == Kind.FACE && l != this.layer0 && l != layer1()
                                ? GBMultiblocks.REACTOR_GLASS.block().get().defaultBlockState()
                                : GBMultiblocks.LEAD_LINED_CASING.block().get().defaultBlockState();
                    }
                    out.add(new Mismatch(at, key(want), true, l - this.layer0));
                }
            }
        }
        return out;
    }

    /** The example pattern's legend character for a block (what a ghost shows for it). */
    static char key(BlockState s) {
        if (channel(s)) {
            return 'C';
        }
        if (glass(s)) {
            return 'G';
        }
        int p = port(s);
        return p == 0 ? 'U' : p == 1 ? 'W' : p == 2 ? 'E' : 'L';
    }
}
