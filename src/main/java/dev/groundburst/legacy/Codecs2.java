package dev.groundburst.legacy;

import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;

/** Codec building 1.20.1's DataFixerUpper lacks. */
public final class Codecs2 {
    private Codecs2() {}

    /** Reads with {@code primary}, else with {@code alternative} (written with the primary). */
    public static <T> Codec<T> withAlternative(Codec<T> primary, Codec<? extends T> alternative) {
        return Codec.either(primary, alternative).xmap(e -> e.map(Function.identity(), Function.identity()), Either::left);
    }

    /** Reads with {@code primary}, else with {@code alternative} turned by {@code converter} (written with the primary). */
    public static <T, U> Codec<T> withAlternative(Codec<T> primary, Codec<U> alternative, Function<U, T> converter) {
        return Codec.either(primary, alternative).xmap(e -> e.map(Function.identity(), converter), Either::left);
    }
}
