package dev.groundburst.content.ordnance.charge;

/**
 * The placed charges, mines and bombs (DESIGN.md §14, Phase 6 slices 6b-6d): what each sticks to, whether it has a frequency
 * and a timer, and what sets it off by itself. Once armed, a Remote Detonator on its frequency (or redstone) sets it
 * off, or, if it is on its timer, starts the countdown; mines go off when stepped on or tripped.
 *
 * @param id       block and item id ({@code groundburst:<id>}), and the blast profile for those that make a blast
 * @param anyFace  sticks to any face of a block (the timed bomb stands on the floor instead)
 * @param remote   has a frequency (all of them do)
 * @param timer    has a timer
 * @param seconds  default timer, seconds
 * @param sensor   what sets it off by itself once armed (mines: pressure; the claymore: a tripwire)
 */
public enum ChargeKind {
    /** Plastic explosive: sticks to any face; up to four stack on one spot for a bigger blast. Goes off at once. */
    C4("c4", true, true, false, 10),
    /** A frame of det-cord on a plate: cuts a 3x3 hole 3 deep into what it is on, and nothing more. At once or on a timer. */
    BREACHING("breaching_charge", true, true, true, 5),
    /** A pot of thermite: burns a 1x1 shaft 8 deep into what it is on, through anything short of obsidian. At once or on a timer. */
    THERMITE("thermite_charge", true, true, true, 5),
    /** A steel box of dynamite on a clock: always on its timer, and once counting it can only be stopped by cutting the right wire. */
    TIMED_BOMB("timed_bomb", false, true, true, 30),
    /** A pressure mine half buried in the ground: anything that steps on it once it is armed sets it off. */
    LANDMINE("landmine", false, false, false, 0, Sensor.PRESSURE),
    /** Stepped on, it pops its canister up to waist height, which bursts into 30 fragments. */
    BOUNCING_MINE("bouncing_mine", false, false, false, 0, Sensor.PRESSURE),
    /** A curved box of fragments facing the enemy: a laser tripwire (or a detonator on its frequency) fires them in a 60 degree cone. */
    CLAYMORE("claymore", false, true, false, 0, Sensor.TRIPWIRE),
    // Warheads (slice 6d; once bombs): fitted to missiles; placed, they stand nose up, fall when unsupported and, armed,
    // go off when they land or are hit.
    /** General-purpose high explosive, small: crater 6. */
    HE_WARHEAD("he_warhead", 10, true),
    /** The heavy general-purpose warhead: crater 12. */
    HEAVY_WARHEAD("heavy_warhead", 10, true),
    /** Disperses a fuel cloud, then ignites it 0.6 s later: a weak crater but a huge, burning overpressure, worse indoors. */
    THERMOBARIC_WARHEAD("thermobaric_warhead", 10, true),
    /** Bursts and scatters 24 bomblets over about 18 blocks; one in twenty is a dud that lies there as a live mine. */
    CLUSTER_WARHEAD("cluster_warhead", 10, true),
    /** Crater 9 where it stands; delivered by a missile it first bores up to 40 blocks down (obsidian costs 10). */
    BUNKER_BUSTER("bunker_buster_warhead", 10, true),
    /** A small burst and a pool of napalm 10 blocks round that burns for 30 s, rain or not. */
    NAPALM_WARHEAD("napalm_warhead", 10, true),
    /** The biggest conventional warhead: crater 28, deadly out to 40. */
    MASSIVE_WARHEAD("massive_warhead", 10, true),
    /** A cluster of poison gas canisters: 14 gas clouds over a wide area. */
    GAS_WARHEAD("gas_warhead", 10, true),
    /** A cluster of smoke canisters: 14 smoke clouds over a wide area, screening it. */
    SMOKE_WARHEAD("smoke_warhead", 10, true),
    /** A cluster of cryo canisters: 14 bursts of cold over a wide area, freezing water, fire and everything in them. */
    CRYO_WARHEAD("cryo_warhead", 10, true),
    /** A cluster of EMP canisters: 14 EMP fields over a wide area, shutting down machines and making fuzes safe. */
    EMP_WARHEAD("emp_warhead", 10, true),
    /** A burst of toxic waste: sludge splashed 10 blocks round, a heavy dose of toxin for anything in it. */
    TOXIC_WASTE_WARHEAD("toxic_waste_warhead", 10, true),
    /** A chemical agent (anthrax) let out over 9 blocks round: a heavy cloud that spreads, sinks and drifts for a minute and more. */
    CHEMICAL_WARHEAD("chemical_warhead", 10, true),
    // Nuclear warheads (Phase 8): full yield only delivered by a missile from a Launch Complex. Placed, dropped, shot or
    // caught in another blast, a nuclear warhead fizzles: its explosive lenses go off unevenly, the core breaks up and
    // scatters (a small blast and a patch of contamination), and there is no nuclear explosion.
    /** A 300 t tactical nuke (class XL): crater 42 (half as big again as the MOW's), deadly to 60, contamination 90 blocks round for a day. */
    TACTICAL_NUCLEAR_WARHEAD("tactical_nuclear_warhead", 10, true),
    /** A 15 kt fission warhead (class XL): crater 75, deadly to 120, contamination 180 blocks round for two days. */
    FISSION_WARHEAD("fission_warhead", 10, true),
    /** A 45 kt boosted fission warhead (class XL): crater 95, deadly to 150, contamination 220 blocks round for two days. */
    BOOSTED_FISSION_WARHEAD("boosted_fission_warhead", 10, true),
    /** A 1 kt neutron warhead (class XL): a small blast, but radiation deadly to 180 blocks through all but thick walls. */
    NEUTRON_WARHEAD("neutron_warhead", 10, true),
    /**
     * A dirty warhead (class S; radiological, not nuclear): a small HE blast that scatters nuclear waste, contamination
     * 90 blocks round for five days. It goes off however it is set off, and any silo fires it.
     */
    DIRTY_WARHEAD("dirty_warhead", 10, true),
    /**
     * A 1.2 Mt two-stage thermonuclear warhead (slice 8c): crater 150, deadly to 260, contamination 360 blocks round for
     * three days. Only ever built whole, into an intercontinental missile, at the ICBM Assembly Hall.
     */
    THERMONUCLEAR_WARHEAD("thermonuclear_warhead", 10, true),
    /**
     * A 400 kt cobalt-salted thermonuclear warhead (slice 8c): a smaller blast, but its fallout spreads far downwind and
     * lasts a month. Built whole like the thermonuclear warhead.
     */
    COBALT_WARHEAD("cobalt_warhead", 10, true),
    /**
     * The 50 Mt Tsar Mega-bomb (slice 8d), the biggest weapon there is: a three-stage thermonuclear bomb (crater 250,
     * wrecks to 380, burns to 450, deadly to 450, injures to 700; contamination 600 blocks round for three days). Built
     * whole like the thermonuclear warhead, a Tertiary Stage in place of the jacket.
     */
    TSAR_WARHEAD("tsar_warhead", 10, true),
    // The superweapons' warheads (DESIGN.md §19.5): never built on their own, only whole into their superweapon's
    // ammunition at its factory, and fired only by that superweapon.
    /**
     * A toxin SCUD's warhead (the GLA's SCUD Storm fires nine): a heavy blast, then a cloud of toxin welling up out of
     * the crater and a front of glowing anthrax residue spreading out over the ground round it.
     */
    SCUD_STORM_WARHEAD("scud_storm_warhead", 10, true),
    /**
     * China's Nuclear Missile's warhead: a nuclear blast between the fission and the boosted fission warheads', and the
     * game's radiation field round it (contamination 200 blocks round for two days).
     */
    NUCLEAR_MISSILE_WARHEAD("nuclear_missile_warhead", 10, true),
    // Exotic warheads (Phase 11, DESIGN.md §16): class XL like the nuclear ones, on the Strategic missile only, and their
    // full effect only on a missile's arrival; set off any other way their containment fails and lets out a little.
    // (Added at the end so no kind before them changes its place.)
    /**
     * An antimatter trap (DESIGN.md §16.3): a white sphere erases everything within 48 blocks, no rubble, no drops, no
     * fire, whatever its blast resistance; its gamma flash kills anything in the open out to 64 and hurts it to 96.
     */
    ANTIMATTER_WARHEAD("antimatter_warhead", 10, true),
    /**
     * A singularity core (DESIGN.md §16.2): a black hole that grows for 20 s to an event horizon of 10 blocks, drawing in
     * everything within 48 and tearing the ground loose out to 32, then collapses in a blast (crater 16).
     */
    SINGULARITY_WARHEAD("singularity_warhead", 10, true),
    /**
     * A Tesla storm generator (DESIGN.md §16.14): a supercell over a region 160 blocks round for a minute and a half,
     * lightning chaining through whatever stands in the open, its winds driving everything round its eye.
     */
    STORM_WARHEAD("storm_warhead", 10, true),
    /**
     * A cryogenic heat sink (DESIGN.md §16.16): exotic matter held near absolute zero, let go. A white front of frozen air
     * rolls out 128 blocks over the land, freezing everything it passes; within 40 of where it struck nothing lives, and
     * spires of ice burst out of the ground; a cold snap holds the region for three minutes.
     */
    CRYOGENIC_WARHEAD("cryogenic_warhead", 10, true);

