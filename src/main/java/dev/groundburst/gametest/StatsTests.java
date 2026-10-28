package dev.groundburst.gametest;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.Fuze;
import dev.groundburst.content.assembly.FuzeSettings;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.registry.GBDataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

/**
 * Phase 7 slice 7c (DESIGN.md §10): the Assembly Bench's stats screen. Text is compared by key and arguments (a
 * dedicated server has no English for the mod's keys).
 */
final class StatsTests {
    private StatsTests() {}

    static void register() {
        GBGameTests.test("bench_stats_show_a_warheads_blast", GBGameTests.FLOOR_16, 20, StatsTests::warhead);
        GBGameTests.test("bench_stats_show_a_missiles_flight", GBGameTests.FLOOR_16, 20, StatsTests::missile);
    }

    /** "key(arg, arg)" for a translatable component, its text otherwise. */
    private static String flat(Component c) {
        if (c.getContents() instanceof TranslatableContents t) {
            return t.getKey() + Arrays.stream(t.getArgs()).map(a -> a instanceof Component ac ? flat(ac) : String.valueOf(a))
                    .collect(Collectors.joining(", ", "(", ")"));
        }
        return c.getString();
    }

    /** Every row as "label = value", every header and note as its text. */
    private static String all(List<AssemblyStats.Stat> stats) {
        return stats.stream().map(s -> s.kind() == AssemblyStats.Kind.ROW ? flat(s.label()) + " = " + flat(s.value()) : flat(s.label()))
                .collect(Collectors.joining("\n"));
    }

    private static void has(GameTestHelper helper, String all, String part) {
        helper.assertTrue(all.contains(part), "stats should show " + part + ":\n" + all);
    }

