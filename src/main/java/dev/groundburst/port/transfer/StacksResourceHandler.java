package dev.groundburst.port.transfer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mojang.serialization.Codec;

import dev.groundburst.port.ValueInput;
import dev.groundburst.port.ValueOutput;
import dev.groundburst.port.transfer.transaction.SnapshotJournal;
import dev.groundburst.port.transfer.transaction.TransactionContext;
import net.minecraft.core.NonNullList;

/**
 * A handler over a list of stacks (item or fluid stacks), each place journaled so a transaction can undo what it did;
 * {@link #onContentsChanged} hears of each place changed once its root transaction commits.
 */
public abstract class StacksResourceHandler<S, T extends Resource> implements ResourceHandler<T> {
    protected final S emptyStack;
    protected NonNullList<S> stacks;
    protected final Codec<List<S>> codec;
    private final List<StackJournal> journals = new ArrayList<>();

    protected StacksResourceHandler(int size, S emptyStack, Codec<S> stackCodec) {
        this(NonNullList.withSize(size, emptyStack), emptyStack, stackCodec);
    }

    protected StacksResourceHandler(NonNullList<S> stacks, S emptyStack, Codec<S> stackCodec) {
        this.emptyStack = emptyStack;
        this.stacks = stacks;
        this.codec = stackCodec.listOf();
        updateJournals();
    }

    private NonNullList<S> mutableCopyOf(Collection<S> list) {
        NonNullList<S> out = NonNullList.withSize(list.size(), this.emptyStack);
        int i = 0;
        for (S s : list) {
            out.set(i++, s);
        }
        return out;
    }

    protected void setStacks(NonNullList<S> stacks) {
        this.stacks = stacks;
        updateJournals();
    }

    private void updateJournals() {
        while (this.journals.size() < this.stacks.size()) {
            this.journals.add(new StackJournal(this.journals.size()));
        }
        if (this.journals.size() > this.stacks.size()) {
            this.journals.subList(this.stacks.size(), this.journals.size()).clear();
        }
    }

    public void serialize(ValueOutput output) {
        output.store("stacks", this.codec, this.stacks);
    }

    public void deserialize(ValueInput input) {
        input.read("stacks", this.codec).ifPresent(l -> setStacks(mutableCopyOf(l)));
    }

    /** Sets place {@code index} at once, outside any transaction. */
    public void set(int index, T resource, int amount) {
        S old = this.stacks.get(index);
        this.stacks.set(index, resource.isEmpty() || amount <= 0 ? this.emptyStack : getStackFrom(resource, amount));
        onContentsChanged(index, old);
    }

    protected abstract T getResourceFrom(S stack);

    protected abstract int getAmountFrom(S stack);

    protected abstract S getStackFrom(T resource, int amount);

    protected abstract S copyOf(S stack);

    protected boolean matches(S stack, T resource) {
        return getResourceFrom(stack).equals(resource);
    }

    @Override
    public boolean isValid(int index, T resource) {
        return true;
    }

    protected abstract int getCapacity(int index, T resource);

    /** Place {@code index} changed (its root transaction committed); {@code previousContents} is what it held before. */
    protected void onContentsChanged(int index, S previousContents) {}

    public NonNullList<S> copyToList() {
        NonNullList<S> out = NonNullList.withSize(this.stacks.size(), this.emptyStack);
        for (int i = 0; i < this.stacks.size(); i++) {
            out.set(i, copyOf(this.stacks.get(i)));
        }
        return out;
    }

    @Override
    public int size() {
        return this.stacks.size();
    }

    @Override
    public T getResource(int index) {
        return getResourceFrom(this.stacks.get(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return getAmountFrom(this.stacks.get(index));
    }

    @Override
    public long getCapacityAsLong(int index, T resource) {
        return getCapacity(index, resource);
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0 || index < 0 || index >= this.stacks.size() || !isValid(index, resource)) {
            return 0;
        }
        S stack = this.stacks.get(index);
        int current = getAmountFrom(stack);
        if (current > 0 && !matches(stack, resource)) {
            return 0;
        }
        int inserted = Math.min(amount, Math.max(0, getCapacity(index, resource) - current));
        if (inserted > 0) {
            this.journals.get(index).updateSnapshots(transaction);
            this.stacks.set(index, getStackFrom(resource, current + inserted));
        }
        return inserted;
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0 || index < 0 || index >= this.stacks.size()) {
            return 0;
        }
        S stack = this.stacks.get(index);
        int current = getAmountFrom(stack);
        if (current <= 0 || !matches(stack, resource)) {
            return 0;
        }
        int extracted = Math.min(amount, current);
        this.journals.get(index).updateSnapshots(transaction);
        this.stacks.set(index, current == extracted ? this.emptyStack : getStackFrom(resource, current - extracted));
        return extracted;
    }

    private final class StackJournal extends SnapshotJournal<S> {
        private final int index;

        StackJournal(int index) {
            this.index = index;
        }

        @Override
        protected S createSnapshot() {
            return copyOf(StacksResourceHandler.this.stacks.get(this.index));
        }

        @Override
        protected void revertToSnapshot(S snapshot) {
            StacksResourceHandler.this.stacks.set(this.index, snapshot);
        }

        @Override
        protected void onRootCommit(S originalState) {
            onContentsChanged(this.index, originalState);
        }
    }
}
