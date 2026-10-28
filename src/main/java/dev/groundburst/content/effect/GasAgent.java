package dev.groundburst.content.effect;

import dev.groundburst.content.ordnance.Toxins;

/**
 * A toxic gas in the {@link GasGrid} (DESIGN.md §16.7): its colour, how hard it hits at full strength, how heavy it
 * is and how long it lingers.
 *
 * @param colour       its haze (RGB)
 * @param damage       toxin damage a dose at concentration 1 deals (doses every half second)
 * @param poisonTicks  poison a dose gives at concentration 1
 * @param amplifier    that poison's level (0 = I)
 * @param sink         share of what flows that goes down rather than sideways (heavy gases hug the ground)
 * @param decay        share lost each step (five ticks)
 */
public enum GasAgent {
    /** Chlorine: the gas grenade's and gas warhead's yellow-green cloud. Heavy; gone in about half a minute. */
    CHLORINE(0xC6DA5E, 1.0f, 100, Toxins.GAS, 0.45f, 0.022f),
    /** Anthrax: the chemical warhead's pale green cloud. Heavy, slow to clear (about a minute and a half), and harsher. */
    ANTHRAX(0x9CE070, 2.0f, 160, Toxins.TOXIN, 0.4f, 0.010f),
    /**
     * The GLA's toxin, a toxin SCUD's vivid green cloud (DESIGN.md §19.5): heavy, the harshest of them, and slow to clear
     * (two minutes and more), the game's "prolonged contamination".
     */
    SCUD_TOXIN(0x76E83C, 2.5f, 200, Toxins.TOXIN, 0.45f, 0.007f);

    public final int colour;
    public final float damage;
    public final int poisonTicks, amplifier;
    public final float sink, decay;

    GasAgent(int colour, float damage, int poisonTicks, int amplifier, float sink, float decay) {
        this.colour = colour;
        this.damage = damage;
        this.poisonTicks = poisonTicks;
        this.amplifier = amplifier;
        this.sink = sink;
        this.decay = decay;
    }
}
