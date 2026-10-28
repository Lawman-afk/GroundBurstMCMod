package dev.groundburst.content.recipe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

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

    public ProcessingRecipe(ProcessingKind kind, List<SizedIngredient> inputs, List<Output> outputs, int time, int energyPerTick) {
        this(kind, inputs, Optional.empty(), List.of(), outputs, List.of(), time, energyPerTick);
    }

    public ProcessingRecipe(ProcessingKind kind, List<SizedIngredient> inputs, Optional<Ingredient> catalyst, List<Output> outputs,
            int time, int energyPerTick) {
        this(kind, inputs, catalyst, List.of(), outputs, List.of(), time, energyPerTick);
    }

    /** A product; `chance` < 1 makes it a byproduct. */
    public record Output(ItemStackTemplate item, float chance) {
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(Output::item),
                ExtraCodecs.floatRange(0, 1).optionalFieldOf("chance", 1f).forGetter(Output::chance)
        ).apply(i, Output::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Output> STREAM_CODEC = StreamCodec.composite(
                ItemStackTemplate.STREAM_CODEC, Output::item, ByteBufCodecs.FLOAT, Output::chance, Output::new);
    }

    static MapCodec<ProcessingRecipe> codec(ProcessingKind kind) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                SizedIngredient.NESTED_CODEC.listOf(0, 9).optionalFieldOf("inputs", List.of()).forGetter(ProcessingRecipe::inputs),
                Ingredient.CODEC.optionalFieldOf("catalyst").forGetter(ProcessingRecipe::catalyst),
                SizedFluidIngredient.CODEC.listOf(0, 4).optionalFieldOf("fluids", List.of()).forGetter(ProcessingRecipe::fluids),
                Output.CODEC.listOf(0, 9).optionalFieldOf("outputs", List.of()).forGetter(ProcessingRecipe::outputs),
                FluidStackTemplate.CODEC.listOf(0, 4).optionalFieldOf("fluid_outputs", List.of()).forGetter(ProcessingRecipe::fluidOutputs),
                ExtraCodecs.POSITIVE_INT.fieldOf("time").forGetter(ProcessingRecipe::time),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("energy_per_tick").forGetter(ProcessingRecipe::energyPerTick)
        ).apply(i, (in, cat, fl, out, flOut, time, fe) -> new ProcessingRecipe(kind, in, cat, fl, out, flOut, time, fe)));
    }

    static StreamCodec<RegistryFriendlyByteBuf, ProcessingRecipe> streamCodec(ProcessingKind kind) {
        return StreamCodec.composite(
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::inputs,
                Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, ProcessingRecipe::catalyst,
                SizedFluidIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::fluids,
                Output.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::outputs,
                FluidStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::fluidOutputs,
                ByteBufCodecs.VAR_INT, ProcessingRecipe::time,
                ByteBufCodecs.VAR_INT, ProcessingRecipe::energyPerTick,
                (in, cat, fl, out, flOut, time, fe) -> new ProcessingRecipe(kind, in, cat, fl, out, flOut, time, fe));
    }

    /** Every ingredient must be satisfied by a different input slot, every fluid by a different input tank. */
    @Override
    public boolean matches(MachineRecipeInput input, Level level) {
        if (this.catalyst.isPresent() && !this.catalyst.get().test(input.catalyst())) {
            return false;
        }
        return assignSlots(input) != null && assignTanks(input) != null;
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

    @Override
    public ItemStack assemble(MachineRecipeInput input) {
        return this.outputs.isEmpty() ? ItemStack.EMPTY : this.outputs.getFirst().item().create();
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
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

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return GBRecipes.MACHINE_CATEGORY.get();
    }
}
