package dev.groundburst.content.radiation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What radiation has done to a living thing (DESIGN.md §21.2, §21.8), kept on it as a data attachment: the dose it has
 * taken (rad) and the dose rate it stood in when last measured (RAD/h). A player's is synced to them alone, for the
 * Geiger Counter and the Dosimeter Badge. Death clears it (it is not copied to the respawned player).
 */
public record RadiationState(float dose, float rate) {
    public static final RadiationState NONE = new RadiationState(0, 0);
    /** The dose (rad) each stage of sickness starts at: I, II, III, IV, and lethal. */
    public static final float[] STAGES = {100, 200, 400, 600, 1000};

    public static final MapCodec<RadiationState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("dose", 0f).forGetter(RadiationState::dose),
            Codec.FLOAT.optionalFieldOf("rate", 0f).forGetter(RadiationState::rate)
    ).apply(i, RadiationState::new));

    public static final StreamCodec<ByteBuf, RadiationState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RadiationState::dose, ByteBufCodecs.FLOAT, RadiationState::rate, RadiationState::new);

    /** The stage of sickness the dose has reached: 0 none, 1 to 4 for I to IV, 5 lethal. */
    public int stage() {
        return stage(this.dose);
    }

    public static int stage(float dose) {
        int s = 0;
        while (s < STAGES.length && dose >= STAGES[s]) {
            s++;
        }
        return s;
    }
}
