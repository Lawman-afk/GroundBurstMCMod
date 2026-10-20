package dev.groundzero.client.manual;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import dev.groundzero.GroundZero;
import dev.groundzero.content.manual.GZManual;
import dev.groundzero.content.manual.ManualStrings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client wiring for the Field Manual: content loading, the 3D structure view, opening the book from its item, and
 * the tooltip key that opens the entry for the item under the mouse in any screen.
 */
public final class ManualHotkey {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(GroundZero.id("groundzero"));
    public static final KeyMapping KEY = new KeyMapping(ManualStrings.KEY, KeyConflictContext.GUI, InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY);

    private ManualHotkey() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(CATEGORY);
            e.register(KEY);
        });
        modBus.addListener((RegisterPictureInPictureRenderersEvent e) -> e.register(StructureView.State.class, StructureView.Renderer::new));
        modBus.addListener((AddClientReloadListenersEvent e) -> e.addListener(GroundZero.id("field_manual"), new Manual.Loader()));
        NeoForge.EVENT_BUS.addListener(ManualHotkey::tooltip);
        NeoForge.EVENT_BUS.addListener(ManualHotkey::keyInScreen);
        GZManual.open = ManualHotkey::open;
        ManualPhotographer.register();
    }

    public static boolean matches(KeyEvent event) {
        return !KEY.isUnbound() && KEY.matches(event);
    }

    /** Opens the book (at an entry, or where it was left), returning to the current screen when it closes. */
    public static void open(@Nullable String entry) {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.gui.screen() instanceof FieldManualScreen ? null : mc.gui.screen();
        mc.gui.setScreen(new FieldManualScreen(current, entry));
    }

    private static void tooltip(ItemTooltipEvent event) {
        if (KEY.isUnbound() || Manual.entryFor(event.getItemStack().getItem()) == null || event.getItemStack().is(GZManual.FIELD_MANUAL.get())) {
            return;
        }
        if (Minecraft.getInstance().gui.screen() instanceof FieldManualScreen) {
            return;                                                   // the book says "Click" instead
        }
        event.getToolTip().add(Component.translatable(ManualStrings.TOOLTIP, KEY.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void keyInScreen(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || !matches(event.getKeyEvent())
                || screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox) {
            return;
        }
        Slot slot = screen.getHoveredSlot();
        if (slot == null || !slot.hasItem()) {
            return;
        }
        Manual.Entry e = Manual.entryFor(slot.getItem().getItem());
        if (e != null) {
            open(e.id());
            event.setCanceled(true);
        }
    }
}
