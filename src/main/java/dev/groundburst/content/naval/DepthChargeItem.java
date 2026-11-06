package dev.groundburst.content.naval;

import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A depth charge (DESIGN.md §14.6): used, thrown (a heavy, short throw); sneaking, its pistol's depth is set. */
public class DepthChargeItem extends Item {
    static final String T_DEPTH = GBLang.add("item." + GroundBurst.MODID + ".depth_charge.depth", "Fires %s blocks under the surface, or on the bottom");
    static final String T_SET = GBLang.add("item." + GroundBurst.MODID + ".depth_charge.set", "Sneak and use to change the depth");
    static final String T_DRY = GBLang.add("item." + GroundBurst.MODID + ".depth_charge.dry", "Never fires out of water");
    static final String T_DEPTH_SET = GBLang.add("message." + GroundBurst.MODID + ".depth_charge.depth_set", "Depth charge set to %s blocks");

    public DepthChargeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            int[] depths = DepthCharge.DEPTHS;
            int now = DepthCharge.depthOf(stack), next = depths[0];
            for (int i = 0; i < depths.length; i++) {
                if (depths[i] == now) {
                    next = depths[(i + 1) % depths.length];
                }
            }
            stack.set(GBDataComponents.NAVAL_DEPTH.get(), next);
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable(T_DEPTH_SET, next));
            }
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel server) {
            DepthCharge thrown = new DepthCharge(server, player, stack);
            thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 0.6f, 1.0f);
            server.addFreshEntity(thrown);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8f, 0.6f);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(stack, 20);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(T_DEPTH, DepthCharge.depthOf(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(T_SET).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable(T_DRY).withStyle(ChatFormatting.DARK_GRAY));
    }
}
