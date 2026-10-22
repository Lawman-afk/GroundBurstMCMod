package dev.groundzero.content.assembly;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.blast.BlastProfile;
import dev.groundzero.blast.Blasts;
import dev.groundzero.content.launch.Flights;
import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.content.ordnance.charge.ChargeKind;
import dev.groundzero.content.ordnance.charge.Charges;
import dev.groundzero.registry.GZDataComponents;
import dev.groundzero.registry.GZLang;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * The Assembly Bench's stats screen (DESIGN.md §10, slice 7c): what a warhead or a missile will do, as rows of a label
 * and a value, headers and notes. The blast radii come from the warhead's blast profile; a missile's flight figures are
 * worked out for a shot at its full range.
 */
public final class AssemblyStats {
    /** A header, a label with its value, or a note (headers and notes wrap). */
    public enum Kind { HEADER, ROW, NOTE }

    public record Stat(Kind kind, Component label, Component value) {
        static Stat header(String key, Object... args) {
            return new Stat(Kind.HEADER, Component.translatable(key, args), Component.empty());
        }

        static Stat header(Component text) {
            return new Stat(Kind.HEADER, text, Component.empty());
        }

        static Stat row(String key, Component value) {
            return new Stat(Kind.ROW, Component.translatable(key), value);
        }

        static Stat note(Component text) {
            return new Stat(Kind.NOTE, text, Component.empty());
        }
    }

    private static final String P = GroundZero.MODID + ".stats.";
    static final String H_FULL_RANGE = GZLang.add(P + "full_range", "At Full Range");
    static final String CLASS = GZLang.add(P + "class", "Class");
    static final String CRATER = GZLang.add(P + "crater", "Crater");
    static final String WRECKS = GZLang.add(P + "wrecks", "Wrecks");
    static final String LETHAL = GZLang.add(P + "lethal", "Lethal");
    static final String INJURES = GZLang.add(P + "injures", "Injures");
    static final String FIRES = GZLang.add(P + "fires", "Sets Fires");
    static final String YIELD = GZLang.add(P + "yield", "Yield");
    static final String FUZE = GZLang.add(P + "fuze", "Fuze");
    static final String BURST = GZLang.add(P + "burst", "Bursts At");
    static final String RANGE = GZLang.add(P + "range", "Range");
    static final String APEX = GZLang.add(P + "apex", "Climbs To");
    static final String TIME = GZLang.add(P + "time", "Flight Time");
    static final String LANDS = GZLang.add(P + "lands", "Lands Within");
    static final String EMP = GZLang.add(P + "emp", "EMP");
    static final String V_BLOCKS = GZLang.add(P + "blocks", "%s blocks");
    static final String V_UP = GZLang.add(P + "up", "%s blocks up");
    static final String V_SECONDS = GZLang.add(P + "seconds", "%s s");
    static final String V_KG = GZLang.add(P + "kg", "%s kg TNT");
    static final String V_TONNES = GZLang.add(P + "tonnes", "%s t TNT");
    static final String FALLOUT = GZLang.add(P + "fallout", "Fallout Radius");
    static final String FALLOUT_LASTS = GZLang.add(P + "fallout_lasts", "Fallout Lasts");
    /** The neutron warhead's burst, as rows (a note would run past the bottom of the panel at GUI scale 3). */
    static final String NEUTRONS = GZLang.add(P + "neutrons", "Neutrons Kill");
    static final String WITHERS = GZLang.add(P + "withers", "Plants Wither");
    static final String V_DAYS = GZLang.add(P + "days", "%s days");
    static final String V_DAY = GZLang.add(P + "day", "1 day");
    static final String V_HOURS = GZLang.add(P + "hours", "%s hours");
    static final String V_YIELD = GZLang.add(P + "yield_value", "%s");
    static final String NOTE_NUCLEAR = GZLang.add(P + "nuclear", "Fires only from a Launch Complex");
    static final String V_HARDENED = GZLang.add(P + "hardened", "Hardened");
    static final String V_UNHARDENED = GZLang.add(P + "unhardened", "May Fail");
    public static final String EMPTY_WARHEAD = GZLang.add(P + "empty_warhead", "Fit a payload core to see what it will do");
    public static final String EMPTY_MISSILE = GZLang.add(P + "empty_missile", "Fit a warhead, a body and a motor to see the missile's figures");

    /** What a warhead does beyond its blast (all as a missile delivers it: cluster types burst in the air). */
    private static final Map<ChargeKind, Component> EFFECTS = new EnumMap<>(ChargeKind.class);

