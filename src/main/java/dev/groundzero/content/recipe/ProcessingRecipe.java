package dev.groundzero.content.recipe;

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
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * One machine process: counted item inputs -> outputs (each with a chance), taking `time` ticks at
 * `energyPerTick` FE/t. An optional catalyst (e.g. an Industrial Press die) must sit in the machine's catalyst slot
 * and is not consumed. Every processing machine uses this shape; the {@link ProcessingKind} decides which machine
 * runs it.
 */
public record ProcessingRecipe(ProcessingKind kind, List<SizedIngredient> inputs, Optional<Ingredient> catalyst,
        Optional<SizedFluidIngredient> fluid, List<Output> outputs, int time, int energyPerTick) implements Recipe<MachineRecipeInput> {

    public ProcessingRecipe(ProcessingKind kind, List<SizedIngredient> inputs, List<Output> outputs, int time, int energyPerTick) {
        this(kind, inputs, Optional.empty(), Optional.empty(), outputs, time, energyPerTick);
    }

    public ProcessingRecipe(ProcessingKind kind, List<SizedIngredient> inputs, Optional<Ingredient> catalyst, List<Output> outputs,
            int time, int energyPerTick) {
        this(kind, inputs, catalyst, Optional.empty(), outputs, time, energyPerTick);
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
                SizedIngredient.NESTED_CODEC.listOf(1, 9).fieldOf("inputs").forGetter(ProcessingRecipe::inputs),
                Ingredient.CODEC.optionalFieldOf("catalyst").forGetter(ProcessingRecipe::catalyst),
                SizedFluidIngredient.CODEC.optionalFieldOf("fluid").forGetter(ProcessingRecipe::fluid),
                Output.CODEC.listOf(1, 9).fieldOf("outputs").forGetter(ProcessingRecipe::outputs),
                ExtraCodecs.POSITIVE_INT.fieldOf("time").forGetter(ProcessingRecipe::time),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("energy_per_tick").forGetter(ProcessingRecipe::energyPerTick)
        ).apply(i, (in, cat, fl, out, time, fe) -> new ProcessingRecipe(kind, in, cat, fl, out, time, fe)));
    }

    static StreamCodec<RegistryFriendlyByteBuf, ProcessingRecipe> streamCodec(ProcessingKind kind) {
        return StreamCodec.composite(
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::inputs,
                Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, ProcessingRecipe::catalyst,
                ByteBufCodecs.optional(SizedFluidIngredient.STREAM_CODEC), ProcessingRecipe::fluid,
                Output.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::outputs,
                ByteBufCodecs.VAR_INT, ProcessingRecipe::time,
                ByteBufCodecs.VAR_INT, ProcessingRecipe::energyPerTick,
                (in, cat, fl, out, time, fe) -> new ProcessingRecipe(kind, in, cat, fl, out, time, fe));
    }

    /** Every ingredient must be satisfied by a different input slot holding at least its count. */
    @Override
    public boolean matches(MachineRecipeInput input, Level level) {
        if (this.catalyst.isPresent() && !this.catalyst.get().test(input.catalyst())) {
            return false;
        }
        if (this.fluid.isPresent() && !this.fluid.get().test(input.fluid())) {
            return false;
        }
        return assignSlots(input) != null;
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

    @Override
    public ItemStack assemble(MachineRecipeInput input) {
        return this.outputs.getFirst().item().create();
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
        return GZRecipes.serializer(this.kind);
    }

    @Override
    public RecipeType<? extends Recipe<MachineRecipeInput>> getType() {
        return GZRecipes.type(this.kind);
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return GZRecipes.MACHINE_CATEGORY.get();
    }
}
