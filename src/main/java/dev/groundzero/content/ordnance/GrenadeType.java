package dev.groundzero.content.ordnance;

/**
 * The thrown explosives (DESIGN.md §14): how each flies, what sets it off and what it does.
 *
 * @param id        item and blast profile id ({@code groundzero:<id>})
 * @param fuse      ticks from the throw to going off (-1: on impact)
 * @param behaviour what it does when it hits something
 * @param speed     throw speed, blocks a tick
 */
public enum GrenadeType {
    /** Bundled sticks with a sputtering fuse: 3 s, bounces. */
    DYNAMITE("dynamite", 60, Behaviour.BOUNCE, 0.9f),
    /** Pineapple frag: 4 s, bounces, throws 24 shrapnel fragments. */
    FRAG("frag_grenade", 80, Behaviour.BOUNCE, 1.1f),
    /** Sticks to the first thing it hits, block or creature: 4 s. */
    STICKY("sticky_grenade", 80, Behaviour.STICK, 1.0f),
    /** Bursts on impact into a carpet of fire. */
    INCENDIARY("incendiary_grenade", -1, Behaviour.IMPACT, 1.1f),
    /** Pops after 2 s and pours out a dense cloud for 30 s that blinds mobs. */
    SMOKE("smoke_grenade", 40, Behaviour.BOUNCE, 1.1f),
    /** Like the smoke grenade, but the cloud is chlorine: 25 s of poison gas that also blinds. */
    POISON_GAS("gas_grenade", 40, Behaviour.BOUNCE, 1.1f),
    /** Bursts on impact and splashes toxin over the ground round it. */
    TOXIN("toxin_grenade", -1, Behaviour.IMPACT, 1.1f);

    public enum Behaviour { BOUNCE, STICK, IMPACT }

    public final String id;
    public final int fuse;
    public final Behaviour behaviour;
    public final float speed;

    GrenadeType(String id, int fuse, Behaviour behaviour, float speed) {
        this.id = id;
        this.fuse = fuse;
        this.behaviour = behaviour;
        this.speed = speed;
    }

    /** How long it pours out its cloud once it pops (0: it makes no cloud). */
    public int cloudTicks() {
        return switch (this) {
            case SMOKE -> 600;
            case POISON_GAS -> 500;
            default -> 0;
        };
    }
}