    /** What sets it off by itself once armed (from the moment it is armed: arm it from a step away). */
    public enum Sensor { NONE, PRESSURE, TRIPWIRE }

    public final String id;
    public final boolean anyFace;
    public final boolean remote;
    public final boolean timer;
    public final int seconds;
    public final Sensor sensor;
    /** A bomb: falls when unsupported, and its impact fuze sets it off when it lands or is hit (once armed). */
    public final boolean bomb;

    ChargeKind(String id, boolean anyFace, boolean remote, boolean timer, int seconds) {
        this(id, anyFace, remote, timer, seconds, Sensor.NONE, false);
    }

    ChargeKind(String id, boolean anyFace, boolean remote, boolean timer, int seconds, Sensor sensor) {
        this(id, anyFace, remote, timer, seconds, sensor, false);
    }

    /** A bomb: a frequency and a timer, on the floor, nose away from the player. */
    ChargeKind(String id, int seconds, boolean bomb) {
        this(id, false, true, true, seconds, Sensor.NONE, bomb);
    }

    ChargeKind(String id, boolean anyFace, boolean remote, boolean timer, int seconds, Sensor sensor, boolean bomb) {
        this.id = id;
        this.anyFace = anyFace;
        this.remote = remote;
        this.timer = timer;
        this.seconds = seconds;
        this.sensor = sensor;
        this.bomb = bomb;
    }

