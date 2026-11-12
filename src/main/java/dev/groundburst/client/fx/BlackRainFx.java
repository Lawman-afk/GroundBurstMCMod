package dev.groundburst.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.groundburst.client.fallout.FalloutClient;
import dev.groundburst.content.fallout.BlackRain;
import dev.groundburst.content.fallout.Plumes;
import dev.groundburst.content.radiation.RadiationSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Black rain on the client (DESIGN.md §21.10; where it falls: {@link BlackRain}, from the radiation sources and plumes the
 * server sends). The game's own rain is drawn sooty where it is black ({@code mixin/WeatherEffectRendererMixin}: each
 * column's colour, toward {@link #SOOT} by its strength); a ground burst's own rain, which falls in clear weather too, is
 * drawn round the camera as sheets of the game's rain streaks (the storm's way), with the sound of rain; under either the
 * sky and fog turn a sooty grey.
 */
public final class BlackRainFx {
    /** The rain's colour at full strength: soot, a little brown (it fell "like mud"). */
    static final int SOOT = 0x2E2A25;
    /** How far the sky and fog turn sooty under it at full strength (a grey sky even under light black rain: the root of it). */
    static final float TINT = 0.6f;
    private static final Identifier RAIN = Identifier.withDefaultNamespace("textures/environment/rain.png");
    private static final RandomSource RANDOM = RandomSource.create();
    private static int soundIn;

    private BlackRainFx() {}

    private static List<RadiationSource> sources(ClientLevel level) {
        return FalloutFx.sources(level);
    }

    /** How black the weather's rain falling at (x, y, z) is (0: plain rain). */
    static float weather(ClientLevel level, double x, double y, double z) {
        List<RadiationSource> sources = sources(level);
        List<Plumes.Cloud> clouds = FalloutClient.clouds();
        if (sources.isEmpty() && clouds.isEmpty()) {
            return 0;
        }
        return BlackRain.strength(level, sources, clouds, x, y, z, level.getGameTime());
    }

    /**
     * A rain column's colour (the mixin's): white at {@code alpha} as the game draws it, or toward soot where the rain is
     * black. Called for each column each frame: cheap when nothing is irradiated (the lists are empty).
     */
    public static int rainColour(int white, int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return white;
        }
        float k = weather(mc.level, x + 0.5, y, z + 0.5);
        if (k <= 0) {
            return white;
        }
        int rgb = ARGB.srgbLerp(k, 0xFFFFFFFF, 0xFF000000 | SOOT) & 0xFFFFFF;
        return ARGB.color(ARGB.alpha(white), rgb);
    }

    /** Whether a splash where the rain lands at {@code pos} is a dark one (black rain falls there). */
    public static boolean darkSplash(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && weather(mc.level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 0.3f;
    }

    /** How sooty the sky and fog are where the camera is: black rain falling on it (the weather's, or a burst's own). */
    static float tint(ClientLevel level, Vec3 camera) {
        float own = BlackRain.ownRain(FalloutClient.clouds(), camera.x, camera.z);
        float weather = level.isRaining() ? level.getRainLevel(1) * weather(level, camera.x, camera.y, camera.z) : 0;
        return TINT * (float) Math.sqrt(Math.max(own, weather));
    }

    /** {@code color} turned toward a sooty grey and darkened, keeping a little of its hue. */
    static Vector3f sooty(float r, float g, float b, float amount) {
        float lum = 0.3f * r + 0.59f * g + 0.11f * b;
        float k = 1 - 0.45f * amount;
        return new Vector3f((r + (lum * 0.95f - r) * amount) * k, (g + (lum * 0.93f - g) * amount) * k, (b + (lum * 0.9f - b) * amount) * k);
    }

    /** The burst's own rain round the camera while it falls on it (in clear weather, where the game draws no rain). */
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, ClientLevel level, Vec3 camera, double now) {
        List<Plumes.Cloud> clouds = FalloutClient.clouds();
        float k;
        if (level.getRainLevel(1) > 0.5f) {
            // In rain the game's own columns are drawn black instead; but a shader pack draws its rain in its own colour
            // (Complementary's and BSL's take no colour from the game), so under one the sheets fall in the rain too, as
            // dark as the rain is black there.
            k = FxRenderTypes.IRIS && IrisFx.packInUse() ? level.getRainLevel(1) * weather(level, camera.x, camera.y, camera.z) : 0;
        } else {
            k = clouds.isEmpty() ? 0 : BlackRain.ownRain(clouds, camera.x, camera.z);
        }
        if (k < 0.05f || !level.canSeeSky(BlockPos.containing(camera))) {
            return;
        }
        float strength = k;
        FxRenderTypes.submit(collector, poseStack, FxRenderTypes.fire(RAIN), (pose, buffer) -> sheets(buffer, pose, now, strength));
    }

    /** Sheets of falling rain round the camera, sooty: two rings, the near one denser. */
    private static void sheets(VertexConsumer b, PoseStack.Pose pose, double now, float k) {
        int color = ARGB.color((int) (Math.min(1, 0.75f * k + 0.15f) * 255), SOOT);
        float scroll = (float) (-(now * 0.09) % 1);
        for (float radius : new float[] {3.2f, 6f, 10f}) {
            int segments = radius < 4 ? 12 : radius < 7 ? 18 : 24;
            for (int i = 0; i < segments; i++) {
                double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
                float x0 = (float) (Math.cos(a0) * radius), z0 = (float) (Math.sin(a0) * radius);
                float x1 = (float) (Math.cos(a1) * radius), z1 = (float) (Math.sin(a1) * radius);
                float bottom = -6, top = 12, v0 = scroll + i * 0.37f, v1 = v0 + (top - bottom) / 8;
                FxVertex.put(b, pose, x0, bottom, z0, 0, v1, color);
                FxVertex.put(b, pose, x1, bottom, z1, 1, v1, color);
                FxVertex.put(b, pose, x1, top, z1, 1, v0, color);
                FxVertex.put(b, pose, x0, top, z0, 0, v0, color);
            }
        }
    }

    /** Once a client tick: the sound of the burst's own rain on the ground round the player (as the game's rain sounds). */
    static void tick(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || level.getRainLevel(1) > 0.5f) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float own = BlackRain.ownRain(FalloutClient.clouds(), camera.x, camera.z);
        if (own < 0.05f || --soundIn > 0) {
            return;
        }
        soundIn = 3 + RANDOM.nextInt(4);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                BlockPos.containing(camera.x + RANDOM.nextInt(9) - 4, camera.y, camera.z + RANDOM.nextInt(9) - 4));
        boolean under = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(camera)).getY() > camera.y;
        level.playLocalSound(ground, under ? SoundEvents.WEATHER_RAIN_ABOVE : SoundEvents.WEATHER_RAIN, SoundSource.WEATHER,
                (under ? 0.1f : 0.2f) * Math.min(1, own * 1.4f), under ? 0.5f : 1, false);
    }
}
