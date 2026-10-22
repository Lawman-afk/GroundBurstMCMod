package dev.groundzero.content.assembly;

import java.util.function.Consumer;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;

/**
 * Missile parts (DESIGN.md §9.3-9.7, Phase 7 slice 7b): bodies (which warheads they take), motors (how far they fly),
 * guidance (how close to the target they come down) and the hardened-electronics module (no EMP dud).
 */
public final class MissileParts {
    private MissileParts() {}

    /** A rocket motor: its range and which bodies it fits. */
    public enum Motor {
        SRM_I("solid_rocket_motor_i", 400, SizeClass.S, SizeClass.M),
        SRM_II("solid_rocket_motor_ii", 1024, SizeClass.S, SizeClass.L),
        LIQUID("liquid_fuel_stage", 2500, SizeClass.L, SizeClass.XL),
        /** The Intercontinental Booster: only ever in a whole weapon from the Strategic Assembly Hall (no bench motor). */
        ICBM("intercontinental_booster", 3000, SizeClass.XXL, SizeClass.XXL);

        public final String id;
        /** How far it carries a missile, blocks. */
        public final int range;
        private final SizeClass smallest, largest;

        Motor(String id, int range, SizeClass smallest, SizeClass largest) {
            this.id = id;
            this.range = range;
            this.smallest = smallest;
            this.largest = largest;
        }

        public boolean fits(SizeClass body) {
            return body.ordinal() >= this.smallest.ordinal() && body.ordinal() <= this.largest.ordinal();
        }

        /** "Tactical and Medium bodies", "Tactical, Medium and Heavy bodies", for tooltips and the bench. */
        public Component bodies() {
            int a = this.smallest.ordinal(), b = this.largest.ordinal();
            Component first = Component.translatable(BODY_NAMES[a]), last = Component.translatable(BODY_NAMES[b]);
            return switch (b - a) {
                case 0 -> Component.translatable(T_BODY_ONE, first);
                case 1 -> Component.translatable(T_BODY_TWO, first, last);
                default -> Component.translatable(T_BODY_THREE, first, Component.translatable(BODY_NAMES[a + 1]), last);
            };
        }

        public static Motor byId(String id) {
            for (Motor m : values()) {
                if (m.id.equals(id)) {
                    return m;
                }
            }
            return SRM_I;
        }
    }

    /** Guidance: how far from its target a missile may come down. No guidance unit fitted: unguided. */
    public enum Guidance {
        UNGUIDED("unguided", 0.25, 0),
        INERTIAL("inertial", 0.04, 0),
        GPS("gps", 0, 2);

        public final String id;
        /** Scatter: this share of the range, or this many blocks. */
        public final double share, blocks;

        Guidance(String id, double share, double blocks) {
            this.id = id;
            this.share = share;
            this.blocks = blocks;
        }

        /** How far off the target it may land when sent {@code distance} blocks. */
        public double scatter(double distance) {
            return this.share * distance + this.blocks;
        }

        /**
         * Where it actually comes down: any point of an ellipse round the target, as likely as any other (so most misses
         * are wide ones), its scatter long or short along the flight and half that to either side.
         */
        public Vec3 aim(Vec3 from, Vec3 target, RandomSource random) {
            double dx = target.x - from.x, dz = target.z - from.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double ax = distance > 1e-6 ? dx / distance : 1, az = distance > 1e-6 ? dz / distance : 0;
            double r = scatter(distance) * Math.sqrt(random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2;
            double along = Math.cos(a) * r, across = Math.sin(a) * r * 0.5;
            return target.add(ax * along - az * across, 0, az * along + ax * across);
        }

        /** "Lands within 2% of the range", "within 2 blocks". */
        public Component accuracy() {
            return this.share > 0 ? Component.translatable(T_ACCURACY_SHARE, Math.round(this.share * 100))
                    : Component.translatable(T_ACCURACY_BLOCKS, (int) this.blocks);
        }

        public static Guidance byId(String id) {
            for (Guidance g : values()) {
                if (g.id.equals(id)) {
                    return g;
                }
            }
            return UNGUIDED;
        }
    }

