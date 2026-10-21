package dev.groundzero.content.assembly;

import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.groundzero.GroundZero;
import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.registry.GZLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** The warhead components (DESIGN.md §9.2-9.4): payload cores, casings and fuzes, each saying where it goes. */
public final class ComponentItems {
    static final String T_CORE = GZLang.add(GroundZero.MODID + ".component.core", "Payload core: makes a %s");
    static final String T_CLASS = GZLang.add(GroundZero.MODID + ".component.class", "%s");
    static final String T_CASING = GZLang.add(GroundZero.MODID + ".component.casing", "Warhead casing for class %s payloads");
    static final String T_FUZE = GZLang.add(GroundZero.MODID + ".component.fuze", "Fuze");
    static final String T_BENCH = GZLang.add(GroundZero.MODID + ".component.bench", "Fitted at an Ordnance Assembly Bench");
    static final String[] T_FUZE_WHAT = {
            GZLang.add(GroundZero.MODID + ".component.fuze.impact", "Goes off on redstone, or when dropped or shot"),
            GZLang.add(GroundZero.MODID + ".component.fuze.timed", "Redstone starts a countdown (1-600 s)"),
            GZLang.add(GroundZero.MODID + ".component.fuze.proximity", "Goes off when anything living comes near (2-16 blocks)"),
            GZLang.add(GroundZero.MODID + ".component.fuze.remote", "Goes off on a Remote Detonator signal"),
            GZLang.add(GroundZero.MODID + ".component.fuze.barometric", "Bursts at a set height when a missile delivers it")};

    private ComponentItems() {}

    private static Component grey(Component c) {
        return c.copy().withStyle(ChatFormatting.GRAY);
    }

    /** A payload core: what warhead it makes, and its size class. */
    public static class PayloadCore extends Item {
        private final Supplier<? extends ChargeBlock> warhead;
        public final SizeClass sizeClass;

        public PayloadCore(Properties properties, Supplier<? extends ChargeBlock> warhead, SizeClass sizeClass) {
            super(properties);
            this.warhead = warhead;
            this.sizeClass = sizeClass;
        }

        /** The warhead block this core is assembled into. */
        public ChargeBlock warhead() {
            return this.warhead.get();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(grey(Component.translatable(T_CORE, warhead().getName())));
            tooltip.accept(grey(Component.translatable(this.sizeClass.label)));
            tooltip.accept(Component.translatable(T_BENCH).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** A warhead casing of one size class. */
    public static class Casing extends Item {
        public final SizeClass sizeClass;

        public Casing(Properties properties, SizeClass sizeClass) {
            super(properties);
            this.sizeClass = sizeClass;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(grey(Component.translatable(T_CASING, this.sizeClass.name())));
            tooltip.accept(Component.translatable(T_BENCH).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** A fuze. */
    public static class FuzeItem extends Item {
        public final Fuze fuze;

        public FuzeItem(Properties properties, Fuze fuze) {
            super(properties);
            this.fuze = fuze;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(grey(Component.translatable(T_FUZE_WHAT[this.fuze.ordinal()])));
            tooltip.accept(Component.translatable(T_BENCH).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
