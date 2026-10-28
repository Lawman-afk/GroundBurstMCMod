package dev.groundburst.content.fallout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One fallout plume (DESIGN.md §21.8, slice 12b): a burst's debris cloud carried downwind, laying its fallout as it goes
 * ({@link Plumes}). It starts {@link #radius0} blocks across at the burst and spreads by half again over its
 * {@link #reach}; its strength along the way is {@code (1 - travelled / reach)^1.5} of the first.
 *
 * @param x            its centre now
 * @param y            the ground it began over (the cloud's height is reckoned from it)
 * @param z            its centre now
 * @param radius0      its radius at the burst, blocks
 * @param reach        how far it travels before it is spent, blocks
 * @param travelled    how far it has come
 * @param sinceDeposit blocks since it last laid a radiation source
 * @param peak0        its first source's peak dose rate, RAD/h
 * @param halfLife     its sources' half-life, ticks (the burst's own)
 * @param ashRate      Fallout Ash layers it lets fall a second at full strength
 */
public record FalloutPlume(double x, double y, double z, float radius0, float reach, float travelled, float sinceDeposit, float peak0,
        int halfLife, float ashRate) {
    public static final Codec<FalloutPlume> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("x").forGetter(FalloutPlume::x),
            Codec.DOUBLE.fieldOf("y").forGetter(FalloutPlume::y),
            Codec.DOUBLE.fieldOf("z").forGetter(FalloutPlume::z),
            Codec.FLOAT.fieldOf("radius0").forGetter(FalloutPlume::radius0),
            Codec.FLOAT.fieldOf("reach").forGetter(FalloutPlume::reach),
            Codec.FLOAT.fieldOf("travelled").forGetter(FalloutPlume::travelled),
            Codec.FLOAT.fieldOf("since_deposit").forGetter(FalloutPlume::sinceDeposit),
            Codec.FLOAT.fieldOf("peak0").forGetter(FalloutPlume::peak0),
            Codec.INT.fieldOf("half_life").forGetter(FalloutPlume::halfLife),
            Codec.FLOAT.fieldOf("ash_rate").forGetter(FalloutPlume::ashRate)
    ).apply(i, FalloutPlume::new));

    /** Its radius now, blocks. */
    public float radius() {
        return this.radius0 * (1 + 0.5f * this.travelled / this.reach);
    }

    /** Its strength now, 1 at the burst to 0 at the end of its reach. */
    public float share() {
        return (float) Math.pow(Math.max(0, 1 - this.travelled / this.reach), 1.5);
    }

    public boolean spent() {
        return this.travelled >= this.reach;
    }

    /** Carried {@code dx}, {@code dz} blocks. */
    FalloutPlume moved(double dx, double dz) {
        float step = (float) Math.sqrt(dx * dx + dz * dz);
        return new FalloutPlume(this.x + dx, this.y, this.z + dz, this.radius0, this.reach, this.travelled + step, this.sinceDeposit + step,
                this.peak0, this.halfLife, this.ashRate);
    }

    /** Having just laid a source. */
    FalloutPlume deposited() {
        return new FalloutPlume(this.x, this.y, this.z, this.radius0, this.reach, this.travelled, 0, this.peak0, this.halfLife, this.ashRate);
    }
}
