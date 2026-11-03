package dev.groundburst.port;

/** Minecraft 26.x's mouse cursors, which a screen may ask for; 1.21.1 keeps the arrow (GuiGraphicsExtractor.requestCursor). */
public final class CursorTypes {
    public static final Object ARROW = "arrow";
    public static final Object IBEAM = "ibeam";
    public static final Object CROSSHAIR = "crosshair";
    public static final Object POINTING_HAND = "pointing_hand";
    public static final Object RESIZE_NS = "resize_ns";
    public static final Object RESIZE_EW = "resize_ew";
    public static final Object RESIZE_ALL = "resize_all";
    public static final Object NOT_ALLOWED = "not_allowed";

    private CursorTypes() {}
}
