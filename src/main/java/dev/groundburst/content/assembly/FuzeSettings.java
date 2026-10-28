package dev.groundburst.content.assembly;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A warhead's fuze and its one setting (the {@code groundburst:fuze} item component, and kept by the placed warhead). */
public record FuzeSettings(Fuze fuze, int value) {
    public static final FuzeSettings IMPACT = new FuzeSettings(Fuze.IMPACT, 0);

    public static final Codec<FuzeSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(Fuze::byId, f -> f.id).fieldOf("fuze").forGetter(FuzeSettings::fuze),
            Codec.INT.optionalFieldOf("value", 0).forGetter(FuzeSettings::value)).apply(i, FuzeSettings::of));
    public static final StreamCodec<ByteBuf, FuzeSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(n -> Fuze.values()[Math.clamp(n, 0, Fuze.values().length - 1)], Fuze::ordinal), FuzeSettings::fuze,
            ByteBufCodecs.VAR_INT, FuzeSettings::value, FuzeSettings::of);

    /** A fuze with its setting kept in range. */
    public static FuzeSettings of(Fuze fuze, int value) {
        return new FuzeSettings(fuze, fuze.setting.clamp(value));
    }

    /** A freshly fitted fuze, at its setting's default. */
    public static FuzeSettings fresh(Fuze fuze) {
        return of(fuze, fuze.setting.fallback);
    }

    public FuzeSettings with(int value) {
        return of(this.fuze, value);
    }
}
