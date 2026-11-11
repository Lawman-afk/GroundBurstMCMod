package dev.groundburst.legacy.client;

import net.minecraft.resources.ResourceLocation;

/**
 * NeoForge 1.21's names of the game's own HUD layers, as Forge 1.20.1's overlays are named (its camera overlays, one layer
 * in 1.21, are four in 1.20.1: a layer above them goes above the last, the portal's).
 */
public final class VanillaGuiLayers {
    private VanillaGuiLayers() {}

    public static final ResourceLocation CAMERA_OVERLAYS = id("portal");
    public static final ResourceLocation CROSSHAIR = id("crosshair");
    public static final ResourceLocation HOTBAR = id("hotbar");
    public static final ResourceLocation JUMP_METER = id("jump_bar");
    public static final ResourceLocation EXPERIENCE_BAR = id("experience_bar");
    public static final ResourceLocation PLAYER_HEALTH = id("player_health");
    public static final ResourceLocation ARMOR_LEVEL = id("armor_level");
    public static final ResourceLocation FOOD_LEVEL = id("food_level");
    public static final ResourceLocation VEHICLE_HEALTH = id("mount_health");
    public static final ResourceLocation AIR_LEVEL = id("air_level");
    public static final ResourceLocation SELECTED_ITEM_NAME = id("item_name");
    public static final ResourceLocation EFFECTS = id("potion_icons");
    public static final ResourceLocation BOSS_OVERLAY = id("boss_event_progress");
    public static final ResourceLocation SLEEP_OVERLAY = id("sleep_fade");
    public static final ResourceLocation DEBUG_OVERLAY = id("debug_text");
    public static final ResourceLocation SCOREBOARD_SIDEBAR = id("scoreboard");
    public static final ResourceLocation OVERLAY_MESSAGE = id("record_overlay");
    public static final ResourceLocation TITLE = id("title_text");
    public static final ResourceLocation CHAT = id("chat_panel");
    public static final ResourceLocation TAB_LIST = id("player_list");
    public static final ResourceLocation SUBTITLE_OVERLAY = id("subtitles");

    private static ResourceLocation id(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
