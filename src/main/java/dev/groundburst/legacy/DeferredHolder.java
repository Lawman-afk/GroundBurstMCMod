package dev.groundburst.legacy;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.RegistryObject;

/**
 * NeoForge's handle on a registered object, over Forge 1.20.1's RegistryObject (or, for what 1.20.1 has no registry of,
 * such as the item data components, the object itself). Unlike NeoForge's it is not itself a Holder (Forge's Holder is
 * a Supplier of the registry's type, and this is a Supplier of the object's own): {@link #getDelegate()} gives one.
 */
public class DeferredHolder<R, T extends R> implements Supplier<T> {
    protected final ResourceKey<R> key;
    private final @Nullable RegistryObject<T> object;
    private final @Nullable T direct;

    public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<? extends Registry<R>> registryKey, ResourceLocation valueName) {
        return create(ResourceKey.create(registryKey, valueName));
    }

    public static <R, T extends R> DeferredHolder<R, T> create(ResourceLocation registryName, ResourceLocation valueName) {
        return create(ResourceKey.create(ResourceKey.createRegistryKey(registryName), valueName));
    }

    public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<R> key) {
        return new DeferredHolder<>(key);
    }

    /** A holder of an object 1.20.1 keeps in no registry (an item data component): the object itself, made now. */
    public static <R, T extends R> DeferredHolder<R, T> direct(ResourceKey<R> key, T value) {
        return new DeferredHolder<>(key, value);
    }

    /**
     * A holder of an object registered elsewhere, found by its name (Forge binds such a handle once its registry has been
     * filled, not as the registry fills: an object registered here is held through its register's own handle instead).
     */
    protected DeferredHolder(ResourceKey<R> key) {
        this(key, RegistryObject.create(key.location(), ResourceKey.<R>createRegistryKey(key.registry()), key.location().getNamespace()));
    }

    /**
     * A holder of what Forge's register registers under {@code object}: bound the moment it is registered, so an object
     * registered after it in the same registry can use it as it is made (stairs of their block, a block's item), as
     * NeoForge's holders can.
     */
    @SuppressWarnings("unchecked")
    protected DeferredHolder(ResourceKey<R> key, RegistryObject<? extends R> object) {
        this.key = key;
        this.object = (RegistryObject<T>) object;
        this.direct = null;
    }

    private DeferredHolder(ResourceKey<R> key, T value) {
        this.key = key;
        this.object = null;
        this.direct = value;
    }

    public T value() {
        return get();
    }

    @Override
    public T get() {
        if (this.direct != null) {
            return this.direct;
        }
        return Objects.requireNonNull(this.object).get();
    }

    public Optional<T> asOptional() {
        return isBound() ? Optional.of(get()) : Optional.empty();
    }

    public ResourceLocation getId() {
        return this.key.location();
    }

    public ResourceKey<R> getKey() {
        return this.key;
    }

    public Optional<ResourceKey<R>> unwrapKey() {
        return Optional.of(this.key);
    }

    public boolean isBound() {
        return this.direct != null || Objects.requireNonNull(this.object).isPresent();
    }

    public boolean is(ResourceLocation id) {
        return id.equals(this.key.location());
    }

    public boolean is(ResourceKey<R> key) {
        return key == this.key || key.equals(this.key);
    }

    @SuppressWarnings("unchecked")
    public boolean is(TagKey<R> tag) {
        Holder<R> h = getDelegate();
        return h != null && h.is(tag);
    }

    /** The registry's own holder of the object (a direct holder for one kept in no registry). */
    @SuppressWarnings("unchecked")
    public Holder<R> getDelegate() {
        if (this.direct != null) {
            return Holder.direct(this.direct);
        }
        return (Holder<R>) Objects.requireNonNull(this.object).getHolder().orElseThrow(
                () -> new IllegalStateException("Not registered yet: " + this.key));
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof DeferredHolder<?, ?> h && h.key.equals(this.key);
    }

    @Override
    public int hashCode() {
        return this.key.hashCode();
    }

    @Override
    public String toString() {
        return "DeferredHolder{" + this.key + "}";
    }
}
