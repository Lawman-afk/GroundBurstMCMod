package dev.groundburst.gametest;

import java.util.List;

import dev.groundburst.content.assembler.AssemblerClass;
import dev.groundburst.content.assembler.MissileAssemblerBlockEntity;
import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.GBAssembly;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.Designators;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaserDesignatorItem;
import dev.groundburst.content.launch.LaunchCodesItem;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.LaunchStationBlockEntity;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.ThrownExplosive;
import dev.groundburst.registry.GBDataComponents;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Laser Designator (DESIGN.md §17.13): it lases where its holder looks (on a creature, the creature) and draws its
 * charge; smoke breaks its beam; a laser-guided missile homes on its holder's spot as it comes down (and one aimed too
 * far from the spot does not); held steady in call-for-fire mode it locks after two seconds and fires the network's
 * armed launcher on the spot.
 */
final class DesignatorTests {
    private DesignatorTests() {}

    private static final BlockPos SILO = new BlockPos(2, 1, 1);
    /** Where the holder stands, looking south at the wall. */
    private static final Vec3 STAND = new Vec3(10.5, 1, 3.5);
    private static final int WALL_Z = 13;

    static void register() {
        GBGameTests.test("designator_lases_where_it_looks", GBGameTests.FLOOR_16, 20, DesignatorTests::lases);
        GBGameTests.test("smoke_breaks_the_designator_beam", GBGameTests.FLOOR_16, 80, DesignatorTests::smoke);
        GBGameTests.test("laser_missile_homes_on_the_spot", GBGameTests.FLOOR_16, 60, DesignatorTests::homes);
        GBGameTests.test("designator_calls_fire_on_armed_launchers", GBGameTests.FLOOR_16, 40, DesignatorTests::callsFire);
        GBGameTests.test("laser_guided_missile_reads_everywhere", GBGameTests.FLOOR_16, 20, DesignatorTests::reads);
    }

