package dev.groundzero.datagen;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZSounds;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.SoundDefinition;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;

/** Writes sounds.json from the declarations in {@link GZSounds}. */
public class GZSoundDefinitions extends SoundDefinitionsProvider {
    public GZSoundDefinitions(PackOutput output) {
        super(output, GroundZero.MODID);
    }

    @Override
    public void registerSounds() {
        for (GZSounds.Def def : GZSounds.definitions()) {
            SoundDefinition definition = definition().subtitle(def.subtitleKey());
            for (String file : def.files()) {
                definition.with(sound(GroundZero.id(file)).stream(def.stream()).attenuationDistance(def.attenuationDistance()));
            }
            add(def.holder(), definition);
        }
    }
}
