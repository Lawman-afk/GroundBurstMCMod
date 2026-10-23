package dev.groundzero.content.launch;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.MissileSpec;

/**
 * A machine whose model shows a missile: a launcher with one standing on it, an assembler or the hall laying one out on
 * its cradles. The missile is drawn by the client from its own item model (one per warhead, tools/assets/missiles.py)
 * at one of the model's bones, so the machines' models carry no missile of their own.
 */
public interface MissileCarrier {
    /** The missile its model shows now, and where (null: none). Read on the client every frame. */
    @Nullable Shown shownMissile();

    /**
     * The arms that hold the shown missile (DESIGN.md §10.4): every missile is its own length and girth, so each arm's
     * collar slides up its spine to its place on the missile and its beam telescopes in to the missile's skin (worked out
     * on the client from the missile's shape, MachineGeoRenderer). None: nothing holds it.
     */
    default List<Grip> grips() {
        return List.of();
    }

    /**
     * The missile the arms are set for (its warhead's id; null: rest as modelled): the one shown, or (a launcher) the
     * last one loaded, so the arms stay where they held it as they swing clear and it lifts off.
     */
    default @Nullable String gripsFor() {
        Shown shown = shownMissile();
        return shown == null ? null : shown.warhead();
    }

    /**
     * @param warhead the warhead whose missile it is (its id: its shape in the client's table)
     * @param looks   the missile's looks to draw (its item model's custom model data): the whole missile, or the assembly
     *                sections laid so far ({@link MissileParts#part})
     * @param bone  the model's bone at the missile's tail, on its axis
     * @param lying the missile lies on its side, its nose toward the model's front (-z): on cradles
     * @param turn  degrees it is turned about its axis (its markings toward where they are seen)
     */
    record Shown(String warhead, List<String> looks, String bone, boolean lying, float turn) {}

    /** Where on a missile an arm holds it. */
    enum Hold {
        /** On the body just under the warhead's joint. */
        UNDER_WARHEAD,
        /**
         * Low: a third stage's missile on its second stage, a two-stage one on its first, any other two fifths of the way
         * up to the warhead (above its fins).
         */
        LOW
    }

    /**
     * An arm that holds a standing missile, as its model has it (model pixels; x out from the missile's axis toward the
     * arm's hinge).
     *
     * @param mount  the bone that carries the arm (its collar, hinge and all): moved up or down to the hold
     * @param reach  the bone that carries the clamp and the inner beam: moved in or out to the missile's skin
     * @param restY  the height the arm is modelled at (its beam's middle)
     * @param tailY  the height of the missile's tail
     * @param clampX the clamp's face as modelled (a missile that thick needs no reach)
     * @param hold   where on the missile it holds
     */
    record Grip(String mount, String reach, float restY, float tailY, float clampX, Hold hold) {}

    /**
     * The cradles a lying missile rests on (DESIGN.md §10.4): flat rubber saddles, their tops at {@code topY}, under the
     * missile {@code at} these heights up its axis (the world's pixels from its tail). The model's missile bone is at
     * {@code axisY}; each missile is drawn as high as its own girth over the saddles puts its axis (null: no cradles,
     * drawn at the bone).
     */
    default @Nullable Cradles cradles() {
        return null;
    }

    record Cradles(float topY, float axisY, float[] at) {}

    /** A missile standing on a launcher, its markings to the launcher's front (null: no missile). */
    static @Nullable Shown standing(@Nullable MissileSpec spec) {
        return spec == null ? null : new Shown(spec.warheadId(), List.of(MissileParts.look(spec.warheadId())), "missile", false, 180);
    }

    /** The first {@code laid} assembly sections of the missile carrying {@code warhead}, lying on cradles (null: none). */
    static @Nullable Shown lying(String warhead, int laid, String bone, float turn) {
        if (laid <= 0 || warhead.isEmpty()) {
            return null;
        }
        List<String> looks = new ArrayList<>();
        for (int k = 1; k <= laid; k++) {
            looks.add(MissileParts.part(warhead, k));
        }
        return new Shown(warhead, looks, bone, true, turn);
    }

    /** The first {@code laid} sections of the missile carrying {@code warhead}, stacked upright stage on stage (null: none). */
    static @Nullable Shown stacked(String warhead, int laid, String bone, float turn) {
        Shown lying = lying(warhead, laid, bone, turn);
        return lying == null ? null : new Shown(warhead, lying.looks(), bone, false, turn);
    }
}