    private static void warhead(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack he = new ItemStack(GBOrdnance.HE_WARHEAD.item().get());
        he.set(GBDataComponents.FUZE.get(), FuzeSettings.of(Fuze.BAROMETRIC, 50));
        String s = all(AssemblyStats.warhead(he, registries));
        has(helper, s, "groundburst.stats.class() = S");
        has(helper, s, "groundburst.stats.crater() = groundburst.stats.blocks(6)");
        has(helper, s, "groundburst.stats.lethal() = groundburst.stats.blocks(7)");
        has(helper, s, "groundburst.stats.yield() = groundburst.stats.kg(110)");
        has(helper, s, "groundburst.stats.fuze() = groundburst.fuze.barometric()");
        has(helper, s, "groundburst.stats.burst() = groundburst.stats.up(50)");
        // The Massive Ordnance Warhead comes to about 11 t, like the real thing.
        String massive = all(AssemblyStats.warhead(new ItemStack(GBOrdnance.MASSIVE_WARHEAD.item().get()), registries));
        has(helper, massive, "groundburst.stats.yield() = groundburst.stats.tonnes(11)");
        has(helper, massive, "groundburst.stats.fires() = groundburst.stats.blocks(10)");
        // A gas warhead's burst is only its casing opening: no blast rows, but what it scatters.
        String gas = all(AssemblyStats.warhead(new ItemStack(GBOrdnance.GAS_WARHEAD.item().get()), registries));
        helper.assertTrue(!gas.contains("groundburst.stats.crater"), "no crater for a gas warhead:\n" + gas);
        has(helper, gas, "groundburst.stats.effect.gas(14, 20)");
        // A fission warhead: its design yield, and its fallout.
        String fission = all(AssemblyStats.warhead(new ItemStack(GBOrdnance.FISSION_WARHEAD.item().get()), registries));
        has(helper, fission, "groundburst.stats.yield() = groundburst.stats.yield_value(15 kt)");
        has(helper, fission, "groundburst.stats.fallout() = groundburst.stats.blocks(180)");
        has(helper, fission, "groundburst.stats.fallout_lasts() = groundburst.stats.days(2)");
        // Slice 8b: a boosted warhead's yield; a neutron warhead's fallout lasts hours; a dirty warhead is class S, five days.
        String boosted = all(AssemblyStats.warhead(new ItemStack(GBOrdnance.BOOSTED_FISSION_WARHEAD.item().get()), registries));
        has(helper, boosted, "groundburst.stats.yield() = groundburst.stats.yield_value(45 kt)");
        has(helper, boosted, "groundburst.stats.fallout() = groundburst.stats.blocks(220)");
        String neutron = all(AssemblyStats.warhead(new ItemStack(GBOrdnance.NEUTRON_WARHEAD.item().get()), registries));
        has(helper, neutron, "groundburst.stats.fallout_lasts() = groundburst.stats.hours(6)");
        has(helper, neutron, "groundburst.stats.neutrons() = groundburst.stats.blocks(180)");
        has(helper, neutron, "groundburst.stats.withers() = groundburst.stats.blocks(120)");
        String dirty = all(AssemblyStats.warhead(new ItemStack(GBOrdnance.DIRTY_WARHEAD.item().get()), registries));
        has(helper, dirty, "groundburst.stats.class() = S");
        has(helper, dirty, "groundburst.stats.fallout_lasts() = groundburst.stats.days(5)");
        // Slice 8c: the whole weapons' warheads.
        String thermo = all(AssemblyStats.warhead(new ItemStack(GBOrdnance.THERMONUCLEAR_WARHEAD.item().get()), registries));
        has(helper, thermo, "groundburst.stats.yield() = groundburst.stats.yield_value(1.2 Mt)");
        has(helper, thermo, "groundburst.stats.fallout() = groundburst.stats.blocks(360)");
        has(helper, thermo, "groundburst.stats.fallout_lasts() = groundburst.stats.days(3)");
        String cobalt = all(AssemblyStats.warhead(new ItemStack(GBOrdnance.COBALT_WARHEAD.item().get()), registries));
        has(helper, cobalt, "groundburst.stats.fallout_lasts() = groundburst.stats.days(30)");
        has(helper, cobalt, "groundburst.stats.effect.cobalt(" + dev.groundburst.content.ordnance.charge.Charges.cobaltReach() + ")");
        // Slice 8d: the Tsar.
        String tsar = all(AssemblyStats.warhead(new ItemStack(GBOrdnance.TSAR_WARHEAD.item().get()), registries));
        has(helper, tsar, "groundburst.stats.yield() = groundburst.stats.yield_value(50 Mt)");
        has(helper, tsar, "groundburst.stats.fallout() = groundburst.stats.blocks(600)");
        has(helper, tsar, "groundburst.stats.fallout_lasts() = groundburst.stats.days(3)");
        helper.assertTrue(AssemblyStats.warhead(new ItemStack(GBOrdnance.C4.item().get()), registries).isEmpty(), "C4 is no warhead");
        helper.succeed();
    }

    private static void missile(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack m = MissileAssembly.missile(new MissileSpec(new ItemStack(GBOrdnance.HEAVY_WARHEAD.item().get()), SizeClass.M,
                MissileParts.Motor.SRM_II, MissileParts.Guidance.INERTIAL, true));
        String s = all(AssemblyStats.missile(m, registries));
        has(helper, s, "groundburst.stats.crater() = groundburst.stats.blocks(12)");
        has(helper, s, "groundburst.stats.range() = groundburst.stats.blocks(1024)");
        has(helper, s, "groundburst.stats.apex() = groundburst.stats.blocks(220)");
        has(helper, s, "groundburst.stats.time() = groundburst.stats.seconds(21)");
        has(helper, s, "groundburst.stats.lands() = groundburst.stats.blocks(41)");
        has(helper, s, "groundburst.stats.emp() = groundburst.stats.hardened()");
        ItemStack unguided = MissileAssembly.missile(new MissileSpec(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.S,
                MissileParts.Motor.SRM_I, MissileParts.Guidance.UNGUIDED, false));
        String u = all(AssemblyStats.missile(unguided, registries));
        has(helper, u, "groundburst.stats.lands() = groundburst.stats.blocks(100)");
        has(helper, u, "groundburst.stats.emp() = groundburst.stats.unhardened()");
        helper.succeed();
    }
}
