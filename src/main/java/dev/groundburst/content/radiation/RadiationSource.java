package dev.groundburst.content.radiation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * One source of radiation in a level (DESIGN.md §21.8): what a nuclear burst, a fizzle, a dirty blast or a meltdown
 * leaves behind. Its dose rate is greatest at its centre and falls to nothing at its edge,
 * {@code peak x (1 - d / radius)^2}, and the whole of it halves every {@link #halfLife} ticks: fresh fission products
 * burn out fast, cobalt-60 lingers. It ends when even its centre has fallen under {@link #END} RAD/h.
 *
 * @param center   where it lies (the burst, the reactor's core)
 * @param radius   how far it reaches, blocks
 * @param peak     the dose rate at its centre when it began, RAD/h
 * @param halfLife ticks for it to halve
 * @param start    the game time it began
 */
public record RadiationSource(Vec3 center, float radius, float peak, int halfLife, long start) {
    /** Under this at its centre (RAD/h) a source is gone. */
    public static final float END = 0.5f;
    /** A source counts as cleared (the "lasts" of the stats) once its centre is under this (RAD/h). */
    public static final float SAFE = 10f;
    /** The dose rate (RAD/h) a source has to have at its centre for its fallout overhead to be drawn at full strength. */
    public static final float FULL = 30_000f;

    public static final Codec<RadiationSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            Vec3.CODEC.fieldOf("center").forGetter(RadiationSource::center),
            Codec.FLOAT.fieldOf("radius").forGetter(RadiationSource::radius),
            Codec.FLOAT.fieldOf("peak").forGetter(RadiationSource::peak),
            Codec.INT.fieldOf("half_life").forGetter(RadiationSource::halfLife),
            Codec.LONG.fieldOf("start").forGetter(RadiationSource::start)
    ).apply(i, RadiationSource::new));

    public static final StreamCodec<ByteBuf, RadiationSource> STREAM_CODEC = StreamCodec.of((buf, s) -> {
        buf.writeDouble(s.center.x);
        buf.writeDouble(s.center.y);
        buf.writeDouble(s.center.z);
        buf.writeFloat(s.radius);
        buf.writeFloat(s.peak);
        ByteBufCodecs.VAR_INT.encode(buf, s.halfLife);
        buf.writeLong(s.start);
    }, buf -> new RadiationSource(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(),
            ByteBufCodecs.VAR_INT.decode(buf), buf.readLong()));

    /**
     * A source whose centre falls under {@link #SAFE} RAD/h {@code lasts} ticks after it begins (the half-life that
     * gives), the server's decay multiplier shortening it.
     */
    public static RadiationSource lasting(Vec3 center, float radius, float peak, int lasts, long start, double decayMultiplier) {
        double halvings = Math.log(Math.max(2 * SAFE, peak) / SAFE) / Math.log(2);
        int halfLife = (int) Math.max(1, Math.round(lasts / halvings / Math.max(0.01, decayMultiplier)));
        return new RadiationSource(center, radius, peak, halfLife, start);
    }

    /** Its centre's dose rate at game time {@code now}, RAD/h. */
    public float peakAt(long now) {
        double age = Math.max(0, now - this.start);
        return (float) (this.peak * Math.pow(0.5, age / this.halfLife));
    }

    /** The dose rate {@code distance} blocks from its centre at game time {@code now}, RAD/h (0 at and beyond its edge). */
    public float rateAt(long now, double distance) {
        if (distance >= this.radius) {
            return 0;
        }
        double f = 1 - distance / this.radius;
        return (float) (peakAt(now) * f * f);
    }

    public boolean ended(long now) {
        return peakAt(now) < END;
    }

    /** How strong it looks at game time {@code now}, 0 to 1: what the fallout over it is drawn from. */
    public float strength(long now) {
        return Math.min(1, peakAt(now) / FULL);
    }

    /** How strong it looks {@code distance} from its centre (0 to 1): the green cast of the sky, the fog and the clouds. */
    public float levelAt(long now, double distance) {
        return Math.min(1, rateAt(now, distance) / FULL);
    }
}
