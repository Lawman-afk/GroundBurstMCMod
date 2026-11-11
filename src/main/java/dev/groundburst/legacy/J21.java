package dev.groundburst.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Java 21's methods the mod's code calls that Java 17 (Minecraft 1.20.1's) lacks: Math.clamp, and the sequenced
 * collections' first and last (the port turns {@code list.getFirst()} into {@code J21.first(list)}).
 */
public final class J21 {
    private J21() {}

    public static int clamp(long value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException(min + " > " + max);
        }
        return (int) Math.min(max, Math.max(value, min));
    }

    public static long clamp(long value, long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException(min + " > " + max);
        }
        return Math.min(max, Math.max(value, min));
    }

    public static double clamp(double value, double min, double max) {
        if (!(min <= max)) {
            throw new IllegalArgumentException(min + " > " + max);
        }
        return Math.min(max, Math.max(value, min));
    }

    public static float clamp(float value, float min, float max) {
        if (!(min <= max)) {
            throw new IllegalArgumentException(min + " > " + max);
        }
        return Math.min(max, Math.max(value, min));
    }

    public static <T> T first(List<T> list) {
        if (list.isEmpty()) {
            throw new java.util.NoSuchElementException();
        }
        return list.get(0);
    }

    public static <T> T last(List<T> list) {
        if (list.isEmpty()) {
            throw new java.util.NoSuchElementException();
        }
        return list.get(list.size() - 1);
    }

    public static <T> T first(Deque<T> deque) {
        return deque.getFirst();
    }

    public static <T> T last(Deque<T> deque) {
        return deque.getLast();
    }

    public static <T> T first(java.util.SortedSet<T> set) {
        return set.first();
    }

    public static <T> T last(java.util.SortedSet<T> set) {
        return set.last();
    }

    public static <T> T first(java.util.LinkedHashSet<T> set) {
        return set.iterator().next();
    }

    /** Any other ordered collection's first (a sequenced set's, a linked map's values'). */
    public static <T> T first(java.util.Collection<T> c) {
        return c.iterator().next();
    }

    public static <T> T last(java.util.Collection<T> c) {
        T last = null;
        for (T t : c) {
            last = t;
        }
        if (last == null && c.isEmpty()) {
            throw new java.util.NoSuchElementException();
        }
        return last;
    }

    public static <T> T removeFirst(List<T> list) {
        if (list.isEmpty()) {
            throw new java.util.NoSuchElementException();
        }
        return list.remove(0);
    }

    public static <T> T removeLast(List<T> list) {
        if (list.isEmpty()) {
            throw new java.util.NoSuchElementException();
        }
        return list.remove(list.size() - 1);
    }

    public static <T> T removeFirst(Deque<T> deque) {
        return deque.removeFirst();
    }

    public static <T> T removeLast(Deque<T> deque) {
        return deque.removeLast();
    }

    public static <T> void addFirst(List<T> list, T value) {
        list.add(0, value);
    }

    public static <T> void addLast(List<T> list, T value) {
        list.add(value);
    }

    public static <T> void addFirst(Deque<T> deque, T value) {
        deque.addFirst(value);
    }

    public static <T> void addLast(Deque<T> deque, T value) {
        deque.addLast(value);
    }

    /** A list's elements in reverse order (a copy: the mod only reads it). */
    public static <T> List<T> reversed(List<T> list) {
        List<T> out = new ArrayList<>(list);
        Collections.reverse(out);
        return out;
    }
}
