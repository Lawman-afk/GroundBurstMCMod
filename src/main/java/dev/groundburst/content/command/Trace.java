package dev.groundburst.content.command;

/**
 * How a Seismograph's pen swings for an arrival (DESIGN.md §17.17), as a drum recorder draws a blast: the first wave's
 * sharp, smaller onset, the bigger second wave after it (as much later as the blast is far), and the coda ringing down;
 * a tremor's shaking held for as long as it lasts. The model's pen follows {@link #pen}; the screen's paper draws
 * {@link #envelope} (at its scale a trace is a band of wiggles, as on a real record).
 */
public final class Trace {
    private Trace() {}

    /** The share of the pen's full swing an arrival felt this hard makes (1: it hits its stops). */
    public static double amplitude(float felt) {
        return Math.min(1.0, 0.12 * Math.pow(1.7, Math.max(-1, felt)));
    }

    /** How long the second wave's coda rings down (ticks, its time constant): the bigger, the longer. */
    public static double coda(float felt) {
        return 30 + 25 * Math.max(0, felt);
    }

    /** The pen at {@code now} (ticks) for an arrival at {@code arrival} with its second wave at {@code second}: -1 to 1. */
    public static double pen(double now, long arrival, long second, float felt, int duration) {
        double t = now - arrival;
        if (t < 0) {
            return 0;
        }
        double a = amplitude(felt);
        double gap = Math.max(1, second - arrival);
        double out = 0.35 * a * Math.exp(-t / (gap + 20)) * Math.sin(t * 2.1) * Math.min(1, t / 2);
        double ts = now - second;
        if (ts >= 0) {
            out += a * Math.exp(-ts / coda(felt)) * Math.sin(ts * 1.3 + 0.5) * Math.min(1, ts / 3);
        }
        if (duration > 0 && t < duration) {
            out += 0.45 * a * Math.sin(t * 1.7) * Math.sin(t * 0.23 + 1.1) * Math.min(1, t / 20) * Math.min(1, (duration - t) / 40);
        }
        return out;
    }

    /** How far the trace reaches either side at {@code now} (0 to about 1): the band the paper shows for it. */
    public static double envelope(double now, long arrival, long second, float felt, int duration) {
        double t = now - arrival;
        if (t < 0) {
            return 0;
        }
        double a = amplitude(felt);
        double gap = Math.max(1, second - arrival);
        double out = 0.35 * a * Math.exp(-t / (gap + 20)) * Math.min(1, t / 2);
        double ts = now - second;
        if (ts >= 0) {
            out += a * Math.exp(-ts / coda(felt)) * Math.min(1, ts / 3);
        }
        if (duration > 0 && t < duration) {
            out += 0.45 * a * Math.min(1, t / 20) * Math.min(1, (duration - t) / 40);
        }
        return out;
    }
}
