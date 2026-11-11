package dev.groundburst.legacy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

/**
 * NeoForge's fluid ingredient (Forge 1.20.1 has none): fluids by id ({"fluid": ...}) or by tag ({"tag": ...}), or a list
 * of them, read from the same JSON as NeoForge's.
 */
public final class FluidIngredient implements Predicate<FluidStack> {
    private final List<Fluid> fluids;
    private final List<TagKey<Fluid>> tags;

    private FluidIngredient(List<Fluid> fluids, List<TagKey<Fluid>> tags) {
        this.fluids = fluids;
        this.tags = tags;
    }

    private record Value(Either<Fluid, TagKey<Fluid>> value) {
    }

    private static final Codec<Value> VALUE_CODEC = Codec.either(
            RecordCodecBuilder.<Fluid>create(i -> i.group(BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(f -> f)).apply(i, f -> f)),
            RecordCodecBuilder.<TagKey<Fluid>>create(i -> i.group(TagKey.codec(Registries.FLUID).fieldOf("tag").forGetter(t -> t)).apply(i, t -> t)))
            .xmap(Value::new, Value::value);

    public static final Codec<FluidIngredient> CODEC = Codec.either(VALUE_CODEC.listOf(), VALUE_CODEC).xmap(
            e -> e.map(FluidIngredient::fromValues, v -> fromValues(List.of(v))),
            ing -> Either.left(ing.values()));
    public static final Codec<FluidIngredient> CODEC_NON_EMPTY = CODEC.flatXmap(
            i -> i.isEmpty() ? DataResult.error(() -> "Empty fluid ingredient") : DataResult.success(i), DataResult::success);
    public static final StreamCodec<FriendlyByteBuf, FluidIngredient> STREAM_CODEC = StreamCodec.of(
            (b, i) -> {
                b.writeCollection(i.fluids, (bb, f) -> bb.writeResourceLocation(BuiltInRegistries.FLUID.getKey(f)));
                b.writeCollection(i.tags, (bb, t) -> bb.writeResourceLocation(t.location()));
            },
            b -> new FluidIngredient(
                    b.readList(bb -> BuiltInRegistries.FLUID.get(bb.readResourceLocation())),
                    b.readList(bb -> TagKey.create(Registries.FLUID, bb.readResourceLocation()))));

    private static FluidIngredient fromValues(List<Value> values) {
        List<Fluid> fluids = new ArrayList<>();
        List<TagKey<Fluid>> tags = new ArrayList<>();
        for (Value v : values) {
            v.value().ifLeft(fluids::add).ifRight(tags::add);
        }
        return new FluidIngredient(fluids, tags);
    }

    private List<Value> values() {
        List<Value> out = new ArrayList<>();
        this.fluids.forEach(f -> out.add(new Value(Either.left(f))));
        this.tags.forEach(t -> out.add(new Value(Either.right(t))));
        return out;
    }

    public static FluidIngredient of() {
        return new FluidIngredient(List.of(), List.of());
    }

    public static FluidIngredient of(Fluid... fluids) {
        return new FluidIngredient(List.of(fluids), List.of());
    }

    public static FluidIngredient of(FluidStack... stacks) {
        return new FluidIngredient(Arrays.stream(stacks).map(FluidStack::getFluid).toList(), List.of());
    }

    public static FluidIngredient single(Fluid fluid) {
        return of(fluid);
    }

    public static FluidIngredient tag(TagKey<Fluid> tag) {
        return new FluidIngredient(List.of(), List.of(tag));
    }

    @Override
    public boolean test(FluidStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Fluid fluid = stack.getFluid();
        if (this.fluids.contains(fluid)) {
            return true;
        }
        for (TagKey<Fluid> tag : this.tags) {
            if (fluid.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** One bucket of each fluid it takes. */
    public FluidStack[] getStacks() {
        List<FluidStack> out = new ArrayList<>();
        for (Fluid f : this.fluids) {
            out.add(new FluidStack(f, FluidType.BUCKET_VOLUME));
        }
        for (TagKey<Fluid> tag : this.tags) {
            BuiltInRegistries.FLUID.getTag(tag).ifPresent(set -> set.forEach(h -> {
                if (h.value() != Fluids.EMPTY) {
                    out.add(new FluidStack(h.value(), FluidType.BUCKET_VOLUME));
                }
            }));
        }
        return out.toArray(FluidStack[]::new);
    }

    public boolean isEmpty() {
        return this.fluids.isEmpty() && this.tags.isEmpty();
    }

    public boolean hasNoFluids() {
        return getStacks().length == 0;
    }

    @Override
    public String toString() {
        List<ResourceLocation> names = new ArrayList<>();
        this.fluids.forEach(f -> names.add(BuiltInRegistries.FLUID.getKey(f)));
        return "FluidIngredient" + names + this.tags;
    }
}
