package dev.groundburst.content.alarm;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The alarm network (DESIGN.md §18.6): channels, numbered as the Remote Detonator's frequencies, each raised while any of
 * its senders has its condition (a Missile Warning Receiver seeing a missile coming, an Alarm Panel switched on or
 * powered, a Hazard Monitor reading too much). A sender refreshes its alert every {@link #REFRESH} ticks; it lapses
 * {@link #LAPSE} ticks after the last. Every sounder, light, display and relay on the channel answers it. A channel can
 * be silenced (its sounders quiet until the alert ends) and tested (sounded a few seconds). One set of channels a
 * dimension. Not saved: senders raise their channels again as they tick.
 */
public final class Alarms {
    /** What a channel is raised for, most pressing first: a missile coming, radiation, poison gas, an alarm by hand. */
    public enum Kind { ATTACK, RADIATION, GAS, ALARM }

    /**
     * A sender's alert: what, and for an attack how many missiles and when the first comes down (game time; -1 none), for
     * radiation the dose rate (RAD/h) and for gas the concentration (percent).
     */
    public record Alert(Kind kind, int count, long impactAt, float reading) {
        public static Alert of(Kind kind) {
            return new Alert(kind, 0, -1, 0);
        }
    }

    public static final int MAX_CHANNEL = 9999;
    /** How often a sender refreshes its alert, and how long an alert lasts without one. */
    public static final int REFRESH = 10, LAPSE = 30;

    private record Raised(Alert alert, long until) {}

    private static final class Channel {
        final Map<BlockPos, Raised> raised = new HashMap<>();
        boolean silenced;
        long testUntil = -1;
    }

    private static final Map<ServerLevel, Map<Integer, Channel>> BY_LEVEL = new WeakHashMap<>();

    private Alarms() {}

    private static Channel channel(ServerLevel level, int channel) {
        return BY_LEVEL.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(channel, c -> new Channel());
    }

    private static @Nullable Channel existing(ServerLevel level, int channel) {
        Map<Integer, Channel> m = BY_LEVEL.get(level);
        return m == null ? null : m.get(channel);
    }

    /** A sender at {@code from} raises (or keeps raised) {@code channel} with {@code alert}. */
    public static void raise(ServerLevel level, int channel, BlockPos from, Alert alert) {
        channel(level, channel).raised.put(from.immutable(), new Raised(alert, level.getGameTime() + LAPSE));
    }

    /** A sender at {@code from} lowers its alert on {@code channel} (its condition has passed, or it was removed). */
    public static void lower(ServerLevel level, int channel, BlockPos from) {
        Channel c = existing(level, channel);
        if (c != null) {
            c.raised.remove(from);
        }
    }

    /**
     * What {@code channel} is raised for now (null: all clear): the most pressing alert; an attack seen by several
     * receivers counts the most any of them sees and the soonest impact. A test sounds as an alarm.
     */
    public static @Nullable Alert status(ServerLevel level, int channel) {
        Channel c = existing(level, channel);
        if (c == null) {
            return null;
        }
        long now = level.getGameTime();
        Alert best = null;
        for (Raised r : c.raised.values()) {
            if (r.until() <= now) {
                continue;
            }
            Alert a = r.alert();
            if (best == null || a.kind().ordinal() < best.kind().ordinal()) {
                best = a;
            } else if (a.kind() == best.kind()) {
                long impact = best.impactAt() < 0 ? a.impactAt() : a.impactAt() < 0 ? best.impactAt() : Math.min(best.impactAt(), a.impactAt());
                best = new Alert(a.kind(), Math.max(best.count(), a.count()), impact, Math.max(best.reading(), a.reading()));
            }
        }
        if (best == null && c.testUntil > now) {
            best = Alert.of(Kind.ALARM);
        }
        return best;
    }

    /** Whether {@code channel}'s sounders have been silenced (until its alert ends). */
    public static boolean silenced(ServerLevel level, int channel) {
        Channel c = existing(level, channel);
        return c != null && c.silenced;
    }

    /** Silences {@code channel}'s sounders until its alert ends (its lights and displays stay on). */
    public static void silence(ServerLevel level, int channel) {
        if (status(level, channel) != null) {
            channel(level, channel).silenced = true;
        }
    }

    /** Sounds {@code channel} for {@code ticks} (a test: its displays read ALARM). */
    public static void test(ServerLevel level, int channel, int ticks) {
        Channel c = channel(level, channel);
        c.testUntil = level.getGameTime() + ticks;
        c.silenced = false;
    }

    /** Once a second: lapsed alerts dropped, a quiet channel's silence lifted, empty channels forgotten. */
    static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        Map<Integer, Channel> m = BY_LEVEL.get(level);
        if (m == null) {
            return;
        }
        long now = level.getGameTime();
        m.values().removeIf(c -> {
            c.raised.values().removeIf(r -> r.until() <= now);
            boolean quiet = c.raised.isEmpty() && c.testUntil <= now;
            if (quiet) {
                c.silenced = false;
            }
            return quiet;
        });
    }

    static void forget(ServerLevel level) {
        BY_LEVEL.remove(level);
    }

    public static void clear() {
        BY_LEVEL.clear();
    }
}
