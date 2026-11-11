package dev.groundburst.client.screen;

import static dev.groundburst.client.screen.Phosphor.AMBER;
import static dev.groundburst.client.screen.Phosphor.DIM;
import static dev.groundburst.client.screen.Phosphor.DIM_RED;
import static dev.groundburst.client.screen.Phosphor.RED;
import static dev.groundburst.client.screen.Phosphor.WHITE;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.RadarArrayBlockEntity;
import dev.groundburst.content.launch.StationPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import dev.groundburst.port.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * A launch network's picture on a green phosphor map (DESIGN.md §17.12, §17.17, §17.19), as the Launch Control Station's
 * screen and the War Room Display's screen both draw it: the radars' reach, the Dead Hand's radius, the blasts its
 * seismographs felt, the members, its designators' laser spots, the missiles in the air (the network's amber, what its
 * radars track red) and a command rocket's signal round it. Each piece is drawn through a {@link View}: the map's
 * rectangle on the screen, the world point at its middle and its scale.
 */
public final class NetworkMap {
    private NetworkMap() {}

    /** A map on the screen: its rectangle (GUI units), the world x/z at its middle, blocks a GUI unit. */
    public record View(int x0, int y0, int w, int h, double cx, double cz, int zoom) {
        public int sx(double wx) {
            return this.x0 + this.w / 2 + (int) Math.floor((wx - this.cx) / this.zoom);
        }

        public int sy(double wz) {
            return this.y0 + this.h / 2 + (int) Math.floor((wz - this.cz) / this.zoom);
        }

        public double wx(double sx) {
            return this.cx + (sx - this.x0 - this.w / 2.0) * this.zoom;
        }

        public double wz(double sy) {
            return this.cz + (sy - this.y0 - this.h / 2.0) * this.zoom;
        }

        public int x1() {
            return this.x0 + this.w;
        }

        public int y1() {
            return this.y0 + this.h;
        }

        public boolean inside(double x, double y) {
            return x >= this.x0 && x < this.x1() && y >= this.y0 && y < this.y1();
        }
    }

    /** The working radars' reach: dim dashed squares, under everything. */
    public static void radarReach(GuiGraphicsExtractor g, View v, List<StationPayloads.MemberView> members) {
        for (StationPayloads.MemberView m : members) {
            if (m.radar() && !m.lost() && m.contact() && m.working()) {
                int ax0 = v.sx(m.at().getX() - RadarArrayBlockEntity.RANGE), ax1 = v.sx(m.at().getX() + RadarArrayBlockEntity.RANGE);
                int az0 = v.sy(m.at().getZ() - RadarArrayBlockEntity.RANGE), az1 = v.sy(m.at().getZ() + RadarArrayBlockEntity.RANGE);
                Phosphor.line(g, ax0, az0, ax1, az0, DIM, 4);
                Phosphor.line(g, ax0, az1, ax1, az1, DIM, 4);
                Phosphor.line(g, ax0, az0, ax0, az1, DIM, 4);
                Phosphor.line(g, ax1, az0, ax1, az1, DIM, 4);
            }
        }
    }

    /** The Dead Hand's radius while it is armed (dashed amber; red and flashing in alert, with the strike's cross). */
    public static void deadHands(GuiGraphicsExtractor g, View v, List<StationPayloads.MemberView> members, long ticks) {
        for (StationPayloads.MemberView m : members) {
            if (m.deadHand() && !m.lost() && m.armed() && m.range() > 0) {
                boolean alert = m.readiness() == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal();
                int r = Math.max(2, (int) Math.round(m.range() / (double) v.zoom()));
                Phosphor.ellipse(g, v.sx(m.at().getX() + 0.5), v.sy(m.at().getZ() + 0.5), r, r, 1, 0,
                        alert ? (ticks % 20 < 12 ? RED : DIM_RED) : 0x99FFB040, 4);
                if (alert && m.hasTarget()) {
                    Phosphor.cross(g, v.sx(m.targetX() + 0.5), v.sy(m.targetZ() + 0.5), 4, RED);
                }
            }
        }
    }

