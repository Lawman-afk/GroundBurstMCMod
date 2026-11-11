package dev.groundburst.content.recipe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.port.FluidStackTemplate;
import dev.groundburst.port.ItemStackTemplate;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;
import dev.groundburst.legacy.net.ByteBufCodecs;
import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import dev.groundburst.legacy.SizedIngredient;
import net.minecraftforge.fluids.FluidStack;
import dev.groundburst.legacy.SizedFluidIngredient;

/**
 * One machine process: counted item inputs and fluid inputs -> item outputs (each with a chance) and fluid outputs,
 * taking `time` ticks at `energyPerTick` FE/t. An optional catalyst (e.g. an Industrial Press die) must sit in the
 * machine's catalyst slot and is not consumed. Fluid inputs come from the machine's input tanks (each from a
 * different tank), fluid outputs go to its output tanks. Every processing machine uses this shape; the
 * {@link ProcessingKind} decides which machine runs it.
 */
public record ProcessingRecipe(ProcessingKind kind, List<SizedIngredient> inputs, Optional<Ingredient> catalyst,
        List<SizedFluidIngredient> fluids, List<Output> outputs, List<FluidStackTemplate> fluidOutputs, int time, int energyPerTick)
        implements Recipe<MachineRecipeInput> {
    @Override
    public net.minecraft.resources.ResourceLocation getId() {
        return dev.groundburst.legacy.recipe.Recipes.idOf(this);
    }


    public ProcessingRecipe(ProcessingKind kind, List<SizedIngredient> inputs, List<Output> outputs, int time, int energyPerTick) {
        this(kind, inputs, Optional.empty(), List.of(), outputs, List.of(), time, energyPerTick);
    }

    /**
     * The recipe a machine of {@code kind} makes from {@code input}: of those that match, the one that takes the most (its
     * items, fluids and catalyst together), so one recipe inside another (RDX's nitric acid and coal inside photoresist's)
     * never takes its place; then the first by name. The same whatever order the game lists the recipes in (it had been
     * the game's first match, which the 1.21.1 build lists otherwise: it made RDX of photoresist's makings).
     */
    public static Optional<dev.groundburst.legacy.recipe.RecipeHolder<ProcessingRecipe>> pick(net.minecraft.server.level.ServerLevel level,
            ProcessingKind kind, MachineRecipeInput input) {
        dev.groundburst.legacy.recipe.RecipeHolder<ProcessingRecipe> best = null;
        int most = -1;
        for (dev.groundburst.legacy.recipe.RecipeHolder<ProcessingRecipe> holder : dev.groundburst.legacy.recipe.Recipes.all(level.getRecipeManager(), GBRecipes.type(kind))) {
            ProcessingRecipe r = holder.value();
            if (!r.matches(input, level)) {
                continue;
            }
            int takes = r.inputs().size() + r.fluids().size() + (r.catalyst().isPresent() ? 1 : 0);
            if (takes > most || takes == most && String.valueOf(holder.id()).compareTo(String.valueOf(best.id())) < 0) {
                best = holder;
                most = takes;
            }
        }
        return Optional.ofNullable(best);
    }

    public ProcessingRecipe(ProcessingKind kind, List<SizedIngredient> inputs, Optional<Ingredient> catalyst, List<Output> outputs,
            int time, int energyPerTick) {
        this(kind, inputs, catalyst, List.of(), outputs, List.of(), time, energyPerTick);
    }

    /** A product; `chance` < 1 makes it a byproduct. */
    public record Output(ItemStackTemplate item, float chance) {
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(Output::item),
                Codec.floatRange(0, 1).optionalFieldOf("chance", 1f).forGetter(Output::chance)
        ).apply(i, Output::new));
        public static final StreamCodec<FriendlyByteBuf, Output> STREAM_CODEC = StreamCodec.composite(
                ItemStackTemplate.STREAM_CODEC, Output::item, ByteBufCodecs.FLOAT, Output::chance, Output::new);
    }

    static MapCodec<ProcessingRecipe> codec(ProcessingKind kind) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                SizedIngredient.NESTED_CODEC.listOf().optionalFieldOf("inputs", List.of()).forGetter(ProcessingRecipe::inputs),
                dev.groundburst.legacy.net.Codecs.INGREDIENT_NONEMPTY_CODEC.optionalFieldOf("catalyst").forGetter(ProcessingRecipe::catalyst),
                SizedFluidIngredient.NESTED_CODEC.listOf().optionalFieldOf("fluids", List.of()).forGetter(ProcessingRecipe::fluids),
                Output.CODEC.listOf().optionalFieldOf("outputs", List.of()).forGetter(ProcessingRecipe::outputs),
                FluidStackTemplate.CODEC.listOf().optionalFieldOf("fluid_outputs", List.of()).forGetter(ProcessingRecipe::fluidOutputs),
                ExtraCodecs.POSITIVE_INT.fieldOf("time").forGetter(ProcessingRecipe::time),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("energy_per_tick").forGetter(ProcessingRecipe::energyPerTick)
        ).apply(i, (in, cat, fl, out, flOut, time, fe) -> new ProcessingRecipe(kind, in, cat, fl, out, flOut, time, fe)));
    }

    static StreamCodec<FriendlyByteBuf, ProcessingRecipe> streamCodec(ProcessingKind kind) {
        return dev.groundburst.port.StreamCodecs.composite(
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::inputs,
                ByteBufCodecs.optional(dev.groundburst.legacy.net.Codecs.INGREDIENT), ProcessingRecipe::catalyst,
                SizedFluidIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::fluids,
                Output.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::outputs,
                FluidStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::fluidOutputs,
                ByteBufCodecs.VAR_INT, ProcessingRecipe::time,
                ByteBufCodecs.VAR_INT, ProcessingRecipe::energyPerTick,
                (in, cat, fl, out, flOut, time, fe) -> new ProcessingRecipe(kind, in, cat, fl, out, flOut, time, fe));
    }

    /**
     * Every ingredient must be satisfied by a different input slot, every fluid by a different input tank; the catalyst
     * must sit in its slot, and a recipe with none only runs with that slot empty (the Electrolytic Separator: with its
     * membrane fitted, hydrogen; without, deuterium).
     */
    @Override
    public boolean matches(MachineRecipeInput input, Level level) {
        if (!catalystFits(input.catalyst())) {
            return false;
        }
        return assignSlots(input) != null && assignTanks(input) != null;
    }

    /** Whether what sits in the machine's catalyst slot suits this recipe: its catalyst, or nothing if it takes none. */
    public boolean catalystFits(ItemStack inSlot) {
        return this.catalyst.isPresent() ? this.catalyst.get().test(inSlot) : inSlot.isEmpty();
    }

    /** Slot index used for each ingredient, or null if the inputs don't satisfy the recipe. */
    public int[] assignSlots(MachineRecipeInput input) {
        int[] assignment = new int[this.inputs.size()];
        boolean[] used = new boolean[input.size()];
        for (int n = 0; n < this.inputs.size(); n++) {
            SizedIngredient need = this.inputs.get(n);
            int found = -1;
            for (int s = 0; s < input.size(); s++) {
                ItemStack stack = input.getItem(s);
                if (!used[s] && need.test(stack)) {
                    found = s;
                    break;
                }
            }
            if (found < 0) {
                return null;
            }
            used[found] = true;
            assignment[n] = found;
        }
        return assignment;
    }

    /** Input tank (index into {@code input.fluids()}) used for each fluid ingredient, or null if unsatisfied. */
    public int[] assignTanks(MachineRecipeInput input) {
        int[] assignment = new int[this.fluids.size()];
        boolean[] used = new boolean[input.fluids().size()];
        for (int n = 0; n < this.fluids.size(); n++) {
            SizedFluidIngredient need = this.fluids.get(n);
            int found = -1;
            for (int t = 0; t < input.fluids().size(); t++) {
                FluidStack stack = input.fluids().get(t);
                if (!used[t] && need.test(stack)) {
                    found = t;
                    break;
                }
            }
            if (found < 0) {
                return null;
            }
            used[found] = true;
            assignment[n] = found;
        }
        return assignment;
    }

    public ItemStack assemble(MachineRecipeInput input) {
        return this.outputs.isEmpty() ? ItemStack.EMPTY : dev.groundburst.legacy.J21.first(this.outputs).item().create();
    }

    @Override
    public ItemStack assemble(MachineRecipeInput input, net.minecraft.core.RegistryAccess registries) {
        return assemble(input);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) {
        return this.outputs.isEmpty() ? ItemStack.EMPTY : dev.groundburst.legacy.J21.first(this.outputs).item().create();
    }

    @Override
    public boolean isIncomplete() {
        return false;   // 1.21.1 counts a recipe with no ingredient list as incomplete; these keep their own
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String getGroup() {
        return "";
    }

    @Override
    public boolean isSpecial() {
        return true;   // not shown in the vanilla recipe book
    }

    @Override
    public RecipeSerializer<? extends Recipe<MachineRecipeInput>> getSerializer() {
        return GBRecipes.serializer(this.kind);
    }

    @Override
    public RecipeType<? extends Recipe<MachineRecipeInput>> getType() {
        return GBRecipes.type(this.kind);
    }
}
