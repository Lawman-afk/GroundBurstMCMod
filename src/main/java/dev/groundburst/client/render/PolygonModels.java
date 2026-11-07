package dev.groundburst.client.render;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.math.Transformation;

import dev.groundburst.GroundBurst;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.context.ContextMap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateUnbakedModel;
import net.neoforged.neoforge.client.model.ExtendedUnbakedGeometry;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * Block and item models with flat polygons beside their elements (DESIGN.md §4.4): a round part's cap, a ring's sides.
 * A model's elements are rectangles; a round part built of them (planks turned about its axis) has caps that overlap in
 * one plane and flicker unless they draw alike. Drawn instead as one polygon, a cap overlaps nothing in its plane and is
 * textured as any face is. The model is the game's own (its elements, textures, display, read as the game reads them),
 * with {@code "loader": "groundburst:polygons"} and its polygons under {@code "groundburst:polygons"}: each its points
 * (block pixels, counter-clockwise seen from outside, convex), each point's UV (0..16 across the texture), its texture,
 * and optionally {@code shade}, {@code ao} (smooth lighting's corner shade), {@code light}, {@code tint}, {@code cull}.
 * It is baked into quads fanned from its first point (a three-sided rest as a quad with its last corner twice), turned
 * with the model's state as the elements are. Written by tools/gb/model.py (make_polygon).
 */
public final class PolygonModels {
    public static final Identifier ID = GroundBurst.id("polygons");
    private static final String KEY = GroundBurst.MODID + ":polygons";

    private PolygonModels() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((ModelEvent.RegisterLoaders event) -> event.register(ID, Loader.INSTANCE));
    }

    /** One polygon: its points (block pixels), each point's UV (0..16), and how it is drawn. */
    private record Polygon(String texture, float[][] points, float[][] uvs, boolean shade, boolean ao, int light, int tint,
            @Nullable Direction cull) {}

    private enum Loader implements UnbakedModelLoader<Model> {
        INSTANCE;

        @Override
        public Model read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            JsonObject model = json.deepCopy();
            model.remove("loader");
            JsonElement polygons = model.remove(KEY);
            List<Polygon> list = new ArrayList<>();
            if (polygons != null) {
                for (JsonElement e : polygons.getAsJsonArray()) {
                    list.add(polygon(e.getAsJsonObject()));
                }
            }
            return new Model(context.deserialize(model, UnbakedModel.class), List.copyOf(list));
        }

        private static Polygon polygon(JsonObject o) {
            JsonArray points = GsonHelper.getAsJsonArray(o, "points");
            JsonArray uv = GsonHelper.getAsJsonArray(o, "uv");
            if (points.size() < 3 || uv.size() != points.size()) {
                throw new JsonParseException("A polygon needs three points or more, and a UV for each");
            }
            float[][] p = new float[points.size()][];
            float[][] t = new float[points.size()][];
            for (int i = 0; i < p.length; i++) {
                JsonArray a = points.get(i).getAsJsonArray();
                p[i] = new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
                JsonArray b = uv.get(i).getAsJsonArray();
                t[i] = new float[] {b.get(0).getAsFloat(), b.get(1).getAsFloat()};
            }
            Direction cull = o.has("cull") ? Direction.byName(GsonHelper.getAsString(o, "cull")) : null;
            return new Polygon(GsonHelper.getAsString(o, "texture", "#0"), p, t, GsonHelper.getAsBoolean(o, "shade", true),
                    GsonHelper.getAsBoolean(o, "ao", true), GsonHelper.getAsInt(o, "light", 0), GsonHelper.getAsInt(o, "tint", -1), cull);
        }
    }

    /** The game's model, its geometry with the polygons added. */
    private static final class Model extends DelegateUnbakedModel {
        private final List<Polygon> polygons;

        Model(UnbakedModel model, List<Polygon> polygons) {
            super(model);
            this.polygons = polygons;
        }

        @Override
        public @Nullable UnbakedGeometry geometry() {
            UnbakedGeometry inner = this.delegate.geometry();
            return new Geometry(inner == null ? UnbakedGeometry.EMPTY : inner, this.polygons);
        }
    }

    private record Geometry(UnbakedGeometry inner, List<Polygon> polygons) implements ExtendedUnbakedGeometry {
        @Override
        public QuadCollection bake(TextureSlots slots, ModelBaker baker, ModelState state, ModelDebugName name, ContextMap properties) {
            QuadCollection base = this.inner.bake(slots, baker, state, name, properties);
            QuadCollection.Builder out = new QuadCollection.Builder();
            for (BakedQuad quad : base.getQuads(null)) {
                out.addUnculledFace(quad);
            }
            for (Direction d : Direction.values()) {
                for (BakedQuad quad : base.getQuads(d)) {
                    out.addCulledFace(d, quad);
                }
            }
            ModelState turned = UnbakedElementsHelper.composeRootTransformIntoModelState(state,
                    properties.getOrDefault(NeoForgeModelProperties.TRANSFORM, Transformation.IDENTITY));
            for (Polygon polygon : this.polygons) {
                Material.Baked material = baker.materials().resolveSlot(slots, polygon.texture(), name);
                quads(polygon, material, turned, out);
            }
            return out.build();
        }
    }

    /** A polygon's quads: its points turned with the model (about the block's middle), fanned from its first. */
    private static void quads(Polygon p, Material.Baked material, ModelState state, QuadCollection.Builder out) {
        Transformation transform = state.transformation();
        boolean turned = !transform.isIdentity();
        Transformation fromCorner = turned ? transform.blockCenterToCorner() : transform;
        int n = p.points().length;
        Vector3f[] at = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            Vector4f v = new Vector4f(p.points()[i][0] / 16f, p.points()[i][1] / 16f, p.points()[i][2] / 16f, 1f);
            if (turned) {
                fromCorner.transformPosition(v);
            }
            at[i] = new Vector3f(v.x, v.y, v.z);
        }
        // The normal from the turned points (Newell's way: robust however the polygon was turned).
        Vector3f normal = new Vector3f();
        for (int i = 0; i < n; i++) {
            Vector3f a = at[i], b = at[(i + 1) % n];
            normal.add((a.y - b.y) * (a.z + b.z), (a.z - b.z) * (a.x + b.x), (a.x - b.x) * (a.y + b.y));
        }
        normal.normalize();
        Direction facing = Direction.getApproximateNearest(normal.x, normal.y, normal.z);
        Direction cull = p.cull() == null ? null : Direction.rotate(transform.getMatrix(), p.cull());
        Transparency transparency = material.forceTranslucent() ? Transparency.TRANSLUCENT : material.sprite().transparency();
        for (int k = 1; k + 1 < n; k += 2) {
            int[] corners = k + 2 < n ? new int[] {0, k, k + 1, k + 2} : new int[] {0, k, k + 1, k + 1};
            QuadBakingVertexConsumer quad = new QuadBakingVertexConsumer();
            quad.setSprite(material, transparency);
            quad.setTintIndex(p.tint());
            quad.setLightEmission(p.light());
            quad.setShadeOverride(p.shade() ? null : Direction.UP);
            quad.setAmbientOcclusion(p.ao());
            quad.setDirection(facing);
            for (int i : corners) {
                quad.addVertex(at[i].x, at[i].y, at[i].z);
                quad.setColor(-1);
                quad.setUv(material.sprite().getU(p.uvs()[i][0] / 16f), material.sprite().getV(p.uvs()[i][1] / 16f));
                quad.setNormal(normal.x, normal.y, normal.z);
            }
            BakedQuad baked = quad.bakeQuad();
            if (cull == null) {
                out.addUnculledFace(baked);
            } else {
                out.addCulledFace(cull, baked);
            }
        }
    }
}
