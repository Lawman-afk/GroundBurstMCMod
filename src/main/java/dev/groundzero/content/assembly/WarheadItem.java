package dev.groundzero.content.assembly;

import java.util.function.Consumer;

import dev.groundzero.GroundZero;
import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.registry.GZDataComponents;
import dev.groundzero.registry.GZLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A warhead's item: it carries its fuze (fitted at the Assembly Bench) and says which, with its setting and class. */
public class WarheadItem extends BlockItem {
    private static final String[] T_FUZE_LINE = {
            GZLang.add(GroundZero.MODID + ".warhead.fuze.impact", "Fuze: Impact"),
            GZLang.add(GroundZero.MODID + ".warhead.fuze.timed", "Fuze: Timed, %s s"),
            GZLang.add(GroundZero.MODID + ".warhead.fuze.proximity", "Fuze: Proximity, %s blocks"),
            GZLang.add(GroundZero.MODID + ".warhead.fuze.remote", "Fuze: Remote, frequency %s"),
            GZLang.add(GroundZero.MODID + ".warhead.fuze.barometric", "Fuze: Barometric, bursts %s blocks up")};

    public WarheadItem(ChargeBlock block, Properties properties) {
        super(block, properties.component(GZDataComponents.FUZE.get(), FuzeSettings.IMPACT));
    }

    public static Component fuzeLine(FuzeSettings fuze) {
        return Component.translatable(T_FUZE_LINE[fuze.fuze().ordinal()], fuze.value());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(fuzeLine(stack.getOrDefault(GZDataComponents.FUZE.get(), FuzeSettings.IMPACT)).copy().withStyle(ChatFormatting.GRAY));
        SizeClass size = WarheadAssembly.sizeOf((ChargeBlock) getBlock());
        if (size != null) {
            tooltip.accept(Component.translatable(size.label).withStyle(ChatFormatting.GRAY));
        }
    }
}
