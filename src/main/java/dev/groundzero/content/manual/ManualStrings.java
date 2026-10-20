package dev.groundzero.content.manual;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;

/** Translation keys for the Field Manual's screen, tooltip line and key (the content itself is in the manual files). */
public final class ManualStrings {
    private static final String P = GroundZero.MODID + ".manual.";

    public static final String TITLE = GZLang.add(P + "title", "FIELD MANUAL");
    public static final String SERIAL = GZLang.add(P + "serial", "GZ-1");
    public static final String TAGLINE = GZLang.add(P + "tagline", "Ordnance, industry and the means to power them.");
    public static final String SEARCH = GZLang.add(P + "search", "Search the manual");
    public static final String RESULTS = GZLang.add(P + "results", "Results");
    public static final String NO_RESULTS = GZLang.add(P + "no_results", "Nothing found.");
    public static final String CONTENTS = GZLang.add(P + "contents", "Contents");
    public static final String ENTRIES = GZLang.add(P + "entries", "Entries");
    public static final String PREVIOUS = GZLang.add(P + "previous", "Previous page");
    public static final String NEXT = GZLang.add(P + "next", "Next page");
    public static final String BACK = GZLang.add(P + "back", "Back");
    public static final String BOOKMARK = GZLang.add(P + "bookmark", "Bookmark this entry");
    public static final String REMOVE_BOOKMARK = GZLang.add(P + "remove_bookmark", "Right-click to remove");
    public static final String UNREAD = GZLang.add(P + "unread", "Not read yet");
    public static final String UNREAD_COUNT = GZLang.add(P + "unread_count", "%s not read yet");
    public static final String OPEN_ENTRY = GZLang.add(P + "open_entry", "Click: %s");
    public static final String ALL_LAYERS = GZLang.add(P + "all_layers", "All layers");
    public static final String LAYER = GZLang.add(P + "layer", "Layer %s of %s");
    public static final String LAYER_DOWN = GZLang.add(P + "layer_down", "Show one layer lower");
    public static final String LAYER_UP = GZLang.add(P + "layer_up", "Show one layer higher");
    public static final String DRAG = GZLang.add(P + "drag", "Drag to turn it");
    public static final String BLOCKS_NEEDED = GZLang.add(P + "blocks_needed", "Blocks needed");
    public static final String SEE_ALSO = GZLang.add(P + "see_also", "See also");
    public static final String TOOLTIP = GZLang.add(P + "tooltip", "Press [%s]: Field Manual");
    public static final String KEY = GZLang.add("key.groundzero.manual", "Open in Field Manual");
    public static final String KEY_CATEGORY = GZLang.add("key.category.groundzero.groundzero", "Ground Zero");

    private ManualStrings() {}

    /** Loads the keys (datagen only sees classes that are loaded). */
    public static void init() {}
}
