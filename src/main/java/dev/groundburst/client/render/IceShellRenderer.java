package dev.groundburst.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.effect.FrozenEffect;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Frozen solid (DESIGN.md §16.4): a block of ice drawn round every living thing the Frozen effect holds, a little bigger
 * than it, translucent so it shows through. Mob effects are not sent to other players, so the client reads the frost
 * counter the effect holds far above powder snow's ({@link FrozenEffect#iceBound}).
 */
public final class IceShellRenderer {
    private static final ContextKey<Boolean> ICE_BOUND = new ContextKey<>(GroundBurst.id("ice_bound"));
    private static final Identifier TEXTURE = GroundBurst.id("textures/entity/ice_shell.png");
    /** How much bigger than the creature the ice is, each side and over its head. */
    private static final float MARGIN = 0.32f, HEADROOM = 0.15f;   // wide enough for heads that stick out past the hitbox

    private IceShellRenderer() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterRenderStateModifiersEvent event) -> event.registerEntityModifier((Class) LivingEntityRenderer.class,
                (entity, state) -> ((LivingEntityRenderState) state).setRenderData(ICE_BOUND, FrozenEffect.iceBound((LivingEntity) entity))));
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Post<?, ?, ?> event) -> {
            LivingEntityRenderState state = event.getRenderState();
            if (!Boolean.TRUE.equals(state.getRenderData(ICE_BOUND))) {
                return;
            }
            float hw = state.boundingBoxWidth / 2 + MARGIN, h = state.boundingBoxHeight + HEADROOM;
            int light = state.lightCoords;
            event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.entityTranslucent(TEXTURE),
                    (pose, buffer) -> cube(pose, buffer, -hw, -0.02f, -hw, hw, h, hw, light));
        });
    }

    private static void cube(PoseStack.Pose pose, VertexConsumer b, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        quad(pose, b, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);   // top
        quad(pose, b, light, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);  // bottom
        quad(pose, b, light, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);  // north
        quad(pose, b, light, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);   // south
        quad(pose, b, light, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);  // west
        quad(pose, b, light, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);   // east
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer b, int light, float nx, float ny, float nz, float... v) {
        float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        for (int i = 0; i < 4; i++) {
            b.addVertex(pose, v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).setColor(255, 255, 255, 255).setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }
}
