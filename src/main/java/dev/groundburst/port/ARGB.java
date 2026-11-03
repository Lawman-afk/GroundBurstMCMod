package dev.groundburst.port;

import net.minecraft.util.Mth;

/** Minecraft 26.x's {@code ARGB} colour helpers the mod uses (1.21.1 has some of them as {@code FastColor.ARGB32}). */
public final class ARGB {
    private ARGB() {}

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return color >> 16 & 255;
    }

    public static int green(int color) {
        return color >> 8 & 255;
    }

    public static int blue(int color) {
        return color & 255;
    }

    public static int color(int alpha, int red, int green, int blue) {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255;
    }

    public static int color(int red, int green, int blue) {
        return color(255, red, green, blue);
    }

    public static int color(int alpha, int rgb) {
        return (alpha & 255) << 24 | rgb & 0xFFFFFF;
    }

    public static int color(float alpha, int rgb) {
        return color(as8BitChannel(alpha), rgb);
    }

    public static int colorFromFloat(float alpha, float red, float green, float blue) {
        return color(as8BitChannel(alpha), as8BitChannel(red), as8BitChannel(green), as8BitChannel(blue));
    }

    public static int as8BitChannel(float value) {
        return Mth.floor(value * 255.0F);
    }

    public static float alphaFloat(int color) {
        return alpha(color) / 255.0F;
    }

    public static float redFloat(int color) {
        return red(color) / 255.0F;
    }

    public static float greenFloat(int color) {
        return green(color) / 255.0F;
    }

    public static float blueFloat(int color) {
        return blue(color) / 255.0F;
    }

    public static int opaque(int color) {
        return color | 0xFF000000;
    }

    public static int white(float alpha) {
        return color(as8BitChannel(alpha), 0xFFFFFF);
    }

    public static int black(float alpha) {
        return color(as8BitChannel(alpha), 0);
    }

    public static int srgbLerp(float alpha, int p0, int p1) {
        return color(Mth.lerpInt(alpha, alpha(p0), alpha(p1)), Mth.lerpInt(alpha, red(p0), red(p1)), Mth.lerpInt(alpha, green(p0), green(p1)),
                Mth.lerpInt(alpha, blue(p0), blue(p1)));
    }

    public static int multiply(int lhs, int rhs) {
        return color(alpha(lhs) * alpha(rhs) / 255, red(lhs) * red(rhs) / 255, green(lhs) * green(rhs) / 255, blue(lhs) * blue(rhs) / 255);
    }

    public static int scaleRGB(int color, float scale) {
        return color(alpha(color), Mth.clamp((int) (red(color) * scale), 0, 255), Mth.clamp((int) (green(color) * scale), 0, 255),
                Mth.clamp((int) (blue(color) * scale), 0, 255));
    }

    /** ARGB as 1.21.1's images keep it (ABGR), or back: red and blue change places. */
    public static int toABGR(int color) {
        return (color & 0xFF00FF00) | (color >> 16 & 0xFF) | (color & 0xFF) << 16;
    }
}
