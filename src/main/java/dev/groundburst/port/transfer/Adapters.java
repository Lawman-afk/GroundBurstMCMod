package dev.groundburst.port.transfer;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.port.transfer.access.ItemAccess;
import dev.groundburst.port.transfer.energy.EnergyHandler;
import dev.groundburst.port.transfer.fluid.FluidResource;
import dev.groundburst.port.transfer.item.ItemResource;
import dev.groundburst.port.transfer.transaction.SnapshotJournal;
import dev.groundburst.port.transfer.transaction.Transaction;
import dev.groundburst.port.transfer.transaction.TransactionContext;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The two ways between the mod's handlers (transactions) and 1.21.1's (a simulate flag). One way, each call is a
 * transaction of its own, committed unless simulated. The other, a change is made at once and undone if its transaction
 * closes without committing.
 */
public final class Adapters {
    private Adapters() {}

    /** A transaction inside whatever one is open (a root one if none is). */
    private static Transaction open() {
        return Transaction.open(Transaction.getCurrentOpenedTransaction());
    }

    // --- the mod's handlers as 1.21.1's ------------------------------------------------------------------------------------
    static @Nullable IItemHandler itemHandler(@Nullable ResourceHandler<ItemResource> h) {
        return h == null ? null : new IItemHandler() {
            @Override
            public int getSlots() {
                return h.size();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return h.getResource(slot).toStack(h.getAmountAsInt(slot));
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                try (Transaction t = open()) {
                    int in = h.insert(slot, ItemResource.of(stack), stack.getCount(), t);
                    if (!simulate) {
                        t.commit();
                    }
                    return stack.copyWithCount(stack.getCount() - in);
                }
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemResource r = h.getResource(slot);
                if (r.isEmpty() || amount <= 0) {
                    return ItemStack.EMPTY;
                }
                try (Transaction t = open()) {
                    int out = h.extract(slot, r, amount, t);
                    if (!simulate) {
                        t.commit();
                    }
                    return r.toStack(out);
                }
            }

            @Override
            public int getSlotLimit(int slot) {
                ItemResource r = h.getResource(slot);
                return h.getCapacityAsInt(slot, r);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return h.isValid(slot, ItemResource.of(stack));
            }
        };
    }

    static @Nullable IFluidHandler fluidHandler(@Nullable ResourceHandler<FluidResource> h) {
        return h == null ? null : new FluidView(h);
    }

    private static class FluidView implements IFluidHandler {
        protected final ResourceHandler<FluidResource> h;

        FluidView(ResourceHandler<FluidResource> h) {
            this.h = h;
        }

        @Override
        public int getTanks() {
            return this.h.size();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return this.h.getResource(tank).toStack(this.h.getAmountAsInt(tank));
        }

