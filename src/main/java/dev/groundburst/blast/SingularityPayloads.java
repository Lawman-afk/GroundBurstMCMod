package dev.groundburst.blast;

import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * What clients are told about a singularity (DESIGN.md §16.2): where it is and how it grows, the blocks it tears loose
 * (their places and states, for the debris that spirals in), and its collapse. Drawn on the client by
 * {@code SingularityFx}, which sets the hooks.
 */
public final class SingularityPayloads {
    public static Consumer<Start> startHook = p -> {};
    public static Consumer<Torn> tornHook = p -> {};
    public static Consumer<Collapse> collapseHook = p -> {};

    private SingularityPayloads() {}

    /**
     * Singularity {@code id} opened {@code age} ticks ago at {@code center}: its event horizon grows to {@code horizon}
     * over {@code grow} ticks, it pulls things in from {@code pull} and tears blocks loose out to {@code rip}.
     */
    public record Start(int id, Vec3 center, float horizon, float pull, float rip, int grow, int age) implements CustomPacketPayload {
        public static final Type<Start> TYPE = GBNetwork.type("singularity_start");
        public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeDouble(p.center.x);
            buf.writeDouble(p.center.y);
            buf.writeDouble(p.center.z);
            buf.writeFloat(p.horizon);
            buf.writeFloat(p.pull);
            buf.writeFloat(p.rip);
            buf.writeVarInt(p.grow);
            buf.writeVarInt(p.age);
        }, buf -> new Start(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<Start> type() {
            return TYPE;
        }
    }

    /** Blocks singularity {@code id} tore loose this tick: their places (as longs) and block states (by id). */
    public record Torn(int id, long[] positions, int[] states) implements CustomPacketPayload {
        public static final Type<Torn> TYPE = GBNetwork.type("singularity_torn");
        public static final StreamCodec<RegistryFriendlyByteBuf, Torn> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeVarInt(p.positions.length);
            for (int i = 0; i < p.positions.length; i++) {
                buf.writeLong(p.positions[i]);
                buf.writeVarInt(p.states[i]);
            }
        }, buf -> {
            int id = buf.readVarInt();
            int n = buf.readVarInt();
            long[] positions = new long[n];
            int[] states = new int[n];
            for (int i = 0; i < n; i++) {
                positions[i] = buf.readLong();
                states[i] = buf.readVarInt();
            }
            return new Torn(id, positions, states);
        });

        @Override
        public Type<Torn> type() {
            return TYPE;
        }
    }

    /** Singularity {@code id} has collapsed (its blast is a blast of its own). */
    public record Collapse(int id) implements CustomPacketPayload {
        public static final Type<Collapse> TYPE = GBNetwork.type("singularity_collapse");
        public static final StreamCodec<RegistryFriendlyByteBuf, Collapse> CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.id),
                buf -> new Collapse(buf.readVarInt()));

        @Override
        public Type<Collapse> type() {
            return TYPE;
        }
    }
}
