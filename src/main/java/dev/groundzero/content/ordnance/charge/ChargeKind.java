package dev.groundzero.content.ordnance.charge;

/**
 * The placed charges (DESIGN.md §14, Phase 6 slice 6b): what each sticks to, which fuzes it takes and its default timer.
 *
 * @param id       block and item id ({@code groundzero:<id>}), and the blast profile for those that make a blast
 * @param anyFace  sticks to any face of a block (the timed bomb stands on the floor instead)
 * @param fuzeChoice offers Remote or Timer (the timed bomb is always on its timer)
 * @param timer    default timer, seconds
 */
public enum ChargeKind {
    /** Plastic explosive: sticks to any face; up to four stack on one spot for a bigger blast. */
    C4("c4", true, true, 10),
    /** A frame of det-cord on a plate: cuts a 3x3 hole 3 deep into what it is on, and nothing more. */
    BREACHING("breaching_charge", true, true, 5),
    /** A pot of thermite: burns a 1x1 shaft 8 deep into what it is on, through anything short of obsidian. */
    THERMITE("thermite_charge", true, true, 5),
    /** A steel box of dynamite on a clock: counts down once armed, and can only be stopped by cutting the right wire. */
    TIMED_BOMB("timed_bomb", false, false, 30);

    public final String id;
    public final boolean anyFace;
    public final boolean fuzeChoice;
    public final int timer;

    ChargeKind(String id, boolean anyFace, boolean fuzeChoice, int timer) {
        this.id = id;
        this.anyFace = anyFace;
        this.fuzeChoice = fuzeChoice;
        this.timer = timer;
    }

    public static final int MAX_TIMER = 600, MAX_FREQUENCY = 9999;
}
