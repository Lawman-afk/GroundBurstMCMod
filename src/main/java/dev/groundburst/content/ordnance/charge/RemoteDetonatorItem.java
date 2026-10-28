package dev.groundburst.content.ordnance.charge;

import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Remote Detonator (DESIGN.md §9.4): press it to set off every armed charge on its frequency within 256 blocks
 * (those on a timer start counting down). Sneak-use it on a charge to take that charge's frequency, or in the air to
 * type one in.
 */
public class RemoteDetonatorItem extends Item {
    private static final String TIP_FREQ = GBLang.add(GroundBurst.MODID + ".tooltip.detonator_frequency", "Frequency: %s");
    private static final String TIP_USE = GBLang.add(GroundBurst.MODID + ".tooltip.detonator", "Use to fire; sneak-use on a charge to link to it, in the air to set the frequency");
    static final String LINKED = GBLang.add(GroundBurst.MODID + ".detonator.linked", "Linked: frequency %s");
    static final String FIRED = GBLang.add(GroundBurst.MODID + ".detonator.fired", "%s charge(s) triggered");
    static final String NOTHING = GBLang.add(GroundBurst.MODID + ".detonator.nothing", "No armed charges on frequency %s in range");

    /** Client hook: opens the frequency screen for the detonator in a hand (set by the client). */
    public static Consumer<InteractionHand> frequencyHook = h -> {};

    public RemoteDetonatorItem(Properties properties) {
        super(properties);
    }

    public static int frequency(ItemStack stack) {
        return stack.getOrDefault(GBDataComponents.FREQUENCY.get(), 0);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ChargeBlockEntity be = ChargeBlock.at(context.getLevel(), context.getClickedPos());
        if (be == null || player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide()) {
            context.getItemInHand().set(GBDataComponents.FREQUENCY.get(), be.frequency());
            player.sendOverlayMessage(Component.translatable(LINKED, be.frequency()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide()) {
                frequencyHook.accept(hand);
            }
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel server) {
            int f = frequency(stack);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), GBOrdnance.DETONATOR_CLICK.get(), SoundSource.PLAYERS, 0.8f, 1f);
            int n = Charges.remote(server, player.getEyePosition(), f, player);
            player.sendOverlayMessage(n > 0 ? Component.translatable(FIRED, n).withStyle(ChatFormatting.RED)
                    : Component.translatable(NOTHING, f).withStyle(ChatFormatting.GRAY));
        }
        player.getCooldowns().addCooldown(stack, 10);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(TIP_FREQ, frequency(stack)).withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable(TIP_USE).withStyle(ChatFormatting.GRAY));
    }
}
