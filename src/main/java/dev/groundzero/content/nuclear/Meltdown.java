package dev.groundzero.content.nuclear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.groundzero.blast.Blast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What a reactor meltdown leaves behind (DESIGN.md §7.1): a pool of molten Corium on the crater floor under the core,
 * and blobs of it thrown out onto the ground round the crater. Corium never lands on water or other fluids. It is
 * poured once the blast has finished (so it does not destroy it), checked four times a second; if the server
 * restarts in those few seconds the corium is not poured.
 */
public final class Meltdown {
    /** Radius of the pool under the core, and how many blobs are thrown out, and how far. */
    private static final double POOL = 3.2;
    private static final int BLOBS = 18;
    private static final double THROW_MIN = 8, THROW_MAX = 26;

    private record Pending(Vec3 centre, @Nullable Blast blast) {}

    private static final Map<ServerLevel, List<Pending>> PENDING = new WeakHashMap<>();

    private Meltdown() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 5 == 0) {
                List<Pending> due = PENDING.get(level);
                if (due != null) {
                    due.removeIf(p -> {
                        if (p.blast() == null || p.blast().isDone()) {
                            aftermath(level, p.centre());
                            return true;
                        }
                        return false;
                    });
                }
            }
        });
    }

    /** Pours the corium at {@code centre} once {@code blast} (if any) has finished. */
    public static void afterBlast(ServerLevel level, Vec3 centre, @Nullable Blast blast) {
        PENDING.computeIfAbsent(level, l -> new ArrayList<>()).add(new Pending(centre, blast));
    }

    public static void aftermath(ServerLevel level, Vec3 centre) {
        RandomSource random = RandomSource.create(BlockPos.containing(centre).asLong());
        BlockState corium = GZNuclear.CORIUM.block().get().defaultBlockState();
        int cx = (int) Math.floor(centre.x), cz = (int) Math.floor(centre.z), top = (int) Math.floor(centre.y) + 1;
        int r = (int) Math.ceil(POOL);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= POOL * POOL) {
                    pour(level, new BlockPos(cx + dx, top, cz + dz), 24, corium);
                }
            }
        }
        for (int i = 0; i < BLOBS; i++) {
            double a = random.nextDouble() * 2 * Math.PI, d = THROW_MIN + random.nextDouble() * (THROW_MAX - THROW_MIN);
            int x = (int) Math.floor(centre.x + Math.cos(a) * d), z = (int) Math.floor(centre.z + Math.sin(a) * d);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos at = new BlockPos(x, ground, z);
            if (place(level, at, corium) && random.nextBoolean()) {
                place(level, at.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random)), corium);
            }
        }
    }

    /** Drops molten corium down from {@code from} (at most {@code depth} blocks) onto the first solid floor. */
    private static void pour(ServerLevel level, BlockPos from, int depth, BlockState corium) {
        BlockPos.MutableBlockPos at = from.mutable();
        for (int i = 0; i < depth && at.getY() > level.getMinY(); i++) {
            BlockState below = level.getBlockState(at.below());
            if (!below.getFluidState().isEmpty()) {
                return;                                    // it would sink: no corium on water
            }
            if (below.isFaceSturdy(level, at.below(), Direction.UP)) {
                place(level, at, corium);
                return;
            }
            at.move(Direction.DOWN);
        }
    }

    /** Places corium at {@code at} if there is room and it stands on solid ground, not fluid. */
    private static boolean place(ServerLevel level, BlockPos at, BlockState corium) {
        BlockState here = level.getBlockState(at), below = level.getBlockState(at.below());
        if (!(here.isAir() || here.canBeReplaced() && here.getFluidState().isEmpty()) || !below.getFluidState().isEmpty()
                || !below.isFaceSturdy(level, at.below(), Direction.UP)) {
            return false;
        }
        level.setBlockAndUpdate(at, corium);
        return true;
    }
}
