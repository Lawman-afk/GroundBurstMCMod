package dev.groundburst.client.fx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.groundburst.GroundBurst;
import dev.groundburst.port.render.SubmitNodeCollector;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

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
 */
final class FxRenderTypes {
    private static final Function<ResourceLocation, RenderType> CLOUD = Util.memoize(t -> Shards.make("cloud", t, false, true));
    private static final Function<ResourceLocation, RenderType> FIRE = Util.memoize(t -> Shards.make("fire", t, false, false));
    private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(t -> Shards.make("glow", t, true, false));

    /** The clouds' shader (loaded with the game's own). */
    private static @Nullable ShaderInstance cloudShader;

    private FxRenderTypes() {}

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterShadersEvent event) -> {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(GroundBurst.MODID, "rendertype_fx_cloud"), DefaultVertexFormat.NEW_ENTITY),
                        shader -> cloudShader = shader);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Draws effect geometry now, into the collector's buffers (1.21.1 draws them after the translucent terrain). */
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        collector.submitCustomGeometry(poseStack, type, renderer);
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

        private static final ShaderStateShard CLOUD_SHADER = new ShaderStateShard(() -> cloudShader);

        /**
         * Blended additively ({@code additive}, as lightning) or over what is behind; writing depth or not. One that writes
         * depth is drawn with the cloud shader, which drops the faint pixels: on the eyes shader each puff's clear
         * corners would write depth too, hiding what is drawn behind them after it, a clear rectangle round the puff.
         */
        static RenderType make(String name, ResourceLocation texture, boolean additive, boolean writeDepth) {
            return create(GroundBurst.MODID + "_" + name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
                    CompositeState.builder()
                            .setShaderState(writeDepth ? CLOUD_SHADER : RENDERTYPE_EYES_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(additive ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(writeDepth ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                            .createCompositeState(false));
        }
    }
}
