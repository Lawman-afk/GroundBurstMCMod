package dev.groundburst.port.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.SimpleUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;

/**
 * Block models whose parts turn by any angle, as Minecraft 26.x's do (round prisms, noses, slopes): 1.21.1 reads a
 * part's turn only at a multiple of 22.5 degrees up to 45, though its baker turns one by any angle; and 26.x turns a part
 * about all three axes at once ({@code "x"}, {@code "y"}, {@code "z"}: about x, then y, then z), which 1.21.1's baker
 * cannot. tools/backport/port.py moves such a model's parts to {@code groundburst:elements} and names this loader: a part
 * turned about one axis is baked as 1.21.1 bakes any; one turned about three is baked unturned and its faces' corners
 * then turned (about its origin, then by the block state's turn), their facing and normals worked out again.
 */
public final class FreeRotationModels {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("groundburst", "free_rotation");

    private FreeRotationModels() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((ModelEvent.RegisterGeometryLoaders event) -> event.register(ID, Loader.INSTANCE));
    }

    /** A part turned about all three axes: its turn (block units, about its origin). */
    private record Turn(Vector3f origin, Matrix4f matrix) {}

    private static final class Loader implements IGeometryLoader<Elements> {
        static final Loader INSTANCE = new Loader();

        @Override
        public Elements read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            List<BlockElement> elements = new ArrayList<>();
            Map<BlockElement, Turn> turns = new IdentityHashMap<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "groundburst:elements")) {
                element(e.getAsJsonObject(), context, elements, turns);
            }
            return new Elements(elements, turns);
        }
    }

    /** The parts, baked as NeoForge's elements model bakes them, but those turned about three axes (see the class). */
    private static final class Elements extends SimpleUnbakedGeometry<Elements> {
        private final List<BlockElement> elements;
        private final Map<BlockElement, Turn> turns;

        Elements(List<BlockElement> elements, Map<BlockElement, Turn> turns) {
            this.elements = elements;
            this.turns = turns;
        }

        @Override
        protected void addQuads(IGeometryBakingContext context, IModelBuilder<?> builder, ModelBaker baker,
                Function<Material, TextureAtlasSprite> sprites, ModelState state) {
            var root = context.getRootTransform();
            if (!root.isIdentity()) {
                state = UnbakedGeometryHelper.composeRootTransformIntoModelState(state, root);
            }
            Matrix4f blockTurn = state.getRotation().getMatrix();
            for (BlockElement element : this.elements) {
                Turn turn = this.turns.get(element);
                for (Direction direction : element.faces.keySet()) {
                    BlockElementFace face = element.faces.get(direction);
                    TextureAtlasSprite sprite = sprites.apply(context.getMaterial(face.texture()));
                    BakedQuad quad = turn == null ? BlockModel.bakeFace(element, face, sprite, direction, state)
                            : turned(BlockModel.bakeFace(element, face, sprite, direction, BlockModelRotation.X0_Y0), turn, blockTurn);
                    if (face.cullForDirection() == null) {
                        builder.addUnculledFace(quad);
                    } else {
                        builder.addCulledFace(state.getRotation().rotateTransform(face.cullForDirection()), quad);
                    }
                }
            }
        }
    }

    /** A quad baked unturned, its corners turned about the part's origin, then by the block state's turn about the block's middle. */
    private static BakedQuad turned(BakedQuad quad, Turn turn, Matrix4f blockTurn) {
        int[] v = quad.getVertices().clone();
        Vector3f middle = new Vector3f(0.5f, 0.5f, 0.5f);
        for (int i = 0; i < 4; i++) {
            int at = i * 8;
            Vector3f p = new Vector3f(Float.intBitsToFloat(v[at]), Float.intBitsToFloat(v[at + 1]), Float.intBitsToFloat(v[at + 2]));
            Vector4f q = turn.matrix().transform(new Vector4f(p.sub(turn.origin()), 1.0f));
            p.set(q.x, q.y, q.z).add(turn.origin()).sub(middle);
            q = blockTurn.transform(new Vector4f(p, 1.0f));
            v[at] = Float.floatToRawIntBits(q.x + 0.5f);
            v[at + 1] = Float.floatToRawIntBits(q.y + 0.5f);
            v[at + 2] = Float.floatToRawIntBits(q.z + 0.5f);
        }
        Direction facing = FaceBakery.calculateFacing(v);
        ClientHooks.fillNormal(v, facing);
        return new BakedQuad(v, quad.getTintIndex(), facing, quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }

    private static void element(JsonObject json, JsonDeserializationContext context, List<BlockElement> out, Map<BlockElement, Turn> turns) {
        Vector3f from = vector(json, "from"), to = vector(json, "to");
        BlockElementRotation rotation = null;
        Turn turn = null;
        if (json.has("rotation")) {
            JsonObject r = GsonHelper.getAsJsonObject(json, "rotation");
            Vector3f origin = vector(r, "origin").mul(0.0625F);
            if (r.has("axis")) {
                Direction.Axis axis = Direction.Axis.byName(GsonHelper.getAsString(r, "axis"));
                if (axis == null) {
                    throw new JsonParseException("Invalid rotation axis: " + GsonHelper.getAsString(r, "axis"));
                }
                rotation = new BlockElementRotation(origin, axis, GsonHelper.getAsFloat(r, "angle"), GsonHelper.getAsBoolean(r, "rescale", false));
            } else {
                // 26.x's turn about x, then y, then z (JOML's rotationZYX: Rz * Ry * Rx).
                float x = GsonHelper.getAsFloat(r, "x", 0), y = GsonHelper.getAsFloat(r, "y", 0), z = GsonHelper.getAsFloat(r, "z", 0);
                turn = new Turn(origin, new Matrix4f().rotationZYX((float) Math.toRadians(z), (float) Math.toRadians(y), (float) Math.toRadians(x)));
            }
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
        BlockElement element = new BlockElement(from, to, faces, rotation, shade, data);
        out.add(element);
        if (turn != null) {
            turns.put(element, turn);
        }
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
