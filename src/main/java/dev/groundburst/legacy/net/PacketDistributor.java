package dev.groundburst.legacy.net;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/** NeoForge's payload sending, over the mod's Forge channel ({@link Network}). */
public final class PacketDistributor {
    private PacketDistributor() {}

    private static net.minecraftforge.network.simple.SimpleChannel channel() {
        return Network.channel();
    }

    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... more) {
        channel().sendToServer(new Network.Wrapped(payload));
        for (CustomPacketPayload p : more) {
            channel().sendToServer(new Network.Wrapped(p));
        }
    }

    private static void send(net.minecraftforge.network.PacketDistributor.PacketTarget target, CustomPacketPayload payload,
            CustomPacketPayload[] more) {
        channel().send(target, new Network.Wrapped(payload));
        for (CustomPacketPayload p : more) {
            channel().send(target, new Network.Wrapped(p));
        }
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... more) {
        send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), payload, more);
    }

    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload, CustomPacketPayload... more) {
        send(net.minecraftforge.network.PacketDistributor.DIMENSION.with(level::dimension), payload, more);
    }

    public static void sendToPlayersNear(ServerLevel level, @Nullable ServerPlayer excluded, double x, double y, double z, double radius,
            CustomPacketPayload payload, CustomPacketPayload... more) {
        send(net.minecraftforge.network.PacketDistributor.NEAR.with(() -> new net.minecraftforge.network.PacketDistributor.TargetPoint(
                excluded, x, y, z, radius * radius, level.dimension())), payload, more);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload, CustomPacketPayload... more) {
        send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), payload, more);
    }

    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload, CustomPacketPayload... more) {
        send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> entity), payload, more);
    }

    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload, CustomPacketPayload... more) {
        send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), payload, more);
    }

    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload, CustomPacketPayload... more) {
        send(net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(chunk.x, chunk.z)), payload, more);
    }
}
