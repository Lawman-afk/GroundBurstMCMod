package dev.groundburst.blast;

import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * What clients are told about an annihilation (DESIGN.md §16.3): where its sphere is, how big it grows and how fast,
 * and when it has erased all it reaches (the sphere then fades, showing the void). The client draws it in
 * {@code AnnihilationFx}, which sets the hooks.
 */
public final class AnnihilationPayloads {
    public static Consumer<Start> startHook = p -> {};
    public static Consumer<End> endHook = p -> {};

    private AnnihilationPayloads() {}

    /**
     * Annihilation {@code id} began {@code age} ticks ago at {@code center}: its sphere grows to {@code radius} over
     * {@code expand} ticks (a player arriving later is sent its age, so the sphere they see is the size it is now).
     */
    public record Start(int id, Vec3 center, float radius, int expand, int age) implements CustomPacketPayload {
        public static final Type<Start> TYPE = GBNetwork.type("annihilation_start");
        public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.id);
            buf.writeDouble(p.center.x);
            buf.writeDouble(p.center.y);
            buf.writeDouble(p.center.z);
            buf.writeFloat(p.radius);
            buf.writeVarInt(p.expand);
            buf.writeVarInt(p.age);
        }, buf -> new Start(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readVarInt(),
                buf.readVarInt()));

        @Override
        public Type<Start> type() {
            return TYPE;
        }
    }

    /** Annihilation {@code id} has erased everything it reaches. */
    public record End(int id) implements CustomPacketPayload {
        public static final Type<End> TYPE = GBNetwork.type("annihilation_end");
        public static final StreamCodec<RegistryFriendlyByteBuf, End> CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.id),
                buf -> new End(buf.readVarInt()));

        @Override
        public Type<End> type() {
            return TYPE;
        }
    }
}
