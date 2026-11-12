package dev.groundburst.client.fx;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * The Spectacle's render types, both the vanilla emissive entity shader (texture times vertex colour, no lighting at
 * all), both sides drawn, not writing depth, so a camera inside a fireball or shell still sees it:
 * <ul>
 * <li><b>glow</b>: blended additively like lightning. The fireball's core, hot puffs, sparks and the shock shell, so
 * overlapping layers brighten toward white instead of hiding one another. Its OIT set is the entity one with the
 * shader's additive switch, as vanilla does for lightning.</li>
 * <li><b>fire</b>: blended over what is behind (like vanilla "eyes", but two-sided). The fireball's body, which must stay
 * orange against a bright sky, where additive light would wash out to white, and every smoke and dust puff (lit on the
 * CPU, {@link FxLight}).</li>
 * <li><b>cloud</b>: as fire, but with our own fragment shader ({@code shaders/core/fx_cloud.fsh}) that hazes toward the
 * fog colour instead of fading into the render-distance fog, and fades out at the clouds' range like vanilla clouds: a
 * mushroom cloud stands over the horizon far beyond the loaded terrain. It cuts out its faint fringe and writes depth
 * (drawn back to front), so vanilla's clouds, drawn after it, do not show through it.</li>
 * </ul>
 * All of them are submitted with {@link #submit}, to be drawn after the translucent terrain as vanilla's own translucent
 * particles are: blended custom geometry is otherwise drawn before it, and water behind any smoke then failed the depth
 * test against the cloud pipeline's depth and was not drawn at all (after the in-game look: "smoke makes all water
 * transparent").
 * <p>
 * With a shader pack on (Iris; DESIGN.md §12.3), and with Distant Horizons, they are drawn last ({@link DrawnLast}): kept
 * out of the level and drawn once the pack has finished its picture and Distant Horizons has laid its far terrain over
 * it, by these same pipelines, against the depth left behind. A player who would rather have the pack draw them says so
 * in the client config ({@link GBClientConfig#SHADER_PACK_FX}).
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
     * there for Iris, which gives a pipeline with a cutout the shader pack's entity program for this vertex layout
     * (without one, the glow and fire got a program for another layout).
     */
    private static final float NEARLY_CLEAR = 1.0F / 255;
    private static final Identifier CLOUD_SHADER = GroundBurst.id("core/fx_cloud");

    static final RenderPipeline GLOW_PIPELINE = glow();
    static final OitPipelineSet OIT_GLOW = OitPipelineSet.builder(GroundBurst.id("glow"),
            RenderPipeline.builder(RenderPipelines.OIT_ENTITY_SNIPPET, RenderPipelines.EYES_SNIPPET).withCull(false).withShaderDefine("OIT_ADDITIVE"))
            .build();
    static final RenderPipeline FIRE_PIPELINE = fire();
    static final OitPipelineSet OIT_FIRE = OitPipelineSet.builder(GroundBurst.id("fire"),
            RenderPipeline.builder(RenderPipelines.OIT_ENTITY_SNIPPET, RenderPipelines.EYES_SNIPPET).withCull(false)).build();
    static final RenderPipeline CLOUD_PIPELINE = cloud();
    static final RenderPipeline FAR_GLOW_PIPELINE = farGlow();
    static final OitPipelineSet OIT_FAR_GLOW = OitPipelineSet.builder(GroundBurst.id("far_glow"),
            RenderPipeline.builder(RenderPipelines.OIT_ENTITY_SNIPPET, RenderPipelines.EYES_SNIPPET).withFragmentShader(CLOUD_SHADER)
                    .withShaderDefine("FX_SOFT").withShaderDefine("OIT_ADDITIVE").withCull(false))
            .build();
    static final OitPipelineSet OIT_CLOUD = OitPipelineSet.builder(GroundBurst.id("cloud"),
            RenderPipeline.builder(RenderPipelines.OIT_ENTITY_SNIPPET, RenderPipelines.EYES_SNIPPET).withFragmentShader(CLOUD_SHADER).withCull(false))
            .build();

    private static RenderPipeline glow() {
        return RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET, RenderPipelines.EYES_SNIPPET)
                .withLocation(GroundBurst.id("pipeline/glow"))
                .withShaderDefine("ALPHA_CUTOUT", NEARLY_CLEAR)
                .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withCull(false)
                .build();
    }

    private static RenderPipeline fire() {
        return RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET, RenderPipelines.EYES_SNIPPET)
                .withLocation(GroundBurst.id("pipeline/fire"))
                .withShaderDefine("ALPHA_CUTOUT", NEARLY_CLEAR)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withCull(false)
                .build();
    }

    private static RenderPipeline cloud() {
        return RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET, RenderPipelines.EYES_SNIPPET)
                .withLocation(GroundBurst.id("pipeline/cloud"))
                .withFragmentShader(CLOUD_SHADER)
                // (fx_cloud.fsh cuts its own fringe out; the define tells Iris to give a shader pack's version a cutout too)
                .withShaderDefine("ALPHA_CUTOUT", 0.16F)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                .withCull(false)
                .build();
    }

    /**
     * The far glow (DESIGN.md §21.10): a nuclear flash's light over the horizon, beyond the loaded terrain. The cloud's
     * fragment shader (hazed toward the fog colour, not faded into the render-distance fog) without its cutout, added as the
     * glow is, writing no depth.
     */
    private static RenderPipeline farGlow() {
        return RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET, RenderPipelines.EYES_SNIPPET)
                .withLocation(GroundBurst.id("pipeline/far_glow"))
                .withFragmentShader(CLOUD_SHADER)
                .withShaderDefine("FX_SOFT")
                .withShaderDefine("ALPHA_CUTOUT", NEARLY_CLEAR)
                .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withCull(false)
                .build();
    }

    /**
     * Each blended type's twin, which leaves its quads in the order they come: the effects' own quads are sorted by the
     * mod ({@link FxSort}) and drawn under it; anything else (a reticle, a laser) under the sorting type as before.
     */
    private static final Map<RenderType, RenderType> PRESORTED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Function<Identifier, RenderType> CLOUD = Util.memoize(texture -> sorting("_cloud", CLOUD_PIPELINE, OIT_CLOUD, texture));
    private static final Function<Identifier, RenderType> FIRE = Util.memoize(texture -> sorting("_fire", FIRE_PIPELINE, OIT_FIRE, texture));

    /** A blended type that has the game sort its quads, and its twin that does not. */
    private static RenderType sorting(String name, RenderPipeline pipeline, OitPipelineSet oit, Identifier texture) {
        RenderType sorted = RenderType.create(GroundBurst.MODID + name,
                RenderSetup.builder(pipeline).setOitPipelines(oit).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup());
        PRESORTED.put(sorted, RenderType.create(GroundBurst.MODID + name + "_presorted",
                RenderSetup.builder(pipeline).setOitPipelines(oit).withTexture("Sampler0", texture).createRenderSetup()));
        return sorted;
    }
    // The glow adds its light and writes no depth, so it comes out the same in any order: not sorted (DESIGN.md §11.10;
    // sorting every effect's quads each frame was a tenth of the frame's work under a mushroom cloud).
    private static final Function<Identifier, RenderType> GLOW = Util.memoize(texture -> RenderType.create(GroundBurst.MODID + "_glow",
            RenderSetup.builder(GLOW_PIPELINE).setOitPipelines(OIT_GLOW).withTexture("Sampler0", texture).createRenderSetup()));
    private static final Function<Identifier, RenderType> FAR_GLOW = Util.memoize(texture -> RenderType.create(GroundBurst.MODID + "_far_glow",
            RenderSetup.builder(FAR_GLOW_PIPELINE).setOitPipelines(OIT_FAR_GLOW).withTexture("Sampler0", texture).createRenderSetup()));

    private FxRenderTypes() {}

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterRenderPipelinesEvent event) -> {
            event.registerPipeline(GLOW_PIPELINE);
            event.registerOitPipelineSet(OIT_GLOW);
            event.registerPipeline(FIRE_PIPELINE);
            event.registerOitPipelineSet(OIT_FIRE);
            event.registerPipeline(CLOUD_PIPELINE);
            event.registerOitPipelineSet(OIT_CLOUD);
            event.registerPipeline(FAR_GLOW_PIPELINE);
            event.registerOitPipelineSet(OIT_FAR_GLOW);
            if (IRIS) {
                try {
                    IrisFx.register();
                } catch (LinkageError e) {                 // an Iris without the API this was built against
                    GroundBurst.LOGGER.warn("Could not hand the blast effects to Iris: {}", e.toString());
                }
            }
        });
        DrawnLast.register();
    }

    /**
     * Submits effect geometry to be drawn after the translucent terrain (water), where vanilla draws its translucent
     * particles; with improved transparency that is the same OIT pass as any other translucent geometry. Under a shader
     * pack or with Distant Horizons ({@link #drawnLast}) it is kept to be drawn after the level instead.
     */
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        if (IRIS && IrisFx.shadowPass()) {
            return;                                        // a shader pack's shadow map: effects cast no shadows
        }
        RenderType presorted = PRESORTED.get(type);
        if (presorted != null && FxSort.add(poseStack, presorted, renderer)) {
            return;                                        // (sorted with the rest of the effects' quads, at the end)
        }
        submitNow(collector, poseStack, type, renderer);
    }

    /** {@link #submit}, without keeping anything back to be sorted. */
    static void submitNow(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        if (drawnLast()) {
            DrawnLast.submit(poseStack, type, renderer);
            return;
        }
        SubmitNodeCollection collection = collector instanceof SubmitNodeStorage storage ? storage.order(0)
                : collector instanceof SubmitNodeCollection c ? c : null;
        if (collection == null) {
            collector.submitCustomGeometry(poseStack, type, renderer);
            return;
        }
        collection.afterTerrain.submit(new CustomFeatureRenderer.Submit(poseStack.last().copy(), type, renderer));
    }

    /**
     * Whether the effects are drawn after the level now ({@link DrawnLast}): under a shader pack (unless the player asked
     * the pack to draw them), and with Distant Horizons.
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

    /** Additive, unlit, two-sided, seen far beyond the render distance (hazed, not fogged out), with this texture. */
    static RenderType farGlow(Identifier texture) {
        return FAR_GLOW.apply(texture);
    }
}
