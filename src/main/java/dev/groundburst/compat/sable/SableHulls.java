package dev.groundburst.compat.sable;

import java.util.function.BiConsumer;

import dev.groundburst.blast.Hulls;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sable's sub-levels (Create Aeronautics' airships, planes, cars and boats) as hulls a blast reaches (main's DESIGN.md
 * §17.16). A ship's blocks are real blocks in a plot of its own far off, shown where the ship is: a blast breaks them in
 * the plot (Sable then works out the ship's mass and shape again, and splits it if it is cut in two) and shoves the ship
 * through Sable's physics, as Sable itself does for vanilla's explosions.
 */
public final class SableHulls {
    /** As the backport's blast writes the world's blocks (its {@code BlastJob}): clients told, no drops of the block itself. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private SableHulls() {}

    public static void register() {
        Hulls.addFinder((level, center, radius, out) -> {
            BoundingBox3d box = new BoundingBox3d(center.x - radius, center.y - radius, center.z - radius, center.x + radius,
                    center.y + radius, center.z + radius);
            for (SubLevelAccess ship : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
                if (ship instanceof SubLevel sub && !sub.isRemoved() && sub.getPlot() != null) {
                    out.accept(new Ship(level, sub));
                }
            }
        });
    }

    /** One ship: its plot's blocks, where the ship is now. */
    record Ship(ServerLevel level, SubLevel ship) implements Hulls.Hull {
        @Override
        public Object key() {
            return this.ship.getUniqueId();
        }

        @Override
        public Vec3 toLocal(Vec3 world) {
            return this.ship.logicalPose().transformPositionInverse(world);
        }

        @Override
        public Vec3 toWorld(Vec3 local) {
            return this.ship.logicalPose().transformPosition(local);
        }

        @Override
        public double scale() {
            return this.ship.logicalPose().scale().x();
        }

        @Override
        public AABB bounds() {
            BoundingBox3ic b = this.ship.getPlot().getBoundingBox();
            return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        }

        @Override
        public void blocks(AABB local, BiConsumer<BlockPos, BlockState> each) {
            AABB box = local.intersect(bounds());
            if (box.getXsize() <= 0 || box.getYsize() <= 0 || box.getZsize() <= 0) {
                return;
            }
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX - 1e-6, box.maxY - 1e-6, box.maxZ - 1e-6))) {
                BlockState state = this.level.getBlockState(p);
                if (!state.isAir()) {
                    each.accept(p, state);
                }
            }
        }

        @Override
        public BlockState state(BlockPos local) {
            return bounds().contains(Vec3.atCenterOf(local)) ? this.level.getBlockState(local) : Blocks.AIR.defaultBlockState();
        }

        @Override
        public BlockPos inLevel(BlockPos local) {
            return local;                                       // (a ship's blocks are real blocks, in its plot)
        }

        @Override
        public void destroy(BlockPos local) {
            this.level.setBlock(local, Blocks.AIR.defaultBlockState(), FLAGS);
        }

        @Override
        public void push(Vec3 localPoint, Vec3 localImpulse) {
            if (this.ship instanceof ServerSubLevel server) {
                RigidBodyHandle body = RigidBodyHandle.of(server);
                if (body != null && body.isValid()) {
                    body.applyImpulseAtPoint(localPoint, localImpulse);
                }
            }
        }
    }
}
