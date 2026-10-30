package dev.groundburst.content.alarm;

import java.util.List;

import dev.groundburst.network.GBNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-bound packets from an alarm device's screen (DESIGN.md §18.6): its settings, and an Alarm Panel's buttons. */
public final class AlarmPayloads {
    /** How close a player has to be to a device to change it. */
    private static final double REACH = 8;

    private AlarmPayloads() {}

    /** A device's channel and choices. */
    public record Settings(BlockPos pos, int channel, List<Integer> options) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = GBNetwork.type("alarm_settings");
        public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Settings::pos, ByteBufCodecs.VAR_INT, Settings::channel,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), Settings::options, Settings::new);

        @Override
        public Type<Settings> type() {
            return TYPE;
        }

        public static void handle(Settings p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && near(player, p.pos())
                    && player.level().getBlockEntity(p.pos()) instanceof AlarmBlockEntity be) {
                be.applySettings(p.channel(), p.options().stream().mapToInt(Integer::intValue).toArray());
            }
        }
    }

    /** An Alarm Panel's button: sound, stop, silence or test (AlarmBlockEntity's actions). */
    public record Panel(BlockPos pos, int action) implements CustomPacketPayload {
        public static final Type<Panel> TYPE = GBNetwork.type("alarm_panel");
        public static final StreamCodec<RegistryFriendlyByteBuf, Panel> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Panel::pos, ByteBufCodecs.VAR_INT, Panel::action, Panel::new);

        @Override
        public Type<Panel> type() {
            return TYPE;
        }

        public static void handle(Panel p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && near(player, p.pos())
                    && player.level().getBlockEntity(p.pos()) instanceof AlarmBlockEntity be) {
                be.panel(p.action());
            }
        }
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return player.position().distanceTo(Vec3.atCenterOf(pos)) <= REACH;
    }
}
