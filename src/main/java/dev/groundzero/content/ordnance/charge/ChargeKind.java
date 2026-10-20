package dev.groundzero.content.ordnance.charge;

/**
 * The placed charges (DESIGN.md §14, Phase 6 slice 6b): what each sticks to, whether it has a timer, and its default
 * time. Every charge has a frequency: once armed, a Remote Detonator on it (or redstone) sets it off, or, if it is on
 * its timer, starts the countdown.
 *
 * @param id       block and item id ({@code groundzero:<id>}), and the blast profile for those that make a blast
 * @param anyFace  sticks to any face of a block (the timed bomb stands on the floor instead)
 * @param remote   has a frequency (all of them do)
 * @param timer    has a timer
 * @param seconds  default timer, seconds
 */
public enum ChargeKind {
    /** Plastic explosive: sticks to any face; up to four stack on one spot for a bigger blast. Goes off at once. */
    C4("c4", true, true, false, 10),
    /** A frame of det-cord on a plate: cuts a 3x3 hole 3 deep into what it is on, and nothing more. At once or on a timer. */
    BREACHING("breaching_charge", true, true, true, 5),
    /** A pot of thermite: burns a 1x1 shaft 8 deep into what it is on, through anything short of obsidian. At once or on a timer. */
    THERMITE("thermite_charge", true, true, true, 5),
    /** A steel box of dynamite on a clock: always on its timer, and once counting it can only be stopped by cutting the right wire. */
    TIMED_BOMB("timed_bomb", false, true, true, 30);

    public final String id;
    public final boolean anyFace;
    public final boolean remote;
    public final boolean timer;
    public final int seconds;

    ChargeKind(String id, boolean anyFace, boolean remote, boolean timer, int seconds) {
        this.id = id;
        this.anyFace = anyFace;
        this.remote = remote;
        this.timer = timer;
        this.seconds = seconds;
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
