package dev.groundburst.client.manual;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.groundburst.GroundBurst;
import net.neoforged.fml.loading.FMLPaths;

/**
 * What the reader has done with the Field Manual: entries read (no more "new" dot), bookmarks (up to
 * {@link #MAX_BOOKMARKS}) and where the book was left open. Kept on the client in {@code config/groundburst-manual.json}.
 */
public final class ManualProgress {
    public static final int MAX_BOOKMARKS = 6;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The saved form. */
    private static final class Data {
        List<String> read = new ArrayList<>();
        List<String> bookmarks = new ArrayList<>();
        @Nullable String lastEntry;
        int lastSpread;
    }

    private static @Nullable Data data;

    private ManualProgress() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("groundburst-manual.json");
    }

    private static Data data() {
        if (data == null) {
            data = new Data();
            Path file = file();
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    Data loaded = GSON.fromJson(reader, Data.class);
                    if (loaded != null) {
                        data = loaded;
                    }
                } catch (IOException | RuntimeException e) {
                    GroundBurst.LOGGER.warn("Field Manual: could not read {}", file, e);
                }
            }
            if (data.read == null) {
                data.read = new ArrayList<>();
            }
            if (data.bookmarks == null) {
                data.bookmarks = new ArrayList<>();
            }
        }
        return data;
    }

    private static void save() {
        try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            GSON.toJson(data(), writer);
        } catch (IOException e) {
            GroundBurst.LOGGER.warn("Field Manual: could not save {}", file(), e);
        }
    }

    public static boolean isRead(String entry) {
        return data().read.contains(entry);
    }

    public static void markRead(String entry) {
        if (!isRead(entry)) {
            data().read.add(entry);
            save();
        }
    }

    public static List<String> bookmarks() {
        return List.copyOf(data().bookmarks);
    }

    public static boolean isBookmarked(String entry) {
        return data().bookmarks.contains(entry);
    }

    /** Adds or removes a bookmark; false when adding and the book already has the maximum. */
    public static boolean toggleBookmark(String entry) {
        Set<String> marks = new LinkedHashSet<>(data().bookmarks);
        if (!marks.remove(entry)) {
            if (marks.size() >= MAX_BOOKMARKS) {
                return false;
            }
            marks.add(entry);
        }
        data().bookmarks = new ArrayList<>(marks);
        save();
        return true;
    }

    public static @Nullable String lastEntry() {
        return data().lastEntry;
    }

    public static int lastSpread() {
        return data().lastSpread;
    }

    public static void remember(@Nullable String entry, int spread) {
        Data d = data();
        if (!java.util.Objects.equals(d.lastEntry, entry) || d.lastSpread != spread) {
            d.lastEntry = entry;
            d.lastSpread = spread;
            save();
        }
    }
}
