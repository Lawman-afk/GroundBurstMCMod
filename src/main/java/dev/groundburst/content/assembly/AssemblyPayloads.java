package dev.groundburst.content.assembly;

import dev.groundburst.network.GBNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import dev.groundburst.legacy.net.ByteBufCodecs;
import dev.groundburst.legacy.net.StreamCodec;
import dev.groundburst.legacy.net.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import dev.groundburst.legacy.net.IPayloadContext;

/** Server-bound packets from the Assembly Bench screen. */
public final class AssemblyPayloads {
    private static final double REACH = 8;

    private AssemblyPayloads() {}

    /** The setting for the fuze in the bench's fuze slot (its seconds, range, frequency or burst height). */
    public record FuzeSetting(BlockPos pos, int value) implements CustomPacketPayload {
        public static final Type<FuzeSetting> TYPE = GBNetwork.type("assembly_fuze_setting");
        public static final StreamCodec<FriendlyByteBuf, FuzeSetting> CODEC = StreamCodec.composite(
                dev.groundburst.legacy.net.Codecs.BLOCK_POS, FuzeSetting::pos, ByteBufCodecs.VAR_INT, FuzeSetting::value, FuzeSetting::new);

        @Override
        public Type<FuzeSetting> type() {
            return TYPE;
        }

        public static void handle(FuzeSetting p, IPayloadContext context) {
            if (((Object) context.player()) instanceof ServerPlayer player && dev.groundburst.content.machine.Carried.near(player, p.pos(), REACH)
                    && ((Object) dev.groundburst.content.machine.Carried.blockEntity(player.level(), p.pos(), AssemblyBenchBlockEntity.class)) instanceof AssemblyBenchBlockEntity bench) {
                bench.setFuzeValue(p.value());
            }
        }
    }
}
