package dev.groundzero.content.nuclear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How far a uranium fuel rod has burned (the {@code groundzero:fuel_burnup} item component, DESIGN.md §7): ticks of full
 * flux it has taken, out of its own lifetime. Each rod's lifetime is drawn when it first burns, a little either side of
 * the average, so a core loaded all at once does not run out all at once. The rod carries it, so a half-burned rod taken
 * out and put back is still half burned.
 */
public record FuelBurnup(float used, int life) {
    public static final Codec<FuelBurnup> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("used").forGetter(FuelBurnup::used),
            Codec.INT.fieldOf("life").forGetter(FuelBurnup::life)).apply(i, FuelBurnup::new));
    public static final StreamCodec<ByteBuf, FuelBurnup> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, FuelBurnup::used, ByteBufCodecs.VAR_INT, FuelBurnup::life, FuelBurnup::new);

    /** How much of the rod is used, 0 to 1. */
    public float fraction() {
        return this.life <= 0 ? 1 : Math.min(1, this.used / this.life);
    }

    public boolean spent() {
        return this.used >= this.life;
    }

    public FuelBurnup burn(float ticks) {
        return new FuelBurnup(this.used + ticks, this.life);
    }
}
