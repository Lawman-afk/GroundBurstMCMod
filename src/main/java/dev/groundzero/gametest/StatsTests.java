package dev.groundzero.gametest;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import dev.groundzero.content.assembly.AssemblyStats;
import dev.groundzero.content.assembly.Fuze;
import dev.groundzero.content.assembly.FuzeSettings;
import dev.groundzero.content.assembly.MissileAssembly;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.registry.GZDataComponents;
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
        GZGameTests.test("bench_stats_show_a_warheads_blast", GZGameTests.FLOOR_16, 20, StatsTests::warhead);
        GZGameTests.test("bench_stats_show_a_missiles_flight", GZGameTests.FLOOR_16, 20, StatsTests::missile);
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
        ItemStack he = new ItemStack(GZOrdnance.HE_WARHEAD.item().get());
        he.set(GZDataComponents.FUZE.get(), FuzeSettings.of(Fuze.BAROMETRIC, 50));
        String s = all(AssemblyStats.warhead(he, registries));
        has(helper, s, "groundzero.stats.class() = S");
        has(helper, s, "groundzero.stats.crater() = groundzero.stats.blocks(6)");
        has(helper, s, "groundzero.stats.lethal() = groundzero.stats.blocks(7)");
        has(helper, s, "groundzero.stats.yield() = groundzero.stats.kg(110)");
        has(helper, s, "groundzero.stats.fuze() = groundzero.fuze.barometric()");
        has(helper, s, "groundzero.stats.burst() = groundzero.stats.up(50)");
        // The Massive Ordnance Warhead comes to about 11 t, like the real thing.
        String massive = all(AssemblyStats.warhead(new ItemStack(GZOrdnance.MASSIVE_WARHEAD.item().get()), registries));
        has(helper, massive, "groundzero.stats.yield() = groundzero.stats.tonnes(11)");
        has(helper, massive, "groundzero.stats.fires() = groundzero.stats.blocks(10)");
        // A gas warhead's burst is only its casing opening: no blast rows, but what it scatters.
        String gas = all(AssemblyStats.warhead(new ItemStack(GZOrdnance.GAS_WARHEAD.item().get()), registries));
        helper.assertTrue(!gas.contains("groundzero.stats.crater"), "no crater for a gas warhead:\n" + gas);
        has(helper, gas, "groundzero.stats.effect.gas(14, 20)");
        // A fission warhead: its design yield, and its fallout.
        String fission = all(AssemblyStats.warhead(new ItemStack(GZOrdnance.FISSION_WARHEAD.item().get()), registries));
        has(helper, fission, "groundzero.stats.yield() = groundzero.stats.yield_value(15 kt)");
        has(helper, fission, "groundzero.stats.fallout() = groundzero.stats.blocks(180)");
        has(helper, fission, "groundzero.stats.fallout_lasts() = groundzero.stats.days(2)");
        // Slice 8b: a boosted warhead's yield; a neutron warhead's fallout lasts hours; a dirty warhead is class S, five days.
        String boosted = all(AssemblyStats.warhead(new ItemStack(GZOrdnance.BOOSTED_FISSION_WARHEAD.item().get()), registries));
        has(helper, boosted, "groundzero.stats.yield() = groundzero.stats.yield_value(45 kt)");
        has(helper, boosted, "groundzero.stats.fallout() = groundzero.stats.blocks(220)");
        String neutron = all(AssemblyStats.warhead(new ItemStack(GZOrdnance.NEUTRON_WARHEAD.item().get()), registries));
        has(helper, neutron, "groundzero.stats.fallout_lasts() = groundzero.stats.hours(6)");
        has(helper, neutron, "groundzero.stats.neutrons() = groundzero.stats.blocks(180)");
        has(helper, neutron, "groundzero.stats.withers() = groundzero.stats.blocks(120)");
        String dirty = all(AssemblyStats.warhead(new ItemStack(GZOrdnance.DIRTY_WARHEAD.item().get()), registries));
        has(helper, dirty, "groundzero.stats.class() = S");
        has(helper, dirty, "groundzero.stats.fallout_lasts() = groundzero.stats.days(5)");
        helper.assertTrue(AssemblyStats.warhead(new ItemStack(GZOrdnance.C4.item().get()), registries).isEmpty(), "C4 is no warhead");
        helper.succeed();
    }

    private static void missile(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack m = MissileAssembly.missile(new MissileSpec(new ItemStack(GZOrdnance.HEAVY_WARHEAD.item().get()), SizeClass.M,
                MissileParts.Motor.SRM_II, MissileParts.Guidance.INERTIAL, true));
        String s = all(AssemblyStats.missile(m, registries));
        has(helper, s, "groundzero.stats.crater() = groundzero.stats.blocks(12)");
        has(helper, s, "groundzero.stats.range() = groundzero.stats.blocks(1024)");
        has(helper, s, "groundzero.stats.apex() = groundzero.stats.blocks(220)");
        has(helper, s, "groundzero.stats.time() = groundzero.stats.seconds(21)");
        has(helper, s, "groundzero.stats.lands() = groundzero.stats.blocks(41)");
        has(helper, s, "groundzero.stats.emp() = groundzero.stats.hardened()");
        ItemStack unguided = MissileAssembly.missile(new MissileSpec(new ItemStack(GZOrdnance.HE_WARHEAD.item().get()), SizeClass.S,
                MissileParts.Motor.SRM_I, MissileParts.Guidance.UNGUIDED, false));
        String u = all(AssemblyStats.missile(unguided, registries));
        has(helper, u, "groundzero.stats.lands() = groundzero.stats.blocks(100)");
        has(helper, u, "groundzero.stats.emp() = groundzero.stats.unhardened()");
        helper.succeed();
    }
}
