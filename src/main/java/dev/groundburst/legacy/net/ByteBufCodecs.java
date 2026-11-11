package dev.groundburst.legacy.net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;

/** Minecraft 1.21's basic stream codecs, over 1.20.1's FriendlyByteBuf. */
public final class ByteBufCodecs {
    private ByteBufCodecs() {}

    static FriendlyByteBuf wrap(ByteBuf buf) {
        return buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
    }

    public static final StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
    public static final StreamCodec<ByteBuf, Byte> BYTE = StreamCodec.of((b, v) -> b.writeByte(v), ByteBuf::readByte);
    public static final StreamCodec<ByteBuf, Short> SHORT = StreamCodec.of((b, v) -> b.writeShort(v), ByteBuf::readShort);
    public static final StreamCodec<ByteBuf, Integer> INT = StreamCodec.of(ByteBuf::writeInt, ByteBuf::readInt);
    public static final StreamCodec<ByteBuf, Integer> VAR_INT = StreamCodec.of((b, v) -> wrap(b).writeVarInt(v), b -> wrap(b).readVarInt());
    public static final StreamCodec<ByteBuf, Long> LONG = StreamCodec.of(ByteBuf::writeLong, ByteBuf::readLong);
    public static final StreamCodec<ByteBuf, Long> VAR_LONG = StreamCodec.of((b, v) -> wrap(b).writeVarLong(v), b -> wrap(b).readVarLong());
    public static final StreamCodec<ByteBuf, Float> FLOAT = StreamCodec.of(ByteBuf::writeFloat, ByteBuf::readFloat);
    public static final StreamCodec<ByteBuf, Double> DOUBLE = StreamCodec.of(ByteBuf::writeDouble, ByteBuf::readDouble);
    public static final StreamCodec<ByteBuf, byte[]> BYTE_ARRAY = StreamCodec.of((b, v) -> wrap(b).writeByteArray(v), b -> wrap(b).readByteArray());
    public static final StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    public static final StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = StreamCodec.of((b, v) -> wrap(b).writeNbt(v), b -> {
        CompoundTag tag = wrap(b).readNbt();
        return tag == null ? new CompoundTag() : tag;
    });

    public static StreamCodec<ByteBuf, String> stringUtf8(int max) {
        return StreamCodec.of((b, v) -> wrap(b).writeUtf(v, max), b -> wrap(b).readUtf(max));
    }

    public static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(final StreamCodec<B, V> codec) {
        return StreamCodec.of((b, v) -> {
            b.writeBoolean(v.isPresent());
            v.ifPresent(x -> codec.encode(b, x));
        }, b -> b.readBoolean() ? Optional.of(codec.decode(b)) : Optional.empty());
    }

    public static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> make, StreamCodec<? super B, V> codec) {
        return collection(make, codec, Integer.MAX_VALUE);
    }

    public static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> make, StreamCodec<? super B, V> codec,
            int max) {
        return StreamCodec.of((b, c) -> {
            if (c.size() > max) {
                throw new IllegalStateException(c.size() + " elements, at most " + max);
            }
            wrap(b).writeVarInt(c.size());
            for (V v : c) {
                codec.encode(b, v);
            }
        }, b -> {
            int n = wrap(b).readVarInt();
            if (n > max) {
                throw new IllegalStateException(n + " elements, at most " + max);
            }
            C c = make.apply(Math.min(n, 65536));
            for (int i = 0; i < n; i++) {
                c.add(codec.decode(b));
            }
            return c;
        });
    }

    public static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return codec -> collection(ArrayList::new, codec);
    }

    public static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list(int max) {
        return codec -> collection(ArrayList::new, codec, max);
    }

    public static <T> StreamCodec<ByteBuf, T> idMapper(IntFunction<T> byId, ToIntFunction<T> toId) {
        return StreamCodec.of((b, v) -> wrap(b).writeVarInt(toId.applyAsInt(v)), b -> byId.apply(wrap(b).readVarInt()));
    }

    public static <T> StreamCodec<ByteBuf, T> idMapper(IdMap<T> map) {
        return idMapper(map::byIdOrThrow, map::getId);
    }

    /** A holder of a built-in registry's object, by its number in the registry. */
    @SuppressWarnings("unchecked")
    public static <T> StreamCodec<FriendlyByteBuf, Holder<T>> holderRegistry(ResourceKey<? extends Registry<T>> key) {
        return StreamCodec.of((b, h) -> {
            Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
            b.writeVarInt(registry.getId(h.value()));
        }, b -> {
            Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
            T value = registry.byIdOrThrow(b.readVarInt());
            return registry.wrapAsHolder(value);
        });
    }

    /** A built-in registry's object, by its number in the registry. */
    @SuppressWarnings("unchecked")
    public static <T> StreamCodec<FriendlyByteBuf, T> registry(ResourceKey<? extends Registry<T>> key) {
        return StreamCodec.of((b, v) -> {
            Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
            b.writeVarInt(registry.getId(v));
        }, b -> {
            Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
            return registry.byIdOrThrow(b.readVarInt());
        });
    }

    /** A value by a codec, sent as NBT (1.21's fromCodec). */
    public static <T> StreamCodec<ByteBuf, T> fromCodec(com.mojang.serialization.Codec<T> codec) {
        return StreamCodec.of((b, v) -> wrap(b).writeJsonWithCodec(codec, v), b -> wrap(b).readJsonWithCodec(codec));
    }

    public static <T> StreamCodec<FriendlyByteBuf, T> fromCodecWithRegistries(com.mojang.serialization.Codec<T> codec) {
        return StreamCodec.of((b, v) -> b.writeJsonWithCodec(codec, v), b -> b.readJsonWithCodec(codec));
    }
}
