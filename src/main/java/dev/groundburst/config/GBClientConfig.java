package dev.groundburst.config;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import dev.groundburst.legacy.TranslatableEnum;

/** Client-only visual and HUD preferences. */
public final class GBClientConfig {
    public enum FxQuality implements TranslatableEnum {
        LOW("Low"), MEDIUM("Medium"), HIGH("High"), ULTRA("Ultra");

        private final String key;

        FxQuality(String english) {
            this.key = GBLang.add(GroundBurst.MODID + ".configuration.fxQuality." + name().toLowerCase(java.util.Locale.ROOT), english);
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
            this.key = GBLang.add(GroundBurst.MODID + ".configuration.hudCorner." + name().toLowerCase(java.util.Locale.ROOT), english);
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable(key);
        }
    }

    /** How the blast effects are drawn with a shader pack on (DESIGN.md §12.3). */
    public enum PackFx implements TranslatableEnum {
        OVER("Over the pack"), IN("Lit by the pack");

        private final String key;

        PackFx(String english) {
            this.key = GBLang.add(GroundBurst.MODID + ".configuration.shaderPackFx." + name().toLowerCase(java.util.Locale.ROOT), english);
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable(key);
        }
    }

    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    static {
        B.translation(GBLang.config("fx", "Visual effects")).comment("Explosion and weapon visuals.").push("fx");
    }

    public static final ForgeConfigSpec.EnumValue<FxQuality> FX_QUALITY = B
            .comment("Detail level of clouds, debris, smoke and beams. Lower it if big blasts drop your frame rate.")
            .translation(GBLang.config("fx.quality", "Effect quality"))
            .defineEnum("quality", FxQuality.HIGH);

    public static final ForgeConfigSpec.DoubleValue FLASH_STRENGTH = B
            .comment("Strength of the white-out flash from nuclear and antimatter detonations. 0 disables it.")
            .translation(GBLang.config("fx.flashStrength", "Flash strength"))
            .defineInRange("flashStrength", 1.0, 0.0, 1.0);

    public static final ForgeConfigSpec.BooleanValue FLASH_BLINDING = B
            .comment("Whether a nuclear flash looked at straight on blinds you for a few seconds, the white-out held and clearing",
                    "slowly (DESIGN.md §21.10). Off: the flash comes and goes as quickly as any other. Flash strength 0 turns all of it off.")
            .translation(GBLang.config("fx.flashBlinding", "Blinding flash"))
            .define("flashBlinding", true);

    public static final ForgeConfigSpec.DoubleValue CAMERA_SHAKE = B
            .comment("Strength of camera shake when shockwaves reach you. 0 disables it.")
            .translation(GBLang.config("fx.cameraShake", "Camera shake"))
            .defineInRange("cameraShake", 1.0, 0.0, 1.0);

    public static final ForgeConfigSpec.BooleanValue FAR_CLOUDS = B
            .comment("Draw mushroom clouds and smoke columns beyond your chunk render distance.")
            .translation(GBLang.config("fx.farClouds", "Distant clouds"))
            .define("farClouds", true);

    public static final ForgeConfigSpec.EnumValue<PackFx> SHADER_PACK_FX = B
            .comment("With a shader pack on (Iris): draw blast effects over the pack's finished picture, as they look without "
                    + "shaders and the same in every pack, or have the pack draw them, lit and fogged like the world round them "
                    + "(some packs then draw smoke dark or lose far clouds).")
            .translation(GBLang.config("fx.shaderPackFx", "Effects with shader packs"))
            .defineEnum("shaderPackFx", PackFx.OVER);

    static {
        B.pop();
        B.translation(GBLang.config("hud", "HUD")).comment("Overlay placement.").push("hud");
    }

    public static final ForgeConfigSpec.EnumValue<HudCorner> GEIGER_CORNER = B
            .comment("Screen corner for the Geiger counter and dosimeter readout.")
            .translation(GBLang.config("hud.geigerCorner", "Geiger readout position"))
            .defineEnum("geigerCorner", HudCorner.BOTTOM_RIGHT);

    static {
        B.pop();
    }

    public static final ForgeConfigSpec SPEC = B.build();

    private GBClientConfig() {}
}
