package dev.groundburst.content.alarm;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The alarm devices (DESIGN.md §18.6): what each does on the network, how it mounts, its shape and the choices on its
 * settings screen. Shapes are written for a device facing north: its back against the block's south face, its front
 * toward the north (a device standing on the floor: its foot on the block's floor).
 */
public enum AlarmDevice {
    RECEIVER("missile_warning_receiver", Role.SENDER, Mount.WALL, box(3, 2, 10, 13, 13, 16),
            option("radius", "Warning radius", 2, "64 blocks", "128 blocks", "256 blocks", "512 blocks", "1024 blocks"),
            option("output", "Redstone", 0, "Steady 15", "Number incoming", "Rising to impact")),
    PANEL("alarm_panel", Role.SENDER, Mount.WALL, box(2, 2, 12, 14, 14, 16),
            option("raises", "Raises", 1, "Attack", "Alarm")),
    MONITOR("hazard_monitor", Role.SENDER, Mount.WALL, box(3, 2, 11, 13, 14, 16),
            option("threshold", "Radiation over", 0, "10 RAD/h", "50 RAD/h", "200 RAD/h", "1000 RAD/h"),
            option("gas", "Poison gas", 0, "Alarms", "Ignored")),
    SIREN("air_raid_siren", Role.SOUNDER, Mount.FLOOR, box(3, 0, 3, 13, 16, 13),
            option("signal", "Signal", 0, "Attack (wail)", "Alert (steady)")),
    KLAXON("klaxon", Role.SOUNDER, Mount.WALL, box(4, 4, 6, 12, 12, 16),
            option("tone", "Tone", 0, "Klaxon", "Slow whoop", "Evacuate", "Hi-lo", "General alarm")),
    BELL("alarm_bell", Role.SOUNDER, Mount.WALL, box(3, 3, 11, 13, 13, 16)),
    BEACON("warning_beacon", Role.LIGHT, Mount.ANY, box(4, 4, 7, 12, 12, 16),
            option("mode", "Light", 0, "Rotating", "Flashing", "Steady"),
            option("colour", "Colour", 0, "Red", "Amber", "Blue", "Green", "White")),
    LIGHT("emergency_light", Role.LIGHT, Mount.ANY, box(4, 3, 10, 12, 13, 16),
            option("lit", "Lit", 0, "Only in alarm", "Always (white)")),
    DISPLAY("alert_display", Role.DISPLAY, Mount.WALL, box(1, 3, 13, 15, 13, 16)),
    RELAY("alarm_relay", Role.RELAY, Mount.WALL, Shapes.block(),
            option("gives", "Redstone", 0, "In alarm", "When all clear"));

    /** What a device does: raises its channel, sounds, lights, shows, or gives redstone. */
    public enum Role { SENDER, SOUNDER, LIGHT, DISPLAY, RELAY }

    /** How it mounts: on a wall or standing (turned four ways), on a floor (standing), or on any face. */
    public enum Mount { WALL, FLOOR, ANY }

    /** A choice on its screen: its key, label, default and values (English, for the lang file). */
    public record Option(String key, String label, int def, List<String> values) {
        public String labelKey() {
            return GroundBurst.MODID + ".alarm.option." + this.key;
        }

        public String valueKey(int i) {
            return GroundBurst.MODID + ".alarm." + this.key + "." + i;
        }
    }

    public final String id;
    public final Role role;
    public final Mount mount;
    public final List<Option> options;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    AlarmDevice(String id, Role role, Mount mount, double[] box, Option... options) {
        this(id, role, mount, (VoxelShape) null, options);
        for (Direction d : Direction.values()) {
            this.shapes.put(d, turned(d, box));
        }
    }

    AlarmDevice(String id, Role role, Mount mount, VoxelShape shape, Option... options) {
        this.id = id;
        this.role = role;
        this.mount = mount;
        this.options = List.of(options);
        if (shape != null) {
            for (Direction d : Direction.values()) {
                this.shapes.put(d, shape);
            }
        }
        for (Option o : options) {
            GBLang.add(o.labelKey(), o.label());
            for (int i = 0; i < o.values().size(); i++) {
                GBLang.add(o.valueKey(i), o.values().get(i));
            }
        }
    }

    /** Its shape facing {@code d} (a floor device's is the same every way). */
    public VoxelShape shape(Direction d) {
        return this.shapes.get(this.mount == Mount.FLOOR ? Direction.NORTH : d);
    }

    /** The index of its option {@code key} (-1: it has none). */
    public int option(String key) {
        for (int i = 0; i < this.options.size(); i++) {
            if (this.options.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether it makes a sound of its own on the network (silenced by an Alarm Panel). */
    public boolean sounds() {
        return this.role == Role.SOUNDER;
    }

    /** Whether it gives a redstone signal. */
    public boolean givesRedstone() {
        return this == RECEIVER || this == MONITOR || this == RELAY;
    }

    private static Option option(String key, String label, int def, String... values) {
        return new Option(key, label, def, List.of(values));
    }

    private static double[] box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new double[] {x0, y0, z0, x1, y1, z1};
    }

    /** A box written facing north, turned to face {@code d} (as the floodlight's: its back stays on the mounting face). */
    private static VoxelShape turned(Direction d, double[] b) {
        double[][] c = {{b[0], b[1], b[2]}, {b[3], b[4], b[5]}};
        double[] lo = {16, 16, 16}, hi = {0, 0, 0};
        for (double[] p : c) {
            double x = p[0], y = p[1], z = p[2];
            double[] q = switch (d) {
                case NORTH -> new double[] {x, y, z};
                case SOUTH -> new double[] {16 - x, y, 16 - z};
                case WEST -> new double[] {z, y, 16 - x};
                case EAST -> new double[] {16 - z, y, x};
                case UP -> new double[] {x, 16 - z, y};
                case DOWN -> new double[] {x, z, 16 - y};
            };
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], q[k]);
                hi[k] = Math.max(hi[k], q[k]);
            }
        }
        return Block.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }
}
