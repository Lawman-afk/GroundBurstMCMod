package dev.groundburst.legacy.net;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;

import io.netty.buffer.ByteBuf;

/**
 * Minecraft 1.21's stream codec (1.20.1 writes packets by hand into a FriendlyByteBuf): how the mod's payloads and item
 * components go over the network, the same calls as 1.21's.
 */
public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {
    static <B, V> StreamCodec<B, V> of(final StreamEncoder<B, V> encoder, final StreamDecoder<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.encode(buf, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(final StreamMemberEncoder<B, V> encoder, final StreamDecoder<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.encode(value, buf);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(final V value) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return value;
            }

            @Override
            public void encode(B buf, V v) {
                if (!v.equals(value)) {
                    throw new IllegalStateException("Can't encode '" + v + "', expected '" + value + "'");
                }
            }
        };
    }

    default <O> StreamCodec<B, O> apply(CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    default <O> StreamCodec<B, O> map(final Function<? super V, ? extends O> to, final Function<? super O, ? extends V> from) {
        StreamCodec<B, V> self = this;
        return new StreamCodec<>() {
            @Override
            public O decode(B buf) {
                return to.apply(self.decode(buf));
            }

            @Override
            public void encode(B buf, O value) {
                self.encode(buf, from.apply(value));
            }
        };
    }

    default <O extends ByteBuf> StreamCodec<O, V> mapStream(final Function<O, ? extends B> f) {
        StreamCodec<B, V> self = this;
        return new StreamCodec<>() {
            @Override
            public V decode(O buf) {
                return self.decode(f.apply(buf));
            }

            @Override
            public void encode(O buf, V value) {
                self.encode(f.apply(buf), value);
            }
        };
    }

    default <U> StreamCodec<B, U> dispatch(final Function<? super U, ? extends V> keyOf,
            final Function<? super V, ? extends StreamCodec<? super B, ? extends U>> codecOf) {
        StreamCodec<B, V> self = this;
        return new StreamCodec<>() {
            @Override
            public U decode(B buf) {
                V key = self.decode(buf);
                StreamCodec<? super B, ? extends U> codec = codecOf.apply(key);
                return codec.decode(buf);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void encode(B buf, U value) {
                V key = keyOf.apply(value);
                StreamCodec<B, U> codec = (StreamCodec<B, U>) codecOf.apply(key);
                self.encode(buf, key);
                codec.encode(buf, value);
            }
        };
    }

    @SuppressWarnings("unchecked")
    default <S extends B> StreamCodec<S, V> cast() {
        return (StreamCodec<S, V>) this;
    }

    static <B, C, T1> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> c1, final Function<C, T1> g1, final Function<T1, C> make) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                return make.apply(c1.decode(buf));
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
            }
        };
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> c1, final Function<C, T1> g1,
            final StreamCodec<? super B, T2> c2, final Function<C, T2> g2, final java.util.function.BiFunction<T1, T2, C> make) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 v1 = c1.decode(buf);
                T2 v2 = c2.decode(buf);
                return make.apply(v1, v2);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
                c2.encode(buf, g2.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> c1, final Function<C, T1> g1,
            final StreamCodec<? super B, T2> c2, final Function<C, T2> g2, final StreamCodec<? super B, T3> c3, final Function<C, T3> g3,
            final Function3<T1, T2, T3, C> make) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 v1 = c1.decode(buf);
                T2 v2 = c2.decode(buf);
                T3 v3 = c3.decode(buf);
                return make.apply(v1, v2, v3);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
                c2.encode(buf, g2.apply(value));
                c3.encode(buf, g3.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> c1, final Function<C, T1> g1,
            final StreamCodec<? super B, T2> c2, final Function<C, T2> g2, final StreamCodec<? super B, T3> c3, final Function<C, T3> g3,
            final StreamCodec<? super B, T4> c4, final Function<C, T4> g4, final Function4<T1, T2, T3, T4, C> make) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 v1 = c1.decode(buf);
                T2 v2 = c2.decode(buf);
                T3 v3 = c3.decode(buf);
                T4 v4 = c4.decode(buf);
                return make.apply(v1, v2, v3, v4);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
                c2.encode(buf, g2.apply(value));
                c3.encode(buf, g3.apply(value));
                c4.encode(buf, g4.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> c1, final Function<C, T1> g1,
            final StreamCodec<? super B, T2> c2, final Function<C, T2> g2, final StreamCodec<? super B, T3> c3, final Function<C, T3> g3,
            final StreamCodec<? super B, T4> c4, final Function<C, T4> g4, final StreamCodec<? super B, T5> c5, final Function<C, T5> g5,
            final Function5<T1, T2, T3, T4, T5, C> make) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 v1 = c1.decode(buf);
                T2 v2 = c2.decode(buf);
                T3 v3 = c3.decode(buf);
                T4 v4 = c4.decode(buf);
                T5 v5 = c5.decode(buf);
                return make.apply(v1, v2, v3, v4, v5);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
                c2.encode(buf, g2.apply(value));
                c3.encode(buf, g3.apply(value));
                c4.encode(buf, g4.apply(value));
                c5.encode(buf, g5.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> c1, final Function<C, T1> g1,
            final StreamCodec<? super B, T2> c2, final Function<C, T2> g2, final StreamCodec<? super B, T3> c3, final Function<C, T3> g3,
            final StreamCodec<? super B, T4> c4, final Function<C, T4> g4, final StreamCodec<? super B, T5> c5, final Function<C, T5> g5,
            final StreamCodec<? super B, T6> c6, final Function<C, T6> g6, final Function6<T1, T2, T3, T4, T5, T6, C> make) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 v1 = c1.decode(buf);
                T2 v2 = c2.decode(buf);
                T3 v3 = c3.decode(buf);
                T4 v4 = c4.decode(buf);
                T5 v5 = c5.decode(buf);
                T6 v6 = c6.decode(buf);
                return make.apply(v1, v2, v3, v4, v5, v6);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
                c2.encode(buf, g2.apply(value));
                c3.encode(buf, g3.apply(value));
                c4.encode(buf, g4.apply(value));
                c5.encode(buf, g5.apply(value));
                c6.encode(buf, g6.apply(value));
            }
        };
    }

    static <B, T> StreamCodec<B, T> recursive(final UnaryOperator<StreamCodec<B, T>> make) {
        return new StreamCodec<>() {
            private final java.util.function.Supplier<StreamCodec<B, T>> inner =
                    com.google.common.base.Suppliers.memoize(() -> make.apply(this));

            @Override
            public T decode(B buf) {
                return this.inner.get().decode(buf);
            }

            @Override
            public void encode(B buf, T value) {
                this.inner.get().encode(buf, value);
            }
        };
    }

    @FunctionalInterface
    interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> codec);
    }
}
