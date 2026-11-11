package dev.groundburst.content.launch;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.Multiblocks;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Launch Codes (DESIGN.md §17.12): a sealed codes card. Used on a Launch Control Station it takes that station's network;
 * then used on a launcher (its controller or any formed part) or a Radar Array it links it to the network, and
 * sneaking it unlinks it. Sneak-used on another station, that station joins the network. Void once the station has
 * issued new codes.
 */
public class LaunchCodesItem extends Item {
    private static final String TIP_CODE = GBLang.add(GroundBurst.MODID + ".tooltip.codes", "Network %s");
    private static final String TIP_BLANK = GBLang.add(GroundBurst.MODID + ".tooltip.codes_blank", "Blank: use on a Launch Control Station");
    private static final String TIP_USE = GBLang.add(GroundBurst.MODID + ".tooltip.codes_use",
            "Use on a launcher or Radar Array to link it; sneak to unlink it");
    private static final String TIP_JOIN = GBLang.add(GroundBurst.MODID + ".tooltip.codes_join", "Sneak-use on another station to make it join");
    private static final String TAKEN = GBLang.add(GroundBurst.MODID + ".codes.taken", "Launch codes for network %s");
    private static final String SAME = GBLang.add(GroundBurst.MODID + ".codes.same", "These are this station's codes (%s)");
    private static final String OTHER = GBLang.add(GroundBurst.MODID + ".codes.other", "These codes are for network %s: sneak to make this station join it");
    private static final String JOINED = GBLang.add(GroundBurst.MODID + ".codes.joined", "This station joined network %s");
    private static final String VOID = GBLang.add(GroundBurst.MODID + ".codes.void", "These codes are void: the network has new codes");
    private static final String ELSEWHERE = GBLang.add(GroundBurst.MODID + ".codes.elsewhere",
            "Network %s is in another dimension: its launchers must be in its station's");
    private static final String BLANK = GBLang.add(GroundBurst.MODID + ".codes.blank", "Blank codes: use them on a Launch Control Station first");
    private static final String LINKED = GBLang.add(GroundBurst.MODID + ".codes.linked", "Linked to network %s (#%s)");
    private static final String MOVED = GBLang.add(GroundBurst.MODID + ".codes.moved", "Moved to network %s (#%s)");
    private static final String ALREADY = GBLang.add(GroundBurst.MODID + ".codes.already", "Already on network %s (#%s)");
    private static final String FULL = GBLang.add(GroundBurst.MODID + ".codes.full", "Network %s is full (%s)");
    private static final String UNLINKED = GBLang.add(GroundBurst.MODID + ".codes.unlinked", "Unlinked from network %s");
    private static final String NOT_ON = GBLang.add(GroundBurst.MODID + ".codes.not_on", "Not on network %s");

    public LaunchCodesItem(Properties properties) {
        super(properties);
    }

    /** The code a card carries (empty: blank). */
    public static String code(ItemStack stack) {
        return stack.getOrDefault(GBDataComponents.LAUNCH_CODES.get(), "");
    }

    /** What a click on {@code pos} reaches: the controller's block entity (of a formed part's structure too), or the block's own. */
    public static @Nullable BlockEntity target(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof LaunchSite || be instanceof RadarArrayBlockEntity || be instanceof LaunchStationBlockEntity) {
            return be;
        }
        MultiblockController c = Multiblocks.formedControllerOf(level, pos);
        if (c != null) {
            BlockEntity owner = level.getBlockEntity(c.controllerPos());
            if (owner instanceof LaunchSite || owner instanceof RadarArrayBlockEntity || owner instanceof LaunchStationBlockEntity) {
                return owner;
            }
        }
        return null;
    }

    /** Before the block itself is used (a launcher would open its screen): link, unlink, take codes, join. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockEntity be = target(level, context.getClickedPos());
        if (be == null || player == null) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            Component message = use(server, stack, be, player.isSecondaryUseActive());
            player.sendOverlayMessage(message);
            server.playSound(null, context.getClickedPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.6f);
        }
        return InteractionResult.SUCCESS;
    }

    /** What the card does to {@code be} (a station, a launcher, a radar); the message for its user. */
    public static Component use(ServerLevel level, ItemStack stack, BlockEntity be, boolean sneaking) {
        LaunchNetwork nets = LaunchNetwork.of(level);
        String code = code(stack);
        LaunchNetwork.Network card = nets.byCode(code);
        if (be instanceof LaunchStationBlockEntity station) {
            LaunchNetwork.Network own = station.network(level);
            if (code.isEmpty() || (card == null && sneaking)) {
                if (own == null) {
                    return Component.translatable(VOID).withStyle(ChatFormatting.RED);
                }
                stack.set(GBDataComponents.LAUNCH_CODES.get(), own.code());
                return Component.translatable(TAKEN, own.code()).withStyle(ChatFormatting.GOLD);
            }
            if (card == null) {
                return Component.translatable(VOID).withStyle(ChatFormatting.RED);
            }
            if (card == own) {
                return Component.translatable(SAME, card.code()).withStyle(ChatFormatting.GRAY);
            }
            if (!sneaking) {
                return Component.translatable(OTHER, card.code()).withStyle(ChatFormatting.YELLOW);
            }
            station.join(level, card.id);
            return Component.translatable(JOINED, card.code()).withStyle(ChatFormatting.GOLD);
        }
        if (code.isEmpty()) {
            return Component.translatable(BLANK).withStyle(ChatFormatting.GRAY);
        }
        if (card == null) {
            for (ServerLevel other : level.getServer().getAllLevels()) {
                LaunchNetwork there = other == level ? null : LaunchNetwork.ifAny(other);
                if (there != null && there.byCode(code) != null) {
                    return Component.translatable(ELSEWHERE, code).withStyle(ChatFormatting.YELLOW);
                }
            }
            return Component.translatable(VOID).withStyle(ChatFormatting.RED);
        }
        BlockPos pos = be.getBlockPos();
        if (sneaking) {
            if (nets.networkOf(pos) != card) {
                return Component.translatable(NOT_ON, card.code()).withStyle(ChatFormatting.GRAY);
            }
            nets.unlink(level, pos);
            return Component.translatable(UNLINKED, card.code()).withStyle(ChatFormatting.YELLOW);
        }
        LaunchNetwork.LinkResult r = nets.link(level, card.id, be);
        int number = card.members().stream().map(m -> m.pos).toList().indexOf(pos) + 1;
        return switch (r) {
            case LINKED -> Component.translatable(LINKED, card.code(), number).withStyle(ChatFormatting.GREEN);
            case MOVED -> Component.translatable(MOVED, card.code(), number).withStyle(ChatFormatting.GREEN);
            case ALREADY -> Component.translatable(ALREADY, card.code(), number).withStyle(ChatFormatting.GRAY);
            case FULL -> Component.translatable(FULL, card.code(), LaunchNetwork.MAX_MEMBERS).withStyle(ChatFormatting.RED);
            case NOT_A_MEMBER -> Component.translatable(VOID).withStyle(ChatFormatting.RED);
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        String code = code(stack);
        if (code.isEmpty()) {
            tooltip.accept(Component.translatable(TIP_BLANK).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(Component.translatable(TIP_CODE, code).withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable(TIP_USE).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(TIP_JOIN).withStyle(ChatFormatting.GRAY));
    }
}
