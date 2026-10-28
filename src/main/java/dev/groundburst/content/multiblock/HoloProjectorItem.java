package dev.groundburst.content.multiblock;

import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Holo-Projector (DESIGN.md §23.1). While held it projects every unfinished structure within range: ghost blocks
 * where pieces are missing, red outlines on wrong blocks. Using it steps through the layers.
 */
public class HoloProjectorItem extends Item {
    public static final int RANGE = 32;
    public static final String TOOLTIP = GBLang.add(GroundBurst.MODID + ".holo_projector.tooltip",
            "Hold near an unfinished multiblock to project it. Use to step through its layers.");

    public HoloProjectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            MultiblockClientHooks.stepLayer.run();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(TOOLTIP).withStyle(ChatFormatting.GRAY));
    }
}
