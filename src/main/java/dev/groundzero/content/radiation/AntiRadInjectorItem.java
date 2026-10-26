package dev.groundzero.content.radiation;

import java.util.function.Consumer;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Anti-Rad Injector (DESIGN.md §21.6, §21.8): an autoinjector that takes {@link #DOSE} rad off at once, at the cost of
 * a minute's weakness (its consumable component).
 */
public class AntiRadInjectorItem extends Item {
    public static final float DOSE = 400;
    static final String HINT = GZLang.add(GroundZero.MODID + ".tooltip.anti_rad", "Takes 400 rad off at once; a minute of weakness after");

    public AntiRadInjectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            Radiation.addDose(entity, -DOSE);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable(HINT).withColor(0xFF9AA0A8));
    }
}
