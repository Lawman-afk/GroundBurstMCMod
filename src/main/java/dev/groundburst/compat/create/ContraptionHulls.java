package dev.groundburst.compat.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.tuple.MutablePair;
import org.jspecify.annotations.Nullable;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;

import dev.groundburst.blast.Hulls;
import dev.groundburst.content.machine.Frame;
import dev.groundburst.content.machine.Frames;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import dev.groundburst.legacy.net.PacketDistributor;

/**
 * Create's moving contraptions as hulls a blast reaches (main's DESIGN.md §17.16). A contraption holds its blocks in its
 * entity, away from the world's, so a blast breaks them out of it there: each goes as a block of the world would (nothing
 * dropped; a machine with all it held, a chest or a tank aboard emptied, whoever sat on a seat set down), the contraption
 * forgets it (its collision shape remade, its pipes and machines aboard looked at afresh) and every player who sees it
 * is told ({@link BlocksChanged}). A blast's scorch or a freeze's frost changes a plain block it holds (one with nothing
 * in it, not a machine); it takes no new blocks, so nothing is laid on it and nothing burns on it (its blocks are not in
 * the level, where a fire would burn). A train's wheels hold. Contraptions are driven (by a bearing, a piston, a train, a
 * cart), so a blast does not shove them, nor a singularity pull them.
 */
public final class ContraptionHulls {
    /** How far a contraption's blocks may lie from its entity and still be found. */
    private static final double SPREAD = 64;

    private ContraptionHulls() {}

    public static void register() {
        Hulls.addFinder((level, center, radius, out) -> {
            AABB reach = new AABB(center, center).inflate(radius);
            // (found by where they are, their boxes then checked: a contraption's blocks may spread far from its entity)
            for (AbstractContraptionEntity entity : level.getEntitiesOfClass(AbstractContraptionEntity.class, reach.inflate(SPREAD),
                    e -> e.isAlive() && e.getContraption() != null && e.getBoundingBox().inflate(1).intersects(reach))) {
                out.accept(new Hull(level, entity, entity.getContraption()));
            }
        });
    }

    /** One contraption's blocks, by their places in it. */
    static final class Hull implements Hulls.Hull {
        private final ServerLevel level;
        private final AbstractContraptionEntity entity;
        private final Contraption contraption;
        private final AABB bounds;
        /** The blocks changed since the last flush, and what each is now (air: broken out). */
        private final Map<BlockPos, BlockState> changed = new java.util.LinkedHashMap<>();

        Hull(ServerLevel level, AbstractContraptionEntity entity, Contraption contraption) {
            this.level = level;
            this.entity = entity;
            this.contraption = contraption;
            AABB box = null;
            for (BlockPos p : contraption.getBlocks().keySet()) {
                AABB b = new AABB(p);
                box = box == null ? b : box.minmax(b);
            }
            this.bounds = box == null ? new AABB(BlockPos.ZERO) : box;
        }

        /** The frame the contraption itself is in (the world's: nothing on 1.20.1 carries contraptions about). */
        private Frame outer() {
            return Frames.at(this.entity.level(), this.entity.blockPosition());
        }

        @Override
        public Object key() {
            return this.entity;
        }

        @Override
        public Vec3 toLocal(Vec3 world) {
            return this.entity.toLocalVector(outer().toLocal(world), 1f);
        }

        @Override
        public Vec3 toWorld(Vec3 local) {
            return outer().toWorld(this.entity.toGlobalVector(local, 1f));
        }

        @Override
        public AABB bounds() {
            return this.bounds;
        }

        @Override
        public void blocks(AABB local, BiConsumer<BlockPos, BlockState> each) {
            for (Map.Entry<BlockPos, StructureBlockInfo> e : List.copyOf(this.contraption.getBlocks().entrySet())) {
                BlockState state = e.getValue().state();
                if (!state.isAir() && local.intersects(new AABB(e.getKey()))) {
                    each.accept(e.getKey(), state);
                }
            }
        }

        @Override
        public BlockState state(BlockPos local) {
            StructureBlockInfo info = this.contraption.getBlocks().get(local);
            return info == null ? Blocks.AIR.defaultBlockState() : info.state();
        }

        @Override
        public @Nullable BlockPos inLevel(BlockPos local) {
            return null;
        }

        @Override
        public boolean holds(BlockPos local, BlockState state) {
            return state.getBlock() instanceof AbstractBogeyBlock<?>;    // a carriage keeps its wheels
        }

        @Override
        public void destroy(BlockPos local) {
            BlockPos pos = local.immutable();
            if (this.contraption.getBlocks().remove(pos) == null) {
                return;
            }
            MutablePair<StructureBlockInfo, MovementContext> actor = this.contraption.getActorAt(pos);
            if (actor != null) {
                MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(actor.getLeft().state());
                if (behaviour != null && actor.getRight() != null) {
                    behaviour.stopMoving(actor.getRight());          // (a carried machine is let go, and forgotten)
                }
                this.contraption.getActors().remove(actor);
            }
            this.contraption.getInteractors().remove(pos);
            MountedItemStorage items = this.contraption.getStorage().getAllItemStorages().get(pos);
            if (items != null) {
                for (int i = 0; i < items.getSlots(); i++) {
                    items.setStackInSlot(i, ItemStack.EMPTY);       // what a chest aboard held goes with it
                }
            }
            MountedFluidStorage fluids = this.contraption.getStorage().getFluids().storages.get(pos);
            if (fluids != null) {
                for (int t = 0; t < fluids.getTanks(); t++) {
                    FluidStack in = fluids.getFluidInTank(t);
                    if (!in.isEmpty()) {
                        fluids.drain(in.copy(), IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            }
            int seat = this.contraption.getSeats().indexOf(pos);
            if (seat >= 0) {
                for (Entity rider : List.copyOf(this.entity.getPassengers())) {
                    Integer at = this.contraption.getSeatMapping().get(rider.getUUID());
                    if (at != null && at == seat) {
                        rider.stopRiding();                         // the seat is gone from under them
                    }
                }
            }
            this.changed.put(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean set(BlockPos local, BlockState state) {
            BlockPos pos = local.immutable();
            StructureBlockInfo info = this.contraption.getBlocks().get(pos);
            if (state.isAir() || state.hasBlockEntity() || info == null || info.nbt() != null || info.state().hasBlockEntity()
                    || this.contraption.getActorAt(pos) != null || this.contraption.getInteractors().containsKey(pos)
                    || this.contraption.getSeats().contains(pos)) {
                return false;                                   // only a plain block it holds, changed for another
            }
            this.contraption.getBlocks().put(pos, new StructureBlockInfo(pos, state, null));
            this.changed.put(pos, state);
            return true;
        }

        @Override
        public void flush() {
            if (this.changed.isEmpty()) {
                return;
            }
            this.contraption.invalidateColliders();
            List<BlockPos> gone = new ArrayList<>(), at = new ArrayList<>();
            List<BlockState> states = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockState> e : this.changed.entrySet()) {
                at.add(e.getKey());
                states.add(e.getValue());
                if (e.getValue().isAir()) {
                    gone.add(e.getKey());
                }
            }
            if (!gone.isEmpty()) {
                Aboard.of(this.contraption).blocksGone(gone);
            }
            PacketDistributor.sendToPlayersTrackingEntity(this.entity, new BlocksChanged(this.entity.getId(), at, states));
            this.changed.clear();
        }
    }
}
