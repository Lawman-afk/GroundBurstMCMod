package dev.groundburst.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchCodesItem;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.LaunchStationBlockEntity;
import dev.groundburst.content.launch.RadarArrayBlockEntity;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The Launch Control Station (DESIGN.md §17.12): its network and codes, links, orders to a loaded launcher, patterns,
 * time on target, waking an unloaded launcher to launch, lost contact, and new codes voiding old cards.
 */
final class StationTests {
    private StationTests() {}

    private static final BlockPos STATION = new BlockPos(6, 1, 10), SILO_A = new BlockPos(2, 1, 1), SILO_B = new BlockPos(9, 1, 1);

    static void register() {
        GBGameTests.test("station_forms_and_issues_codes", GBGameTests.FLOOR_16, 20, StationTests::issuesCodes);
        GBGameTests.test("codes_link_launchers_and_radars", GBGameTests.FLOOR_16, 20, StationTests::links);
        GBGameTests.test("station_aims_arms_and_launches_a_loaded_silo", GBGameTests.FLOOR_16, 40, StationTests::runsLoaded);
        GBGameTests.test("station_patterns_spread_the_aim", GBGameTests.FLOOR_16, 20, StationTests::patterns);
        GBGameTests.test("station_times_launches_to_land_together", GBGameTests.FLOOR_16, 40, StationTests::timeOnTarget);
        GBGameTests.test("station_wakes_an_unloaded_silo_to_launch", GBGameTests.FLOOR_16, 600, StationTests::wakes);
        GBGameTests.test("station_loses_contact_with_a_broken_silo", GBGameTests.FLOOR_16, 60, StationTests::lostContact);
        GBGameTests.test("new_codes_void_old_cards", GBGameTests.FLOOR_16, 20, StationTests::newCodes);
    }

    static LaunchStationBlockEntity station(GameTestHelper helper) {
        LaunchTests.build(helper, STATION, GBLaunch.STATION_CONTROLLER.block().get(), GBLaunch.STATION);
        LaunchStationBlockEntity s = (LaunchStationBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(STATION));
        helper.assertTrue(s != null, "a station controller");
        s.revalidate();
        s.energy().generate(LaunchStationBlockEntity.ENERGY_CAPACITY);
        return s;
    }

    private static ItemStack codes(ServerLevel level, LaunchStationBlockEntity station) {
        ItemStack card = new ItemStack(GBLaunch.LAUNCH_CODES.get());
        LaunchCodesItem.use(level, card, station, false);
        return card;
    }

