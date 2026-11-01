package dev.groundburst.content.fluid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.chem.GBChemicals;
import dev.groundburst.content.fluid.ChemFluid.Hazard;
import dev.groundburst.content.fluid.ChemFluid.Look;
import dev.groundburst.content.part.GBParts;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBTab;
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
public final class GBFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, GroundBurst.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, GroundBurst.MODID);

    private static final List<ChemFluid> ALL = new ArrayList<>();

    public static final ChemFluid SULFURIC_ACID = liquid("sulfuric_acid", "Sulfuric Acid", Look.LIQUID, 0xFFD8CE4A, Hazard.ACID,
            1830, 1, 300, GBChemicals.SULFURIC_ACID_CANISTER, GBParts.EMPTY_CANISTER, MapColor.COLOR_YELLOW);
    public static final ChemFluid NITRIC_ACID = liquid("nitric_acid", "Nitric Acid", Look.LIQUID, 0xFFE8A050, Hazard.ACID,
            1510, 1, 300, GBChemicals.NITRIC_ACID_CANISTER, GBParts.EMPTY_CANISTER, MapColor.COLOR_ORANGE);
    public static final ChemFluid SYNTHETIC_FUEL = liquid("synthetic_fuel", "Synthetic Fuel", Look.LIQUID, 0xFF7A5420, Hazard.NONE,
            820, 2, 300, GBChemicals.SYNTHETIC_FUEL_CANISTER, GBParts.EMPTY_CANISTER, MapColor.COLOR_BROWN);
    public static final ChemFluid LIQUID_ROCKET_FUEL = liquid("liquid_rocket_fuel", "Liquid Rocket Fuel", Look.LIQUID, 0xFFD04838, Hazard.NONE,
            900, 1, 250, GBChemicals.LIQUID_ROCKET_FUEL_CANISTER, GBParts.EMPTY_CANISTER, MapColor.COLOR_RED);
    public static final ChemFluid THERMOBARIC_SLURRY = liquid("thermobaric_slurry", "Thermobaric Slurry", Look.THICK, 0xFF8C949C, Hazard.NONE,
            1400, 6, 300, GBChemicals.THERMOBARIC_SLURRY_CANISTER, GBParts.EMPTY_CANISTER, MapColor.COLOR_GRAY);
    public static final ChemFluid NAPALM = liquid("napalm", "Napalm Gel", Look.THICK, 0xFFE2742A, Hazard.FIRE,
            950, 8, 300, GBChemicals.NAPALM_CANISTER, GBParts.EMPTY_CANISTER, MapColor.COLOR_ORANGE);
    public static final ChemFluid LIQUID_OXYGEN = liquid("liquid_oxygen", "Liquid Oxygen", Look.LIQUID, 0xFF9CC4FF, Hazard.CRYO,
            1140, 1, 90, GBChemicals.LIQUID_OXYGEN_CANISTER, GBParts.EMPTY_CANISTER, MapColor.COLOR_LIGHT_BLUE);
    public static final ChemFluid LIQUID_NITROGEN = liquid("liquid_nitrogen", "Liquid Nitrogen", Look.LIQUID, 0xFFD8E8F2, Hazard.CRYO,
            810, 1, 77, GBChemicals.LIQUID_NITROGEN_CANISTER, GBParts.EMPTY_CANISTER, MapColor.SNOW);
    /** Reactor coolant that runs the core cooler than water and is used up five times slower (see FissionReactorBlockEntity). */
    public static final ChemFluid HEAVY_WATER = liquid("heavy_water", "Heavy Water", Look.LIQUID, 0xFF5A8CD8, Hazard.NONE,
            1107, 1, 300, GBChemicals.HEAVY_WATER_CANISTER, GBParts.EMPTY_CANISTER, MapColor.WATER);
    public static final ChemFluid DEUTERIUM = gas("deuterium", "Deuterium", 0xFFA4BCFF, Hazard.NONE, GBChemicals.DEUTERIUM_CANISTER);
    public static final ChemFluid TRITIUM = gas("tritium", "Tritium", 0xFFB8F0D0, Hazard.NONE, GBChemicals.TRITIUM_CANISTER);
    public static final ChemFluid URANIUM_HEXAFLUORIDE = gas("uranium_hexafluoride", "Uranium Hexafluoride", 0xFFC6D06A, Hazard.TOXIC,
            GBChemicals.UF6_CANISTER);
    /** Poison gas grenades' filling (and the toxin's base). */
    public static final ChemFluid CHLORINE = gas("chlorine", "Chlorine", 0xFFA8D430, Hazard.TOXIC, GBChemicals.CHLORINE_CANISTER);
    /** A made-up, glowing green poison for the toxin grenade (and, later, larger chemical weapons). */
    public static final ChemFluid TOXIN = liquid("toxin", "Toxin", Look.THICK, 0xFF7CE03A, Hazard.TOXIC,
            1300, 4, 300, GBChemicals.TOXIN_CANISTER, GBParts.EMPTY_CANISTER, MapColor.COLOR_LIGHT_GREEN);

    // The semiconductor chain (DESIGN.md §8.1).
    /** From coal, steam and oxygen (an oxygen-blown gasifier). */
    public static final ChemFluid HYDROGEN = gas("hydrogen", "Hydrogen", 0xFFEFD3EA, Hazard.NONE, GBChemicals.HYDROGEN_CANISTER);
    /** Hydrogen burnt in chlorine; fumes that eat at the lungs. */
    public static final ChemFluid HYDROGEN_CHLORIDE = gas("hydrogen_chloride", "Hydrogen Chloride", 0xFFCFE3B8, Hazard.TOXIC,
            GBChemicals.HYDROGEN_CHLORIDE_CANISTER);
    /** Silicon dissolved in hydrogen chloride: a clear, fuming liquid, distilled pure before it is turned back into silicon. */
    public static final ChemFluid TRICHLOROSILANE = liquid("trichlorosilane", "Trichlorosilane", Look.LIQUID, 0xFFBCCAD4, Hazard.ACID,
            1342, 1, 300, GBChemicals.TRICHLOROSILANE_CANISTER, GBParts.EMPTY_CANISTER, MapColor.COLOR_LIGHT_GRAY);
    /** Drawn from the air in the Cryogenic Condenser; the cover gas a crystal is grown under. */
    public static final ChemFluid ARGON = gas("argon", "Argon", 0xFFB9A6F0, Hazard.NONE, GBChemicals.ARGON_CANISTER);
    /** A light-sensitive resin the chips' patterns are printed in. */
    public static final ChemFluid PHOTORESIST = liquid("photoresist", "Photoresist", Look.LIQUID, 0xFFC0541E, Hazard.NONE,
            1050, 3, 300, GBChemicals.PHOTORESIST_CANISTER, GBParts.EMPTY_CANISTER, MapColor.COLOR_ORANGE);
    /** Fluorite and sulfuric acid; it etches glass and silicon, and the chips' patterns into them. */
    public static final ChemFluid HYDROFLUORIC_ACID = liquid("hydrofluoric_acid", "Hydrofluoric Acid", Look.LIQUID, 0xFFC8F0E4, Hazard.ACID,
            1150, 1, 300, GBChemicals.HYDROFLUORIC_ACID_CANISTER, GBParts.EMPTY_CANISTER, MapColor.ICE);

    /** Fluid pipes in three tiers, like the cables (see {@link FluidPipeTier}). */
    public static final GBBlocks.WithItem<FluidPipeBlock> COPPER_FLUID_PIPE = pipe(FluidPipeTier.COPPER);
    public static final GBBlocks.WithItem<FluidPipeBlock> FLUID_PIPE = pipe(FluidPipeTier.STEEL);
    public static final GBBlocks.WithItem<FluidPipeBlock> PRESSURIZED_FLUID_PIPE = pipe(FluidPipeTier.PRESSURIZED);

    public static List<GBBlocks.WithItem<FluidPipeBlock>> pipes() {
        return List.of(COPPER_FLUID_PIPE, FLUID_PIPE, PRESSURIZED_FLUID_PIPE);
    }

    private static GBBlocks.WithItem<FluidPipeBlock> pipe(FluidPipeTier tier) {
        return GBBlocks.register(tier.id(), tier.english(), GBTab.MACHINES, p -> new FluidPipeBlock(p, tier),
                p -> p.mapColor(tier == FluidPipeTier.COPPER ? MapColor.COLOR_ORANGE : MapColor.METAL).strength(2f, 6f)
                        .requiresCorrectToolForDrops().sound(SoundType.COPPER).noOcclusion().forceSolidOn());
    }

    /** Expandable fluid tank: blocks join into tanks up to 3x3x5 (see {@link FluidTankStructure}). */
    public static final GBBlocks.WithItem<FluidTankBlock> FLUID_TANK = GBBlocks.register("fluid_tank", "Fluid Tank", GBTab.MACHINES,
            FluidTankBlock::new, p -> p.mapColor(MapColor.METAL).strength(3f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .noOcclusion().isViewBlocking((s, l, pos, box) -> false).isSuffocating((s, l, pos) -> false));
    /** Creative-only endless source of whatever fluid is poured in (see {@link CreativeFluidTankBlockEntity}). */
    public static final GBBlocks.WithItem<CreativeFluidTankBlock> CREATIVE_FLUID_TANK = GBBlocks.register("creative_fluid_tank", "Creative Fluid Tank",
            GBTab.MACHINES, CreativeFluidTankBlock::new, p -> p.mapColor(MapColor.COLOR_PURPLE).strength(3f, 8f).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion().isViewBlocking((s, l, pos, box) -> false).isSuffocating((s, l, pos) -> false));

    private GBFluids() {}

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
        List<Item> canisters = new ArrayList<>(List.of(GBParts.EMPTY_CANISTER.get(), GBChemicals.EMPTY_GAS_CANISTER.get()));
        for (ChemFluid f : ALL) {
            canisters.add(f.canister().get());
        }
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM, (stack, access) -> new CanisterFluidHandler(access),
                canisters.toArray(Item[]::new));
        // Gas buckets are BucketItem subclasses, which NeoForge only gives the bucket capability for the exact class.
        for (ChemFluid f : ALL) {
            if (f.isGas()) {
                event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM,
                        (stack, access) -> new net.neoforged.neoforge.transfer.fluid.BucketResourceHandler(access), f.bucket().get());
            }
        }
        event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                (level, pos, state, be, side) -> side != null && FluidPipeBlock.mode(state, side) == FluidPipeBlock.Mode.DISABLED
                        ? null : FluidPipeNetwork.handler(level, pos, side),
                pipes().stream().map(p -> (net.minecraft.world.level.block.Block) p.block().get()).toArray(net.minecraft.world.level.block.Block[]::new));
    }

    private static ChemFluid liquid(String name, String english, Look look, int color, Hazard hazard, int density, int viscosityFactor,
            int temperature, Supplier<? extends Item> canister, Supplier<? extends Item> empty, MapColor mapColor) {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                .density(density).viscosity(1000 * viscosityFactor).temperature(temperature)
                .canSwim(viscosityFactor < 4).canDrown(true).canExtinguish(hazard != Hazard.FIRE).supportsBoating(false)
                .canConvertToSource(false).canHydrate(false).fallDistanceModifier(0.5f)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL).sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));
        GBLang.add("fluid_type." + GroundBurst.MODID + "." + name, english);
        Holders h = new Holders();
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(type, () -> h.source.get(), () -> h.flowing.get())
                .bucket(() -> h.bucket.get()).block(() -> h.block.get())
                .slopeFindDistance(viscosityFactor > 4 ? 2 : 4).levelDecreasePerBlock(viscosityFactor > 4 ? 2 : 1)
                .tickRate(5 * viscosityFactor).explosionResistance(100f);
        h.source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(props));
        h.flowing = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(props));
        h.block = GBBlocks.blockOnly(name, english, p -> new ChemLiquidBlock(h.source.get(), hazard, p),
                p -> p.mapColor(mapColor).replaceable().noCollision().strength(100f).pushReaction(PushReaction.POPPED)
                        .noLootTable().liquid().sound(SoundType.EMPTY).lightLevel(s -> hazard == Hazard.FIRE ? 3 : 0));
        h.bucket = GBItems.register(name + "_bucket", english + " Bucket", GBTab.RESOURCES, p -> new BucketItem(h.source.get(), p),
                p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
        ChemFluid fluid = new ChemFluid(name, look, color, hazard, type, h.source, h.flowing, h.block, null, h.bucket, canister, empty);
        ALL.add(fluid);
        return fluid;
    }

    /** Gases: lighter than air, so their buckets hang upside down and released gas rises (see {@link GasCloudBlock}). */
    private static ChemFluid gas(String name, String english, int color, Hazard hazard, Supplier<? extends Item> canister) {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                .density(-500).viscosity(100).temperature(300).canSwim(false).canDrown(false).supportsBoating(false)
                .canConvertToSource(false).canHydrate(false)));
        GBLang.add("fluid_type." + GroundBurst.MODID + "." + name, english);
        Holders h = new Holders();
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(type, () -> h.source.get(), () -> h.flowing.get())
                .bucket(() -> h.gasBucket.get());
        h.source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(props));
        h.flowing = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(props));
        DeferredBlock<GasCloudBlock> cloud = GBBlocks.blockOnly(name, english, p -> new GasCloudBlock(p, hazard),
                p -> p.mapColor(MapColor.NONE).replaceable().noCollision().noOcclusion().strength(0f).pushReaction(PushReaction.POPPED)
                        .noLootTable().sound(SoundType.EMPTY).isViewBlocking((s, l, pos, box) -> false).isSuffocating((s, l, pos) -> false));
        h.gasBucket = GBItems.register(name + "_bucket", english + " Bucket", GBTab.RESOURCES,
                p -> new GasBucketItem(h.source.get(), cloud, p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
        ChemFluid fluid = new ChemFluid(name, Look.GAS, color, hazard, type, h.source, h.flowing, null, cloud, h.gasBucket, canister,
                GBChemicals.EMPTY_GAS_CANISTER);
        ALL.add(fluid);
        return fluid;
    }

    /** Late-bound holders: the fluid properties, fluids, block and bucket all refer to each other. */
    private static final class Holders {
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        DeferredBlock<ChemLiquidBlock> block;
        DeferredItem<BucketItem> bucket;
        DeferredItem<GasBucketItem> gasBucket;
    }
}
