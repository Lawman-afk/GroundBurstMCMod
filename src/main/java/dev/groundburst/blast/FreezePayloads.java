package dev.groundburst.blast;

import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * What clients are told about a deep freeze (DESIGN.md §16.16): where it struck, how far its front runs and how fast,
 * its heart, how long its cold snap holds and how high its cold climbs; how far its front has really run (it waits for
 * the world to load); every spire of ice it raises; and its end. Drawn on the client by {@code FreezeFx}, which sets the
 * hooks.
 */
public final class FreezePayloads {
    public static Consumer<Start> startHook = p -> {};
    public static Consumer<Front> frontHook = p -> {};
    public static Consumer<Spire> spireHook = p -> {};
    public static Consumer<End> endHook = p -> {};

    private FreezePayloads() {}

    /**
     * Freeze {@code id} struck {@code age} ticks ago at {@code center}: its front {@code radius} round in {@code frontTicks},
     * its heart {@code core}, its cold snap {@code life} ticks, its cold climbing {@code climb} over where it struck.
     */
    public record Start(int id, Vec3 center, float radius, float core, int frontTicks, int life, int age, float climb)
            implements CustomPacketPayload {
        public static final Type<Start> TYPE = GBNetwork.type("freeze_start");
        public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeDouble(p.center.x);
            buf.writeDouble(p.center.y);
            buf.writeDouble(p.center.z);
            buf.writeFloat(p.radius);
            buf.writeFloat(p.core);
            buf.writeVarInt(p.frontTicks);
            buf.writeVarInt(p.life);
            buf.writeVarInt(p.age);
            buf.writeFloat(p.climb);
        }, buf -> new Start(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat()));

        @Override
        public Type<Start> type() {
            return TYPE;
        }
    }

    /** Freeze {@code id}'s front has run {@code reach} blocks out (sent every few ticks while it runs). */
    public record Front(int id, float reach) implements CustomPacketPayload {
        public static final Type<Front> TYPE = GBNetwork.type("freeze_front");
        public static final StreamCodec<RegistryFriendlyByteBuf, Front> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeFloat(p.reach);
        }, buf -> new Front(buf.readVarInt(), buf.readFloat()));

        @Override
        public Type<Front> type() {
            return TYPE;
        }
    }

    /** Freeze {@code id} raises a spire of ice {@code height} blocks tall from {@code foot}, over {@code ticks}. */
    public record Spire(int id, Vec3 foot, float height, int ticks) implements CustomPacketPayload {
        public static final Type<Spire> TYPE = GBNetwork.type("freeze_spire");
        public static final StreamCodec<RegistryFriendlyByteBuf, Spire> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeDouble(p.foot.x);
            buf.writeDouble(p.foot.y);
            buf.writeDouble(p.foot.z);
            buf.writeFloat(p.height);
            buf.writeVarInt(p.ticks);
        }, buf -> new Spire(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readVarInt()));

        @Override
        public Type<Spire> type() {
            return TYPE;
        }
    }

    /** Freeze {@code id}'s cold snap is over. */
    public record End(int id) implements CustomPacketPayload {
        public static final Type<End> TYPE = GBNetwork.type("freeze_end");
        public static final StreamCodec<RegistryFriendlyByteBuf, End> CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.id),
                buf -> new End(buf.readVarInt()));

        @Override
        public Type<End> type() {
            return TYPE;
        }
    }
}
