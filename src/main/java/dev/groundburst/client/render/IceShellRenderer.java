package dev.groundburst.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.effect.FrozenEffect;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Frozen solid (DESIGN.md §16.4): a block of ice drawn round every living thing the Frozen effect holds, a little bigger
 * than it, translucent so it shows through. Mob effects are not sent to other players, so the client reads the frost
 * counter the effect holds far above powder snow's ({@link FrozenEffect#iceBound}).
 */
public final class IceShellRenderer {
    private static final ResourceLocation TEXTURE = GroundBurst.id("textures/entity/ice_shell.png");
    /** How much bigger than the creature the ice is, each side and over its head. */
    private static final float MARGIN = 0.32f, HEADROOM = 0.15f;   // wide enough for heads that stick out past the hitbox

    private IceShellRenderer() {}

    public static void register(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener((RenderLivingEvent.Post<?, ?> event) -> {
            LivingEntity entity = event.getEntity();
            if (!FrozenEffect.iceBound(entity)) {
                return;
            }
            float hw = entity.getBbWidth() / 2 + MARGIN, h = entity.getBbHeight() + HEADROOM;
            VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(TEXTURE));
            cube(event.getPoseStack().last(), buffer, -hw, -0.02f, -hw, hw, h, hw, event.getPackedLight());
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
            b.vertex(dev.groundburst.legacy.Verts.matrix(pose), v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).color(255, 255, 255, 255).uv(uv[i][0], uv[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(dev.groundburst.legacy.Verts.normal(pose), nx, ny, nz).endVertex();
        }
    }
}
