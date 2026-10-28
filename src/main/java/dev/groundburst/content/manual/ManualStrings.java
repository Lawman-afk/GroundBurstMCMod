package dev.groundburst.content.manual;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;

/** Translation keys for the Field Manual's screen, tooltip line and key (the content itself is in the manual files). */
public final class ManualStrings {
    private static final String P = GroundBurst.MODID + ".manual.";

    public static final String TITLE = GBLang.add(P + "title", "FIELD MANUAL");
    public static final String SERIAL = GBLang.add(P + "serial", "GB-1");
    public static final String TAGLINE = GBLang.add(P + "tagline", "Ordnance, industry and the means to power them.");
    public static final String SEARCH = GBLang.add(P + "search", "Search the manual");
    public static final String RESULTS = GBLang.add(P + "results", "Results");
    public static final String NO_RESULTS = GBLang.add(P + "no_results", "Nothing found.");
    public static final String CONTENTS = GBLang.add(P + "contents", "Contents");
    public static final String ENTRIES = GBLang.add(P + "entries", "Entries");
    public static final String PREVIOUS = GBLang.add(P + "previous", "Previous page");
    public static final String NEXT = GBLang.add(P + "next", "Next page");
    public static final String BACK = GBLang.add(P + "back", "Back");
    public static final String BOOKMARK = GBLang.add(P + "bookmark", "Bookmark this entry");
    public static final String REMOVE_BOOKMARK = GBLang.add(P + "remove_bookmark", "Right-click to remove");
    public static final String UNREAD = GBLang.add(P + "unread", "Not read yet");
    public static final String UNREAD_COUNT = GBLang.add(P + "unread_count", "%s not read yet");
    public static final String OPEN_ENTRY = GBLang.add(P + "open_entry", "Click: %s");
    public static final String ALL_LAYERS = GBLang.add(P + "all_layers", "All layers");
    public static final String LAYER = GBLang.add(P + "layer", "Layer %s of %s");
    public static final String LAYER_DOWN = GBLang.add(P + "layer_down", "Show one layer lower");
    public static final String LAYER_UP = GBLang.add(P + "layer_up", "Show one layer higher");
    public static final String BLOCKS_NEEDED = GBLang.add(P + "blocks_needed", "Blocks needed");
    public static final String SEE_ALSO = GBLang.add(P + "see_also", "See also");
    public static final String KEY = GBLang.add("key.groundburst.manual", "Open in Field Manual");
    public static final String KEY_CATEGORY = GBLang.add("key.category.groundburst.groundburst", "Ground Burst");

    private ManualStrings() {}

    /** Loads the keys (datagen only sees classes that are loaded). */
    public static void init() {}
}
