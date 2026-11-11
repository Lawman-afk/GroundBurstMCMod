package dev.groundburst.legacy.component;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft 1.21's item data component, on 1.20.1: kept in the item's NBT, under its own id (so a stack's components
 * stack and compare as its tag does), written by its codec; vanilla's few the mod touches keep 1.20.1's own places in
 * the tag ({@link DataComponents}).
 */
public class DataComponentType<T> {
    private @Nullable ResourceLocation id;
    private final @Nullable Codec<T> codec;
    private final @Nullable StreamCodec<? super FriendlyByteBuf, T> streamCodec;

    protected DataComponentType(@Nullable ResourceLocation id, @Nullable Codec<T> codec, @Nullable StreamCodec<? super FriendlyByteBuf, T> streamCodec) {
        this.id = id;
        this.codec = codec;
        this.streamCodec = streamCodec;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public @Nullable Codec<T> codec() {
        return this.codec;
    }

    public Codec<T> codecOrThrow() {
        return Objects.requireNonNull(this.codec, () -> "No codec for " + this.id);
    }

    public @Nullable StreamCodec<? super FriendlyByteBuf, T> streamCodec() {
        return this.streamCodec;
    }

    public boolean isTransient() {
        return this.codec == null;
    }

    public ResourceLocation id() {
        return Objects.requireNonNull(this.id);
    }

    /** The key it is kept under in an item's tag. */
    protected String key() {
        return id().toString();
    }

    /** Its value in {@code stack} (null: none there). */
    public @Nullable T read(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(key()) || this.codec == null) {
            return null;
        }
        return this.codec.parse(NbtOps.INSTANCE, tag.get(key())).result().orElse(null);
    }

    /** Puts {@code value} in {@code stack} (null takes it out). */
    public void write(ItemStack stack, @Nullable T value) {
        if (value == null) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(key());
                if (tag.isEmpty()) {
                    stack.setTag(null);
                }
            }
            return;
        }
        if (this.codec == null) {
            return;
        }
        Tag encoded = this.codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
        if (encoded != null) {
            stack.getOrCreateTag().put(key(), encoded);
        }
    }

    public boolean present(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(key());
    }

    @Override
    public String toString() {
        return "DataComponentType[" + this.id + "]";
    }

    public static class Builder<T> {
        private @Nullable Codec<T> codec;
        private @Nullable StreamCodec<? super FriendlyByteBuf, T> streamCodec;

        public Builder<T> persistent(Codec<T> codec) {
            this.codec = codec;
            return this;
        }

        public Builder<T> networkSynchronized(StreamCodec<? super FriendlyByteBuf, T> streamCodec) {
            this.streamCodec = streamCodec;
            return this;
        }

        public Builder<T> cacheEncoding() {
            return this;
        }

        public DataComponentType<T> build(ResourceLocation id) {
            return new DataComponentType<>(id, this.codec, this.streamCodec);
        }

        public DataComponentType<T> build() {
            return new DataComponentType<>(null, this.codec, this.streamCodec);
        }
    }
}