    static final String[] BODY_NAMES = {
            GZLang.add(GroundZero.MODID + ".missile.body.s", "Tactical"),
            GZLang.add(GroundZero.MODID + ".missile.body.m", "Medium"),
            GZLang.add(GroundZero.MODID + ".missile.body.l", "Heavy"),
            GZLang.add(GroundZero.MODID + ".missile.body.xl", "Strategic"),
            GZLang.add(GroundZero.MODID + ".missile.body.xxl", "Intercontinental")};
    static final String T_BODY_ONE = GZLang.add(GroundZero.MODID + ".missile.fits_one", "%s bodies");
    static final String T_BODY_TWO = GZLang.add(GroundZero.MODID + ".missile.fits_two", "%s and %s bodies");
    static final String T_BODY_THREE = GZLang.add(GroundZero.MODID + ".missile.fits_three", "%s, %s and %s bodies");
    static final String T_ACCURACY_SHARE = GZLang.add(GroundZero.MODID + ".missile.accuracy_share", "Lands within %s%% of the distance");
    static final String T_ACCURACY_BLOCKS = GZLang.add(GroundZero.MODID + ".missile.accuracy_blocks", "Lands within %s blocks of the target");
    static final String T_BODY_TIP = GZLang.add(GroundZero.MODID + ".missile.body_tip", "Takes conventional warheads up to class %s");
    static final String T_STRATEGIC_TIP = GZLang.add(GroundZero.MODID + ".missile.strategic_tip", "Takes nuclear warheads only; fires from a Launch Complex or a Missile Silo");
    static final String T_MOTOR_TIP = GZLang.add(GroundZero.MODID + ".missile.motor_tip", "Range %s blocks; fits %s");
    static final String T_HARDENED_TIP = GZLang.add(GroundZero.MODID + ".missile.hardened_tip", "Optional: an EMP never leaves the missile a dud");
    static final String T_BENCH = GZLang.add(GroundZero.MODID + ".missile.bench", "Fitted at an Ordnance Assembly Bench (Missile page)");
    static final String[] GUIDANCE_NAMES = {
            GZLang.add(GroundZero.MODID + ".missile.guidance.unguided", "Unguided"),
            GZLang.add(GroundZero.MODID + ".missile.guidance.inertial", "Inertial"),
            GZLang.add(GroundZero.MODID + ".missile.guidance.gps", "GPS")};

    private static Component grey(Component c) {
        return c.copy().withStyle(ChatFormatting.GRAY);
    }

    /** A missile body of one size class. */
    public static class Body extends Item {
        public final SizeClass sizeClass;

