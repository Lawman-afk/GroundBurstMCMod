package dev.groundburst.client.fx;

import dev.groundburst.content.fallout.FarAftermath;
import dev.groundburst.content.fallout.FarWave;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * The far wave seen coming (DESIGN.md §21.10): a nuclear blast's shock carried on past its reach at the speed of sound.
 * Where the wave is near the camera, dust is thrown up along it over the land, a wall of it rolling over the ground
 * toward the player, in the colour of the ground it rises from; as it passes the camera, a jolt (the camera shake). Only
 * the stretch of the wave near the camera is drawn, and only over loaded ground.
 */
final class FarWaveFx {
    private static final Map<Integer, FarWave> WAVES = new HashMap<>();
    /** How far along the wave from the camera's bearing dust is thrown up, blocks, and how near the wave must be. */
    static final double SIDE = 320, NEAR = 640;
    /** Blocks between dust bursts along the wave. */
    static final float SPACING = 5;
    /** The most bursts a tick, the jolt as it passes (at its start, less to its end). */
    static final int MOST = 150;
    static final float JOLT = 0.45f;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<Integer, Double> PASSED = new HashMap<>();

    private FarWaveFx() {}

    static void register() {
        FarWave.hook = w -> WAVES.put(w.id(), w);
    }

    static void clear() {
        WAVES.clear();
        PASSED.clear();
    }

    static boolean isEmpty() {
        return WAVES.isEmpty();
    }

    /** Once a client tick: dust thrown up along each wave near the camera, and the jolt of one passing it. */
    /**
     * The dust a column throws up: earth where things grow (grass, leaves and plants are torn up, as the blast's own dust
     * has it), spray over water, the ground's own colour otherwise; greyed a little with the dust of the air.
     */
    static int dust(ClientLevel level, BlockPos at) {
        if (!level.getBlockState(at).getFluidState().isEmpty()) {
            return 0xC9CED2;
        }
        int colour = BlastFx.earthColor(level, at);
        return Puffs.lerpRgb(colour == 0 ? 0x9A8A70 : colour, 0x9A8A70, 0.35f);
    }

    static void tick(ClientLevel level, Puffs puffs, float quality, Vec3 camera) {
        if (WAVES.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (var it = WAVES.values().iterator(); it.hasNext(); ) {
            FarWave w = it.next();
            if (now < w.startTick()) {
                continue;
            }
            double r = w.from() + (now - w.startTick()) * FarAftermath.SPEED;
            if (r > w.to() + 40) {
                PASSED.remove(w.id());
                it.remove();
                continue;
            }
            Vec3 c = w.centre();
            double dx = camera.x - c.x, dz = camera.z - c.z;
            double dc = Math.sqrt(dx * dx + dz * dz);
            // The jolt as it goes by.
            double was = PASSED.getOrDefault(w.id(), r - FarAftermath.SPEED);
            if (was < dc && r >= dc && dc <= w.to()) {
                float t = (float) ((dc - w.from()) / Math.max(1, w.to() - w.from()));
                Spectacle.addTrauma(JOLT * (1 - 0.75f * t));
            }
            PASSED.put(w.id(), r);
            if (Math.abs(r - dc) > NEAR || r > w.to() || now % 2 != 0) {
                continue;
            }
            double bearing = Math.atan2(dz, dx);
            double span = Math.min(Math.PI, SIDE / Math.max(r, 1));
            int n = Math.min(MOST, (int) Math.ceil(2 * span * r / SPACING));
            float fade = (float) (1 - 0.6 * (r - w.from()) / Math.max(1, w.to() - w.from()));
            for (int i = 0; i <= n; i++) {
                double a = bearing - span + 2 * span * i / Math.max(1, n) + (RANDOM.nextDouble() - 0.5) * span / Math.max(1, n);
                double px = c.x + Math.cos(a) * r, pz = c.z + Math.sin(a) * r;
                BlockPos column = BlockPos.containing(px, level.getMinBuildHeight(), pz);
                if (!level.hasChunkAt(column)) {
                    continue;
                }
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
                BlockPos under = new BlockPos(column.getX(), ground - 1, column.getZ());
                int colour = dust(level, under);
                Puffs.Puff p = new Puffs.Puff();
                p.kind = Puffs.Kind.DUST;
                p.x = px;
                p.y = ground + 0.5 + RANDOM.nextDouble() * 2;
                p.z = pz;
                // Thrown out along the wave and up, then hanging and spreading behind it.
                float out = 0.25f + 0.35f * RANDOM.nextFloat();
                p.vx = (float) Math.cos(a) * out;
                p.vz = (float) Math.sin(a) * out;
                p.vy = 0.18f + 0.2f * RANDOM.nextFloat();      // a wall of it, thrown up high
                p.drag = 0.06f;
                p.rise = 0.01f;
                p.birth = Spectacle.now();
                p.life = 90 + RANDOM.nextInt(60);
                p.size0 = 4 + RANDOM.nextFloat() * 3;
                p.size1 = 18 + RANDOM.nextFloat() * 10;
                p.color0 = colour;
                p.color1 = Puffs.lerpRgb(colour, 0x8C8578, 0.5f);
                p.alpha = 0.85f * fade;
                p.variant = RANDOM.nextInt(Puffs.Kind.DUST.count);
                p.rot = RANDOM.nextFloat() * 6.283f;
                p.spin = (RANDOM.nextFloat() - 0.5f) * 0.02f;
                puffs.add(p, quality);
            }
        }
    }
}
