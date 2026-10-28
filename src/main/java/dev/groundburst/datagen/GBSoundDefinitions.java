package dev.groundburst.datagen;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBSounds;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.SoundDefinition;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;

/** Writes sounds.json from the declarations in {@link GBSounds}. */
public class GBSoundDefinitions extends SoundDefinitionsProvider {
    public GBSoundDefinitions(PackOutput output) {
        super(output, GroundBurst.MODID);
    }

    @Override
    public void registerSounds() {
        for (GBSounds.Def def : GBSounds.definitions()) {
            SoundDefinition definition = definition().subtitle(def.subtitleKey());
            for (String file : def.files()) {
                definition.with(sound(GroundBurst.id(file)).stream(def.stream()).attenuationDistance(def.attenuationDistance()));
            }
            add(def.holder(), definition);
        }
    }
}
