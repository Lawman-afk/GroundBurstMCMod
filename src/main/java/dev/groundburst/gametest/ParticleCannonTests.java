package dev.groundburst.gametest;

import java.util.UUID;

import dev.groundburst.blast.ParticleBeams;
import dev.groundburst.content.fallout.GBFallout;
import dev.groundburst.content.launch.Designators;
import dev.groundburst.content.launch.LaunchSiloBlockEntity.Readiness;
import dev.groundburst.content.launch.LaunchSite;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.superweapon.GBSuperweapons;
import dev.groundburst.content.superweapon.ParticleCannonBlockEntity;
import dev.groundburst.content.superweapon.SuperweaponBlockEntity;
import dev.groundburst.content.superweapon.SuperweaponBlockEntity.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Particle Uplink Cannon (DESIGN.md §19.6): it forms, takes no ammunition, charges on power and fires its beam,
 * which can be aimed anew while it burns and dies when the cannon's power is cut; the beam melts a trench into the
 * ground, vaporises what is in its core and sets fires round it; and it follows its designator's spot.
 */
final class ParticleCannonTests {
    private ParticleCannonTests() {}

    static void register() {
        GBGameTests.test("particle_uplink_cannon_forms_charges_and_fires", GBGameTests.FLOOR_16, 300, ParticleCannonTests::fires);
        GBGameTests.testOpenSky("particle_beam_melts_a_trench_and_burns_round_it", GBGameTests.FLOOR_16, 240, ParticleCannonTests::trench);
        GBGameTests.test("particle_beam_follows_its_designator", GBGameTests.FLOOR_16, 160, ParticleCannonTests::steered);
    }

