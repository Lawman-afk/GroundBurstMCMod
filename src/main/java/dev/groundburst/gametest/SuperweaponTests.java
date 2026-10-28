package dev.groundburst.gametest;

import java.util.List;

import dev.groundburst.content.effect.GasAgent;
import dev.groundburst.content.effect.GasGrid;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.LaunchSiloBlockEntity.Readiness;
import dev.groundburst.content.launch.LaunchSite;
import dev.groundburst.content.launch.Launches;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.radiation.RadiationSources;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.ToxinSpreads;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.content.ordnance.charge.Charges;
import dev.groundburst.content.superweapon.AmmoFactoryBlockEntity;
import dev.groundburst.content.superweapon.GBSuperweapons;
import dev.groundburst.content.superweapon.NuclearMissileBlockEntity;
import dev.groundburst.content.superweapon.ScudStormBlockEntity;
import dev.groundburst.content.superweapon.SuperweaponBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The superweapons (DESIGN.md §19.5): the SCUD Storm and the Nuclear Missile Silo form on their structures, take only
 * their own ammunition, charge on power, are found, aimed, armed and fired from a Launch Computer and fire their
 * missiles (nine one after another, or one); a toxin SCUD's strike spreads anthrax residue and a toxin cloud, the
 * Nuclear Missile's warhead leaves its radiation field; the Arms Dealer and the War Factory build their rounds.
 */
final class SuperweaponTests {
    private SuperweaponTests() {}

