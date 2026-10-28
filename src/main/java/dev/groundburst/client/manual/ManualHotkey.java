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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client wiring for the Field Manual: content loading, the 3D structure view, opening the book from its item, and
 * the key (H by default) that opens the entry for the item under the mouse in any screen. Items carry no tooltip
 * line for it: the manual's first entry explains the key.
 */
public final class ManualHotkey {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(GroundBurst.id("groundburst"));
    public static final KeyMapping KEY = new KeyMapping(ManualStrings.KEY, KeyConflictContext.GUI, InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY);

    private ManualHotkey() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(CATEGORY);
            e.register(KEY);
        });
        modBus.addListener((RegisterPictureInPictureRenderersEvent e) -> e.register(StructureView.State.class, StructureView.Renderer::new));
        modBus.addListener((AddClientReloadListenersEvent e) -> e.addListener(GroundBurst.id("field_manual"), new Manual.Loader()));
        NeoForge.EVENT_BUS.addListener(ManualHotkey::keyInScreen);
        GBManual.open = ManualHotkey::open;
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
