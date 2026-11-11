package dev.groundburst.legacy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.legacy.net.CustomPacketPayload;
import dev.groundburst.legacy.net.Network;
import dev.groundburst.legacy.net.PacketDistributor;
import dev.groundburst.legacy.net.StreamCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * NeoForge's data attachments on Forge 1.20.1: every entity and chunk carries a store of them (a Forge capability, saved
 * with it), and the attachment calls on an entity or chunk ({@code body.getData(type)}, {@code chunk.setData(...)}) are
 * written by the port as calls here ({@code Attachments.get(body, type)}).
 */
public final class Attachments {
    private Attachments() {}

    /** NeoForge's registry of attachment types (none on 1.20.1: they are named as they are registered). */
    public static final ResourceKey<Registry<AttachmentType<?>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation("neoforge", "attachment_types"));

    private static final Map<ResourceLocation, AttachmentType<?>> TYPES = new HashMap<>();
    private static final Capability<Store> STORE = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation STORE_ID = new ResourceLocation(GroundBurst.MODID, "attachments");

    static void name(AttachmentType<?> type, ResourceLocation id) {
        type.name(id);
        TYPES.put(id, type);
    }

    static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, (AttachCapabilitiesEvent<Entity> e) -> attach(e, e.getObject()));
        MinecraftForge.EVENT_BUS.addGenericListener(LevelChunk.class, (AttachCapabilitiesEvent<LevelChunk> e) -> attach(e, e.getObject()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> syncAll(e.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> syncAll(e.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> syncAll(e.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            e.getOriginal().reviveCaps();
            Store from = store(e.getOriginal()), to = store(e.getEntity());
            if (from != null && to != null) {
                for (Map.Entry<AttachmentType<?>, Object> a : from.values.entrySet()) {
                    if (!e.isWasDeath() || a.getKey().copyOnDeath) {
                        to.values.put(a.getKey(), a.getValue());
                    }
                }
            }
            e.getOriginal().invalidateCaps();
        });
        Network.playToClient(Sync.TYPE, Sync.CODEC, (payload, context) -> payload.apply());
    }

    static void registerCapability(net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent event) {
        event.register(Store.class);
    }

    private static void attach(AttachCapabilitiesEvent<?> event, Object holder) {
        Store store = new Store(holder);
        event.addCapability(STORE_ID, store);
        event.addListener(store.optional::invalidate);
    }

    private static @Nullable Store store(Object holder) {
        if (holder instanceof Entity e) {
            return e.getCapability(STORE).resolve().orElse(null);
        }
        if (holder instanceof LevelChunk c) {
            return c.getCapability(STORE).resolve().orElse(null);
        }
        return null;
    }

    // --- the calls the port writes ----------------------------------------------------------------------------------------
    public static boolean has(Object holder, AttachmentType<?> type) {
        Store s = store(holder);
        return s != null && s.values.containsKey(type);
    }

    public static boolean has(Object holder, Supplier<? extends AttachmentType<?>> type) {
        return has(holder, type.get());
    }

    /** The value (made from the type's default if none was there yet). */
    @SuppressWarnings("unchecked")
    public static <T> T get(Object holder, AttachmentType<T> type) {
        Store s = store(holder);
        if (s == null) {
            return type.defaultValue.apply(holder);
        }
        Object v = s.values.get(type);
        if (v == null) {
            v = type.defaultValue.apply(holder);
            s.values.put(type, v);
            changed(holder, type, (T) v);
        }
        return (T) v;
    }

    public static <T> T get(Object holder, Supplier<? extends AttachmentType<T>> type) {
        return get(holder, type.get());
    }

    @SuppressWarnings("unchecked")
    public static <T> @Nullable T getExistingOrNull(Object holder, AttachmentType<T> type) {
        Store s = store(holder);
        return s == null ? null : (T) s.values.get(type);
    }

    public static <T> @Nullable T getExistingOrNull(Object holder, Supplier<? extends AttachmentType<T>> type) {
        return getExistingOrNull(holder, type.get());
    }

    public static <T> java.util.Optional<T> getExisting(Object holder, AttachmentType<T> type) {
        return java.util.Optional.ofNullable(getExistingOrNull(holder, type));
    }

    @SuppressWarnings("unchecked")
    public static <T> @Nullable T set(Object holder, AttachmentType<T> type, T value) {
        Store s = store(holder);
        if (s == null) {
            return null;
        }
        T before = (T) s.values.put(type, value);
        changed(holder, type, value);
        return before;
    }

    public static <T> @Nullable T set(Object holder, Supplier<? extends AttachmentType<T>> type, T value) {
        return set(holder, type.get(), value);
    }

    @SuppressWarnings("unchecked")
    public static <T> @Nullable T remove(Object holder, AttachmentType<T> type) {
        Store s = store(holder);
        if (s == null) {
            return null;
        }
        T before = (T) s.values.remove(type);
        if (holder instanceof LevelChunk c) {
            c.setUnsaved(true);
        }
        return before;
    }

    public static <T> @Nullable T remove(Object holder, Supplier<? extends AttachmentType<T>> type) {
        return remove(holder, type.get());
    }

    private static <T> void changed(Object holder, AttachmentType<T> type, T value) {
        if (holder instanceof LevelChunk c) {
            c.setUnsaved(true);
        }
        if (type.syncTo != null && type.syncCodec != null && holder instanceof Entity e && e.level() instanceof ServerLevel level) {
            Sync payload = Sync.of(e, type, value);
            for (ServerPlayer p : level.players()) {
                if ((p == e || p.distanceToSqr(e) < 128 * 128) && type.syncTo.test(holder, p)) {
                    PacketDistributor.sendToPlayer(p, payload);
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void syncAll(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        Store s = store(sp);
        if (s == null) {
            return;
        }
        for (Map.Entry<AttachmentType<?>, Object> a : s.values.entrySet()) {
            AttachmentType type = a.getKey();
            if (type.syncTo != null && type.syncCodec != null && type.syncTo.test(sp, sp)) {
                PacketDistributor.sendToPlayer(sp, Sync.of(sp, type, a.getValue()));
            }
        }
    }

    /** An entity's attachment, sent to a player. */
    record Sync(int entity, ResourceLocation typeId, byte[] value) implements CustomPacketPayload {
        static final Type<Sync> TYPE = new Type<>(new ResourceLocation(GroundBurst.MODID, "attachment_sync"));
        static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.of(
                (b, s) -> {
                    b.writeVarInt(s.entity);
                    b.writeResourceLocation(s.typeId);
                    b.writeByteArray(s.value);
                },
                b -> new Sync(b.readVarInt(), b.readResourceLocation(), b.readByteArray()));

        @SuppressWarnings("unchecked")
        static <T> Sync of(Entity e, AttachmentType<T> type, T value) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            ((StreamCodec<FriendlyByteBuf, T>) type.syncCodec).encode(buf, value);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new Sync(e.getId(), type.id(), bytes);
        }

        @Override
        public Type<Sync> type() {
            return TYPE;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        void apply() {
            net.minecraft.world.entity.player.Player player = Network.clientPlayer.get();
            Level level = player == null ? null : player.level();
            Entity e = level == null ? null : level.getEntity(this.entity);
            AttachmentType type = TYPES.get(this.typeId);
            if (e == null || type == null || type.syncCodec == null) {
                return;
            }
            Store s = store(e);
            if (s != null) {
                s.values.put(type, type.syncCodec.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(this.value))));
            }
        }
    }

    /** One entity's or chunk's attachments, saved with it. */
    static final class Store implements ICapabilitySerializable<CompoundTag> {
        final Map<AttachmentType<?>, Object> values = new LinkedHashMap<>();
        final LazyOptional<Store> optional = LazyOptional.of(() -> this);
        private final Object holder;

        Store(Object holder) {
            this.holder = holder;
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return cap == STORE ? this.optional.cast() : LazyOptional.empty();
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public CompoundTag serializeNBT() {
            CompoundTag out = new CompoundTag();
            for (Map.Entry<AttachmentType<?>, Object> a : this.values.entrySet()) {
                AttachmentType type = a.getKey();
                if (type.codec == null || !type.named() || !type.shouldSerialize.test(a.getValue())) {
                    continue;
                }
                type.codec.encodeStart(NbtOps.INSTANCE, a.getValue()).result()
                        .ifPresent(t -> out.put(type.id().toString(), (Tag) t));
            }
            return out;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void deserializeNBT(CompoundTag tag) {
            for (String key : tag.getAllKeys()) {
                AttachmentType type = TYPES.get(ResourceLocation.tryParse(key));
                if (type == null || type.codec == null) {
                    continue;
                }
                type.codec.parse(NbtOps.INSTANCE, tag.get(key)).result().ifPresent(v -> this.values.put(type, v));
            }
        }
    }
}
