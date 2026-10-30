package dev.groundburst.content.strategic;

import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A part only the ICBM Assembly Hall uses (DESIGN.md §15.2): what it is for, and where it goes. */
public class StrategicPart extends Item {
    static final String T_HALL = GBLang.add(GroundBurst.MODID + ".strategic.hall_part", "Fitted at an ICBM Assembly Hall");

    private final String tip;

    public StrategicPart(Properties properties, String tip) {
        super(properties);
        this.tip = tip;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(this.tip).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(T_HALL).withStyle(ChatFormatting.DARK_GRAY));
    }
}
