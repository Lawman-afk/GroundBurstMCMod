package dev.groundburst.compat.create;

import dev.groundburst.GroundBurst;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import dev.groundburst.legacy.net.ByteBufCodecs;
import dev.groundburst.legacy.net.StreamCodec;
import dev.groundburst.legacy.net.CustomPacketPayload;

/**
 * A carried launcher's looks for clients (its update tag): the contraption {@code entity}, the block's place in it
 * ({@code local}). Sent when its looks change while a Create contraption carries it.
 */
public record CarriedSync(int entity, BlockPos local, CompoundTag tag) implements CustomPacketPayload {
    public static final Type<CarriedSync> TYPE = new Type<>(GroundBurst.id("carried_sync"));
    public static final StreamCodec<ByteBuf, CarriedSync> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CarriedSync::entity, dev.groundburst.legacy.net.Codecs.BLOCK_POS, CarriedSync::local, ByteBufCodecs.COMPOUND_TAG, CarriedSync::tag,
            CarriedSync::new);

    @Override
    public Type<CarriedSync> type() {
        return TYPE;
    }
}
