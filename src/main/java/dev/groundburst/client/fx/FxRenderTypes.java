package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.joml.Matrix4fStack;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Spectacle's render types (the 26.1.2 port's: main builds them on 26.3's pipelines with order-independent
 * transparency), all on vanilla's emissive entity shader (texture times vertex colour, no lighting at all), both sides
 * drawn:
 * <ul>
 * <li><b>glow</b>: blended additively like lightning, not writing depth: the fireball's core, hot puffs, sparks and the
 * shock shell, so overlapping layers brighten toward white instead of hiding one another.</li>
 * <li><b>fire</b>: blended over what is behind, not writing depth: the fireball's body and every smoke and dust
 * puff.</li>
 * <li><b>cloud</b>: as fire, but with the mod's own fragment shader ({@code shaders/core/fx_cloud.fsh}: hazed toward the
 * fog colour and faded out at the clouds' range, so a mushroom cloud stands over the horizon), cutting out its faint
 * fringe and writing depth (drawn back to front), so vanilla's clouds, drawn after it, do not show through it.</li>
 * </ul>
 * Everything is drawn after the translucent terrain ({@link #submit}): 26.1.2 draws custom geometry before the water, and
 * water behind smoke then failed the depth test against a cloud's depth ("smoke makes all water transparent").
 * <p>
 * With a shader pack on (Iris; main's DESIGN.md §12.3), and with Distant Horizons, they are drawn last ({@link #drawnLast}):
 * kept out of the level and drawn at NeoForge's after-level stage, once the pack has finished its picture and Distant
 * Horizons has laid its far terrain over it, by these same pipelines against the depth left behind; Iris has stopped
 * swapping in the pack's programs by then. In the level, a pack had no program for them: the effects drew nothing at all
 * (Iris 1.11.4). A player who would rather have the pack draw them says so in the client config
 * ({@link GBClientConfig#SHADER_PACK_FX}): {@link IrisFx} hands each pipeline to the pack program it is most like.
 */
final class FxRenderTypes {
    /** Iris is installed: the effects are handed to a shader pack's programs ({@link IrisFx}), or drawn over the pack. */
    static final boolean IRIS = net.neoforged.fml.ModList.get().isLoaded("iris");
    /** Distant Horizons is installed: its far terrain is laid over the level after the effects, so they are drawn after it. */
    static final boolean DISTANT_HORIZONS = net.neoforged.fml.ModList.get().isLoaded("distanthorizons");
    /** Dev runs: {@code -PpackFx=over|in} sets the way under a pack whatever the config says. */
    private static final String DEV_PACK_FX = System.getProperty("groundburst.packFx", "").trim().toLowerCase(Locale.ROOT);
    /**
     * The glow's and fire's alpha cutout: only what is all but clear (a 255th) is dropped, nothing anyone sees. It is
     * there for Iris, which gives a pipeline with a cutout the shader pack's entity program for this vertex layout.
     */
    private static final float NEARLY_CLEAR = 1.0F / 255;

    /** Vanilla's eyes pipeline (the emissive entity shader without overlay or the sun's light), both sides drawn. */
    private static RenderPipeline.Builder eyes(String name) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation(GroundBurst.id("pipeline/" + name))
                .withVertexShader("core/entity")
                .withFragmentShader("core/entity")
                .withShaderDefine("EMISSIVE")
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                .withCull(false);
    }

    static final RenderPipeline GLOW_PIPELINE = eyes("glow")
            .withShaderDefine("ALPHA_CUTOUT", NEARLY_CLEAR)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();
    static final RenderPipeline FIRE_PIPELINE = eyes("fire")
            .withShaderDefine("ALPHA_CUTOUT", NEARLY_CLEAR)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();
    static final RenderPipeline CLOUD_PIPELINE = eyes("cloud")
            .withFragmentShader(GroundBurst.id("core/fx_cloud"))
            // (fx_cloud.fsh cuts its own fringe out; the define tells Iris to give a shader pack's version a cutout too)
            .withShaderDefine("ALPHA_CUTOUT", 0.16F)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .build();

    private static final Function<Identifier, RenderType> CLOUD = Util.memoize(texture -> RenderType.create(GroundBurst.MODID + "_cloud",
            RenderSetup.builder(CLOUD_PIPELINE).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup()));
    private static final Function<Identifier, RenderType> FIRE = Util.memoize(texture -> RenderType.create(GroundBurst.MODID + "_fire",
            RenderSetup.builder(FIRE_PIPELINE).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup()));
    // The glow adds its light and writes no depth, so it comes out the same in any order: not sorted (main's DESIGN.md §11.10).
    private static final Function<Identifier, RenderType> GLOW = Util.memoize(texture -> RenderType.create(GroundBurst.MODID + "_glow",
            RenderSetup.builder(GLOW_PIPELINE).withTexture("Sampler0", texture).createRenderSetup()));

    /** This frame's effect geometry, drawn after the translucent terrain (or after the level, {@link #drawnLast}). */
    private record Queued(PoseStack.Pose pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {}

    private static final List<Queued> QUEUE = new ArrayList<>();
    /** The fog the level was drawn with, for the effects drawn after it. */
    private static @Nullable GpuBufferSlice levelFog;

    private FxRenderTypes() {}

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterRenderPipelinesEvent event) -> {
            event.registerPipeline(GLOW_PIPELINE);
            event.registerPipeline(FIRE_PIPELINE);
            event.registerPipeline(CLOUD_PIPELINE);
            if (IRIS) {
                // The machines' glowmasks' own pipeline with Iris (MachineGeoRenderer's GlowLayer)
                event.registerPipeline(dev.groundburst.client.render.MachineGeoRenderer.glowPipeline());
                try {
                    IrisFx.register();
                } catch (LinkageError e) {                 // an Iris without the API this was built against
                    GroundBurst.LOGGER.warn("Could not hand the blast effects to Iris: {}", e.toString());
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks event) -> {
            if (!drawnLast()) {
                draw();
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentParticles event) -> levelFog = RenderSystem.getShaderFog());
        NeoForge.EVENT_BUS.addListener(FxRenderTypes::drawAfterLevel);
    }

    /** Queues effect geometry to be drawn after the translucent terrain (water), where vanilla draws its translucent particles. */
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        if (IRIS && IrisFx.shadowPass()) {
            return;                                        // a shader pack's shadow map: effects cast no shadows
        }
        QUEUE.add(new Queued(poseStack.last().copy(), type, renderer));
    }

    /**
     * Whether the effects are drawn after the level now: under a shader pack (unless the player asked the pack to draw
     * them), and with Distant Horizons.
     */
    static boolean drawnLast() {
        if (IRIS && IrisFx.packInUse()) {
            if (!DEV_PACK_FX.isEmpty()) {
                return !DEV_PACK_FX.equals("in");
            }
            return GBClientConfig.SHADER_PACK_FX.get() == GBClientConfig.PackFx.OVER;
        }
        return DISTANT_HORIZONS;
    }

    /**
     * The effects kept back from the level ({@link #drawnLast}), drawn into its finished picture: the view is the stage's,
     * the projection still the level's, the fog the level's main pass's.
     */
    private static void drawAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        if (QUEUE.isEmpty()) {
            levelFog = null;
            return;
        }
        GpuBufferSlice fog = RenderSystem.getShaderFog();
        if (levelFog != null) {
            RenderSystem.setShaderFog(levelFog);
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        try {
            draw();
        } finally {
            modelView.popMatrix();
            if (fog != null) {
                RenderSystem.setShaderFog(fog);
            }
            levelFog = null;
        }
    }

    private static void draw() {
        if (QUEUE.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (Queued q : QUEUE) {
            q.renderer().render(q.pose(), buffers.getBuffer(q.type()));
        }
        buffers.endBatch();
        QUEUE.clear();
    }

    /** Blended, unlit, two-sided, with this texture. */
    static RenderType fire(Identifier texture) {
        return FIRE.apply(texture);
    }

    /** Blended, unlit, two-sided, seen far beyond the render distance, with this texture. */
    static RenderType cloud(Identifier texture) {
        return CLOUD.apply(texture);
    }

    /** Additive, unlit, two-sided, with this texture. */
    static RenderType glow(Identifier texture) {
        return GLOW.apply(texture);
    }
}
