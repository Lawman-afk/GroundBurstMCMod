package dev.groundzero.content.fluid;

import org.jspecify.annotations.Nullable;

import dev.groundzero.registry.GZBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * One block of a fluid tank. A tank is a box of these (see {@link FluidTankStructure}); its lowest north-west block is
 * the controller and holds the fluid for the whole box, the others point at it. Every block exposes the controller's
 * fluid to pipes and machines. Each tick the controller lets liquids out of the bottom of the tank (gases out of the
 * top) into whatever sits there: a pipe, a machine, another mod's tank.
 */
public class FluidTankBlockEntity extends BlockEntity {
    public static final int CAPACITY_PER_BLOCK = 16_000;
    public static final int OUTFLOW_PER_TICK = 1_000;
    private static final int SYNC_STEPS = 64;

    /** The controller's position, or null when this block is the controller (of a single block or a whole box). */
    private @Nullable BlockPos controller;
    private int width = 1, height = 1;
    private final Tank tank = new Tank();
    private int syncedKey = -1;

    /** The fluid store; only the controller's is used. */
    public final class Tank extends FluidStacksResourceHandler {
        Tank() {
            super(1, CAPACITY_PER_BLOCK);
        }

        void resize(int capacity) {
            this.capacity = capacity;
        }

        public FluidStack fluid() {
            return this.stacks.get(0);
        }

        public int capacity() {
            return this.capacity;
        }

        void setContents(FluidStack stack) {
            set(0, stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(stack), Math.min(stack.getAmount(), this.capacity));
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            changed();
        }
    }

    public FluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.FLUID_TANK.get(), pos, state);
    }

    // --- structure ---------------------------------------------------------------------------------------------------------
    public boolean isController() {
        return this.controller == null;
    }

    public BlockPos controllerPos() {
        return this.controller == null ? this.worldPosition : this.controller;
    }

    /** The controller's block entity (this one if it is the controller, or if the controller has gone missing). */
    public FluidTankBlockEntity controllerEntity() {
        if (this.controller != null && this.level != null && this.level.getBlockEntity(this.controller) instanceof FluidTankBlockEntity c) {
            return c;
        }
        return this;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Tank tank() {
        return this.tank;
    }

    /** Makes this block part of a box whose controller is {@code controller} (null: this block). */
    void join(@Nullable BlockPos controller, int width, int height, FluidStack contents) {
        this.controller = controller;
        this.width = width;
        this.height = height;
        this.tank.resize(controller == null ? width * width * height * CAPACITY_PER_BLOCK : CAPACITY_PER_BLOCK);
        this.tank.setContents(controller == null ? contents : FluidStack.EMPTY);
        changed();
        sync(true);
    }

    /** What pipes and machines see on any side of any block of the tank. */
    public ResourceHandler<FluidResource> handler() {
        return controllerEntity().tank;
    }

    // --- ticking -------------------------------------------------------------------------------------------------------------
    public void serverTick(ServerLevel level) {
        if (!isController() || this.tank.fluid().isEmpty()) {
            return;
        }
        boolean gas = this.tank.fluid().getFluidType().isLighterThanAir();
        int y = gas ? this.worldPosition.getY() + this.height : this.worldPosition.getY() - 1;
        Direction from = gas ? Direction.DOWN : Direction.UP;
        int left = OUTFLOW_PER_TICK;
        for (int dx = 0; dx < this.width && left > 0; dx++) {
            for (int dz = 0; dz < this.width && left > 0; dz++) {
                BlockPos target = new BlockPos(this.worldPosition.getX() + dx, y, this.worldPosition.getZ() + dz);
                if (level.getBlockState(target).getBlock() instanceof FluidTankBlock) {
                    continue;
                }
                ResourceHandler<FluidResource> h = level.getCapability(Capabilities.Fluid.BLOCK, target, from);
                if (h != null) {
                    left -= ResourceHandlerUtil.move(this.tank, h, r -> true, left, null);
                }
            }
        }
    }

    // --- sync and persistence ----------------------------------------------------------------------------------------------------
    private void changed() {
        setChanged();
        sync(false);
    }

    /** Tells clients about the fill level in coarse steps (they draw the fluid), or always when {@code force}. */
    private void sync(boolean force) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        FluidStack f = this.tank.fluid();
        int key = f.isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(f.getFluid()) * (SYNC_STEPS + 1)
                + (int) ((long) f.getAmount() * SYNC_STEPS / Math.max(1, this.tank.capacity())) + 1;
        if (force || key != this.syncedKey) {
            this.syncedKey = key;
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.controller != null) {
            output.putLong("controller", this.controller.asLong());
        }
        output.putInt("width", this.width);
        output.putInt("height", this.height);
        this.tank.serialize(output.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.controller = input.getLong("controller").map(BlockPos::of).orElse(null);
        this.width = input.getIntOr("width", 1);
        this.height = input.getIntOr("height", 1);
        this.tank.resize(this.controller == null ? this.width * this.width * this.height * CAPACITY_PER_BLOCK : CAPACITY_PER_BLOCK);
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

    /** Breaking a block splits the tank: the rest re-forms (as a box if it still is one) and keeps its share of fluid. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            FluidTankStructure.detach(server, pos, this);
        }
    }
}
