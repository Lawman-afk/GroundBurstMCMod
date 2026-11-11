package dev.groundburst.legacy;

import net.minecraft.network.chat.Component;

/** NeoForge's enum with a translated name (the client config's choices; Forge 1.20.1 draws no config screen of its own). */
public interface TranslatableEnum {
    Component getTranslatedName();
}
