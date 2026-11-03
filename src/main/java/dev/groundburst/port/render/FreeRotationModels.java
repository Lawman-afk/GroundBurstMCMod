package dev.groundburst.port.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.ElementsModel;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

/**
 * Block models whose parts turn by any angle, as Minecraft 26.x's do (round prisms, noses, slopes): 1.21.1 reads a
 * part's turn only at a multiple of 22.5 degrees up to 45, though its baker turns one by any angle. tools/backport/port.py
 * moves such a model's parts to {@code groundburst:elements} and names this loader; it reads them as 1.21.1 does
 * otherwise and bakes them as NeoForge's elements model.
 */
public final class FreeRotationModels {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("groundburst", "free_rotation");

    private FreeRotationModels() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((ModelEvent.RegisterGeometryLoaders event) -> event.register(ID, Loader.INSTANCE));
    }

    private static final class Loader implements IGeometryLoader<ElementsModel> {
        static final Loader INSTANCE = new Loader();

        @Override
        public ElementsModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            List<BlockElement> elements = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "groundburst:elements")) {
                elements.add(element(e.getAsJsonObject(), context));
            }
            return new ElementsModel(elements);
        }
    }

    private static BlockElement element(JsonObject json, JsonDeserializationContext context) {
        Vector3f from = vector(json, "from"), to = vector(json, "to");
        BlockElementRotation rotation = null;
        if (json.has("rotation")) {
            JsonObject r = GsonHelper.getAsJsonObject(json, "rotation");
            Vector3f origin = vector(r, "origin").mul(0.0625F);
            Direction.Axis axis = Direction.Axis.byName(GsonHelper.getAsString(r, "axis"));
            if (axis == null) {
                throw new JsonParseException("Invalid rotation axis: " + GsonHelper.getAsString(r, "axis"));
            }
            rotation = new BlockElementRotation(origin, axis, GsonHelper.getAsFloat(r, "angle"), GsonHelper.getAsBoolean(r, "rescale", false));
        }
        Map<Direction, BlockElementFace> faces = new EnumMap<>(Direction.class);
        for (Map.Entry<String, JsonElement> face : GsonHelper.getAsJsonObject(json, "faces").entrySet()) {
            Direction side = Direction.byName(face.getKey());
            if (side == null) {
                throw new JsonParseException("Unknown face: " + face.getKey());
            }
            faces.put(side, context.deserialize(face.getValue(), BlockElementFace.class));
        }
        boolean shade = GsonHelper.getAsBoolean(json, "shade", true);
        ExtraFaceData data = ExtraFaceData.read(json.get("neoforge_data"), ExtraFaceData.DEFAULT);
        return new BlockElement(from, to, faces, rotation, shade, data);
    }

    private static Vector3f vector(JsonObject json, String key) {
        var a = GsonHelper.getAsJsonArray(json, key);
        if (a.size() != 3) {
            throw new JsonParseException("Expected 3 " + key + " values, found: " + a.size());
        }
        return new Vector3f(GsonHelper.convertToFloat(a.get(0), key + "[0]"), GsonHelper.convertToFloat(a.get(1), key + "[1]"),
                GsonHelper.convertToFloat(a.get(2), key + "[2]"));
    }
}
