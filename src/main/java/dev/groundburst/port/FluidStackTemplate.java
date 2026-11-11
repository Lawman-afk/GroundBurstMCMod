package dev.groundburst.port;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import dev.groundburst.legacy.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import dev.groundburst.legacy.net.ByteBufCodecs;
import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

/**
 * NeoForge 26.x's fluid stack template (a fluid, an amount and a component patch, fixed: what a recipe makes), written
 * as 1.21.1 writes a fluid stack ({"id", "amount", "components"}, or the bare id for a bucket's worth).
 */
public record FluidStackTemplate(Holder<Fluid> fluid, int amount, DataComponentPatch components) {
    private static final Codec<Holder<Fluid>> FLUID_CODEC = BuiltInRegistries.FLUID.holderByNameCodec();

    public static final MapCodec<FluidStackTemplate> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FLUID_CODEC.fieldOf("id").forGetter(FluidStackTemplate::fluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidStackTemplate::amount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(FluidStackTemplate::components)
    ).apply(i, FluidStackTemplate::new));
    public static final Codec<FluidStackTemplate> CODEC = dev.groundburst.legacy.Codecs2.withAlternative(MAP_CODEC.codec(), FLUID_CODEC,
            fluid -> new FluidStackTemplate(fluid, FluidType.BUCKET_VOLUME, DataComponentPatch.EMPTY));
    public static final StreamCodec<FriendlyByteBuf, FluidStackTemplate> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.FLUID), FluidStackTemplate::fluid,
            ByteBufCodecs.VAR_INT, FluidStackTemplate::amount,
            DataComponentPatch.STREAM_CODEC, FluidStackTemplate::components,
            FluidStackTemplate::new);

    public FluidStackTemplate(Holder<Fluid> fluid, int amount) {
        this(fluid, amount, DataComponentPatch.EMPTY);
    }

    public FluidStackTemplate(Fluid fluid, int amount, DataComponentPatch components) {
        this(fluid.builtInRegistryHolder(), amount, components);
    }

    public FluidStackTemplate(Fluid fluid, int amount) {
        this(fluid.builtInRegistryHolder(), amount, DataComponentPatch.EMPTY);
    }

    public static FluidStackTemplate fromNonEmptyStack(FluidStack stack) {
        return new FluidStackTemplate(dev.groundburst.legacy.FluidStacks.holder(stack), stack.getAmount(), dev.groundburst.legacy.component.DataComponentPatch.of(stack));
    }

    public FluidStackTemplate withAmount(int amount) {
        return new FluidStackTemplate(this.fluid, amount, this.components);
    }

    public FluidStack create() {
        return dev.groundburst.legacy.component.DataComponentPatch.fluid(this.fluid, this.amount, this.components);
    }

    public Holder<Fluid> typeHolder() {
        return this.fluid;
    }
}
