package dev.groundzero.client.fx;

import java.util.ArrayList;
import java.util.List;

import dev.groundzero.content.effect.GasAgent;
import dev.groundzero.content.effect.GasPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * What the slice 6e systems look like (DESIGN.md §16.7, §21.4): the gas grid's blocks of gas as a drifting tinted haze
 * (puffs spawned through the blocks the server says hold gas, thicker where it is stronger), and EMP fields as a
 * shell of light racing out to the field's edge, then sparks crawling over everything inside until it ends.
 */
final class EffectFx {
    private record Emp(Vec3 centre, float radius, double start, double end) {}

    private static List<GasPayloads.Cell> gas = List.of();
    private static double gasX, gasZ;
    private static final List<Emp> EMPS = new ArrayList<>();
    private static final GasAgent[] AGENTS = GasAgent.values();

    private EffectFx() {}

    static void register() {
        GasPayloads.cellsHook = c -> {
            gas = c.cells();
            double x = 0, z = 0;
            for (GasPayloads.Cell cell : gas) {
                x += BlockPos.getX(cell.pos());
                z += BlockPos.getZ(cell.pos());
            }
            gasX = gas.isEmpty() ? 0 : x / gas.size() + 0.5;
            gasZ = gas.isEmpty() ? 0 : z / gas.size() + 0.5;
        };
        GasPayloads.empHook = e -> EMPS.add(new Emp(e.centre(), e.radius(), Spectacle.ticks, Spectacle.ticks + e.ticks()));
    }

    static void clear() {
        gas = List.of();
        EMPS.clear();
    }

    /** A client tick: a few more puffs of haze through the gas, and the EMP fields' light and sparks. */
    static void tick(Puffs puffs, float quality, Vec3 eye) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource random = level.getRandom();
        // Haze: about one puff a block alive at a time (each lives ~40 ticks), fewer and bigger when there is a lot of gas.
        List<GasPayloads.Cell> cells = gas;
        if (!cells.isEmpty()) {
            float budget = Math.min(cells.size() * 0.03f, 90 * quality);
            float scale = (float) Math.sqrt(Math.max(1, cells.size() * 0.03f / Math.max(1, budget)));
            int n = (int) budget + (random.nextFloat() < budget - (int) budget ? 1 : 0);
            for (int i = 0; i < n; i++) {
                GasPayloads.Cell cell = cells.get(random.nextInt(cells.size()));
                GasAgent agent = AGENTS[Math.floorMod(cell.agent(), AGENTS.length)];
                float strength = Math.min(1f, (cell.strength() & 0xFF) / 128f);
                Puffs.Puff p = new Puffs.Puff();
                p.kind = Puffs.Kind.SMOKE;
                p.x = BlockPos.getX(cell.pos()) + random.nextDouble();
                p.y = BlockPos.getY(cell.pos()) + random.nextDouble() * 0.8;
                p.z = BlockPos.getZ(cell.pos()) + random.nextDouble();
                p.vx = (random.nextFloat() - 0.5f) * 0.03f;
                p.vz = (random.nextFloat() - 0.5f) * 0.03f;
                p.drag = 0.05f;
                p.birth = Spectacle.ticks;
                p.life = 34 + random.nextInt(14);
                p.size0 = 1.1f * scale;
                p.size1 = 2.1f * scale;
                p.color0 = agent.colour;
                p.color1 = darken(agent.colour, 0.85f);
                p.alpha = 0.18f + 0.42f * strength;
                p.variant = random.nextInt(8);
                p.rot = random.nextFloat() * 6.283f;
                p.spin = (random.nextFloat() - 0.5f) * 0.01f;
                p.axisX = gasX;
                p.axisZ = gasZ;
                puffs.add(p, quality);
            }
        }
        // EMP: the shell races out over its first 10 ticks; then sparks crackle over surfaces in the field near you.
        double now = Spectacle.ticks;
        EMPS.removeIf(e -> now > e.end());
        for (Emp e : EMPS) {
            if (e.centre().distanceTo(eye) > e.radius() + 96) {
                continue;
            }
            double age = now - e.start();
            if (age < 10) {
                double r = e.radius() * (age + 1) / 10.0;
                int count = (int) (90 * quality);
                for (int i = 0; i < count; i++) {
                    double y = 1 - 2 * random.nextDouble(), a = random.nextDouble() * 2 * Math.PI, ring = Math.sqrt(1 - y * y);
                    if (y < -0.2) {
                        continue;                                   // the dome: little of it below ground
                    }
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, e.centre().x + Math.cos(a) * ring * r, e.centre().y + y * r,
                            e.centre().z + Math.sin(a) * ring * r, 0, 0, 0);
                }
            }
            int sparks = (int) (8 * quality);
            double reach = Math.min(e.radius(), 24);
            for (int i = 0; i < sparks; i++) {
                double x = eye.x + (random.nextDouble() * 2 - 1) * reach, z = eye.z + (random.nextDouble() * 2 - 1) * reach;
                BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, 0, z));
                if (Vec3.atCenterOf(top).distanceTo(e.centre()) <= e.radius()) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, top.getY() + 0.05, z, 0, 0.05, 0);
                }
            }
        }
    }

    private static int darken(int rgb, float k) {
        int r = (int) ((rgb >> 16 & 255) * k), g = (int) ((rgb >> 8 & 255) * k), b = (int) ((rgb & 255) * k);
        return r << 16 | g << 8 | b;
    }
}
