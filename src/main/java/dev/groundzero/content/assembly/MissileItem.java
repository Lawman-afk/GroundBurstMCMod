package dev.groundzero.content.assembly;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZDataComponents;
import dev.groundzero.registry.GZLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * An assembled missile (DESIGN.md §10): what goes in a Launch Silo. Everything about it is in its
 * {@code groundzero:missile} component; its tooltip lists the warhead, body, range, guidance and hardening.
 */
public class MissileItem extends Item {
    private static final String T_EMPTY = GZLang.add(GroundZero.MODID + ".missile.tip.empty", "Assemble one at an Ordnance Assembly Bench");
    private static final String T_WARHEAD = GZLang.add(GroundZero.MODID + ".missile.tip.warhead", "Warhead: %s");
    private static final String T_RANGE = GZLang.add(GroundZero.MODID + ".missile.tip.range", "Range: %s blocks");
    private static final String T_GUIDANCE = GZLang.add(GroundZero.MODID + ".missile.tip.guidance", "Guidance: %s");
    private static final String T_HARDENED = GZLang.add(GroundZero.MODID + ".missile.tip.hardened", "Hardened against EMP");
    private static final String T_NUCLEAR = GZLang.add(GroundZero.MODID + ".missile.tip.nuclear", "Nuclear: fires from a Launch Complex or a Missile Silo");
    static final String T_NAMED = GZLang.add(GroundZero.MODID + ".missile.named", "%s Missile");

    public MissileItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static @Nullable MissileSpec spec(ItemStack stack) {
        return stack.get(GZDataComponents.MISSILE.get());
    }

    /** "Heavy Missile", by its body. */
    @Override
    public Component getName(ItemStack stack) {
        MissileSpec s = spec(stack);
        return s == null ? super.getName(stack) : Component.translatable(T_NAMED, Component.translatable(MissileParts.bodyName(s.body())));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        MissileSpec s = spec(stack);
        if (s == null) {
            tooltip.accept(Component.translatable(T_EMPTY).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.accept(Component.translatable(T_WARHEAD, s.warhead().getHoverName()).withStyle(ChatFormatting.GRAY));
        FuzeSettings fuze = s.warhead().getOrDefault(GZDataComponents.FUZE.get(), FuzeSettings.IMPACT);
        tooltip.accept(Component.literal("  ").append(WarheadItem.fuzeLine(fuze)).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable(T_RANGE, s.range()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(T_GUIDANCE, Component.translatable(MissileParts.guidanceName(s.guidance()))).withStyle(ChatFormatting.GRAY));
        if (s.hardened()) {
            tooltip.accept(Component.translatable(T_HARDENED).withStyle(ChatFormatting.AQUA));
        }
        if (s.nuclear()) {
            tooltip.accept(Component.translatable(T_NUCLEAR).withStyle(ChatFormatting.YELLOW));
        }
    }
}
