package dev.groundzero.content.assembly;

import java.util.ArrayList;
import java.util.List;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZDataComponents;
import dev.groundzero.registry.GZLang;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * The missile line of the Assembly Bench (DESIGN.md §10): a warhead, a body that takes its class, a motor that fits
 * the body, and optionally a guidance unit and the hardened-electronics module make a missile; taken apart, a missile
 * gives them all back.
 */
public final class MissileAssembly {
    /** Assembling a missile takes 8 s. */
    public static final int TICKS = 160;

    public enum Problem {
        NONE(""),
        NO_WARHEAD("Fit a warhead"),
        NO_BODY("Fit a missile body"),
        NO_MOTOR("Fit a rocket motor"),
        WARHEAD_TOO_BIG("A class %s warhead needs a bigger body"),
        NUCLEAR_NEEDS_STRATEGIC("A nuclear warhead needs a Strategic body"),
        STRATEGIC_NUCLEAR_ONLY("A Strategic body carries nuclear warheads only"),
        WHOLE_WEAPON("Built whole at a Strategic Assembly Hall"),
        MOTOR_DOES_NOT_FIT("This motor fits %s"),
        OUTPUT_FULL("Take the finished missile out first");

        public final String key;

        Problem(String english) {
            this.key = english.isEmpty() ? "" : GZLang.add(GroundZero.MODID + ".assembly.missile_problem." + name().toLowerCase(java.util.Locale.ROOT), english);
        }
    }

    private MissileAssembly() {}

    public static Problem check(ItemStack warhead, ItemStack body, ItemStack motor, ItemStack output) {
        if (!WarheadAssembly.isWarhead(warhead)) {
            return Problem.NO_WARHEAD;
        }
        if (!(body.getItem() instanceof MissileParts.Body b)) {
            return Problem.NO_BODY;
        }
        if (!(motor.getItem() instanceof MissileParts.MotorItem m)) {
            return Problem.NO_MOTOR;
        }
        Problem fit = fit(warhead, b.sizeClass);
        if (fit != Problem.NONE) {
            return fit;
        }
        if (!m.motor.fits(b.sizeClass)) {
            return Problem.MOTOR_DOES_NOT_FIT;
        }
        return output.isEmpty() ? Problem.NONE : Problem.OUTPUT_FULL;
    }

    /**
     * Whether a body takes a warhead: a nuclear warhead only a Strategic body (and that body nothing else); any other a
     * body of its class or bigger.
     */
    public static Problem fit(ItemStack warhead, SizeClass body) {
        var kind = AssemblyStats.kindOf(warhead);
        if (kind != null && kind.wholeWeapon()) {
            return Problem.WHOLE_WEAPON;                   // a thermonuclear warhead is only ever built into its missile
        }
        boolean nuclear = kind != null && kind.nuclear();
        if (nuclear != (body == SizeClass.XL)) {
            return nuclear ? Problem.NUCLEAR_NEEDS_STRATEGIC : Problem.STRATEGIC_NUCLEAR_ONLY;
        }
        SizeClass size = warhead.getItem() instanceof net.minecraft.world.item.BlockItem bi
                && bi.getBlock() instanceof dev.groundzero.content.ordnance.charge.ChargeBlock c ? WarheadAssembly.sizeOf(c) : null;
        return !nuclear && size != null && size.ordinal() > body.ordinal() ? Problem.WARHEAD_TOO_BIG : Problem.NONE;
    }

    /** The missile these parts make (guidance and module may be empty). */
    public static ItemStack assemble(ItemStack warhead, MissileParts.Body body, MissileParts.MotorItem motor, ItemStack guidance, ItemStack module) {
        MissileParts.Guidance g = guidance.getItem() instanceof MissileParts.GuidanceItem gi ? gi.guidance : MissileParts.Guidance.UNGUIDED;
        return missile(new MissileSpec(warhead.copyWithCount(1), body.sizeClass, motor.motor, g, module.getItem() instanceof MissileParts.HardenedModule));
    }

    /** A missile item for {@code spec}. */
    public static ItemStack missile(MissileSpec spec) {
        ItemStack out = new ItemStack(GZAssembly.MISSILE.get());
        out.set(GZDataComponents.MISSILE.get(), spec);
        // The item picks its nose (painted as its warhead) by this, like the missile in flight.
        out.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(MissileParts.look(spec.warheadId(), spec.body())),
                List.of()));
        return out;
    }

    /**
     * A missile's parts: its warhead, body and motor, then its guidance unit and module if it has them. None for an
     * intercontinental missile: only the Strategic Assembly Hall takes those apart.
     */
    public static List<ItemStack> takeApart(ItemStack missile) {
        MissileSpec s = MissileItem.spec(missile);
        if (s == null || s.intercontinental()) {
            return List.of();
        }
        List<ItemStack> parts = new ArrayList<>();
        parts.add(s.warhead().copy());
        parts.add(new ItemStack(GZAssembly.BODIES.get(s.body().ordinal()).get()));
        parts.add(new ItemStack(GZAssembly.MOTORS.get(s.motor().ordinal()).get()));
        parts.add(s.guidance() == MissileParts.Guidance.UNGUIDED ? ItemStack.EMPTY
                : new ItemStack(s.guidance() == MissileParts.Guidance.INERTIAL ? GZAssembly.INERTIAL_GUIDANCE.get() : GZAssembly.GPS_GUIDANCE.get()));
        parts.add(s.hardened() ? new ItemStack(GZAssembly.HARDENED_ELECTRONICS.get()) : ItemStack.EMPTY);
        return parts;
    }
}
