package dev.groundburst.gametest;

import java.util.List;

import dev.groundburst.content.artillery.ArtilleryShell;
import dev.groundburst.content.artillery.Ballistics;
import dev.groundburst.content.artillery.GBArtillery;
import dev.groundburst.content.artillery.GunSeat;
import dev.groundburst.content.artillery.Calibre;
import dev.groundburst.content.artillery.GunBlock;
import dev.groundburst.content.artillery.GunBlockEntity;
import dev.groundburst.content.artillery.MortarBlockEntity;
import dev.groundburst.content.artillery.ShellType;
import dev.groundburst.content.effect.EffectSystems;
import dev.groundburst.content.effect.EmpFields;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.Shrapnel;
import dev.groundburst.content.ordnance.ThrownExplosive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Artillery (DESIGN.md §20.1): shells fly true parabolas and the mortar's aim is solved exactly on them; its spread is
 * World of Tanks' (never outside its circle); manned, it is laid at its own pace, blooms as it turns and fires and
 * settles back, fires from its rack and refuses while loading or empty; each shell bursts with what it holds.
 */
final class ArtilleryTests {
    private ArtilleryTests() {}

    static void register() {
        GBGameTests.test("artillery_shells_fly_the_curve_the_aim_is_solved_on", GBGameTests.FLOOR_16, 20, ArtilleryTests::curve);
        GBGameTests.test("artillery_spread_stays_in_its_circle", GBGameTests.FLOOR_16, 20, ArtilleryTests::spread);
        GBGameTests.test("mortar_is_manned_laid_blooms_and_fires", GBGameTests.FLOOR_16, 260, ArtilleryTests::crewed);
        GBGameTests.testOpenSky("mortar_shell_flies_to_where_it_was_aimed", GBGameTests.FLOOR_16, 200, ArtilleryTests::flight);
        for (ShellType t : GBArtillery.MORTAR_SPEC.shells()) {
            GBGameTests.test("mortar_shell_bursts_" + t.key, GBGameTests.FLOOR_16, 100, helper -> burst(helper, Calibre.MORTAR, t));
        }
        GBGameTests.test("howitzer_shell_bursts_he", GBGameTests.FLOOR_16, 100, helper -> burst(helper, Calibre.HOWITZER, ShellType.HE));
        GBGameTests.test("howitzer_shell_bursts_incendiary", GBGameTests.FLOOR_16, 100, helper -> burst(helper, Calibre.HOWITZER, ShellType.INCENDIARY));
        GBGameTests.test("howitzer_lays_within_its_traverse", GBGameTests.FLOOR_16, 20, ArtilleryTests::traverse);
        GBGameTests.testOpenSky("howitzer_scatters_over_ground_not_loaded", GBGameTests.FLOOR_16, 20, ArtilleryTests::farScatter);
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(1, 1, 1, 15, 1, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /**
     * A stand-in crewman who can sit in a seat: as GameTestHelper's mock player (survival), with a connection to
     * nowhere (sitting down and getting up tell the client), and not ticked as a player (its seat ticks it).
     */
    private static ServerPlayer seatable(GameTestHelper helper) {
        var level = helper.getLevel();
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-crewman");
        ServerPlayer p = new ServerPlayer(level.getServer(), level, profile, net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }

            @Override
            public boolean isClientAuthoritative() {
                return false;
            }

            @Override
            public void tick() {
            }
        };
        GameType.SURVIVAL.updatePlayerAbilities(p.getAbilities());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), connection, p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
        return p;
    }

