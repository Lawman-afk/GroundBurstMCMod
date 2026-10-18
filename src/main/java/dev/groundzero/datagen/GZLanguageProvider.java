package dev.groundzero.datagen;

import dev.groundzero.GroundZero;
import dev.groundzero.config.GZClientConfig;
import dev.groundzero.config.GZServerConfig;
import dev.groundzero.registry.GZLang;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** Writes en_us.json from the names recorded in {@link GZLang} at registration time. */
public class GZLanguageProvider extends LanguageProvider {
    public GZLanguageProvider(PackOutput output) {
        super(output, GroundZero.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        // Config classes record their labels in static initialisers; make sure both have run.
        GZServerConfig.SPEC.getClass();
        GZClientConfig.SPEC.getClass();

        GZLang.entries().forEach(this::add);
    }
}
