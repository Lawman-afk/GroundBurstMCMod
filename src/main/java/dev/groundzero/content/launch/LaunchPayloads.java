package dev.groundzero.content.launch;

import java.util.List;
import java.util.function.Consumer;

import dev.groundzero.network.GZNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The Launch Computer's screen: opening and closing it, setting the target, launching; and the scope it is sent. */
public final class LaunchPayloads {
    private static final double REACH = 8;

    /** Client hook: the scope arrives (set by the client). */
    public static Consumer<Scope> scopeHook = s -> {};

    private LaunchPayloads() {}

    private static LaunchComputerBlockEntity computer(IPayloadContext context, BlockPos pos) {
        if (context.player() instanceof ServerPlayer player && player.position().distanceTo(Vec3.atCenterOf(pos)) <= REACH
                && player.level().getBlockEntity(pos) instanceof LaunchComputerBlockEntity c) {
            return c;
        }
        return null;
    }

    /** The screen opened (true) or closed (false). */
    public record Watch(BlockPos pos, boolean open) implements CustomPacketPayload {
        public static final Type<Watch> TYPE = GZNetwork.type("launch_watch");
        public static final StreamCodec<RegistryFriendlyByteBuf, Watch> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Watch::pos, ByteBufCodecs.BOOL, Watch::open, Watch::new);

        @Override
        public Type<Watch> type() {
            return TYPE;
        }

        public static void handle(Watch p, IPayloadContext context) {
            LaunchComputerBlockEntity c = computer(context, p.pos());
            if (c != null && context.player() instanceof ServerPlayer player) {
                if (p.open()) {
                    c.addViewer(player);
                } else {
                    c.removeViewer(player);
                }
            }
        }
    }

    public record Target(BlockPos pos, int x, int z) implements CustomPacketPayload {
        public static final Type<Target> TYPE = GZNetwork.type("launch_target");
        public static final StreamCodec<RegistryFriendlyByteBuf, Target> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Target::pos, ByteBufCodecs.VAR_INT, Target::x, ByteBufCodecs.VAR_INT, Target::z, Target::new);

        @Override
        public Type<Target> type() {
            return TYPE;
        }

        public static void handle(Target p, IPayloadContext context) {
            LaunchComputerBlockEntity c = computer(context, p.pos());
            if (c != null) {
                c.setTarget(p.x(), p.z());
            }
        }
    }

    public record Launch(BlockPos pos, BlockPos silo) implements CustomPacketPayload {
        public static final Type<Launch> TYPE = GZNetwork.type("launch_fire");
        public static final StreamCodec<RegistryFriendlyByteBuf, Launch> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Launch::pos, BlockPos.STREAM_CODEC, Launch::silo, Launch::new);

        @Override
        public Type<Launch> type() {
            return TYPE;
        }

        public static void handle(Launch p, IPayloadContext context) {
            LaunchComputerBlockEntity c = computer(context, p.pos());
            if (c != null && context.player() instanceof ServerPlayer player) {
                c.launch((ServerLevel) player.level(), p.silo(), player);
            }
        }
    }

    /** A linked silo: where, its readiness ({@link LaunchSiloBlockEntity.Readiness} ordinal), its countdown. */
    public record Silo(BlockPos pos, int readiness, int countdown) {
        public static final StreamCodec<FriendlyByteBuf, Silo> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Silo::pos, ByteBufCodecs.VAR_INT, Silo::readiness, ByteBufCodecs.VAR_INT, Silo::countdown, Silo::new);
    }

    /** A missile on the radar: where, which way it is heading (radians, 0 = south), where it is going. */
    public record Contact(int x, int y, int z, float heading, int targetX, int targetZ) {
        public static final StreamCodec<FriendlyByteBuf, Contact> CODEC = StreamCodec.of((buf, c) -> {
            buf.writeVarInt(c.x());
            buf.writeVarInt(c.y());
            buf.writeVarInt(c.z());
            buf.writeFloat(c.heading());
            buf.writeVarInt(c.targetX());
            buf.writeVarInt(c.targetZ());
        }, buf -> new Contact(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt()));
    }

    /** The computer's state for its screen, sent while it is open. */
    public record Scope(BlockPos pos, boolean formed, boolean powered, int energy, boolean hasTarget, int targetX, int targetZ,
                        List<Silo> silos, List<Contact> contacts) implements CustomPacketPayload {
        public static final Type<Scope> TYPE = GZNetwork.type("launch_scope");
        public static final StreamCodec<RegistryFriendlyByteBuf, Scope> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBlockPos(s.pos());
            buf.writeBoolean(s.formed());
            buf.writeBoolean(s.powered());
            buf.writeVarInt(s.energy());
            buf.writeBoolean(s.hasTarget());
            buf.writeVarInt(s.targetX());
            buf.writeVarInt(s.targetZ());
            Silo.CODEC.apply(ByteBufCodecs.list()).encode(buf, s.silos());
            Contact.CODEC.apply(ByteBufCodecs.list()).encode(buf, s.contacts());
        }, buf -> new Scope(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                buf.readVarInt(), Silo.CODEC.apply(ByteBufCodecs.list()).decode(buf), Contact.CODEC.apply(ByteBufCodecs.list()).decode(buf)));

        @Override
        public Type<Scope> type() {
            return TYPE;
        }
    }
}
