package dev.groundzero.content.ordnance.charge;

import java.util.function.Consumer;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/** The Arming Key (DESIGN.md §9.4): sneak-use it on a placed charge to arm it, and again to make it safe. */
public class ArmingKeyItem extends Item {
    static final String ARMED = GZLang.add(GroundZero.MODID + ".charge.armed", "Armed");
    static final String ARMED_TIMER = GZLang.add(GroundZero.MODID + ".charge.armed_timer", "Armed: %s s");
    static final String SAFE = GZLang.add(GroundZero.MODID + ".charge.safe", "Safe");
    static final String MUST_DEFUSE = GZLang.add(GroundZero.MODID + ".charge.must_defuse", "It is counting down: cut the right wire to stop it");
    private static final String TIP = GZLang.add(GroundZero.MODID + ".tooltip.arming_key", "Sneak-use on a charge to arm it or make it safe");

    public ArmingKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ChargeBlockEntity be = ChargeBlock.at(context.getLevel(), context.getClickedPos());
        if (be == null || player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel level) {
            if (!be.armed()) {
                be.arm(level, player);
                player.sendOverlayMessage(be.counting() ? Component.translatable(ARMED_TIMER, be.timer()).withStyle(ChatFormatting.RED)
                        : Component.translatable(ARMED).withStyle(ChatFormatting.RED));
            } else if (be.disarm(level)) {
                player.sendOverlayMessage(Component.translatable(SAFE).withStyle(ChatFormatting.GREEN));
            } else {
                player.sendOverlayMessage(Component.translatable(MUST_DEFUSE).withStyle(ChatFormatting.GOLD));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(TIP).withStyle(ChatFormatting.GRAY));
    }
}
