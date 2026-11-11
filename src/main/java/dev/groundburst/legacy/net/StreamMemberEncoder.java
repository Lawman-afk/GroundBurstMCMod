package dev.groundburst.legacy.net;

/** Minecraft 1.21's stream encoder written as a method of the value. */
@FunctionalInterface
public interface StreamMemberEncoder<O, T> {
    void encode(T value, O buf);
}
