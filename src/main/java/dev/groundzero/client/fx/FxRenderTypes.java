package dev.groundzero.client.fx;

import java.util.function.Function;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import dev.groundzero.GroundZero;
import net.minecraft.client.renderer.RenderPipelines;
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
 * orange against a bright sky, where additive light would wash out to white.</li>
 * </ul>
 */
final class FxRenderTypes {
    static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET, RenderPipelines.EYES_SNIPPET)
            .withLocation(GroundZero.id("pipeline/glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();
    static final OitPipelineSet OIT_GLOW = OitPipelineSet.builder(GroundZero.id("glow"),
            RenderPipeline.builder(RenderPipelines.OIT_ENTITY_SNIPPET, RenderPipelines.EYES_SNIPPET).withCull(false).withShaderDefine("OIT_ADDITIVE"))
            .build();

    static final RenderPipeline FIRE_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET, RenderPipelines.EYES_SNIPPET)
            .withLocation(GroundZero.id("pipeline/fire"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();
    static final OitPipelineSet OIT_FIRE = OitPipelineSet.builder(GroundZero.id("fire"),
            RenderPipeline.builder(RenderPipelines.OIT_ENTITY_SNIPPET, RenderPipelines.EYES_SNIPPET).withCull(false)).build();

    private static final Function<Identifier, RenderType> FIRE = Util.memoize(texture -> RenderType.create(GroundZero.MODID + "_fire",
            RenderSetup.builder(FIRE_PIPELINE).setOitPipelines(OIT_FIRE).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup()));
    private static final Function<Identifier, RenderType> GLOW = Util.memoize(texture -> RenderType.create(GroundZero.MODID + "_glow",
            RenderSetup.builder(GLOW_PIPELINE).setOitPipelines(OIT_GLOW).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup()));

    private FxRenderTypes() {}

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterRenderPipelinesEvent event) -> {
            event.registerPipeline(GLOW_PIPELINE);
            event.registerOitPipelineSet(OIT_GLOW);
            event.registerPipeline(FIRE_PIPELINE);
            event.registerOitPipelineSet(OIT_FIRE);
        });
    }

    /** Blended, unlit, two-sided, with this texture. */
    static RenderType fire(Identifier texture) {
        return FIRE.apply(texture);
    }

    /** Additive, unlit, two-sided, with this texture. */
    static RenderType glow(Identifier texture) {
        return GLOW.apply(texture);
    }
}