    /** A nuclear warhead: its full yield only when a missile delivers it; set off any other way it fizzles. */
    public boolean nuclear() {
        return switch (this) {
            case TACTICAL_NUCLEAR_WARHEAD, FISSION_WARHEAD, BOOSTED_FISSION_WARHEAD, NEUTRON_WARHEAD, THERMONUCLEAR_WARHEAD, COBALT_WARHEAD,
                 TSAR_WARHEAD, NUCLEAR_MISSILE_WARHEAD -> true;
            default -> false;
        };
    }

    /**
     * An exotic warhead (Phase 11): antimatter, a singularity, a storm or a deep freeze. Only ever built whole into its ICBM, at the
     * Special Weapons Assembler from its core (a whole weapon), and goes off in full only on a missile's arrival; set off
     * any other way (a warhead placed in creative) its containment fails (a breach).
     */
    public boolean exotic() {
        return this == ANTIMATTER_WARHEAD || this == SINGULARITY_WARHEAD || this == STORM_WARHEAD || this == CRYOGENIC_WARHEAD;
    }

    /** Only a Launch Complex or a Missile Silo fires it: the nuclear warheads (on the Strategic body) and the exotic ones. */
    public boolean strategic() {
        return nuclear() || exotic();
    }

    /**
     * A warhead only ever built whole into its missile: the ICBM Assembly Hall's (the fusion ones) and the Special Weapons Assembler's (the exotic ones),
     * and the superweapons' ammunition.
     */
    public boolean wholeWeapon() {
        return this == THERMONUCLEAR_WARHEAD || this == COBALT_WARHEAD || this == TSAR_WARHEAD || exotic() || superweapon();
    }

    /** A superweapon's warhead (DESIGN.md §19.5): only its superweapon fires it. */
    public boolean superweapon() {
        return this == SCUD_STORM_WARHEAD || this == NUCLEAR_MISSILE_WARHEAD;
    }

    /** It leaves fallout (contamination): the nuclear warheads, and the dirty one. */
    public boolean radiological() {
        return nuclear() || this == DIRTY_WARHEAD;
    }

    /**
     * A cluster warhead: carried by a missile, it bursts high over its target and rains its submunitions down over a
     * wide area (set off on the ground, it throws them out round itself).
     */
    public boolean airburst() {
        return this == CLUSTER_WARHEAD || this == GAS_WARHEAD || this == SMOKE_WARHEAD || this == CRYO_WARHEAD || this == EMP_WARHEAD;
    }

    /** Placed facing the way the player looks (its front away from them): the claymore, and bombs (nose away). */
    public boolean facesAway() {
        return this == CLAYMORE || this.bomb;
    }

    /** Whether it can be set to go off at once or on its timer (the timed bomb is always on its timer). */
    public boolean fuzeChoice() {
        return this.timer && this != TIMED_BOMB && !this.bomb;            // a warhead's fuze is fitted at the bench
    }

    /** The fuze it starts with, and the only one it has if there is no choice. */
    public ChargeBlockEntity.Mode defaultMode() {
        return this == TIMED_BOMB ? ChargeBlockEntity.Mode.TIMER : ChargeBlockEntity.Mode.REMOTE;
    }

    public static final int MAX_TIMER = 600, MAX_FREQUENCY = 9999;
}
