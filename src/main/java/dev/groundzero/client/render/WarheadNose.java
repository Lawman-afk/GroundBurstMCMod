package dev.groundzero.client.render;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.groundzero.content.ordnance.charge.ChargeBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A missile's warhead, drawn from the warhead's own block model standing on the missile's body: on the missile as an
 * item (WarheadSpecialRenderer, so also in flight and on the assembly screens) and on the launchers' and the hall's
 * models (MachineGeoRenderer, at the model's warhead bone). Placed by {@link WarheadMounts}.
 */
public final class WarheadNose {
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    /** Each warhead's model, armed or not, as the game draws a block held or carried (render thread only). */
    private static final Map<BlockState, BlockModelRenderState> MODELS = new HashMap<>();

    private WarheadNose() {}

    /** Forgets the models (the resources were reloaded). */
    static void forget() {
        Minecraft.getInstance().execute(MODELS::clear);
    }

    /**
     * Draws {@code warhead} standing on a missile of {@code airframe}: the pose's origin is the middle of the top of the
     * missile's body, its unit sixteen of the missile's item pixels, its up the missile's axis. The warhead keeps its
     * own proportions however the missile is drawn stretched along its length (a Light body is squatter than it is
     * wide): it takes the missile's scale across as its own. Draws nothing for a warhead the airframe does not carry.
     */
    public static void submit(PoseStack pose, SubmitNodeCollector collector, Block warhead, String airframe, boolean armed, int light,
            int overlay, int outline) {
        WarheadMounts.Mount mount = WarheadMounts.get(airframe, BuiltInRegistries.BLOCK.getKey(warhead).getPath());
        if (mount == null) {
            return;
        }
        BlockState state = warhead.defaultBlockState();
        if (state.hasProperty(ChargeBlock.ARMED)) {
            state = state.setValue(ChargeBlock.ARMED, armed);
        }
        if (state.hasProperty(ChargeBlock.FACING)) {
            state = state.setValue(ChargeBlock.FACING, Direction.NORTH);
        }
        BlockModelRenderState model = MODELS.computeIfAbsent(state, s -> {
            BlockModelRenderState r = new BlockModelRenderState();
            Minecraft.getInstance().getBlockModelResolver().update(r, s, CONTEXT);
            return r;
        });
        pose.pushPose();
        Matrix4f m = pose.last().pose();
        float across = new Vector3f(m.m00(), m.m01(), m.m02()).length();
        float along = new Vector3f(m.m10(), m.m11(), m.m12()).length();
        if (along > 1e-6f && across > 1e-6f) {
            pose.scale(1, across / along, 1);
        }
        pose.scale(mount.scale(), mount.scale(), mount.scale());
        pose.translate(-0.5f, 0, -0.5f);                    // the block model's middle on the missile's axis
        model.submit(pose, collector, light, overlay, outline);
        pose.popPose();
    }
}
