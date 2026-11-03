package dev.groundburst.content.artillery;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The 81 mm Mortar (DESIGN.md §20.1): a GunBlockEntity with the mortar's model, its tube on a ball socket and its
 * bipod leaned about its feet so its elevating screw, stretched, meets the tube's collar.
 */
public class MortarBlockEntity extends GunBlockEntity {
    /** The four charges' muzzle speeds, blocks a tick: 50, 100, 150 and 200 blocks at 45 degrees on the flat. */
    public static final double[] CHARGES = {1.58, 2.24, 2.74, 3.16};
    public static final float MIN_ELEVATION = 45, MAX_ELEVATION = 85, REST_ELEVATION = 60;
    public static final double MIN_RANGE = 16, MAX_RANGE = 200;
    public static final int RELOAD = 50;
    public static final double BLOOM = 4, MAX_BLOOM = 6;
    /** Its tube's socket (blocks over its floor, and back from the block's middle toward its rest), the tube's length. */
    public static final double SOCKET_Y = 2.2 / 16, SOCKET_BACK = 2.0 / 16, TUBE = 20.0 / 16;
    /** The tube's collar (where the bipod's screw holds it), model pixels along the tube from the socket. */
    static final double COLLAR = 12;
    /**
     * The bipod's feet line and its screw's foot at rest (model pixels, y up and z back of the block's middle). The
     * bipod is built upright on its feet line and leaned back {@link #LEAN} to put the screw's foot there.
     */
    static final double FEET_Z = -8, SCREW_Y = 7, SCREW_Z = -4;
    static final double LEAN = Math.atan2(SCREW_Z - FEET_Z, SCREW_Y);

    public MortarBlockEntity(BlockPos pos, BlockState state) {
        super(GBArtillery.MORTAR_ENTITY.get(), pos, state, GBArtillery.MORTAR_SPEC);
    }

    /**
     * The tube turned toward its yaw about the socket and raised (it is built standing straight up); the bipod leaned
     * about its feet so its screw, stretched, meets the tube's collar (see {@link #boneScales}).
     */
    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        if (this.level == null) {
            return Map.of();
        }
        float e = shownElevation(partialTick);
        Map<String, float[]> out = new HashMap<>();
        out.put("traverse", new float[] {0, traverseRadians(partialTick), 0});
        double[] b = bipod(e);
        out.put("tube", new float[] {(float) Math.toRadians(-(90 - e)), 0, 0});
        out.put("bipod", new float[] {(float) (b[0] + LEAN), 0, 0});
        out.put("screw_base", new float[] {(float) -LEAN, 0, 0});
        return out;
    }

    /** The model at rest (as an item shows it): the tube laid at {@link #REST_ELEVATION} over its front. */
    public static Map<String, float[]> restPose() {
        double[] b = bipod(REST_ELEVATION);
        return Map.of("tube", new float[] {(float) Math.toRadians(-(90 - REST_ELEVATION)), 0, 0},
                "bipod", new float[] {(float) (b[0] + LEAN), 0, 0}, "screw_base", new float[] {(float) -LEAN, 0, 0});
    }

    /** The screw stretched to the collar. */
    @Override
    public Map<String, float[]> boneScales() {
        if (this.level == null) {
            return Map.of();
        }
        return Map.of("screw", new float[] {1, (float) bipod(shownElevation(0))[1], 1});
    }

    /** As it fires, the whole mortar is driven down into the ground a little and springs back. */
    @Override
    public Map<String, float[]> boneOffsets(float partialTick) {
        double since = sinceFired(partialTick);
        if (since < 0 || since > 10) {
            return Map.of();
        }
        float kick = (float) (since < 1.5 ? since / 1.5 : Math.max(0, 1 - (since - 1.5) / 8.5)) * 0.9f;
        return Map.of("frame", new float[] {0, -kick, 0});
    }

    /**
     * The bipod at elevation {@code e}: how far it leans about its feet (radians, a turn about x) and how long its screw
     * is against its length at rest. Its feet stay put; its screw's top meets the collar ({@link #COLLAR} along the
     * tube from the socket).
     */
    static double[] bipod(double e) {
        double r = Math.toRadians(e);
        double socketY = SOCKET_Y * 16, socketZ = SOCKET_BACK * 16;
        double qy = socketY + COLLAR * Math.sin(r), qz = socketZ - COLLAR * Math.cos(r);   // the collar
        double rest = Math.toRadians(REST_ELEVATION);
        double ry = socketY + COLLAR * Math.sin(rest);
        double restLength = ry - SCREW_Y;                                                 // the screw's length at rest
        double dz = SCREW_Z - FEET_Z;                                                     // its foot ahead of the feet line
        double toY = qy, toZ = qz - FEET_Z;
        double length = Math.sqrt(Math.max(0, toY * toY + toZ * toZ - dz * dz)) - SCREW_Y;
        double lean = Math.atan2(toZ, toY) - Math.atan2(dz, SCREW_Y + length);
        return new double[] {lean, Math.max(0.2, length / restLength)};
    }
}
