package dev.groundburst.legacy;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/**
 * NeoForge's data map: a value for objects of a registry, read from data/&lt;namespace&gt;/data_maps/&lt;registry&gt;/&lt;name&gt;.json
 * ({@link DataMaps} loads it on Forge 1.20.1). The port writes {@code holder.getData(type)} as {@code type.get(holder)}.
 */
public final class DataMapType<R, T> {
    private final ResourceLocation id;
    private final ResourceKey<? extends Registry<R>> registryKey;
    final Codec<T> codec;
    /** As read: values by object id and by tag. */
    final Map<ResourceLocation, T> byId = new HashMap<>();
    final Map<TagKey<R>, T> byTag = new HashMap<>();
    /** By object, once the tags are known. */
    private volatile @Nullable Map<R, T> resolved;

    private DataMapType(ResourceLocation id, ResourceKey<? extends Registry<R>> registryKey, Codec<T> codec) {
        this.id = id;
        this.registryKey = registryKey;
        this.codec = codec;
    }

    public static <R, T> Builder<R, T> builder(ResourceLocation id, ResourceKey<? extends Registry<R>> registryKey, Codec<T> codec) {
        return new Builder<>(id, registryKey, codec);
    }

    public ResourceLocation id() {
        return this.id;
    }

    public ResourceKey<? extends Registry<R>> registryKey() {
        return this.registryKey;
    }

    /** The folder its files are read from, under data/&lt;namespace&gt;/. */
    String folder() {
        ResourceLocation r = this.registryKey.location();
        return "data_maps/" + ("minecraft".equals(r.getNamespace()) ? r.getPath() : r.getNamespace() + "/" + r.getPath());
    }

    public @Nullable T get(Holder<R> holder) {
        return get(holder.value());
    }

    public @Nullable T get(R object) {
        Map<R, T> map = this.resolved;
        if (map == null) {
            map = resolve();
        }
        return map.get(object);
    }

    /** The values by object: an id's own, else its tags'. */
    @SuppressWarnings("unchecked")
    synchronized Map<R, T> resolve() {
        Map<R, T> map = new IdentityHashMap<>();
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.get(this.registryKey.location());
        if (registry != null) {
            for (Map.Entry<TagKey<R>, T> e : this.byTag.entrySet()) {
                registry.getTag(e.getKey()).ifPresent(set -> set.forEach(h -> map.put(h.value(), e.getValue())));
            }
            for (Map.Entry<ResourceLocation, T> e : this.byId.entrySet()) {
                registry.getOptional(e.getKey()).ifPresent(o -> map.put(o, e.getValue()));
            }
        }
        this.resolved = map;
        return map;
    }

    synchronized void clear() {
        this.byId.clear();
        this.byTag.clear();
        this.resolved = null;
    }

    void stale() {
        this.resolved = null;
    }

    public static final class Builder<R, T> {
        private final ResourceLocation id;
        private final ResourceKey<? extends Registry<R>> registryKey;
        private final Codec<T> codec;

        private Builder(ResourceLocation id, ResourceKey<? extends Registry<R>> registryKey, Codec<T> codec) {
            this.id = id;
            this.registryKey = registryKey;
            this.codec = codec;
        }

        public Builder<R, T> synced(Codec<T> networkCodec, boolean mandatory) {
            return this;
        }

        public DataMapType<R, T> build() {
            return new DataMapType<>(this.id, this.registryKey, this.codec);
        }
    }
}
