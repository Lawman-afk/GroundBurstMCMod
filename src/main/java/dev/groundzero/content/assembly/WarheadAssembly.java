package dev.groundzero.content.assembly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.registry.GZDataComponents;
import dev.groundzero.registry.GZLang;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * The warhead line of the Assembly Bench (DESIGN.md §10): a payload core, a casing of its class and a fuze make the
 * core's warhead, carrying the fuze; a warhead taken apart gives all three back.
 */
public final class WarheadAssembly {
    /** Why the parts on the bench cannot be put together (NONE: they can). */
    public enum Problem {
        NONE(""),
        NO_CORE("Fit a payload core"),
        NO_CASING("Fit a warhead casing"),
        NO_FUZE("Fit a fuze"),
        WRONG_CASING("Payload class %s needs a %s casing"),
        OUTPUT_FULL("Take the finished warhead out first");

        public final String key;

        Problem(String english) {
            this.key = english.isEmpty() ? "" : GZLang.add(GroundZero.MODID + ".assembly.problem." + name().toLowerCase(java.util.Locale.ROOT), english);
        }
    }

    /** Text for the JEI category (kept here, in common code, so the language data always has it). */
    public static final String JEI_INFO = GZLang.add(GroundZero.MODID + ".jei.assembly", "Class %s, %s s at %s FE/t");
    public static final String JEI_MISSILES = GZLang.add(GroundZero.MODID + ".jei.missiles", "Missile Assembly");
    public static final String JEI_OPTIONAL = GZLang.add(GroundZero.MODID + ".jei.optional", "Optional");
    public static final String JEI_MISSILE_INFO = GZLang.add(GroundZero.MODID + ".jei.missile_info", "Class %s, range %s-%s blocks");
    public static final String JEI_MISSILE_POWER = GZLang.add(GroundZero.MODID + ".jei.missile_power", "%s FE/t for %s s");
    public static final String JEI_ANY_FUZE = GZLang.add(GroundZero.MODID + ".jei.any_fuze", "Impact or Barometric: where it hits, or in the air over the target");

    private static @Nullable Map<ChargeBlock, ComponentItems.PayloadCore> coreByWarhead;

    private WarheadAssembly() {}

    private static Map<ChargeBlock, ComponentItems.PayloadCore> cores() {
        if (coreByWarhead == null) {
            Map<ChargeBlock, ComponentItems.PayloadCore> map = new HashMap<>();
            for (var core : GZAssembly.CORES) {
                map.put(core.get().warhead(), core.get());
            }
            coreByWarhead = map;
        }
        return coreByWarhead;
    }

    /** The payload core a warhead is made from (null: not an assembled warhead). */
    public static ComponentItems.@Nullable PayloadCore coreOf(ChargeBlock warhead) {
        return cores().get(warhead);
    }

    /** A warhead's size class (null: not an assembled warhead). */
    public static @Nullable SizeClass sizeOf(ChargeBlock warhead) {
        ComponentItems.PayloadCore core = coreOf(warhead);
        return core == null ? null : core.sizeClass;
    }

    public static ComponentItems.Casing casing(SizeClass size) {
        return GZAssembly.CASINGS.get(size.ordinal()).get();
    }

    public static ComponentItems.FuzeItem fuzeItem(Fuze fuze) {
        return GZAssembly.FUZES.get(fuze.ordinal()).get();
    }

    /** Whether a stack is a warhead the bench can take apart. */
    public static boolean isWarhead(ItemStack stack) {
        return stack.getItem() instanceof BlockItem b && b.getBlock() instanceof ChargeBlock c && coreOf(c) != null;
    }

    /** What stops these parts from being put together, if anything. */
    public static Problem check(ItemStack core, ItemStack casing, ItemStack fuze, ItemStack output) {
        if (!(core.getItem() instanceof ComponentItems.PayloadCore c)) {
            return Problem.NO_CORE;
        }
        if (!(casing.getItem() instanceof ComponentItems.Casing k)) {
            return Problem.NO_CASING;
        }
        if (!(fuze.getItem() instanceof ComponentItems.FuzeItem)) {
            return Problem.NO_FUZE;
        }
        if (k.sizeClass != c.sizeClass) {
            return Problem.WRONG_CASING;
        }
        return output.isEmpty() ? Problem.NONE : Problem.OUTPUT_FULL;
    }

    /** The warhead a core and a fuze make, the fuze given {@code setting}. */
    public static ItemStack assemble(ComponentItems.PayloadCore core, ComponentItems.FuzeItem fuze, int setting) {
        ItemStack out = new ItemStack(core.warhead());
        out.set(GZDataComponents.FUZE.get(), FuzeSettings.of(fuze.fuze, setting));
        return out;
    }

    /** A warhead's parts: its core, a casing of its class and its fuze (empty if it is not an assembled warhead). */
    public static List<ItemStack> takeApart(ItemStack warhead) {
        if (!(warhead.getItem() instanceof BlockItem b) || !(b.getBlock() instanceof ChargeBlock block)) {
            return List.of();
        }
        ComponentItems.PayloadCore core = coreOf(block);
        if (core == null) {
            return List.of();
        }
        Fuze fuze = warhead.getOrDefault(GZDataComponents.FUZE.get(), FuzeSettings.IMPACT).fuze();
        return List.of(new ItemStack(core), new ItemStack(casing(core.sizeClass)), new ItemStack(fuzeItem(fuze)));
    }

    /** How long assembly takes, in ticks: a light warhead 5 s, a medium 8 s, a heavy 12 s. */
    public static int assemblyTicks(SizeClass size) {
        return switch (size) {
            case S -> 100;
            case M -> 160;
            case L -> 240;
            case XL -> 320;
            case XXL -> 400;
        };
    }
}
