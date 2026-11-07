package dev.groundburst.content.construction;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Filing Cabinet's 27 stacks (main's DESIGN.md §22.4), kept as a barrel keeps its own: its top drawer pulled out
 * while anyone has it open, or while a redstone signal reaches it (the user's wish: so it can be built with open), a
 * drawer's slide and thump as it opens and shuts. The 1.21.1 backport's, after 1.21.1's barrel (its containers are
 * opened by players and saved to tags).
 */
public class FilingCabinetBlockEntity extends RandomizableContainerBlockEntity {
    public static final String T_NAME = GBLang.add("container." + GroundBurst.MODID + ".filing_cabinet", "Filing Cabinet");
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            if (!isOpen(state)) {
                sound(state, openSound());
                setOpen(state, true);
            }
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            if (!held()) {                                     // (a signal holding it open, it stays open)
                sound(state, closeSound());
                setOpen(state, false);
            }
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == FilingCabinetBlockEntity.this;
        }
    };

    public FilingCabinetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items, registries);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items, registries);
        }
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(T_NAME);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public void startOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openers.incrementOpeners(player, getLevel(), getBlockPos(), getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openers.decrementOpeners(player, getLevel(), getBlockPos(), getBlockState());
        }
    }

    public void recheckOpen() {
        if (!this.remove) {
            this.openers.recheckOpeners(getLevel(), getBlockPos(), getBlockState());
        }
    }

    /** Whether a redstone signal holds it open: one reaching its block (a locker's, either of its two). */
    protected boolean held() {
        return this.level != null && this.level.hasNeighborSignal(this.worldPosition);
    }

    /** Looks again at the redstone round it: open while anyone has it open or a signal holds it, shut when neither does. */
    public void lookAtPower() {
        if (this.level == null || this.remove) {
            return;
        }
        BlockState state = getBlockState();
        boolean open = this.openers.getOpenerCount() > 0 || held();
        if (state.hasProperty(FilingCabinetBlock.OPEN) && open != isOpen(state)) {
            sound(state, open ? openSound() : closeSound());
            setOpen(state, open);
        }
    }

    private static boolean isOpen(BlockState state) {
        return state.hasProperty(FilingCabinetBlock.OPEN) && state.getValue(FilingCabinetBlock.OPEN);
    }

    /** Its drawer rolled out (a locker: its door swung open). */
    protected SoundEvent openSound() {
        return GBSounds.FILING_CABINET_OPEN.get();
    }

    protected SoundEvent closeSound() {
        return GBSounds.FILING_CABINET_CLOSE.get();
    }

    private void setOpen(BlockState state, boolean open) {
        if (this.level != null && state.hasProperty(FilingCabinetBlock.OPEN)) {
            this.level.setBlockAndUpdate(getBlockPos(), state.setValue(FilingCabinetBlock.OPEN, open));
        }
    }

    private void sound(BlockState state, SoundEvent event) {
        if (this.level == null) {
            return;
        }
        var front = state.getValue(FilingCabinetBlock.FACING);
        this.level.playSound(null, this.worldPosition.getX() + 0.5 + front.getStepX() * 0.4, this.worldPosition.getY() + 0.8,
                this.worldPosition.getZ() + 0.5 + front.getStepZ() * 0.4, event, SoundSource.BLOCKS, 0.6f, 0.9f + this.level.getRandom().nextFloat() * 0.1f);
    }
}