    private static MortarBlockEntity mortar(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, GBArtillery.MORTAR.block().get().defaultBlockState().setValue(GunBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(rel, MortarBlockEntity.class);
        helper.assertTrue(be != null, "a mortar");
        return be;
    }

    private static GunBlockEntity gun(GameTestHelper helper, BlockPos rel, net.minecraft.world.level.block.Block block) {
        helper.setBlock(rel, block.defaultBlockState().setValue(GunBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(rel, GunBlockEntity.class);
        helper.assertTrue(be != null, "a gun");
        return be;
    }

    /**
     * The howitzer, facing north, reaches 30 degrees either side: a point 400 blocks off 20 degrees round is in reach
     * (on the high arc, 45 to 70 degrees, with the lowest charge that reaches), one 45 degrees round is not, nor one
     * nearer than 64 or further than 600.
     */
    private static void traverse(GameTestHelper helper) {
        GunBlockEntity h = gun(helper, new BlockPos(8, 2, 8), GBArtillery.HOWITZER.block().get());
        Vec3 base = h.socket();
        java.util.function.BiFunction<Double, Double, Vec3> at = (range, turn) -> {
            double b = Math.toRadians(h.frontYaw() + turn);
            return base.add(Math.sin(b) * range, 0, Math.cos(b) * range);
        };
        GunBlockEntity.Solution s = h.solve(at.apply(400.0, 20.0));
        helper.assertTrue(s.reachable() && s.elevation() >= 45 - 1e-3 && s.elevation() <= 70 + 1e-3, "400 blocks, 20 degrees round: " + s);
        for (int k = 0; k < s.charge(); k++) {
            double c = GBArtillery.HOWITZER_SPEC.charges()[k];
            helper.assertTrue(c * c / Ballistics.G < 400 * 1.05, "a lower charge would have reached: " + k);
        }
        helper.assertTrue(!h.solve(at.apply(400.0, 45.0)).reachable(), "45 degrees round is beyond its traverse");
        helper.assertTrue(!h.solve(at.apply(40.0, 0.0)).reachable(), "too near");
        helper.assertTrue(!h.solve(at.apply(620.0, 0.0)).reachable(), "too far");
        helper.assertTrue(h.solve(at.apply(590.0, 0.0)).reachable(), "590 blocks is in reach");
        helper.succeed();
    }

    /**
     * Fired 590 blocks over ground the server has not loaded (as its longest shots are), the howitzer's shells still spread
     * over its circle: nearly all land off the aim, none outside the circle (stretched along the line of fire). Once they
     * went out unscattered, the trace of where they would land giving up where the ground was not loaded. (Under open sky:
     * its steep shot would meet a test's barrier roof a few blocks up, and be spread round that.)
     */
    private static void farScatter(GameTestHelper helper) {
        GunBlockEntity a = gun(helper, new BlockPos(8, 2, 8), GBArtillery.HOWITZER.block().get());
        var level = helper.getLevel();
        Vec3 target = a.socket().add(0, 0, -590);
        int off = 0;
        for (int i = 0; i < 16; i++) {
            a.layForTest(target);                                // fully aimed again before each shot
            Vec3 muzzle = a.muzzle(a.yaw(), a.elevation());
            Vec3 u = Ballistics.velocity(GBArtillery.HOWITZER_SPEC.charges()[a.charge()], a.yaw(), a.elevation());
            Ballistics.Fall laid = Ballistics.flightTo(muzzle, u, target);
            double r = a.radius(Math.hypot(target.x - muzzle.x, target.z - muzzle.z));
            a.shootForTest(level, ShellType.HE);
            List<ArtilleryShell> shells = level.getEntitiesOfClass(ArtilleryShell.class, new AABB(muzzle, muzzle).inflate(4));
            helper.assertTrue(shells.size() == 1, "one shell away: " + shells.size());
            ArtilleryShell shell = shells.get(0);
            Vec3 v = shell.getDeltaMovement();
            shell.discard();                                     // (it would fly 590 blocks, keeping the ground loaded)
            // Where it comes down at the target's height, on its own curve.
            double dy = muzzle.y - target.y, t = (v.y + Math.sqrt(v.y * v.y + 2 * Ballistics.G * dy)) / Ballistics.G;
            Vec3 land = new Vec3(muzzle.x + v.x * t, target.y, muzzle.z + v.z * t);
            double across = Math.abs(land.x - target.x), along = Math.abs(land.z - target.z);
            double stretch = laid.stretch();
            helper.assertTrue(Math.pow(across / r, 2) + Math.pow(along / (r * stretch), 2) <= 1.05,
                    "inside its circle (" + r + " across, " + r * stretch + " along): " + across + ", " + along);
            off += Math.hypot(across, along) > 0.25 ? 1 : 0;
        }
        helper.assertTrue(off >= 13, "they spread: " + off + " of 16 off the aim");
        helper.succeed();
    }

    /**
     * Stepped tick by tick as a shell is (its velocity first, from the curve's plus half a tick of gravity), it is on
     * the curve every tick; the mortar's solution for a point brings the curve through it (flat, raised and lowered,
     * near and far), with the lowest charge that reaches, inside its elevations; too near and too far are out of reach.
     */
    private static void curve(GameTestHelper helper) {
        for (double[] c : new double[][] {{1.58, 30, 70}, {3.16, -120, 45}, {2.24, 200, 84}}) {
            Vec3 from = new Vec3(1, 64, 1), u = Ballistics.velocity(c[0], c[1], c[2]);
            Vec3 v = u.add(0, Ballistics.G / 2, 0), p = from;
            for (int n = 1; n <= 120; n++) {
                v = v.add(0, -Ballistics.G, 0);
                p = p.add(v);
                helper.assertTrue(p.distanceTo(Ballistics.point(from, u, n)) < 1e-9, "on the curve at tick " + n + ": " + p);
            }
        }
        MortarBlockEntity m = mortar(helper, new BlockPos(8, 2, 8));
        Vec3 base = m.socket();
        // {range, height, the charge expected (the lowest that reaches: 50, 100, 150 and 200 at 45 degrees), or -1}
        for (double[] t : new double[][] {{20, 0, 0}, {40, 0, 0}, {90, 0, 1}, {150, 6, 3}, {199, 0, 3}, {120, 25, -1}, {70, -18, -1}}) {
            Vec3 target = base.add(-t[0] * 0.6, t[1], -t[0] * 0.8);
            MortarBlockEntity.Solution s = m.solve(target);
            helper.assertTrue(s.reachable(), "in reach at " + t[0] + " (" + t[1] + " up): " + s);
            helper.assertTrue(s.elevation() >= MortarBlockEntity.MIN_ELEVATION - 1e-3 && s.elevation() <= MortarBlockEntity.MAX_ELEVATION + 1e-3,
                    "laid within its elevations: " + s);
            helper.assertTrue(t[2] < 0 || s.charge() == (int) t[2], "the lowest charge that reaches " + t[0] + ": " + s);
            Vec3 muzzle = m.muzzle(s.yaw(), s.elevation());
            Vec3 u = Ballistics.velocity(MortarBlockEntity.CHARGES[s.charge()], s.yaw(), s.elevation());
            double h = Math.hypot(target.x - muzzle.x, target.z - muzzle.z);
            double time = h / Math.hypot(u.x, u.z);
            Vec3 at = Ballistics.point(muzzle, u, time);
            helper.assertTrue(at.distanceTo(target) < 0.05, "the curve passes through " + target + ": " + at);
        }
        helper.assertTrue(!m.solve(base.add(0, 0, -10)).reachable(), "too near");
        helper.assertTrue(!m.solve(base.add(0, 0, -210)).reachable(), "too far");
        helper.succeed();
    }

    /**
     * Thousands of shots spread over a circle of radius 3, stretched half again along the line of fire: none outside
     * it, about two in five inside half of it (a normal spread, sigma half the radius, its stragglers rolled again
     * inside), centred.
     */
    private static void spread(GameTestHelper helper) {
        RandomSource r = RandomSource.create(1234);
        double radius = 3, stretch = 1.5, yaw = 30;
        double y = Math.toRadians(yaw), fx = Math.sin(y), fz = Math.cos(y);
        int n = 6000, inner = 0;
        double sx = 0, sz = 0;
        for (int i = 0; i < n; i++) {
            Vec3 o = Ballistics.scatter(r, radius, stretch, yaw);
            double across = o.x * fz - o.z * fx, along = (o.x * fx + o.z * fz) / stretch;
            double d = Math.hypot(across, along) / radius;
            helper.assertTrue(d <= 1 + 1e-9, "a shot outside its circle: " + d);
            if (d <= 0.5) {
                inner++;
            }
            sx += o.x;
            sz += o.z;
        }
        double share = inner / (double) n;
        helper.assertTrue(share > 0.37 && share < 0.48, "about two in five inside half the radius: " + share);
        helper.assertTrue(Math.abs(sx / n) < 0.15 && Math.abs(sz / n) < 0.15, "centred: " + sx / n + ", " + sz / n);
        helper.succeed();
    }

    /**
     * Manned (its crewman sits in its seat), aimed 60 blocks off: laid there at its pace (charge 2, elevation from 60
     * to 71.6 at a degree a tick) blooming while it turns, then settling to its least; fired, it blooms fourfold,
     * reloads, and fires nothing more until loaded; emptied, it will not fire; the crewman gets up and the seat goes.
     */
    private static void crewed(GameTestHelper helper) {
        floor(helper);
        MortarBlockEntity m = mortar(helper, new BlockPos(8, 2, 8));
        var level = helper.getLevel();
        ServerPlayer crew = seatable(helper);
        crew.setPos(helper.absoluteVec(new Vec3(8.5, 2, 9.5)));
        helper.assertTrue(m.man(crew), "manned");
        helper.assertTrue(crew.getVehicle() instanceof GunSeat, "its crewman sits in its seat");
        helper.assertTrue(m.manned(), "it knows it is manned");
        m.load(new ItemStack(GBArtillery.MORTAR_SHELLS.get(ShellType.HE).get(), 1));
        Vec3 target = m.socket().add(0, 0, -60);
        m.aim(crew, target);
        GunBlockEntity.Solution s = m.solve(target);
        helper.assertTrue(s.reachable() && s.charge() == 1, "60 blocks: the second charge: " + s);
        Vec3 base = m.socket();
        double[] last = {m.laid() == null ? Double.NaN : Math.hypot(m.laid().x - base.x, m.laid().z - base.z)};
        Vec3[] prev = {m.laid()};
        helper.onEachTick(() -> {
            Vec3 l = m.laid();
            if (l != null && prev[0] != null) {
                double step = Math.hypot(l.x - prev[0].x, l.z - prev[0].z);
                double most = GBArtillery.MORTAR_SPEC.rangeRate() + Math.toRadians(GBArtillery.MORTAR_SPEC.traverseRate())
                        * Math.hypot(l.x - base.x, l.z - base.z) + 0.01;
                helper.assertTrue(step <= most, "it slides, never jumps: " + step + " in a tick");
            }
            prev[0] = l;
        });
        helper.assertTrue(last[0] >= MortarBlockEntity.MIN_RANGE - 1e-6 && last[0] <= MortarBlockEntity.MAX_RANGE + 1e-6,
                "manned, it is laid within its ranges: " + last[0]);
        helper.runAfterDelay(4, () -> {
            Vec3 l = m.laid();
            double r = Math.hypot(l.x - base.x, l.z - base.z);
            helper.assertTrue(Math.abs(r - 60) < Math.abs(last[0] - 60) - 6, "laid toward the aim at its pace: " + r + " from " + last[0]);
            helper.assertTrue(m.dispersion() > 2.5f, "it blooms while it is laid: " + m.dispersion());
        });
        int[] shots = {0};
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(m.laid().distanceTo(target) < 0.01 && Math.abs(m.elevation() - s.elevation()) < 0.01f,
                    "laid on the aim: " + m.laid() + ", " + m.elevation() + " for " + s.elevation());
            helper.assertTrue(m.dispersion() < 1.01f, "settled to its least: " + m.dispersion());
            shots[0] = m.shots();
            m.fire(crew);
            helper.assertTrue(m.shots() == shots[0] + 1, "it fired");
            helper.assertTrue(m.reload() == MortarBlockEntity.RELOAD, "it is loading");
            helper.assertTrue(Math.abs(m.dispersion() - MortarBlockEntity.BLOOM) < 0.05, "it blooms fourfold: " + m.dispersion());
            List<ArtilleryShell> away = level.getEntitiesOfClass(ArtilleryShell.class, new AABB(helper.absoluteVec(new Vec3(8.5, 3, 8.5)), helper.absoluteVec(new Vec3(8.5, 3, 8.5))).inflate(6));
            helper.assertTrue(away.size() == 1 && away.get(0).type() == ShellType.HE, "an HE shell away: " + away.size());
            away.forEach(e -> e.discard());                   // (it would land 60 blocks off, among other tests)
            m.fire(crew);
            helper.assertTrue(m.shots() == shots[0] + 1, "not while it loads");
        });
        helper.runAfterDelay(110 + MortarBlockEntity.RELOAD + 2, () -> {
            m.fire(crew);
            helper.assertTrue(m.shots() == shots[0] + 1, "nothing left to fire");
            crew.stopRiding();
        });
        helper.runAfterDelay(110 + MortarBlockEntity.RELOAD + 12, () -> {
            helper.assertTrue(!m.manned(), "its crewman has got up");
            helper.assertTrue(level.getEntitiesOfClass(GunSeat.class, new AABB(helper.absolutePos(new BlockPos(8, 2, 8))).inflate(3)).isEmpty(),
                    "and the seat is gone");
            helper.succeed();
        });
    }

    /**
     * A shell launched on the mortar's solution for a point 12 blocks off (a slow charge, so it stays in the plot)
     * comes down within half a block of it, and bursts there.
     */
    private static void flight(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        Vec3 from = helper.absoluteVec(new Vec3(2.5, 2.2, 2.5)), target = helper.absoluteVec(new Vec3(10.5, 2.0, 11.5));
        double speed = 1.0;
        double e = Ballistics.highAngle(speed, Math.hypot(target.x - from.x, target.z - from.z), target.y - from.y);
        helper.assertTrue(!Double.isNaN(e) && e > 45, "a high arc there: " + e);
        Vec3 u = Ballistics.velocity(speed, Ballistics.yaw(from, target), e);
        ArtilleryShell shell = ArtilleryShell.launch(level, from, u, Calibre.MORTAR, ShellType.SMOKE, null);
        Vec3[] last = {from};
        helper.onEachTick(() -> {
            if (!shell.isRemoved()) {
                last[0] = shell.position();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(shell.isRemoved(), "still flying");
            helper.assertTrue(Math.hypot(last[0].x - target.x, last[0].z - target.z) < 0.6, "came down where it was aimed: " + last[0] + " for " + target);
            List<ThrownExplosive> canisters = level.getEntitiesOfClass(ThrownExplosive.class, new AABB(target, target).inflate(8));
            helper.assertTrue(canisters.size() == ArtilleryShell.SMOKE_CANISTERS, "and burst there: " + canisters.size());
            canisters.forEach(c -> c.discard());
        });
    }

    /** Each shell, dropped onto a stone floor, bursts with what it holds. */
    private static void burst(GameTestHelper helper, Calibre calibre, ShellType type) {
        floor(helper);
        var level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(8.5, 2.0, 8.5));
        Pig pig = type == ShellType.CRYO ? helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(10, 2, 8)) : null;
        ArtilleryShell.launch(level, at.add(0, 3, 0), new Vec3(0, -0.6, 0), calibre, type, null);
        helper.succeedWhen(() -> {
            AABB around = new AABB(at, at).inflate(9);
            switch (type) {
                case HE -> helper.assertTrue(!level.getEntitiesOfClass(Shrapnel.class, around.inflate(30)).isEmpty()
                        || level.getBlockState(helper.absolutePos(new BlockPos(8, 1, 8))).isAir(), "a blast and fragments");
                case INCENDIARY -> {
                    int fires = 0;
                    for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(2, 1, 2)), helper.absolutePos(new BlockPos(14, 4, 14)))) {
                        if (level.getBlockState(p).is(GBOrdnance.NAPALM_FIRE.get())) {
                            fires++;
                        }
                    }
                    helper.assertTrue(fires >= (calibre == Calibre.MORTAR ? 12 : 20), "napalm over the ground round it: " + fires);
                }
                case SMOKE, CHEMICAL -> {
                    List<ThrownExplosive> c = level.getEntitiesOfClass(ThrownExplosive.class, around);
                    int want = type == ShellType.SMOKE ? ArtilleryShell.SMOKE_CANISTERS : ArtilleryShell.GAS_CANISTERS;
                    helper.assertTrue(c.size() == want && c.stream().allMatch(ThrownExplosive::smoking), "its canisters pouring out: " + c.size());
                    c.forEach(e -> e.discard());
                }
                case CRYO -> helper.assertTrue(pig != null && pig.hasEffect(EffectSystems.FROZEN), "the pig two blocks off frozen");
                case EMP -> helper.assertTrue(EmpFields.inField(level, at.add(0, 0.5, 0)), "an EMP field where it landed");
            }
        });
    }
}
