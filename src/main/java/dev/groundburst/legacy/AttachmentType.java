package dev.groundburst.legacy;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * NeoForge's data attachment: a value kept on an entity or a chunk, saved with it by its codec and (if it says so) sent
 * to players. On Forge 1.20.1 every entity and chunk carries a store of them as a Forge capability ({@link Attachments}).
 */
public final class AttachmentType<T> {
    private @Nullable ResourceLocation id;
    final Function<Object, T> defaultValue;
    final @Nullable Codec<T> codec;
    final Predicate<? super T> shouldSerialize;
    final boolean copyOnDeath;
    final @Nullable BiPredicate<Object, ServerPlayer> syncTo;
    final @Nullable StreamCodec<? super FriendlyByteBuf, T> syncCodec;

    private AttachmentType(Builder<T> b) {
        this.defaultValue = b.defaultValue;
        this.codec = b.codec;
        this.shouldSerialize = b.shouldSerialize;
        this.copyOnDeath = b.copyOnDeath;
        this.syncTo = b.syncTo;
        this.syncCodec = b.syncCodec;
    }

    public static <T> Builder<T> builder(Supplier<T> defaultValue) {
        return new Builder<>(holder -> defaultValue.get());
    }

    public static <T> Builder<T> builder(Function<Object, T> defaultValue) {
        return new Builder<>(defaultValue);
    }

    /** Its name (given as it is registered: {@link Attachments#name}). */
    public ResourceLocation id() {
        if (this.id == null) {
            throw new IllegalStateException("An attachment type was used before it was registered");
        }
        return this.id;
    }

    void name(ResourceLocation id) {
        this.id = id;
    }

    boolean named() {
        return this.id != null;
    }

    public static final class Builder<T> {
        private final Function<Object, T> defaultValue;
        private @Nullable Codec<T> codec;
        private Predicate<? super T> shouldSerialize = t -> true;
        private boolean copyOnDeath;
        private @Nullable BiPredicate<Object, ServerPlayer> syncTo;
        private @Nullable StreamCodec<? super FriendlyByteBuf, T> syncCodec;

        private Builder(Function<Object, T> defaultValue) {
            this.defaultValue = defaultValue;
        }

        public Builder<T> serialize(Codec<T> codec) {
            this.codec = codec;
            return this;
        }

        public Builder<T> serialize(Codec<T> codec, Predicate<? super T> shouldSerialize) {
            this.codec = codec;
            this.shouldSerialize = shouldSerialize;
            return this;
        }

        public Builder<T> copyOnDeath() {
            this.copyOnDeath = true;
            return this;
        }

        public Builder<T> sync(StreamCodec<? super FriendlyByteBuf, T> codec) {
            return sync((holder, to) -> true, codec);
        }

        public Builder<T> sync(BiPredicate<Object, ServerPlayer> sendToPlayer, StreamCodec<? super FriendlyByteBuf, T> codec) {
            this.syncTo = sendToPlayer;
            this.syncCodec = codec;
            return this;
        }

        public AttachmentType<T> build() {
            return new AttachmentType<>(this);
        }
    }
}
