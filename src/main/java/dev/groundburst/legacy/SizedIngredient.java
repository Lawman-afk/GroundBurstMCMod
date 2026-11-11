package dev.groundburst.legacy;

import java.util.Arrays;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.legacy.net.ByteBufCodecs;
import dev.groundburst.legacy.net.Codecs;
import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

/** NeoForge's ingredient with a count (a machine recipe's input), read from the same JSON. */
public final class SizedIngredient {
    /** {"ingredient": ..., "count": n} */
    public static final Codec<SizedIngredient> NESTED_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codecs.INGREDIENT_NONEMPTY_CODEC.fieldOf("ingredient").forGetter(SizedIngredient::ingredient),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(SizedIngredient::count))
            .apply(i, SizedIngredient::new));
    public static final StreamCodec<FriendlyByteBuf, SizedIngredient> STREAM_CODEC = StreamCodec.composite(
            Codecs.INGREDIENT, SizedIngredient::ingredient, ByteBufCodecs.VAR_INT, SizedIngredient::count, SizedIngredient::new);

    private final Ingredient ingredient;
    private final int count;

    public SizedIngredient(Ingredient ingredient, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Size must be positive");
        }
        this.ingredient = ingredient;
        this.count = count;
    }

    public static SizedIngredient of(ItemLike item, int count) {
        return new SizedIngredient(Ingredient.of(item), count);
    }

    public static SizedIngredient of(TagKey<Item> tag, int count) {
        return new SizedIngredient(Ingredient.of(tag), count);
    }

    public Ingredient ingredient() {
        return this.ingredient;
    }

    public int count() {
        return this.count;
    }

    public boolean test(ItemStack stack) {
        return this.ingredient.test(stack) && stack.getCount() >= this.count;
    }

    public ItemStack[] getItems() {
        return Arrays.stream(this.ingredient.getItems()).map(s -> s.copyWithCount(this.count)).toArray(ItemStack[]::new);
    }

    @Override
    public String toString() {
        return this.count + "x " + this.ingredient;
    }
}
