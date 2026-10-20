package dev.groundzero.content.manual;

import java.util.function.Consumer;

import dev.groundzero.GroundZero;
import dev.groundzero.config.GZServerConfig;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZTab;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** The Field Manual item (DESIGN.md §24.2), and handing one to each player on their first join. */
public final class GZManual {
    /** Set by the client: opens the manual at an entry (null: where it was left). */
    public static Consumer<String> open = entry -> {};
    private static final String GIVEN = GroundZero.MODID + ":manual_given";

    public static final DeferredItem<FieldManualItem> FIELD_MANUAL = GZItems.register("field_manual", "Field Manual GZ-1", GZTab.MACHINES,
            FieldManualItem::new, p -> p.stacksTo(1));

    private GZManual() {}

    public static void init() {
        ManualStrings.init();
        NeoForge.EVENT_BUS.addListener(GZManual::loggedIn);
    }

    private static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!GZServerConfig.GIVE_MANUAL.get() || player.getPersistentData().getBooleanOr(GIVEN, false)) {
            return;
        }
        player.getPersistentData().putBoolean(GIVEN, true);
        player.getInventory().placeItemBackInInventory(new ItemStack(FIELD_MANUAL.get()), net.minecraft.util.Prediction.SERVER_ONLY);
    }

    public static final class FieldManualItem extends Item {
        public FieldManualItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level.isClientSide()) {
                open.accept(null);
            }
            return InteractionResult.SUCCESS;
        }
    }
}
