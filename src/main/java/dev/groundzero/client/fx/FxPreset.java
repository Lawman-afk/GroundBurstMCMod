package dev.groundzero.client.fx;

/**
 * How a blast looks (DESIGN.md §12): one preset per visual set named by its profile ({@code small}, {@code medium},
 * {@code large}, {@code nuke}). Sizes are multiples of the crater radius the server sends, times in ticks; counts are
 * at "high" quality and scaled by the client's effect quality.
 *
 * @param fireball     fireball radius, in craters
 * @param fireballLife how long the fireball burns
 * @param fireballRise how fast the fireball climbs, in craters a second
 * @param glow         glowing puffs that give the fireball its boiling body
 * @param flash        white-out strength next to the blast (0 to 1)
 * @param flashTicks   how long the flash takes to fade
 * @param shake        camera trauma when the shock reaches someone at the blast
 * @param debris       block chunks thrown out
 * @param embers       sparks thrown out of the fireball
 * @param smoke        smoke puffs over the crater
 * @param smokeTicks   how long smoke keeps rising from it
 * @param smokeLife    how long a smoke puff lasts
 * @param dust         dust puffs along the shock front, per block of circumference
 * @param shell        alpha of the pale shock shell (0: none)
 * @param skyTint      how far the sky and fog turn smoky orange nearby (0: not at all)
 * @param skyTicks     how long that lasts
 * @param jets         dirt jets thrown up in a cone out of the crater (and fire jets out of the fireball)
 * @param surge        how big and lasting the dust along the shock front is (the nuke's base surge)
 * @param mushroom     how long a mushroom cloud lasts (0: none; the fireball then rides its cap)
 */
public record FxPreset(float fireball, int fireballLife, float fireballRise, int glow, float flash, int flashTicks, float shake,
        int debris, int embers, int smoke, int smokeTicks, int smokeLife, float dust, float shell, float skyTint, int skyTicks,
        int jets, float surge, int mushroom) {

    public static final FxPreset SMALL = new FxPreset(0.9f, 9, 0.3f, 10, 0.12f, 3, 0.35f,
            14, 10, 14, 12, 100, 0.25f, 0f, 0f, 0, 8, 1f, 0);
    public static final FxPreset MEDIUM = new FxPreset(0.75f, 18, 0.4f, 28, 0.3f, 5, 0.6f,
            40, 30, 45, 60, 200, 0.3f, 0.18f, 0f, 0, 24, 1f, 0);
    public static final FxPreset LARGE = new FxPreset(0.65f, 34, 0.5f, 60, 0.55f, 8, 0.85f,
            90, 60, 90, 80, 300, 0.25f, 0.22f, 0.35f, 1800, 50, 1.3f, 0);
    public static final FxPreset NUKE = new FxPreset(0.6f, 70, 0.6f, 120, 1.0f, 14, 1.0f,
            160, 110, 40, 200, 600, 0.18f, 0.28f, 0.4f, 6000, 60, 2.5f, 6000);

    public static FxPreset byName(String name) {
        return switch (name) {
            case "small" -> SMALL;
            case "medium" -> MEDIUM;
            case "large" -> LARGE;
            default -> name.startsWith("nuke") || name.startsWith("tsar") ? NUKE : MEDIUM;
        };
    }
}
