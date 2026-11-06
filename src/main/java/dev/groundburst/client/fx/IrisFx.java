package dev.groundburst.client.fx;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import dev.groundburst.GroundBurst;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;

/**
 * Shader packs (Iris; DESIGN.md §12.3). With a pack on, Iris draws the world with the pack's own programs, which take
 * more from each vertex than vanilla's shaders do; a render pipeline Iris does not know is still drawn with its own
 * shader but from vertices laid out for the pack, and the effects came out as long black and coloured shards across
 * the screen.
 * <p>
 * So by default the effects are not drawn in the level at all under a pack but over its finished picture
 * ({@link DrawnLast}), where Iris leaves vanilla's shaders alone. No pack program suits a soft cloud seen from afar:
 * Bliss fades every half-clear particle out by 100 blocks (for its world border) and lights entities in a pass of its own
 * that drew smoke black and a mushroom cloud as a hole of sky colour; its emissive programs follow its auto-exposure, so
 * a fireball's glow swung in colour as the flash lit the scene.
 * <p>
 * For a player who would rather have the pack draw them ({@link dev.groundburst.config.GBClientConfig#SHADER_PACK_FX}),
 * each effect pipeline is handed to the pack program it is most like ({@link IrisApi#assignPipeline}):
 * <ul>
 * <li>the glow (the fireball's white-hot core, the shock shell, sparks, beams, lasers) to the emissive entity program
 * (spider eyes: unlit, glowing);</li>
 * <li>the fire (the fireball's body, fire puffs) and the cloud (smoke, dust, the mushroom cloud) to the translucent
 * entity program, which the pack lights. On the emissive program a pack made them glow white, smoke and all. Their
 * colours are still shaded here ({@link FxLight}) and they are marked full-bright as without a pack: with the world's
 * real light handed to the pack instead, smoke came out dark and blue in both packs tried.</li>
 * </ul>
 * Either way the effects stay out of the pack's shadow pass ({@link #shadowPass}). Only loaded when Iris is
 * ({@link FxRenderTypes#IRIS}).
 */
final class IrisFx {
    /** The effect pipelines, by the names the dev switch uses. */
    enum Fx { GLOW, FIRE, CLOUD }

    private IrisFx() {}

    static void register() {
        IrisApi api = IrisApi.getInstance();
        if (api.getMinorApiRevision() < 3) {
            GroundBurst.LOGGER.warn("This Iris is too old to be told about the blast effects (its API is 0.{}; 0.3 is needed): they may "
                    + "not draw with a shader pack on", api.getMinorApiRevision());
            return;
        }
        Map<Fx, IrisProgram> programs = new EnumMap<>(Fx.class);
        programs.put(Fx.GLOW, IrisProgram.EMISSIVE_ENTITIES);
        programs.put(Fx.FIRE, IrisProgram.ENTITIES_TRANSLUCENT);
        programs.put(Fx.CLOUD, IrisProgram.ENTITIES_TRANSLUCENT);
        // Dev runs try others: -PirisFx=glow:EMISSIVE_ENTITIES,cloud:ENTITIES_TRANSLUCENT (with -PpackFx=in)
        for (String pair : System.getProperty("groundburst.irisFx", "").split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                programs.put(Fx.valueOf(kv[0].trim().toUpperCase(Locale.ROOT)), IrisProgram.valueOf(kv[1].trim().toUpperCase(Locale.ROOT)));
            }
        }
        assign(api, FxRenderTypes.GLOW_PIPELINE, programs.get(Fx.GLOW));
        assign(api, FxRenderTypes.FIRE_PIPELINE, programs.get(Fx.FIRE));
        assign(api, FxRenderTypes.CLOUD_PIPELINE, programs.get(Fx.CLOUD));
        GroundBurst.LOGGER.info("Blast effects handed to Iris's programs (when a pack is to draw them): {}", programs);
    }

    /** Whether a shader pack is on now. */
    static boolean packInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    private static void assign(IrisApi api, RenderPipeline pipeline, IrisProgram program) {
        api.assignPipeline(pipeline, program);
    }

    /** Whether Iris is drawing the shader pack's shadow map now (effects cast no shadows). */
    static boolean shadowPass() {
        return IrisApi.getInstance().isRenderingShadowPass();
    }
}