    static void register() {
        GBGameTests.test("scud_storm_forms_charges_and_fires_its_salvo", GBGameTests.FLOOR_16, 400, SuperweaponTests::scudStormFires);
        GBGameTests.test("nuclear_missile_silo_forms_charges_and_fires", GBGameTests.FLOOR_16, 300, SuperweaponTests::nuclearSiloFires);
        GBGameTests.test("superweapons_take_only_their_own_ammunition", GBGameTests.FLOOR_16, 20, SuperweaponTests::ownAmmo);
        GBGameTests.test("toxin_scud_spreads_residue_and_a_cloud", GBGameTests.FLOOR_16, 120, SuperweaponTests::toxinStrike);
        GBGameTests.test("nuclear_missile_warhead_leaves_its_radiation", GBGameTests.FLOOR_16, 100, SuperweaponTests::nuclearStrike);
        GBGameTests.test("superweapon_warheads_are_whole_weapons", GBGameTests.FLOOR_16, 20, SuperweaponTests::warheadKinds);
        GBGameTests.test("arms_dealer_builds_toxin_scuds", GBGameTests.FLOOR_16, 40, h -> factory(h, AmmoFactoryBlockEntity.Kind.ARMS_DEALER));
        GBGameTests.test("war_factory_builds_nuclear_missiles", GBGameTests.FLOOR_16, 40, h -> factory(h, AmmoFactoryBlockEntity.Kind.WAR_FACTORY));
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /** Builds a structure round a controller at {@code rel} facing north, as the Structure Spawner would. */
    private static void build(GameTestHelper helper, BlockPos rel, Block controller, MultiblockDefinition def) {
        floor(helper);
        helper.setBlock(rel, controller.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        for (MultiblockDefinition.Cell cell : def.cells(helper.absolutePos(rel), Direction.NORTH)) {
            if (cell.key() == '.') {
                helper.getLevel().setBlockAndUpdate(cell.pos(), Blocks.AIR.defaultBlockState());
            } else if (cell.key() != MultiblockDefinition.CONTROLLER) {
                helper.getLevel().setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
            }
        }
    }

    static ScudStormBlockEntity scudStorm(GameTestHelper helper, boolean build) {
        BlockPos rel = new BlockPos(8, 1, 0);                   // (the 16x16 pattern, its controller in the front row's ninth cell)
        if (build) {
            build(helper, rel, GBSuperweapons.SCUD_STORM_CONTROLLER.block().get(), GBSuperweapons.SCUD_STORM);
        } else {
            floor(helper);
            helper.setBlock(rel, GBSuperweapons.SCUD_STORM_CONTROLLER.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        }
        var be = (ScudStormBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(be != null, "a SCUD Storm controller");
        be.revalidate();
        return be;
    }

    static NuclearMissileBlockEntity nuclearSilo(GameTestHelper helper) {
        BlockPos rel = new BlockPos(5, 1, 0);
        build(helper, rel, GBSuperweapons.NUCLEAR_MISSILE_CONTROLLER.block().get(), GBSuperweapons.NUCLEAR_MISSILE);
        var be = (NuclearMissileBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(be != null, "a Nuclear Missile Silo controller");
        be.revalidate();
        return be;
    }

    /** The missiles new since {@code before} bound for near {@code target}; cancelled (none comes down on another test). */
    private static int launched(GameTestHelper helper, List<Flights.Flight> before, BlockPos target, double within) {
        var level = helper.getLevel();
        Vec3 aim = Vec3.atBottomCenterOf(target);
        var ours = Flights.of(level).all().stream().filter(f -> !before.contains(f)
                && Math.hypot(f.target().x - aim.x, f.target().z - aim.z) <= within).toList();
        ours.forEach(f -> Flights.of(level).cancelForTest(level, f));
        return ours.size();
    }

    /**
     * Formed, loaded and powered it charges; charged it is READY and a Launch Computer's list has it; aimed and armed,
     * LAUNCH counts down with its erectors up and fires all nine, one after another, each near the target.
     */
    private static void scudStormFires(GameTestHelper helper) {
        ScudStormBlockEntity s = scudStorm(helper, true);
        var level = helper.getLevel();
        helper.assertTrue(s.isFormed(), "formed: " + GBSuperweapons.SCUD_STORM.check(level, s.getBlockPos(), Direction.NORTH));
        helper.assertTrue(s.siteKind() == LaunchSite.Kind.SCUD_STORM && s.range() == SuperweaponBlockEntity.RANGE, "a SCUD Storm, reaching 4096");
        s.inventory().setItem(SuperweaponBlockEntity.SLOT_AMMO, new ItemStack(GBSuperweapons.TOXIN_SCUD.get(), ScudStormBlockEntity.CAPACITY));
        helper.assertTrue(s.readiness() == Readiness.NO_POWER, "unpowered it cannot charge: " + s.readiness());
        s.energy().generate(400_000);
        helper.assertTrue(s.readiness() == Readiness.CHARGING && s.countdown() == ScudStormBlockEntity.CHARGE_TICKS, "charging: " + s.readiness());
        helper.assertTrue(!s.arm(true), "it will not arm while it charges");
        s.chargeForTest();
        helper.assertTrue(s.readiness() == Readiness.READY, "charged: " + s.readiness());
        Launches.track(level, s.getBlockPos());
        helper.assertTrue(Launches.silosNear(level, s.getBlockPos()).contains(s), "a Launch Computer finds it");
        BlockPos target = s.getBlockPos().offset(400, 0, 200);
        helper.assertTrue(s.setTarget(target.getX(), target.getZ()) && s.arm(true), "aimed and armed");
        var before = Flights.of(level).all();
        helper.assertTrue(s.launchArmed(level, null), "LAUNCH");
        helper.assertTrue(s.raised() && s.readiness() == Readiness.COUNTING, "counting down, its erectors up: " + s.readiness());
        s.skipCountdownForTest();
        helper.runAfterDelay(9 * ScudStormBlockEntity.FIRE_INTERVAL + 10, () -> {
            helper.assertTrue(s.loaded() == 0, "all nine fired: " + s.loaded() + " left");
            int n = launched(helper, before, target, ScudStormBlockEntity.SPREAD + 1);
            helper.assertTrue(n == ScudStormBlockEntity.CAPACITY, "nine SCUDs in the air: " + n);
            helper.succeed();
        });
    }

    /** The same for the Nuclear Missile Silo: one missile, raised, fired, on its way. */
    private static void nuclearSiloFires(GameTestHelper helper) {
        NuclearMissileBlockEntity n = nuclearSilo(helper);
        var level = helper.getLevel();
        helper.assertTrue(n.isFormed(), "formed: " + GBSuperweapons.NUCLEAR_MISSILE.check(level, n.getBlockPos(), Direction.NORTH));
        n.inventory().setItem(SuperweaponBlockEntity.SLOT_AMMO, new ItemStack(GBSuperweapons.NUCLEAR_MISSILE_ROUND.get()));
        n.energy().generate(400_000);
        n.chargeForTest();
        helper.assertTrue(n.readiness() == Readiness.READY, "charged and loaded: " + n.readiness());
        BlockPos target = n.getBlockPos().offset(-300, 0, 500);
        n.setTarget(target.getX(), target.getZ());
        var before = Flights.of(level).all();
        helper.assertTrue(n.arm(true) && n.launchArmed(level, null), "armed and launched");
        helper.assertTrue(!n.shownMissiles().isEmpty() && n.raised(), "its missile raised");
        n.skipCountdownForTest();
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(n.loaded() == 0 && n.shownMissiles().isEmpty(), "its missile gone");
            helper.assertTrue(launched(helper, before, target, NuclearMissileBlockEntity.SPREAD + 1) == 1, "one missile in the air");
        });
        helper.runAfterDelay(10 + SuperweaponBlockEntity.LINGER + 80, () -> {
            helper.assertTrue(n.phase() == SuperweaponBlockEntity.Phase.IDLE && !n.raised(), "stood down, its erector lowered: " + n.phase());
            helper.assertTrue(n.charge() < 100, "charging again from empty after the strike: " + n.charge());
            helper.succeed();
        });
    }

    /** Each takes only its own round (nine Toxin SCUDs, one Nuclear Missile), and not an assembled missile. */
    private static void ownAmmo(GameTestHelper helper) {
        ScudStormBlockEntity s = scudStorm(helper, false);
        helper.assertTrue(s.inventory().canPlaceItem(SuperweaponBlockEntity.SLOT_AMMO, new ItemStack(GBSuperweapons.TOXIN_SCUD.get())),
                "the SCUD Storm takes Toxin SCUDs");
        helper.assertTrue(!s.inventory().canPlaceItem(SuperweaponBlockEntity.SLOT_AMMO, new ItemStack(GBSuperweapons.NUCLEAR_MISSILE_ROUND.get())),
                "but not a Nuclear Missile");
        helper.assertTrue(!s.inventory().canPlaceItem(SuperweaponBlockEntity.SLOT_AMMO, NuclearTests.nuke()), "nor an assembled missile");
        helper.assertTrue(new ItemStack(GBSuperweapons.TOXIN_SCUD.get()).getMaxStackSize() == 9 && new ItemStack(GBSuperweapons.NUCLEAR_MISSILE_ROUND.get())
                .getMaxStackSize() == 1, "nine SCUDs to a stack, one missile");
        helper.succeed();
    }

    /**
     * A toxin SCUD coming down: its blast, then (the crater dug) a toxin cloud welling up and anthrax residue spreading
     * out over the ground round it.
     */
    private static void toxinStrike(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(8, 1, 8));
        float scale = Charges.bombScale;
        Charges.bombScale = 0.3f;
        Charges.impact(level, ChargeKind.SCUD_STORM_WARHEAD, at, null);
        Charges.bombScale = scale;
        helper.assertTrue(ToxinSpreads.active(level) > 0, "a front of toxin on its way");
        helper.succeedWhen(() -> {
            int residue = 0;
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-7, -4, -7), at.offset(7, 2, 7))) {
                if (level.getBlockState(p).is(GBOrdnance.ANTHRAX_RESIDUE.get())) {
                    residue++;
                }
            }
            helper.assertTrue(residue >= 12, "anthrax residue spread round it: " + residue);
            float cloud = 0;
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, -3, -3), at.offset(3, 3, 3))) {
                cloud = Math.max(cloud, GasGrid.of(level).concentration(p, GasAgent.SCUD_TOXIN));
            }
            helper.assertTrue(cloud > 0.2f, "a toxin cloud over it: " + cloud);
        });
    }

    /** The Nuclear Missile's warhead, delivered: a nuclear blast and contamination 200 blocks round (scaled down here). */
    private static void nuclearStrike(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(8, 1, 8));
        Vec3 centre = Vec3.atCenterOf(at);
        float scale = Charges.bombScale;
        Charges.bombScale = 0.05f;
        Charges.impact(level, ChargeKind.NUCLEAR_MISSILE_WARHEAD, at, null);
        Charges.bombScale = scale;
        helper.succeedWhen(() -> {
            float c = RadiationSources.of(level).rateAt(level, centre);
            helper.assertTrue(c > 0.9f * Charges.falloutPeak(ChargeKind.NUCLEAR_MISSILE_WARHEAD), "full fallout where it came down: " + c);
            RadiationSources.of(level).removeNear(centre, 2);
        });
    }

    /** The superweapons' warheads: whole weapons (never fitted at an assembler); the nuke's nuclear, the SCUD's not. */
    private static void warheadKinds(GameTestHelper helper) {
        helper.assertTrue(ChargeKind.SCUD_STORM_WARHEAD.superweapon() && ChargeKind.SCUD_STORM_WARHEAD.wholeWeapon()
                && !ChargeKind.SCUD_STORM_WARHEAD.nuclear(), "the toxin SCUD's warhead: a whole weapon, not nuclear");
        helper.assertTrue(ChargeKind.NUCLEAR_MISSILE_WARHEAD.superweapon() && ChargeKind.NUCLEAR_MISSILE_WARHEAD.nuclear()
                && ChargeKind.NUCLEAR_MISSILE_WARHEAD.radiological(), "the Nuclear Missile's: nuclear and radiological");
        helper.assertTrue(Charges.contaminationRadius(ChargeKind.NUCLEAR_MISSILE_WARHEAD) == 200, "its radiation field 200 blocks round");
        helper.succeed();
    }

    /** Each factory, its parts in and powered, sets to work by itself and builds its round, using its parts. */
    private static void factory(GameTestHelper helper, AmmoFactoryBlockEntity.Kind kind) {
        BlockPos rel = new BlockPos(5, 1, 1);
        boolean arms = kind == AmmoFactoryBlockEntity.Kind.ARMS_DEALER;
        build(helper, rel, (arms ? GBSuperweapons.ARMS_DEALER_CONTROLLER : GBSuperweapons.WAR_FACTORY_CONTROLLER).block().get(),
                arms ? GBSuperweapons.ARMS_DEALER : GBSuperweapons.WAR_FACTORY);
        var f = (AmmoFactoryBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(f != null && f.kind() == kind, "a " + kind + " controller");
        f.revalidate();
        helper.assertTrue(f.isFormed(), "formed: " + f.definition().check(helper.getLevel(), f.getBlockPos(), Direction.NORTH));
        for (int i = 0; i < kind.inputs.size(); i++) {
            f.inventory().setItem(i, new ItemStack(kind.inputs.get(i).item().get(), kind.inputs.get(i).count()));
        }
        f.energy().generate(kind.capacity);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(f.working(), "it set to work by itself");
            f.skipToEndForTest();
            helper.succeedWhen(() -> {
                helper.assertTrue(f.inventory().getItem(kind.outputSlot()).is(kind.output()), "its round built");
                for (int i = 0; i < kind.inputs.size(); i++) {
                    helper.assertTrue(f.inventory().getItem(i).isEmpty(), "part " + i + " used");
                }
                var shown = f.shownMissile();
                helper.assertTrue(shown != null && shown.looks().size() == AmmoFactoryBlockEntity.SECTIONS && shown.warhead().equals(kind.warhead),
                        "the finished round on its cradles: " + shown);
            });
        });
    }
}
