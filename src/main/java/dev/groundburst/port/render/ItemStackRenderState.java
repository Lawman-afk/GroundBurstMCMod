package dev.groundburst.port.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Minecraft 26.x's item resolved for drawing (set by {@link ItemModelResolver}), drawn by 1.21.1's item renderer. */
public final class ItemStackRenderState {
    ItemStack stack = ItemStack.EMPTY;
    ItemDisplayContext context = ItemDisplayContext.NONE;
    @Nullable Level level;
    @Nullable LivingEntity owner;
    int seed;

    public boolean isEmpty() {
        return this.stack.isEmpty();
    }

    public void clear() {
        this.stack = ItemStack.EMPTY;
    }

    void set(ItemStack stack, ItemDisplayContext context, @Nullable Level level, @Nullable LivingEntity owner, int seed) {
        this.stack = stack;
        this.context = context;
        this.level = level;
        this.owner = owner;
        this.seed = seed;
    }

    /**
     * The model's bounds as it is drawn (no display pose: centred as 1.21.1's item renderer centres it), from its quads;
     * a block about the origin for a model drawn by its own renderer.
     */
    public net.minecraft.world.phys.AABB getModelBoundingBox() {
        if (this.stack.isEmpty()) {
            return new net.minecraft.world.phys.AABB(0, 0, 0, 0, 0, 0);
        }
        Minecraft mc = Minecraft.getInstance();
        var model = mc.getItemRenderer().getModel(this.stack, this.level, this.owner, this.seed);
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        var random = net.minecraft.util.RandomSource.create(42);
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads = new java.util.ArrayList<>();
        for (var pass : model.getRenderPasses(this.stack, true)) {
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                quads.addAll(pass.getQuads(null, side, random));
            }
            quads.addAll(pass.getQuads(null, null, random));
        }
        int stride = com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK.getVertexSize() / 4;
        for (var quad : quads) {
            int[] v = quad.getVertices();
            for (int i = 0; i + 2 < v.length; i += stride) {
                for (int a = 0; a < 3; a++) {
                    float p = Float.intBitsToFloat(v[i + a]) - 0.5F;
                    min[a] = Math.min(min[a], p);
                    max[a] = Math.max(max[a], p);
                }
            }
        }
        if (min[0] > max[0]) {
            return new net.minecraft.world.phys.AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);
        }
        return new net.minecraft.world.phys.AABB(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, int outlineColor) {
        if (this.stack.isEmpty()) {
            return;
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(this.owner, this.stack, this.context, false, poseStack, collector.buffers(),
                this.level, lightCoords, overlayCoords, this.seed);
    }
}
