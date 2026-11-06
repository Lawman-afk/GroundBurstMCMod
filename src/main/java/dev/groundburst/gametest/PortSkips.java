package dev.groundburst.gametest;

import java.util.Map;

/**
 * Main's GameTests that cannot run on this branch, each with why (what 1.21.1 lacks, not what is broken here): left out
 * of the generator ({@link GameTestRegistration}). Empty means every one of main's runs.
 */
final class PortSkips {
    private PortSkips() {}

    private static final Map<String, String> SKIPPED = Map.of();

    static boolean skipped(String name) {
        return SKIPPED.containsKey(name);
    }
}
