package dev.groundzero.client.render;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.FissionCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * The formed Fission Reactor's sections (DESIGN.md §7.2): the vessel FissionReactorRenderer draws over a reactor of any
 * size while its walls' own blocks hide. Each kind of cell has a block model of its own (tools/assets/fission_formed.py,
 * whose {@code pieces} mirrors {@link #layout}): plinth corners and edges at the foot, corner posts, bolted lead walls,
 * window bays, the head's corners and edges, its plates, skylights over glass in the roof, a control-rod drive over every
 * core column that holds a channel, the ports' housings, and the console where the controller stands.
 *
 * <p>Every section is written for a cell's north side (corners and posts for its north-west); a piece turns it clockwise
 * a quarter turn at a time, seen from above (a blockstate's y rotation), or stands it upside down (a port in the floor).
 */
public final class FissionSections {
    private FissionSections() {}

    public enum Section {
        WALL, POST, CORNER_BOTTOM, EDGE_BOTTOM, BOTTOM, CORNER_TOP, EDGE_TOP, TOP, WINDOW, WINDOW_GLASS, SKYLIGHT, SKYLIGHT_GLASS,
        DRIVE, DRIVE_ROD, PORT_ITEM, PORT_FLUID, PORT_ENERGY, PORT_ITEM_TOP, PORT_FLUID_TOP, PORT_ENERGY_TOP, CONSOLE;

        private final String id = "fission_" + name().toLowerCase(Locale.ROOT);
        private final StandaloneModelKey<BlockStateModelPart> key = new StandaloneModelKey<>(() -> GroundZero.MODID + ":" + this.id);

        public Identifier model() {
            return Identifier.fromNamespaceAndPath(GroundZero.MODID, "block/" + this.id);
        }

        /** See-through panes, drawn with the coolant (sorted with it) rather than with the vessel. */
        public boolean glass() {
            return this == WINDOW_GLASS || this == SKYLIGHT_GLASS;
        }

        public @Nullable BlockStateModelPart part() {
            return Minecraft.getInstance().getModelManager().getStandaloneModel(this.key);
        }
    }

    private static final Section[] WALL_PORTS = {Section.PORT_ITEM, Section.PORT_FLUID, Section.PORT_ENERGY};
    private static final Section[] TOP_PORTS = {Section.PORT_ITEM_TOP, Section.PORT_FLUID_TOP, Section.PORT_ENERGY_TOP};

    /**
     * One section in one cell: the cell's offset from the controller, the quarter turns, upside down or not, and the
     * light it is drawn in (the brightest of the cell and the open cells it faces).
     */
    public record Piece(Section section, int x, int y, int z, int turns, boolean flip, int light) {
        /** Moves a pose (at the controller block) onto this piece; the drive rods sit {@code rodDrop} blocks down. */
        public void place(PoseStack.Pose pose, float rodDrop) {
            pose.translate(this.x, this.y - (this.section == Section.DRIVE_ROD ? rodDrop : 0), this.z);
            if (this.flip) {
                pose.translate(0.5f, 0.5f, 0.5f);
                pose.rotateDegrees(Axis.XP, 180);
                pose.translate(-0.5f, -0.5f, -0.5f);
            }
            if (this.turns != 0) {
                pose.translate(0.5f, 0, 0.5f);
                pose.rotateDegrees(Axis.YP, -90f * this.turns);
                pose.translate(-0.5f, 0, -0.5f);
            }
        }
    }

    public static void register(ModelEvent.RegisterStandalone event) {
        for (Section s : Section.values()) {
            event.register(s.key, SimpleUnbakedStandaloneModel.simpleModelWrapper(s.model()));
        }
    }

    /** The pieces of a formed reactor, from the blocks of its walls as the client sees them, offsets from {@code origin}. */
    public static void layout(FissionCore core, Level level, BlockPos origin, List<Piece> out) {
        for (int l = core.layer0(); l <= core.layer1(); l++) {
            for (int r = 0; r < core.depth(); r++) {
                for (int c = core.col0(); c <= core.col1(); c++) {
                    if (core.sides(c, l, r) > 0) {
                        cell(core, level, origin, c, l, r, out);
                    }
                }
            }
        }
    }

    private static void cell(FissionCore core, Level level, BlockPos origin, int c, int l, int r, List<Piece> out) {
        BlockPos pos = core.pos(c, l, r);
        List<Direction> outward = core.outward(c, l, r);
        Direction vertical = outward.getFirst().getAxis() == Direction.Axis.Y ? outward.getFirst() : null;
        List<Direction> sides = vertical != null ? outward.subList(1, outward.size()) : outward;
        BlockState state = level.getBlockState(pos);
        boolean glass = FissionCore.glass(state);
        int port = FissionCore.port(state);
        int light = LightCoordsUtil.getLightCoords(level, pos);
        for (Direction d : outward) {
            light = LightCoordsUtil.max(light, LightCoordsUtil.getLightCoords(level, pos.relative(d)));
        }
        int x = pos.getX() - origin.getX(), y = pos.getY() - origin.getY(), z = pos.getZ() - origin.getZ();
        if (sides.size() == 2) {
            Section corner = vertical == Direction.DOWN ? Section.CORNER_BOTTOM : vertical == Direction.UP ? Section.CORNER_TOP : Section.POST;
            out.add(new Piece(corner, x, y, z, cornerTurns(sides.get(0), sides.get(1)), false, light));
        } else if (sides.size() == 1) {
            int turns = turns(sides.getFirst());
            if (vertical != null) {
                out.add(new Piece(vertical == Direction.DOWN ? Section.EDGE_BOTTOM : Section.EDGE_TOP, x, y, z, turns, false, light));
            } else if (glass) {
                out.add(new Piece(Section.WINDOW, x, y, z, turns, false, light));
                out.add(new Piece(Section.WINDOW_GLASS, x, y, z, turns, false, light));
            } else {
                out.add(new Piece(Section.WALL, x, y, z, turns, false, light));
                if (port >= 0) {
                    out.add(new Piece(WALL_PORTS[port], x, y, z, turns, false, light));
                }
            }
            if (pos.equals(core.controller())) {
                out.add(new Piece(Section.CONSOLE, x, y, z, turns, false, light));
            }
        } else if (vertical == Direction.UP) {
            if (glass) {
                out.add(new Piece(Section.SKYLIGHT, x, y, z, 0, false, light));
                out.add(new Piece(Section.SKYLIGHT_GLASS, x, y, z, 0, false, light));
            } else {
                out.add(new Piece(Section.TOP, x, y, z, 0, false, light));
                if (port >= 0) {
                    out.add(new Piece(TOP_PORTS[port], x, y, z, 0, false, light));
                } else if (core.columnHasChannel(c, r)) {
                    out.add(new Piece(Section.DRIVE, x, y, z, 0, false, light));
                    out.add(new Piece(Section.DRIVE_ROD, x, y, z, 0, false, light));
                }
            }
        } else {
            out.add(new Piece(Section.BOTTOM, x, y, z, 0, false, light));
            if (port >= 0) {
                out.add(new Piece(TOP_PORTS[port], x, y, z, 0, true, light));       // a port in the floor: its housing hangs under it
            }
        }
    }

    /** Quarter turns clockwise (from above) taking north to {@code d}. */
    static int turns(Direction d) {
        return switch (d) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** Quarter turns taking the north-west corner to the corner between {@code a} and {@code b}. */
    static int cornerTurns(Direction a, Direction b) {
        boolean north = a == Direction.NORTH || b == Direction.NORTH, west = a == Direction.WEST || b == Direction.WEST;
        return north ? (west ? 0 : 1) : (west ? 3 : 2);
    }
}
