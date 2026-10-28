package dev.groundburst.content.power;

import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A rechargeable FE cell (DESIGN.md §26.2): charged in the Battery Charger (or any mod's charger, through the item
 * energy capability), and the power source later gear draws from. Shows its charge as a bar and in the tooltip.
 */
public class BatteryItem extends Item {
    public static final int CAPACITY = 50_000;
    public static final int TRANSFER = 1_000;
    public static final String CHARGE = GBLang.add(GroundBurst.MODID + ".tooltip.charge", "Charge: %s / %s FE");

    public BatteryItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int stored(ItemStack stack) {
        return stack.getOrDefault(GBDataComponents.ENERGY.get(), 0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * stored(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.08f + 0.22f * stored(stack) / CAPACITY, 0.85f, 1f);   // amber when low, green when full
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(chargeLine(stored(stack), CAPACITY));
    }

    public static Component chargeLine(int stored, int capacity) {
        java.text.NumberFormat f = java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT);
        return Component.translatable(CHARGE, f.format(stored), f.format(capacity)).withColor(0xFF9AA0A8);
    }
}