    static {
        int spread = 20;                                   // airburst submunitions land within about 20 blocks of the burst
        effect(ChargeKind.THERMOBARIC_WARHEAD, "thermobaric", "A fuel-air blast, deadlier indoors");
        effect(ChargeKind.CLUSTER_WARHEAD, "cluster", "%s bomblets over a %s-block radius", Charges.CLUSTER_BOMBLETS + 8, spread);
        effect(ChargeKind.GAS_WARHEAD, "gas", "%s poison gas canisters over a %s-block radius", Charges.CLUSTER_CANISTERS, spread);
        effect(ChargeKind.SMOKE_WARHEAD, "smoke", "%s smoke canisters over a %s-block radius", Charges.CLUSTER_CANISTERS, spread);
        effect(ChargeKind.CRYO_WARHEAD, "cryo", "%s cryo canisters over a %s-block radius", Charges.CLUSTER_CANISTERS, spread);
        effect(ChargeKind.EMP_WARHEAD, "emp", "%s EMP canisters over a %s-block radius", Charges.CLUSTER_CANISTERS, spread);
        effect(ChargeKind.BUNKER_BUSTER, "bunker_buster", "Bores up to %s blocks down before it goes off", Charges.BUNKER_DEPTH);
        effect(ChargeKind.NAPALM_WARHEAD, "napalm", "A pool of napalm %s blocks round", (int) Charges.NAPALM_RADIUS);
        effect(ChargeKind.TOXIC_WASTE_WARHEAD, "toxic_waste", "Toxic sludge splashed %s blocks round", (int) Charges.TOXIC_WASTE_RADIUS);
        effect(ChargeKind.CHEMICAL_WARHEAD, "chemical", "An anthrax cloud %s blocks round that spreads and drifts", (int) Charges.CHEMICAL_RADIUS);
        effect(ChargeKind.DIRTY_WARHEAD, "dirty", "Scatters nuclear waste");          // one line: its missile's list is long
        effect(ChargeKind.COBALT_WARHEAD, "cobalt", "Fallout drifts %s blocks downwind", Charges.cobaltReach());
    }

    private static void effect(ChargeKind kind, String id, String english, Object... args) {
        EFFECTS.put(kind, Component.translatable(GZLang.add(P + "effect." + id, english), args));
    }

    private AssemblyStats() {}

    /** The warhead kind a stack is (null: not a warhead). */
    public static @Nullable ChargeKind kindOf(ItemStack stack) {
        // A warhead: one the bench assembles from a core, or one the Strategic Assembly Hall builds whole (no core).
        return stack.getItem() instanceof BlockItem b && b.getBlock() instanceof ChargeBlock c && (WarheadAssembly.coreOf(c) != null || c.kind.wholeWeapon())
                ? c.kind : null;
    }

    /** Cluster types: their burst is only a casing opening in the air; what matters is what it scatters. */
    private static boolean clusterType(ChargeKind kind) {
        return kind == ChargeKind.CLUSTER_WARHEAD || kind == ChargeKind.GAS_WARHEAD || kind == ChargeKind.SMOKE_WARHEAD
                || kind == ChargeKind.CRYO_WARHEAD || kind == ChargeKind.EMP_WARHEAD;
    }

    /** Warheads whose blast is their point, so a TNT figure means something. */
    private static boolean explosive(ChargeKind kind) {
        return kind == ChargeKind.HE_WARHEAD || kind == ChargeKind.HEAVY_WARHEAD || kind == ChargeKind.MASSIVE_WARHEAD
                || kind == ChargeKind.THERMOBARIC_WARHEAD || kind == ChargeKind.BUNKER_BUSTER || kind.nuclear();
    }

    /** The nuclear warheads' design yields (DESIGN.md §15), in place of the TNT figure from the crater. */
    private static @Nullable String nuclearYield(ChargeKind kind) {
        return switch (kind) {
            case TACTICAL_NUCLEAR_WARHEAD -> "300 t";
            case FISSION_WARHEAD -> "15 kt";
            case BOOSTED_FISSION_WARHEAD -> "45 kt";
            case NEUTRON_WARHEAD -> "1 kt";
            case THERMONUCLEAR_WARHEAD -> "1.2 Mt";
            case COBALT_WARHEAD -> "400 kt";
            case TSAR_WARHEAD -> "50 Mt";
            default -> null;
        };
    }

    private static Optional<BlastProfile> profile(RegistryAccess registries, ChargeKind kind) {
        return registries.lookupOrThrow(BlastProfile.REGISTRY).getOptional(Blasts.profile(kind.id));
    }

    /** A warhead's stats: its class, blast, what else it does, its fuze. Empty if the stack is not a warhead. */
    public static List<Stat> warhead(ItemStack warhead, RegistryAccess registries) {
        List<Stat> out = new ArrayList<>();
        ChargeKind kind = kindOf(warhead);
        if (kind == null) {
            return out;
        }
        SizeClass size = WarheadAssembly.sizeOf((ChargeBlock) ((BlockItem) warhead.getItem()).getBlock());
        if (size != null) {
            out.add(Stat.row(CLASS, Component.literal(size.name())));
        }
        blast(out, kind, registries, true);
        FuzeSettings fuze = warhead.getOrDefault(GZDataComponents.FUZE.get(), FuzeSettings.IMPACT);
        out.add(Stat.row(FUZE, Component.translatable(fuze.fuze().name)));
        if (fuze.fuze() == Fuze.BAROMETRIC) {
            out.add(Stat.row(BURST, Component.translatable(V_UP, fuze.value())));
        }
        return out;
    }

