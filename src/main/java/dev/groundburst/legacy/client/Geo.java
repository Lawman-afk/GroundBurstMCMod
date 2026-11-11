package dev.groundburst.legacy.client;

import software.bernie.geckolib.cache.object.GeoBone;

/**
 * GeckoLib 4's bone setters as the 1.21.1 build calls them (GeckoLib on 1.21 sets a bone's three axes in one call and marks
 * it changed; on 1.20.1 each axis has its own setter, and the mark is a call of its own).
 */
public final class Geo {
    private Geo() {}

    public static void updateRotation(GeoBone bone, float x, float y, float z) {
        bone.setRotX(x);
        bone.setRotY(y);
        bone.setRotZ(z);
        bone.markRotationAsChanged();
    }

    public static void updatePosition(GeoBone bone, float x, float y, float z) {
        bone.setPosX(x);
        bone.setPosY(y);
        bone.setPosZ(z);
        bone.markPositionAsChanged();
    }

    public static void updateScale(GeoBone bone, float x, float y, float z) {
        bone.setScaleX(x);
        bone.setScaleY(y);
        bone.setScaleZ(z);
        bone.markScaleAsChanged();
    }

    /** 1.21's whole colour (ARGB) as GeckoLib 4 on 1.20.1 takes it, one channel at a time. */
    public static float red(int argb) {
        return ((argb >> 16) & 255) / 255f;
    }

    public static float green(int argb) {
        return ((argb >> 8) & 255) / 255f;
    }

    public static float blue(int argb) {
        return (argb & 255) / 255f;
    }

    public static float alpha(int argb) {
        return ((argb >>> 24) & 255) / 255f;
    }

    /** The four channels back as one colour (ARGB). */
    public static int argb(float red, float green, float blue, float alpha) {
        return (Math.round(alpha * 255) & 255) << 24 | (Math.round(red * 255) & 255) << 16 | (Math.round(green * 255) & 255) << 8
                | Math.round(blue * 255) & 255;
    }
}
