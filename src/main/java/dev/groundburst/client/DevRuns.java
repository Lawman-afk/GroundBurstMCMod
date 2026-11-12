package dev.groundburst.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.windows.User32;

/**
 * The dev test runs (the photographers, the benches, the world survey, the showroom: build.gradle sets
 * {@code groundburst.keepMouse}) leave the person at the computer their mouse and their windows: the game's window shows
 * without taking the focus and is put behind the other windows, and the game never grabs the mouse
 * ({@code mixin/DevMouseMixin}; the runs turn the camera themselves). 26.3's window is SDL's: it is told before it shows
 * not to take the focus when it does, and, once shown, set behind. Players' games never set it.
 */
public final class DevRuns {
    public static final boolean KEEP_MOUSE = Boolean.getBoolean("groundburst.keepMouse");
    /** Client ticks it goes on setting the window behind (the game may raise it as it loads its first world). */
    private static final int BEHIND_FOR = 200;
    private static int ticks;

    private DevRuns() {}

    /** Called as the client starts, before the window shows. */
    public static void register() {
        if (!KEEP_MOUSE) {
            return;
        }
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_WINDOW_ACTIVATE_WHEN_SHOWN, "0");
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_WINDOW_ACTIVATE_WHEN_RAISED, "0");
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            if (ticks < BEHIND_FOR && ticks++ % 20 == 0) {
                behind(Minecraft.getInstance());
            }
        });
    }

    /** The game's window behind every other, not activated (Windows). */
    private static void behind(Minecraft mc) {
        if (net.minecraft.util.Util.getPlatform() != net.minecraft.util.Util.OS.WINDOWS) {
            return;
        }
        long window = mc.getWindow().handle();
        int props = SDLVideo.SDL_GetWindowProperties(window);
        long hwnd = SDLProperties.SDL_GetPointerProperty(props, SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
        if (hwnd != 0) {
            User32.SetWindowPos(null, hwnd, User32.HWND_BOTTOM, 0, 0, 0, 0, User32.SWP_NOMOVE | User32.SWP_NOSIZE | User32.SWP_NOACTIVATE);
        }
    }
}
