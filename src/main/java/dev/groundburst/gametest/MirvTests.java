package dev.groundburst.gametest;

import java.util.List;

import dev.groundburst.content.assembly.GBAssembly;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.Mirv;
import dev.groundburst.content.assembly.MirvBusMenu;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.defence.Interception;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSite;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.MissileEntity;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.registry.GBDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * MIRVs (DESIGN.md §17.14): what each bus takes and how much of its load, loaded through its own screen with its spread
 * set there; a MIRV assembles on its own class's body only once loaded, and fires from its body's launchers; its strike
 * is a circle per RV; and in flight its bus lets its RVs go, each onto its point of the spread, the decoy among them,
 * each the hardest kind of target there is.
 */
final class MirvTests {
    private MirvTests() {}

    static void register() {
        GBGameTests.test("mirv_buses_take_rvs_by_their_load", GBGameTests.FLOOR_16, 20, MirvTests::loads);
        GBGameTests.test("mirv_assembles_loaded_on_its_own_body", GBGameTests.FLOOR_16, 20, MirvTests::assembles);
        GBGameTests.testOpenSky("mirv_bus_lets_its_rvs_go_onto_its_spread", GBGameTests.FLOOR_16, 60, MirvTests::splits);
    }

    private static ItemStack warhead(net.minecraft.world.level.ItemLike item) {
        return new ItemStack(item);
    }

