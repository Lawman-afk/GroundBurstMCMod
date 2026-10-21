package dev.groundzero.content.nuclear;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.groundzero.network.GZNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What clients are told of the contaminated zones in their level, to draw them (until Phase 12's fallout plumes: a
 * layer of fallout cloud over each zone, and a green cast to the sky inside one): every zone, sent whole whenever one
 * begins or ends, and to a player as they arrive in the level.
 */
public final class ContaminationPayloads {
    /** Client hook (set by the client). */
    public static Consumer<Zones> zonesHook = z -> {};

    private ContaminationPayloads() {}

    /** Every contaminated zone in the receiving player's level (empty: none). */
    public record Zones(List<Contamination.Zone> zones) implements CustomPacketPayload {
        public static final Type<Zones> TYPE = GZNetwork.type("contamination_zones");
        public static final StreamCodec<RegistryFriendlyByteBuf, Zones> CODEC = StreamCodec.of((buf, z) -> {
            buf.writeVarInt(z.zones().size());
            for (Contamination.Zone zone : z.zones()) {
                buf.writeDouble(zone.center().x);
                buf.writeDouble(zone.center().y);
                buf.writeDouble(zone.center().z);
                buf.writeFloat(zone.radius());
                buf.writeFloat(zone.strength());
                buf.writeLong(zone.start());
                buf.writeVarInt(zone.duration());
            }
        }, buf -> {
            int n = buf.readVarInt();
            List<Contamination.Zone> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                float radius = buf.readFloat(), strength = buf.readFloat();
                long start = buf.readLong();
                out.add(new Contamination.Zone(center, radius, strength, start, buf.readVarInt()));
            }
            return new Zones(out);
        });

        @Override
        public Type<Zones> type() {
            return TYPE;
        }
    }

    static void sendAll(ServerLevel level, List<Contamination.Zone> zones) {
        PacketDistributor.sendToPlayersInDimension(level, new Zones(zones));
    }

    static void sendTo(ServerPlayer player, List<Contamination.Zone> zones) {
        PacketDistributor.sendToPlayer(player, new Zones(zones));
    }
}
