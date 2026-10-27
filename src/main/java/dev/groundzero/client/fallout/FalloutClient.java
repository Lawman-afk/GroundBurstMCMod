package dev.groundzero.client.fallout;

import java.util.List;
import java.util.function.ToIntBiFunction;

import dev.groundzero.content.fallout.GZFallout;
import dev.groundzero.content.fallout.Plumes;
import dev.groundzero.content.fallout.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The aftermath on the client (DESIGN.md §21.8, slice 12b): the dead tint that turns vanilla's grass block into the Dead
 * Grass Block; the wind and the plumes the server tells it of; and the ash falling round the player under a plume,
 * drifting with the wind.
 */
public final class FalloutClient {
    /** The dead grass's colour: dry and straw-grey; in the world a quarter of the biome's own grass colour shows through. */
    public static final int DEAD_GRASS = 0xFF9C8F62;
    static final float BIOME_SHARE = 0.25f;
    /** Flakes a tick round a player at a plume's heart, at full strength (fewer toward its edge and as it weakens). */
    static final float FLAKES = 14;
    /** How much of the wind's speed the flakes take (it blows slower near the ground). */
    static final float FLAKE_DRIFT = 0.6f;

    private static float windHeading, windSpeed = 2;
    private static List<Plumes.Cloud> clouds = List.of();
    /** How far the wind has carried things on this client, blocks (the fallout clouds overhead drift by it). */
    private static double driftX, driftZ;
    private static final RandomSource RANDOM = RandomSource.create();

    private FalloutClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterColorHandlersEvent.BlockTintSources event) -> event.register(
                List.of(dead(DEAD_GRASS, BiomeColors::getAverageGrassColor)), GZFallout.DEAD_GRASS_BLOCK.block().get()));
        modBus.addListener((RegisterParticleProvidersEvent event) ->
                event.registerSpriteSet(GZFallout.FALLOUT_ASH_PARTICLE.get(), AshFlakeParticle.Provider::new));
        Wind.Sync.hook = s -> {
            windHeading = s.heading();
            windSpeed = s.speed();
        };
        Plumes.Sync.hook = s -> clouds = s.clouds();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> clouds = List.of());
    }

    /** The wind in the player's level: where it carries things in a second, blocks. */
    public static Vec3 wind() {
        return Wind.vector(windHeading, windSpeed);
    }

    /** How far the wind has carried things here, blocks, between ticks by {@code partial}. */
    public static double driftX(float partial) {
        return driftX + wind().x / 20 * partial;
    }

    public static double driftZ(float partial) {
        return driftZ + wind().z / 20 * partial;
    }

    private static void tick(Minecraft mc) {
        if (mc.isPaused()) {
            return;
        }
        Vec3 wind = wind();
        driftX += wind.x / 20;
        driftZ += wind.z / 20;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || clouds.isEmpty()) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        float strength = 0;
        for (Plumes.Cloud c : clouds) {
            double d = Math.hypot(eye.x - c.x(), eye.z - c.z());
            if (d < c.radius()) {
                strength = Math.max(strength, c.share() * (1 - 0.6f * (float) (d / c.radius())));
            }
        }
        if (strength <= 0) {
            return;
        }
        // Flakes a tick, the fraction by chance; spawned upwind and above, so they drift down into view.
        float rate = FLAKES * strength;
        int n = (int) rate + (RANDOM.nextFloat() < rate - (int) rate ? 1 : 0);
        double dx = wind.x / 20 * FLAKE_DRIFT, dz = wind.z / 20 * FLAKE_DRIFT;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            double x = eye.x + (RANDOM.nextDouble() * 2 - 1) * 12 - dx * 60;
            double z = eye.z + (RANDOM.nextDouble() * 2 - 1) * 12 - dz * 60;
            double y = eye.y + 1 + RANDOM.nextDouble() * 8;
            at.set(x, y, z);
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) > at.getY()) {
                continue;                                  // under a roof or in the ground: no ash falls there
            }
            level.addParticle(GZFallout.FALLOUT_ASH_PARTICLE.get(), x, y, z, dx, 0, dz);
        }
    }

    /**
     * A dead tint: {@code colour}, with {@link #BIOME_SHARE} of the biome's own in the world (so dead ground still varies
     * a little as live ground does). Break particles keep their texture's colour (the grass block's particle is plain
     * dirt).
     */
    static BlockTintSource dead(int colour, ToIntBiFunction<BlockAndTintGetter, BlockPos> biome) {
        return new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return colour;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return ARGB.srgbLerp(BIOME_SHARE, colour, 0xFF000000 | biome.applyAsInt(level, pos));
            }

            @Override
            public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return -1;
            }
        };
    }
}
