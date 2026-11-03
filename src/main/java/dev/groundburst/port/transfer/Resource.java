package dev.groundburst.port.transfer;

/** Something a handler holds (an item or a fluid, with its components), without an amount. */
public interface Resource {
    boolean isEmpty();
}
