package dev.groundzero.content.radiation;

import java.util.function.Consumer;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Lead Container (DESIGN.md §21.1, §21.8): a thick lead flask with nine places for radioactive items and nothing else.
 * What is inside does not reach whoever carries it ({@link Emitters#carried(ItemStack)}). Using it opens it.
 */
public class LeadContainerItem extends Item {
    public static final int SLOTS = 9;
    static final String HINT = GZLang.add(GroundZero.MODID + ".tooltip.lead_container",
            "Holds radioactive items; nothing inside reaches you. Use to open.");

    public LeadContainerItem(Properties properties) {
        super(properties.stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            ItemStack stack = player.getItemInHand(hand);
            server.openMenu(new SimpleMenuProvider((id, inventory, p) -> new LeadContainerMenu(id, inventory, hand), stack.getHoverName()),
                    buf -> buf.writeEnum(hand));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), GZSounds.LEAD_CONTAINER_OPEN.get(), SoundSource.PLAYERS, 0.8f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    /** It goes in no other container (nor into a bundle): lead inside a shulker box inside a bundle helps nobody. */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable(HINT).withColor(0xFF9AA0A8));
    }
}
