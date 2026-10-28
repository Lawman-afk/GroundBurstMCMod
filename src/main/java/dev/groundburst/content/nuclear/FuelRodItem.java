package dev.groundburst.content.nuclear;

import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A uranium fuel rod: once it has burned in a reactor, a bar under it shows the fuel it has left (see {@link FuelBurnup}). */
public class FuelRodItem extends Item {
    public static final String BURNUP = GBLang.add(GroundBurst.MODID + ".fuel_rod.burnup", "Burned: %s%%");

    public FuelRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.has(GBDataComponents.FUEL_BURNUP.get());
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        FuelBurnup burnup = stack.get(GBDataComponents.FUEL_BURNUP.get());
        return burnup == null ? MAX_BAR_WIDTH : Math.round(MAX_BAR_WIDTH * (1 - burnup.fraction()));
    }

    /** Green while fresh, through yellow, to red near the end (as a tool's wear). */
    @Override
    public int getBarColor(ItemStack stack) {
        FuelBurnup burnup = stack.get(GBDataComponents.FUEL_BURNUP.get());
        float left = burnup == null ? 1 : 1 - burnup.fraction();
        return Mth.hsvToRgb(left / 3f, 1f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        FuelBurnup burnup = stack.get(GBDataComponents.FUEL_BURNUP.get());
        if (burnup != null) {
            tooltip.accept(Component.translatable(BURNUP, (int) Math.floor(burnup.fraction() * 100)).withStyle(ChatFormatting.GRAY));
        }
    }
}
