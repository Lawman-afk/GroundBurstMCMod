package dev.groundzero.config;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/** Client-only visual and HUD preferences. */
public final class GZClientConfig {
    public enum FxQuality implements TranslatableEnum {
        LOW("Low"), MEDIUM("Medium"), HIGH("High"), ULTRA("Ultra");

        private final String key;

        FxQuality(String english) {
            this.key = GZLang.add(GroundZero.MODID + ".configuration.fxQuality." + name().toLowerCase(java.util.Locale.ROOT), english);
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable(key);
        }
    }

    public enum HudCorner implements TranslatableEnum {
        TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right");

        private final String key;

        HudCorner(String english) {
            this.key = GZLang.add(GroundZero.MODID + ".configuration.hudCorner." + name().toLowerCase(java.util.Locale.ROOT), english);
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable(key);
        }
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.translation(GZLang.config("fx", "Visual effects")).comment("Explosion and weapon visuals.").push("fx");
    }

    public static final ModConfigSpec.EnumValue<FxQuality> FX_QUALITY = B
            .comment("Detail level of clouds, debris, smoke and beams. Lower it if big blasts drop your frame rate.")
            .translation(GZLang.config("fx.quality", "Effect quality"))
            .defineEnum("quality", FxQuality.HIGH);

    public static final ModConfigSpec.DoubleValue FLASH_STRENGTH = B
            .comment("Strength of the white-out flash from nuclear and antimatter detonations. 0 disables it.")
            .translation(GZLang.config("fx.flashStrength", "Flash strength"))
            .defineInRange("flashStrength", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE = B
            .comment("Strength of camera shake when shockwaves reach you. 0 disables it.")
            .translation(GZLang.config("fx.cameraShake", "Camera shake"))
            .defineInRange("cameraShake", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue FAR_CLOUDS = B
            .comment("Draw mushroom clouds and smoke columns beyond your chunk render distance.")
            .translation(GZLang.config("fx.farClouds", "Distant clouds"))
            .define("farClouds", true);

    static {
        B.pop();
        B.translation(GZLang.config("hud", "HUD")).comment("Overlay placement.").push("hud");
    }

    public static final ModConfigSpec.EnumValue<HudCorner> GEIGER_CORNER = B
            .comment("Screen corner for the Geiger counter and dosimeter readout.")
            .translation(GZLang.config("hud.geigerCorner", "Geiger readout position"))
            .defineEnum("geigerCorner", HudCorner.BOTTOM_RIGHT);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private GZClientConfig() {}
}
