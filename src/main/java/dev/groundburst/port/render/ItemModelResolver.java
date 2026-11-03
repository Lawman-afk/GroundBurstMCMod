package dev.groundburst.port.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Minecraft 26.x's item model resolver, as the mod uses it: here it only notes what to draw (1.21.1 resolves it as it draws). */
public final class ItemModelResolver {
    public static final ItemModelResolver INSTANCE = new ItemModelResolver();

    private ItemModelResolver() {}

    public void updateForNonLiving(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Entity entity) {
        state.set(stack, context, entity.level(), null, entity.getId());
    }

    public void updateForLiving(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, LivingEntity entity) {
        state.set(stack, context, entity.level(), entity, entity.getId());
    }

    public void updateForTopItem(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, @Nullable Level level,
            @Nullable Object owner, int seed) {
        state.set(stack, context, level, owner instanceof LivingEntity l ? l : null, seed);
    }
}
