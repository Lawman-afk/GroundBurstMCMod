package dev.groundburst.port.input;

/** Minecraft 26.x's mouse button event as a screen is given it; made from 1.21.1's position and button. */
public record MouseButtonEvent(double x, double y, int button) {
    public int modifiers() {
        return 0;
    }

    public boolean hasShiftDown() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }

    public boolean hasControlDown() {
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
    }

    public boolean hasAltDown() {
        return net.minecraft.client.gui.screens.Screen.hasAltDown();
    }
}
