package dev.groundzero.gametest;

import dev.groundzero.content.assembly.Fuze;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.assembly.MissileAssembly;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.content.assembly.WarheadAssembly;
import dev.groundzero.content.launch.Flights;
import dev.groundzero.content.launch.GZLaunch;
import dev.groundzero.content.launch.LaunchComplexBlockEntity;
import dev.groundzero.content.launch.LaunchSiloBlockEntity;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.nuclear.Contamination;
import dev.groundzero.content.nuclear.GZEffects;
import dev.groundzero.content.nuclear.NeutronBurst;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.charge.ChargeKind;
import dev.groundzero.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;

/** Phase 8 (DESIGN.md §15, §17.2): the Launch Complex, and nuclear (and dirty) warheads going off or fizzling. */
final class NuclearTests {
    private NuclearTests() {}

    static void register() {
        GZGameTests.test("nuclear_missile_needs_a_launch_complex", GZGameTests.FLOOR_16, 20, NuclearTests::needsComplex);
        GZGameTests.test("launch_complex_counts_down_and_fires", GZGameTests.FLOOR_16, 40, NuclearTests::complexFires);
        GZGameTests.test("nuclear_warhead_goes_off_from_a_missile", GZGameTests.FLOOR_16, 200, NuclearTests::fullYield);
        GZGameTests.test("placed_nuclear_warhead_fizzles", GZGameTests.FLOOR_16, 100, NuclearTests::fizzles);
        GZGameTests.test("strategic_body_carries_nuclear_warheads_only", GZGameTests.FLOOR_16, 20, NuclearTests::strategic);
        GZGameTests.test("boosted_fission_warhead_goes_off_from_a_missile", GZGameTests.FLOOR_16, 200, NuclearTests::boosted);
        // Open sky: the withering works down from the highest block, so a lid over the plot would shelter it like a roof.
        GZGameTests.testOpenSky("neutron_warhead_kills_through_all_but_thick_walls", GZGameTests.FLOOR_16, 100, NuclearTests::neutron);
        GZGameTests.test("dirty_warhead_goes_off_however_set_off", GZGameTests.FLOOR_16, 100, NuclearTests::dirty);
        GZGameTests.test("dirty_warhead_flies_from_any_silo", GZGameTests.FLOOR_16, 20, NuclearTests::dirtyMissile);
        GZGameTests.test("contamination_zones_reach_clients_intact", GZGameTests.FLOOR_16, 20, NuclearTests::zonesPayload);
    }

