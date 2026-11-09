package dev.groundburst.content.command;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.Multiblocks;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Command Rocket (DESIGN.md §17.17): the Dead Hand's round, a small rocket whose payload is a transmitter: fired, it
 * climbs out of the module's tube and, as it transmits, orders the network's launchers to fire. Used on a Dead Hand
 * (its controller or any part of it), it is loaded.
 */
public class CommandRocketItem extends Item {
    private static final String TIP = GBLang.add(GroundBurst.MODID + ".tooltip.command_rocket",
            "Use on a Dead Hand to load it: fired, it orders the network's launchers to fire");
    private static final String LOADED = GBLang.add(GroundBurst.MODID + ".command_rocket.loaded", "Command rocket loaded");
    private static final String FULL = GBLang.add(GroundBurst.MODID + ".command_rocket.full", "The Dead Hand already has its rocket");
    private static final String UNFORMED = GBLang.add(GroundBurst.MODID + ".command_rocket.unformed", "Finish the Dead Hand first");

    public CommandRocketItem(Properties properties) {
        super(properties);
    }

    /** The Dead Hand a click on {@code pos} reaches (its controller, or a formed part's). */
    static @Nullable DeadHandBlockEntity target(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof DeadHandBlockEntity d) {
            return d;
        }
        MultiblockController c = Multiblocks.formedControllerOf(level, pos);
        return c != null && level.getBlockEntity(c.controllerPos()) instanceof DeadHandBlockEntity d ? d : null;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        DeadHandBlockEntity d = target(level, context.getClickedPos());
        if (d == null || player == null) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            Component message;
            if (!d.isFormed()) {
                message = Component.translatable(UNFORMED).withStyle(ChatFormatting.GRAY);
            } else if (d.load(server)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                message = Component.translatable(LOADED).withStyle(ChatFormatting.GOLD);
            } else {
                message = Component.translatable(FULL).withStyle(ChatFormatting.GRAY);
            }
            player.sendOverlayMessage(message);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(TIP).withStyle(ChatFormatting.GRAY));
    }
}
