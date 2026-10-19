package dev.groundzero.content.fluid;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * One of the mod's chemical fluids (DESIGN.md §6): its fluid type, still and flowing fluids, and for liquids the
 * in-world block and bucket. Every one also has a canister item that holds 1000 mB of it.
 *
 * @param look      which shared animated texture set it uses (tinted by {@code color})
 * @param color     ARGB tint for the texture, GUI gauges, tank windows and buckets
 * @param hazard    what touching the in-world liquid does
 * @param canister  the filled canister item; {@code emptyCanister} is what is left when it is poured out
 */
public record ChemFluid(String name, Look look, int color, Hazard hazard,
                        DeferredHolder<FluidType, FluidType> type,
                        DeferredHolder<net.minecraft.world.level.material.Fluid, BaseFlowingFluid.Source> source,
                        DeferredHolder<net.minecraft.world.level.material.Fluid, BaseFlowingFluid.Flowing> flowing,
                        @Nullable DeferredBlock<? extends LiquidBlock> block, @Nullable DeferredItem<BucketItem> bucket,
                        Supplier<? extends Item> canister, Supplier<? extends Item> emptyCanister) {

    /** Shared texture sets: thin watery liquid, thick gel or slurry, and gas (never placed in the world). */
    public enum Look {
        LIQUID, THICK, GAS;

        public String texture() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** What a spilled liquid does to things standing in it. */
    public enum Hazard { NONE, ACID, FIRE, CRYO }

    /** The common tag other mods use for this fluid, e.g. {@code c:sulfuric_acid}; recipes take it, not our fluid. */
    public net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> commonTag() {
        return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("c", this.name));
    }

    public boolean isGas() {
        return this.look == Look.GAS;
    }

    public FlowingFluid sourceFluid() {
        return this.source.get();
    }
}
