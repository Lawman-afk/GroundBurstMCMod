package dev.groundburst.content.artillery;

import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A gun's crew and the gun (DESIGN.md §20.1): the crewman's aim, shots and shell picks to the server; the gun as it is
 * laid to everyone near it (its model, and its crew's reticle and readout).
 */
public final class ArtilleryPayloads {
    /** Client hook: a gun's state arrives (set by the client). */
    public static Consumer<State> stateHook = s -> {};

    private ArtilleryPayloads() {}

    private static MortarBlockEntity mortarAt(IPayloadContext context, BlockPos pos) {
        return context.player() instanceof ServerPlayer player && player.level().isLoaded(pos)
                && player.level().getBlockEntity(pos) instanceof MortarBlockEntity m ? m : null;
    }

    /** Where the crewman wants the gun laid: a point on the ground. */
    public record Aim(BlockPos gun, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Aim> TYPE = GBNetwork.type("artillery_aim");
        public static final StreamCodec<RegistryFriendlyByteBuf, Aim> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Aim::gun,
                ByteBufCodecs.DOUBLE, Aim::x, ByteBufCodecs.DOUBLE, Aim::y, ByteBufCodecs.DOUBLE, Aim::z, Aim::new);

        public Aim(BlockPos gun, Vec3 at) {
            this(gun, at.x, at.y, at.z);
        }

        @Override
        public Type<Aim> type() {
            return TYPE;
        }

        public static void handle(Aim aim, IPayloadContext context) {
            MortarBlockEntity m = mortarAt(context, aim.gun());
            if (m != null && Double.isFinite(aim.x()) && Double.isFinite(aim.y()) && Double.isFinite(aim.z())) {
                m.aim((ServerPlayer) context.player(), new Vec3(aim.x(), aim.y(), aim.z()));
            }
        }
    }

    /** The crewman fires. */
    public record Fire(BlockPos gun) implements CustomPacketPayload {
        public static final Type<Fire> TYPE = GBNetwork.type("artillery_fire");
        public static final StreamCodec<RegistryFriendlyByteBuf, Fire> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Fire::gun, Fire::new);

        @Override
        public Type<Fire> type() {
            return TYPE;
        }

        public static void handle(Fire fire, IPayloadContext context) {
            MortarBlockEntity m = mortarAt(context, fire.gun());
            if (m != null) {
                m.fire((ServerPlayer) context.player());
            }
        }
    }

    /** The crewman picks the shell to fire next ({@link ShellType}'s ordinal). */
    public record Select(BlockPos gun, int shell) implements CustomPacketPayload {
        public static final Type<Select> TYPE = GBNetwork.type("artillery_select");
        public static final StreamCodec<RegistryFriendlyByteBuf, Select> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Select::gun,
                ByteBufCodecs.VAR_INT, Select::shell, Select::new);

        @Override
        public Type<Select> type() {
            return TYPE;
        }

        public static void handle(Select select, IPayloadContext context) {
            MortarBlockEntity m = mortarAt(context, select.gun());
            if (m != null) {
                m.select((ServerPlayer) context.player(), select.shell());
            }
        }
    }

    /**
     * A gun as it is laid: its tube's yaw and elevation, its charge, its spread (times its least), ticks until it is
     * loaded, the shell picked, whether its aim is in reach, how many shots it has fired (the client kicks the tube
     * when this rises), its crewman (an entity id; -1: nobody) and the shells of each kind it can fire (its rack and
     * its crewman's inventory; -1: without end, a creative crewman's).
     */
    public record State(BlockPos gun, float yaw, float elevation, int charge, float dispersion, int reload, int selected, boolean reachable,
                        int shots, int crew, int[] counts) implements CustomPacketPayload {
        public static final Type<State> TYPE = GBNetwork.type("artillery_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((buf, s) -> s.write(buf), State::read);

        @Override
        public Type<State> type() {
            return TYPE;
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(this.gun);
            buf.writeFloat(this.yaw);
            buf.writeFloat(this.elevation);
            buf.writeVarInt(this.charge);
            buf.writeFloat(this.dispersion);
            buf.writeVarInt(this.reload);
            buf.writeVarInt(this.selected);
            buf.writeBoolean(this.reachable);
            buf.writeVarInt(this.shots);
            buf.writeVarInt(this.crew);
            buf.writeVarInt(this.counts.length);
            for (int c : this.counts) {
                buf.writeVarInt(c);
            }
        }

        private static State read(RegistryFriendlyByteBuf buf) {
            BlockPos gun = buf.readBlockPos();
            float yaw = buf.readFloat(), elevation = buf.readFloat();
            int charge = buf.readVarInt();
            float dispersion = buf.readFloat();
            int reload = buf.readVarInt(), selected = buf.readVarInt();
            boolean reachable = buf.readBoolean();
            int shots = buf.readVarInt(), crew = buf.readVarInt();
            int[] counts = new int[Math.min(16, buf.readVarInt())];
            for (int i = 0; i < counts.length; i++) {
                counts[i] = buf.readVarInt();
            }
            return new State(gun, yaw, elevation, charge, dispersion, reload, selected, reachable, shots, crew, counts);
        }
    }
}
