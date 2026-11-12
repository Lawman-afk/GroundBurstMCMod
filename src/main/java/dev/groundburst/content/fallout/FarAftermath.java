package dev.groundburst.content.fallout;

import dev.groundburst.blast.BlastProfile;
import dev.groundburst.blast.BlastSettings;
import dev.groundburst.blast.TerrainTable;
import dev.groundburst.config.GBServerConfig;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A nuclear blast's far aftermath (DESIGN.md §21.10): past its own reach D (its entity zone's outer radius), rings of
 * charred land (to 1.4 D: what the fireball's heat sees burns), broken land (to 3 D: the blast wave's broken glass, doors
 * blown open, leaves stripped) and withered land (to 4.3 D: leaves and grass dying in patches), each fading out at its
 * edge, and a far wave that carries the shock on at the speed of sound to the last.
 */
public final class FarAftermath {
    private FarAftermath() {}

    /**
     * The rings' outer radii for a blast profile {@code id} as set off ({@code p}: its settings by weapon applied), in
     * blocks: charred, broken, withered; or null if it has none (not nuclear, the far aftermath off, or {@code far=0}). A
     * weapon's {@code far} share scales how far past its reach they go.
     */
    public static float @Nullable [] rings(Identifier id, BlastProfile p) {
        if (!TerrainTable.NUCLEAR.equals(p.terrain()) || !GBServerConfig.AFTERMATH_ENABLED.get()) {
            return null;
        }
        float k = BlastSettings.far(id);
        float d = Math.max(p.damage(), p.lethal());
        if (k <= 0 || d < 4) {
            return null;
        }
        float max = GBServerConfig.AFTERMATH_MAX_REACH.get();
        float charred = (float) (d + (GBServerConfig.AFTERMATH_CHARRED.get() - 1) * d * k);
        float broken = (float) (d + (GBServerConfig.AFTERMATH_BROKEN.get() - 1) * d * k);
        float withered = (float) (d + (GBServerConfig.AFTERMATH_WITHERED.get() - 1) * d * k);
        broken = Math.max(broken, charred);
        withered = Math.max(withered, broken);
        return new float[] {Math.min(max, charred), Math.min(max, broken), Math.min(max, withered)};
    }
}
