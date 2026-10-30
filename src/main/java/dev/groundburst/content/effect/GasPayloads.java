package dev.groundburst.content.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** What clients are told of the gas grid and of EMP fields, to draw them. */
public final class GasPayloads {
    /** Client hooks (set by the client). */
    public static Consumer<Cells> cellsHook = c -> {};
    public static Consumer<Emp> empHook = e -> {};

    private GasPayloads() {}

    /** A block of gas: where (BlockPos packed), which agent (ordinal), how thick (128 = full strength). */
    public record Cell(long pos, byte agent, byte strength) {}

    /** The gas near a player (all of it they should draw; empty once it has gone). */
    public record Cells(List<Cell> cells) implements CustomPacketPayload {
        public static final Type<Cells> TYPE = GBNetwork.type("gas_cells");
        public static final StreamCodec<RegistryFriendlyByteBuf, Cells> CODEC = StreamCodec.of((buf, c) -> {
            buf.writeVarInt(c.cells().size());
            for (Cell cell : c.cells()) {
                buf.writeLong(cell.pos());
                buf.writeByte(cell.agent());
                buf.writeByte(cell.strength());
            }
        }, buf -> {
            int n = buf.readVarInt();
            List<Cell> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(new Cell(buf.readLong(), buf.readByte(), buf.readByte()));
            }
            return new Cells(out);
        });

        @Override
        public Type<Cells> type() {
            return TYPE;
        }
    }

    /**
     * An EMP field went up: where, how big, for how long (ticks), and whether it is a storm strike's pulse (a ring of
     * sparks running out along the ground) rather than a full EMP (a dome of light).
     */
    public record Emp(Vec3 centre, float radius, int ticks, boolean pulse) implements CustomPacketPayload {
        public static final Type<Emp> TYPE = GBNetwork.type("emp_field");
        public static final StreamCodec<RegistryFriendlyByteBuf, Emp> CODEC = StreamCodec.of((buf, e) -> {
            buf.writeDouble(e.centre().x);
            buf.writeDouble(e.centre().y);
            buf.writeDouble(e.centre().z);
            buf.writeFloat(e.radius());
            buf.writeVarInt(e.ticks());
            buf.writeBoolean(e.pulse());
        }, buf -> new Emp(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<Emp> type() {
            return TYPE;
        }
    }
}