    private static void floor(GameTestHelper helper) {
        Charges.bombScale = 0.3f;
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    static ItemStack nuke() {
        return MissileAssembly.missile(new MissileSpec(new ItemStack(GZOrdnance.TACTICAL_NUCLEAR_WARHEAD.item().get()), SizeClass.XL,
                MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS, false));
    }

    private static LaunchComplexBlockEntity complex(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, GZLaunch.COMPLEX_CONTROLLER.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        for (MultiblockDefinition.Cell cell : GZLaunch.COMPLEX.cells(helper.absolutePos(rel), Direction.NORTH)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                helper.getLevel().setBlockAndUpdate(cell.pos(), GZLaunch.COMPLEX.legend().get(cell.key()).preview().get());
            }
        }
        LaunchComplexBlockEntity c = (LaunchComplexBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(c != null, "a complex controller");
        c.revalidate();
        return c;
    }

    /** The basic silo will not take a nuclear missile; the Launch Complex will, and a conventional one too. */
    private static void needsComplex(GameTestHelper helper) {
        LaunchSiloBlockEntity silo = LaunchTests.formedSilo(helper);
        helper.assertTrue(!silo.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, nuke()), "a silo refuses a nuclear missile");
        helper.assertTrue(silo.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile()), "but takes an HE one");
        LaunchComplexBlockEntity c = complex(helper, new BlockPos(9, 1, 9));
        helper.assertTrue(c.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, nuke()), "a complex takes a nuclear missile");
        helper.assertTrue(c.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile()), "and an HE one");
        helper.succeed();
    }

    /** Formed, loaded and powered, the complex counts down 15 s and lights the missile, using 250k FE. */
    private static void complexFires(GameTestHelper helper) {
        floor(helper);
        LaunchComplexBlockEntity c = complex(helper, new BlockPos(5, 1, 2));
        helper.assertTrue(c.isFormed(), "formed: " + GZLaunch.COMPLEX.check(helper.getLevel(), c.getBlockPos(), Direction.NORTH));
        c.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, nuke());
        helper.assertTrue(c.readiness() == LaunchSiloBlockEntity.Readiness.NO_POWER, "wants power: " + c.readiness());
        c.energy().generate(LaunchComplexBlockEntity.COMPLEX_COST);
        helper.assertTrue(c.readiness() == LaunchSiloBlockEntity.Readiness.READY, "ready: " + c.readiness());
        BlockPos target = c.getBlockPos().offset(300, 0, 0);
        helper.assertTrue(c.launch(helper.getLevel(), target, null), "starts its countdown");
        helper.assertTrue(c.countdown() == LaunchComplexBlockEntity.COMPLEX_COUNTDOWN, "15 s: " + c.countdown());
        var before = Flights.of(helper.getLevel()).all();
        c.skipCountdownForTest();
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(c.inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty(), "the missile is gone");
            helper.assertTrue(c.energy().stored() == 0, "250k FE used: " + c.energy().stored());
            // Other tests launch missiles too: ours is the new one bound for our target (GPS: within 2 blocks of it).
            Vec3 aim = Vec3.atBottomCenterOf(target);
            var ours = Flights.of(helper.getLevel()).all().stream()
                    .filter(f -> !before.contains(f) && f.target().distanceTo(aim) < 4).toList();
            helper.assertTrue(ours.size() == 1, "a missile in the air: " + ours.size());
            Flights.of(helper.getLevel()).cancelForTest(helper.getLevel(), ours.getFirst());   // no nuke landing on another test
            helper.succeed();
        });
    }

    /** Delivered by a missile, a tactical nuke blasts a crater and leaves contamination at full strength. */
    private static void fullYield(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 1, 8);
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(at));
        // A tenth of its size, so its crater (42 blocks) stays in this test's plot: the scale is read as it goes off.
        float scale = Charges.bombScale;
        Charges.bombScale = 0.1f;
        Charges.impact(helper.getLevel(), ChargeKind.TACTICAL_NUCLEAR_WARHEAD, helper.absolutePos(at), null);
        Charges.bombScale = scale;
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(8, 0, 8))).isAir(), "a crater where it hit");
            float level = Contamination.of(helper.getLevel()).levelAt(helper.getLevel(), centre);
            helper.assertTrue(level > 0.9f, "full contamination at ground zero: " + level);
            Contamination.of(helper.getLevel()).removeNear(centre, 2);
        });
    }

    /**
     * A nuclear warhead needs a Strategic body, a Strategic body takes nothing else and only the Liquid-fuel Stage moves
     * it; the missile is a Strategic missile (its own model) that only a Launch Complex fires.
     */
    private static void strategic(GameTestHelper helper) {
        ItemStack nuke = new ItemStack(GZOrdnance.TACTICAL_NUCLEAR_WARHEAD.item().get());
        ItemStack fission = new ItemStack(GZOrdnance.FISSION_WARHEAD.item().get());
        ItemStack he = new ItemStack(GZOrdnance.HE_WARHEAD.item().get());
        ItemStack heavy = new ItemStack(dev.groundzero.content.assembly.GZAssembly.HEAVY_BODY.get());
        ItemStack body = new ItemStack(dev.groundzero.content.assembly.GZAssembly.STRATEGIC_BODY.get());
        ItemStack liquid = new ItemStack(dev.groundzero.content.assembly.GZAssembly.LIQUID_FUEL_STAGE.get());
        ItemStack srm = new ItemStack(dev.groundzero.content.assembly.GZAssembly.SRM_II.get());
        helper.assertTrue(MissileAssembly.check(nuke, heavy, liquid, ItemStack.EMPTY) == MissileAssembly.Problem.NUCLEAR_NEEDS_STRATEGIC,
                "a nuclear warhead needs a Strategic body");
        helper.assertTrue(MissileAssembly.check(he, body, liquid, ItemStack.EMPTY) == MissileAssembly.Problem.STRATEGIC_NUCLEAR_ONLY,
                "a Strategic body takes nuclear warheads only");
        helper.assertTrue(MissileAssembly.check(nuke, body, srm, ItemStack.EMPTY) == MissileAssembly.Problem.MOTOR_DOES_NOT_FIT,
                "a Solid Rocket Motor II does not move a Strategic body");
        helper.assertTrue(MissileAssembly.check(fission, body, liquid, ItemStack.EMPTY) == MissileAssembly.Problem.NONE,
                "a fission warhead fits a Strategic body");
        ItemStack missile = MissileAssembly.assemble(nuke, (MissileParts.Body) body.getItem(), (MissileParts.MotorItem) liquid.getItem(),
                ItemStack.EMPTY, ItemStack.EMPTY);
        MissileSpec spec = dev.groundzero.content.assembly.MissileItem.spec(missile);
        helper.assertTrue(spec != null && spec.body() == SizeClass.XL && spec.range() == 2500 && spec.needsComplex(), "a Strategic missile: " + spec);
        var look = missile.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        helper.assertTrue(look != null && look.strings().getFirst().equals("strategic_tactical_nuclear_warhead"), "its own model: " + look);
        helper.succeed();
    }

    /** Our contamination zone (centred where the warhead went off). */
    private static Contamination.Zone zoneAt(GameTestHelper helper, Vec3 centre) {
        var zone = Contamination.of(helper.getLevel()).zones().stream().filter(z -> z.center().distanceTo(centre) < 1).findFirst();
        helper.assertTrue(zone.isPresent(), "contamination where it went off");
        return zone.get();
    }

    /** A boosted fission warhead delivered by a missile: a crater, and full contamination reaching past a fission warhead's. */
    private static void boosted(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 1, 8);
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(at));
        // A twenty-fifth of its size, so its crater (95 blocks) stays in this test's plot.
        float scale = Charges.bombScale;
        Charges.bombScale = 0.04f;
        Charges.impact(helper.getLevel(), ChargeKind.BOOSTED_FISSION_WARHEAD, helper.absolutePos(at), null);
        Charges.bombScale = scale;
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(8, 0, 8))).isAir(), "a crater where it hit");
            float level = Contamination.of(helper.getLevel()).levelAt(helper.getLevel(), centre);
            helper.assertTrue(level > 0.9f, "full contamination at ground zero: " + level);
            Contamination.Zone zone = zoneAt(helper, centre);
            helper.assertTrue(Math.abs(zone.radius() - 220 * 0.04f) < 0.01f && zone.duration() == 48_000, "220 blocks for two days: " + zone);
            Contamination.of(helper.getLevel()).removeNear(centre, 2);
        });
    }

    /**
     * A neutron warhead (at a twenty-fifth of its size): its neutrons kill a pig in the open, but a pig as far away behind
     * two blocks of obsidian lives; grass withers to bare earth and leaves fall, but a log stands.
     */
    private static void neutron(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 1, 8);
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(at));
        for (BlockPos p : BlockPos.betweenClosed(7, 1, 10, 9, 2, 11)) {
            helper.setBlock(p, Blocks.OBSIDIAN);
        }
        helper.setBlock(new BlockPos(6, 0, 8), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(6, 1, 8), Blocks.SHORT_GRASS);
        helper.setBlock(new BlockPos(10, 1, 9), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(10, 2, 9), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        Pig open = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(8, 1, 4));
        Pig sheltered = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(8, 1, 12));
        double through = NeutronBurst.shielding(helper.getLevel(), centre, sheltered.getBoundingBox().getCenter());
        helper.assertTrue(through < 0.15, "two blocks of obsidian stop most of it: " + through);
        float scale = Charges.bombScale;
        Charges.bombScale = 0.04f;
        Charges.impact(helper.getLevel(), ChargeKind.NEUTRON_WARHEAD, helper.absolutePos(at), null);
        Charges.bombScale = scale;
        helper.succeedWhen(() -> {
            helper.assertTrue(!open.isAlive(), "the pig in the open is dead");
            helper.assertTrue(sheltered.isAlive(), "the pig behind obsidian lives: " + sheltered.getHealth());
            helper.assertTrue(sheltered.hasEffect(GZEffects.RADIATION_SICKNESS), "but it is radiation-sick");
            helper.assertBlockPresent(Blocks.COARSE_DIRT, new BlockPos(6, 0, 8));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, 1, 8));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(10, 2, 9));
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(10, 1, 9));
            Contamination.Zone zone = zoneAt(helper, centre);
            helper.assertTrue(zone.duration() == 6_000, "its fallout lasts a quarter of a day: " + zone);
            Contamination.of(helper.getLevel()).removeNear(centre, 2);
            sheltered.discard();
        });
    }

    /** A dirty warhead is only HE and nuclear waste: set off on the ground it goes off in full, no fizzle. */
    private static void dirty(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 1, 8);
        helper.setBlock(at, GZOrdnance.DIRTY_WARHEAD.block().get().defaultBlockState());
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(at));
        // A twentieth of its size, so its waste (90 blocks round) stays in this test's plot.
        float scale = Charges.bombScale;
        Charges.bombScale = 0.05f;
        Charges.burst(helper.getLevel(), ChargeKind.DIRTY_WARHEAD, centre, null);
        Charges.bombScale = scale;
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(at)).isAir(), "the warhead is gone");
            Contamination.Zone zone = zoneAt(helper, centre);
            helper.assertTrue(Math.abs(zone.strength() - 0.8f) < 0.01f && zone.duration() == 120_000 && Math.abs(zone.radius() - 4.5f) < 0.01f,
                    "a dirty warhead's waste (90 blocks, 0.8, five days), not a fizzle's: " + zone);
            Contamination.of(helper.getLevel()).removeNear(centre, 2);
        });
    }

    /** A dirty warhead is class S: a Light casing, any missile body but a Strategic one, and a basic silo fires it. */
    private static void dirtyMissile(GameTestHelper helper) {
        ItemStack payload = new ItemStack(GZAssembly.DIRTY_PAYLOAD.get());
        ItemStack fuze = new ItemStack(WarheadAssembly.fuzeItem(Fuze.IMPACT));
        helper.assertTrue(WarheadAssembly.check(payload, new ItemStack(WarheadAssembly.casing(SizeClass.S)), fuze, ItemStack.EMPTY)
                == WarheadAssembly.Problem.NONE, "a Light Warhead Casing");
        helper.assertTrue(WarheadAssembly.check(payload, new ItemStack(GZAssembly.STRATEGIC_CASING.get()), fuze, ItemStack.EMPTY)
                == WarheadAssembly.Problem.WRONG_CASING, "not a Strategic one");
        ItemStack dirty = new ItemStack(GZOrdnance.DIRTY_WARHEAD.item().get());
        helper.assertTrue(MissileAssembly.check(dirty, new ItemStack(GZAssembly.TACTICAL_BODY.get()), new ItemStack(GZAssembly.SRM_I.get()),
                ItemStack.EMPTY) == MissileAssembly.Problem.NONE, "fits a Tactical body");
        helper.assertTrue(MissileAssembly.check(dirty, new ItemStack(GZAssembly.STRATEGIC_BODY.get()), new ItemStack(GZAssembly.LIQUID_FUEL_STAGE.get()),
                ItemStack.EMPTY) == MissileAssembly.Problem.STRATEGIC_NUCLEAR_ONLY, "but not a Strategic one");
        ItemStack missile = MissileAssembly.missile(new MissileSpec(dirty, SizeClass.S, MissileParts.Motor.SRM_I, MissileParts.Guidance.GPS, false));
        MissileSpec spec = dev.groundzero.content.assembly.MissileItem.spec(missile);
        helper.assertTrue(spec != null && !spec.needsComplex(), "no Launch Complex needed: " + spec);
        LaunchSiloBlockEntity silo = LaunchTests.formedSilo(helper);
        helper.assertTrue(silo.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, missile), "a basic silo takes it");
        helper.succeed();
    }

    /** The contaminated zones clients are sent (to draw their fallout) come through the wire as they were sent. */
    private static void zonesPayload(GameTestHelper helper) {
        var zones = java.util.List.of(new Contamination.Zone(new Vec3(1.5, 64, -2.25), 180, 1f, 1234L, 48_000),
                new Contamination.Zone(new Vec3(-300, 70.5, 9), 90, 0.8f, 99L, 120_000));
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        dev.groundzero.content.nuclear.ContaminationPayloads.Zones.CODEC.encode(buf, new dev.groundzero.content.nuclear.ContaminationPayloads.Zones(zones));
        var back = dev.groundzero.content.nuclear.ContaminationPayloads.Zones.CODEC.decode(buf);
        helper.assertTrue(back.zones().equals(zones) && buf.readableBytes() == 0, "the zones survive the trip: " + back.zones());
        helper.succeed();
    }

    /** Set off where it stands, a nuclear warhead only fizzles: a small blast and weaker contamination. */
    private static void fizzles(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 1, 8);
        helper.setBlock(at, GZOrdnance.FISSION_WARHEAD.block().get().defaultBlockState());
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(at));
        Charges.caughtInBlast(helper.getLevel(), helper.absolutePos(at), helper.getLevel().getBlockState(helper.absolutePos(at)));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(at)).isAir(), "the warhead is gone");
            float level = Contamination.of(helper.getLevel()).levelAt(helper.getLevel(), centre);
            helper.assertTrue(level > 0.3f && level <= Charges.FIZZLE_STRENGTH + 0.01f, "a fizzle's contamination: " + level);
            // A fission warhead's real blast (crater 40, 12 at this test scale) would have taken the floor 6 blocks off.
            helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(14, 0, 8))).isAir(), "no nuclear crater");
            Contamination.of(helper.getLevel()).removeNear(centre, 2);
        });
    }
}