    /** A loaded, powered silo ready to fire. */
    private static LaunchSiloBlockEntity readySilo(GameTestHelper helper, BlockPos rel) {
        LaunchSiloBlockEntity s = LaunchTests.silo(helper, rel);
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile());
        s.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.READY, "the silo is ready: " + s.readiness());
        return s;
    }

    /** Formed, the station makes its network; blank codes used on it take its code. */
    private static void issuesCodes(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = station(helper);
        helper.assertTrue(st.isFormed(), "the station formed");
        LaunchNetwork.Network n = st.network(level);
        helper.assertTrue(n != null && n.code().matches("[A-Z2-9]{3}-[A-Z2-9]{3}"), "a network with a code: " + (n == null ? null : n.code()));
        ItemStack card = codes(level, st);
        helper.assertTrue(LaunchCodesItem.code(card).equals(n.code()), "the card took the code: " + LaunchCodesItem.code(card));
        helper.assertTrue(LaunchNetwork.of(level).byCode(n.code()) == n, "the code finds the network");
        helper.succeed();
    }

    /** The codes link a launcher (by its controller or any part of it) and a Radar Array; sneaking unlinks. */
    private static void links(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = station(helper);
        LaunchSiloBlockEntity silo = LaunchTests.silo(helper, SILO_A);
        ItemStack card = codes(level, st);
        LaunchNetwork.Network n = st.network(level);
        LaunchCodesItem.use(level, card, silo, false);
        helper.assertTrue(n.member(silo.getBlockPos()) != null, "the silo is linked");
        // A part of it (the pad beside its controller) reaches the same controller.
        var viaPart = LaunchCodesItem.target(level, helper.absolutePos(SILO_A.west()));
        helper.assertTrue(viaPart == silo, "a part reaches the controller: " + viaPart);
        LaunchCodesItem.use(level, card, viaPart, false);
        helper.assertTrue(n.members().size() == 1, "linked once: " + n.members().size());
        LaunchCodesItem.use(level, card, silo, true);
        helper.assertTrue(n.member(silo.getBlockPos()) == null, "sneaking unlinks it");
        // A Radar Array.
        BlockPos radarRel = new BlockPos(11, 1, 2);
        LaunchTests.build(helper, radarRel, GBLaunch.RADAR_CONTROLLER.block().get(), GBLaunch.RADAR);
        RadarArrayBlockEntity radar = (RadarArrayBlockEntity) level.getBlockEntity(helper.absolutePos(radarRel));
        radar.revalidate();
        LaunchCodesItem.use(level, card, radar, false);
        LaunchNetwork.Member m = n.member(radar.getBlockPos());
        helper.assertTrue(m != null && m.role == LaunchNetwork.Role.RADAR, "the radar is linked as a radar");
        helper.succeed();
    }

    /** A loaded silo on the network takes its aim, arming and launch at once. */
    private static void runsLoaded(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = station(helper);
        LaunchSiloBlockEntity silo = readySilo(helper, SILO_A);
        LaunchCodesItem.use(level, codes(level, st), silo, false);
        helper.runAfterDelay(3, () -> {                                // the station has ticked: powered
            helper.assertTrue(st.powered(), "the station is powered");
            BlockPos t = silo.getBlockPos().offset(300, 0, 40);
            List<BlockPos> picked = List.of(silo.getBlockPos());
            st.aim(level, picked, t.getX(), t.getZ(), LaunchNetwork.Pattern.POINT, 0);
            helper.assertTrue(silo.target() != null && silo.target().getX() == t.getX() && silo.target().getZ() == t.getZ(),
                    "aimed at once: " + silo.target());
            st.arm(level, picked, true);
            helper.assertTrue(silo.armed(), "armed at once");
            helper.assertTrue(st.launch(level, false, null) == 1, "one launcher ordered to fire");
            helper.assertTrue(silo.countdown() >= 0, "counting down: " + silo.countdown());
            LaunchNetwork.Member m = st.network(level).member(silo.getBlockPos());
            helper.assertTrue(m.report().readiness() == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal(), "its record shows it counting");
            helper.setBlock(SILO_A, Blocks.AIR);                         // scrubbed: no missile left flying after the test
            helper.succeed();
        });
    }

    /** A line across the line of attack, a ring and a grid, each the spacing apart; a point keeps them all on the aim. */
    private static void patterns(GameTestHelper helper) {
        List<BlockPos> three = List.of(new BlockPos(0, 64, 0), new BlockPos(4, 64, 0), new BlockPos(8, 64, 0));
        List<int[]> line = LaunchNetwork.points(three, 0, 500, LaunchNetwork.Pattern.LINE, 20);
        helper.assertTrue(Math.abs(line.get(0)[0] - line.get(2)[0]) >= 39 && Math.abs(line.get(0)[1] - line.get(2)[1]) <= 1,
                "the line runs across the line of attack (east to west for an attack to the south): " + str(line));
        List<BlockPos> four = new ArrayList<>(three);
        four.add(new BlockPos(12, 64, 0));
        for (int[] p : LaunchNetwork.points(four, 100, 100, LaunchNetwork.Pattern.RING, 30)) {
            double r = Math.hypot(p[0] + 0.5 - 100.5, p[1] + 0.5 - 100.5);
            helper.assertTrue(Math.abs(r - 30) <= 1.5, "on the ring: " + r);
        }
        List<int[]> grid = LaunchNetwork.points(four, 0, 0, LaunchNetwork.Pattern.GRID, 16);
        helper.assertTrue(grid.stream().map(p -> p[0] + "," + p[1]).distinct().count() == 4, "four grid points: " + str(grid));
        helper.assertTrue(Math.abs(grid.get(0)[0] - grid.get(1)[0]) == 16, "16 apart: " + str(grid));
        for (int[] p : LaunchNetwork.points(four, 7, 9, LaunchNetwork.Pattern.POINT, 50)) {
            helper.assertTrue(p[0] == 7 && p[1] == 9, "a point: all on the aim");
        }
        helper.succeed();
    }

    private static String str(List<int[]> ps) {
        StringBuilder b = new StringBuilder();
        for (int[] p : ps) {
            b.append('(').append(p[0]).append(',').append(p[1]).append(')');
        }
        return b.toString();
    }

    /** Time on target: the farther silo fires at once, the nearer one is held so both first warheads arrive together. */
    private static void timeOnTarget(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = station(helper);
        LaunchSiloBlockEntity far = readySilo(helper, SILO_A), near = readySilo(helper, SILO_B);
        ItemStack card = codes(level, st);
        LaunchCodesItem.use(level, card, far, false);
        LaunchCodesItem.use(level, card, near, false);
        helper.runAfterDelay(3, () -> {
            st.aim(level, List.of(far.getBlockPos()), far.getBlockPos().getX() + 900, far.getBlockPos().getZ(), LaunchNetwork.Pattern.POINT, 0);
            st.aim(level, List.of(near.getBlockPos()), near.getBlockPos().getX() + 100, near.getBlockPos().getZ(), LaunchNetwork.Pattern.POINT, 0);
            st.arm(level, List.of(far.getBlockPos(), near.getBlockPos()), true);
            long now = level.getGameTime();
            helper.assertTrue(st.launch(level, true, null) == 2, "two ordered");
            LaunchNetwork.Network n = st.network(level);
            LaunchNetwork.Member mf = n.member(far.getBlockPos()), mn = n.member(near.getBlockPos());
            int etaFar = far.timing().toImpact(far.target().getX(), far.target().getZ());
            int etaNear = near.timing().toImpact(near.target().getX(), near.target().getZ());
            helper.assertTrue(etaFar > etaNear, "the far one takes longer: " + etaFar + " vs " + etaNear);
            helper.assertTrue(far.countdown() >= 0, "the far silo fires at once");
            helper.assertTrue(near.countdown() < 0 && mn.orders().launchAt() == now + (etaFar - etaNear),
                    "the near one is held " + (etaFar - etaNear) + " ticks: " + (mn.orders().launchAt() - now));
            helper.assertTrue(mf.orders().launchAt() < 0, "the far one's order is done");
            helper.setBlock(SILO_A, Blocks.AIR);
            helper.setBlock(SILO_B, Blocks.AIR);
            helper.succeed();
        });
    }

    /**
     * A silo far off (its chunks unloaded) is woken to launch: aimed and armed while asleep (the orders wait in its
     * record), then LAUNCH loads its chunks and it counts down.
     */
    private static void wakes(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = station(helper);
        BlockPos far = helper.absolutePos(SILO_A).offset(4096, 0, 0);
        List<long[]> forced = new ArrayList<>();
        for (int cx = (far.getX() - 4) >> 4; cx <= (far.getX() + 6) >> 4; cx++) {
            for (int cz = (far.getZ() - 3) >> 4; cz <= (far.getZ() + 5) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                forced.add(new long[] {cx, cz});
            }
        }
        // A floor there, the space over it clear, and the silo built on it.
        for (BlockPos p : BlockPos.betweenClosed(far.offset(-3, -1, -2), far.offset(5, -1, 4))) {
            level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos p : BlockPos.betweenClosed(far.offset(-3, 0, -2), far.offset(5, 12, 4))) {
            level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(far, GBLaunch.SILO_CONTROLLER.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        for (MultiblockDefinition.Cell cell : GBLaunch.SILO.cells(far, Direction.NORTH)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                level.setBlockAndUpdate(cell.pos(), GBLaunch.SILO.legend().get(cell.key()).preview().get());
            }
        }
        LaunchSiloBlockEntity silo = (LaunchSiloBlockEntity) level.getBlockEntity(far);
        silo.revalidate();
        helper.assertTrue(silo.isFormed(), "the far silo formed");
        silo.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile());
        silo.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        helper.assertTrue(LaunchNetwork.of(level).link(level, st.network(level).id, silo) == LaunchNetwork.LinkResult.LINKED, "linked");
        for (long[] c : forced) {
            level.setChunkForced((int) c[0], (int) c[1], false);
        }
        BlockPos target = far.offset(300, 0, 0);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!level.isLoaded(far), "the far silo's chunk unloads"))
                .thenExecute(() -> {
                    List<BlockPos> picked = List.of(far);
                    st.aim(level, picked, target.getX(), target.getZ(), LaunchNetwork.Pattern.POINT, 0);
                    st.arm(level, picked, true);
                    LaunchNetwork.Member m = st.network(level).member(far);
                    helper.assertTrue(m.orders().aim().isPresent() && m.orders().arm() == 1, "aim and arm wait in its record: " + m.orders());
                    helper.assertTrue(!level.isLoaded(far), "still asleep after aiming and arming");
                    helper.assertTrue(st.launch(level, false, null) == 1, "ordered to fire");
                })
                .thenWaitUntil(() -> {
                    LaunchNetwork.Member m = st.network(level).member(far);
                    helper.assertTrue(m.report().readiness() == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal(),
                            "woken, it counts down: " + m.report().readiness() + " orders " + m.orders());
                })
                .thenExecute(() -> {
                    LaunchSiloBlockEntity woke = (LaunchSiloBlockEntity) level.getBlockEntity(far);
                    helper.assertTrue(woke != null && woke.countdown() >= 0 && woke.target() != null && woke.target().getX() == target.getX(),
                            "the silo itself counts down at its target");
                    level.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());   // scrubbed
                    LaunchNetwork.of(level).unlink(level, far);                   // and let sleep
                })
                .thenSucceed();
    }

    /** A linked silo broken while loaded is out of contact, and the log says so. */
    private static void lostContact(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = station(helper);
        LaunchSiloBlockEntity silo = LaunchTests.silo(helper, SILO_A);
        LaunchCodesItem.use(level, codes(level, st), silo, false);
        BlockPos at = silo.getBlockPos();
        helper.setBlock(SILO_A, Blocks.AIR);
        helper.succeedWhen(() -> {
            LaunchNetwork.Network n = st.network(level);
            helper.assertTrue(n.member(at) != null && n.member(at).lost(), "out of contact");
            helper.assertTrue(n.log().stream().anyMatch(e -> e.kind() == LaunchNetwork.EventKind.NO_CONTACT), "logged");
        });
    }

    /** New codes: the old card is void (it links nothing), a card taken after works. */
    private static void newCodes(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = station(helper);
        LaunchSiloBlockEntity silo = LaunchTests.silo(helper, SILO_A);
        ItemStack old = codes(level, st);
        helper.runAfterDelay(3, () -> {
            st.newCodes(level);
            LaunchCodesItem.use(level, old, silo, false);
            helper.assertTrue(st.network(level).member(silo.getBlockPos()) == null, "the old card links nothing");
            LaunchCodesItem.use(level, codes(level, st), silo, false);
            helper.assertTrue(st.network(level).member(silo.getBlockPos()) != null, "a new card links it");
            helper.succeed();
        });
    }
}
