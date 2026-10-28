package dev.groundburst.gametest;

import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchComputerBlockEntity;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.Launches;
import dev.groundburst.content.launch.MissileEntity;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.content.multiblock.PartCut;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** The basic launch site (DESIGN.md §17): the silo and the computer forming, the silo's readiness, a launch to impact. */
final class LaunchTests {
    private LaunchTests() {}

    static void register() {
        GBGameTests.test("launch_silo_forms_and_gets_ready", GBGameTests.FLOOR_16, 20, LaunchTests::siloReady);
        GBGameTests.test("launch_computer_forms_and_links_silos", GBGameTests.FLOOR_16, 20, LaunchTests::computerLinks);
        GBGameTests.test("silo_will_not_launch_unready", GBGameTests.FLOOR_16, 20, LaunchTests::unready);
        GBGameTests.test("computer_aims_and_arms_each_silo", GBGameTests.FLOOR_16, 20, LaunchTests::perSilo);
        GBGameTests.test("armed_silo_disarms_when_unready", GBGameTests.FLOOR_16, 20, LaunchTests::disarms);
        GBGameTests.test("counting_silo_locks_its_missile", GBGameTests.FLOOR_16, 20, LaunchTests::lockedMissile);
        GBGameTests.test("halted_silo_refuses_and_scrubs_its_countdown", GBGameTests.FLOOR_16, 20, LaunchTests::halted);
        GBGameTests.test("emp_silo_refuses_to_arm", GBGameTests.FLOOR_16, 20, LaunchTests::empRefuses);
        GBGameTests.testOpenSky("missile_flies_on_over_unloaded_ground", GBGameTests.FLOOR_16, 400, LaunchTests::longFlight);
        GBGameTests.test("radar_array_tracks_incoming_missiles", GBGameTests.FLOOR_16, 60, LaunchTests::radar);
        GBGameTests.test("missiles_drop_stages_burn_out_and_reenter", GBGameTests.FLOOR_16, 20, LaunchTests::stages);
        GBGameTests.test("large_radar_dish_places_and_breaks_whole", GBGameTests.FLOOR_16, 40, LaunchTests::largeDish);
        GBGameTests.test("computer_surveys_the_ground_round_it", GBGameTests.FLOOR_16, 40, LaunchTests::survey);
        GBGameTests.testOpenSky("missile_flies_and_its_warhead_goes_off", GBGameTests.FLOOR_16, 400, LaunchTests::flight);
        GBGameTests.test("formed_decks_take_the_models_shape", GBGameTests.FLOOR_16, 20, LaunchTests::walkableDecks);
    }

    /** Whether a point of a block (in pixels) is inside its shape. */
    private static boolean solidAt(net.minecraft.world.phys.shapes.VoxelShape shape, double x, double y, double z) {
        return shape.toAabbs().stream().anyMatch(b -> b.contains(x / 16, y / 16, z / 16));
    }

