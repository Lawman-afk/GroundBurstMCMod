package dev.groundburst.compat.create;

import java.util.List;

import dev.groundburst.GroundBurst;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks a blast or a freeze changed in a Create contraption ({@link ContraptionHulls}): the contraption {@code entity},
 * their places in it and what each is now (air: broken out of it). Clients change them too, and Create draws it and works
 * out its collisions as it is now.
 */
public record BlocksChanged(int entity, List<BlockPos> local, List<BlockState> states) implements CustomPacketPayload {
    public static final Type<BlocksChanged> TYPE = new Type<>(GroundBurst.id("contraption_blocks_changed"));
    public static final StreamCodec<ByteBuf, BlocksChanged> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BlocksChanged::entity, BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), BlocksChanged::local,
            ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY).apply(ByteBufCodecs.list()), BlocksChanged::states, BlocksChanged::new);

    @Override
    public Type<BlocksChanged> type() {
        return TYPE;
    }
}