    /** A stone wall across the far side, three high. */
    private static void wall(GameTestHelper helper) {
        for (int x = 3; x <= 14; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, WALL_Z), Blocks.STONE);
            }
        }
    }

    /**
     * A survival player with a connection (what the designator says to it has somewhere to go), kept out of the server's
     * player list (joining, it would be sent what the mod sends every player, which a test's connection cannot take).
     */
    private static ServerPlayer holder(GameTestHelper helper) {
        ServerPlayer p = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, CommonListenerCookie.createInitial(p.getGameProfile(), false));
        stand(helper, p);
        return p;
    }

    private static void stand(GameTestHelper helper, Player p) {
        Vec3 at = helper.absoluteVec(STAND);
        p.snapTo(at.x, at.y, at.z, 0, 0);                            // facing south, level
        p.setYHeadRot(0);
    }

    /** It lases the wall ahead and draws its charge for it; a creature in the way takes the spot. */
    private static void lases(GameTestHelper helper) {
        LaunchTests.floor(helper);
        wall(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer p = holder(helper);
        ItemStack d = new ItemStack(GBLaunch.LASER_DESIGNATOR.get());
        LaserDesignatorItem item = (LaserDesignatorItem) d.getItem();
        helper.assertTrue(LaserDesignatorItem.stored(d) == LaserDesignatorItem.CAPACITY, "it comes charged");
        item.onUseTick(level, p, d, 1000);
        Designators.Spot s = Designators.spotOf(level, p.getUUID());
        helper.assertTrue(s != null, "a spot");
        Vec3 rel = helper.relativeVec(s.pos());
        helper.assertTrue(Math.abs(rel.z - WALL_Z) < 0.01 && Math.abs(rel.x - STAND.x) < 0.01 && !s.onEntity(), "on the wall's face ahead: " + rel);
        helper.assertTrue(LaserDesignatorItem.stored(d) == LaserDesignatorItem.CAPACITY - LaserDesignatorItem.USE,
                "it drew its charge: " + LaserDesignatorItem.stored(d));
        var villager = helper.spawn(EntityTypes.VILLAGER, new Vec3(STAND.x, 1, 8.5));    // as tall as the beam is high
        villager.setNoAi(true);
        item.onUseTick(level, p, d, 1000);
        s = Designators.spotOf(level, p.getUUID());
        helper.assertTrue(s != null && s.onEntity() && s.pos().distanceTo(villager.getBoundingBox().getCenter()) < 0.01,
                "the villager in the way takes the spot (its middle): " + (s == null ? null : helper.relativeVec(s.pos())));
        d.set(GBDataComponents.ENERGY.get(), 0);
        helper.assertTrue(!LaserDesignatorItem.works(d, level), "flat, it does not work");
        villager.discard();
        helper.succeed();
    }

    /** A smoke grenade's cloud between it and the wall breaks the beam. */
    private static void smoke(GameTestHelper helper) {
        LaunchTests.floor(helper);
        wall(helper);
        ServerLevel level = helper.getLevel();
        Player p = helper.makeMockServerPlayer(GameType.SURVIVAL);
        stand(helper, p);
        helper.assertTrue(Designators.trace(level, p) != null, "a clear beam reaches the wall");
        ThrownExplosive g = new ThrownExplosive(level, p, new ItemStack(GBOrdnance.SMOKE_GRENADE.get()));
        g.setPos(helper.absoluteVec(new Vec3(STAND.x, 1.05, 8.5)));
        g.setDeltaMovement(Vec3.ZERO);
        g.setFuseForTest(2);
        level.addFreshEntity(g);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(g.smoking(), "it is pouring smoke");
            helper.assertTrue(Designators.trace(level, p) == null, "the smoke breaks the beam");
            g.discard();
            helper.succeed();
        });
    }

    /**
     * Two laser-guided missiles from the same player, past half way: the one aimed 30 blocks from the player's spot homes
     * on it (its target walks onto the spot, a block a tick); the one aimed 70 blocks from it (beyond its 63-block reach)
     * flies on as aimed.
     */
    private static void homes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 pad = helper.absoluteVec(new Vec3(8.5, 2, 8.5));
        Vec3 aim = pad.add(300, 0, 0);
        Vec3 spot = aim.add(0, 0, 30);
        MissileSpec spec = new MissileSpec(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.M, MissileParts.Motor.SRM_II,
                MissileParts.Guidance.LASER, false);
        Flights.Flight lased = Flights.launch(level, pad, aim, spec, owner);
        Flights.Flight blind = Flights.launch(level, pad, aim.add(0, 0, 100), spec, owner);
        helper.assertTrue(lased.laserGuided() && blind.laserGuided(), "laser guided");
        int past = (int) (0.4 * Flights.flightTicks(300));                // to six tenths of the way, coming down
        lased.skipForTest(lased.ticksToImpact() - past);
        blind.skipForTest(blind.ticksToImpact() - past);
        helper.onEachTick(() -> Designators.lase(level, owner.getUUID(), 0, spot, false));
        helper.runAfterDelay(20, () -> {
            Vec3 t = lased.target();
            double off = Math.hypot(t.x - spot.x, t.z - spot.z);
            helper.assertTrue(lased.homing(), "the missile aimed near the spot homes on it");
            helper.assertTrue(off < 15, "its target walks onto the spot (30 blocks off at first): " + off);
            Vec3 b = blind.target();
            helper.assertTrue(!blind.homing() && Math.abs(b.x - aim.x) < 1e-6 && Math.abs(b.z - (aim.z + 100)) < 1e-6,
                    "the one aimed beyond its reach flies on as aimed: " + b);
            Flights.of(level).cancelForTest(level, lased);
            Flights.of(level).cancelForTest(level, blind);
            helper.succeed();
        });
    }

    /** Call for fire: on the network, held steady two seconds, the lock fires the network's armed silo on the spot. */
    private static void callsFire(GameTestHelper helper) {
        LaunchTests.floor(helper);
        wall(helper);
        ServerLevel level = helper.getLevel();
        LaunchStationBlockEntity st = StationTests.station(helper);
        LaunchSiloBlockEntity silo = LaunchTests.silo(helper, SILO);
        silo.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile());
        silo.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        ItemStack card = new ItemStack(GBLaunch.LAUNCH_CODES.get());
        LaunchCodesItem.use(level, card, st, false);
        LaunchCodesItem.use(level, card, silo, false);
        String code = LaunchCodesItem.code(card);
        helper.runAfterDelay(3, () -> {
            List<BlockPos> picked = List.of(silo.getBlockPos());
            BlockPos far = silo.getBlockPos().offset(150, 0, 40);
            st.aim(level, picked, far.getX(), far.getZ(), LaunchNetwork.Pattern.POINT, 0);
            st.arm(level, picked, true);
            helper.assertTrue(silo.armed(), "the silo is armed");
        });
        helper.runAfterDelay(6, () -> {
            ServerPlayer p = holder(helper);
            ItemStack d = new ItemStack(GBLaunch.LASER_DESIGNATOR.get());
            d.set(GBDataComponents.LAUNCH_CODES.get(), code);
            d.set(GBDataComponents.DESIGNATOR_MODE.get(), LaserDesignatorItem.Mode.FIRE.ordinal());
            LaserDesignatorItem item = (LaserDesignatorItem) d.getItem();
            for (int i = 0; i < LaserDesignatorItem.LOCK - 1; i++) {
                item.onUseTick(level, p, d, 1000);
            }
            helper.assertTrue(silo.countdown() < 0, "not yet: the lock takes two seconds");
            item.onUseTick(level, p, d, 1000);
            helper.assertTrue(silo.countdown() >= 0, "locked: fire called, the silo counts down");
            BlockPos spot = BlockPos.containing(helper.absoluteVec(new Vec3(STAND.x, 2.62, WALL_Z)));
            helper.assertTrue(silo.target() != null && silo.target().getX() == spot.getX() && silo.target().getZ() == spot.getZ(),
                    "aimed at the spot: " + silo.target() + " for " + spot);
            List<LaunchNetwork.Event> log = st.network(level).log();
            LaunchNetwork.Event last = log.getLast();
            helper.assertTrue(last.kind() == LaunchNetwork.EventKind.FIRE_CALLED && last.detail() == 1, "the log has the call for fire: " + last);
            item.onUseTick(level, p, d, 1000);
            helper.assertTrue(st.network(level).log().size() == log.size(), "one call for each lock");
            helper.setBlock(SILO, Blocks.AIR);                             // scrubbed: no missile left flying after the test
                helper.succeed();
        });
    }

    /**
     * A laser-guided missile read everywhere it is shown (a missing name for laser guidance crashed the assembler's
     * screen): the assembler's readout with the laser unit loaded, the missile's tooltip and stats; taken apart, the laser
     * unit comes back.
     */
    private static void reads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MissileAssemblerBlockEntity a = AssemblerTests.assembler(helper, AssemblerClass.LIGHT, true);
        SizeClass size = AssemblerClass.LIGHT.size;
        a.energy().generate(AssemblerClass.LIGHT.capacity);
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_WARHEAD, new ItemStack(GBOrdnance.HE_WARHEAD.item().get()));
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_BODY, new ItemStack(GBAssembly.BODIES.get(size.ordinal()).get()));
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_MOTOR,
                new ItemStack(GBAssembly.MOTORS.get(MissileAssembly.rocketsFor(size).getLast().ordinal()).get()));
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_GUIDANCE, new ItemStack(GBAssembly.LASER_GUIDANCE.get()));
        helper.runAfterDelay(3, () -> {
            Player p = helper.makeMockServerPlayer(GameType.SURVIVAL);
            MachineMenu menu = (MachineMenu) a.createMenu(0, p.getInventory(), p);
            List<Component> lines = a.readout(menu);
            helper.assertTrue(lines.stream().anyMatch(c -> c.getString().toLowerCase(java.util.Locale.ROOT).contains("laser")),
                    "the readout names laser guidance: " + lines);
            ItemStack missile = MissileAssembly.missile(new MissileSpec(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), size,
                    MissileParts.Motor.SRM_I, MissileParts.Guidance.LASER, false));
            helper.assertTrue(!missile.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL).isEmpty(), "its tooltip");
            helper.assertTrue(!AssemblyStats.missile(missile, level.registryAccess()).isEmpty(), "its stats");
            helper.assertTrue(MissileAssembly.takeApart(missile).stream().anyMatch(s -> s.is(GBAssembly.LASER_GUIDANCE.get())),
                    "taken apart, the laser unit comes back");
            helper.succeed();
        });
    }
}
