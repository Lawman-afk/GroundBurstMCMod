package dev.groundburst.content.fallout;

import dev.groundburst.network.GBNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * A far wave on its way (DESIGN.md §21.10, {@link FarAftermath}): where the blast was, the radius it sets out from and the
 * one it dies at, and the game tick it sets out; it runs at the speed of sound. Sent to every player in the level when a
 * far aftermath begins, and to a player arriving while it runs; the client draws its wall of dust ({@code FarWaveFx}).
 */
public record FarWave(int id, Vec3 centre, float from, float to, long startTick) implements CustomPacketPayload {
    public static final Type<FarWave> TYPE = GBNetwork.type("far_wave");
    public static final StreamCodec<RegistryFriendlyByteBuf, FarWave> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FarWave::id, Vec3.STREAM_CODEC, FarWave::centre, ByteBufCodecs.FLOAT, FarWave::from,
            ByteBufCodecs.FLOAT, FarWave::to, ByteBufCodecs.VAR_LONG, FarWave::startTick, FarWave::new);
    /** Client hook (set by the client). */
    public static Consumer<FarWave> hook = w -> {};

    static FarWave of(FarAftermath.Far f) {
        return new FarWave(f.id(), f.centre(), f.reach(), f.withered(), f.waveStart());
    }

    @Override
    public Type<FarWave> type() {
        return TYPE;
    }
}
