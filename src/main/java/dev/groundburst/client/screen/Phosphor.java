package dev.groundburst.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Drawing for the green phosphor screens (the Launch Computer's map, the Radar Array's scope): colours and simple shapes. */
public final class Phosphor {
    public static final int PANEL = 0xF0101410, EDGE = 0xFF3A4A3A, BRIGHT = 0xFF40FF70, DIM = 0xFF1C6A30, FAINT = 0x5530A048,
            TEXT = 0xFFC8F0C8, RED = 0xFFFF4A3A, DIM_RED = 0xAA902A20, AMBER = 0xFFFFB040, WHITE = 0xFFF0F0F0, LIST_BG = 0xFF061A0C,
            SELECTED_BG = 0xFF14401E, SCOPE_BG = 0xFF061A0C;

    private Phosphor() {}

    /** A filled disc, row by row. */
    public static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    public static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        int steps = Math.max(24, r * 6);
        for (int i = 0; i < steps; i++) {
            double a = i * 2 * Math.PI / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    /**
     * An ellipse round (cx, cy) as single pixels: semi-axis {@code a} along the unit direction (ux, uy), {@code b} across
     * it; dashed every {@code dash} pixels if dash > 0.
     */
    public static void ellipse(GuiGraphicsExtractor g, int cx, int cy, double a, double b, double ux, double uy, int color, int dash) {
        int steps = Math.max(24, (int) ((a + b) * Math.PI * 1.5));
        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE, n = 0;
        for (int i = 0; i < steps; i++) {
            double t = i * 2 * Math.PI / steps, p = Math.cos(t) * a, q = Math.sin(t) * b;
            int x = cx + (int) Math.round(ux * p - uy * q), y = cy + (int) Math.round(uy * p + ux * q);
            if (x == lastX && y == lastY) {
                continue;
            }
            lastX = x;
            lastY = y;
            if (dash > 0 && (n++ / dash) % 2 == 1) {
                continue;
            }
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    /** A filled disc kept to a rectangle (a strike far bigger than the map draws only the rows it shows). */
    public static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color, int x0, int y0, int x1, int y1) {
        for (int dy = Math.max(-r, y0 - cy); dy <= Math.min(r, y1 - 1 - cy); dy++) {
            int half = (int) Math.sqrt((double) r * r - (double) dy * dy);
            int from = Math.max(x0, cx - half), to = Math.min(x1, cx + half + 1);
            if (from < to) {
                g.fill(from, cy + dy, to, cy + dy + 1, color);
            }
        }
    }

    /** A line of single pixels; dashed every {@code dash} pixels if dash > 0. */
    public static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, int dash) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            if (dash > 0 && (i / dash) % 2 == 1) {
                continue;
            }
            int x = x0 + (n == 0 ? 0 : (x1 - x0) * i / n), y = y0 + (n == 0 ? 0 : (y1 - y0) * i / n);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    public static void cross(GuiGraphicsExtractor g, int x, int y, int r, int color) {
        line(g, x - r, y - r, x + r, y + r, color, 0);
        line(g, x - r, y + r, x + r, y - r, color, 0);
    }

    /** Faint scan lines over a rectangle, every other row. */
    public static void scanlines(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y += 2) {
            g.fill(x0, y, x1, y + 1, 0x22000000);
        }
    }
}
