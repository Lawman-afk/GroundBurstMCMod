package dev.groundburst.legacy;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import dev.groundburst.GroundBurst;
import dev.groundburst.legacy.net.CustomPacketPayload;
import dev.groundburst.legacy.net.Network;
import dev.groundburst.legacy.net.PacketDistributor;
import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;

/**
 * NeoForge's data maps on Forge 1.20.1: each registered map read from the data packs as the server's data loads (a
 * pack's file may replace the ones below it), its tags resolved once they are known, and sent to joining players.
 */
public final class DataMaps {
    private DataMaps() {}

    private static final List<DataMapType<?, ?>> TYPES = new ArrayList<>();

    static void add(DataMapType<?, ?> type) {
        TYPES.add(type);
    }

    static void register() {
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new Loader()));
        MinecraftForge.EVENT_BUS.addListener((TagsUpdatedEvent e) -> {
            TYPES.forEach(DataMapType::stale);
            MinecraftForge.EVENT_BUS.post(new DataMapsUpdatedEvent(e.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED));
        });
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> {
            Sync sync = Sync.now();
            if (e.getPlayer() != null) {
                PacketDistributor.sendToPlayer(e.getPlayer(), sync);
            } else {
                e.getPlayerList().getPlayers().forEach(p -> PacketDistributor.sendToPlayer(p, sync));
            }
        });
        Network.playToClient(Sync.TYPE, Sync.CODEC, (payload, context) -> payload.apply());
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<DataMapType<?, ?>, List<JsonObject>>> {
        @Override
        protected Map<DataMapType<?, ?>, List<JsonObject>> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<DataMapType<?, ?>, List<JsonObject>> out = new LinkedHashMap<>();
            for (DataMapType<?, ?> type : TYPES) {
                ResourceLocation file = new ResourceLocation(type.id().getNamespace(), type.folder() + "/" + type.id().getPath() + ".json");
                List<JsonObject> files = new ArrayList<>();
                for (Resource resource : manager.getResourceStack(file)) {
                    try (Reader reader = resource.openAsReader()) {
                        files.add(JsonParser.parseReader(reader).getAsJsonObject());
                    } catch (Exception ex) {
                        GroundBurst.LOGGER.error("Data map {} in {} could not be read", type.id(), resource.sourcePackId(), ex);
                    }
                }
                out.put(type, files);
            }
            return out;
        }

        @Override
        protected void apply(Map<DataMapType<?, ?>, List<JsonObject>> files, ResourceManager manager, ProfilerFiller profiler) {
            files.forEach((type, list) -> {
                type.clear();
                for (JsonObject json : list) {
                    read(type, json);
                }
            });
        }
    }

    private static <R, T> void read(DataMapType<R, T> type, JsonObject json) {
        if (json.has("replace") && json.get("replace").getAsBoolean()) {
            type.byId.clear();
            type.byTag.clear();
        }
        if (json.has("values")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("values").entrySet()) {
                JsonElement v = e.getValue();
                if (v.isJsonObject() && v.getAsJsonObject().has("value")) {
                    v = v.getAsJsonObject().get("value");
                }
                T value = type.codec.parse(JsonOps.INSTANCE, v).resultOrPartial(err -> GroundBurst.LOGGER.error("Data map {}: {}", type.id(), err))
                        .orElse(null);
                if (value != null) {
                    put(type, e.getKey(), value);
                }
            }
        }
        if (json.has("remove")) {
            for (JsonElement r : json.getAsJsonArray("remove")) {
                String key = r.getAsString();
                if (key.startsWith("#")) {
                    type.byTag.remove(TagKey.create(type.registryKey(), new ResourceLocation(key.substring(1))));
                } else {
                    type.byId.remove(new ResourceLocation(key));
                }
            }
        }
    }

    private static <R, T> void put(DataMapType<R, T> type, String key, T value) {
        if (key.startsWith("#")) {
            type.byTag.put(TagKey.create(type.registryKey(), new ResourceLocation(key.substring(1))), value);
        } else {
            type.byId.put(new ResourceLocation(key), value);
        }
    }

    /** Every data map, as JSON, for a player (the client reads them as the server did). */
    record Sync(Map<ResourceLocation, String> maps) implements CustomPacketPayload {
        static final Type<Sync> TYPE = new Type<>(new ResourceLocation(GroundBurst.MODID, "data_map_sync"));
        static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.of(
                (b, s) -> b.writeMap(s.maps, FriendlyByteBuf::writeResourceLocation, (bb, v) -> bb.writeUtf(v, 1 << 20)),
                b -> new Sync(b.readMap(FriendlyByteBuf::readResourceLocation, bb -> bb.readUtf(1 << 20))));

        static Sync now() {
            Map<ResourceLocation, String> maps = new LinkedHashMap<>();
            for (DataMapType<?, ?> type : TYPES) {
                maps.put(type.id().withPrefix(type.folder() + "/"), json(type).toString());
            }
            return new Sync(maps);
        }

        private static <R, T> JsonObject json(DataMapType<R, T> type) {
            JsonObject values = new JsonObject();
            type.byTag.forEach((tag, v) -> type.codec.encodeStart(JsonOps.INSTANCE, v).result().ifPresent(j -> values.add("#" + tag.location(), j)));
            type.byId.forEach((id, v) -> type.codec.encodeStart(JsonOps.INSTANCE, v).result().ifPresent(j -> values.add(id.toString(), j)));
            JsonObject out = new JsonObject();
            out.add("values", values);
            return out;
        }

        @Override
        public Type<Sync> type() {
            return TYPE;
        }

        void apply() {
            for (DataMapType<?, ?> type : TYPES) {
                String json = this.maps.get(type.id().withPrefix(type.folder() + "/"));
                if (json != null) {
                    type.clear();
                    read(type, JsonParser.parseString(json).getAsJsonObject());
                }
            }
            MinecraftForge.EVENT_BUS.post(new DataMapsUpdatedEvent(true));
        }
    }
}
