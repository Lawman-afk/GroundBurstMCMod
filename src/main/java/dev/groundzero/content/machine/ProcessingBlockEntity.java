package dev.groundzero.content.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.groundzero.content.recipe.GZRecipes;
import dev.groundzero.content.recipe.MachineRecipeInput;
import dev.groundzero.content.recipe.ProcessingKind;
import dev.groundzero.content.recipe.ProcessingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A machine that runs {@link ProcessingRecipe}s of one kind: input slots first, then output slots. Progress only
 * advances on ticks with enough energy; outputs must fully fit before a cycle starts, so nothing is ever voided.
 */
public abstract class ProcessingBlockEntity extends MachineBlockEntity {
    public static final int DATA_PROGRESS = 0, DATA_MAX_PROGRESS = 1;

    private final ProcessingKind kind;
    private final int inputCount;
    /** Index of the catalyst (die) slot, or -1. Sits between the inputs and the outputs. */
    private final int catalystSlot;
    private final int outputStart;
    private final int outputCount;
    private int progress;
    private int maxProgress;
    private @Nullable RecipeHolder<ProcessingRecipe> cached;

    protected ProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, ProcessingKind kind,
            int inputCount, int outputCount, int energyCapacity, int maxInsert) {
        this(type, pos, state, kind, inputCount, false, outputCount, energyCapacity, maxInsert);
    }

    protected ProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, ProcessingKind kind,
            int inputCount, boolean catalyst, int outputCount, int energyCapacity, int maxInsert) {
        super(type, pos, state, energyCapacity, maxInsert, 0, inputCount + (catalyst ? 1 : 0) + outputCount,
                range(0, inputCount + (catalyst ? 1 : 0)), range(inputCount + (catalyst ? 1 : 0), outputCount));
        this.kind = kind;
        this.inputCount = inputCount;
        this.catalystSlot = catalyst ? inputCount : -1;
        this.outputStart = inputCount + (catalyst ? 1 : 0);
        this.outputCount = outputCount;
    }

    private static int[] range(int start, int count) {
        int[] r = new int[count];
        for (int i = 0; i < count; i++) {
            r[i] = start + i;
        }
        return r;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        if (slot >= this.outputStart) {
            return false;                // outputs are filled by the machine only
        }
        // Recipes live on the server; the client side of a menu stays permissive and the server corrects it.
        return !(this.level instanceof ServerLevel server) || usedByRecipe(server, stack, slot == this.catalystSlot);
    }

    /** True if some recipe of this machine's kind uses the item as an ingredient (or as its catalyst). */
    private boolean usedByRecipe(ServerLevel level, ItemStack stack, boolean asCatalyst) {
        for (RecipeHolder<ProcessingRecipe> holder : level.recipeAccess().recipeMap().byType(GZRecipes.type(this.kind))) {
            ProcessingRecipe recipe = holder.value();
            if (asCatalyst) {
                if (recipe.catalyst().isPresent() && recipe.catalyst().get().test(stack)) {
                    return true;
                }
                continue;
            }
            for (var ingredient : recipe.inputs()) {
                if (ingredient.ingredient().test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private MachineRecipeInput input() {
        List<ItemStack> stacks = new ArrayList<>(this.inputCount);
        for (int i = 0; i < this.inputCount; i++) {
            stacks.add(this.inventory.getItem(i));
        }
        return new MachineRecipeInput(stacks, this.catalystSlot >= 0 ? this.inventory.getItem(this.catalystSlot) : ItemStack.EMPTY);
    }

    private Optional<RecipeHolder<ProcessingRecipe>> findRecipe(ServerLevel level, MachineRecipeInput input) {
        if (this.cached != null && this.cached.value().matches(input, level)) {
            return Optional.of(this.cached);
        }
        Optional<RecipeHolder<ProcessingRecipe>> found = level.recipeAccess().getRecipeFor(GZRecipes.type(this.kind), input, level);
        this.cached = found.orElse(null);
        return found;
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        MachineRecipeInput input = input();
        Optional<RecipeHolder<ProcessingRecipe>> holder = input.isEmpty() ? Optional.empty() : findRecipe(level, input);
        if (holder.isEmpty()) {
            resetProgress();
            this.status = input.isEmpty() ? MachineStatus.IDLE : MachineStatus.NO_RECIPE;
            return false;
        }
        ProcessingRecipe recipe = holder.get().value();
        if (!outputsFit(recipe)) {
            this.status = MachineStatus.OUTPUT_FULL;
            return false;
        }
        if (!this.energy.consume(recipe.energyPerTick())) {
            this.status = MachineStatus.NO_POWER;
            return false;
        }
        this.status = MachineStatus.RUNNING;
        this.maxProgress = recipe.time();
        if (++this.progress >= this.maxProgress) {
            finish(level, recipe, input);
            this.progress = 0;
        }
        setChanged();
        return true;
    }

    private void resetProgress() {
        if (this.progress != 0 || this.maxProgress != 0) {
            this.progress = 0;
            this.maxProgress = 0;
            setChanged();
        }
    }

    /** Every output (even unlikely byproducts) must fit, so a lucky roll can never be lost. */
    private boolean outputsFit(ProcessingRecipe recipe) {
        List<ItemStack> simulated = new ArrayList<>();
        for (int i = 0; i < this.outputCount; i++) {
            simulated.add(this.inventory.getItem(this.outputStart + i).copy());
        }
        for (ProcessingRecipe.Output out : recipe.outputs()) {
            ItemStack remaining = out.item().create();
            for (int i = 0; i < simulated.size() && !remaining.isEmpty(); i++) {
                ItemStack slot = simulated.get(i);
                if (slot.isEmpty()) {
                    simulated.set(i, remaining);
                    remaining = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(slot, remaining)) {
                    int moved = Math.min(slot.getMaxStackSize() - slot.getCount(), remaining.getCount());
                    slot.grow(moved);
                    remaining.shrink(moved);
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void finish(ServerLevel level, ProcessingRecipe recipe, MachineRecipeInput input) {
        int[] slots = recipe.assignSlots(input);
        if (slots == null) {
            return;
        }
        for (int n = 0; n < slots.length; n++) {
            this.inventory.removeItem(slots[n], recipe.inputs().get(n).count());
        }
        for (ProcessingRecipe.Output out : recipe.outputs()) {
            if (out.chance() < 1f && level.getRandom().nextFloat() >= out.chance()) {
                continue;
            }
            ItemStack remaining = out.item().create();
            for (int i = 0; i < this.outputCount && !remaining.isEmpty(); i++) {
                remaining = this.inventory.insertDirect(this.outputStart + i, remaining, false);
            }
        }
    }

    @Override
    protected int extraDataCount() {
        return 2;
    }

    @Override
    protected int getExtraData(int index) {
        return index == DATA_PROGRESS ? this.progress : index == DATA_MAX_PROGRESS ? this.maxProgress : 0;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("progress", this.progress);
        output.putInt("max_progress", this.maxProgress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("progress", 0);
        this.maxProgress = input.getIntOr("max_progress", 0);
    }
}
