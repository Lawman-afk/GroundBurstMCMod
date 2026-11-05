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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A sea mine to lay (DESIGN.md §14.6): used on water it is laid where the player looks (from under water, just ahead of
 * them). A moored one's depth is set by using it sneaking.
 */
public class SeaMineItem extends Item {
    static final String T_DEPTH = GBLang.add("item." + GroundBurst.MODID + ".sea_mine.depth", "Depth: its top %s blocks under the surface");
    static final String T_AWASH = GBLang.add("item." + GroundBurst.MODID + ".sea_mine.awash", "Depth: awash, just under the surface (boats strike it)");
    static final String T_SET = GBLang.add("item." + GroundBurst.MODID + ".sea_mine.set", "Sneak and use to change the depth");
    static final String T_LAY = GBLang.add("item." + GroundBurst.MODID + ".sea_mine.lay", "Use on water to lay it: live 5 seconds after it settles");
    static final String T_DRIFT = GBLang.add("item." + GroundBurst.MODID + ".sea_mine.drift", "Floats awash and drifts; harmless after an hour adrift");
    static final String T_NOT_WATER = GBLang.add("message." + GroundBurst.MODID + ".sea_mine.not_water", "Lay it on water");
    static final String T_DEPTH_SET = GBLang.add("message." + GroundBurst.MODID + ".sea_mine.depth_set", "Mine depth: %s blocks under the surface");
    static final String T_AWASH_SET = GBLang.add("message." + GroundBurst.MODID + ".sea_mine.awash_set", "Mine depth: awash");

    private final boolean moored;

    public SeaMineItem(Properties properties, boolean moored) {
        super(properties);
        this.moored = moored;
    }

    public boolean moored() {
        return this.moored;
    }

    public static int depthOf(ItemStack stack) {
        return stack.getOrDefault(GBDataComponents.NAVAL_DEPTH.get(), 0);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (this.moored && player.isShiftKeyDown()) {
            int[] depths = SeaMine.DEPTHS;
            int now = depthOf(stack), next = depths[0];
            for (int i = 0; i < depths.length; i++) {
                if (depths[i] == now) {
                    next = depths[(i + 1) % depths.length];
                }
            }
            stack.set(GBDataComponents.NAVAL_DEPTH.get(), next);
            if (!level.isClientSide()) {
                player.displayClientMessage(next == 0 ? Component.translatable(T_AWASH_SET) : Component.translatable(T_DEPTH_SET, next), true);
            }
            return dev.groundburst.port.Results.holder(InteractionResult.SUCCESS, player, hand);
        }
        Vec3 at = layingPoint(level, player);
        if (at == null) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(T_NOT_WATER), true);
            }
            return dev.groundburst.port.Results.holder(InteractionResult.FAIL, player, hand);
        }
        if (level instanceof ServerLevel server) {
            SeaMine.lay(server, at, this.moored, depthOf(stack), player);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.9f, 0.8f);
        }
        player.getCooldowns().addCooldown(stack.getItem(), 10);
        stack.consume(1, player);
        return dev.groundburst.port.Results.holder(InteractionResult.SUCCESS, player, hand);
    }

    /** Where a player lays a mine: the water's surface they look at (within reach), or under water just ahead of them; null if neither. */
    static Vec3 layingPoint(Level level, Player player) {
        if (player.isUnderWater()) {
            Vec3 ahead = player.getEyePosition().add(player.getLookAngle().scale(1.5));
            return Water.water(level, net.minecraft.core.BlockPos.containing(ahead)) ? ahead : null;
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() != HitResult.Type.BLOCK || !Water.water(level, hit.getBlockPos())) {
            return null;
        }
        return hit.getLocation().add(0, -0.2, 0);
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip_list, net.minecraft.world.item.TooltipFlag flag) { java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip = tooltip_list::add;
        if (this.moored) {
            int depth = depthOf(stack);
            tooltip.accept((depth == 0 ? Component.translatable(T_AWASH) : Component.translatable(T_DEPTH, depth)).withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable(T_SET).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.accept(Component.translatable(T_DRIFT).withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable(T_LAY).withStyle(ChatFormatting.DARK_GRAY));
    }
}
