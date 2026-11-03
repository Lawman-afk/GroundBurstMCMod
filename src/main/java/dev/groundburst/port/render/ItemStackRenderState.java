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

    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, int outlineColor) {
        if (this.stack.isEmpty()) {
            return;
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(this.owner, this.stack, this.context, false, poseStack, collector.buffers(),
                this.level, lightCoords, overlayCoords, this.seed);
    }
}