    /**
     * A blast the network's seismographs felt: fixed (two or more), a ring as big as it was strong and its magnitude beside
     * it; from one station alone, the arc it may lie in (its bearing and distance give or take). Both fade over ten minutes.
     */
    public static void blast(GuiGraphicsExtractor g, Font font, View v, StationPayloads.BlastView b) {
        int alpha = (int) Mth.clamp(255 - b.age() / 12000.0 * 200, 55, 255);
        int col = alpha << 24 | 0xFFB040;
        if (b.fixed()) {
            int x = v.sx(b.x() + 0.5), z = v.sy(b.z() + 0.5);
            Phosphor.ring(g, x, z, Math.max(2, Math.round(b.magnitude() * 1.4f)), col);
            label(g, font, String.format(Locale.ROOT, "M%.1f", b.magnitude()), x + 5, z - 3, col);
            return;
        }
        double near = Math.max(0, b.distance() - b.distanceSpread()), far = b.distance() + b.distanceSpread();
        double a0 = Math.toRadians(b.bearing() - b.bearingSpread()), a1 = Math.toRadians(b.bearing() + b.bearingSpread());
        int steps = 10;
        int lastNx = 0, lastNz = 0, lastFx = 0, lastFz = 0;
        for (int i = 0; i <= steps; i++) {
            double a = a0 + (a1 - a0) * i / steps;
            int nx = v.sx(b.fromX() + Math.sin(a) * near), nz = v.sy(b.fromZ() - Math.cos(a) * near);
            int fx = v.sx(b.fromX() + Math.sin(a) * far), fz = v.sy(b.fromZ() - Math.cos(a) * far);
            if (i > 0) {
                Phosphor.line(g, lastNx, lastNz, nx, nz, col, 0);
                Phosphor.line(g, lastFx, lastFz, fx, fz, col, 0);
            } else {
                Phosphor.line(g, nx, nz, fx, fz, col, 2);
            }
            if (i == steps) {
                Phosphor.line(g, nx, nz, fx, fz, col, 2);
            }
            lastNx = nx;
            lastNz = nz;
            lastFx = fx;
            lastFz = fz;
        }
        label(g, font, String.format(Locale.ROOT, "M%.1f?", b.magnitude()), lastFx + 3, lastFz - 3, col);
    }

    /**
     * The members, each by its kind (a launcher a filled square, a radar a ring, a seismograph a diamond, the Dead Hand a
     * crossed box) in {@code colour}'s colour with its number beside it ({@code number}, 0: none), a launcher joined to its
     * target while it is armed or {@code joined} says so (red, the cross dim unless {@code bright}).
     */
    public static void members(GuiGraphicsExtractor g, Font font, View v, List<StationPayloads.MemberView> members, long ticks,
            ToIntFunction<StationPayloads.MemberView> colour, ToIntFunction<BlockPos> number, Predicate<StationPayloads.MemberView> joined,
            Predicate<StationPayloads.MemberView> bright) {
        boolean blink = ticks % 20 < 12;
        for (StationPayloads.MemberView m : members) {
            int sx = v.sx(m.at().getX() + 0.5), sz = v.sy(m.at().getZ() + 0.5);
            if (m.launcher() && m.hasTarget()) {
                int tx = v.sx(m.targetX() + 0.5), tz = v.sy(m.targetZ() + 0.5);
                boolean armed = m.willBeArmed() || m.launchAt() >= 0 || m.standingBy();
                if (armed || joined.test(m)) {
                    Phosphor.line(g, sx, sz, tx, tz, armed ? 0xCCFF4A3A : 0x88FF4A3A, 2);
                }
                if (!m.pendingAim() || blink) {
                    Phosphor.cross(g, tx, tz, 3, bright.test(m) ? RED : DIM_RED);
                }
            }
            int col = colour.applyAsInt(m);
            if (m.radar()) {
                Phosphor.ring(g, sx, sz, 3, col);
            } else if (m.seismograph()) {                            // a diamond
                Phosphor.line(g, sx - 3, sz, sx, sz - 3, col, 0);
                Phosphor.line(g, sx, sz - 3, sx + 3, sz, col, 0);
                Phosphor.line(g, sx + 3, sz, sx, sz + 3, col, 0);
                Phosphor.line(g, sx, sz + 3, sx - 3, sz, col, 0);
            } else if (m.deadHand()) {                               // a square with a cross in it
                g.outline(sx - 3, sz - 3, 7, 7, col);
                Phosphor.cross(g, sx, sz, 2, col);
            } else {
                g.fill(sx - 2, sz - 2, sx + 3, sz + 3, col);
            }
            int n = number.applyAsInt(m.pos());
            if (n > 0) {
                label(g, font, Integer.toString(n), sx + 4, sz - 6, bright.test(m) ? WHITE : DIM);
            }
        }
    }

