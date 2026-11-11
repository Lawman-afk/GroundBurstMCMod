package dev.groundburst.client.fx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBClientConfig;
import dev.groundburst.port.render.SubmitNodeCollector;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.RegisterShadersEvent;

/**
 * The Spectacle's render types (the 1.21.1 backport's: main builds them as 26.x render pipelines), all on vanilla's
 * unlit entity shaders (texture times vertex colour, no lighting at all), both sides drawn:
 * <ul>
 * <li><b>glow</b>: blended additively like lightning, not writing depth: the fireball's core, hot puffs, sparks and the
 * shock shell, so overlapping layers brighten toward white instead of hiding one another.</li>
 * <li><b>fire</b>: blended over what is behind, not writing depth: the fireball's body and every smoke and dust puff.</li>
 * <li><b>cloud</b>: as fire, but writing depth (drawn back to front), so vanilla's clouds do not show through a mushroom
 * cloud; on this branch's own shader ({@code rendertype_fx_cloud}), which cuts out the faint fringe as main's cloud
 * shader does, so a puff's clear pixels hide nothing drawn behind them after it. (Main's haze toward the fog colour,
 * rather than into it, is not in 1.21.1: here a cloud fades into the fog at the render distance like everything
 * else.)</li>
 * </ul>
 * Effects are drawn after the translucent terrain and the particles ({@code RenderBridge}), into the game's buffers.
 *
 * <p>With a shader pack on (Iris), and with Distant Horizons, they are drawn last ({@link DrawnLast}, main's DESIGN.md
 * §12.3): kept out of the level and drawn once the pack has finished its picture and Distant Horizons has laid its far
 * terrain over it, on these same shaders. A player who would rather have the pack draw them says so in the client config
 * ({@link GBClientConfig#SHADER_PACK_FX}): then the fire and the clouds draw on vanilla's translucent entity shader,
 * which Iris swaps for the pack's lit entity program (on the eyes shader a pack made the fire glow white, and the
 * clouds' own shader is one Iris does not know: the smoke did not show at all), and the glow stays on the eyes shader,
 * the pack's glowing program. Effects stay out of the pack's shadow pass.
 */
final class FxRenderTypes {
    /** Iris is installed: with a shader pack on, the effects are drawn last, or the pack draws them ({@link IrisFx}). */
    static final boolean IRIS = net.minecraftforge.fml.ModList.get().isLoaded("iris");
    /** Distant Horizons is installed: its far terrain is laid over the level after the effects, so they are drawn after it. */
    static final boolean DISTANT_HORIZONS = net.minecraftforge.fml.ModList.get().isLoaded("distanthorizons");
    /** Dev runs: {@code -PpackFx=over|in} sets the way under a pack whatever the config says. */
    private static final String DEV_PACK_FX = System.getProperty("groundburst.packFx", "").trim().toLowerCase(Locale.ROOT);

    private static final Function<ResourceLocation, RenderType> CLOUD = Util.memoize(t -> Shards.make("cloud", t, false, true));
    private static final Function<ResourceLocation, RenderType> FIRE = Util.memoize(t -> Shards.make("fire", t, false, false));
    private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(t -> Shards.make("glow", t, true, false));

    /** The clouds' shader (loaded with the game's own). */
    private static @Nullable ShaderInstance cloudShader;
    /** The glow's shader: the eyes shader, its own file blending it additively (1.20.1's shaders set their blending). */
    private static @Nullable ShaderInstance glowShader;

    private FxRenderTypes() {}

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterShadersEvent event) -> {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        new ResourceLocation(GroundBurst.MODID, "rendertype_fx_cloud"), DefaultVertexFormat.NEW_ENTITY),
                        shader -> cloudShader = shader);
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        new ResourceLocation(GroundBurst.MODID, "rendertype_fx_glow"), DefaultVertexFormat.NEW_ENTITY),
                        shader -> glowShader = shader);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        DrawnLast.register();
    }

    /**
     * Draws effect geometry now, into the collector's buffers (1.21.1 draws them after the translucent terrain); under a
     * shader pack or with Distant Horizons ({@link #drawnLast}) it is kept to be drawn after the level instead.
     */
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        if (IRIS && IrisFx.shadowPass()) {
            return;                                        // a shader pack's shadow map: effects cast no shadows
        }
        if (drawnLast()) {
            DrawnLast.submit(poseStack, type, renderer);
            return;
        }
        collector.submitCustomGeometry(poseStack, type, renderer);
    }

    /**
     * Whether the effects are drawn after the level now ({@link DrawnLast}): under a shader pack (unless the player asked
     * the pack to draw them), and with Distant Horizons.
     */
    static boolean drawnLast() {
        if (IRIS && IrisFx.packInUse()) {
            return !packDraws();
        }
        return DISTANT_HORIZONS;
    }

    /** A shader pack is on and the player asked it to draw the effects (0.8.1's way): the fire and clouds on its lit program. */
    private static boolean packDraws() {
        if (!IRIS || !IrisFx.packInUse()) {
            return false;
        }
        if (!DEV_PACK_FX.isEmpty()) {
            return DEV_PACK_FX.equals("in");
        }
        return GBClientConfig.SHADER_PACK_FX.get() == GBClientConfig.PackFx.IN;
    }

    /** Blended, unlit, two-sided, with this texture. */
    static RenderType fire(ResourceLocation texture) {
        return FIRE.apply(texture);
    }

    /** Blended, unlit, two-sided, writing depth, with this texture. */
    static RenderType cloud(ResourceLocation texture) {
        return CLOUD.apply(texture);
    }

    /** Additive, unlit, two-sided, with this texture. */
    static RenderType glow(ResourceLocation texture) {
        return GLOW.apply(texture);
    }

    /** Only to reach the render state pieces, which 1.21.1 keeps for render types and their kin. */
    private abstract static class Shards extends RenderType {
        private Shards() {
            super("", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }

        private static final ShaderStateShard CLOUD_SHADER = new ShaderStateShard(
                () -> packDraws() ? net.minecraft.client.renderer.GameRenderer.getRendertypeEntityTranslucentShader() : cloudShader);
        private static final ShaderStateShard FIRE_SHADER = new ShaderStateShard(
                () -> packDraws() ? net.minecraft.client.renderer.GameRenderer.getRendertypeEntityTranslucentShader()
                        : net.minecraft.client.renderer.GameRenderer.getRendertypeEyesShader());
        private static final ShaderStateShard GLOW_SHADER = new ShaderStateShard(
                () -> packDraws() ? net.minecraft.client.renderer.GameRenderer.getRendertypeEyesShader() : glowShader);

        /**
         * Blended additively ({@code additive}, as lightning) or over what is behind; writing depth or not. One that writes
         * depth is drawn with the cloud shader, which drops the faint pixels: on the eyes shader each puff's clear
         * corners would write depth too, hiding what is drawn behind them after it, a clear rectangle round the puff.
         * The lightmap and overlay are set up for a shader pack's lit program (the game's own shaders here read neither).
         */
        static RenderType make(String name, ResourceLocation texture, boolean additive, boolean writeDepth) {
            return create(GroundBurst.MODID + "_" + name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
                    CompositeState.builder()
                            .setShaderState(writeDepth ? CLOUD_SHADER : additive ? GLOW_SHADER : FIRE_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(additive ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setWriteMaskState(writeDepth ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                            .createCompositeState(false));
        }
    }
}
