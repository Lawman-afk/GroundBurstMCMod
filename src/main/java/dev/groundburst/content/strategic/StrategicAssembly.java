package dev.groundburst.content.strategic;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.ComponentItems;
import dev.groundburst.content.assembly.GBAssembly;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileItem;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.charge.ChargeBlock;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.registry.GBLang;
import net.minecraft.world.item.ItemStack;

/**
 * The ICBM Assembly Hall's recipes (DESIGN.md §15.2): a Boosted Fission Core (the primary) and a Fusion Secondary
 * make a thermonuclear warhead; the hall's jacket slot may add a Cobalt-59 Jacket round the secondary (a cobalt warhead)
 * or a Tertiary Stage (the Tsar, slice 8d), whose primary is a Twin Boosted Core instead. An exotic core in the primary
 * slot (an Antimatter Trap or a Singularity Core, Phase 11) needs neither secondary nor jacket: it is its own warhead.
 * Set on an Intercontinental Booster, with a guidance unit and Hardened Electronics if fitted, they make one
 * intercontinental missile. Taken apart, the missile gives them all back.
 */
public final class StrategicAssembly {
    /** A whole weapon takes a minute; the Tsar and the exotic ones a minute and a half. */
    public static final int TICKS = 1200, TSAR_TICKS = 1800, EXOTIC_TICKS = 1800;
    public static final String JEI_TITLE = GBLang.add(GroundBurst.MODID + ".jei.strategic_assembly", "ICBM Assembly");
    public static final String JEI_INFO = GBLang.add(GroundBurst.MODID + ".jei.strategic_assembly_info", "Range %s, %s FE/t");

    public enum Problem {
        NONE(""),
        NOT_FORMED("Finish building the hall first"),
        NO_PRIMARY("Primary: Boosted Fission or exotic core"),
        NO_TWIN("Tsar primary: a Twin Boosted Core"),
        TWIN_FOR_TSAR("Twin Boosted Core: Tsar only"),
        NO_SECONDARY("Fit a Fusion Secondary"),
        EXOTIC_ALONE("Exotic core: no secondary or jacket"),
        NO_BOOSTER("Fit an Intercontinental Booster"),
        OUTPUT_FULL("Take the finished missile out first");

        public final String key;

        Problem(String english) {
            this.key = english.isEmpty() ? "" : GBLang.add(GroundBurst.MODID + ".strategic.problem." + name().toLowerCase(java.util.Locale.ROOT), english);
        }
    }

    private StrategicAssembly() {}

    /**
     * Whether a stack is a primary the hall takes: a Boosted Fission Core, the Tsar's Twin Boosted Core, or an exotic
     * core.
     */
    public static boolean isPrimary(ItemStack stack) {
        return stack.is(GBAssembly.BOOSTED_FISSION_CORE.get()) || stack.is(GBStrategic.TWIN_BOOSTED_CORE.get()) || exoticCore(stack) != null;
    }

    /** The exotic warhead a core in the primary slot is (an Antimatter Trap's, a Singularity Core's); null for any other. */
    public static @Nullable ChargeBlock exoticCore(ItemStack stack) {
        return stack.getItem() instanceof ComponentItems.IcbmCore core ? core.warhead() : null;
    }

    /** The exotic cores, in the order JEI lists their missiles. */
    public static List<ItemStack> exoticCores() {
        return List.of(new ItemStack(GBAssembly.ANTIMATTER_TRAP.get()), new ItemStack(GBAssembly.SINGULARITY_CORE.get()));
    }

    /** The primary the jacket slot's part asks for: a Twin Boosted Core for the Tsar, a Boosted Fission Core otherwise. */
    public static ItemStack primaryFor(ItemStack jacket) {
        return new ItemStack(jacket.is(GBStrategic.TERTIARY_STAGE.get()) ? GBStrategic.TWIN_BOOSTED_CORE.get() : GBAssembly.BOOSTED_FISSION_CORE.get());
    }

    /** What stops the parts making a missile (NONE: they will). */
    public static Problem check(ItemStack primary, ItemStack secondary, ItemStack jacket, ItemStack booster, ItemStack output) {
        if (exoticCore(primary) != null) {
            if (!secondary.isEmpty() || !jacket.isEmpty()) {
                return Problem.EXOTIC_ALONE;
            }
        } else {
            boolean tsar = jacket.is(GBStrategic.TERTIARY_STAGE.get());
            if (tsar && !primary.is(GBStrategic.TWIN_BOOSTED_CORE.get())) {
                return Problem.NO_TWIN;
            }
            if (!tsar && primary.is(GBStrategic.TWIN_BOOSTED_CORE.get())) {
                return Problem.TWIN_FOR_TSAR;
            }
            if (!tsar && !primary.is(GBAssembly.BOOSTED_FISSION_CORE.get())) {
                return Problem.NO_PRIMARY;
            }
            if (!secondary.is(GBStrategic.FUSION_SECONDARY.get())) {
                return Problem.NO_SECONDARY;
            }
        }
        if (!booster.is(GBStrategic.INTERCONTINENTAL_BOOSTER.get())) {
            return Problem.NO_BOOSTER;
        }
        return output.isEmpty() ? Problem.NONE : Problem.OUTPUT_FULL;
    }

