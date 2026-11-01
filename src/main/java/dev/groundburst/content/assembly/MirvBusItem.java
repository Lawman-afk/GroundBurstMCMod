package dev.groundburst.content.assembly;

import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
 * A MIRV bus (DESIGN.md §17.14): the payload a MIRV carries in its warhead's place, of its class (the Heavy bus L, the
 * Strategic bus XL). Using it opens it: its mounts take re-entry vehicles, as far as its load allows, and it keeps how
 * they spread round the aim.
 */
public class MirvBusItem extends Item {
    private static final String T_CLASS = GBLang.add(GroundBurst.MODID + ".mirv.class", "MIRV bus, %s: for a %s");
    private static final String T_HINT = GBLang.add(GroundBurst.MODID + ".mirv.hint", "Use to load it and set its spread");

    public final SizeClass size;

    public MirvBusItem(Properties properties, SizeClass size) {
        super(properties.stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .component(GBDataComponents.MIRV_SPREAD.get(), Mirv.defaultSpread(size)));
        this.size = size;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            ItemStack stack = player.getItemInHand(hand);
            server.openMenu(new SimpleMenuProvider((id, inventory, p) -> new MirvBusMenu(id, inventory, hand), stack.getHoverName()),
                    buf -> MirvBusMenu.write(buf, hand, this.size));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.6f, 1.3f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        Item body = this.size == SizeClass.XL ? GBAssembly.STRATEGIC_BODY.get() : GBAssembly.HEAVY_BODY.get();
        tooltip.accept(Component.translatable(T_CLASS, Component.translatable(this.size.label), new ItemStack(body).getHoverName())
                .withStyle(ChatFormatting.DARK_AQUA));
        Mirv.describe(stack).forEach(tooltip);
        tooltip.accept(Component.translatable(T_HINT).withStyle(ChatFormatting.DARK_GRAY));
    }
}
