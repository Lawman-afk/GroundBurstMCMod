package dev.groundburst.content.fluid;

import dev.groundburst.registry.GBBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Creative fluid tank (DESIGN.md §6.4): an endless source of whatever fluid was first poured in. It is always full;
 * anything drawn from it, by a bucket, canister or pipe, is there again at once. While empty it takes the first fluid
 * offered; after that it takes nothing (it is full) until a player clears it or pours in a different fluid.
 */
public class CreativeFluidTankBlockEntity extends BlockEntity implements TankDisplay {
    public static final int CAPACITY = 16_000;

    private final Tank tank = new Tank();

    /** One slot that refills itself: extraction never takes anything away, and a first fill tops it up to capacity. */
    public final class Tank extends FluidStacksResourceHandler {
        Tank() {
            super(1, CAPACITY);
        }

        public FluidStack fluid() {
            return this.stacks.get(0);
        }

        /** Empties the tank so it takes the next fluid offered. */
        public void clear() {
            set(0, FluidResource.EMPTY, 0);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            // An endless source: hand over the whole request, however much it is, and leave the store untouched.
            FluidStack now = this.stacks.get(index);
            return amount > 0 && !resource.isEmpty() && !now.isEmpty() && matches(now, resource) ? amount : 0;
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            FluidStack now = this.stacks.get(0);
            if (!now.isEmpty() && now.getAmount() < CAPACITY) {
                set(0, FluidResource.of(now), CAPACITY);                          // calls back here once, now full
                return;
            }
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    public CreativeFluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(GBBlockEntities.CREATIVE_FLUID_TANK.get(), pos, state);
    }

    public Tank tank() {
        return this.tank;
    }

    /** What pipes and machines see on every side. */
    public ResourceHandler<FluidResource> handler() {
        return this.tank;
    }

    // --- TankDisplay -------------------------------------------------------------------------------------------------------
    @Override
    public boolean drawsContents() {
        return true;
    }

    @Override
    public FluidStack shownFluid() {
        return this.tank.fluid();
    }

    @Override
    public int shownCapacity() {
        return CAPACITY;
    }

    @Override
    public int boxWidth() {
        return 1;
    }

    @Override
    public int boxHeight() {
        return 1;
    }

    // --- sync and persistence ----------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.tank.serialize(output.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.tank.deserialize(input.childOrEmpty("tank"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