    /** A bus of {@code size} holding {@code rvs} in its first mounts, spread in {@code pattern} {@code spacing} apart. */
    static ItemStack bus(SizeClass size, LaunchNetwork.Pattern pattern, int spacing, ItemStack... rvs) {
        ItemStack bus = new ItemStack(size == SizeClass.XL ? GBAssembly.STRATEGIC_MIRV_BUS.get() : GBAssembly.HEAVY_MIRV_BUS.get());
        NonNullList<ItemStack> mounts = NonNullList.withSize(Mirv.mounts(size), ItemStack.EMPTY);
        for (int i = 0; i < rvs.length; i++) {
            mounts.set(i, rvs[i]);
        }
        bus.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(mounts));
        bus.set(GBDataComponents.MIRV_SPREAD.get(), Mirv.Spread.of(pattern, spacing));
        return bus;
    }

    /**
     * Either bus takes Light and Medium warheads and decoys, nothing bigger and nothing nuclear: the Heavy bus by its load
     * of 12, the Strategic bus by its load of 16. Loaded through its screen, the bus keeps what is in it and the spread set
     * there.
     */
    private static void loads(GameTestHelper helper) {
        Player p = helper.makeMockServerPlayer(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GBAssembly.HEAVY_MIRV_BUS.get()));
        MirvBusMenu menu = new MirvBusMenu(1, p.getInventory(), InteractionHand.MAIN_HAND);
        ItemStack he = warhead(GBOrdnance.HE_WARHEAD.item().get()), heavy = warhead(GBOrdnance.HEAVY_WARHEAD.item().get());
        ItemStack decoy = new ItemStack(GBAssembly.RV_DECOY.get());
        helper.assertTrue(menu.mounts() == 6 && Mirv.capacity(SizeClass.L) == 12, "the Heavy bus: 6 mounts, load 12");
        helper.assertTrue(menu.fits(0, he) && menu.fits(0, heavy) && menu.fits(0, decoy), "it takes Light and Medium warheads and decoys");
        helper.assertTrue(!menu.fits(0, warhead(GBOrdnance.THERMOBARIC_WARHEAD.item().get())), "not a Heavy warhead");
        helper.assertTrue(!menu.fits(0, warhead(GBOrdnance.TACTICAL_NUCLEAR_WARHEAD.item().get())), "nor a nuclear one");
        helper.assertTrue(!menu.fits(0, new ItemStack(Items.DIRT)), "nor anything else");
        for (int i = 0; i < 3; i++) {
            menu.getSlot(i).set(heavy.copy());
        }
        helper.assertTrue(menu.load() == 12, "three Medium warheads fill its load: " + menu.load());
        helper.assertTrue(!menu.fits(3, decoy), "then not even a decoy fits");
        helper.assertTrue(menu.fits(0, he), "but a Light warhead in a Medium one's place does");
        menu.clickMenuButton(p, MirvBusMenu.NEXT_PATTERN);
        menu.clickMenuButton(p, MirvBusMenu.WIDER);
        ItemStack bus = p.getMainHandItem();
        helper.assertTrue(Mirv.rvs(bus).size() == 3 && Mirv.loadOf(bus) == 12, "the bus keeps what was loaded: " + Mirv.rvs(bus));
        Mirv.Spread spread = Mirv.spread(bus);
        helper.assertTrue(spread.pattern() == LaunchNetwork.Pattern.GRID && spread.spacing() == 16 + Mirv.STEP,
                "and its spread: a ring of 16 made a grid " + (16 + Mirv.STEP) + " apart: " + spread);
        helper.assertTrue(Mirv.mounts(SizeClass.XL) == 8 && Mirv.capacity(SizeClass.XL) == 16, "the Strategic bus: 8 mounts, load 16");
        helper.assertTrue(Mirv.load(SizeClass.XL, he) == 2 && Mirv.load(SizeClass.XL, heavy) == 4 && Mirv.load(SizeClass.XL, decoy) == 1,
                "the Strategic bus takes the same: Light and Medium warheads, decoys");
        helper.assertTrue(Mirv.load(SizeClass.XL, warhead(GBOrdnance.TACTICAL_NUCLEAR_WARHEAD.item().get())) == 0
                        && Mirv.load(SizeClass.XL, warhead(GBOrdnance.FISSION_WARHEAD.item().get())) == 0
                        && Mirv.load(SizeClass.XL, warhead(GBOrdnance.THERMOBARIC_WARHEAD.item().get())) == 0
                        && Mirv.load(SizeClass.XL, warhead(GBOrdnance.THERMONUCLEAR_WARHEAD.item().get())) == 0,
                "nothing nuclear and nothing bigger than Medium");
        helper.succeed();
    }

    /**
     * A MIRV assembles on its own class's body only, and only loaded; its look is its bus's; the Launch Silo fires a
     * Heavy MIRV and not a Strategic one (its body); its strike is a circle at each RV's point (a decoy's empty).
     */
    private static void assembles(GameTestHelper helper) {
        ItemStack he = warhead(GBOrdnance.HE_WARHEAD.item().get());
        ItemStack heavyBus = bus(SizeClass.L, LaunchNetwork.Pattern.RING, 12, he.copy(), he.copy(), new ItemStack(GBAssembly.RV_DECOY.get()));
        ItemStack body = new ItemStack(GBAssembly.HEAVY_BODY.get());
        MissileParts.Motor motor = MissileAssembly.rocketsFor(SizeClass.L).getLast();
        ItemStack motorItem = new ItemStack(GBAssembly.MOTORS.stream().filter(m -> m.get().motor == motor).findFirst().orElseThrow().get());
        helper.assertTrue(MissileAssembly.check(new ItemStack(GBAssembly.HEAVY_MIRV_BUS.get()), body, motorItem, ItemStack.EMPTY, SizeClass.L)
                == MissileAssembly.Problem.EMPTY_BUS, "an empty bus does not assemble");
        helper.assertTrue(MissileAssembly.check(heavyBus, body, motorItem, ItemStack.EMPTY, SizeClass.L) == MissileAssembly.Problem.NONE,
                "a loaded Heavy bus on a Heavy body does");
        helper.assertTrue(MissileAssembly.fit(heavyBus, SizeClass.XL) == MissileAssembly.Problem.BUS_BODY, "not on a Strategic body");
        helper.assertTrue(MissileAssembly.classOf(heavyBus) == SizeClass.L && MissileAssembly.isPayload(heavyBus), "a payload of class L");
        MissileSpec heavy = new MissileSpec(heavyBus, SizeClass.L, motor, MissileParts.Guidance.GPS, false);
        helper.assertTrue(heavy.mirv() && heavy.look().equals("heavy_mirv_bus") && !heavy.nuclear(), "a Heavy MIRV: " + heavy.look());
        ItemStack strategicBus = bus(SizeClass.XL, LaunchNetwork.Pattern.LINE, 30, warhead(GBOrdnance.HEAVY_WARHEAD.item().get()));
        MissileSpec strategic = new MissileSpec(strategicBus, SizeClass.XL, MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS, false);
        helper.assertTrue(!strategic.nuclear() && strategic.needsComplex(), "a Strategic MIRV is not nuclear, but its body needs a complex");
        LaunchSiloBlockEntity silo = LaunchTests.formedSilo(helper);
        helper.assertTrue(silo.accepts(heavy) && !silo.accepts(strategic), "the Launch Silo fires the Heavy MIRV, not the Strategic");
        BlockPos from = silo.getBlockPos(), aim = from.offset(200, 0, 0);
        LaunchSite.Strike strike = LaunchSite.Strike.of(helper.getLevel(), heavy, from, aim);
        List<int[]> points = Mirv.points(heavyBus, from, aim.getX(), aim.getZ());
        helper.assertTrue(strike.rvs().size() == 3, "a circle per RV: " + strike.rvs());
        for (int i = 0; i < 3; i++) {
            LaunchSite.Strike.Rv rv = strike.rvs().get(i);
            helper.assertTrue(rv.dx() == points.get(i)[0] - aim.getX() && rv.dz() == points.get(i)[1] - aim.getZ()
                    && Math.abs(Math.hypot(rv.dx(), rv.dz()) - 12) < 1.5, "RV " + i + " on the ring of 12: " + rv);
        }
        helper.assertTrue(strike.rvs().get(0).lethal() > 0 && strike.rvs().get(2).reach() == 0, "the warheads' circles; the decoy's none");
        helper.succeed();
    }

    /**
     * Coming down from 60% of its flight the bus lets its RVs go, one every few ticks, each falling for its point of the
     * spread round where the bus is aimed (the decoy too), and the spent bus breaks up; each RV is the hardest kind of
     * target (0.45, less again as it is white-hot).
     */
    private static void splits(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack he = warhead(GBOrdnance.HE_WARHEAD.item().get());
        ItemStack bus = bus(SizeClass.L, LaunchNetwork.Pattern.RING, 6, he.copy(), he.copy(), he.copy(), new ItemStack(GBAssembly.RV_DECOY.get()));
        MissileSpec spec = new MissileSpec(bus, SizeClass.L, MissileAssembly.rocketsFor(SizeClass.L).getLast(), MissileParts.Guidance.GPS, false);
        Vec3 pad = helper.absoluteVec(new Vec3(8, 1, 8));
        Flights.Flight f = Flights.launch(level, pad, pad.add(300, 0, 0), spec, null);
        int flight = f.ticksToImpact() - MissileEntity.IGNITION;
        int split = MissileEntity.IGNITION + (int) Math.ceil(Flights.MIRV_SPLIT * flight);
        f.skipForTest(split - 2);                                    // two ticks short of letting the first go
        helper.assertTrue(f.bus() && !f.rv(), "a MIRV flies as its bus");
        helper.runAfterDelay(2 + 3 * Flights.MIRV_GAP + 4, () -> {
            List<Flights.Flight> all = Flights.of(level).all();
            List<Flights.Flight> rvs = all.stream().filter(r -> r.rv() && r.target().distanceTo(f.target()) < 30).toList();
            helper.assertTrue(rvs.size() == 4, "four RVs let go: " + rvs.size());
            helper.assertTrue(!all.contains(f), "the spent bus broke up after the last");
            List<int[]> points = Mirv.points(bus, BlockPos.containing(pad), Mth.floor(f.target().x), Mth.floor(f.target().z));
            for (int i = 0; i < rvs.size(); i++) {
                Vec3 t = rvs.get(i).target();
                helper.assertTrue(Mth.floor(t.x) == points.get(i)[0] && Mth.floor(t.z) == points.get(i)[1],
                        "RV " + i + " falls on its point of the ring: " + t + " for " + points.get(i)[0] + ", " + points.get(i)[1]);
            }
            helper.assertTrue(rvs.stream().filter(Flights.Flight::decoy).count() == 1 && rvs.get(3).decoy(), "the last out, the decoy");
            Flights.Flight rv = rvs.get(0);
            helper.assertTrue(!rv.burning() && rv.dropped() == 0 && rv.reentry() > 0.5f, "an RV: no engine, no stages, white-hot");
            double kill = Interception.killChance(1.0, rv, true);
            helper.assertTrue(Math.abs(kill - Interception.RV_FACTOR * 0.85) < 1e-6, "the hardest target there is: " + kill);
            for (Flights.Flight r : rvs) {
                Flights.of(level).cancelForTest(level, r);
            }
            helper.succeed();
        });
    }
}
