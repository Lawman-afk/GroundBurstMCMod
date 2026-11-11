package dev.groundburst.gametest;

import java.util.List;

import dev.groundburst.blast.Blasts;
import dev.groundburst.content.alarm.AlarmBlockEntity;
import dev.groundburst.content.alarm.Alarms;
import dev.groundburst.content.alarm.GBAlarms;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.command.CommandRocket;
import dev.groundburst.content.command.DeadHandBlockEntity;
import dev.groundburst.content.command.GBCommand;
import dev.groundburst.content.command.Seismic;
import dev.groundburst.content.command.SeismographBlockEntity;
import dev.groundburst.content.command.WarRoomDisplayBlockEntity;
import dev.groundburst.content.defence.Interception;
import dev.groundburst.content.effect.EmpFields;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchCodesItem;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.LaunchStationBlockEntity;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The war room (DESIGN.md §17.17): the ground's model of a blast (magnitude from yield, how it fades with distance, an
 * airburst's loss) and the blasts it hears (the mod's own, the game's); the Seismograph recording a blast with what one
 * station can tell, keeping to its setting, raising its alarm, giving its comparator, deaf without power; two seismographs
 * fixing a blast on their network; the War Room Display forming in every facing, shown a network by codes (no member of
 * it), its alert condition rising for a blast near; the Dead Hand armed only by both keys, alerting on a strike in its
 * radius (not its own network's, not one outside), held by HOLD and by redstone, standing down when its command answers,
 * readying its network's launchers to stand by in its alert and standing them down, firing its network when it is silent
 * (the war plan, and return fire) as its command rocket's signal reaches them, at zero, those out of its reach standing
 * down, its rocket's course planned over them, drilling, untouched by an EMP; and a command rocket, harmless, raising no
 * warning (DESIGN.md §17.19). Strikes are placed straight into the ground's record ({@link Seismic#addForTest}).
 * The ground carries every test's blasts to every other, so the tests whose figures any blast would change run alone
 * ({@link GBGameTests#testAlone}); the rest check their own strikes by their own figures.
 */
final class CommandTests {
    private CommandTests() {}

    private static final BlockPos SEIS = new BlockPos(8, 1, 8), DEAD_HAND = new BlockPos(11, 1, 5), SILO = new BlockPos(2, 1, 1);

    static void register() {
        GBGameTests.test("seismic_magnitude_follows_yield_and_distance", GBGameTests.FLOOR_16, 20, CommandTests::model);
        GBGameTests.testOpenSky("seismic_hears_blasts_and_the_games_explosions", GBGameTests.FLOOR_16, 40, CommandTests::hears);
        GBGameTests.testAlone("seismograph_records_a_blast", GBGameTests.FLOOR_16, 120, false, CommandTests::records);
        GBGameTests.test("seismograph_records_what_it_feels_at_its_setting", GBGameTests.FLOOR_16, 60, CommandTests::sensitivity);
        GBGameTests.testAlone("seismograph_raises_a_blast_alert", GBGameTests.FLOOR_16, 60, false, CommandTests::alarm);
        GBGameTests.test("seismograph_without_power_records_nothing", GBGameTests.FLOOR_16, 80, CommandTests::unpowered);
        GBGameTests.test("seismographs_fix_a_blast_on_their_network", GBGameTests.FLOOR_16, 80, CommandTests::fix);
        GBGameTests.test("war_room_display_forms_in_every_facing", GBGameTests.FLOOR_16, 20, CommandTests::warRoomForms);
        GBGameTests.testAlone("war_room_display_shows_a_network_and_its_condition", GBGameTests.FLOOR_16, 120, false, CommandTests::warRoomShows);
        GBGameTests.test("dead_hand_arms_with_both_keys", GBGameTests.FLOOR_16, 120, CommandTests::arms);
        GBGameTests.testAlone("dead_hand_alerts_on_a_strike_and_holds", GBGameTests.FLOOR_16, 160, false, CommandTests::alerts);
        GBGameTests.testAlone("dead_hand_stands_down_when_command_answers", GBGameTests.FLOOR_16, 120, false, CommandTests::answered);
        GBGameTests.testAlone("dead_hand_readies_its_launchers_and_stands_them_down", GBGameTests.FLOOR_16, 160, false, CommandTests::readies);
        GBGameTests.testAlone("dead_hand_fires_its_network_when_command_is_silent", GBGameTests.FLOOR_16, 400, true, h -> fires(h, false));
        GBGameTests.testAlone("dead_hand_returns_fire", GBGameTests.FLOOR_16, 400, true, h -> fires(h, true));
        GBGameTests.testAlone("dead_hand_launchers_fire_as_it_reaches_zero", GBGameTests.FLOOR_16, 900, true, CommandTests::atZero);
        GBGameTests.testAlone("dead_hand_launchers_out_of_its_rockets_reach_stand_down", GBGameTests.FLOOR_16, 300, true, CommandTests::outOfReach);
        GBGameTests.test("command_rocket_plans_its_course_over_the_launchers", GBGameTests.FLOOR_16, 20, CommandTests::course);
        GBGameTests.testAlone("dead_hand_drills_and_shrugs_off_an_emp", GBGameTests.FLOOR_16, 160, false, CommandTests::drillAndEmp);
        GBGameTests.testOpenSky("command_rocket_raises_no_warning", GBGameTests.FLOOR_16, 120, CommandTests::harmless);
    }

    // --- the ground ------------------------------------------------------------------------------------------------------
    /** mb 4.45 for a kiloton, back again from its magnitude; felt 2 less for each tenfold distance past 100, no nearer than 30. */
    private static void model(GameTestHelper helper) {
        helper.assertTrue(Math.abs(Seismic.magnitude(1000) - 4.45) < 0.01, "a kiloton is mb 4.45: " + Seismic.magnitude(1000));
        helper.assertTrue(Math.abs(Seismic.magnitude(1) - 2.2) < 0.01, "a tonne is 2.2: " + Seismic.magnitude(1));
        double back = Seismic.tonnes(Seismic.magnitude(15_000));
        helper.assertTrue(Math.abs(back / 15_000 - 1) < 0.01, "the yield worked back from its magnitude: " + back);
        helper.assertTrue(Math.abs(Seismic.felt(5, 100) - 5) < 1e-4 && Math.abs(Seismic.felt(5, 1000) - 3) < 1e-4, "felt 2 less ten times as far");
        helper.assertTrue(Seismic.felt(5, 10) == Seismic.felt(5, 30), "no stronger nearer than 30 blocks");
        helper.assertTrue(Math.abs(Seismic.reach(5, 3) - 1000) < 0.5, "felt at 3 out to 1000 blocks: " + Seismic.reach(5, 3));
        helper.succeed();
    }

    /**
     * A profile blast and the game's own explosion each go into the ground's record where they went off (the explosion
     * as TNT of its radius: mb 1.08 for 4), and the same explosion high over the ground a magnitude and a half less.
     */
    private static void hears(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        Seismic seismic = Seismic.of(level);
        long before = seismic.latest();
        Vec3 blast = helper.absoluteVec(new Vec3(4.5, 1.5, 4.5));
        Blasts.detonate(level, blast, Blasts.profile("test/small"), null);
        Seismic.Event b = at(seismic, before, blast);
        helper.assertTrue(b != null && b.magnitude() < 3 && b.duration() == 0, "the blast is in the record: " + b);
        Vec3 ground = helper.absoluteVec(new Vec3(11.5, 1.5, 11.5)), high = helper.absoluteVec(new Vec3(11.5, 13.5, 4.5));
        level.explode(null, ground.x, ground.y, ground.z, 4f, Level.ExplosionInteraction.NONE);
        level.explode(null, high.x, high.y, high.z, 4f, Level.ExplosionInteraction.NONE);
        Seismic.Event g = at(seismic, before, ground), a = at(seismic, before, high);
        float tnt = Seismic.magnitude(0.0005 * 64);
        helper.assertTrue(g != null && Math.abs(g.magnitude() - tnt) < 0.01, "the game's explosion, as TNT: " + g + " vs " + tnt);
        helper.assertTrue(a != null && Math.abs(a.magnitude() - (tnt - 1.5f)) < 0.01, "an airburst, 1.5 less: " + a);
        helper.succeed();
    }

    private static Seismic.Event at(Seismic seismic, long after, Vec3 p) {
        for (Seismic.Event e : seismic.after(after)) {
            if (Math.abs(e.x() - p.x) < 0.01 && Math.abs(e.y() - p.y) < 0.01 && Math.abs(e.z() - p.z) < 0.01) {
                return e;
            }
        }
        return null;
    }

    // --- the Seismograph -------------------------------------------------------------------------------------------------
    private static SeismographBlockEntity seismograph(GameTestHelper helper, BlockPos rel, boolean power) {
        helper.setBlock(rel, GBCommand.SEISMOGRAPH.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        SeismographBlockEntity s = helper.getBlockEntity(rel, SeismographBlockEntity.class);
        if (power) {
            s.energy().generate(SeismographBlockEntity.ENERGY_CAPACITY);
        }
        return s;
    }

    private static SeismographBlockEntity.Record record(SeismographBlockEntity s, long event) {
        for (SeismographBlockEntity.Record r : s.records()) {
            if (r.event() == event) {
                return r;
            }
        }
        return null;
    }

    /** {@code dx}, {@code dz} blocks from the middle of {@code rel}'s block, at its height. */
    private static Vec3 off(GameTestHelper helper, BlockPos rel, double dx, double dz) {
        return helper.absoluteVec(Vec3.atCenterOf(rel)).add(dx, 0, dz);
    }

    /**
     * A blast of mb 3.5 240 blocks to the north-east: recorded with its magnitude, distance and bearing to within what a
     * station feeling it that hard can tell, the S wave after the P wave, the yield worked back; its comparator lit for
     * two seconds.
     */
    private static void records(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        SeismographBlockEntity s = seismograph(helper, SEIS, true);
        Seismic.Event[] e = new Seismic.Event[1];
        helper.startSequence()
                .thenExecuteAfter(2, () -> e[0] = Seismic.of(level).addForTest(level, off(helper, SEIS, 170, -170), 3.5f, 0, null))
                .thenWaitUntil(() -> helper.assertTrue(record(s, e[0].id()) != null, "recorded"))
                .thenExecute(() -> {
                    SeismographBlockEntity.Record r = record(s, e[0].id());
                    helper.assertTrue(Math.abs(r.magnitude() - 3.5f) <= 0.3f, "its magnitude: " + r.magnitude());
                    helper.assertTrue(Math.abs(r.distance() - 240) <= 0.15 * 240, "its distance: " + r.distance());
                    double db = Math.abs(((r.bearing() - 45) % 360 + 540) % 360 - 180);
                    helper.assertTrue(db <= 14, "its bearing (north-east): " + r.bearing());
                    helper.assertTrue(r.second() > r.arrival(), "the S wave after the P wave: " + r.arrival() + ", " + r.second());
                    double t = r.tonnes();
                    helper.assertTrue(t > Seismic.tonnes(3.2f) && t < Seismic.tonnes(3.8f), "the yield worked back: " + t);
                    helper.assertTrue(s.comparator() == Math.clamp(Math.round(r.magnitude() * 2), 1, 15), "its comparator: " + s.comparator());
                })
                .thenExecuteAfter(SeismographBlockEntity.COMPARATOR_TICKS + 2, () ->
                        helper.assertTrue(s.comparator() == 0, "its comparator dark again: " + s.comparator()))
                .thenSucceed();
    }

    /** Set to record what it feels at 3 and up: mb 3 400 blocks off (felt 1.8) passes it by, mb 5 there (3.8) is recorded. */
    private static void sensitivity(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        SeismographBlockEntity s = seismograph(helper, SEIS, true);
        s.applySettings(level, 3, 0, 9211);
        Seismic.Event[] faint = new Seismic.Event[1], strong = new Seismic.Event[1];
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    faint[0] = Seismic.of(level).addForTest(level, off(helper, SEIS, 0, -400), 3.0f, 0, null);
                    strong[0] = Seismic.of(level).addForTest(level, off(helper, SEIS, 400, 0), 5.0f, 0, null);
                })
                .thenWaitUntil(() -> helper.assertTrue(record(s, strong[0].id()) != null, "the strong one recorded"))
                .thenExecute(() -> helper.assertTrue(record(s, faint[0].id()) == null, "the faint one passed by"))
                .thenSucceed();
    }

    /** Its alarm set at 3: an mb 3.6 blast 150 blocks off raises a Blast alert on its channel, with the blast's figures. */
    private static void alarm(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        SeismographBlockEntity s = seismograph(helper, SEIS, true);
        s.applySettings(level, SeismographBlockEntity.DEFAULT_SENSITIVITY, 2, 9212);
        helper.startSequence()
                .thenExecuteAfter(2, () -> Seismic.of(level).addForTest(level, off(helper, SEIS, -150, 0), 3.6f, 0, null))
                .thenWaitUntil(() -> {
                    Alarms.Alert a = Alarms.status(level, 9212);
                    helper.assertTrue(a != null && a.kind() == Alarms.Kind.BLAST, "a Blast alert on its channel: " + a);
                    helper.assertTrue(Math.abs(a.reading() - 3.6f) <= 0.3f && Math.abs(a.count() - 150) <= 25, "its magnitude and distance: " + a);
                })
                .thenExecute(() -> {
                    s.applySettings(level, SeismographBlockEntity.DEFAULT_SENSITIVITY, 0, 9212);       // alarm off: lowered
                    helper.assertTrue(Alarms.status(level, 9212) == null, "lowered when its alarm is turned off");
                })
                .thenSucceed();
    }

    /** No power: what arrives passes unrecorded (and is not recorded once power is back); what comes after is. */
    private static void unpowered(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        SeismographBlockEntity s = seismograph(helper, SEIS, false);
        Seismic.Event[] dark = new Seismic.Event[1], lit = new Seismic.Event[1];
        helper.startSequence()
                .thenExecuteAfter(2, () -> dark[0] = Seismic.of(level).addForTest(level, off(helper, SEIS, 100, 0), 4.0f, 0, null))
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(!s.powered() && record(s, dark[0].id()) == null, "dark, it records nothing");
                    s.energy().generate(SeismographBlockEntity.ENERGY_CAPACITY);
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(s.powered() && record(s, dark[0].id()) == null, "what came while it was dark stays unrecorded");
                    lit[0] = Seismic.of(level).addForTest(level, off(helper, SEIS, 0, 100), 4.1f, 0, null);
                })
                .thenWaitUntil(() -> helper.assertTrue(record(s, lit[0].id()) != null, "powered, it records again"))
                .thenSucceed();
    }

    /** Two seismographs on a network both feel a blast: the network fixes it where it was, and logs it. */
    private static void fix(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = StationTests.station(helper);
        SeismographBlockEntity a = seismograph(helper, new BlockPos(1, 1, 1), true), b = seismograph(helper, new BlockPos(14, 1, 1), true);
        ItemStack card = codes(level, st);
        LaunchCodesItem.use(level, card, a, false);
        LaunchCodesItem.use(level, card, b, false);
        LaunchNetwork.Network n = st.network(level);
        helper.assertTrue(n.member(a.getBlockPos()) != null && n.member(a.getBlockPos()).role == LaunchNetwork.Role.SEISMOGRAPH,
                "linked as a seismograph");
        Seismic.Event[] e = new Seismic.Event[1];
        helper.startSequence()
                .thenExecuteAfter(2, () -> e[0] = Seismic.of(level).addForTest(level, off(helper, SEIS, 120, -380), 4.0f, 0, null))
                .thenWaitUntil(() -> {
                    LaunchNetwork.Blast blast = n.blasts().stream().filter(x -> x.event == e[0].id()).findFirst().orElse(null);
                    helper.assertTrue(blast != null && blast.fixed(), "fixed by two: " + blast);
                    helper.assertTrue(Math.abs(blast.x() - e[0].x()) < 1 && Math.abs(blast.z() - e[0].z()) < 1, "where it was");
                    helper.assertTrue(n.log().stream().anyMatch(x -> x.kind() == LaunchNetwork.EventKind.BLAST), "logged");
                })
                .thenSucceed();
    }

    private static ItemStack codes(ServerLevel level, LaunchStationBlockEntity station) {
        ItemStack card = new ItemStack(GBLaunch.LAUNCH_CODES.get());
        LaunchCodesItem.use(level, card, station, false);
        return card;
    }

    // --- the War Room Display --------------------------------------------------------------------------------------------
    /** A multiblock built facing {@code facing} (its controller at {@code rel}). */
    private static void build(GameTestHelper helper, BlockPos rel, Block controller, MultiblockDefinition def, Direction facing) {
        helper.setBlock(rel, controller.defaultBlockState().setValue(MachineBlock.FACING, facing));
        for (MultiblockDefinition.Cell cell : def.cells(helper.absolutePos(rel), facing)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                helper.getLevel().setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
            }
        }
    }

    /** Seven wide, five high, one deep: it forms facing east, west and south as it does north. */
    private static void warRoomForms(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        record Placed(BlockPos rel, Direction facing) {}
        for (Placed p : List.of(new Placed(new BlockPos(3, 1, 8), Direction.EAST), new Placed(new BlockPos(12, 1, 8), Direction.WEST),
                new Placed(new BlockPos(8, 1, 13), Direction.SOUTH), new Placed(new BlockPos(8, 1, 2), Direction.NORTH))) {
            build(helper, p.rel(), GBCommand.WAR_ROOM_CONTROLLER.block().get(), GBCommand.WAR_ROOM, p.facing());
            WarRoomDisplayBlockEntity d = (WarRoomDisplayBlockEntity) level.getBlockEntity(helper.absolutePos(p.rel()));
            d.revalidate();
            helper.assertTrue(d.isFormed(), "formed facing " + p.facing());
        }
        helper.succeed();
    }

    /**
     * Codes used on it (or on a panel of it) show their network on it, without making it a member; sneaking shows none.
     * Shown a network, a blast its seismograph feels near raises its alert condition to 1 (ATTACK).
     */
    private static void warRoomShows(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = StationTests.station(helper);
        BlockPos rel = new BlockPos(8, 1, 3);
        LaunchTests.build(helper, rel, GBCommand.WAR_ROOM_CONTROLLER.block().get(), GBCommand.WAR_ROOM);
        WarRoomDisplayBlockEntity d = (WarRoomDisplayBlockEntity) level.getBlockEntity(helper.absolutePos(rel));
        d.revalidate();
        d.energy().generate(WarRoomDisplayBlockEntity.ENERGY_CAPACITY);
        helper.assertTrue(d.isFormed(), "formed");
        ItemStack card = codes(level, st);
        LaunchNetwork.Network n = st.network(level);
        var viaPanel = LaunchCodesItem.target(level, helper.absolutePos(rel.above()));
        helper.assertTrue(viaPanel == d, "a panel reaches the display: " + viaPanel);
        LaunchCodesItem.use(level, card, d, true);
        helper.assertTrue(d.shownNetwork() == 0, "sneaking shows none");
        LaunchCodesItem.use(level, card, viaPanel, false);
        helper.assertTrue(d.shownNetwork() == n.id, "it shows the network");
        helper.assertTrue(n.member(d.getBlockPos()) == null, "and is no member of it");
        SeismographBlockEntity s = seismograph(helper, new BlockPos(1, 1, 12), true);
        LaunchCodesItem.use(level, card, s, false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(d.condition() == 5 && d.shownPowered(), "lit at 5, NORMAL: " + d.condition()))
                .thenExecute(() -> Seismic.of(level).addForTest(level, off(helper, rel, 90, -60), 4.2f, 0, null))
                .thenWaitUntil(() -> helper.assertTrue(d.condition() == 1, "a blast near: 1, ATTACK: " + d.condition()))
                .thenSucceed();
    }

    // --- the Dead Hand ---------------------------------------------------------------------------------------------------
    /** A Dead Hand built, powered and loaded, on the station's network (not armed). */
    private static DeadHandBlockEntity deadHand(GameTestHelper helper, LaunchStationBlockEntity st) {
        ServerLevel level = helper.getLevel();
        LaunchTests.build(helper, DEAD_HAND, GBCommand.DEAD_HAND_CONTROLLER.block().get(), GBCommand.DEAD_HAND);
        DeadHandBlockEntity dh = (DeadHandBlockEntity) level.getBlockEntity(helper.absolutePos(DEAD_HAND));
        dh.revalidate();
        helper.assertTrue(dh.isFormed(), "the Dead Hand formed");
        dh.energy().generate(DeadHandBlockEntity.ENERGY_CAPACITY);
        dh.load(level);
        LaunchCodesItem.use(level, codes(level, st), dh, false);
        return dh;
    }

    /** Both keys turned (ARM twice, within the time). */
    private static void arm(ServerLevel level, DeadHandBlockEntity dh) {
        dh.order(level, DeadHandBlockEntity.ARM, 0);
        dh.order(level, DeadHandBlockEntity.ARM, 0);
    }

    private static boolean alerted(DeadHandBlockEntity dh, float m) {
        return dh.log().stream().anyMatch(e -> e.line() == DeadHandBlockEntity.Line.ALERT && e.a() == Math.round(m * 10));
    }

    /**
     * It will not arm without its rocket or its network; ARM once is one key (still SAFE); a second ARM after the first has
     * lapsed is a first key again; two in time arm it, logged on the network.
     */
    private static void arms(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = StationTests.station(helper);
        LaunchTests.build(helper, DEAD_HAND, GBCommand.DEAD_HAND_CONTROLLER.block().get(), GBCommand.DEAD_HAND);
        DeadHandBlockEntity dh = (DeadHandBlockEntity) level.getBlockEntity(helper.absolutePos(DEAD_HAND));
        dh.revalidate();
        dh.energy().generate(DeadHandBlockEntity.ENERGY_CAPACITY);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue("rocket".equals(dh.cannotArm(level)), "no rocket: " + dh.cannotArm(level));
                    helper.assertTrue(dh.load(level) && !dh.load(level), "a rocket loads, and only one");
                    helper.assertTrue("network".equals(dh.cannotArm(level)), "no network: " + dh.cannotArm(level));
                    LaunchCodesItem.use(level, codes(level, st), dh, false);
                    LaunchNetwork.Member m = st.network(level).member(dh.getBlockPos());
                    helper.assertTrue(m != null && m.role == LaunchNetwork.Role.DEAD_HAND, "linked as the Dead Hand");
                    helper.assertTrue(dh.cannotArm(level) == null, "it can be armed");
                    dh.order(level, DeadHandBlockEntity.ARM, 0);
                    helper.assertTrue(dh.state() == DeadHandBlockEntity.State.SAFE, "one key: still safe");
                })
                .thenExecuteAfter(DeadHandBlockEntity.CONFIRM + 5, () -> {
                    dh.order(level, DeadHandBlockEntity.ARM, 0);
                    helper.assertTrue(dh.state() == DeadHandBlockEntity.State.SAFE, "the first key lapsed: this is a first key again");
                    dh.order(level, DeadHandBlockEntity.ARM, 0);
                    helper.assertTrue(dh.state() == DeadHandBlockEntity.State.WATCHING, "both keys: armed");
                    helper.assertTrue(st.network(level).log().stream().anyMatch(e -> e.kind() == LaunchNetwork.EventKind.DEAD_HAND_ARMED),
                            "the network logged it");
                })
                .thenSucceed();
    }

    /**
     * Armed (radius 128, strikes of mb 5 and up): its own network's strike and one outside its radius pass; another's
     * inside puts it in alert with a Blast alert on its channel; HOLD stands it down; a second strike, then a redstone
     * signal into one of its blocks, does the same.
     */
    private static void alerts(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = StationTests.station(helper);
        DeadHandBlockEntity dh = deadHand(helper, st);
        dh.order(level, DeadHandBlockEntity.SET_RADIUS, 0);
        dh.order(level, DeadHandBlockEntity.SET_THRESHOLD, 2);
        dh.order(level, DeadHandBlockEntity.SET_CHANNEL, 9221);
        long own = st.network(level).id;
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    arm(level, dh);
                    helper.assertTrue(dh.state() == DeadHandBlockEntity.State.WATCHING, "armed");
                    Seismic.of(level).addForTest(level, off(helper, DEAD_HAND, 60, 0), 5.4f, own, null);
                    Seismic.of(level).addForTest(level, off(helper, DEAD_HAND, 0, -300), 5.3f, 0, null);
                })
                .thenExecuteAfter(6, () -> {
                    helper.assertTrue(!alerted(dh, 5.4f), "its own network's strike passes");
                    helper.assertTrue(!alerted(dh, 5.3f), "a strike outside its radius passes");
                    Seismic.of(level).addForTest(level, off(helper, DEAD_HAND, -70, 0), 5.6f, 0, null);
                })
                .thenWaitUntil(() -> helper.assertTrue(dh.state() == DeadHandBlockEntity.State.ALERT, "in alert: " + dh.state()))
                .thenExecute(() -> {
                    Alarms.Alert a = Alarms.status(level, 9221);
                    helper.assertTrue(a != null && a.kind() == Alarms.Kind.BLAST, "a Blast alert on its channel: " + a);
                    helper.assertTrue(dh.alertUntil() > level.getGameTime(), "counting down");
                    dh.order(level, DeadHandBlockEntity.HOLD, 0);
                    helper.assertTrue(dh.state() == DeadHandBlockEntity.State.WATCHING, "HOLD: watching again");
                    helper.assertTrue(Alarms.status(level, 9221) == null, "its alarm lowered");
                    Seismic.of(level).addForTest(level, off(helper, DEAD_HAND, 0, 80), 5.7f, 0, null);
                })
                .thenWaitUntil(() -> helper.assertTrue(dh.state() == DeadHandBlockEntity.State.ALERT, "in alert again: " + dh.state()))
                .thenExecute(() -> helper.setBlock(DEAD_HAND.offset(1, 0, -1), Blocks.REDSTONE_BLOCK))     // against its front's corner
                .thenWaitUntil(() -> helper.assertTrue(dh.state() == DeadHandBlockEntity.State.WATCHING, "held by redstone: " + dh.state()))
                .thenSucceed();
    }

    /** In alert at zero, a station of its network answers: the command lives, so it stands down by itself. */
    private static void answered(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = StationTests.station(helper);
        DeadHandBlockEntity dh = deadHand(helper, st);
        dh.order(level, DeadHandBlockEntity.SET_RADIUS, 0);
        dh.order(level, DeadHandBlockEntity.SET_THRESHOLD, 2);
        dh.order(level, DeadHandBlockEntity.SET_CHANNEL, 9222);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    arm(level, dh);
                    Seismic.of(level).addForTest(level, off(helper, DEAD_HAND, 50, 50), 5.5f, 0, null);
                })
                .thenWaitUntil(() -> helper.assertTrue(dh.state() == DeadHandBlockEntity.State.ALERT, "in alert: " + dh.state()))
                .thenExecute(() -> {
                    helper.assertTrue(st.powered(), "the station is working");
                    dh.hurryForTest(level);
                })
                .thenWaitUntil(() -> helper.assertTrue(dh.state() == DeadHandBlockEntity.State.WATCHING, "stood down: " + dh.state()))
                .thenExecute(() -> {
                    helper.assertTrue(dh.log().stream().anyMatch(e -> e.line() == DeadHandBlockEntity.Line.ANSWERED), "its log: the command answered");
                    helper.assertTrue(dh.rocket(), "its rocket kept");
                    helper.assertTrue(st.network(level).log().stream().anyMatch(e -> e.kind() == LaunchNetwork.EventKind.DEAD_HAND_STOOD_DOWN),
                            "the network logged it");
                })
                .thenSucceed();
    }

    /** A firing test's network: a station, the Dead Hand (radius 128, strikes of mb 5 and up), a silo loaded, powered, linked. */
    private record Setup(LaunchStationBlockEntity st, DeadHandBlockEntity dh, LaunchSiloBlockEntity silo, LaunchNetwork.Network n,
            BlockPos plan, BlockPos from) {}

    private static Setup setup(GameTestHelper helper, boolean returnFire, int channel) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = StationTests.station(helper);
        DeadHandBlockEntity dh = deadHand(helper, st);
        dh.order(level, DeadHandBlockEntity.SET_RADIUS, 0);
        dh.order(level, DeadHandBlockEntity.SET_THRESHOLD, 2);
        dh.order(level, DeadHandBlockEntity.SET_CHANNEL, channel);
        dh.order(level, DeadHandBlockEntity.SET_TARGETS, returnFire ? 1 : 0);
        LaunchSiloBlockEntity silo = LaunchTests.silo(helper, SILO);
        silo.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile());
        silo.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        LaunchCodesItem.use(level, codes(level, st), silo, false);
        return new Setup(st, dh, silo, st.network(level), silo.getBlockPos().offset(300, 0, 40), silo.getBlockPos().offset(-420, 0, 260));
    }

    /** The silo aimed on its war plan, the Dead Hand armed and (where {@code alone}) its station gone: no command answers. */
    private static void ready(GameTestHelper helper, Setup s, boolean alone) {
        ServerLevel level = helper.getLevel();
        s.st().aim(level, List.of(s.silo().getBlockPos()), s.plan().getX(), s.plan().getZ(), LaunchNetwork.Pattern.POINT, 0);
        helper.assertTrue(s.silo().target() != null && !s.silo().armed(), "aimed (the war plan), not armed");
        arm(level, s.dh());
        helper.assertTrue(s.dh().state() == DeadHandBlockEntity.State.WATCHING, "the Dead Hand armed");
        if (alone) {
            level.setBlockAndUpdate(s.st().getBlockPos(), Blocks.AIR.defaultBlockState());     // the command post is gone
        }
    }

    /** A strike of mb 5.8 off the Dead Hand, launched from {@code from}. */
    private static void strike(GameTestHelper helper, Setup s) {
        Seismic.of(helper.getLevel()).addForTest(helper.getLevel(), off(helper, DEAD_HAND, -40, 60), 5.8f, 0, Vec3.atCenterOf(s.from()));
    }

    private static boolean logged(LaunchNetwork.Network n, LaunchNetwork.EventKind kind, java.util.function.IntPredicate detail) {
        return n.log().stream().anyMatch(e -> e.kind() == kind && detail.test(e.detail()));
    }

    /**
     * A strike puts it in alert and readies the network's silo to stand by (DESIGN.md §17.19): armed at its war plan, its
     * launch ordered to stand by so that its countdown is over by zero; HOLD stands it down again: its launch called off,
     * made safe.
     */
    private static void readies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup s = setup(helper, false, 9227);
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    ready(helper, s, false);
                    strike(helper, s);
                })
                .thenWaitUntil(() -> helper.assertTrue(s.dh().state() == DeadHandBlockEntity.State.ALERT, "in alert: " + s.dh().state()))
                .thenExecute(() -> {
                    LaunchNetwork.Member m = s.n().member(s.silo().getBlockPos());
                    helper.assertTrue(m != null && s.dh().getBlockPos().equals(m.standingBy()), "the silo stands by for it");
                    helper.assertTrue(m.orders().hold() == LaunchNetwork.STAND_BY && m.orders().launchAt() >= level.getGameTime(),
                            "its launch ordered, to stand by: " + m.orders());
                    long due = s.dh().alertUntil() - m.report().timing().delay();
                    helper.assertTrue(m.orders().launchAt() <= due, "so its countdown is over by zero: " + m.orders().launchAt() + " <= " + due);
                    BlockPos t = s.silo().target();
                    helper.assertTrue(s.silo().armed() && t != null && t.getX() == s.plan().getX() && t.getZ() == s.plan().getZ(),
                            "armed at its war plan: " + t);
                    helper.assertTrue(LaunchNetwork.of(level).standingFor(s.n().id, s.dh().getBlockPos()) == 1, "one launcher standing by");
                    s.dh().order(level, DeadHandBlockEntity.HOLD, 0);
                    helper.assertTrue(s.dh().state() == DeadHandBlockEntity.State.WATCHING, "HOLD: watching again");
                    helper.assertTrue(m.standingBy() == null && m.orders().launchAt() < 0, "its launcher stood down: " + m.orders());
                    helper.assertTrue(!s.silo().armed() && s.silo().countdown() < 0 && !s.silo().standingBy(), "made safe, nothing counting");
                })
                .thenSucceed();
    }

    /**
     * Its station destroyed, a strike, and at zero no command answers: the command rocket (harmless) leaves its tube and its
     * signal reaches the silo beside it at once: its launch (not yet begun, the alert cut short) starts at once with nothing
     * to wait for, at its own aim (the war plan) or, set to return fire, back at where the strike's missile came from. The
     * rocket down, it is spent; the logs say how many stood by and how many it reached.
     */
    private static void fires(GameTestHelper helper, boolean returnFire) {
        ServerLevel level = helper.getLevel();
        Setup s = setup(helper, returnFire, returnFire ? 9224 : 9223);
        helper.startSequence()
                .thenExecuteAfter(3, () -> ready(helper, s, true))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(!s.n().stations().contains(s.st().getBlockPos()) && LaunchNetwork.of(level).network(s.n().id) != null,
                            "the network lives on without its station");
                    strike(helper, s);
                })
                .thenWaitUntil(() -> helper.assertTrue(s.dh().state() == DeadHandBlockEntity.State.ALERT, "in alert: " + s.dh().state()))
                .thenExecute(() -> {
                    BlockPos want = returnFire ? s.from() : s.plan();
                    BlockPos t = s.silo().target();
                    helper.assertTrue(t != null && t.getX() == want.getX() && t.getZ() == want.getZ(),
                            (returnFire ? "aimed back where the strike came from: " : "at its own aim: ") + t);
                    s.dh().hurryForTest(level);
                })
                .thenWaitUntil(() -> helper.assertTrue(s.dh().state() == DeadHandBlockEntity.State.FIRING, "no answer: firing: " + s.dh().state()))
                .thenExecute(() -> {
                    helper.assertTrue(!s.dh().rocket(), "the rocket away");
                    Flights.Flight rocket = Flights.of(level).all().stream().filter(f -> f.id().equals(s.dh().rocketFlight())).findFirst().orElse(null);
                    helper.assertTrue(rocket != null && rocket.harmless() && rocket.start().distanceTo(helper.absoluteVec(Vec3.atCenterOf(DEAD_HAND))) < 4,
                            "the command rocket flies from its tube, harmless");
                    LaunchNetwork.Member m = s.n().member(s.silo().getBlockPos());
                    helper.assertTrue(m != null && m.standingBy() == null, "its signal reached the silo");
                    helper.assertTrue(s.silo().countdown() >= 0 && !s.silo().standingBy(), "the silo launching, nothing to wait for: " + s.silo().countdown());
                    helper.assertTrue(s.dh().sent() == 1, "one launcher has the order: " + s.dh().sent());
                    helper.assertTrue(s.dh().log().stream().anyMatch(e -> e.line() == DeadHandBlockEntity.Line.FIRED && e.b() == 1), "one stood by");
                    helper.assertTrue(logged(s.n(), LaunchNetwork.EventKind.DEAD_HAND_FIRED, d -> d == 1), "the network logged it");
                    Flights.of(level).cancelForTest(level, rocket);
                })
                .thenWaitUntil(() -> helper.assertTrue(s.dh().state() == DeadHandBlockEntity.State.SPENT, "the rocket down: spent: " + s.dh().state()))
                .thenWaitUntil(() -> helper.assertTrue(logged(s.n(), LaunchNetwork.EventKind.DEAD_HAND_ROCKET_DOWN, d -> d == 1),
                        "the network logged the rocket down, one reached"))
                .thenExecute(() -> {
                    helper.assertTrue(s.dh().log().stream().anyMatch(e -> e.line() == DeadHandBlockEntity.Line.ROCKET_DOWN && e.b() == 1), "its own log too");
                    helper.setBlock(SILO, Blocks.AIR);                                      // scrubbed: no missile left flying
                })
                .thenSucceed();
    }

    /**
     * The whole of it, untouched (DESIGN.md §17.19): in the alert the silo's countdown runs and holds at T-1 before zero, its
     * missile on the pad; at zero the rocket leaves its tube and the silo's missile is lit within a few ticks.
     */
    private static void atZero(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup s = setup(helper, false, 9228);
        long[] zero = {-1};
        java.util.function.Predicate<Flights.Flight> ours = f -> Math.hypot(f.target().x - (s.plan().getX() + 0.5), f.target().z - (s.plan().getZ() + 0.5)) < 4;
        helper.startSequence()
                .thenExecuteAfter(3, () -> ready(helper, s, true))
                .thenExecuteAfter(2, () -> strike(helper, s))
                .thenWaitUntil(() -> helper.assertTrue(s.dh().state() == DeadHandBlockEntity.State.ALERT, "in alert: " + s.dh().state()))
                .thenExecute(() -> zero[0] = s.dh().alertUntil())
                .thenWaitUntil(() -> helper.assertTrue(s.silo().standingBy() && s.silo().countdown() == 1, "standing by at T-1: " + s.silo().countdown()))
                .thenExecute(() -> {
                    helper.assertTrue(level.getGameTime() < zero[0], "before zero: " + (zero[0] - level.getGameTime()) + " ticks to go");
                    helper.assertTrue(!s.silo().inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty(), "its missile still on the pad");
                })
                .thenWaitUntil(() -> helper.assertTrue(s.dh().state() == DeadHandBlockEntity.State.FIRING, "zero: firing: " + s.dh().state()))
                .thenWaitUntil(() -> helper.assertTrue(s.silo().inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty(), "the silo's missile lit"))
                .thenExecute(() -> {
                    long late = level.getGameTime() - zero[0];
                    helper.assertTrue(late <= 3, "lit as the Dead Hand reached zero: " + late + " ticks after");
                    helper.assertTrue(Flights.of(level).all().stream().anyMatch(ours), "on its way to its war plan");
                    Flights.of(level).all().stream().filter(f -> ours.test(f) || f.id().equals(s.dh().rocketFlight()))
                            .forEach(f -> Flights.of(level).cancelForTest(level, f));
                })
                .thenSucceed();
    }

    /**
     * A launcher the rocket's signal never reaches (its reach cut to nothing for the test) stands down when the rocket is
     * down: its launch called off, made safe, the log saying so.
     */
    private static void outOfReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup s = setup(helper, false, 9229);
        CommandRocket.REACH_FOR_TEST.put(s.dh().getBlockPos(), 0.5);
        helper.startSequence()
                .thenExecuteAfter(3, () -> ready(helper, s, true))
                .thenExecuteAfter(2, () -> strike(helper, s))
                .thenWaitUntil(() -> helper.assertTrue(s.dh().state() == DeadHandBlockEntity.State.ALERT, "in alert: " + s.dh().state()))
                .thenExecute(() -> s.dh().hurryForTest(level))
                .thenWaitUntil(() -> helper.assertTrue(s.dh().state() == DeadHandBlockEntity.State.FIRING, "firing: " + s.dh().state()))
                .thenExecute(() -> {
                    LaunchNetwork.Member m = s.n().member(s.silo().getBlockPos());
                    helper.assertTrue(m != null && s.dh().getBlockPos().equals(m.standingBy()), "out of its reach: still standing by");
                    Flights.of(level).all().stream().filter(f -> f.id().equals(s.dh().rocketFlight())).forEach(f -> Flights.of(level).cancelForTest(level, f));
                })
                .thenWaitUntil(() -> helper.assertTrue(s.n().member(s.silo().getBlockPos()).standingBy() == null, "the rocket down: stood down"))
                .thenExecute(() -> {
                    helper.assertTrue(logged(s.n(), LaunchNetwork.EventKind.FAILED, d -> d == LaunchNetwork.OUT_OF_REACH), "logged: out of its reach");
                    helper.assertTrue(logged(s.n(), LaunchNetwork.EventKind.DEAD_HAND_ROCKET_DOWN, d -> d == 0), "and the rocket down, none reached");
                    helper.assertTrue(!s.silo().armed() && s.silo().countdown() < 0, "made safe, nothing counting: " + s.silo().countdown());
                    CommandRocket.REACH_FOR_TEST.remove(s.dh().getBlockPos());
                })
                .thenSucceed();
    }

    /**
     * Its signal: 256 blocks round it at lift-off, a block more for each block it climbs, 480 at most. Its course: east over
     * two launchers 600 and 900 blocks off (one 120 to the side), reaching them and the one beside it; with one more far to
     * the west, still east (three of four); none beyond its first reach, out over them at its shortest; none at all, away
     * the way it is told.
     */
    private static void course(GameTestHelper helper) {
        helper.assertTrue(CommandRocket.reach(0) == 256 && CommandRocket.reach(100) == 356 && CommandRocket.reach(1000) == 480,
                "its reach: " + CommandRocket.reach(0) + ", " + CommandRocket.reach(100) + ", " + CommandRocket.reach(1000));
        Vec3 pad = new Vec3(0.5, 64, 0.5);
        BlockPos near = new BlockPos(-40, 64, 10), a = new BlockPos(600, 64, 0), b = new BlockPos(900, 64, 120), west = new BlockPos(-1500, 64, 0);
        CommandRocket.Course c = CommandRocket.plan(pad, List.of(near, a, b), Direction.SOUTH);
        helper.assertTrue(c.reached().size() == 3 && c.reached().getFirst().equals(near), "all three, the one beside it first: " + c.reached());
        helper.assertTrue(c.target().x > 900 && Math.abs(c.target().z - pad.z) < 300, "out east past the farthest: " + c.target());
        CommandRocket.Course d = CommandRocket.plan(pad, List.of(near, a, b, west), Direction.SOUTH);
        helper.assertTrue(d.reached().size() == 3 && !d.reached().contains(west) && d.of() == 4, "the way that reaches the most: " + d.reached());
        CommandRocket.Course e = CommandRocket.plan(pad, List.of(near), Direction.SOUTH);
        double out = Math.hypot(e.target().x - pad.x, e.target().z - pad.z);
        helper.assertTrue(Math.abs(out - CommandRocket.SHORTEST) < 1 && e.target().x < pad.x, "out over it at its shortest: " + e.target());
        CommandRocket.Course f = CommandRocket.plan(pad, List.of(), Direction.SOUTH);
        helper.assertTrue(f.target().z > pad.z + 250 && f.reached().isEmpty(), "none at all: away south: " + f.target());
        helper.succeed();
    }

    /**
     * DRILL runs an alert (an Alarm on its channel, not a Blast) that ends with nothing fired; in an EMP field it keeps
     * its power and its watch, while a seismograph beside it goes dark.
     */
    private static void drillAndEmp(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = StationTests.station(helper);
        DeadHandBlockEntity dh = deadHand(helper, st);
        dh.order(level, DeadHandBlockEntity.SET_RADIUS, 0);
        dh.order(level, DeadHandBlockEntity.SET_THRESHOLD, 2);
        dh.order(level, DeadHandBlockEntity.SET_CHANNEL, 9225);
        SeismographBlockEntity s = seismograph(helper, new BlockPos(14, 1, 9), true);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    arm(level, dh);
                    dh.order(level, DeadHandBlockEntity.DRILL, 0);
                    helper.assertTrue(dh.state() == DeadHandBlockEntity.State.ALERT && dh.drill(), "a drill: in alert");
                    Alarms.Alert a = Alarms.status(level, 9225);
                    helper.assertTrue(a != null && a.kind() == Alarms.Kind.ALARM, "an Alarm on its channel: " + a);
                    dh.hurryForTest(level);
                })
                .thenWaitUntil(() -> helper.assertTrue(dh.state() == DeadHandBlockEntity.State.WATCHING, "the drill over: watching"))
                .thenExecute(() -> {
                    helper.assertTrue(dh.rocket() && dh.log().stream().anyMatch(e -> e.line() == DeadHandBlockEntity.Line.DRILL_ENDED),
                            "nothing fired, the drill logged");
                    EmpFields.add(level, helper.absoluteVec(new Vec3(12.5, 2, 7.5)), 6, 200, null);
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(!s.powered(), "the seismograph in the field goes dark");
                    helper.assertTrue(dh.powered() && dh.state() == DeadHandBlockEntity.State.WATCHING, "the Dead Hand keeps watching");
                })
                .thenSucceed();
    }

    /**
     * A command rocket (no warhead) bound for a spot beside a Missile Warning Receiver, tracked by radar: no warning.
     */
    private static void harmless(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(4, 1, 8), GBAlarms.RECEIVER.block().get().defaultBlockState());
        AlarmBlockEntity receiver = helper.getBlockEntity(new BlockPos(4, 1, 8), AlarmBlockEntity.class);
        receiver.applySettings(9226, new int[] {0, 0});
        BlockPos radar = helper.absolutePos(new BlockPos(8, 1, 1));
        helper.onEachTick(() -> Interception.radarWorking(level, radar, radar));
        Vec3 target = helper.absoluteVec(new Vec3(6.5, 1, 8.5));
        Flights.Flight f = Flights.launch(level, target.add(-120, 0, 0), target, ItemStack.EMPTY, SizeClass.S, true, null, 0);
        helper.assertTrue(f.harmless(), "a flight with no warhead is harmless");
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(receiver.output() == 0 && Alarms.status(level, 9226) == null, "no warning: " + Alarms.status(level, 9226));
                    Flights.of(level).cancelForTest(level, f);
                })
                .thenSucceed();
    }
}
