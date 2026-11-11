package dev.groundburst.legacy;

import java.util.Arrays;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.legacy.net.ByteBufCodecs;
import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/** NeoForge's fluid ingredient with an amount (a machine recipe's fluid input), read from the same JSON. */
public final class SizedFluidIngredient {
    /** {"ingredient": ..., "amount": n} */
    public static final Codec<SizedFluidIngredient> NESTED_CODEC = RecordCodecBuilder.create(i -> i.group(
            FluidIngredient.CODEC_NON_EMPTY.fieldOf("ingredient").forGetter(SizedFluidIngredient::ingredient),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("amount", 1000).forGetter(SizedFluidIngredient::amount))
            .apply(i, SizedFluidIngredient::new));
    public static final StreamCodec<FriendlyByteBuf, SizedFluidIngredient> STREAM_CODEC = StreamCodec.composite(
            FluidIngredient.STREAM_CODEC, SizedFluidIngredient::ingredient, ByteBufCodecs.VAR_INT, SizedFluidIngredient::amount,
            SizedFluidIngredient::new);

    private final FluidIngredient ingredient;
    private final int amount;

    public SizedFluidIngredient(FluidIngredient ingredient, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.ingredient = ingredient;
        this.amount = amount;
    }

    public static SizedFluidIngredient of(Fluid fluid, int amount) {
        return new SizedFluidIngredient(FluidIngredient.of(fluid), amount);
    }

    public static SizedFluidIngredient of(FluidStack stack) {
        return new SizedFluidIngredient(FluidIngredient.of(stack.getFluid()), stack.getAmount());
    }

    public static SizedFluidIngredient of(TagKey<Fluid> tag, int amount) {
        return new SizedFluidIngredient(FluidIngredient.tag(tag), amount);
    }

    public FluidIngredient ingredient() {
        return this.ingredient;
    }

    public int amount() {
        return this.amount;
    }

    public boolean test(FluidStack stack) {
        return this.ingredient.test(stack) && stack.getAmount() >= this.amount;
    }

    public FluidStack[] getFluids() {
        return Arrays.stream(this.ingredient.getStacks()).map(s -> new FluidStack(s.getFluid(), this.amount)).toArray(FluidStack[]::new);
    }

    @Override
    public String toString() {
        return this.amount + "mb " + this.ingredient;
    }
}
