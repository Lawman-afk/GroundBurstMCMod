package dev.groundzero.content.superweapon;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A superweapon's ammunition (DESIGN.md §19.5): a whole missile, warhead and all, built at its faction's factory and
 * fired only by its superweapon. Drawn by its missile's own model.
 */
public class SuperweaponAmmoItem extends Item {
    private final List<String> lines;

    /** {@code lines}: its tooltip's translation keys, in order (what fires it, what it does, where it is built). */
    public SuperweaponAmmoItem(Properties properties, List<String> lines) {
        super(properties);
        this.lines = lines;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        for (int i = 0; i < this.lines.size(); i++) {
            tooltip.accept(Component.translatable(this.lines.get(i)).withStyle(i == 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
    }
}
