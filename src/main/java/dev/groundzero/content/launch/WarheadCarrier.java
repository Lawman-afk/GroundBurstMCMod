package dev.groundzero.content.launch;

import org.jspecify.annotations.Nullable;

import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.assembly.SizeClass;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * A machine whose model shows a missile carrying its warhead: the warhead's own block model, drawn by the client at one
 * of the model's bones (WarheadNose), sized to the missile by its airframe (missile_warheads.json, written by
 * tools/assets/launch.py).
 */
public interface WarheadCarrier {
    /** The warhead its model shows now, and where (null: none). Read on the client every frame. */
    @Nullable Shown shownWarhead();

    /**
     * @param warhead  the warhead's block
     * @param airframe the missile it stands on: "basic", "strategic" or "icbm" (how big it is drawn)
     * @param bone     the model's bone at its foot: the middle of the top of the missile's body
     * @param armed    its armed lamp lit (the launcher counting down)
     * @param lying    the missile lies on its side, its nose to the model's front (-z): the hall's cradles
     * @param turn     degrees it is turned about the missile's axis (to face its markings where they are seen)
     */
    record Shown(Block warhead, String airframe, String bone, boolean armed, boolean lying, float turn) {}

    /**
     * A missile standing on a launcher, its warhead's markings to the launcher's front (null: no missile, or one with no
     * warhead). The launchers' models name the bone at each airframe's warhead "warhead", "swarhead" (a Strategic
     * missile) and "iwarhead" (an intercontinental one).
     */
    static @Nullable Shown standing(@Nullable MissileSpec spec, boolean armed) {
        if (spec == null || !(spec.warhead().getItem() instanceof BlockItem item)) {
            return null;
        }
        return new Shown(item.getBlock(), airframe(spec.body()), switch (spec.body()) {
            case XL -> "swarhead";
            case XXL -> "iwarhead";
            default -> "warhead";
        }, armed, false, 180);
    }

    /** The airframe a missile's body is (launch.py's AIRFRAMES): the usual missile at any size, or its own long ones. */
    static String airframe(SizeClass body) {
        return switch (body) {
            case XL -> "strategic";
            case XXL -> "icbm";
            default -> "basic";
        };
    }
}
