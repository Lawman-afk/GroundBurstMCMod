package dev.groundzero.blast;

import dev.groundzero.network.GZNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * What clients are told about blasts (DESIGN.md §12): the server owns every radius, clients only draw and play what
 * these numbers say. A start packet when a blast goes off (or when a player arrives while one runs), the front's
 * radius every 4 ticks, and an end packet.
 */
public final class BlastPayloads {
    private BlastPayloads() {}

    /**
     * A blast went off.
     *
     * @param id         short id of this blast for the session
     * @param soundSet   which sound set it is heard as (small, medium, large, nuke)
     * @param visual     the visual preset (slice 5c)
     * @param center     the burst point
     * @param crater     crater radius
     * @param reach      the outer radius of its damage zone (sets how far its sound carries)
     * @param outer      the radius the front runs to
     * @param frontSpeed blocks a tick
     * @param seed       shared with the server's ray field, so client visuals can match the crater's lobes
     * @param startTick  game time it went off
     */
    public record Start(int id, String soundSet, String visual, Vec3 center, float crater, float reach, float outer, float frontSpeed,
            long seed, long startTick) implements CustomPacketPayload {
        public static final Type<Start> TYPE = GZNetwork.type("blast_start");
        public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeUtf(p.soundSet);
            buf.writeUtf(p.visual);
            buf.writeDouble(p.center.x);
            buf.writeDouble(p.center.y);
            buf.writeDouble(p.center.z);
            buf.writeFloat(p.crater);
            buf.writeFloat(p.reach);
            buf.writeFloat(p.outer);
            buf.writeFloat(p.frontSpeed);
            buf.writeLong(p.seed);
            buf.writeLong(p.startTick);
        }, buf -> new Start(buf.readVarInt(), buf.readUtf(), buf.readUtf(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readLong(), buf.readLong()));

        @Override
        public Type<Start> type() {
            return TYPE;
        }
    }

    /** The front of blast {@code id} has reached {@code radius}. */
    public record Front(int id, float radius) implements CustomPacketPayload {
        public static final Type<Front> TYPE = GZNetwork.type("blast_front");
        public static final StreamCodec<RegistryFriendlyByteBuf, Front> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeFloat(p.radius);
        }, buf -> new Front(buf.readVarInt(), buf.readFloat()));

        @Override
        public Type<Front> type() {
            return TYPE;
        }
    }

    /** Blast {@code id} has finished. */
    public record End(int id) implements CustomPacketPayload {
        public static final Type<End> TYPE = GZNetwork.type("blast_end");
        public static final StreamCodec<RegistryFriendlyByteBuf, End> CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.id),
                buf -> new End(buf.readVarInt()));

        @Override
        public Type<End> type() {
            return TYPE;
        }
    }

    static Start start(BlastJob job) {
        BlastProfile p = job.profile;
        return new Start(job.netId, p.soundSet(), p.visual().orElse(p.soundSet()), job.center, p.crater(),
                Math.max(p.damage(), Math.max(p.lethal(), p.crater())), p.outerRadius(), p.isInstant() ? 0 : p.frontSpeed(), job.seed, job.startTick);
    }
}
