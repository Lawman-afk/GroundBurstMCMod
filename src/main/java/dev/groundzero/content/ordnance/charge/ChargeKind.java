package dev.groundzero.content.ordnance.charge;

/**
 * The placed charges, mines and bombs (DESIGN.md §14, Phase 6 slices 6b-6d): what each sticks to, whether it has a frequency
 * and a timer, and what sets it off by itself. Once armed, a Remote Detonator on its frequency (or redstone) sets it
 * off, or, if it is on its timer, starts the countdown; mines go off when stepped on or tripped.
 *
 * @param id       block and item id ({@code groundzero:<id>}), and the blast profile for those that make a blast
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
    /** A 300 t tactical nuke: crater 42 (half as big again as the MOW's), deadly to 60, contamination 90 blocks round for a day. */
    TACTICAL_NUCLEAR_WARHEAD("tactical_nuclear_warhead", 10, true),
    /** A 15 kt fission warhead: crater 75, deadly to 120, contamination 180 blocks round for two days. */
    FISSION_WARHEAD("fission_warhead", 10, true);

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

    /**
     * A cluster warhead: carried by a missile, it bursts high over its target and rains its submunitions down over a
     * wide area (set off on the ground, it throws them out round itself).
     */
    public boolean nuclear() {
        return this == TACTICAL_NUCLEAR_WARHEAD || this == FISSION_WARHEAD;
    }

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
