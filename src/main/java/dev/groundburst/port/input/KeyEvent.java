package dev.groundburst.port.input;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.screens.Screen;

/** Minecraft 26.x's key event as a screen is given it; made from 1.21.1's key, scan code and modifiers. */
public record KeyEvent(int key, int scancode, int modifiers) {
    public boolean isEscape() {
        return this.key == GLFW.GLFW_KEY_ESCAPE;
    }

    public boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }

    public boolean hasControlDown() {
        return Screen.hasControlDown();
    }
}