        @Override
        public int getTankCapacity(int tank) {
            return this.h.getCapacityAsInt(tank, this.h.getResource(tank));
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return this.h.isValid(tank, FluidResource.of(stack));
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            try (Transaction t = open()) {
                int in = this.h.insert(FluidResource.of(resource), resource.getAmount(), t);
                if (action.execute()) {
                    t.commit();
                }
                return in;
            }
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidResource r = FluidResource.of(resource);
            try (Transaction t = open()) {
                int out = this.h.extract(r, resource.getAmount(), t);
                if (action.execute()) {
                    t.commit();
                }
                return r.toStack(out);
            }
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < this.h.size(); i++) {
                FluidResource r = this.h.getResource(i);
                if (!r.isEmpty() && this.h.getAmountAsLong(i) > 0) {
                    return drain(r.toStack(maxDrain), action);
                }
            }
            return FluidStack.EMPTY;
        }
    }

    static @Nullable IEnergyStorage energyStorage(@Nullable EnergyHandler h) {
        return h == null ? null : new IEnergyStorage() {
            @Override
            public int receiveEnergy(int toReceive, boolean simulate) {
                try (Transaction t = open()) {
                    int in = h.insert(toReceive, t);
                    if (!simulate) {
                        t.commit();
                    }
                    return in;
                }
            }

            @Override
            public int extractEnergy(int toExtract, boolean simulate) {
                try (Transaction t = open()) {
                    int out = h.extract(toExtract, t);
                    if (!simulate) {
                        t.commit();
                    }
                    return out;
                }
            }

            @Override
            public int getEnergyStored() {
                return h.getAmountAsInt();
            }

            @Override
            public int getMaxEnergyStored() {
                return h.getCapacityAsInt();
            }

            @Override
            public boolean canExtract() {
                return extractEnergy(1, true) > 0 || h.getAmountAsLong() == 0;
            }

            @Override
            public boolean canReceive() {
                return receiveEnergy(1, true) > 0 || h.getAmountAsLong() >= h.getCapacityAsLong();
            }
        };
    }

    /** One of the mod's items holding fluid (a canister), as 1.21.1's fluid item handler: changed on a copy, read back with getContainer. */
    static @Nullable IFluidHandlerItem fluidItem(ItemStack stack) {
        ItemAccess.StackAccess access = (ItemAccess.StackAccess) ItemAccess.forStack(stack);
        ResourceHandler<FluidResource> h = access.getCapability(Capabilities.Fluid.ITEM);
        if (h == null) {
            return null;
        }
        return new ItemView(h, access);
    }

    private static final class ItemView extends FluidView implements IFluidHandlerItem {
        private final ItemAccess.StackAccess access;

        ItemView(ResourceHandler<FluidResource> h, ItemAccess.StackAccess access) {
            super(h);
            this.access = access;
        }

        @Override
        public ItemStack getContainer() {
            return this.access.stack();
        }
    }

    /** One of the mod's items holding energy, as 1.21.1's energy handler: changed in place, as 1.21.1 expects. */
    static @Nullable IEnergyStorage energyItem(ItemStack stack) {
        ItemAccess here = new InPlaceAccess(stack);
        return energyStorage(here.getCapability(Capabilities.Energy.ITEM));
    }

    /** Sets a stack's components to exactly {@code patch} over its item's own (1.21.1 keeps the map's restore inside). */
    private static void restore(ItemStack stack, DataComponentPatch patch) {
        if (stack.getComponents() instanceof net.minecraft.core.component.PatchedDataComponentMap map) {
            map.restorePatch(patch);
        }
    }

    /** A stack whose item stays the same, changed in place: its components swapped, its count kept. */
    private static final class InPlaceAccess implements ItemAccess {
        private final ItemStack stack;
        private final SnapshotJournal<DataComponentPatch> journal = new SnapshotJournal<>() {
            @Override
            protected DataComponentPatch createSnapshot() {
                return InPlaceAccess.this.stack.getComponentsPatch();
            }

            @Override
            protected void revertToSnapshot(DataComponentPatch snapshot) {
                restore(InPlaceAccess.this.stack, snapshot);
            }
        };

        InPlaceAccess(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public ItemResource getResource() {
            return ItemResource.of(this.stack);
        }

        @Override
        public int getAmount() {
            return this.stack.getCount();
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int exchange(ItemResource newResource, int amount, @Nullable TransactionContext transaction) {
            if (newResource.getItem() != this.stack.getItem() || amount != this.stack.getCount()) {
                return 0;
            }
            try (Transaction t = Transaction.open(transaction)) {
                this.journal.updateSnapshots(t);
                restore(this.stack, newResource.getComponentsPatch());
                t.commit();
            }
            return amount;
        }
    }

    // --- 1.21.1's handlers as the mod's ------------------------------------------------------------------------------------
    /** What a foreign handler did inside open transactions, to undo it if they close without committing. */
    private static final class UndoLog extends SnapshotJournal<Integer> {
        private final List<Runnable> undo = new ArrayList<>();

        void did(TransactionContext transaction, Runnable undoIt) {
            updateSnapshots(transaction);
            this.undo.add(undoIt);
        }

        @Override
        protected Integer createSnapshot() {
            return this.undo.size();
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            for (int i = this.undo.size() - 1; i >= snapshot; i--) {
                this.undo.remove(i).run();
            }
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            this.undo.clear();
        }
    }

    public static @Nullable ResourceHandler<ItemResource> foreignItems(@Nullable IItemHandler h) {
        if (h == null) {
            return null;
        }
        UndoLog log = new UndoLog();
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return h.getSlots();
            }

            @Override
            public ItemResource getResource(int index) {
                return ItemResource.of(h.getStackInSlot(index));
            }

            @Override
            public long getAmountAsLong(int index) {
                return h.getStackInSlot(index).getCount();
            }

            @Override
            public long getCapacityAsLong(int index, ItemResource resource) {
                return resource.isEmpty() ? h.getSlotLimit(index) : Math.min(h.getSlotLimit(index), resource.getMaxStackSize());
            }

            @Override
            public boolean isValid(int index, ItemResource resource) {
                return h.isItemValid(index, resource.toStack(1));
            }

            @Override
            public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
                if (resource.isEmpty() || amount <= 0) {
                    return 0;
                }
                ItemStack left = h.insertItem(index, resource.toStack(amount), false);
                int in = amount - left.getCount();
                if (in > 0) {
                    log.did(transaction, () -> h.extractItem(index, in, false));
                }
                return in;
            }

            @Override
            public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
                if (resource.isEmpty() || amount <= 0 || !resource.matches(h.getStackInSlot(index))) {
                    return 0;
                }
                ItemStack out = h.extractItem(index, amount, false);
                int n = out.getCount();
                if (n > 0) {
                    log.did(transaction, () -> h.insertItem(index, out, false));
                }
                return n;
            }
        };
    }

    public static @Nullable ResourceHandler<FluidResource> foreignFluids(@Nullable IFluidHandler h) {
        if (h == null) {
            return null;
        }
        UndoLog log = new UndoLog();
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return h.getTanks();
            }

            @Override
            public FluidResource getResource(int index) {
                return FluidResource.of(h.getFluidInTank(index));
            }

            @Override
            public long getAmountAsLong(int index) {
                return h.getFluidInTank(index).getAmount();
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return h.getTankCapacity(index);
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return h.isFluidValid(index, resource.toStack(1));
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                if (index != 0 || resource.isEmpty() || amount <= 0) {
                    return 0;                                   // (1.21.1's fluid handlers fill as a whole)
                }
                int in = h.fill(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
                if (in > 0) {
                    log.did(transaction, () -> h.drain(resource.toStack(in), IFluidHandler.FluidAction.EXECUTE));
                }
                return in;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                if (resource.isEmpty() || amount <= 0 || !resource.matches(h.getFluidInTank(index))) {
                    return 0;
                }
                FluidStack out = h.drain(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
                int n = out.getAmount();
                if (n > 0) {
                    log.did(transaction, () -> h.fill(out, IFluidHandler.FluidAction.EXECUTE));
                }
                return n;
            }
        };
    }

    static @Nullable EnergyHandler foreignEnergy(@Nullable IEnergyStorage h) {
        if (h == null) {
            return null;
        }
        UndoLog log = new UndoLog();
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return h.getEnergyStored();
            }

            @Override
            public long getCapacityAsLong() {
                return h.getMaxEnergyStored();
            }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                int in = amount <= 0 ? 0 : h.receiveEnergy(amount, false);
                if (in > 0) {
                    log.did(transaction, () -> h.extractEnergy(in, false));
                }
                return in;
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                int out = amount <= 0 ? 0 : h.extractEnergy(amount, false);
                if (out > 0) {
                    log.did(transaction, () -> h.receiveEnergy(out, false));
                }
                return out;
            }
        };
    }

    /** Another item holding fluid (a bucket), changed on a copy and swapped in where it is through its access. */
    static @Nullable ResourceHandler<FluidResource> foreignFluidItem(ItemStack stack, ItemAccess access) {
        if (stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM) == null) {
            return null;
        }
        return new ResourceHandler<>() {
            private @Nullable IFluidHandlerItem copy() {
                ItemResource r = access.getResource();
                return r.isEmpty() ? null : r.toStack(1).getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM);
            }

            @Override
            public int size() {
                IFluidHandlerItem h = copy();
                return h == null ? 0 : h.getTanks();
            }

            @Override
            public FluidResource getResource(int index) {
                IFluidHandlerItem h = copy();
                return h == null || index >= h.getTanks() ? FluidResource.EMPTY : FluidResource.of(h.getFluidInTank(index));
            }

            @Override
            public long getAmountAsLong(int index) {
                IFluidHandlerItem h = copy();
                return h == null || index >= h.getTanks() ? 0 : (long) h.getFluidInTank(index).getAmount() * access.getAmount();
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                IFluidHandlerItem h = copy();
                return h == null || index >= h.getTanks() ? 0 : (long) h.getTankCapacity(index) * access.getAmount();
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                IFluidHandlerItem h = copy();
                return h != null && index < h.getTanks() && h.isFluidValid(index, resource.toStack(1));
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                IFluidHandlerItem h = copy();
                if (h == null || index != 0 || access.getAmount() != 1 || resource.isEmpty() || amount <= 0) {
                    return 0;
                }
                int in = h.fill(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
                return in > 0 && access.exchange(ItemResource.of(h.getContainer()), 1, transaction) == 1 ? in : 0;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                IFluidHandlerItem h = copy();
                if (h == null || access.getAmount() != 1 || resource.isEmpty() || amount <= 0) {
                    return 0;
                }
                int out = h.drain(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE).getAmount();
                return out > 0 && access.exchange(ItemResource.of(h.getContainer()), 1, transaction) == 1 ? out : 0;
            }
        };
    }

    /** Another item holding energy, changed on a copy and swapped in where it is through its access. */
    static @Nullable EnergyHandler foreignEnergyItem(ItemStack stack, ItemAccess access) {
        if (stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM) == null) {
            return null;
        }
        return new EnergyHandler() {
            private @Nullable ItemStack copy() {
                ItemResource r = access.getResource();
                return r.isEmpty() ? null : r.toStack(1);
            }

            private @Nullable IEnergyStorage of(@Nullable ItemStack s) {
                return s == null ? null : s.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
            }

            @Override
            public long getAmountAsLong() {
                IEnergyStorage e = of(copy());
                return e == null ? 0 : (long) e.getEnergyStored() * access.getAmount();
            }

            @Override
            public long getCapacityAsLong() {
                IEnergyStorage e = of(copy());
                return e == null ? 0 : (long) e.getMaxEnergyStored() * access.getAmount();
            }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                ItemStack s = copy();
                IEnergyStorage e = of(s);
                if (e == null || access.getAmount() != 1 || amount <= 0) {
                    return 0;
                }
                int in = e.receiveEnergy(amount, false);
                return in > 0 && access.exchange(ItemResource.of(s), 1, transaction) == 1 ? in : 0;
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                ItemStack s = copy();
                IEnergyStorage e = of(s);
                if (e == null || access.getAmount() != 1 || amount <= 0) {
                    return 0;
                }
                int out = e.extractEnergy(amount, false);
                return out > 0 && access.exchange(ItemResource.of(s), 1, transaction) == 1 ? out : 0;
            }
        };
    }
}
