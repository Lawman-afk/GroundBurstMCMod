package dev.groundzero.content.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.GeoBlockItem;
import dev.groundzero.content.machine.WideMachineBlock;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZBlocks;
import dev.groundzero.registry.GZBlocks.WithItem;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Components and assembly (DESIGN.md §9-10, Phase 7): the payload cores, warhead casings and fuzes, and the Ordnance
 * Assembly Bench that puts them together.
 */
public final class GZAssembly {
    // --- payload cores (one per warhead, with its size class) ---
    public static final List<DeferredItem<ComponentItems.PayloadCore>> CORES = new ArrayList<>();
    public static final DeferredItem<ComponentItems.PayloadCore> HE_CHARGE = core("he_charge", "HE Charge", () -> GZOrdnance.HE_WARHEAD.block().get(), SizeClass.S);
    public static final DeferredItem<ComponentItems.PayloadCore> HEAVY_HE_CHARGE = core("heavy_he_charge", "Heavy HE Charge",
            () -> GZOrdnance.HEAVY_WARHEAD.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> THERMOBARIC_CHARGE = core("thermobaric_charge", "Thermobaric Charge",
            () -> GZOrdnance.THERMOBARIC_WARHEAD.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> CLUSTER_DISPENSER = core("cluster_dispenser", "Cluster Dispenser",
            () -> GZOrdnance.CLUSTER_WARHEAD.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> PENETRATOR_CHARGE = core("penetrator_charge", "Penetrator Charge",
            () -> GZOrdnance.BUNKER_BUSTER.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> INCENDIARY_CHARGE = core("incendiary_charge", "Incendiary Charge",
            () -> GZOrdnance.NAPALM_WARHEAD.block().get(), SizeClass.S);
    public static final DeferredItem<ComponentItems.PayloadCore> MASSIVE_ORDNANCE_CHARGE = core("massive_ordnance_charge", "Massive Ordnance Charge",
            () -> GZOrdnance.MASSIVE_WARHEAD.block().get(), SizeClass.L);
    public static final DeferredItem<ComponentItems.PayloadCore> GAS_DISPENSER = core("gas_dispenser", "Gas Canister Dispenser",
            () -> GZOrdnance.GAS_WARHEAD.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> SMOKE_DISPENSER = core("smoke_dispenser", "Smoke Canister Dispenser",
            () -> GZOrdnance.SMOKE_WARHEAD.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> CRYO_DISPENSER = core("cryo_dispenser", "Cryo Canister Dispenser",
            () -> GZOrdnance.CRYO_WARHEAD.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> EMP_DISPENSER = core("emp_dispenser", "EMP Canister Dispenser",
            () -> GZOrdnance.EMP_WARHEAD.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> TOXIC_WASTE_TANK = core("toxic_waste_tank", "Toxic Waste Tank",
            () -> GZOrdnance.TOXIC_WASTE_WARHEAD.block().get(), SizeClass.S);
    public static final DeferredItem<ComponentItems.PayloadCore> CHEMICAL_AGENT_CANISTER = core("chemical_agent_canister", "Chemical Agent Canister",
            () -> GZOrdnance.CHEMICAL_WARHEAD.block().get(), SizeClass.S);

    // --- casings (one per size class, in SizeClass order) ---
    public static final List<DeferredItem<ComponentItems.Casing>> CASINGS = new ArrayList<>();
    public static final DeferredItem<ComponentItems.Casing> LIGHT_CASING = casing("light_warhead_casing", "Light Warhead Casing", SizeClass.S);
    public static final DeferredItem<ComponentItems.Casing> MEDIUM_CASING = casing("medium_warhead_casing", "Medium Warhead Casing", SizeClass.M);
    public static final DeferredItem<ComponentItems.Casing> HEAVY_CASING = casing("heavy_warhead_casing", "Heavy Warhead Casing", SizeClass.L);

    // --- fuzes (in Fuze order) ---
    public static final List<DeferredItem<ComponentItems.FuzeItem>> FUZES = new ArrayList<>();
    public static final DeferredItem<ComponentItems.FuzeItem> IMPACT_FUZE = fuze("impact_fuze", "Impact Fuze", Fuze.IMPACT);
    public static final DeferredItem<ComponentItems.FuzeItem> TIMED_FUZE = fuze("timed_fuze", "Timed Fuze", Fuze.TIMED);
    public static final DeferredItem<ComponentItems.FuzeItem> PROXIMITY_FUZE = fuze("proximity_fuze", "Proximity Fuze", Fuze.PROXIMITY);
    public static final DeferredItem<ComponentItems.FuzeItem> REMOTE_FUZE = fuze("remote_fuze", "Remote Fuze", Fuze.REMOTE);
    public static final DeferredItem<ComponentItems.FuzeItem> BAROMETRIC_FUZE = fuze("barometric_fuze", "Barometric Fuze", Fuze.BAROMETRIC);

    // --- the bench ---
    /** Two blocks wide: the bench top and hoist over both, the side half to the right of its front. */
    public static final WithItem<WideMachineBlock> ASSEMBLY_BENCH = GZBlocks.registerWithItem("assembly_bench", "Ordnance Assembly Bench",
            GZTab.ORDNANCE, p -> new WideMachineBlock(p, GZAssembly.BENCH_ENTITY, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 15, 16)), Shapes.or(Block.box(0, 0, 0, 16, 15, 16))),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .pushReaction(PushReaction.IMMOVEABLE),
            (block, props) -> new GeoBlockItem(block, props, GroundZero.id("assembly_bench"), 0.5f));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyBenchBlockEntity>> BENCH_ENTITY =
            GZBlockEntities.BLOCK_ENTITIES.register("assembly_bench", () -> new BlockEntityType<>(AssemblyBenchBlockEntity::new,
                    ASSEMBLY_BENCH.block().get()));

    private GZAssembly() {}

    /** Loads the registrations (and the language keys of the fuze and size-class names). */
    public static void init() {
        Fuze.values();
        SizeClass.values();
        WarheadAssembly.Problem.values();
    }

    private static DeferredItem<ComponentItems.PayloadCore> core(String id, String english, Supplier<? extends ChargeBlock> warhead, SizeClass size) {
        DeferredItem<ComponentItems.PayloadCore> item = GZItems.register(id, english, GZTab.ORDNANCE, p -> new ComponentItems.PayloadCore(p, warhead, size));
        CORES.add(item);
        return item;
    }

    private static DeferredItem<ComponentItems.Casing> casing(String id, String english, SizeClass size) {
        DeferredItem<ComponentItems.Casing> item = GZItems.register(id, english, GZTab.ORDNANCE, p -> new ComponentItems.Casing(p, size));
        CASINGS.add(item);
        return item;
    }

    private static DeferredItem<ComponentItems.FuzeItem> fuze(String id, String english, Fuze fuze) {
        DeferredItem<ComponentItems.FuzeItem> item = GZItems.register(id, english, GZTab.ORDNANCE, p -> new ComponentItems.FuzeItem(p, fuze));
        FUZES.add(item);
        return item;
    }
}
