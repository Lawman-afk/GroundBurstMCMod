package dev.groundburst.port;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Minecraft 26.x's saved data type (NeoForge's, with the level given to its factories): an id, how to make one, and the
 * codec it is saved with, over 1.21.1's data storage (a factory and a file name; saved as the codec's value under
 * "data"). The mod's {@code level.getDataStorage().computeIfAbsent(TYPE)} becomes {@code TYPE.get(level)}.
 */
public final class SavedDataType<T extends SavedData> {
    @FunctionalInterface
    public interface Factory<T> {
        T create(@Nullable ServerLevel level);
    }

    private final ResourceLocation id;
    private final Factory<T> factory;
    private final Factory<Codec<T>> codecFactory;

    public SavedDataType(ResourceLocation id, Supplier<T> constructor, Codec<T> codec, @Nullable DataFixTypes dataFixType) {
        this(id, ignored -> constructor.get(), ignored -> codec);
    }

    public SavedDataType(ResourceLocation id, Supplier<T> constructor, Codec<T> codec) {
        this(id, ignored -> constructor.get(), ignored -> codec);
    }

    public SavedDataType(ResourceLocation id, Factory<T> constructor, Factory<Codec<T>> codec) {
        this.id = id;
        this.factory = constructor;
        this.codecFactory = codec;
    }

    public ResourceLocation id() {
        return this.id;
    }

    /** Its file's name in the level's data folder. */
    public String name() {
        return this.id.getNamespace() + "_" + this.id.getPath().replace('/', '_');
    }

    private SavedData.Factory<T> factory(ServerLevel level) {
        return new SavedData.Factory<>(() -> this.factory.create(level), (tag, registries) -> {
            var data = tag.get("data");
            if (data == null) {
                return this.factory.create(level);
            }
            return this.codecFactory.create(level).parse(registries.createSerializationContext(NbtOps.INSTANCE), data).result()
                    .orElseGet(() -> this.factory.create(level));
        }, null);
    }

    /** The level's, made if it has none yet. */
    public T get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(level), name());
    }

    /** The level's, if it has one. */
    public @Nullable T find(ServerLevel level) {
        return level.getDataStorage().get(factory(level), name());
    }

    /** Saves {@code data} into {@code tag} with its codec. */
    public CompoundTag save(T data, CompoundTag tag, HolderLookup.Provider registries) {
        this.codecFactory.create(null).encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), data).result()
                .ifPresent(t -> tag.put("data", t));
        return tag;
    }
}
