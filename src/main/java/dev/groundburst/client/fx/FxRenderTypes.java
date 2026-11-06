package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.groundburst.GroundBurst;
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
 */
final class FxRenderTypes {
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
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();
    static final RenderPipeline FIRE_PIPELINE = eyes("fire")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();
    static final RenderPipeline CLOUD_PIPELINE = eyes("cloud")
            .withFragmentShader(GroundBurst.id("core/fx_cloud"))
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

    /** This frame's effect geometry, drawn after the translucent terrain. */
    private record Queued(PoseStack.Pose pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {}

    private static final List<Queued> QUEUE = new ArrayList<>();

    private FxRenderTypes() {}

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterRenderPipelinesEvent event) -> {
            event.registerPipeline(GLOW_PIPELINE);
            event.registerPipeline(FIRE_PIPELINE);
            event.registerPipeline(CLOUD_PIPELINE);
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks event) -> draw());
    }

    /** Queues effect geometry to be drawn after the translucent terrain (water), where vanilla draws its translucent particles. */
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        QUEUE.add(new Queued(poseStack.last().copy(), type, renderer));
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
