package dev.groundburst.compat.sable;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;

import dev.groundburst.blast.Hulls;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sable's sub-levels (Create Aeronautics' airships, planes, cars and boats) as hulls a blast reaches (main's DESIGN.md
 * §17.16). A ship's blocks are real blocks in a plot of its own far off, shown where the ship is: a blast breaks,
 * scorches, freezes or sets them alight in the plot (Sable then works out the ship's mass and shape again, and splits it
 * if it is cut in two), and shoves or pulls the ship through Sable's physics, as Sable itself does for vanilla's
 * explosions. A ship with no blocks left is passed over: Sable's box for an empty plot runs from the top of the int range
 * to the bottom, which would have every blast walk the plot's empty air. Its blocks are read from the plot's own chunks,
 * and only those it has: nothing is loaded for a look.
 */
public final class SableHulls {
    /** As the backport's blast writes the world's blocks (its {@code BlastJob}): clients told, no drops of the block itself. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private SableHulls() {}

    public static void register() {
        Hulls.addFinder((level, center, radius, out) -> {
            BoundingBox3d box = new BoundingBox3d(center.x - radius, center.y - radius, center.z - radius, center.x + radius,
                    center.y + radius, center.z + radius);
            for (SubLevelAccess ship : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
                if (ship instanceof SubLevel sub && !sub.isRemoved() && sub.getPlot() != null && plotBox(sub) != null) {
                    out.accept(new Ship(level, sub));
                }
            }
        });
    }

    /** The box its plot's blocks are all in (max inclusive), or null while it has none. */
    static @Nullable BoundingBox3ic plotBox(SubLevel ship) {
        BoundingBox3ic b = ship.getPlot().getBoundingBox();
        return b.minX() > b.maxX() || b.minY() > b.maxY() || b.minZ() > b.maxZ() ? null : b;
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
            BoundingBox3ic b = plotBox(this.ship);
            if (b == null) {
                Vec3 c = Vec3.atCenterOf(this.ship.getPlot().getCenterBlock());
                return new AABB(c, c);                          // (emptied: a point, which no block's box contains)
            }
            return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        }

        /** Its plot's chunk at a chunk of the level, if it has one there. */
        private @Nullable LevelChunk chunk(int cx, int cz) {
            LevelPlot plot = this.ship.getPlot();
            return plot.getChunk(plot.toLocal(new ChunkPos(cx, cz)));
        }

        @Override
        public void blocks(AABB local, BiConsumer<BlockPos, BlockState> each) {
            if (plotBox(this.ship) == null) {
                return;
            }
            AABB bounds = bounds();
            if (!local.intersects(bounds)) {
                return;                                         // (an AABB's intersect of two apart is a box between them)
            }
            AABB box = local.intersect(bounds);
            int x0 = Mth.floor(box.minX), y0 = Mth.floor(box.minY), z0 = Mth.floor(box.minZ);
            int x1 = Mth.ceil(box.maxX) - 1, y1 = Mth.ceil(box.maxY) - 1, z1 = Mth.ceil(box.maxZ) - 1;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
                for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                    LevelChunk chunk = chunk(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    LevelChunkSection[] sections = chunk.getSections();
                    int s0 = Math.max(0, chunk.getSectionIndex(y0)), s1 = Math.min(sections.length - 1, chunk.getSectionIndex(y1));
                    for (int si = s0; si <= s1; si++) {
                        LevelChunkSection section = sections[si];
                        if (section == null || section.hasOnlyAir()) {
                            continue;
                        }
                        int by = chunk.getSectionYFromSectionIndex(si) << 4;
                        for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++) {
                            for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
                                for (int y = Math.max(y0, by); y <= Math.min(y1, by + 15); y++) {
                                    BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                    if (!state.isAir()) {
                                        each.accept(p.set(x, y, z), state);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        @Override
        public BlockState state(BlockPos local) {
            if (plotBox(this.ship) == null || !bounds().contains(Vec3.atCenterOf(local))) {
                return AIR;
            }
            LevelChunk chunk = chunk(local.getX() >> 4, local.getZ() >> 4);
            return chunk == null ? AIR : chunk.getBlockState(local);
        }

        @Override
        public BlockPos inLevel(BlockPos local) {
            return local;                                       // (a ship's blocks are real blocks, in its plot)
        }

        @Override
        public void destroy(BlockPos local) {
            this.level.setBlock(local, AIR, FLAGS);
        }

        @Override
        public boolean set(BlockPos local, BlockState state) {
            if (chunk(local.getX() >> 4, local.getZ() >> 4) == null) {
                return false;                                   // (outside the plot's chunks: not the ship's to change)
            }
            this.level.setBlock(local, state, FLAGS);
            return true;
        }

        @Override
        public void push(Vec3 localPoint, Vec3 localImpulse) {
            RigidBodyHandle body = body();
            if (body != null) {
                body.applyImpulseAtPoint(localPoint, localImpulse);
            }
        }

        @Override
        public void pull(Function<Vec3, Vec3> pull) {
            RigidBodyHandle body = body();
            if (body == null || !(this.ship instanceof ServerSubLevel server)) {
                return;
            }
            MassData mass = server.getMassTracker();
            Vector3dc com = mass.getCenterOfMass();
            if (com == null || mass.getMass() <= 0) {
                return;
            }
            Vec3 middle = new Vec3(com.x(), com.y(), com.z());
            Vec3 dv = pull.apply(toWorld(middle));             // blocks a tick, in the world
            if (dv.lengthSqr() < 1e-12) {
                return;
            }
            Vector3d local = this.ship.logicalPose().transformNormalInverse(new Vector3d(dv.x, dv.y, dv.z), new Vector3d());
            // All of it alike, as gravity pulls: its mass times the change, in blocks a second, at its middle.
            double k = mass.getMass() * 20;
            body.applyImpulseAtPoint(middle, new Vec3(local.x * k, local.y * k, local.z * k));
        }

        private @Nullable RigidBodyHandle body() {
            if (this.ship instanceof ServerSubLevel server) {
                RigidBodyHandle body = RigidBodyHandle.of(server);
                return body != null && body.isValid() ? body : null;
            }
            return null;
        }
    }
}
