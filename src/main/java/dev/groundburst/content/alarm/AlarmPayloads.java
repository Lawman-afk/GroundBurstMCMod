package dev.groundburst.content.alarm;

import java.util.List;

import dev.groundburst.network.GBNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import dev.groundburst.legacy.net.ByteBufCodecs;
import dev.groundburst.legacy.net.StreamCodec;
import dev.groundburst.legacy.net.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import dev.groundburst.legacy.net.IPayloadContext;

/** Server-bound packets from an alarm device's screen (DESIGN.md §18.6): its settings, and an Alarm Panel's buttons. */
public final class AlarmPayloads {
    /** How close a player has to be to a device to change it. */
    private static final double REACH = 8;

    private AlarmPayloads() {}

    /** A device's channel and choices. */
    public record Settings(BlockPos pos, int channel, List<Integer> options) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = GBNetwork.type("alarm_settings");
        public static final StreamCodec<FriendlyByteBuf, Settings> CODEC = StreamCodec.composite(
                dev.groundburst.legacy.net.Codecs.BLOCK_POS, Settings::pos, ByteBufCodecs.VAR_INT, Settings::channel,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), Settings::options, Settings::new);

        @Override
        public Type<Settings> type() {
            return TYPE;
        }

        public static void handle(Settings p, IPayloadContext context) {
            if (((Object) context.player()) instanceof ServerPlayer player && near(player, p.pos())
                    && ((Object) dev.groundburst.content.machine.Carried.blockEntity(player.level(), p.pos(), AlarmBlockEntity.class)) instanceof AlarmBlockEntity be) {
                be.applySettings(p.channel(), p.options().stream().mapToInt(Integer::intValue).toArray());
            }
        }
    }

    /** An Alarm Panel's button: sound, stop, silence or test (AlarmBlockEntity's actions). */
    public record Panel(BlockPos pos, int action) implements CustomPacketPayload {
        public static final Type<Panel> TYPE = GBNetwork.type("alarm_panel");
        public static final StreamCodec<FriendlyByteBuf, Panel> CODEC = StreamCodec.composite(
                dev.groundburst.legacy.net.Codecs.BLOCK_POS, Panel::pos, ByteBufCodecs.VAR_INT, Panel::action, Panel::new);

        @Override
        public Type<Panel> type() {
            return TYPE;
        }

        public static void handle(Panel p, IPayloadContext context) {
            if (((Object) context.player()) instanceof ServerPlayer player && near(player, p.pos())
                    && ((Object) dev.groundburst.content.machine.Carried.blockEntity(player.level(), p.pos(), AlarmBlockEntity.class)) instanceof AlarmBlockEntity be) {
                be.panel(p.action());
            }
        }
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return dev.groundburst.content.machine.Carried.near(player, pos, REACH);
    }
}
