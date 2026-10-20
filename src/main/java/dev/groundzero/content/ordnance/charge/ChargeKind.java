package dev.groundzero.content.ordnance.charge;

/**
 * The placed charges and mines (DESIGN.md §14, Phase 6 slices 6b, 6c): what each sticks to, whether it has a frequency
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
    CLAYMORE("claymore", false, true, false, 0, Sensor.TRIPWIRE);

    /** What sets it off by itself once armed (after a few seconds' grace to get clear). */
    public enum Sensor { NONE, PRESSURE, TRIPWIRE }

    public final String id;
    public final boolean anyFace;
    public final boolean remote;
    public final boolean timer;
    public final int seconds;
    public final Sensor sensor;

    ChargeKind(String id, boolean anyFace, boolean remote, boolean timer, int seconds) {
        this(id, anyFace, remote, timer, seconds, Sensor.NONE);
    }

    ChargeKind(String id, boolean anyFace, boolean remote, boolean timer, int seconds, Sensor sensor) {
        this.id = id;
        this.anyFace = anyFace;
        this.remote = remote;
        this.timer = timer;
        this.seconds = seconds;
        this.sensor = sensor;
    }

    /** Placed facing the way the player looks (its front away from them): the claymore. Others face the player. */
    public boolean facesAway() {
        return this == CLAYMORE;
    }

    /** Whether it can be set to go off at once or on its timer (the timed bomb is always on its timer). */
    public boolean fuzeChoice() {
        return this.timer && this != TIMED_BOMB;
    }

    /** The fuze it starts with, and the only one it has if there is no choice. */
    public ChargeBlockEntity.Mode defaultMode() {
        return this.timer && !fuzeChoice() ? ChargeBlockEntity.Mode.TIMER : ChargeBlockEntity.Mode.REMOTE;
    }

    public static final int MAX_TIMER = 600, MAX_FREQUENCY = 9999;
}
