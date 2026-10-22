package dev.groundzero.content.strategic;

import java.util.List;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.AssemblyStats;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.assembly.MissileAssembly;
import dev.groundzero.content.assembly.MissileItem;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.content.ordnance.charge.ChargeKind;
import dev.groundzero.registry.GZLang;
import net.minecraft.world.item.ItemStack;

/**
 * The Strategic Assembly Hall's recipe (DESIGN.md §15.2): a Boosted Fission Core (the primary) and a Fusion Secondary
 * make a thermonuclear warhead (a Cobalt-59 Jacket round the secondary makes it a cobalt one); set on an
 * Intercontinental Booster, with a guidance unit and Hardened Electronics if fitted, they make one intercontinental
 * missile. Taken apart, the missile gives them all back.
 */
public final class StrategicAssembly {
    /** A whole weapon takes a minute. */
    public static final int TICKS = 1200;

    public enum Problem {
        NONE(""),
        NOT_FORMED("Finish building the hall first"),
        NO_PRIMARY("Primary: a Boosted Fission Core"),
        NO_SECONDARY("Fit a Fusion Secondary"),
        NO_BOOSTER("Fit an Intercontinental Booster"),
        OUTPUT_FULL("Take the finished missile out first");

        public final String key;

        Problem(String english) {
            this.key = english.isEmpty() ? "" : GZLang.add(GroundZero.MODID + ".strategic.problem." + name().toLowerCase(java.util.Locale.ROOT), english);
        }
    }

    private StrategicAssembly() {}

    public static Problem check(ItemStack primary, ItemStack secondary, ItemStack booster, ItemStack output) {
        if (!primary.is(GZAssembly.BOOSTED_FISSION_CORE.get())) {
            return Problem.NO_PRIMARY;
        }
        if (!secondary.is(GZStrategic.FUSION_SECONDARY.get())) {
            return Problem.NO_SECONDARY;
        }
        if (!booster.is(GZStrategic.INTERCONTINENTAL_BOOSTER.get())) {
            return Problem.NO_BOOSTER;
        }
        return output.isEmpty() ? Problem.NONE : Problem.OUTPUT_FULL;
    }

    /** The warhead the parts make: a cobalt one with the jacket fitted, a thermonuclear one without. */
    public static ChargeBlock warhead(ItemStack jacket) {
        return (jacket.is(GZStrategic.COBALT_JACKET.get()) ? GZOrdnance.COBALT_WARHEAD : GZOrdnance.THERMONUCLEAR_WARHEAD).block().get();
    }

    /** The intercontinental missile the parts make (jacket, guidance and module may be empty). */
    public static ItemStack assemble(ItemStack jacket, ItemStack guidance, ItemStack module) {
        MissileParts.Guidance g = guidance.getItem() instanceof MissileParts.GuidanceItem gi ? gi.guidance : MissileParts.Guidance.UNGUIDED;
        return MissileAssembly.missile(new MissileSpec(new ItemStack(warhead(jacket)), SizeClass.XXL, MissileParts.Motor.ICBM, g,
                module.getItem() instanceof MissileParts.HardenedModule));
    }

    /**
     * An intercontinental missile's parts, in the hall's slot order: primary, secondary, jacket (a cobalt warhead's),
     * booster, guidance, module (empty where it had none). Nothing for any other missile.
     */
    public static List<ItemStack> takeApart(ItemStack missile) {
        MissileSpec s = MissileItem.spec(missile);
        if (s == null || !s.intercontinental()) {
            return List.of();
        }
        boolean cobalt = AssemblyStats.kindOf(s.warhead()) == ChargeKind.COBALT_WARHEAD;
        ItemStack guidance = s.guidance() == MissileParts.Guidance.UNGUIDED ? ItemStack.EMPTY
                : new ItemStack(s.guidance() == MissileParts.Guidance.INERTIAL ? GZAssembly.INERTIAL_GUIDANCE.get() : GZAssembly.GPS_GUIDANCE.get());
        return List.of(new ItemStack(GZAssembly.BOOSTED_FISSION_CORE.get()), new ItemStack(GZStrategic.FUSION_SECONDARY.get()),
                cobalt ? new ItemStack(GZStrategic.COBALT_JACKET.get()) : ItemStack.EMPTY, new ItemStack(GZStrategic.INTERCONTINENTAL_BOOSTER.get()),
                guidance, s.hardened() ? new ItemStack(GZAssembly.HARDENED_ELECTRONICS.get()) : ItemStack.EMPTY);
    }
}
