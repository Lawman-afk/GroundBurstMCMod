package dev.groundburst.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.windows.User32;

/**
 * The dev test runs (the photographers, the benches, the showroom: build.gradle sets {@code groundburst.keepMouse}) leave the
 * person at the computer their mouse and their windows: the game's window is kept from taking the focus when shown and
 * set behind the other windows, and the game never grabs the mouse ({@code mixin/DevMouseMixin}). This branch's window
 * is GLFW's (main's, on 26.3, is SDL's: main's own DevRuns). Players' games never set it.
 */
public final class DevRuns {
    public static final boolean KEEP_MOUSE = Boolean.getBoolean("groundburst.keepMouse");
    /** Client ticks it goes on setting the window behind (the game may raise it as it loads its first world). */
    private static final int BEHIND_FOR = 200;
    private static int ticks;

    private DevRuns() {}

    /** Called as the client starts. */
    public static void register() {
        if (!KEEP_MOUSE) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            if (ticks < BEHIND_FOR && ticks++ % 20 == 0) {
                behind(Minecraft.getInstance());
            }
        });
    }

    /** The game's window behind every other, not activated, and not focused when shown again (Windows). */
    private static void behind(Minecraft mc) {
        long window = mc.getWindow().handle();
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        if (net.minecraft.util.Util.getPlatform() == net.minecraft.util.Util.OS.WINDOWS) {
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(window);
            if (hwnd != 0) {
                User32.SetWindowPos(null, hwnd, User32.HWND_BOTTOM, 0, 0, 0, 0, User32.SWP_NOMOVE | User32.SWP_NOSIZE | User32.SWP_NOACTIVATE);
            }
        }
    }
}
