package dev.groundburst.port.transfer.access;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.port.transfer.ResourceHandler;
import dev.groundburst.port.transfer.item.ItemResource;
import dev.groundburst.port.transfer.transaction.SnapshotJournal;
import dev.groundburst.port.transfer.transaction.Transaction;
import dev.groundburst.port.transfer.transaction.TransactionContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ItemCapability;

/**
 * Where an item is (a player's hand, a slot of a handler, a stack on its own), so a handler living in the item (a
 * canister's fluid, a battery's charge) can change it there: swapping the item for its changed self, in a transaction.
 */
public interface ItemAccess {
    ItemResource getResource();

    int getAmount();

    int insert(ItemResource resource, int amount, TransactionContext transaction);

    int extract(ItemResource resource, int amount, TransactionContext transaction);

    /** Swaps {@code amount} of what is here for {@code newResource}; how many were swapped (all or none). */
    default int exchange(ItemResource newResource, int amount, @Nullable TransactionContext transaction) {
        ItemResource current = getResource();
        if (current.isEmpty() || newResource.isEmpty() || amount <= 0) {
            return 0;
        }
        try (Transaction sub = Transaction.open(transaction)) {
            int extracted = extract(current, amount, sub);
            if (extracted == amount && insert(newResource, extracted, sub) == extracted) {
                sub.commit();
                return extracted;
            }
        }
        return 0;
    }

    /** The same place seen as holding one item at most (a stack of buckets filled one at a time). */
    default ItemAccess oneByOne() {
        ItemAccess outer = this;
        return new ItemAccess() {
            @Override
            public ItemResource getResource() {
                return outer.getResource();
            }

            @Override
            public int getAmount() {
                return Math.min(1, outer.getAmount());
            }

            @Override
            public int insert(ItemResource resource, int amount, TransactionContext transaction) {
                return outer.insert(resource, Math.min(1, amount), transaction);
            }

            @Override
            public int extract(ItemResource resource, int amount, TransactionContext transaction) {
                return outer.extract(resource, Math.min(1, amount), transaction);
            }
        };
    }

    /** The item's capability, given this place as its context (null: it has none). */
    default <T> @Nullable T getCapability(ItemCapability<T, ItemAccess> capability) {
        ItemResource r = getResource();
        return r.isEmpty() ? null : r.toStack(Math.max(1, getAmount())).getCapability(capability, this);
    }

    /** A stack on its own (a copy is kept and changed; read it back with the access's resource). */
    static ItemAccess forStack(ItemStack stack) {
        return new StackAccess(stack.copy());
    }

    /** The item in a player's hand: what does not fit back in the hand goes to their inventory (or is dropped). */
    static ItemAccess forPlayerInteraction(Player player, InteractionHand hand) {
        return new PlayerAccess(player, hand);
    }

    /** Slot {@code index} of a handler (what does not fit back there goes into its other slots). */
    static ItemAccess forHandlerIndex(ResourceHandler<ItemResource> handler, int index) {
        return new ItemAccess() {
            @Override
            public ItemResource getResource() {
                return handler.getResource(index);
            }

            @Override
            public int getAmount() {
                return handler.getAmountAsInt(index);
            }

            @Override
            public int insert(ItemResource resource, int amount, TransactionContext transaction) {
                int in = handler.insert(index, resource, amount, transaction);
                return in < amount ? in + handler.insert(resource, amount - in, transaction) : in;
            }

            @Override
            public int extract(ItemResource resource, int amount, TransactionContext transaction) {
                return handler.extract(index, resource, amount, transaction);
            }
        };
    }

    /** A stack held here and changed in place (journaled). */
    final class StackAccess implements ItemAccess {
        private ItemStack stack;
        private final SnapshotJournal<ItemStack> journal = new SnapshotJournal<>() {
            @Override
            protected ItemStack createSnapshot() {
                return StackAccess.this.stack.copy();
            }

            @Override
            protected void revertToSnapshot(ItemStack snapshot) {
                StackAccess.this.stack = snapshot;
            }
        };

        StackAccess(ItemStack stack) {
            this.stack = stack;
        }

        public ItemStack stack() {
            return this.stack;
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
            if (resource.isEmpty() || amount <= 0 || !this.stack.isEmpty() && !resource.matches(this.stack)) {
                return 0;
            }
            int in = Math.min(amount, resource.getMaxStackSize() - this.stack.getCount());
            if (in <= 0) {
                return 0;
            }
            this.journal.updateSnapshots(transaction);
            this.stack = resource.toStack(this.stack.getCount() + in);
            return in;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0 || !resource.matches(this.stack)) {
                return 0;
            }
            int out = Math.min(amount, this.stack.getCount());
            this.journal.updateSnapshots(transaction);
            this.stack = resource.toStack(this.stack.getCount() - out);
            return out;
        }
    }

    /** The item in a player's hand; what does not fit back there is given to them when the transaction commits. */
    final class PlayerAccess implements ItemAccess {
        private final Player player;
        private final InteractionHand hand;
        private final List<ItemStack> overflow = new ArrayList<>();
        private final SnapshotJournal<Object[]> journal = new SnapshotJournal<>() {
            @Override
            protected Object[] createSnapshot() {
                return new Object[] {PlayerAccess.this.player.getItemInHand(PlayerAccess.this.hand).copy(), PlayerAccess.this.overflow.size()};
            }

            @Override
            protected void revertToSnapshot(Object[] snapshot) {
                PlayerAccess.this.player.setItemInHand(PlayerAccess.this.hand, (ItemStack) snapshot[0]);
                int n = (Integer) snapshot[1];
                PlayerAccess.this.overflow.subList(n, PlayerAccess.this.overflow.size()).clear();
            }

            @Override
            protected void onRootCommit(Object[] originalState) {
                for (ItemStack s : PlayerAccess.this.overflow) {
                    if (!PlayerAccess.this.player.getInventory().add(s)) {
                        PlayerAccess.this.player.drop(s, false);
                    }
                }
                PlayerAccess.this.overflow.clear();
            }
        };

        PlayerAccess(Player player, InteractionHand hand) {
            this.player = player;
            this.hand = hand;
        }

        private boolean infinite() {
            return this.player.hasInfiniteMaterials();
        }

        @Override
        public ItemResource getResource() {
            return ItemResource.of(this.player.getItemInHand(this.hand));
        }

        @Override
        public int getAmount() {
            return this.player.getItemInHand(this.hand).getCount();
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            this.journal.updateSnapshots(transaction);
            if (infinite()) {
                if (!this.player.getInventory().contains(resource.toStack(1))) {
                    this.overflow.add(resource.toStack(1));
                }
                return amount;
            }
            ItemStack held = this.player.getItemInHand(this.hand);
            int left = amount;
            if (held.isEmpty() || resource.matches(held)) {
                int in = Math.min(left, resource.getMaxStackSize() - held.getCount());
                if (in > 0) {
                    this.player.setItemInHand(this.hand, resource.toStack(held.getCount() + in));
                    left -= in;
                }
            }
            if (left > 0) {
                this.overflow.add(resource.toStack(left));
            }
            return amount;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            ItemStack held = this.player.getItemInHand(this.hand);
            if (resource.isEmpty() || amount <= 0 || !resource.matches(held)) {
                return 0;
            }
            int out = Math.min(amount, held.getCount());
            if (!infinite()) {
                this.journal.updateSnapshots(transaction);
                this.player.setItemInHand(this.hand, resource.toStack(held.getCount() - out));
            }
            return out;
        }
    }
}
