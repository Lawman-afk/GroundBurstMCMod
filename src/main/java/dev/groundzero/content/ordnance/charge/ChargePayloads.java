package dev.groundzero.content.ordnance.charge;

import dev.groundzero.network.GZNetwork;
import dev.groundzero.registry.GZDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-bound packets from the charge screens: fuze settings, cutting a wire, a detonator's frequency. */
public final class ChargePayloads {
    /** How close a player has to be to a charge to change it. */
    private static final double REACH = 8;

    private ChargePayloads() {}

    public record Settings(BlockPos pos, boolean timer, int frequency, int seconds) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = GZNetwork.type("charge_settings");
        public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Settings::pos, ByteBufCodecs.BOOL, Settings::timer, ByteBufCodecs.VAR_INT, Settings::frequency,
                ByteBufCodecs.VAR_INT, Settings::seconds, Settings::new);

        @Override
        public Type<Settings> type() {
            return TYPE;
        }

        public static void handle(Settings p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && near(player, p.pos())) {
                ChargeBlockEntity be = ChargeBlock.at(player.level(), p.pos());
                if (be != null) {
                    be.configure(p.timer() ? ChargeBlockEntity.Mode.TIMER : ChargeBlockEntity.Mode.REMOTE, p.frequency(), p.seconds());
                }
            }
        }
    }

    public record CutWire(BlockPos pos, int wire) implements CustomPacketPayload {
        public static final Type<CutWire> TYPE = GZNetwork.type("charge_cut_wire");
        public static final StreamCodec<RegistryFriendlyByteBuf, CutWire> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, CutWire::pos, ByteBufCodecs.VAR_INT, CutWire::wire, CutWire::new);

        @Override
        public Type<CutWire> type() {
            return TYPE;
        }

        public static void handle(CutWire p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && near(player, p.pos())) {
                ChargeBlockEntity be = ChargeBlock.at(player.level(), p.pos());
                if (be != null) {
                    be.cut((ServerLevel) player.level(), p.wire());
                }
            }
        }
    }

    public record DetonatorFrequency(boolean mainHand, int frequency) implements CustomPacketPayload {
        public static final Type<DetonatorFrequency> TYPE = GZNetwork.type("detonator_frequency");
        public static final StreamCodec<RegistryFriendlyByteBuf, DetonatorFrequency> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, DetonatorFrequency::mainHand, ByteBufCodecs.VAR_INT, DetonatorFrequency::frequency, DetonatorFrequency::new);

        @Override
        public Type<DetonatorFrequency> type() {
            return TYPE;
        }

        public static void handle(DetonatorFrequency p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) {
                ItemStack stack = player.getItemInHand(p.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
                if (stack.getItem() instanceof RemoteDetonatorItem) {
                    stack.set(GZDataComponents.FREQUENCY.get(), Math.clamp(p.frequency(), 0, ChargeKind.MAX_FREQUENCY));
                }
            }
        }
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return player.position().distanceTo(Vec3.atCenterOf(pos)) <= REACH;
    }
}