    /**
     * Formed, the Launch Complex's deck is 14 px high, its flame trench a floor 2 px up with a player's room over it
     * all the way under the missile, the deflector's steps rising out of it; the Radar Array's deck is 6 px high with
     * its railings, cabinet, power box and mast foot; and the shapes turn with the structure (after slice 9b).
     */
    private static void walkableDecks(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        var c = NuclearTests.complex(helper, new BlockPos(5, 1, 2));
        helper.assertTrue(c.isFormed(), "the complex formed");
        java.util.Map<String, BlockPos> at = new java.util.HashMap<>();
        for (MultiblockDefinition.Cell cell : GBLaunch.COMPLEX.cells(c.getBlockPos(), Direction.NORTH)) {
            if (cell.layer() == 0) {
                at.put(cell.col() + "," + cell.row(), cell.pos());
            }
        }
        java.util.function.BiFunction<String, PartCut, net.minecraft.world.phys.shapes.VoxelShape> shape = (key, cut) -> {
            var state = level.getBlockState(at.get(key));
            helper.assertTrue(state.getValue(MultiblockPartBlock.CUT) == cut, key + " is " + cut + ": " + state);
            return state.getCollisionShape(level, at.get(key));
        };
        var deck = shape.apply("0,1", PartCut.DECK);
        helper.assertTrue(solidAt(deck, 8, 13, 8) && !solidAt(deck, 8, 15, 8), "the deck is 14 px high");
        var trench = shape.apply("0,2", PartCut.TRENCH_NORTH);
        helper.assertTrue(solidAt(trench, 8, 1, 8) && !solidAt(trench, 8, 3, 8) && solidAt(trench, 8, 13, 1), "the trench: floor 2 px up, deck along its side");
        var steps = shape.apply("2,2", PartCut.STEPS_NORTH);
        helper.assertTrue(solidAt(steps, 2, 4, 8) && !solidAt(steps, 2, 6, 8) && solidAt(steps, 12, 10, 8) && !solidAt(steps, 12, 12, 8),
                "the deflector's steps rise east, 5 px to 11 px");
        shape.apply("2,0", PartCut.CONSOLE_NORTH);                      // the controller, under its console
        shape.apply("2,4", PartCut.HOUSING_NORTH);
        // A player's room (0.6 wide, 1.8 tall) on the trench's floor, all the way along it and under the missile.
        for (String key : java.util.List.of("0,2", "1,2")) {
            BlockPos p = at.get(key);
            var box = new net.minecraft.world.phys.AABB(p.getX() + 0.2, p.getY() + 2.05 / 16, p.getZ() + 0.2, p.getX() + 0.8, p.getY() + 2.05 / 16 + 1.8, p.getZ() + 0.8);
            helper.assertTrue(level.noCollision(box), "room to walk along the trench at " + key);
        }
        BlockPos under = at.get("2,2");
        var onStep = new net.minecraft.world.phys.AABB(under.getX() + 0.55, under.getY() + 11.05 / 16, under.getZ() + 0.2, under.getX() + 0.95,
                under.getY() + 11.05 / 16 + 1.8, under.getZ() + 0.8);
        helper.assertTrue(level.noCollision(onStep), "room to stand on the top step under the missile");
        // The radar's deck.
        BlockPos rel = new BlockPos(5, 1, 10);
        build(helper, rel, GBLaunch.RADAR_CONTROLLER.block().get(), GBLaunch.RADAR);
        var r = (dev.groundburst.content.launch.RadarArrayBlockEntity) level.getBlockEntity(helper.absolutePos(rel));
        r.revalidate();
        helper.assertTrue(r.isFormed(), "the radar formed");
        at.clear();
        for (MultiblockDefinition.Cell cell : GBLaunch.RADAR.cells(r.getBlockPos(), Direction.NORTH)) {
            if (cell.layer() == 0) {
                at.put(cell.col() + "," + cell.row(), cell.pos());
            }
        }
        var left = shape.apply("0,1", PartCut.RAIL_NORTH);
        helper.assertTrue(solidAt(left, 8, 5, 8) && !solidAt(left, 8, 7, 8) && solidAt(left, 2.5, 10, 8), "the radar deck is 6 px high, railed on the west");
        var right = shape.apply("2,1", PartCut.RAIL_SOUTH);
        helper.assertTrue(solidAt(right, 13.5, 10, 8) && !solidAt(right, 2.5, 10, 8), "and on the east");
        var corner = shape.apply("2,2", PartCut.CORNER_RAIL_WEST);
        helper.assertTrue(solidAt(corner, 13.5, 10, 4) && solidAt(corner, 6, 10, 13.5) && !solidAt(corner, 2.5, 10, 4), "the back-right corner: east and back");
        shape.apply("1,1", PartCut.MAST);
        shape.apply("1,0", PartCut.CABINET_NORTH);
        shape.apply("1,2", PartCut.POWER_BOX_NORTH);
        // Turned with the structure: for a controller facing east the steps rise south and the trench runs north-south.
        helper.assertTrue(PartCut.STEPS_NORTH.rotated(Direction.EAST) == PartCut.STEPS_EAST && PartCut.RAIL_SOUTH.rotated(Direction.WEST) == PartCut.RAIL_EAST,
                "the cuts turn with the structure");
        var turned = PartCut.STEPS_EAST.shape();
        helper.assertTrue(solidAt(turned, 8, 10, 14) && !solidAt(turned, 8, 6, 2) && solidAt(turned, 15, 13, 8), "turned steps rise south");
        helper.succeed();
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    private static void build(GameTestHelper helper, BlockPos rel, Block controller, MultiblockDefinition def) {
        helper.setBlock(rel, controller.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        for (MultiblockDefinition.Cell cell : def.cells(helper.absolutePos(rel), Direction.NORTH)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                helper.getLevel().setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
            }
        }
    }

    private static LaunchSiloBlockEntity silo(GameTestHelper helper, BlockPos rel) {
        build(helper, rel, GBLaunch.SILO_CONTROLLER.block().get(), GBLaunch.SILO);
        LaunchSiloBlockEntity s = (LaunchSiloBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(s != null, "a silo controller");
        s.revalidate();
        return s;
    }

    /** A formed silo on a stone floor (for other test classes). */
    static LaunchSiloBlockEntity formedSilo(GameTestHelper helper) {
        floor(helper);
        return silo(helper, new BlockPos(5, 1, 3));
    }

    /** An assembled medium HE missile: Solid Rocket Motor II (1024 blocks), GPS guidance. */
    static ItemStack missile() {
        return dev.groundburst.content.assembly.MissileAssembly.missile(new dev.groundburst.content.assembly.MissileSpec(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), dev.groundburst.content.assembly.SizeClass.M, dev.groundburst.content.assembly.MissileParts.Motor.SRM_II, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false));
    }

    /** Formed, the silo asks for a missile, then power; then it is ready. */
    private static void siloReady(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(5, 1, 3));
        helper.assertTrue(s.isFormed(), "formed");
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.NO_MISSILE, "wants a missile: " + s.readiness());
        helper.assertTrue(!s.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, new ItemStack(GBOrdnance.HE_WARHEAD.item().get())),
                "a bare warhead does not go in");
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, missile());
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.NO_POWER, "wants power: " + s.readiness());
        helper.assertTrue(s.range() == 1024, "a Solid Rocket Motor II's range: " + s.range());
        s.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.READY, "ready: " + s.readiness());
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.STONE);              // something in the missile's column
        s.revalidate();
        helper.assertTrue(!s.isFormed(), "the missile's column must be clear");
        helper.succeed();
    }

    /** The computer forms, and finds the silo near it. */
    private static void computerLinks(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(5, 1, 3));
        BlockPos rel = new BlockPos(11, 1, 11);
        build(helper, rel, GBLaunch.COMPUTER_CONTROLLER.block().get(), GBLaunch.COMPUTER);
        LaunchComputerBlockEntity c = (LaunchComputerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(c != null, "a computer controller");
        c.revalidate();
        helper.assertTrue(c.isFormed(), "formed");
        helper.succeedWhen(() -> helper.assertTrue(Launches.silosNear(helper.getLevel(), helper.absolutePos(rel)).contains(s), "it links the silo"));
    }

    private static void load(LaunchSiloBlockEntity s) {
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, missile());
        s.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
    }

    /**
     * Two ready silos and a computer: each silo has its own target and is armed on its own (no arming without a
     * target), and LAUNCH fires only the armed one, at its own target.
     */
    private static void perSilo(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity a = silo(helper, new BlockPos(3, 1, 2)), b = silo(helper, new BlockPos(10, 1, 2));
        load(a);
        load(b);
        BlockPos rel = new BlockPos(6, 1, 10);
        build(helper, rel, GBLaunch.COMPUTER_CONTROLLER.block().get(), GBLaunch.COMPUTER);
        LaunchComputerBlockEntity c = (LaunchComputerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(c != null, "a computer controller");
        c.revalidate();
        c.energy().generate(LaunchComputerBlockEntity.ENERGY_CAPACITY);
        helper.runAfterDelay(2, () -> {                                       // powered from its first tick
            var level = helper.getLevel();
            BlockPos ta = helper.absolutePos(new BlockPos(14, 1, 14)), tb = helper.absolutePos(new BlockPos(1, 1, 14));
            helper.assertTrue(!c.arm(level, b.getBlockPos(), true), "no arming without a target");
            helper.assertTrue(c.setTarget(level, a.getBlockPos(), ta.getX(), ta.getZ()), "aim A");
            helper.assertTrue(c.setTarget(level, b.getBlockPos(), tb.getX(), tb.getZ()), "aim B");
            helper.assertTrue(a.target().getX() == ta.getX() && b.target().getX() == tb.getX(), "each silo keeps its own target");
            helper.assertTrue(c.arm(level, a.getBlockPos(), true) && a.armed() && !b.armed(), "only A armed");
            // At least one: a computer fires every armed silo within reach, and a neighbouring test's may be armed too.
            helper.assertTrue(c.launchArmed(level, null) >= 1, "LAUNCH fires the armed silo");
            helper.assertTrue(a.countdown() > 0 && b.countdown() < 0, "A counts down, B waits");
            helper.assertTrue(!a.armed(), "launching clears the arming");
            helper.assertTrue(!a.setTarget(1, 1), "no re-aiming during the countdown");
            helper.succeed();
        });
    }

    /** From the countdown on, a silo's missile cannot be taken out of its screen or swapped; before it, it can. */
    private static void lockedMissile(GameTestHelper helper) {
        LaunchSiloBlockEntity s = formedSilo(helper);
        load(s);
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var slot = s.createMenu(0, player.getInventory(), player).getSlot(LaunchSiloBlockEntity.SLOT_MISSILE);
        helper.assertTrue(slot.mayPickup(player), "a waiting missile can be taken out");
        helper.assertTrue(s.launch(helper.getLevel(), helper.absolutePos(new BlockPos(14, 1, 14)), null), "it counts down");
        helper.assertTrue(!slot.mayPickup(player) && !slot.mayPlace(missile()), "a counting one cannot, nor be swapped");
        helper.succeed();
    }

    /** An armed silo whose missile is taken out makes itself safe. */
    /**
     * A ready silo whose redstone setting holds it stopped will not arm or launch (it says so); stopped mid-countdown,
     * the launch is scrubbed.
     */
    private static void halted(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(5, 1, 3));
        load(s);
        BlockPos t = helper.absolutePos(new BlockPos(12, 1, 12));
        s.setTarget(t.getX(), t.getZ());
        helper.assertTrue(s.launch(helper.getLevel(), t, null), "launch ordered");
        s.cycleRedstoneMode();                                   // "run with a signal", and there is none
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.HALTED, "halted: " + s.readiness());
        helper.assertTrue(!s.arm(true), "it will not arm");
        helper.assertTrue(!s.launch(helper.getLevel(), t, null), "nor launch");
        // Its own missile stays on the pad (other tests' missiles may fly past, so no looking for missiles nearby).
        helper.succeedWhen(() -> helper.assertTrue(s.countdown() < 0 && !s.inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty(),
                "the countdown is scrubbed"));
    }

    /** A ready silo in an EMP field will not arm; it says it is disabled. */
    private static void empRefuses(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(5, 1, 3));
        load(s);
        BlockPos t = helper.absolutePos(new BlockPos(12, 1, 12));
        s.setTarget(t.getX(), t.getZ());
        dev.groundburst.content.effect.EmpFields.add(helper.getLevel(), net.minecraft.world.phys.Vec3.atCenterOf(s.getBlockPos()), 8, 40, null);
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.EMP, "disabled: " + s.readiness());
        helper.assertTrue(!s.arm(true), "it will not arm");
        helper.succeed();
    }

    private static void disarms(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(5, 1, 3));
        load(s);
        BlockPos t = helper.absolutePos(new BlockPos(12, 1, 12));
        s.setTarget(t.getX(), t.getZ());
        helper.assertTrue(s.arm(true), "armed");
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, ItemStack.EMPTY);
        helper.succeedWhen(() -> helper.assertTrue(!s.armed(), "disarmed once it is not ready"));
    }

    /**
     * A missile sent 400 blocks off (no warhead) over ground no one has loaded: its flight goes on every tick whatever
     * is loaded (it used to freeze where it flew into an unloaded chunk), and it arrives on time.
     */
    private static void longFlight(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        net.minecraft.world.phys.Vec3 pad = helper.absoluteVec(new net.minecraft.world.phys.Vec3(8, 1, 8));
        dev.groundburst.content.launch.Flights.Flight f = dev.groundburst.content.launch.Flights.launch(level, pad, pad.add(400, 0, 0),
                ItemStack.EMPTY, null);
        int due = f.ticksToImpact();
        helper.runAfterDelay(120, () -> helper.assertTrue(f.position().distanceTo(pad) > 60, "well on its way after 6 s: " + f.position()));
        helper.succeedWhen(() -> {
            helper.assertTrue(!dev.groundburst.content.launch.Flights.of(level).all().contains(f), "arrived (due in " + due + " ticks)");
        });
    }

    /**
     * Slice 9a: an intercontinental missile drops its first stage at 12% of its flight and its second at 24%, burns out
     * at 36% and coasts, and its nose glows coming down from past 55%; a Strategic missile drops one stage and burns out
     * at 40%; a basic missile drops none and burns out at 45%. (Each flight is called off before it arrives.)
     */
    private static void stages(GameTestHelper helper) {
        var level = helper.getLevel();
        net.minecraft.world.phys.Vec3 pad = helper.absoluteVec(new net.minecraft.world.phys.Vec3(8, 1, 8));
        var icbm = dev.groundburst.content.assembly.MissileItem.spec(
                dev.groundburst.content.strategic.StrategicAssembly.assemble(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        var f = dev.groundburst.content.launch.Flights.launch(level, pad, pad.add(2500, 0, 0), icbm, null);
        int ticks = f.ticksToImpact() - dev.groundburst.content.launch.MissileEntity.IGNITION;
        helper.assertTrue(f.dropped() == 0 && f.burning() && f.reentry() == 0, "on the pad: whole and burning");
        f.skipForTest(dev.groundburst.content.launch.MissileEntity.IGNITION + (int) (ticks * 0.13));
        helper.assertTrue(f.dropped() == 1 && f.burning(), "the first stage gone by 13%: " + f.dropped());
        f.skipForTest((int) (ticks * 0.12));
        helper.assertTrue(f.dropped() == 2 && f.burning(), "the second gone by 25%: " + f.dropped());
        f.skipForTest((int) (ticks * 0.15));
        helper.assertTrue(!f.burning() && f.reentry() == 0, "burnt out by 40%, not yet glowing");
        f.skipForTest((int) (ticks * 0.4));
        helper.assertTrue(f.reentry() > 0.5f, "glowing coming down at 80%: " + f.reentry());
        dev.groundburst.content.launch.Flights.of(level).cancelForTest(level, f);
        var strategic = new dev.groundburst.content.assembly.MissileSpec(new ItemStack(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()),
                dev.groundburst.content.assembly.SizeClass.XL, dev.groundburst.content.assembly.MissileParts.Motor.LIQUID,
                dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false);
        var s = dev.groundburst.content.launch.Flights.launch(level, pad, pad.add(1500, 0, 0), strategic, null);
        int sTicks = s.ticksToImpact() - dev.groundburst.content.launch.MissileEntity.IGNITION;
        s.skipForTest(dev.groundburst.content.launch.MissileEntity.IGNITION + (int) (sTicks * 0.3));
        helper.assertTrue(s.dropped() == 1 && s.burning(), "a Strategic missile: one stage gone at 30%, still burning");
        s.skipForTest((int) (sTicks * 0.12));
        helper.assertTrue(!s.burning(), "burnt out by 42%");
        dev.groundburst.content.launch.Flights.of(level).cancelForTest(level, s);
        var basic = dev.groundburst.content.launch.Flights.launch(level, pad, pad.add(400, 0, 0), ItemStack.EMPTY, null);
        int bTicks = basic.ticksToImpact() - dev.groundburst.content.launch.MissileEntity.IGNITION;
        basic.skipForTest(dev.groundburst.content.launch.MissileEntity.IGNITION + (int) (bTicks * 0.4));
        helper.assertTrue(basic.dropped() == 0 && basic.burning(), "a basic missile: one stage, burning at 40%");
        basic.skipForTest((int) (bTicks * 0.1));
        helper.assertTrue(!basic.burning(), "burnt out by 50%");
        dev.groundburst.content.launch.Flights.of(level).cancelForTest(level, basic);
        helper.succeed();
    }

    /** The Radar Array forms, and a missile coming down beside it is incoming (and a comparator reads it). */
    private static void radar(GameTestHelper helper) {
        floor(helper);
        BlockPos rel = new BlockPos(7, 1, 2);
        build(helper, rel, GBLaunch.RADAR_CONTROLLER.block().get(), GBLaunch.RADAR);
        dev.groundburst.content.launch.RadarArrayBlockEntity r =
                (dev.groundburst.content.launch.RadarArrayBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(r != null, "a radar controller");
        r.revalidate();
        helper.assertTrue(r.isFormed(), "formed");
        r.energy().generate(dev.groundburst.content.launch.RadarArrayBlockEntity.ENERGY_CAPACITY);
        net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(7, 1, 2));
        // From 300 blocks off, coming down 40 blocks from it (no warhead): far from landing within the test.
        dev.groundburst.content.launch.Flights.launch(helper.getLevel(), at.add(-300, 0, 0), at.add(40, 0, 0), ItemStack.EMPTY, null);
        helper.succeedWhen(() -> {
            helper.assertTrue(r.incoming() >= 1, "it sees the missile coming");
            int signal = helper.getLevel().getBlockState(helper.absolutePos(rel))
                    .getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(rel), net.minecraft.core.Direction.NORTH);
            helper.assertTrue(signal >= 1, "a comparator reads it (" + signal + ")");
        });
    }

    /**
     * The Large Radar Dish placed fills the 3x3 round it (its middle and eight parts); blow one part away and the whole
     * dish goes, dropping one dish.
     */
    private static void largeDish(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        var block = GBLaunch.LARGE_RADAR_DISH.block().get();
        BlockPos c = helper.absolutePos(new BlockPos(8, 1, 8));
        level.setBlockAndUpdate(c, block.defaultBlockState());
        block.setPlacedBy(level, c, level.getBlockState(c), null, new ItemStack(GBLaunch.LARGE_RADAR_DISH.item().get()));
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, 0, -1), c.offset(1, 0, 1))) {
            helper.assertTrue(level.getBlockState(p).is(block), "the 3x3 is dish at " + p);
        }
        helper.assertTrue(level.getBlockState(c.offset(1, 0, 1)).getValue(dev.groundburst.content.launch.LargeRadarDishBlock.PART)
                == dev.groundburst.content.launch.LargeRadarDishBlock.Part.EDGE, "a corner is a part");
        level.removeBlock(c.offset(1, 0, 0), false);                         // a part lost (to a blast, say)
        helper.succeedWhen(() -> {
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, 0, -1), c.offset(1, 0, 1))) {
                helper.assertTrue(!level.getBlockState(p).is(block), "all of it gone");
            }
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(c).inflate(3));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(GBLaunch.LARGE_RADAR_DISH.item().get()), "one dish dropped");
        });
    }

    /** A formed computer maps the ground round it: the tile it stands in has colours. */
    private static void survey(GameTestHelper helper) {
        floor(helper);
        BlockPos rel = new BlockPos(8, 1, 8);
        build(helper, rel, GBLaunch.COMPUTER_CONTROLLER.block().get(), GBLaunch.COMPUTER);
        LaunchComputerBlockEntity c = (LaunchComputerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(c != null, "a computer controller");
        c.revalidate();
        c.energy().generate(LaunchComputerBlockEntity.ENERGY_CAPACITY);
        BlockPos at = helper.absolutePos(rel);
        int span = dev.groundburst.content.launch.TerrainSurvey.TILE;
        helper.succeedWhen(() -> {
            byte[] tile = dev.groundburst.content.launch.TerrainSurvey.of(helper.getLevel()).tile(helper.getLevel(), 1,
                    Math.floorDiv(at.getX(), span), Math.floorDiv(at.getZ(), span)).colours();
            helper.assertTrue(tile.length == span * span, "the ground round it is mapped");
        });
    }

    /** No missile, no launch. */
    private static void unready(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(5, 1, 3));
        s.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        helper.assertTrue(!s.launch(helper.getLevel(), helper.absolutePos(new BlockPos(12, 1, 12)), null), "refused");
        helper.assertTrue(s.countdown() < 0, "no countdown");
        helper.succeed();
    }

    /**
     * Ready, launched at a spot 10 blocks off: the countdown runs, the missile lifts off (taking the missile and
     * warhead), flies its arc and comes down there, and its warhead (scaled down for the test) blows a hole.
     */
    private static void flight(GameTestHelper helper) {
        floor(helper);
        Charges.bombScale = 0.3f;
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(3, 1, 2));
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, missile());
        s.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        BlockPos target = new BlockPos(13, 1, 13);
        helper.assertTrue(s.launch(helper.getLevel(), helper.absolutePos(target), null), "launch ordered");
        s.skipCountdownForTest();
        AABB sky = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(64, 300, 64);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(MissileEntity.class, sky).isEmpty(), "lit on the pad");
            helper.assertTrue(s.inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty(), "the missile is used");
            helper.succeedWhen(() -> {
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(MissileEntity.class, sky).isEmpty(), "it has come down");
                // GPS guidance: it comes down within 2 blocks of the target.
                boolean hole = false;
                for (BlockPos p : BlockPos.betweenClosed(11, 0, 11, 15, 0, 15)) {
                    hole |= helper.getLevel().getBlockState(helper.absolutePos(p)).isAir();
                }
                helper.assertTrue(hole, "a hole at the target");
            });
        });
    }
}
