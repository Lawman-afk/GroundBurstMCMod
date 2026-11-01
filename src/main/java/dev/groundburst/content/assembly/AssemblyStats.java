package dev.groundburst.content.assembly;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.BlastProfile;
import dev.groundburst.blast.Blasts;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.ordnance.charge.ChargeBlock;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.content.ordnance.charge.Charges;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
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

    private static final String P = GroundBurst.MODID + ".stats.";
    static final String H_FULL_RANGE = GBLang.add(P + "full_range", "At Full Range");
    static final String CLASS = GBLang.add(P + "class", "Class");
    static final String CRATER = GBLang.add(P + "crater", "Crater");
    static final String WRECKS = GBLang.add(P + "wrecks", "Wrecks");
    static final String LETHAL = GBLang.add(P + "lethal", "Lethal");
    static final String INJURES = GBLang.add(P + "injures", "Injures");
    static final String FIRES = GBLang.add(P + "fires", "Sets Fires");
    static final String YIELD = GBLang.add(P + "yield", "Yield");
    static final String FUZE = GBLang.add(P + "fuze", "Fuze");
    static final String RVS = GBLang.add(P + "rvs", "Re-entry Vehicles");
    static final String V_RVS = GBLang.add(P + "rvs_value", "%s, load %s/%s");
    static final String MIRV_SPREAD = GBLang.add(P + "spread", "Spread");
    static final String V_SPREAD = GBLang.add(P + "spread_value", "%s, %s blocks apart");
    static final String V_ON_AIM = GBLang.add(P + "on_aim", "All on the aim");
    static final String H_RV = GBLang.add(P + "rv_header", "%sx %s");
    static final String BURST = GBLang.add(P + "burst", "Bursts At");
    static final String RANGE = GBLang.add(P + "range", "Range");
    static final String APEX = GBLang.add(P + "apex", "Climbs To");
    static final String TIME = GBLang.add(P + "time", "Flight Time");
    static final String LANDS = GBLang.add(P + "lands", "Lands Within");
    static final String EMP = GBLang.add(P + "emp", "EMP");
    static final String V_BLOCKS = GBLang.add(P + "blocks", "%s blocks");
    /** Laser guidance: it homes on a designator's spot; without one, it lands within its inertial scatter. */
    static final String HOMES = GBLang.add(P + "homes", "Homes On"), ELSE = GBLang.add(P + "else", "Else Within");
    static final String V_SPOT = GBLang.add(P + "laser_spot", "A Laser Spot");
    static final String V_UP = GBLang.add(P + "up", "%s blocks up");
    static final String V_SECONDS = GBLang.add(P + "seconds", "%s s");
    static final String V_KG = GBLang.add(P + "kg", "%s kg TNT");
    static final String V_TONNES = GBLang.add(P + "tonnes", "%s t TNT");
    static final String FALLOUT = GBLang.add(P + "fallout", "Fallout Radius");
    static final String FALLOUT_LASTS = GBLang.add(P + "fallout_lasts", "Fallout Lasts");
    /** The neutron warhead's burst, as rows (a note would run past the bottom of the panel at GUI scale 3). */
    static final String NEUTRONS = GBLang.add(P + "neutrons", "Neutrons Kill");
    static final String WITHERS = GBLang.add(P + "withers", "Plants Wither");
    /** The antimatter warhead's sphere and gamma flash, as rows. */
    static final String ERASES = GBLang.add(P + "erases", "Erases");
    static final String GAMMA_KILLS = GBLang.add(P + "gamma_kills", "Gamma Kills");
    static final String GAMMA_HURTS = GBLang.add(P + "gamma_hurts", "Gamma Injures");
    /** The singularity warhead's black hole, as rows. */
    static final String HORIZON = GBLang.add(P + "horizon", "Event Horizon");
    static final String PULLS = GBLang.add(P + "pulls", "Pulls In");
    static final String TEARS = GBLang.add(P + "tears", "Tears Loose");
    static final String COLLAPSES = GBLang.add(P + "collapses", "Collapses After");
    static final String NOTE_FEEDS = GBLang.add(P + "feeds", "Grows with what it eats: these on open ground");
    /** The storm warhead's supercell, as rows. */
    static final String REGION = GBLang.add(P + "region", "Storm Region");
    static final String LIGHTNING = GBLang.add(P + "lightning", "Lightning");
    static final String RAGES = GBLang.add(P + "rages", "Rages For");
    static final String V_STRIKES = GBLang.add(P + "strikes", "%s a second");
    static final String V_DAYS = GBLang.add(P + "days", "%s days");
    static final String V_DAY = GBLang.add(P + "day", "1 day");
    static final String V_HOURS = GBLang.add(P + "hours", "%s hours");
    static final String V_YIELD = GBLang.add(P + "yield_value", "%s");
    static final String NOTE_NUCLEAR = GBLang.add(P + "nuclear", "Fires from a Launch Complex or a Missile Silo");
    static final String V_HARDENED = GBLang.add(P + "hardened", "Hardened");
    static final String V_UNHARDENED = GBLang.add(P + "unhardened", "May Fail");
    public static final String EMPTY_WARHEAD = GBLang.add(P + "empty_warhead", "Fit a payload core to see what it will do");
    public static final String EMPTY_MISSILE = GBLang.add(P + "empty_missile", "Fit a warhead, a body and a motor to see the missile's figures");

    /** What a warhead does beyond its blast (all as a missile delivers it: cluster types burst in the air). */
    private static final Map<ChargeKind, Component> EFFECTS = new EnumMap<>(ChargeKind.class);

    /** Airburst submunitions land within about this many blocks of the burst. */
    static final int SPREAD = 20;

    static {
        int spread = SPREAD;
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
        effect(ChargeKind.SCUD_STORM_WARHEAD, "scud_storm", "Toxin residue spreads %s blocks round, under a toxin cloud", (int) Charges.SCUD_SPREAD);
        effect(ChargeKind.NUCLEAR_MISSILE_WARHEAD, "nuclear_missile", "Fired only by a Nuclear Missile silo");
        effect(ChargeKind.ANTIMATTER_WARHEAD, "antimatter", "A perfect sphere erased: no rubble, no drops, no fallout");
        effect(ChargeKind.SINGULARITY_WARHEAD, "singularity", "A black hole: it feeds and grows, then implodes");
        effect(ChargeKind.STORM_WARHEAD, "storm", "A supercell: lightning chains through all in the open, a region round");
    }

    private static void effect(ChargeKind kind, String id, String english, Object... args) {
        EFFECTS.put(kind, Component.translatable(GBLang.add(P + "effect." + id, english), args));
    }

    private AssemblyStats() {}

    /** The warhead kind a stack is (null: not a warhead). */
    public static @Nullable ChargeKind kindOf(ItemStack stack) {
        // A warhead: one the bench assembles from a core, or one the ICBM Assembly Hall builds whole (no core).
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
                || kind == ChargeKind.THERMOBARIC_WARHEAD || kind == ChargeKind.BUNKER_BUSTER || kind == ChargeKind.SCUD_STORM_WARHEAD
                || kind.nuclear();
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
            case NUCLEAR_MISSILE_WARHEAD -> "20 kt";
            default -> null;
        };
    }

    private static Optional<BlastProfile> profile(RegistryAccess registries, ChargeKind kind) {
        return profile(registries, kind.id);
    }

    private static Optional<BlastProfile> profile(RegistryAccess registries, String id) {
        return registries.lookupOrThrow(BlastProfile.REGISTRY).getOptional(Blasts.profile(id));
    }

    /**
     * How far round where it comes down a charge does harm, blocks (the strike circles on the Launch Computer's map):
     * inside {@code lethal} an exposed body dies; {@code reach} is the farthest it hurts, wrecks or burns anything. A type
     * that scatters bomblets or canisters covers the ground they land on and what each does round it.
     */
    public record Area(float lethal, float reach) {
        public static final Area NONE = new Area(0, 0);
    }

    /** The {@link Area} of the charge a stack is (none for anything else). */
    public static Area area(ItemStack charge, RegistryAccess registries) {
        return charge.getItem() instanceof BlockItem b && b.getBlock() instanceof ChargeBlock c ? area(c.kind, registries) : Area.NONE;
    }

    public static Area area(ChargeKind kind, RegistryAccess registries) {
        if (clusterType(kind)) {
            float each = switch (kind) {
                case CLUSTER_WARHEAD -> profile(registries, "cluster_bomblet").map(BlastProfile::damage).orElse(4f);
                case CRYO_WARHEAD -> dev.groundburst.content.ordnance.ThrownExplosive.CRYO_RADIUS;
                case EMP_WARHEAD -> dev.groundburst.content.ordnance.ThrownExplosive.EMP_RADIUS;
                default -> dev.groundburst.content.ordnance.ThrownExplosive.SMOKE_RADIUS;     // gas and smoke clouds
            };
            boolean kills = kind == ChargeKind.CLUSTER_WARHEAD || kind == ChargeKind.GAS_WARHEAD;
            return new Area(kills ? SPREAD : 0, SPREAD + each);
        }
        float lethal = 0, reach = 0;
        Optional<BlastProfile> found = profile(registries, kind);
        if (found.isPresent()) {
            BlastProfile p = found.get();
            lethal = p.lethal();
            reach = Math.max(p.damage(), Math.max(p.structural(), p.fire()));
        }
        float effect = switch (kind) {
            case THERMOBARIC_WARHEAD -> Charges.THERMOBARIC_REACH;
            case NAPALM_WARHEAD -> Charges.NAPALM_RADIUS;
            case TOXIC_WASTE_WARHEAD -> Charges.TOXIC_WASTE_RADIUS;
            case CHEMICAL_WARHEAD -> Charges.CHEMICAL_RADIUS;
            case SCUD_STORM_WARHEAD -> Charges.SCUD_SPREAD;
            case DIRTY_WARHEAD -> Charges.contaminationRadius(kind);              // its waste is its point
            case NEUTRON_WARHEAD -> dev.groundburst.content.nuclear.NeutronBurst.WITHER_RADIUS;
            default -> 0;
        };
        if (kind == ChargeKind.NEUTRON_WARHEAD) {
            lethal = Math.max(lethal, dev.groundburst.content.nuclear.NeutronBurst.LETHAL_RADIUS);
        }
        if (kind == ChargeKind.SINGULARITY_WARHEAD) {    // nothing escapes from well inside its pull
            float hole = dev.groundburst.blast.Singularities.TYPICAL_REACH;
            return new Area(dev.groundburst.blast.Singularities.stoneReach(hole), dev.groundburst.blast.Singularities.pullRadius(hole));
        }
        if (kind == ChargeKind.STORM_WARHEAD) {          // its region: lightning anywhere in it
            return new Area(0, dev.groundburst.blast.Storms.RADIUS);
        }
        if (kind == ChargeKind.ANTIMATTER_WARHEAD) {     // its gamma flash, in the open
            return new Area(dev.groundburst.content.exotic.Antimatter.GAMMA_LETHAL, dev.groundburst.content.exotic.Antimatter.GAMMA_REACH);
        }
        return new Area(lethal, Math.max(Math.max(reach, effect), lethal));
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
        FuzeSettings fuze = warhead.getOrDefault(GBDataComponents.FUZE.get(), FuzeSettings.IMPACT);
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
        if (kind == ChargeKind.SINGULARITY_WARHEAD) {      // its black hole (its collapse's blast is under its profile)
            float reach = dev.groundburst.blast.Singularities.TYPICAL_REACH;
            out.add(Stat.row(HORIZON, blocks(dev.groundburst.blast.Singularities.horizon(reach))));
            out.add(Stat.row(PULLS, blocks(dev.groundburst.blast.Singularities.pullRadius(reach))));
            if (full) {
                out.add(Stat.row(TEARS, blocks(dev.groundburst.blast.Singularities.stoneReach(reach))));
                out.add(Stat.row(COLLAPSES, Component.translatable(V_SECONDS, dev.groundburst.blast.Singularities.LIFE / 20)));
                out.add(Stat.note(Component.translatable(NOTE_FEEDS)));
                out.add(Stat.note(Component.translatable(NOTE_NUCLEAR)));
            }
        }
        if (kind == ChargeKind.STORM_WARHEAD) {            // its supercell
            out.add(Stat.row(REGION, blocks(dev.groundburst.blast.Storms.RADIUS)));
            out.add(Stat.row(LIGHTNING, Component.translatable(V_STRIKES, (int) dev.groundburst.blast.Storms.STRIKES)));
            if (full) {
                out.add(Stat.row(RAGES, Component.translatable(V_SECONDS, dev.groundburst.blast.Storms.LIFE / 20)));
                out.add(Stat.note(Component.translatable(NOTE_NUCLEAR)));
            }
        }
        if (kind == ChargeKind.ANTIMATTER_WARHEAD) {       // its sphere and its gamma flash (walls stop the flash)
            out.add(Stat.row(ERASES, blocks(dev.groundburst.content.exotic.Antimatter.RADIUS)));
            out.add(Stat.row(GAMMA_KILLS, blocks(dev.groundburst.content.exotic.Antimatter.GAMMA_LETHAL)));
            if (full) {
                out.add(Stat.row(GAMMA_HURTS, blocks(dev.groundburst.content.exotic.Antimatter.GAMMA_REACH)));
                out.add(Stat.note(Component.translatable(NOTE_NUCLEAR)));
            }
        }
        if (kind == ChargeKind.NEUTRON_WARHEAD) {          // its neutron burst (how walls stop it is in the Field Manual)
            out.add(Stat.row(NEUTRONS, Component.translatable(V_BLOCKS, (int) dev.groundburst.content.nuclear.NeutronBurst.LETHAL_RADIUS)));
            out.add(Stat.row(WITHERS, Component.translatable(V_BLOCKS, (int) dev.groundburst.content.nuclear.NeutronBurst.WITHER_RADIUS)));
        }
        if (kind.radiological()) {
            int ticks = Charges.contaminationTicks(kind);
            out.add(Stat.row(FALLOUT, Component.translatable(V_BLOCKS, (int) Charges.contaminationRadius(kind))));
            out.add(Stat.row(FALLOUT_LASTS, ticks < 24_000 ? Component.translatable(V_HOURS, ticks / 1000)
                    : ticks == 24_000 ? Component.translatable(V_DAY) : Component.translatable(V_DAYS, ticks / 24_000)));
            if (full && kind.nuclear() && !kind.superweapon()) {
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
        MissileSpec spec = missile.get(GBDataComponents.MISSILE.get());
        if (spec == null) {
            return out;
        }
        if (spec.mirv()) {
            mirv(out, spec.warhead(), registries);
        } else {
            ChargeKind kind = kindOf(spec.warhead());
            out.add(Stat.header(spec.warhead().getHoverName()));
            if (kind != null) {
                blast(out, kind, registries, false);
            }
            fuze(out, spec.warhead());
        }
        int range = spec.range();
        out.add(Stat.header(H_FULL_RANGE));
        out.add(Stat.row(RANGE, Component.translatable(V_BLOCKS, range)));
        out.add(Stat.row(APEX, Component.translatable(V_BLOCKS, (int) Math.round(Flights.apex(range)))));
        out.add(Stat.row(TIME, Component.translatable(V_SECONDS, Math.round(Flights.flightTicks(range) / 20.0))));
        int scatter = (int) Math.round(spec.guidance().scatter(range));
        if (spec.guidance() == MissileParts.Guidance.LASER) {
            out.add(Stat.row(HOMES, Component.translatable(V_SPOT)));
            out.add(Stat.row(ELSE, Component.translatable(V_BLOCKS, scatter)));
        } else {
            out.add(Stat.row(LANDS, Component.translatable(V_BLOCKS, scatter)));
        }
        out.add(Stat.row(EMP, Component.translatable(spec.hardened() ? V_HARDENED : V_UNHARDENED)));
        return out;
    }

    private static void fuze(List<Stat> out, ItemStack warhead) {
        FuzeSettings fuze = warhead.getOrDefault(GBDataComponents.FUZE.get(), FuzeSettings.IMPACT);
        out.add(Stat.row(FUZE, fuze.fuze() == Fuze.BAROMETRIC ? Component.translatable(V_UP, fuze.value()) : Component.translatable(fuze.fuze().name)));
    }

    /**
     * A MIRV's bus (DESIGN.md §17.14): how many RVs and how much of its load, its spread, then each kind of RV it carries
     * (how many, and its warhead's stats and fuze; a decoy just its name).
     */
    private static void mirv(List<Stat> out, ItemStack bus, RegistryAccess registries) {
        out.add(Stat.header(bus.getHoverName()));
        SizeClass size = Mirv.classOf(bus);
        List<ItemStack> rvs = Mirv.rvs(bus);
        out.add(Stat.row(RVS, Component.translatable(V_RVS, rvs.size(), Mirv.loadOf(bus), size == null ? 0 : Mirv.capacity(size))));
        Mirv.Spread spread = Mirv.spread(bus);
        out.add(Stat.row(MIRV_SPREAD, spread.pattern() == dev.groundburst.content.launch.LaunchNetwork.Pattern.POINT || spread.spacing() == 0
                ? Component.translatable(V_ON_AIM)
                : Component.translatable(V_SPREAD, Component.translatable(Mirv.T_PATTERN[spread.pattern().ordinal()]), spread.spacing())));
        java.util.Map<net.minecraft.world.item.Item, int[]> counts = new java.util.LinkedHashMap<>();
        java.util.Map<net.minecraft.world.item.Item, ItemStack> first = new java.util.LinkedHashMap<>();
        for (ItemStack rv : rvs) {
            counts.computeIfAbsent(rv.getItem(), k -> new int[1])[0]++;
            first.putIfAbsent(rv.getItem(), rv);
        }
        counts.forEach((item, n) -> {
            ItemStack rv = first.get(item);
            out.add(Stat.header(Component.translatable(H_RV, n[0], rv.getHoverName())));
            ChargeKind kind = kindOf(rv);
            if (kind != null) {
                blast(out, kind, registries, false);
                fuze(out, rv);
            }
        });
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
