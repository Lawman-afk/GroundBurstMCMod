package dev.groundburst.content.radiation;

import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBSounds;
import dev.groundburst.legacy.component.DataComponents;
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
import dev.groundburst.legacy.component.ItemContainerContents;

import net.minecraft.world.level.Level;

/**
 * The Lead Container (DESIGN.md §21.1, §21.8): a thick lead flask with nine places for radioactive items and nothing else.
 * What is inside does not reach whoever carries it ({@link Emitters#carried(ItemStack)}). Using it opens it.
 */
public class LeadContainerItem extends Item {
    public static final int SLOTS = 9;
    static final String HINT = GBLang.add(GroundBurst.MODID + ".tooltip.lead_container",
            "Holds radioactive items; nothing inside reaches you. Use to open.");

    public LeadContainerItem(Properties properties) {
        super(dev.groundburst.legacy.component.Comps.withDefault(properties.stacksTo(1), DataComponents.CONTAINER, ItemContainerContents.EMPTY));
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (((Object) player) instanceof ServerPlayer server) {
            ItemStack stack = player.getItemInHand(hand);
            net.minecraftforge.network.NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) server, new SimpleMenuProvider((id, inventory, p) -> new LeadContainerMenu(id, inventory, hand), stack.getHoverName()),
                    buf -> buf.writeEnum(hand));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), GBSounds.LEAD_CONTAINER_OPEN.get(), SoundSource.PLAYERS, 0.8f, 1f);
        }
        return dev.groundburst.port.Results.holder(InteractionResult.SUCCESS, player, hand);
    }

    /** It goes in no other container (nor into a bundle): lead inside a shulker box inside a bundle helps nobody. */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level context_level, java.util.List<net.minecraft.network.chat.Component> builder_list, net.minecraft.world.item.TooltipFlag flag) { dev.groundburst.legacy.TooltipContext context = dev.groundburst.legacy.TooltipContext.of(context_level); java.util.function.Consumer<net.minecraft.network.chat.Component> builder = builder_list::add;
        builder.accept(Component.translatable(HINT).withStyle(s_ -> s_.withColor(0xFF9AA0A8)));
    }
}
