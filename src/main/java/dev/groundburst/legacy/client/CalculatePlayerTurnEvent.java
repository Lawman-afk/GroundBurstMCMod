package dev.groundburst.legacy.client;

import net.minecraftforge.eventbus.api.Event;

/**
 * NeoForge's event for how far the mouse turns the player (the Laser Designator's sight slows it): posted on the game bus
 * as 1.20.1's mouse handler reads the sensitivity (dev.groundburst.legacy.mixin.MouseHandlerMixin).
 */
public final class CalculatePlayerTurnEvent extends Event {
    private double mouseSensitivity;
    private boolean cinematicCameraEnabled;

    public CalculatePlayerTurnEvent(double mouseSensitivity, boolean cinematicCameraEnabled) {
        this.mouseSensitivity = mouseSensitivity;
        this.cinematicCameraEnabled = cinematicCameraEnabled;
    }

    public double getMouseSensitivity() {
        return this.mouseSensitivity;
    }

    public void setMouseSensitivity(double mouseSensitivity) {
        this.mouseSensitivity = mouseSensitivity;
    }

    public boolean getCinematicCameraEnabled() {
        return this.cinematicCameraEnabled;
    }

    public void setCinematicCameraEnabled(boolean cinematicCameraEnabled) {
        this.cinematicCameraEnabled = cinematicCameraEnabled;
    }
}
