package dev.groundburst.blast;

import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * What clients are told about a Particle Uplink Cannon's beam (DESIGN.md §19.6): where it left its cannon's pit and
 * when, how long it takes to come down and how long it burns; where it touches the ground as it moves; and its end.
 * Drawn on the client by {@code ParticleBeamFx}, which sets the hooks.
 */
public final class ParticleBeamPayloads {
    public static Consumer<Start> startHook = p -> {};
    public static Consumer<Move> moveHook = p -> {};
    public static Consumer<End> endHook = p -> {};

    private ParticleBeamPayloads() {}

    /**
     * Beam {@code id} left its cannon's pit at {@code from} {@code age} ticks ago (no cannon: {@code uplink} 0, and
     * {@code from} is where it comes down); it comes down on {@code at} {@code uplink} ticks after it left and burns
     * for {@code burn}.
     */
    public record Start(int id, Vec3 from, Vec3 at, int age, int uplink, int burn) implements CustomPacketPayload {
        public static final Type<Start> TYPE = GBNetwork.type("particle_beam_start");
        public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeDouble(p.from.x);
            buf.writeDouble(p.from.y);
            buf.writeDouble(p.from.z);
            buf.writeDouble(p.at.x);
            buf.writeDouble(p.at.y);
            buf.writeDouble(p.at.z);
            buf.writeVarInt(p.age);
            buf.writeVarInt(p.uplink);
            buf.writeVarInt(p.burn);
        }, buf -> new Start(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<Start> type() {
            return TYPE;
        }
    }

    /** Beam {@code id} touches the ground at {@code at} now (sent every other tick). */
    public record Move(int id, Vec3 at) implements CustomPacketPayload {
        public static final Type<Move> TYPE = GBNetwork.type("particle_beam_move");
        public static final StreamCodec<RegistryFriendlyByteBuf, Move> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeDouble(p.at.x);
            buf.writeFloat((float) p.at.y);
            buf.writeDouble(p.at.z);
        }, buf -> new Move(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readFloat(), buf.readDouble())));

        @Override
        public Type<Move> type() {
            return TYPE;
        }
    }

    /** Beam {@code id} is gone: it burned out, or its cannon stopped firing it (power cut, knocked out, broken). */
    public record End(int id) implements CustomPacketPayload {
        public static final Type<End> TYPE = GBNetwork.type("particle_beam_end");
        public static final StreamCodec<RegistryFriendlyByteBuf, End> CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.id),
                buf -> new End(buf.readVarInt()));

        @Override
        public Type<End> type() {
            return TYPE;
        }
    }
}
