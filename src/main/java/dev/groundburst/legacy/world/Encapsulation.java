package dev.groundburst.legacy.world;

import java.util.ArrayList;
import java.util.List;

import dev.groundburst.world.structure.SitePiece;
import dev.groundburst.world.structure.SiteStructure;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * 1.21's "encapsulate" terrain adjustment, which 1.20.1 lacks: rock closed round a structure's part on every side, out to
 * {@link #REACH} blocks past its box and fading (1.21's beardifier: its bury contribution at half the distances outside
 * the part's box, times 0.8). The mod's sites ask it for their parts underground (a bunker's rooms, a silo's shaft, a
 * waste store's vault), so caves and water keep off them. Here those parts carry BURY in its place (no part of the
 * mod's is buried otherwise; such a {@link SitePiece} tells Forge's beardifier hook NONE), and each chunk's beardifier
 * adds their rock to its density ({@code mixin.BeardifierMixin}).
 */
public final class Encapsulation {
    /** How far past a part's box its rock reaches, blocks. */
    public static final int REACH = 12;

    private Encapsulation() {}

    /** What a chunk's beardifier keeps of the mod's parts closed in rock (the mixin makes it one). */
    public interface ClosedIn {
        void groundburst$closeIn(List<BoundingBox> boxes);
    }

    /** The boxes of the mod's site parts closed in rock whose rock may reach into {@code chunk}. */
    public static List<BoundingBox> near(StructureManager structures, ChunkPos chunk) {
        List<BoundingBox> out = null;
        for (StructureStart start : structures.startsForStructure(chunk, s -> s instanceof SiteStructure)) {
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof SitePiece site && site.closedIn() && piece.isCloseToChunk(chunk, REACH)) {
                    if (out == null) {
                        out = new ArrayList<>(2);
                    }
                    out.add(site.getBeardifierBox());
                }
            }
        }
        return out == null ? List.of() : out;
    }

    /** The rock a part closed in by {@code box} adds to the ground's density at a block (1.21's, as it gives it). */
    public static double rock(BoundingBox box, int x, int y, int z) {
        int dx = Math.max(0, Math.max(box.minX() - x, x - box.maxX()));
        int dy = Math.max(0, Math.max(box.minY() - y, y - box.maxY()));
        int dz = Math.max(0, Math.max(box.minZ() - z, z - box.maxZ()));
        if (dx > REACH || dy > REACH || dz > REACH) {
            return 0;
        }
        return Mth.clampedMap(Mth.length(dx / 2.0, dy / 2.0, dz / 2.0), 0.0, 6.0, 1.0, 0.0) * 0.8;
    }
}
