package dev.groundburst.legacy.net;

/** Minecraft 1.21's stream encoder. */
@FunctionalInterface
public interface StreamEncoder<O, T> {
    void encode(O buf, T value);
}
