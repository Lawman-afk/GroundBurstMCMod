package dev.groundzero.content.assembly;

import dev.groundzero.network.GZNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-bound packets from the Assembly Bench screen. */
public final class AssemblyPayloads {
    private static final double REACH = 8;

    private AssemblyPayloads() {}

    /** The setting for the fuze in the bench's fuze slot (its seconds, range, frequency or burst height). */
    public record FuzeSetting(BlockPos pos, int value) implements CustomPacketPayload {
        public static final Type<FuzeSetting> TYPE = GZNetwork.type("assembly_fuze_setting");
        public static final StreamCodec<RegistryFriendlyByteBuf, FuzeSetting> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, FuzeSetting::pos, ByteBufCodecs.VAR_INT, FuzeSetting::value, FuzeSetting::new);

        @Override
        public Type<FuzeSetting> type() {
            return TYPE;
        }

        public static void handle(FuzeSetting p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && player.position().distanceTo(Vec3.atCenterOf(p.pos())) <= REACH
                    && player.level().getBlockEntity(p.pos()) instanceof AssemblyBenchBlockEntity bench) {
                bench.setFuzeValue(p.value());
            }
        }
    }
}
