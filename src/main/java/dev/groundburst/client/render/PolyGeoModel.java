package dev.groundburst.client.render;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoLocator;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.model.DefaultedBlockGeoModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.groundburst.GroundBurst;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;

/**
 * A GeckoLib block model with its bones' polygons drawn (DESIGN.md §4.4): a round part's caps, built as one polygon each
 * (tools/gb/geo.py), laid out as Bedrock's poly mesh under {@code "groundburst:poly_mesh"} (GeckoLib reads a
 * {@code "poly_mesh"} as flat lists of numbers, failing on lists of points, and bakes cubes alone). Each bone
 * with a mesh gets one more cube holding its polygons as quads (points as the cubes': model pixels with x negated, over
 * 16; UVs 0..1 from the texture's top left; three-cornered ones a quad with its last corner twice), drawn with the bone
 * as its cubes are. The model is GeckoLib's baked one, its bones copied with the cube added, kept for as long as
 * GeckoLib keeps that one (a resource reload bakes anew).
 */
public class PolyGeoModel<T extends GeoAnimatable> extends DefaultedBlockGeoModel<T> {
    /** A bone's polygons: laid out as Bedrock's poly mesh, under the mod's own key (GeckoLib fails on a "poly_mesh" of points). */
    private static final String KEY = GroundBurst.MODID + ":poly_mesh";
    /** GeckoLib's baked models, each with its poly meshes added (or itself, having none), until GeckoLib lets it go. */
    private static final Map<BakedGeoModel, BakedGeoModel> WITH_POLYGONS = Collections.synchronizedMap(new WeakHashMap<>());

    public PolyGeoModel(Identifier id) {
        super(id);
    }

    @Override
    public BakedGeoModel getBakedModel(Identifier location) {
        BakedGeoModel base = super.getBakedModel(location);
        if (base.isMissingno()) {
            return base;
        }
        BakedGeoModel known = WITH_POLYGONS.get(base);
        if (known == null) {
            known = withPolygons(location, base);
            WITH_POLYGONS.put(base, known);
        }
        return known;
    }

    /** The model with each bone's poly mesh (read from its file) added as a cube; the model itself if it has none. */
    static BakedGeoModel withPolygons(Identifier location, BakedGeoModel base) {
        Map<String, List<GeoQuad>> meshes;
        try {
            meshes = meshes(location);
        } catch (RuntimeException e) {
            GroundBurst.LOGGER.warn("Could not read the poly meshes of {}", location, e);
            return base;
        }
        if (meshes.isEmpty()) {
            return base;
        }
        for (GeoBone bone : base.topLevelBones()) {
            if (!allCuboid(bone)) {
                GroundBurst.LOGGER.warn("{}: a bone not of cubes; its poly meshes left out", location);
                return base;
            }
        }
        Map<String, GeoLocator> locators = new HashMap<>();
        GeoBone[] top = new GeoBone[base.topLevelBones().length];
        for (int i = 0; i < top.length; i++) {
            top[i] = copy(base.topLevelBones()[i], null, meshes, locators);
        }
        return new BakedGeoModel(top, locators, base.properties());
    }

    private static boolean allCuboid(GeoBone bone) {
        if (!(bone instanceof CuboidGeoBone)) {
            return false;
        }
        for (GeoBone child : bone.children()) {
            if (!allCuboid(child)) {
                return false;
            }
        }
        return true;
    }

    /** A bone and its children copied (each child's parent the copy, each locator on its copied bone), a mesh's cube added. */
    private static GeoBone copy(GeoBone old, GeoBone parent, Map<String, List<GeoQuad>> meshes, Map<String, GeoLocator> locators) {
        GeoCube[] cubes = ((CuboidGeoBone) old).cubes;
        List<GeoQuad> mesh = meshes.get(old.name());
        if (mesh != null && !mesh.isEmpty()) {
            cubes = Arrays.copyOf(cubes, cubes.length + 1);
            // (Its size only so GeckoLib's renderer takes it for a solid cube: a flat one's faces that look down or
            // away have their normals turned back (RenderUtil.fixInvertedFlatCube), and a cap underneath would light as a top.)
            cubes[cubes.length - 1] = new GeoCube(mesh.toArray(GeoQuad[]::new), Vec3.ZERO, Vec3.ZERO, new Vec3(1, 1, 1));
        }
        GeoBone[] children = new GeoBone[old.children().length];
        GeoLocator[] locs = new GeoLocator[old.locators().length];
        CuboidGeoBone bone = new CuboidGeoBone(parent, old.name(), children, cubes, locs, old.pivotX(), old.pivotY(), old.pivotZ(),
                old.baseRotX(), old.baseRotY(), old.baseRotZ());
        for (int i = 0; i < locs.length; i++) {
            GeoLocator l = old.locators()[i];
            locs[i] = new GeoLocator(bone, l.name(), l.offsetX(), l.offsetY(), l.offsetZ(), l.rotX(), l.rotY(), l.rotZ());
            locators.put(l.name(), locs[i]);
        }
        for (int i = 0; i < children.length; i++) {
            children[i] = copy(old.children()[i], bone, meshes, locators);
        }
        return bone;
    }

    /** Each bone's poly mesh in the model's file (geckolib/models/<path>.geo.json), as quads; none if it has no meshes. */
    private static Map<String, List<GeoQuad>> meshes(Identifier location) {
        Identifier file = location.getPath().endsWith(".json") ? location
                : Identifier.fromNamespaceAndPath(location.getNamespace(), "geckolib/models/" + location.getPath() + ".geo.json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
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
                if (bone.has(KEY)) {
                    out.put(bone.get("name").getAsString(), quads(bone.getAsJsonObject(KEY)));
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
                vertices[i] = new GeoVertex(-at[0] / 16f, at[1] / 16f, at[2] / 16f, uv[0], uv[1]);
                n = normals[c.get(1).getAsInt()];
            }
            float nx = -n[0], ny = n[1], nz = n[2];
            out.add(new GeoQuad(vertices, nx, ny, nz, Direction.getApproximateNearest(nx, ny, nz)));
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
