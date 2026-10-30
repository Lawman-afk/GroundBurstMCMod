package dev.groundburst.blast;

import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * What clients are told about a storm (DESIGN.md §16.14): where it rages and how long, its every strike (where it came
 * down, the creatures its chain jumped through, a seed for the bolt's shape) and its end. Drawn on the client by
 * {@code StormFx}, which sets the hooks.
 */
public final class StormPayloads {
    public static Consumer<Start> startHook = p -> {};
    public static Consumer<Strike> strikeHook = p -> {};
    public static Consumer<End> endHook = p -> {};

    private StormPayloads() {}

    /** Storm {@code id} gathered {@code age} ticks ago over {@code center}: {@code radius} round, {@code life} ticks, its clouds at {@code cloudY}. */
    public record Start(int id, Vec3 center, float radius, int life, int age, double cloudY) implements CustomPacketPayload {
        public static final Type<Start> TYPE = GBNetwork.type("storm_start");
        public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeDouble(p.center.x);
            buf.writeDouble(p.center.y);
            buf.writeDouble(p.center.z);
            buf.writeFloat(p.radius);
            buf.writeVarInt(p.life);
            buf.writeVarInt(p.age);
            buf.writeDouble(p.cloudY);
        }, buf -> new Start(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readVarInt(),
                buf.readVarInt(), buf.readDouble()));

        @Override
        public Type<Start> type() {
            return TYPE;
        }
    }

    /** Storm {@code id} struck at {@code foot}; its chain jumped through {@code chain} (x, y, z each, from the first). */
    public record Strike(int id, Vec3 foot, double[] chain, int seed) implements CustomPacketPayload {
        public static final Type<Strike> TYPE = GBNetwork.type("storm_strike");
        public static final StreamCodec<RegistryFriendlyByteBuf, Strike> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeDouble(p.foot.x);
            buf.writeDouble(p.foot.y);
            buf.writeDouble(p.foot.z);
            buf.writeVarInt(p.chain.length);
            for (double d : p.chain) {
                buf.writeFloat((float) d);
            }
            buf.writeInt(p.seed);
        }, buf -> {
            int id = buf.readVarInt();
            Vec3 foot = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            int n = buf.readVarInt();
            double[] chain = new double[n];
            for (int i = 0; i < n; i++) {
                chain[i] = buf.readFloat();
            }
            return new Strike(id, foot, chain, buf.readInt());
        });

        @Override
        public Type<Strike> type() {
            return TYPE;
        }
    }

    /** Storm {@code id} has died away. */
    public record End(int id) implements CustomPacketPayload {
        public static final Type<End> TYPE = GBNetwork.type("storm_end");
        public static final StreamCodec<RegistryFriendlyByteBuf, End> CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.id),
                buf -> new End(buf.readVarInt()));

        @Override
        public Type<End> type() {
            return TYPE;
        }
    }
}
