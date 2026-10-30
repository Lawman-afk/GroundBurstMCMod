package dev.groundburst.gametest;

import dev.groundburst.content.alarm.AlarmBlock;
import dev.groundburst.content.alarm.AlarmBlockEntity;
import dev.groundburst.content.alarm.Alarms;
import dev.groundburst.content.alarm.GBAlarms;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.defence.Interception;
import dev.groundburst.content.effect.GasAgent;
import dev.groundburst.content.effect.GasGrid;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.ordnance.charge.Charges;
import dev.groundburst.content.radiation.RadiationSource;
import dev.groundburst.content.radiation.RadiationSources;
import dev.groundburst.registry.GBBlocks.WithItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The base alarm system (DESIGN.md §18.6). Each test keeps to a channel of its own (the 91xx), as tests run side by side
 * in one world: a Missile Warning Receiver raises its channel for a tracked missile coming down within its radius and
 * a siren sounds; an Alarm Panel powered by redstone sets off every kind of device on its channel and none on another;
 * silencing quiets the sounders but not the lights, stopping clears the channel; a siren sounds the All Clear after an
 * attack; a Hazard Monitor alarms on radiation and on poison gas; the network ranks its alerts.
 */
final class AlarmTests {
    private AlarmTests() {}

    static void register() {
        GBGameTests.test("missile_warning_receiver_sounds_the_siren", GBGameTests.FLOOR_16, 300, AlarmTests::receiver);
        GBGameTests.test("alarm_panel_sets_off_its_channel", GBGameTests.FLOOR_16, 60, AlarmTests::panel);
        GBGameTests.test("alarm_panel_silences_and_stops", GBGameTests.FLOOR_16, 160, AlarmTests::silence);
        GBGameTests.test("air_raid_siren_sounds_the_all_clear", GBGameTests.FLOOR_16, 100, AlarmTests::allClear);
        GBGameTests.test("hazard_monitor_alarms_on_radiation", GBGameTests.FLOOR_16, 60, AlarmTests::radiation);
        GBGameTests.test("hazard_monitor_alarms_on_gas", GBGameTests.FLOOR_16, 60, AlarmTests::gas);
        GBGameTests.test("alarm_channels_rank_their_alerts", GBGameTests.FLOOR_16, 20, AlarmTests::ranks);
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /** A device (facing north, or up) on {@code channel}. */
    private static AlarmBlockEntity device(GameTestHelper helper, WithItem<? extends AlarmBlock> block, BlockPos rel, int channel) {
        helper.setBlock(rel, block.block().get().defaultBlockState());
        AlarmBlockEntity be = helper.getBlockEntity(rel, AlarmBlockEntity.class);
        be.applySettings(channel, be.options());
        return be;
    }

    /**
     * A radar tracking the sky (as a working Radar Array reports itself), a missile from 180 blocks coming down 3 blocks
     * from a receiver (its radius 64): the receiver gives redstone and raises its channel; the siren on it sounds.
     */
    private static void receiver(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        AlarmBlockEntity receiver = device(helper, GBAlarms.RECEIVER, new BlockPos(4, 1, 8), 9101);
        receiver.applySettings(9101, new int[] {0, 0});                // a radius of 64, steady redstone
        AlarmBlockEntity siren = device(helper, GBAlarms.SIREN, new BlockPos(12, 1, 8), 9101);
        BlockPos radar = helper.absolutePos(new BlockPos(8, 1, 1));
        helper.onEachTick(() -> Interception.radarWorking(level, radar));
        Vec3 target = helper.absoluteVec(new Vec3(6.5, 1, 8.5));
        Flights.Flight[] f = new Flights.Flight[1];
        var spec = new dev.groundburst.content.assembly.MissileSpec(new ItemStack(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()),
                SizeClass.M, dev.groundburst.content.assembly.MissileParts.Motor.SRM_II, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false);
        Charges.scaled(0.25f, () -> f[0] = Flights.launch(level, target.add(-180, 0, 0), target, spec, null));
        helper.succeedWhen(() -> {
            helper.assertTrue(receiver.output() == 15, "the receiver gives redstone: " + receiver.output());
            Alarms.Alert alert = Alarms.status(level, 9101);
            helper.assertTrue(alert != null && alert.kind() == Alarms.Kind.ATTACK && alert.count() >= 1, "its channel raised for an attack: " + alert);
            helper.assertTrue(siren.active(), "the siren sounds");
            Flights.of(level).cancelForTest(level, f[0]);
        });
    }

    /**
     * A panel powered by a redstone block: on its channel the klaxon sounds, the beacon and the emergency light (red)
     * light, the relay gives redstone and the display shows the alarm; a klaxon on another channel stays quiet.
     */
    private static void panel(GameTestHelper helper) {
        floor(helper);
        device(helper, GBAlarms.PANEL, new BlockPos(2, 1, 2), 9102);
        AlarmBlockEntity klaxon = device(helper, GBAlarms.KLAXON, new BlockPos(5, 1, 2), 9102);
        AlarmBlockEntity beacon = device(helper, GBAlarms.BEACON, new BlockPos(8, 1, 2), 9102);
        AlarmBlockEntity light = device(helper, GBAlarms.LIGHT, new BlockPos(11, 1, 2), 9102);
        AlarmBlockEntity relay = device(helper, GBAlarms.RELAY, new BlockPos(2, 1, 6), 9102);
        AlarmBlockEntity display = device(helper, GBAlarms.DISPLAY, new BlockPos(5, 1, 6), 9102);
        AlarmBlockEntity other = device(helper, GBAlarms.KLAXON, new BlockPos(8, 1, 6), 9103);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            helper.assertTrue(klaxon.active(), "the klaxon sounds");
            helper.assertTrue(beacon.active(), "the beacon lights");
            helper.assertTrue(light.getBlockState().getValue(AlarmBlock.Light.LAMP) == AlarmBlock.Lamp.RED, "the emergency light is red");
            helper.assertTrue(relay.output() == 15, "the relay gives redstone");
            helper.assertTrue(display.kind() == Alarms.Kind.ALARM.ordinal(), "the display shows the alarm");
            helper.assertTrue(!other.active(), "another channel's klaxon stays quiet");
        });
    }

    /** Sounded by hand: silenced, the klaxon stops but the beacon stays lit; stopped, the beacon goes out. */
    private static void silence(GameTestHelper helper) {
        floor(helper);
        AlarmBlockEntity panel = device(helper, GBAlarms.PANEL, new BlockPos(2, 1, 2), 9104);
        AlarmBlockEntity klaxon = device(helper, GBAlarms.KLAXON, new BlockPos(5, 1, 2), 9104);
        AlarmBlockEntity beacon = device(helper, GBAlarms.BEACON, new BlockPos(8, 1, 2), 9104);
        helper.runAfterDelay(5, () -> panel.panel(AlarmBlockEntity.SOUND));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(klaxon.active() && beacon.active(), "sounding and lit");
            panel.panel(AlarmBlockEntity.SILENCE);
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(!klaxon.active(), "silenced, the klaxon stops");
            helper.assertTrue(beacon.active(), "and the beacon stays lit");
            panel.panel(AlarmBlockEntity.STOP);
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(!beacon.active(), "stopped, the beacon goes out");
            helper.assertTrue(Alarms.status(helper.getLevel(), 9104) == null, "and the channel is clear");
            helper.succeed();
        });
    }

    /** A panel raising an attack: the siren wails; stopped, it sounds the All Clear (steady) and keeps sounding. */
    private static void allClear(GameTestHelper helper) {
        floor(helper);
        AlarmBlockEntity panel = device(helper, GBAlarms.PANEL, new BlockPos(2, 1, 2), 9105);
        panel.applySettings(9105, new int[] {0});                       // raises: Attack
        AlarmBlockEntity siren = device(helper, GBAlarms.SIREN, new BlockPos(8, 1, 8), 9105);
        helper.runAfterDelay(5, () -> panel.panel(AlarmBlockEntity.SOUND));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(siren.active() && !siren.allClear(), "the siren wails for the attack");
            panel.panel(AlarmBlockEntity.STOP);
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(siren.allClear(), "the attack over, it sounds the All Clear");
            helper.assertTrue(siren.active(), "still sounding");
            helper.succeed();
        });
    }

    /** A strong source in front of a monitor: it alarms for radiation, gives redstone, and a relay on its channel gives it too. */
    private static void radiation(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        AlarmBlockEntity monitor = device(helper, GBAlarms.MONITOR, new BlockPos(8, 1, 8), 9106);
        AlarmBlockEntity relay = device(helper, GBAlarms.RELAY, new BlockPos(3, 1, 3), 9106);
        RadiationSources.of(level).add(new RadiationSource(helper.absoluteVec(new Vec3(8.5, 1.5, 7.5)), 4, 5_000, 40, level.getGameTime()));
        helper.succeedWhen(() -> {
            helper.assertTrue(monitor.kind() == Alarms.Kind.RADIATION.ordinal(), "the monitor alarms for radiation: " + monitor.reading());
            helper.assertTrue(monitor.output() == 15, "and gives redstone");
            helper.assertTrue(relay.output() == 15, "the relay on its channel gives redstone");
        });
    }

    /** Chlorine in front of a monitor: it alarms for gas. */
    private static void gas(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        AlarmBlockEntity monitor = device(helper, GBAlarms.MONITOR, new BlockPos(8, 1, 8), 9107);
        BlockPos front = helper.absolutePos(new BlockPos(8, 1, 7));
        helper.onEachTick(() -> GasGrid.of(level).emit(level, front, GasAgent.values()[0], 0.5f));
        helper.succeedWhen(() -> helper.assertTrue(monitor.kind() == Alarms.Kind.GAS.ordinal(), "the monitor alarms for gas: " + monitor.gas()));
    }

    /** The network: an attack outranks an alarm, receivers' attacks merge (the most missiles, the soonest impact). */
    private static void ranks(GameTestHelper helper) {
        var level = helper.getLevel();
        long now = level.getGameTime();
        BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1)), b = helper.absolutePos(new BlockPos(2, 1, 1)), c = helper.absolutePos(new BlockPos(3, 1, 1));
        Alarms.raise(level, 9108, a, Alarms.Alert.of(Alarms.Kind.ALARM));
        helper.assertTrue(Alarms.status(level, 9108).kind() == Alarms.Kind.ALARM, "an alarm");
        Alarms.raise(level, 9108, b, new Alarms.Alert(Alarms.Kind.ATTACK, 2, now + 300, 0));
        Alarms.raise(level, 9108, c, new Alarms.Alert(Alarms.Kind.ATTACK, 1, now + 100, 0));
        Alarms.Alert top = Alarms.status(level, 9108);
        helper.assertTrue(top.kind() == Alarms.Kind.ATTACK && top.count() == 2 && top.impactAt() == now + 100, "the attack, merged: " + top);
        Alarms.lower(level, 9108, b);
        Alarms.lower(level, 9108, c);
        helper.assertTrue(Alarms.status(level, 9108).kind() == Alarms.Kind.ALARM, "the alarm again");
        Alarms.lower(level, 9108, a);
        helper.assertTrue(Alarms.status(level, 9108) == null, "all clear");
        helper.succeed();
    }
}