    /** The blast rows (all of them, or just crater and lethal), the yield and the warhead's other effect. */
    private static void blast(List<Stat> out, ChargeKind kind, RegistryAccess registries, boolean full) {
        Optional<BlastProfile> found = profile(registries, kind);
        if (!clusterType(kind) && found.isPresent()) {
            BlastProfile p = found.get();
            out.add(Stat.row(CRATER, blocks(p.crater())));
            if (full) {
                out.add(Stat.row(WRECKS, blocks(p.structural())));
            }
            out.add(Stat.row(LETHAL, blocks(p.lethal())));
            if (full) {
                out.add(Stat.row(INJURES, blocks(p.damage())));
                if (p.fire() > 0) {
                    out.add(Stat.row(FIRES, blocks(p.fire())));
                }
                if (explosive(kind)) {
                    String nuclear = nuclearYield(kind);
                    out.add(Stat.row(YIELD, nuclear != null ? Component.translatable(V_YIELD, nuclear) : tnt(p.crater())));
                }
            }
        }
        if (kind == ChargeKind.NEUTRON_WARHEAD) {          // its neutron burst (how walls stop it is in the Field Manual)
            out.add(Stat.row(NEUTRONS, Component.translatable(V_BLOCKS, (int) dev.groundzero.content.nuclear.NeutronBurst.LETHAL_RADIUS)));
            out.add(Stat.row(WITHERS, Component.translatable(V_BLOCKS, (int) dev.groundzero.content.nuclear.NeutronBurst.WITHER_RADIUS)));
        }
        if (kind.radiological()) {
            int ticks = Charges.contaminationTicks(kind);
            out.add(Stat.row(FALLOUT, Component.translatable(V_BLOCKS, (int) Charges.contaminationRadius(kind))));
            out.add(Stat.row(FALLOUT_LASTS, ticks < 24_000 ? Component.translatable(V_HOURS, ticks / 1000)
                    : ticks == 24_000 ? Component.translatable(V_DAY) : Component.translatable(V_DAYS, ticks / 24_000)));
            if (full && kind.nuclear()) {
                out.add(Stat.note(Component.translatable(NOTE_NUCLEAR)));
            }
        }
        Component effect = EFFECTS.get(kind);
        if (effect != null) {
            out.add(Stat.note(effect));
        }
    }

    /** A missile's stats: its warhead (name, crater, lethal radius, effect, fuze), then its flight at full range. */
    public static List<Stat> missile(ItemStack missile, RegistryAccess registries) {
        List<Stat> out = new ArrayList<>();
        MissileSpec spec = missile.get(GZDataComponents.MISSILE.get());
        if (spec == null) {
            return out;
        }
        ChargeKind kind = kindOf(spec.warhead());
        out.add(Stat.header(spec.warhead().getHoverName()));
        if (kind != null) {
            blast(out, kind, registries, false);
        }
        FuzeSettings fuze = spec.warhead().getOrDefault(GZDataComponents.FUZE.get(), FuzeSettings.IMPACT);
        out.add(Stat.row(FUZE, fuze.fuze() == Fuze.BAROMETRIC ? Component.translatable(V_UP, fuze.value()) : Component.translatable(fuze.fuze().name)));
        int range = spec.range();
        out.add(Stat.header(H_FULL_RANGE));
        out.add(Stat.row(RANGE, Component.translatable(V_BLOCKS, range)));
        out.add(Stat.row(APEX, Component.translatable(V_BLOCKS, (int) Math.round(Flights.apex(range)))));
        out.add(Stat.row(TIME, Component.translatable(V_SECONDS, Math.round(Flights.flightTicks(range) / 20.0))));
        out.add(Stat.row(LANDS, Component.translatable(V_BLOCKS, (int) Math.round(spec.guidance().scatter(range)))));
        out.add(Stat.row(EMP, Component.translatable(spec.hardened() ? V_HARDENED : V_UNHARDENED)));
        return out;
    }

    /** "28 blocks", "1.5 blocks". */
    private static Component blocks(float radius) {
        String n = radius == Math.rint(radius) ? Integer.toString((int) radius) : String.format(Locale.ROOT, "%.1f", radius);
        return Component.translatable(V_BLOCKS, n);
    }

    /**
     * A flavour TNT figure from the crater: half a kilogram times the crater radius cubed (the Massive Ordnance Warhead,
     * crater 28, comes to about 11 t, like the real GBU-43).
     */
    static Component tnt(float crater) {
        double kg = 0.5 * crater * crater * crater;
        if (kg < 1000) {
            return Component.translatable(V_KG, (int) (Math.round(kg / 10) * 10));
        }
        double t = kg / 1000;
        return Component.translatable(V_TONNES, t < 10 ? String.format(Locale.ROOT, "%.1f", t) : Long.toString(Math.round(t)));
    }
}
