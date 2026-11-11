package dev.groundburst.client.manual;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.manual.GBManual;
import dev.groundburst.content.manual.ManualStrings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import dev.groundburst.port.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.eventbus.api.IEventBus;

import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;

/**
 * Client wiring for the Field Manual: content loading, the 3D structure view, opening the book from its item, and
 * the key (H by default) that opens the entry for the item under the mouse in any screen. Items carry no tooltip
 * line for it: the manual's first entry explains the key.
 */
public final class ManualHotkey {
    public static final String CATEGORY = dev.groundburst.port.input.KeyEvent.category(GroundBurst.id("groundburst"));
    public static final KeyMapping KEY = new KeyMapping(ManualStrings.KEY, KeyConflictContext.GUI, InputConstants.Type.KEYSYM, InputConstants.KEY_H, CATEGORY);

    private ManualHotkey() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(KEY);
        });
        modBus.addListener((net.minecraftforge.client.event.RegisterClientReloadListenersEvent e) -> e.registerReloadListener(new Manual.Loader()));
        MinecraftForge.EVENT_BUS.addListener(ManualHotkey::keyInScreen);
        GBManual.open = ManualHotkey::open;
    }

    public static boolean matches(KeyEvent event) {
        return !KEY.isUnbound() && KEY.matches(event.key(), event.scancode());
    }

    /** Opens the book (at an entry, or where it was left), returning to the current screen when it closes. */
    public static void open(@Nullable String entry) {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen instanceof FieldManualScreen ? null : mc.screen;
        mc.setScreen(new FieldManualScreen(current, entry));
    }

    private static void keyInScreen(ScreenEvent.KeyPressed.Pre event) {
        if (!(((Object) event.getScreen()) instanceof AbstractContainerScreen<?> screen) || !matches(new dev.groundburst.port.input.KeyEvent(event.getKeyCode(), event.getScanCode(), event.getModifiers()))
                || screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
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
