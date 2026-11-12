package dev.groundburst.config;

import java.util.Locale;

/**
 * Lengths of time written as the server config writes them (DESIGN.md §21.10): a number and a unit, several together,
 * as "30s", "5m", "12h", "7d", "2w" or "1d12h" ({@code s} seconds, {@code m} minutes, {@code h} hours, {@code d} days,
 * {@code w} weeks; the longer names "min", "hours", "days" and so on are read too). A bare number is seconds; a number
 * may have a fraction ("1.5h"). Read once into seconds, written back the same way.
 */
public final class Durations {
    private Durations() {}

    /** The seconds {@code text} says, or -1 if it is not a length of time. */
    public static long seconds(String text) {
        if (text == null) {
            return -1;
        }
        String s = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.isEmpty()) {
            return -1;
        }
        double total = 0;
        int i = 0, parts = 0;
        while (i < s.length()) {
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                i++;
            }
            if (i == start) {
                return -1;                                 // a unit with no number before it
            }
            double number;
            try {
                number = Double.parseDouble(s.substring(start, i));
            } catch (NumberFormatException e) {
                return -1;
            }
            int unitStart = i;
            while (i < s.length() && Character.isLetter(s.charAt(i))) {
                i++;
            }
            long unit = unit(s.substring(unitStart, i));
            if (unit < 0) {
                return -1;
            }
            total += number * unit;
            parts++;
        }
        return parts == 0 || total > Long.MAX_VALUE / 1000 ? -1 : Math.round(total);
    }

    /** Seconds in one of the unit's names ("" is seconds), or -1. */
    private static long unit(String u) {
        return switch (u) {
            case "", "s", "sec", "secs", "second", "seconds" -> 1;
            case "m", "min", "mins", "minute", "minutes" -> 60;
            case "h", "hr", "hrs", "hour", "hours" -> 3_600;
            case "d", "day", "days" -> 86_400;
            case "w", "wk", "wks", "week", "weeks" -> 604_800;
            default -> -1;
        };
    }

    /** Whether {@code o} is a length of time of at most {@code maxSeconds} (a config value's check). */
    public static boolean valid(Object o, long maxSeconds) {
        if (!(o instanceof String s)) {
            return false;
        }
        long v = seconds(s);
        return v >= 0 && v <= maxSeconds;
    }

    /** {@code seconds} as the config writes a time: "5m", "1d 12h", "45s", "0s". */
    public static String format(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long[] sizes = {604_800, 86_400, 3_600, 60, 1};
        String[] names = {"w", "d", "h", "m", "s"};
        StringBuilder b = new StringBuilder();
        long left = seconds;
        int shown = 0;
        for (int k = 0; k < sizes.length && left > 0 && shown < 2; k++) {
            long n = left / sizes[k];
            if (n > 0 || shown > 0) {
                if (n > 0) {
                    if (b.length() > 0) {
                        b.append(' ');
                    }
                    b.append(n).append(names[k]);
                }
                left -= n * sizes[k];
                shown++;                                   // the two largest units are plenty for a reading
            }
        }
        return b.toString();
    }

    /** {@code ticks} (20 a second) as {@link #format(long)} writes them. */
    public static String formatTicks(long ticks) {
        return format((ticks + 19) / 20);
    }
}
