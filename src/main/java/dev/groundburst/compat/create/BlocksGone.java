package dev.groundburst.compat.create;

import java.util.List;

import dev.groundburst.GroundBurst;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Blocks a blast broke out of a Create contraption ({@link ContraptionHulls}): the contraption {@code entity} and their
 * places in it. Clients forget them too, and Create draws it and works out its collisions without them.
 */
public record BlocksGone(int entity, List<BlockPos> local) implements CustomPacketPayload {
    public static final Type<BlocksGone> TYPE = new Type<>(GroundBurst.id("contraption_blocks_gone"));
    public static final StreamCodec<ByteBuf, BlocksGone> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BlocksGone::entity, BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), BlocksGone::local, BlocksGone::new);

    @Override
    public Type<BlocksGone> type() {
        return TYPE;
    }
}
