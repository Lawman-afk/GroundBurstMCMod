package dev.groundzero.content.multiblock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A multiblock pattern (DESIGN.md §23). Layers run bottom-up. Within a layer, written for a controller facing north,
 * row 0 is the northmost (front) row and column 0 the westmost column. Other facings rotate the pattern about the
 * controller the same way GeckoLib rotates the formed model, so pattern and model always agree.
 *
 * @param layers  layers[y][row] is a string of legend characters, one per column; '.' = must be air, ' ' = anything
 * @param legend  what each character accepts, plus the block shown for it in previews and placed by the spawner
 * @param cuts    pattern cells (column, row; every layer) whose formed blocks have a corner cut on a slope, with the
 *                corner as written for a controller facing north
 */
public record MultiblockDefinition(Identifier id, String[][] layers, Map<Character, Entry> legend, Map<Long, PartCut> cuts) {
    public MultiblockDefinition(Identifier id, String[][] layers, Map<Character, Entry> legend) {
        this(id, layers, legend, Map.of());
    }

    /** Key for {@link #cuts}. */
    public static long cutKey(int col, int row) {
        return ((long) col << 32) | (row & 0xFFFFFFFFL);
    }

    /** The controller's character in every pattern. */
    public static final char CONTROLLER = 'X';

    /** A legend entry: which blocks count, and which one to show or place. */
    public record Entry(Predicate<BlockState> accepts, Supplier<BlockState> preview) {
        public static Entry of(Supplier<? extends Block> block) {
            return new Entry(s -> s.is(block.get()), () -> block.get().defaultBlockState());
        }
    }

    /** One expected block: where, which legend character, which layer (0 = bottom). */
    public record Cell(BlockPos pos, char key, int layer, int col, int row) {}

    /** A cell whose block is wrong (missing or a different block). */
    public record Mismatch(BlockPos pos, char key, boolean empty, int layer) {}

    /** The controller's (column, layer, row) inside the pattern. */
    private int[] controllerCell() {
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                int x = layers[y][z].indexOf(CONTROLLER);
                if (x >= 0) {
                    return new int[] {x, y, z};
                }
            }
        }
        throw new IllegalStateException("pattern " + id + " has no controller");
    }

    /** World position of a pattern cell for a controller at {@code controller} facing {@code facing}. */
    public BlockPos cellPos(BlockPos controller, Direction facing, int col, int layer, int row) {
        int[] c = controllerCell();
        int dx = col - c[0], dy = layer - c[1], dz = row - c[2];
        Direction right = facing.getClockWise();          // +column (east when facing north)
        Direction back = facing.getOpposite();            // +row (south when facing north)
        return controller.relative(right, dx).relative(back, dz).above(dy);
    }

    /** Every non-wildcard cell, controller included, in placement order (bottom layer first). */
    public List<Cell> cells(BlockPos controller, Direction facing) {
        List<Cell> out = new ArrayList<>();
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                String row = layers[y][z];
                for (int x = 0; x < row.length(); x++) {
                    char key = row.charAt(x);
                    if (key != ' ') {
                        out.add(new Cell(cellPos(controller, facing, x, y, z), key, y, x, z));
                    }
                }
            }
        }
        return out;
    }

    /** Parts only (no controller, no required-air cells). */
    public List<BlockPos> partPositions(BlockPos controller, Direction facing) {
        return cells(controller, facing).stream().filter(c -> c.key() != CONTROLLER && c.key() != '.').map(Cell::pos).toList();
    }

    /** The corner cut of a cell's formed block for a controller facing {@code facing}. */
    public PartCut cutFor(Cell cell, Direction facing) {
        return this.cuts.getOrDefault(cutKey(cell.col(), cell.row()), PartCut.NONE).rotated(facing);
    }

    /** Wrong or missing blocks; empty means the structure is complete. */
    public List<Mismatch> check(Level level, BlockPos controller, Direction facing) {
        List<Mismatch> out = new ArrayList<>();
        for (Cell cell : cells(controller, facing)) {
            if (cell.key() == CONTROLLER) {
                continue;
            }
            BlockState state = level.getBlockState(cell.pos());
            boolean ok = cell.key() == '.' ? state.isAir() : legend.get(cell.key()).accepts().test(state);
            if (!ok) {
                out.add(new Mismatch(cell.pos(), cell.key(), state.isAir(), cell.layer()));
            }
        }
        return out;
    }

    /** Whether a position belongs to the structure (any cell, controller included). */
    public boolean contains(BlockPos controller, Direction facing, BlockPos pos) {
        return cells(controller, facing).stream().anyMatch(c -> c.pos().equals(pos));
    }

    /** Number of layers, for layer-by-layer previews. */
    public int layerCount() {
        return layers.length;
    }

    /** Pattern size (columns, layers, rows), for previews and the Field Manual. */
    public int[] size() {
        int cols = 0, rows = 0;
        for (String[] layer : layers) {
            rows = Math.max(rows, layer.length);
            for (String row : layer) {
                cols = Math.max(cols, row.length());
            }
        }
        return new int[] {cols, layers.length, rows};
    }

    /** Legend copy in insertion order (for books and tooltips). */
    public Map<Character, Entry> legendOrdered() {
        return new LinkedHashMap<>(legend);
    }
}
