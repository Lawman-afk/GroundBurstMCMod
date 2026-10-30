package dev.groundburst.content.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.recipe.GBRecipes;
import dev.groundburst.content.recipe.MachineRecipeInput;
import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.content.recipe.ProcessingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
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
    private float speedCarry;

    protected ProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, ProcessingKind kind,
            int inputCount, int outputCount, int energyCapacity, int maxInsert) {
        this(type, pos, state, kind, inputCount, false, outputCount, energyCapacity, maxInsert);
    }

    protected ProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, ProcessingKind kind,
            int inputCount, boolean catalyst, int outputCount, int energyCapacity, int maxInsert) {
        this(type, pos, state, kind, inputCount, catalyst, outputCount, energyCapacity, maxInsert, List.of());
    }

    /** With tanks: recipe fluids come from the input tanks and fluid products go to the output tanks. */
    protected ProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, ProcessingKind kind,
            int inputCount, boolean catalyst, int outputCount, int energyCapacity, int maxInsert, List<MachineTank.Spec> tanks) {
        super(type, pos, state, energyCapacity, maxInsert, 0, inputCount + (catalyst ? 1 : 0) + outputCount,
                range(0, inputCount + (catalyst ? 1 : 0)), range(inputCount + (catalyst ? 1 : 0), outputCount), tanks);
        this.kind = kind;
        this.inputCount = inputCount;
        this.catalystSlot = catalyst ? inputCount : -1;
        this.outputStart = inputCount + (catalyst ? 1 : 0);
        this.outputCount = outputCount;
    }

    /** Every processing machine takes an upgrade chip (DESIGN.md §7.3). */
    @Override
    public boolean acceptsUpgrades() {
        return true;
    }

    /** Recipe progress per tick (1 = the recipe's own time); energy is still drawn once per tick. */
    protected float speedMultiplier() {
        return 1f;
    }

    public ProcessingKind kind() {
        return this.kind;
    }

    /** Index of the catalyst (die) slot, or -1 for machines without one. */
    public int catalystSlot() {
        return this.catalystSlot;
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

    /** Input tanks only take fluids some recipe of this machine uses (checked on the server, where recipes live). */
    @Override
    protected boolean acceptsFluid(int tank, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
        if (!(this.level instanceof ServerLevel server)) {
            return true;
        }
        FluidStack probe = resource.toStack(1000);
        for (RecipeHolder<ProcessingRecipe> holder : server.recipeAccess().recipeMap().byType(GBRecipes.type(this.kind))) {
            for (var fluid : holder.value().fluids()) {
                if (fluid.ingredient().test(probe)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if some recipe of this machine's kind uses the item as an ingredient (or as its catalyst). */
    private boolean usedByRecipe(ServerLevel level, ItemStack stack, boolean asCatalyst) {
        for (RecipeHolder<ProcessingRecipe> holder : level.recipeAccess().recipeMap().byType(GBRecipes.type(this.kind))) {
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
        List<FluidStack> fluids = new ArrayList<>();
        for (MachineTank tank : this.tanks) {
            if (tank.isInput()) {
                fluids.add(tank.fluid());
            }
        }
        return new MachineRecipeInput(stacks, this.catalystSlot >= 0 ? this.inventory.getItem(this.catalystSlot) : ItemStack.EMPTY, fluids);
    }

    /** Tank index of the n-th input tank (the order {@link #input()} lists their fluids in). */
    private MachineTank inputTank(int n) {
        for (MachineTank tank : this.tanks) {
            if (tank.isInput() && n-- == 0) {
                return tank;
            }
        }
        throw new IndexOutOfBoundsException();
    }

    /** Output tank for each fluid product (one each, preferring a tank that already holds it), or null if any won't fit. */
    private MachineTank @Nullable [] assignOutputTanks(ProcessingRecipe recipe) {
        MachineTank[] out = new MachineTank[recipe.fluidOutputs().size()];
        java.util.Set<MachineTank> used = new java.util.HashSet<>();
        for (int n = 0; n < out.length; n++) {
            FluidStack product = recipe.fluidOutputs().get(n).create();
            MachineTank best = null;
            for (MachineTank tank : this.tanks) {
                if (tank.isInput() || used.contains(tank) || tank.space(product.getFluid()) < product.getAmount()) {
                    continue;
                }
                if (best == null || !tank.isEmpty()) {
                    best = tank;
                }
            }
            if (best == null) {
                return null;
            }
            used.add(best);
            out[n] = best;
        }
        return out;
    }

    private Optional<RecipeHolder<ProcessingRecipe>> findRecipe(ServerLevel level, MachineRecipeInput input) {
        if (this.cached != null && this.cached.value().matches(input, level)) {
            return Optional.of(this.cached);
        }
        Optional<RecipeHolder<ProcessingRecipe>> found = level.recipeAccess().getRecipeFor(GBRecipes.type(this.kind), input, level);
        this.cached = found.orElse(null);
        return found;
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        MachineRecipeInput input = input();
        Optional<RecipeHolder<ProcessingRecipe>> holder = input.isEmpty() ? Optional.empty() : findRecipe(level, input);
        if (holder.isEmpty()) {
            resetProgress();
            this.status = input.isEmpty() ? MachineStatus.IDLE : shortOfFluid(level, input) ? MachineStatus.NEED_FLUID : MachineStatus.NO_RECIPE;
            return false;
        }
        ProcessingRecipe recipe = holder.get().value();
        if (!outputsFit(recipe)) {
            this.status = MachineStatus.OUTPUT_FULL;
            return false;
        }
        if (!this.energy.consume(workEnergy(recipe.energyPerTick()))) {
            this.status = MachineStatus.NO_POWER;
            return false;
        }
        this.status = MachineStatus.RUNNING;
        this.maxProgress = recipe.time();
        // Faster machines (a centrifuge cascade, an upgrade chip) bank the fraction of a step they gain each tick; a chip
        // draws power as much faster as it works (workEnergy), so a job costs the same.
        this.progress++;
        this.speedCarry += speedMultiplier() * workSpeed() - 1f;
        while (this.speedCarry >= 1f) {
            this.progress++;
            this.speedCarry -= 1f;
        }
        if (this.progress >= this.maxProgress) {
            finish(level, recipe, input);
            this.progress = 0;
        }
        setChanged();
        return true;
    }

    /**
     * Whether some recipe is only waiting for fluid, so the GUI can say "needs more fluid" rather than "no valid
     * input": its items (and catalyst) are all there but its fluids are missing or short. A recipe without items
     * counts once one of its fluids is in a tank (a separator with a single bucket of water).
     */
    private boolean shortOfFluid(ServerLevel level, MachineRecipeInput input) {
        for (RecipeHolder<ProcessingRecipe> holder : level.recipeAccess().recipeMap().byType(GBRecipes.type(this.kind))) {
            ProcessingRecipe recipe = holder.value();
            if (recipe.fluids().isEmpty() || recipe.assignSlots(input) == null
                    || recipe.catalyst().isPresent() && !recipe.catalyst().get().test(input.catalyst())) {
                continue;
            }
            boolean started = !recipe.inputs().isEmpty() || recipe.fluids().stream().anyMatch(need -> input.fluids().stream()
                    .anyMatch(have -> !have.isEmpty() && need.ingredient().test(have)));
            if (started && recipe.assignTanks(input) == null) {
                return true;
            }
        }
        return false;
    }

    private void resetProgress() {
        if (this.progress != 0 || this.maxProgress != 0) {
            this.progress = 0;
            this.maxProgress = 0;
            setChanged();
        }
    }

    /** Every output (even unlikely byproducts, and every fluid) must fit, so a lucky roll can never be lost. */
    private boolean outputsFit(ProcessingRecipe recipe) {
        if (assignOutputTanks(recipe) == null) {
            return false;
        }
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
        int[] tanks = recipe.assignTanks(input);
        MachineTank[] products = assignOutputTanks(recipe);
        if (slots == null || tanks == null || products == null) {
            return;
        }
        for (int n = 0; n < slots.length; n++) {
            this.inventory.removeItem(slots[n], recipe.inputs().get(n).count());
        }
        for (int n = 0; n < tanks.length; n++) {
            inputTank(tanks[n]).drainInternal(recipe.fluids().get(n).amount());
        }
        for (int n = 0; n < products.length; n++) {
            products[n].fillInternal(recipe.fluidOutputs().get(n).create());
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
