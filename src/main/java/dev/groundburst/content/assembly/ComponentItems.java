package dev.groundburst.content.assembly;

import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.ordnance.charge.ChargeBlock;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** The warhead components (DESIGN.md §9.2-9.4): payload cores, casings and fuzes, each saying where it goes. */
public final class ComponentItems {
    static final String T_CORE = GBLang.add(GroundBurst.MODID + ".component.core", "Payload core: makes a %s");
    static final String T_CLASS = GBLang.add(GroundBurst.MODID + ".component.class", "%s");
    static final String T_CASING = GBLang.add(GroundBurst.MODID + ".component.casing", "Warhead casing for class %s payloads");
    static final String T_FUZE = GBLang.add(GroundBurst.MODID + ".component.fuze", "Fuze");
    static final String T_BENCH = GBLang.add(GroundBurst.MODID + ".component.bench", "Fitted at an Ordnance Assembly Bench");
    /** An ICBM core (Phase 11): the hall builds it whole into its ICBM, with a booster. */
    static final String T_ICBM_CORE = GBLang.add(GroundBurst.MODID + ".component.icbm_core", "ICBM core: the heart of the %s");
    static final String T_HALL = GBLang.add(GroundBurst.MODID + ".component.icbm_hall", "Fitted at a Special Weapons Assembler, with a booster");
    static final String[] T_FUZE_WHAT = {
            GBLang.add(GroundBurst.MODID + ".component.fuze.impact", "The missile's warhead goes off where it hits"),
            GBLang.add(GroundBurst.MODID + ".component.fuze.barometric", "The warhead bursts in the air, a set height over the target (10-200 blocks)")};

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

    /**
     * An ICBM core (Phase 11, DESIGN.md §16.12): an exotic warhead's heart, which only the Special Weapons Assembler
     * takes (§16.15), building it whole into its ICBM; no bench makes a warhead of it.
     */
    public static class IcbmCore extends Item {
        private final Supplier<? extends ChargeBlock> warhead;

        public IcbmCore(Properties properties, Supplier<? extends ChargeBlock> warhead) {
            super(properties);
            this.warhead = warhead;
        }

        /** The warhead its ICBM carries. */
        public ChargeBlock warhead() {
            return this.warhead.get();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(warhead()).getPath();
            tooltip.accept(grey(Component.translatable(T_ICBM_CORE, Component.translatable(GroundBurst.MODID + ".missile.name." + id))));
            tooltip.accept(Component.translatable(T_HALL).withStyle(ChatFormatting.DARK_GRAY));
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
