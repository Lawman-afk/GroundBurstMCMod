package dev.groundburst.content.construction;

import java.util.List;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Filing Cabinet's 27 stacks (DESIGN.md §22.4), kept as a barrel keeps its own: its top drawer pulled out while
 * anyone has it open, or while a redstone signal reaches it (the user's wish: so it can be built with open), a drawer's
 * slide and thump as it opens and shuts.
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
        public boolean isOwnContainer(Player player) {
            if (player.containerMenu instanceof ChestMenu menu) {
                Container container = menu.getContainer();
                return container == FilingCabinetBlockEntity.this;
            }
            return false;
        }
    };

    public FilingCabinetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
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
    public void startOpen(ContainerUser user) {
        if (!this.remove && !user.getLivingEntity().isSpectator()) {
            this.openers.incrementOpeners(user.getLivingEntity(), getLevel(), getBlockPos(), getBlockState(), user.getContainerInteractionRange());
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (!this.remove && !user.getLivingEntity().isSpectator()) {
            this.openers.decrementOpeners(user.getLivingEntity(), getLevel(), getBlockPos(), getBlockState());
        }
    }

    @Override
    public List<ContainerUser> getEntitiesWithContainerOpen() {
        return this.openers.getEntitiesWithContainerOpen(getLevel(), getBlockPos());
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
        var front = state.getValue(FilingCabinetBlock.FACING).getUnitVec3i();
        this.level.playSound(null, this.worldPosition.getX() + 0.5 + front.getX() * 0.4, this.worldPosition.getY() + 0.8,
                this.worldPosition.getZ() + 0.5 + front.getZ() * 0.4, event, SoundSource.BLOCKS, 0.6f, 0.9f + this.level.getRandom().nextFloat() * 0.1f);
    }
}
