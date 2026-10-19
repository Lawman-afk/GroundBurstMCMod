package dev.groundzero.content.fluid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.chem.GZChemicals;
import dev.groundzero.content.fluid.ChemFluid.Hazard;
import dev.groundzero.content.fluid.ChemFluid.Look;
import dev.groundzero.content.part.GZParts;
import dev.groundzero.registry.GZBlocks;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZLang;
import dev.groundzero.registry.GZTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The mod's chemical fluids (DESIGN.md §6.4). Liquids can be poured into the world with a bucket and carried in
 * canisters; gases live only in tanks, pipes and gas canisters. Machines, pipes, buckets and canisters all move the
 * same fluids, so another mod's tank or pipe works with them too.
 */
public final class GZFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, GroundZero.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, GroundZero.MODID);

    private static final List<ChemFluid> ALL = new ArrayList<>();

    public static final ChemFluid SULFURIC_ACID = liquid("sulfuric_acid", "Sulfuric Acid", Look.LIQUID, 0xFFD8CE4A, Hazard.ACID,
            1830, 1, 300, GZChemicals.SULFURIC_ACID_CANISTER, GZParts.EMPTY_CANISTER, MapColor.COLOR_YELLOW);
    public static final ChemFluid NITRIC_ACID = liquid("nitric_acid", "Nitric Acid", Look.LIQUID, 0xFFE8A050, Hazard.ACID,
            1510, 1, 300, GZChemicals.NITRIC_ACID_CANISTER, GZParts.EMPTY_CANISTER, MapColor.COLOR_ORANGE);
    public static final ChemFluid SYNTHETIC_FUEL = liquid("synthetic_fuel", "Synthetic Fuel", Look.LIQUID, 0xFF7A5420, Hazard.NONE,
            820, 2, 300, GZChemicals.SYNTHETIC_FUEL_CANISTER, GZParts.EMPTY_CANISTER, MapColor.COLOR_BROWN);
    public static final ChemFluid LIQUID_ROCKET_FUEL = liquid("liquid_rocket_fuel", "Liquid Rocket Fuel", Look.LIQUID, 0xFFD04838, Hazard.NONE,
            900, 1, 250, GZChemicals.LIQUID_ROCKET_FUEL_CANISTER, GZParts.EMPTY_CANISTER, MapColor.COLOR_RED);
    public static final ChemFluid THERMOBARIC_SLURRY = liquid("thermobaric_slurry", "Thermobaric Slurry", Look.THICK, 0xFF8C949C, Hazard.NONE,
            1400, 6, 300, GZChemicals.THERMOBARIC_SLURRY_CANISTER, GZParts.EMPTY_CANISTER, MapColor.COLOR_GRAY);
    public static final ChemFluid NAPALM = liquid("napalm", "Napalm Gel", Look.THICK, 0xFFE2742A, Hazard.FIRE,
            950, 8, 300, GZChemicals.NAPALM_CANISTER, GZParts.EMPTY_CANISTER, MapColor.COLOR_ORANGE);
    public static final ChemFluid LIQUID_OXYGEN = liquid("liquid_oxygen", "Liquid Oxygen", Look.LIQUID, 0xFF9CC4FF, Hazard.CRYO,
            1140, 1, 90, GZChemicals.LIQUID_OXYGEN_CANISTER, GZParts.EMPTY_CANISTER, MapColor.COLOR_LIGHT_BLUE);
    public static final ChemFluid LIQUID_NITROGEN = liquid("liquid_nitrogen", "Liquid Nitrogen", Look.LIQUID, 0xFFD8E8F2, Hazard.CRYO,
            810, 1, 77, GZChemicals.LIQUID_NITROGEN_CANISTER, GZParts.EMPTY_CANISTER, MapColor.SNOW);
    public static final ChemFluid DEUTERIUM = gas("deuterium", "Deuterium", 0xFFA4BCFF, GZChemicals.DEUTERIUM_CANISTER);
    public static final ChemFluid URANIUM_HEXAFLUORIDE = gas("uranium_hexafluoride", "Uranium Hexafluoride", 0xFFC6D06A, GZChemicals.UF6_CANISTER);

    /** Steel pipe that carries fluids between machines and tanks. */
    public static final GZBlocks.WithItem<FluidPipeBlock> FLUID_PIPE = GZBlocks.register("fluid_pipe", "Fluid Pipe", GZTab.MACHINES,
            FluidPipeBlock::new, p -> p.mapColor(MapColor.METAL).strength(2f, 6f).requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER).noOcclusion().forceSolidOn());

    private GZFluids() {}

    public static List<ChemFluid> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** The chemical fluid a fluid belongs to (still or flowing), or null for anything else (water, other mods). */
    public static @Nullable ChemFluid of(Fluid fluid) {
        for (ChemFluid f : ALL) {
            if (f.source().get() == fluid || f.flowing().get() == fluid) {
                return f;
            }
        }
        return null;
    }

    /** The chemical fluid a filled canister holds, or null. */
    public static @Nullable ChemFluid ofCanister(Item item) {
        for (ChemFluid f : ALL) {
            if (f.canister().get() == item) {
                return f;
            }
        }
        return null;
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
    }

    public static void init() {}

    /** Empty and filled canisters fill and pour like buckets (see {@link CanisterFluidHandler}). */
    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        List<Item> canisters = new ArrayList<>(List.of(GZParts.EMPTY_CANISTER.get(), GZChemicals.EMPTY_GAS_CANISTER.get()));
        for (ChemFluid f : ALL) {
            canisters.add(f.canister().get());
        }
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM, (stack, access) -> new CanisterFluidHandler(access),
                canisters.toArray(Item[]::new));
        event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                (level, pos, state, be, side) -> FluidPipeNetwork.handler(level, pos, side), FLUID_PIPE.block().get());
    }

    private static ChemFluid liquid(String name, String english, Look look, int color, Hazard hazard, int density, int viscosityFactor,
            int temperature, Supplier<? extends Item> canister, Supplier<? extends Item> empty, MapColor mapColor) {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                .density(density).viscosity(1000 * viscosityFactor).temperature(temperature)
                .canSwim(viscosityFactor < 4).canDrown(true).canExtinguish(hazard != Hazard.FIRE).supportsBoating(false)
                .canConvertToSource(false).canHydrate(false).fallDistanceModifier(0.5f)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL).sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));
        GZLang.add("fluid_type." + GroundZero.MODID + "." + name, english);
        Holders h = new Holders();
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(type, () -> h.source.get(), () -> h.flowing.get())
                .bucket(() -> h.bucket.get()).block(() -> h.block.get())
                .slopeFindDistance(viscosityFactor > 4 ? 2 : 4).levelDecreasePerBlock(viscosityFactor > 4 ? 2 : 1)
                .tickRate(5 * viscosityFactor).explosionResistance(100f);
        h.source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(props));
        h.flowing = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(props));
        h.block = GZBlocks.blockOnly(name, english, p -> new ChemLiquidBlock(h.source.get(), hazard, p),
                p -> p.mapColor(mapColor).replaceable().noCollision().strength(100f).pushReaction(PushReaction.POPPED)
                        .noLootTable().liquid().sound(SoundType.EMPTY).lightLevel(s -> hazard == Hazard.FIRE ? 3 : 0));
        h.bucket = GZItems.register(name + "_bucket", english + " Bucket", GZTab.RESOURCES, p -> new BucketItem(h.source.get(), p),
                p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
        ChemFluid fluid = new ChemFluid(name, look, color, hazard, type, h.source, h.flowing, h.block, h.bucket, canister, empty);
        ALL.add(fluid);
        return fluid;
    }

    private static ChemFluid gas(String name, String english, int color, Supplier<? extends Item> canister) {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                .density(-500).viscosity(100).temperature(300).canSwim(false).canDrown(false).supportsBoating(false)
                .canConvertToSource(false).canHydrate(false)));
        GZLang.add("fluid_type." + GroundZero.MODID + "." + name, english);
        Holders h = new Holders();
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(type, () -> h.source.get(), () -> h.flowing.get());
        h.source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(props));
        h.flowing = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(props));
        ChemFluid fluid = new ChemFluid(name, Look.GAS, color, Hazard.NONE, type, h.source, h.flowing, null, null, canister,
                GZChemicals.EMPTY_GAS_CANISTER);
        ALL.add(fluid);
        return fluid;
    }

    /** Late-bound holders: the fluid properties, fluids, block and bucket all refer to each other. */
    private static final class Holders {
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        DeferredBlock<ChemLiquidBlock> block;
        DeferredItem<BucketItem> bucket;
    }
}
