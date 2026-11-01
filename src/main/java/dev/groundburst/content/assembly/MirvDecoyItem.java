package dev.groundburst.content.assembly;

import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * An RV Decoy (DESIGN.md §17.14): a light replica of a re-entry vehicle for a MIRV bus. It falls as an RV does and
 * lands as nothing; radar and interceptors cannot tell it from a warhead, so it draws their fire.
 */
public class MirvDecoyItem extends Item {
    private static final String T_HINT = GBLang.add(GroundBurst.MODID + ".mirv.decoy_hint",
            "Load it in a MIRV bus: it falls with the warheads and draws interceptors off them");

    public MirvDecoyItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(T_HINT).withStyle(ChatFormatting.GRAY));
    }
}
