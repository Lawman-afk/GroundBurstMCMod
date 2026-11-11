package dev.groundburst.legacy;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;

/**
 * NeoForge's item capability, on Forge 1.20.1: providers registered per item ({@link RegisterCapabilitiesEvent});
 * NeoForge's own (fluid and energy handlers: {@link Capabilities}) fall back to Forge's capability of the stack (a
 * bucket, another mod's cell).
 */
public final class ItemCapability<T, C> {
    private final ResourceLocation name;
    private final Class<T> typeClass;
    private final Class<C> contextClass;
    final @Nullable Capability<? extends T> forge;
    final Map<Item, List<ICapabilityProvider<ItemStack, C, T>>> byItem = new IdentityHashMap<>();

    private ItemCapability(ResourceLocation name, Class<T> typeClass, Class<C> contextClass, @Nullable Capability<? extends T> forge) {
        this.name = name;
        this.typeClass = typeClass;
        this.contextClass = contextClass;
        this.forge = forge;
    }

    public static <T, C> ItemCapability<T, C> create(ResourceLocation name, Class<T> typeClass, Class<C> contextClass) {
        return new ItemCapability<>(name, typeClass, contextClass, null);
    }

    public static <T> ItemCapability<T, @Nullable Void> createVoid(ResourceLocation name, Class<T> typeClass) {
        return new ItemCapability<>(name, typeClass, Void.class, null);
    }

    static <T> ItemCapability<T, @Nullable Void> forge(ResourceLocation name, Class<T> typeClass, Capability<? extends T> forge) {
        return new ItemCapability<>(name, typeClass, Void.class, forge);
    }

    public ResourceLocation name() {
        return this.name;
    }

    public Class<T> typeClass() {
        return this.typeClass;
    }

    public Class<C> contextClass() {
        return this.contextClass;
    }

    public @Nullable T fromProviders(ItemStack stack, C context) {
        if (stack.isEmpty()) {
            return null;
        }
        List<ICapabilityProvider<ItemStack, C, T>> providers = this.byItem.get(stack.getItem());
        if (providers != null) {
            for (ICapabilityProvider<ItemStack, C, T> p : providers) {
                T t = p.getCapability(stack, context);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    public @Nullable T getCapability(ItemStack stack, C context) {
        T t = fromProviders(stack, context);
        if (t != null || this.forge == null || stack.isEmpty() || CapabilityBridge.isBridged(stack.getItem())) {
            return t;
        }
        return stack.getCapability(this.forge).resolve().orElse(null);
    }

    void add(Item item, ICapabilityProvider<ItemStack, C, T> provider) {
        this.byItem.computeIfAbsent(item, i -> new ArrayList<>()).add(provider);
    }

    @Override
    public String toString() {
        return "ItemCapability[" + this.name + "]";
    }
}