    /**
     * The warhead a fusion weapon's parts make, by what is in the jacket slot: a cobalt one with a Cobalt-59 Jacket, the
     * Tsar with a Tertiary Stage, a thermonuclear one with neither.
     */
    public static ChargeBlock warhead(ItemStack jacket) {
        return (jacket.is(GBStrategic.COBALT_JACKET.get()) ? GBOrdnance.COBALT_WARHEAD
                : jacket.is(GBStrategic.TERTIARY_STAGE.get()) ? GBOrdnance.TSAR_WARHEAD : GBOrdnance.THERMONUCLEAR_WARHEAD).block().get();
    }

    /** The warhead the parts make: an exotic core's own, or the fusion weapon its jacket slot makes. */
    public static ChargeBlock warhead(ItemStack primary, ItemStack jacket) {
        ChargeBlock exotic = exoticCore(primary);
        return exotic != null ? exotic : warhead(jacket);
    }

    /** How long the hall works on the fusion weapon the jacket slot makes. */
    public static int ticks(ItemStack jacket) {
        return jacket.is(GBStrategic.TERTIARY_STAGE.get()) ? TSAR_TICKS : TICKS;
    }

    /** How long the hall works on the weapon the parts make. */
    public static int ticks(ItemStack primary, ItemStack jacket) {
        return exoticCore(primary) != null ? EXOTIC_TICKS : ticks(jacket);
    }

    /** The intercontinental missile a fusion weapon's parts make (jacket, guidance and module may be empty). */
    public static ItemStack assemble(ItemStack jacket, ItemStack guidance, ItemStack module) {
        return assemble(ItemStack.EMPTY, jacket, guidance, module);
    }

    /** The intercontinental missile the parts make (an exotic core's, or a fusion weapon's; the rest may be empty). */
    public static ItemStack assemble(ItemStack primary, ItemStack jacket, ItemStack guidance, ItemStack module) {
        MissileParts.Guidance g = guidance.getItem() instanceof MissileParts.GuidanceItem gi ? gi.guidance : MissileParts.Guidance.UNGUIDED;
        return MissileAssembly.missile(new MissileSpec(new ItemStack(warhead(primary, jacket)), SizeClass.XXL, MissileParts.Motor.ICBM, g,
                module.getItem() instanceof MissileParts.HardenedModule));
    }

    /**
     * An intercontinental missile's parts, in the hall's slot order: primary, secondary, the jacket slot's part (a cobalt
     * warhead's jacket, the Tsar's Tertiary Stage), booster, guidance, module (empty where it had none; an exotic
     * missile's core alone in the primary slot). Nothing for any other missile.
     */
    public static List<ItemStack> takeApart(ItemStack missile) {
        MissileSpec s = MissileItem.spec(missile);
        if (s == null || !s.intercontinental()) {
            return List.of();
        }
        ChargeKind kind = AssemblyStats.kindOf(s.warhead());
        ItemStack guidance = s.guidance() == MissileParts.Guidance.UNGUIDED ? ItemStack.EMPTY
                : new ItemStack(s.guidance() == MissileParts.Guidance.INERTIAL ? GBAssembly.INERTIAL_GUIDANCE.get() : GBAssembly.GPS_GUIDANCE.get());
        ItemStack module = s.hardened() ? new ItemStack(GBAssembly.HARDENED_ELECTRONICS.get()) : ItemStack.EMPTY;
        ItemStack booster = new ItemStack(GBStrategic.INTERCONTINENTAL_BOOSTER.get());
        if (kind != null && kind.exotic()) {
            ItemStack core = exoticCores().stream().filter(c -> exoticCore(c) != null && exoticCore(c).kind == kind).findFirst().orElse(ItemStack.EMPTY);
            return List.of(core, ItemStack.EMPTY, ItemStack.EMPTY, booster, guidance, module);
        }
        ItemStack third = kind == ChargeKind.COBALT_WARHEAD ? new ItemStack(GBStrategic.COBALT_JACKET.get())
                : kind == ChargeKind.TSAR_WARHEAD ? new ItemStack(GBStrategic.TERTIARY_STAGE.get()) : ItemStack.EMPTY;
        return List.of(primaryFor(third), new ItemStack(GBStrategic.FUSION_SECONDARY.get()), third, booster, guidance, module);
    }
}
