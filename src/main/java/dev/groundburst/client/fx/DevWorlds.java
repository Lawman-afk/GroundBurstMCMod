package dev.groundburst.client.fx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import net.minecraft.client.Minecraft;
import net.minecraft.util.FileUtil;

/**
 * The development aids' throwaway worlds (the photographers, the blast bench, the world survey): each run makes its own
 * from scratch, so the last run's goes first; left alone they piled up (173 of the effects photographer's alone, 11 GB).
 */
public final class DevWorlds {
    private DevWorlds() {}

    /** Deletes the worlds earlier runs left under {@code name} (and its numbered copies) and gives the folder for this run's. */
    public static String fresh(Minecraft mc, String name) throws IOException {
        Path saves = mc.getLevelSource().getBaseDir();
        try (var old = Files.list(saves)) {
            for (Path dir : old.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith(name)).toList()) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        }
        return FileUtil.findAvailableName(saves, name, "");
    }
}