    /** The network's laser spots: pulsing red diamonds, who lases each beside it. */
    public static void spots(GuiGraphicsExtractor g, Font font, View v, List<StationPayloads.SpotView> spots, long ticks) {
        boolean pulse = ticks % 10 < 6;
        for (StationPayloads.SpotView spot : spots) {
            int px = v.sx(spot.x() + 0.5), pz = v.sy(spot.z() + 0.5);
            int r = pulse ? 4 : 3;
            Phosphor.line(g, px - r, pz, px, pz - r, RED, 0);
            Phosphor.line(g, px, pz - r, px + r, pz, RED, 0);
            Phosphor.line(g, px + r, pz, px, pz + r, RED, 0);
            Phosphor.line(g, px, pz + r, px - r, pz, RED, 0);
            g.fill(px, pz, px + 1, pz + 1, WHITE);
            if (!spot.who().isEmpty()) {
                label(g, font, spot.who(), px + 6, pz - 3, RED);
            }
        }
    }

    /**
     * Missiles in the air: the network's amber, those its radars track red, each with its line to where it comes down
     * (an incoming one's ring there, flashing, and its time to impact where {@code times}); a command rocket with the
     * dashed ring of its signal's reach round it.
     */
    public static void contacts(GuiGraphicsExtractor g, Font font, View v, List<StationPayloads.ContactView> contacts, long ticks, boolean times) {
        boolean blink = ticks % 20 < 12;
        for (StationPayloads.ContactView c : contacts) {
            if (c.command()) {
                int r = Math.max(3, (int) Math.round(c.reach() / (double) v.zoom()));
                Phosphor.ellipse(g, v.sx(c.x() + 0.5), v.sy(c.z() + 0.5), r, r, 1, 0, blink ? 0xCCFFB040 : 0x88FFB040, 5);
            }
        }
        for (StationPayloads.ContactView c : contacts) {
            int mx = v.sx(c.x()), mz = v.sy(c.z());
            int col = c.own() ? AMBER : RED;
            int tx = v.sx(c.targetX()), tz = v.sy(c.targetZ());
            Phosphor.line(g, mx, mz, tx, tz, c.own() ? 0x88FFB040 : 0x88FF4A3A, 2);
            if (times && !c.own()) {
                Phosphor.ring(g, tx, tz, blink ? 4 : 3, RED);
                int s = Math.max(0, (c.eta() + 19) / 20);
                label(g, font, s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60), mx + 4, mz + 2, RED);
            }
            g.fill(mx - 1, mz - 1, mx + 2, mz + 2, col);
            Phosphor.line(g, mx, mz, mx + (int) (Math.sin(c.heading()) * 5), mz + (int) (Math.cos(c.heading()) * 5), col, 0);
        }
    }

    /** Small type (two thirds of the font's size, as the station's list), drawn on whole screen pixels. */
    public static void label(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        int gs = Math.max(1, ((int) Minecraft.getInstance().getWindow().getGuiScale()));
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        g.pose().pushMatrix();
        g.pose().translate(x, Math.round(y * gs) / (float) gs);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** The colour {@code argb} two thirds as bright. */
    public static int dimmer(int argb) {
        int r = (argb >> 16 & 0xFF) * 2 / 3, gr = (argb >> 8 & 0xFF) * 2 / 3, b = (argb & 0xFF) * 2 / 3;
        return argb & 0xFF000000 | r << 16 | gr << 8 | b;
    }
}