    static ParticleCannonBlockEntity cannon(GameTestHelper helper) {
        BlockPos rel = new BlockPos(GBSuperweapons.PUC_CENTER_COL, 1, 0);
        SuperweaponTests.build(helper, rel, GBSuperweapons.PARTICLE_CANNON_CONTROLLER.block().get(), GBSuperweapons.PARTICLE_CANNON);
        var be = (ParticleCannonBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(be != null, "a Particle Uplink Cannon controller");
        be.revalidate();
        return be;
    }

    /**
     * Formed and powered it charges with nothing loaded (it takes no ammunition); charged it is READY; fired, its beam
     * goes up, it may be aimed anew while the beam burns, and with its power cut it stands down and the beam is gone.
     * (Its beam is a dry one: it leaves the ground it comes down on alone.)
     */
    private static void fires(GameTestHelper helper) {
        ParticleCannonBlockEntity c = cannon(helper);
        var level = helper.getLevel();
        helper.assertTrue(c.isFormed(), "formed: " + GBSuperweapons.PARTICLE_CANNON.check(level, c.getBlockPos(), Direction.NORTH));
        helper.assertTrue(c.siteKind() == LaunchSite.Kind.PARTICLE_CANNON, "a Particle Uplink Cannon");
        helper.assertTrue(!c.inventory().canPlaceItem(SuperweaponBlockEntity.SLOT_AMMO, new ItemStack(GBSuperweapons.TOXIN_SCUD.get())),
                "it takes no ammunition");
        helper.assertTrue(c.readiness() == Readiness.NO_POWER, "unpowered it cannot charge: " + c.readiness());
        c.energy().generate(400_000);
        helper.assertTrue(c.readiness() == Readiness.CHARGING && c.countdown() == ParticleCannonBlockEntity.CHARGE_TICKS, "charging: " + c.readiness());
        c.chargeForTest();
        helper.assertTrue(c.readiness() == Readiness.READY, "charged, with nothing loaded, it is ready: " + c.readiness());
        helper.assertTrue(c.timing().toImpact(5000, -5000) == SuperweaponBlockEntity.COUNTDOWN + 1 + ParticleBeams.UPLINK,
                "its beam comes down as long after LAUNCH wherever it is aimed");
        ParticleBeams.DRY.add(c.getBlockPos());
        BlockPos target = c.getBlockPos().offset(300, 0, -200);
        helper.assertTrue(c.setTarget(target.getX(), target.getZ()) && c.arm(true) && c.launchArmed(level, null), "aimed, armed and fired");
        helper.assertTrue(c.readiness() == Readiness.COUNTING, "counting down: " + c.readiness());
        c.skipCountdownForTest();
        int[] beam = {0};
        helper.runAfterDelay(6, () -> {
            beam[0] = c.beam();
            helper.assertTrue(c.phase() == Phase.FIRING && beam[0] != 0 && ParticleBeams.running(level, beam[0]), "its beam is up: " + c.phase());
            helper.assertTrue(c.raised(), "its column up");
            helper.assertTrue(c.setTarget(target.getX() + 20, target.getZ()), "it may be aimed anew while its beam burns");
            c.energy().consume(c.energy().stored());           // its power cut
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(c.phase() == Phase.STANDING_DOWN, "out of power, it stands down: " + c.phase());
            helper.assertTrue(!ParticleBeams.running(level, beam[0]), "and its beam is gone");
        });
        helper.runAfterDelay(10 + SuperweaponBlockEntity.LINGER + 70, () -> {
            helper.assertTrue(c.phase() == Phase.IDLE && !c.raised() && c.charge() < 100, "stood down, charging again from empty: " + c.phase());
            ParticleBeams.DRY.remove(c.getBlockPos());
            helper.succeed();
        });
    }

    /**
     * A beam parked on grass over stone for four seconds: a trench five deep under it, its bottom melted to slag (or
     * magma); a pool of water in its core boiled away; a pig in its core vaporised, one six blocks off set alight; the
     * grass round it scorched and napalm burning on it here and there.
     */
    private static void trench(GameTestHelper helper) {
        var level = helper.getLevel();
        for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 15, 6, 15)) {
            helper.setBlock(p, p.getY() == 6 ? Blocks.GRASS_BLOCK : p.getY() == 5 ? Blocks.DIRT : Blocks.STONE);
        }
        helper.setBlock(new BlockPos(10, 6, 8), Blocks.WATER);
        Pig heart = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(8, 7, 8));
        Pig near = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(14, 7, 8));
        boolean[] lit = {false};
        helper.onEachTick(() -> lit[0] |= near.isAlive() && near.getRemainingFireTicks() > 0);
        ParticleBeams.burnOverride = 80;
        int id;
        try {
            id = ParticleBeams.strike(level, helper.absolutePos(new BlockPos(8, 7, 8)), null, false);
        } finally {
            ParticleBeams.burnOverride = 0;                     // read as it starts: the next beam burns its own time
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(!ParticleBeams.running(level, id), "the beam has burned out");
            for (int y = 2; y <= 6; y++) {
                BlockState s = helper.getBlockState(new BlockPos(8, y, 8));
                helper.assertTrue(s.isAir(), "the trench eaten down to y 2: " + s + " at y " + y);
            }
            BlockState bottom = helper.getBlockState(new BlockPos(8, 1, 8));
            helper.assertTrue(bottom.is(GBFallout.SLAG.block().get()) || bottom.is(Blocks.MAGMA_BLOCK), "its bottom melted: " + bottom);
            helper.assertTrue(!helper.getBlockState(new BlockPos(10, 6, 8)).is(Blocks.WATER), "the pool in its core boiled away");
            helper.assertTrue(!heart.isAlive(), "the pig in its core vaporised");
            helper.assertTrue(lit[0], "the pig six blocks off caught fire");
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 6, 14)).is(GBFallout.SCORCHED_EARTH.block().get()), "the grass round it scorched");
            int fires = 0;
            for (BlockPos p : BlockPos.betweenClosed(0, 7, 0, 15, 7, 15)) {
                double d = Math.hypot(p.getX() - 8, p.getZ() - 8);
                if (d >= 4.5 && d <= 7.5 && helper.getBlockState(p).is(GBOrdnance.NAPALM_FIRE.get())) {
                    fires++;
                }
            }
            helper.assertTrue(fires >= 3, "fires round it: " + fires);
        });
    }

    /** A dry beam steered by its designator's spot: it moves toward it at three blocks a second, then holds there. */
    private static void steered(GameTestHelper helper) {
        var level = helper.getLevel();
        UUID who = UUID.randomUUID();
        BlockPos aim = helper.absolutePos(new BlockPos(3, 1, 8));
        Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 1, 8)));
        ParticleBeams.burnOverride = 140;
        int id;
        try {
            id = ParticleBeams.strike(level, aim, who, true);
        } finally {
            ParticleBeams.burnOverride = 0;
        }
        helper.onEachTick(() -> Designators.lase(level, who, 0, spot, false));
        double start = aim.getX() + 0.5;
        helper.runAfterDelay(40, () -> {
            Vec3 at = ParticleBeams.position(level, id);
            helper.assertTrue(at != null, "the beam still burns");
            double moved = at.x - start;
            helper.assertTrue(moved > 40 * ParticleBeams.SPEED - 1.2 && moved < 40 * ParticleBeams.SPEED + 0.5,
                    "two seconds in it has come about six blocks toward the spot: " + moved);
        });
        helper.runAfterDelay(100, () -> {
            Vec3 at = ParticleBeams.position(level, id);
            helper.assertTrue(at != null && Math.abs(at.x - spot.x) < 0.05 && Math.abs(at.z - spot.z) < 0.05, "it reached the spot and holds there: " + at);
            helper.succeed();
        });
    }
}