        public Body(Properties properties, SizeClass sizeClass) {
            super(properties);
            this.sizeClass = sizeClass;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(grey(this.sizeClass == SizeClass.XL ? Component.translatable(T_STRATEGIC_TIP)
                    : Component.translatable(T_BODY_TIP, this.sizeClass.name())));
            tooltip.accept(Component.translatable(T_BENCH).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static class MotorItem extends Item {
        public final Motor motor;

        public MotorItem(Properties properties, Motor motor) {
            super(properties);
            this.motor = motor;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(grey(Component.translatable(T_MOTOR_TIP, this.motor.range, this.motor.bodies())));
            tooltip.accept(Component.translatable(T_BENCH).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static class GuidanceItem extends Item {
        public final Guidance guidance;

        public GuidanceItem(Properties properties, Guidance guidance) {
            super(properties);
            this.guidance = guidance;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(grey(this.guidance.accuracy()));
            tooltip.accept(Component.translatable(T_BENCH).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static class HardenedModule extends Item {
        public HardenedModule(Properties properties) {
            super(properties);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(grey(Component.translatable(T_HARDENED_TIP)));
            tooltip.accept(Component.translatable(T_BENCH).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** How a body draws the missile (the silo's and the one in flight): {across, along} its length. */
    public static float[] scale(SizeClass body) {
        return switch (body) {
            case S -> new float[] {0.85f, 0.75f};
            case M -> new float[] {1f, 1f};
            case L -> new float[] {1.15f, 1.2f};
            case XL -> new float[] {1f, 1f};                     // its own, longer model (see STRATEGIC_TAIL)
            case XXL -> new float[] {1f, 1f};                    // its own model too (ICBM_TAIL)
        };
    }

    /**
     * The Strategic missile's model runs this many item pixels below the usual missile's tail line (item models may
     * reach down to -16): drawn from the item model, it is lifted by this much more to stand on its tail.
     */
    public static final float STRATEGIC_TAIL = 11.5f;

    /** The same for the intercontinental missile's model (launch.py's icbm_model). */
    public static final float ICBM_TAIL = 11.5f;

    /**
     * The custom model data that picks a missile's item model: its warhead's nose, on a Strategic or an intercontinental
     * missile its own model.
     */
    public static String look(String warheadId, SizeClass body) {
        String id = warheadId.isEmpty() ? "none" : warheadId;
        return switch (body) {
            case XL -> "strategic_" + id;
            case XXL -> "icbm_" + id;
            default -> warheadId;
        };
    }

    /**
     * The flight model of a look ("flight_" before it; "flight_none" for a missile with no warhead): four texels an item
     * pixel, as fine as the missiles standing on the launchers, for a missile in flight, its spent stages and the
     * assembly screens' big preview (after slice 9b). The inventory keeps the item model.
     */
    public static String flight(String look) {
        return "flight_" + (look.isEmpty() ? "none" : look);
    }

    /** A missile as its flight model draws it (any other stack as it is), for a big preview. */
    public static net.minecraft.world.item.ItemStack flightLook(net.minecraft.world.item.ItemStack stack) {
        if (!stack.is(GZAssembly.MISSILE.get())) {
            return stack;
        }
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        String look = data == null || data.strings().isEmpty() ? "" : data.strings().getFirst();
        net.minecraft.world.item.ItemStack out = stack.copy();
        out.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(
                java.util.List.of(), java.util.List.of(), java.util.List.of(flight(look)), java.util.List.of()));
        return out;
    }

    // --- in flight (slice 9a) ------------------------------------------------------------------------------------------------
    /** The model of a missile in flight that has dropped {@code dropped} spent stages ("_s1", "_s2" after the first). */
    public static String look(String warheadId, SizeClass body, int dropped) {
        String id = look(warheadId, body);
        return dropped > 0 && (body == SizeClass.XL || body == SizeClass.XXL) ? id + "_s" + dropped : id;
    }

    /**
     * When (fractions of the flight, from lift-off to the target) a missile drops its spent stages: a Strategic missile
     * its first stage, an intercontinental one its first and second; the rest have one stage.
     */
    public static float[] stageDrops(SizeClass body) {
        return switch (body) {
            case XL -> new float[] {0.16f};
            case XXL -> new float[] {0.12f, 0.24f};
            default -> new float[0];
        };
    }

    /** The fraction of its flight at which its last engine burns out, after which it coasts in silence. */
    public static float burnout(SizeClass body) {
        return switch (body) {
            case XL -> 0.40f;
            case XXL -> 0.36f;
            default -> 0.45f;
        };
    }

    /** The model of its {@code k}th spent stage falling away (1: the first). */
    public static String spentStage(SizeClass body, int k) {
        return body == SizeClass.XXL ? "icbm_stage" + k : "strategic_stage" + k;
    }

    /** The middle of its {@code k}th spent stage, in its model's own item pixels (it tumbles about this). */
    public static float spentStageMiddle(SizeClass body, int k) {
        return body == SizeClass.XXL ? (k == 1 ? -2.15f : 11.75f) : -1.15f;
    }

    /** How far up its axis from its tail (blocks, as drawn in flight) the engine that is burning sits. */
    public static float engineHeight(SizeClass body, int dropped) {
        float px = switch (body) {
            case XL -> dropped >= 1 ? 8.1f + STRATEGIC_TAIL : 0;          // the second stage's bell
            case XXL -> dropped >= 2 ? 16.5f + ICBM_TAIL : dropped >= 1 ? 6.1f + ICBM_TAIL : 0;
            default -> 0;
        };
        return px / 16 * 4 * scale(body)[1];
    }

    /** How far up its axis from its tail (blocks, as drawn in flight) its nose tip is. */
    public static float noseHeight(SizeClass body) {
        float px = switch (body) {
            case XL -> 32 + STRATEGIC_TAIL;
            case XXL -> 32 + ICBM_TAIL;
            default -> 32;
        };
        return px / 16 * 4 * scale(body)[1];
    }

    public static String guidanceName(Guidance g) {
        return GUIDANCE_NAMES[g.ordinal()];
    }

    public static String bodyName(SizeClass s) {
        return BODY_NAMES[s.ordinal()];
    }
}
