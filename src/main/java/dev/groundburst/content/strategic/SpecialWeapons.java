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
import dev.groundburst.content.ordnance.charge.ChargeBlock;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.registry.GBLang;
import net.minecraft.world.item.ItemStack;

/**
 * The Special Weapons Assembler's recipes (DESIGN.md §16.15; after the user's look at Phase 11 the exotic weapons moved
 * here from the ICBM Assembly Hall): an exotic core (an Antimatter Trap, a Singularity Core or a Storm Core) is a warhead
 * in itself. Set on an Intercontinental Booster, with a guidance unit and Hardened Electronics if fitted, it makes that
 * weapon's intercontinental missile, fired like any other from a Launch Complex or a Missile Silo. Taken apart, the
 * missile gives them all back.
 */
public final class SpecialWeapons {
    /** An exotic weapon takes a minute and a half. */
    public static final int TICKS = 1800;
    public static final String JEI_TITLE = GBLang.add(GroundBurst.MODID + ".jei.special_weapons", "Special Weapons Assembly");

    public enum Problem {
        NONE(""),
        NOT_FORMED("Finish building the assembler first"),
        NO_CORE("Fit an exotic core"),
        NO_BOOSTER("Fit an Intercontinental Booster"),
        OUTPUT_FULL("Take the finished missile out first");

        public final String key;

        Problem(String english) {
            this.key = english.isEmpty() ? ""
                    : GBLang.add(GroundBurst.MODID + ".special_weapons.problem." + name().toLowerCase(java.util.Locale.ROOT), english);
        }
    }

    private SpecialWeapons() {}

    /** The exotic warhead a core is (an Antimatter Trap's, a Singularity Core's, a Storm Core's); null for anything else. */
    public static @Nullable ChargeBlock core(ItemStack stack) {
        return stack.getItem() instanceof ComponentItems.IcbmCore core ? core.warhead() : null;
    }

    /** Whether a stack is an exotic core. */
    public static boolean isCore(ItemStack stack) {
        return core(stack) != null;
    }

    /** The exotic cores, in the order JEI lists their missiles. */
    public static List<ItemStack> cores() {
        return List.of(new ItemStack(GBAssembly.ANTIMATTER_TRAP.get()), new ItemStack(GBAssembly.SINGULARITY_CORE.get()),
                new ItemStack(GBAssembly.STORM_CORE.get()));
    }

    /** What stops the parts making a missile (NONE: they will). */
    public static Problem check(ItemStack core, ItemStack booster, ItemStack output) {
        if (!isCore(core)) {
            return Problem.NO_CORE;
        }
        if (!booster.is(GBStrategic.INTERCONTINENTAL_BOOSTER.get())) {
            return Problem.NO_BOOSTER;
        }
        return output.isEmpty() ? Problem.NONE : Problem.OUTPUT_FULL;
    }

    /** The intercontinental missile a core makes (guidance and module may be empty); nothing for anything but a core. */
    public static ItemStack assemble(ItemStack core, ItemStack guidance, ItemStack module) {
        ChargeBlock warhead = core(core);
        if (warhead == null) {
            return ItemStack.EMPTY;
        }
        MissileParts.Guidance g = guidance.getItem() instanceof MissileParts.GuidanceItem gi ? gi.guidance : MissileParts.Guidance.UNGUIDED;
        return MissileAssembly.missile(new MissileSpec(new ItemStack(warhead), SizeClass.XXL, MissileParts.Motor.ICBM, g,
                module.getItem() instanceof MissileParts.HardenedModule));
    }

    /**
     * An exotic missile's parts, in the assembler's slot order: core, booster, guidance, module (empty where it had none).
     * Nothing for any other missile.
     */
    public static List<ItemStack> takeApart(ItemStack missile) {
        MissileSpec s = MissileItem.spec(missile);
        if (s == null || !s.intercontinental()) {
            return List.of();
        }
        ChargeKind kind = AssemblyStats.kindOf(s.warhead());
        ItemStack core = kind == null ? ItemStack.EMPTY
                : cores().stream().filter(c -> core(c) != null && core(c).kind == kind).findFirst().orElse(ItemStack.EMPTY);
        if (core.isEmpty()) {
            return List.of();
        }
        ItemStack guidance = GBAssembly.guidanceItem(s.guidance());
        ItemStack module = s.hardened() ? new ItemStack(GBAssembly.HARDENED_ELECTRONICS.get()) : ItemStack.EMPTY;
        return List.of(core, new ItemStack(GBStrategic.INTERCONTINENTAL_BOOSTER.get()), guidance, module);
    }
}
