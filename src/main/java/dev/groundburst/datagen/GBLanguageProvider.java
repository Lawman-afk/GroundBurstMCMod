package dev.groundburst.datagen;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Writes en_us.json from the names recorded in {@link GBLang}. Text keys are recorded by static initialisers, which
 * only run if something loads the class, and data generation loads few of them (a block entity's or a screen's keys
 * were silently missed, leaving raw "groundburst.x.y" text in game). So first it loads every class of ours that uses
 * GBLang.
 */
public class GBLanguageProvider extends LanguageProvider {
    private static final byte[] MARK = "dev/groundburst/registry/GBLang".getBytes(StandardCharsets.UTF_8);

    public GBLanguageProvider(PackOutput output) {
        super(output, GroundBurst.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        loadKeyClasses();
        GBLang.entries().forEach(this::add);
    }

    /** Initialises every class in our code that refers to GBLang (found by scanning the class files). */
    private static void loadKeyClasses() {
        List<String> names = new ArrayList<>();
        try {
            Path root = Path.of(GBLang.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(root)) {
                scan(root, root, names);
            } else {
                try (FileSystem jar = FileSystems.newFileSystem(root)) {
                    Path top = jar.getPath("/");
                    scan(top, top, names);
                }
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            throw new IllegalStateException("Could not scan our classes for text keys", e);
        }
        ClassLoader loader = GBLang.class.getClassLoader();
        for (String name : names) {
            try {
                Class.forName(name, true, loader);
            } catch (Throwable t) {
                throw new IllegalStateException("Could not load " + name + " for its text keys", t);
            }
        }
        GroundBurst.LOGGER.info("Language data: loaded {} classes that declare text keys", names.size());
    }

    private static void scan(Path root, Path dir, List<String> out) throws IOException {
        Path base = root.resolve("dev/groundburst");
        if (!Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> files = Files.walk(base)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (!rel.endsWith(".class") || rel.contains("$")) {
                    continue;                                       // nested classes load with their outer class
                }
                if (rel.startsWith("dev/groundburst/registry/GBLang") || !mentions(p)) {
                    continue;
                }
                out.add(rel.substring(0, rel.length() - ".class".length()).replace('/', '.'));
            }
        }
    }

    private static boolean mentions(Path classFile) throws IOException {
        byte[] bytes;
        try (InputStream in = Files.newInputStream(classFile)) {
            bytes = in.readAllBytes();
        }
        outer:
        for (int i = 0; i <= bytes.length - MARK.length; i++) {
            for (int k = 0; k < MARK.length; k++) {
                if (bytes[i + k] != MARK[k]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
