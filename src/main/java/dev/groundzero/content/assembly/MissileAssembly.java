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
        SizeClass size = WarheadAssembly.sizeOf((dev.groundzero.content.ordnance.charge.ChargeBlock) ((net.minecraft.world.item.BlockItem) warhead.getItem()).getBlock());
        if (size != null && size.ordinal() > b.sizeClass.ordinal()) {
            return Problem.WARHEAD_TOO_BIG;
        }
        if (!m.motor.fits(b.sizeClass)) {
            return Problem.MOTOR_DOES_NOT_FIT;
        }
        return output.isEmpty() ? Problem.NONE : Problem.OUTPUT_FULL;
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
        out.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(spec.warheadId()), List.of()));
        return out;
    }

    /** A missile's parts: its warhead, body and motor, then its guidance unit and module if it has them. */
    public static List<ItemStack> takeApart(ItemStack missile) {
        MissileSpec s = MissileItem.spec(missile);
        if (s == null) {
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
