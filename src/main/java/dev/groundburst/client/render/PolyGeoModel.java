package dev.groundburst.client.render;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.groundburst.GroundBurst;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

/**
 * This branch's GeckoLib model with its bones' polygons drawn (main's client/render/PolyGeoModel; main's DESIGN.md
 * §4.4: a round part's caps, built as one polygon each), laid out as Bedrock's poly mesh under
 * {@code "groundburst:poly_mesh"} (GeckoLib passes it by; a {@code "poly_mesh"} it reads as flat lists of numbers). Each
 * bone's is added to it as one more cube of quads (points: model pixels with x negated, over 16; UVs 0..1 from the
 * texture's top left; three-cornered ones a quad with its last corner twice). GeckoLib 4's bones keep their cubes in a list, so the cube is added to GeckoLib's own
 * baked model, once (a resource reload bakes anew).
 */
public class PolyGeoModel<T extends GeoAnimatable> extends DefaultedBlockGeoModel<T> {
    /** GeckoLib's baked models already given their meshes, until GeckoLib lets them go. */
    private static final Set<BakedGeoModel> DONE = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public PolyGeoModel(ResourceLocation id) {
        super(id);
    }

    @Override
    public BakedGeoModel getBakedModel(ResourceLocation location) {
        BakedGeoModel model = super.getBakedModel(location);
        if (model != null && DONE.add(model)) {
            try {
                addPolygons(location, model);
            } catch (RuntimeException e) {
                GroundBurst.LOGGER.warn("Could not read the poly meshes of {}", location, e);
            }
        }
        return model;
    }

    private static void addPolygons(ResourceLocation location, BakedGeoModel model) {
        Map<String, List<GeoQuad>> meshes = meshes(location);
        if (meshes.isEmpty()) {
            return;
        }
        for (GeoBone bone : model.topLevelBones()) {
            add(bone, meshes);
        }
    }

    private static void add(GeoBone bone, Map<String, List<GeoQuad>> meshes) {
        List<GeoQuad> mesh = meshes.get(bone.getName());
        if (mesh != null && !mesh.isEmpty()) {
            // (Its size only so GeckoLib takes it for a solid cube: a flat one's faces looking down or away have their
            // normals turned back, and a cap underneath would light as a top.)
            bone.getCubes().add(new GeoCube(mesh.toArray(GeoQuad[]::new), Vec3.ZERO, Vec3.ZERO, new Vec3(1, 1, 1), 0, false));
        }
        for (GeoBone child : bone.getChildBones()) {
            add(child, meshes);
        }
    }

    /** Each bone's poly mesh in the model's file, as quads; none if it has no meshes. */
    private static Map<String, List<GeoQuad>> meshes(ResourceLocation location) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return Map.of();
        }
        JsonObject json;
        try (Reader reader = resource.get().openAsReader()) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        Map<String, List<GeoQuad>> out = new HashMap<>();
        for (JsonElement g : json.getAsJsonArray("minecraft:geometry")) {
            for (JsonElement b : g.getAsJsonObject().getAsJsonArray("bones")) {
                JsonObject bone = b.getAsJsonObject();
                if (bone.has("groundburst:poly_mesh")) {
                    out.put(bone.get("name").getAsString(), quads(bone.getAsJsonObject("groundburst:poly_mesh")));
                }
            }
        }
        return out;
    }

    private static List<GeoQuad> quads(JsonObject mesh) {
        float[][] positions = vectors(mesh.getAsJsonArray("positions"));
        float[][] normals = vectors(mesh.getAsJsonArray("normals"));
        float[][] uvs = vectors(mesh.getAsJsonArray("uvs"));
        List<GeoQuad> out = new ArrayList<>();
        for (JsonElement p : mesh.getAsJsonArray("polys")) {
            JsonArray corners = p.getAsJsonArray();
            GeoVertex[] vertices = new GeoVertex[4];
            float[] n = null;
            for (int i = 0; i < 4; i++) {
                JsonArray c = corners.get(Math.min(i, corners.size() - 1)).getAsJsonArray();
                float[] at = positions[c.get(0).getAsInt()];
                float[] uv = uvs[c.get(2).getAsInt()];
                vertices[i] = new GeoVertex(new Vector3f(-at[0] / 16f, at[1] / 16f, at[2] / 16f), uv[0], uv[1]);
                n = normals[c.get(1).getAsInt()];
            }
            Vector3f normal = new Vector3f(-n[0], n[1], n[2]);
            out.add(new GeoQuad(vertices, normal, Direction.getNearest(normal.x, normal.y, normal.z)));
        }
        return out;
    }

    private static float[][] vectors(JsonArray array) {
        float[][] out = new float[array.size()][];
        for (int i = 0; i < out.length; i++) {
            JsonArray v = array.get(i).getAsJsonArray();
            out[i] = new float[v.size()];
            for (int k = 0; k < v.size(); k++) {
                out[i][k] = v.get(k).getAsFloat();
            }
        }
        return out;
    }
}
