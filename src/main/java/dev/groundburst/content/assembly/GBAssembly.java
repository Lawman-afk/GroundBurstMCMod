package dev.groundburst.content.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.GeoBlockItem;
import dev.groundburst.content.machine.WideMachineBlock;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.charge.ChargeBlock;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBTab;
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
public final class GBAssembly {
    // --- payload cores (one per warhead, with its size class) ---
    public static final List<DeferredItem<ComponentItems.PayloadCore>> CORES = new ArrayList<>();
    public static final DeferredItem<ComponentItems.PayloadCore> HE_CHARGE = core("he_charge", "HE Charge", () -> GBOrdnance.HE_WARHEAD.block().get(), SizeClass.S);
    public static final DeferredItem<ComponentItems.PayloadCore> HEAVY_HE_CHARGE = core("heavy_he_charge", "Heavy HE Charge",
            () -> GBOrdnance.HEAVY_WARHEAD.block().get(), SizeClass.M);
    // The Thermobaric, Penetrator and EMP payloads are class L (DESIGN.md §10.4): real fuel-air, penetrating and
    // flux-compression warheads are among the heaviest, and the Heavy Missile Assembler has a family to build.
    public static final DeferredItem<ComponentItems.PayloadCore> THERMOBARIC_CHARGE = core("thermobaric_charge", "Thermobaric Charge",
            () -> GBOrdnance.THERMOBARIC_WARHEAD.block().get(), SizeClass.L);
    public static final DeferredItem<ComponentItems.PayloadCore> CLUSTER_DISPENSER = core("cluster_dispenser", "Cluster Dispenser",
            () -> GBOrdnance.CLUSTER_WARHEAD.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> PENETRATOR_CHARGE = core("penetrator_charge", "Penetrator Charge",
            () -> GBOrdnance.BUNKER_BUSTER.block().get(), SizeClass.L);
    public static final DeferredItem<ComponentItems.PayloadCore> INCENDIARY_CHARGE = core("incendiary_charge", "Incendiary Charge",
            () -> GBOrdnance.NAPALM_WARHEAD.block().get(), SizeClass.S);
    public static final DeferredItem<ComponentItems.PayloadCore> MASSIVE_ORDNANCE_CHARGE = core("massive_ordnance_charge", "Massive Ordnance Charge",
            () -> GBOrdnance.MASSIVE_WARHEAD.block().get(), SizeClass.L);
    // Nuclear cores are class XL (Strategic): they go in the Strategic Warhead Casing and ride the Strategic missile.
    public static final DeferredItem<ComponentItems.PayloadCore> TACTICAL_FISSION_CORE = core("tactical_fission_core", "Tactical Fission Core",
            () -> GBOrdnance.TACTICAL_NUCLEAR_WARHEAD.block().get(), SizeClass.XL);
    public static final DeferredItem<ComponentItems.PayloadCore> FISSION_CORE = core("fission_core", "Fission Core",
            () -> GBOrdnance.FISSION_WARHEAD.block().get(), SizeClass.XL);
    public static final DeferredItem<ComponentItems.PayloadCore> BOOSTED_FISSION_CORE = core("boosted_fission_core", "Boosted Fission Core",
            () -> GBOrdnance.BOOSTED_FISSION_WARHEAD.block().get(), SizeClass.XL);
    public static final DeferredItem<ComponentItems.PayloadCore> NEUTRON_CORE = core("neutron_core", "Neutron Core",
            () -> GBOrdnance.NEUTRON_WARHEAD.block().get(), SizeClass.XL);
    /** Radiological, not nuclear: a class S core for an ordinary casing and missile. */
    public static final DeferredItem<ComponentItems.PayloadCore> DIRTY_PAYLOAD = core("dirty_payload", "Dirty Payload",
            () -> GBOrdnance.DIRTY_WARHEAD.block().get(), SizeClass.S);
    public static final DeferredItem<ComponentItems.PayloadCore> GAS_DISPENSER = core("gas_dispenser", "Gas Canister Dispenser",
            () -> GBOrdnance.GAS_WARHEAD.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> SMOKE_DISPENSER = core("smoke_dispenser", "Smoke Canister Dispenser",
            () -> GBOrdnance.SMOKE_WARHEAD.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> CRYO_DISPENSER = core("cryo_dispenser", "Cryo Canister Dispenser",
            () -> GBOrdnance.CRYO_WARHEAD.block().get(), SizeClass.M);
    public static final DeferredItem<ComponentItems.PayloadCore> EMP_DISPENSER = core("emp_dispenser", "EMP Canister Dispenser",
            () -> GBOrdnance.EMP_WARHEAD.block().get(), SizeClass.L);
    public static final DeferredItem<ComponentItems.PayloadCore> TOXIC_WASTE_TANK = core("toxic_waste_tank", "Toxic Waste Tank",
            () -> GBOrdnance.TOXIC_WASTE_WARHEAD.block().get(), SizeClass.S);
    public static final DeferredItem<ComponentItems.PayloadCore> CHEMICAL_AGENT_CANISTER = core("chemical_agent_canister", "Chemical Agent Canister",
            () -> GBOrdnance.CHEMICAL_WARHEAD.block().get(), SizeClass.S);

    // --- casings (one per size class, in SizeClass order) ---
    public static final List<DeferredItem<ComponentItems.Casing>> CASINGS = new ArrayList<>();
    public static final DeferredItem<ComponentItems.Casing> LIGHT_CASING = casing("light_warhead_casing", "Light Warhead Casing", SizeClass.S);
    public static final DeferredItem<ComponentItems.Casing> MEDIUM_CASING = casing("medium_warhead_casing", "Medium Warhead Casing", SizeClass.M);
    public static final DeferredItem<ComponentItems.Casing> HEAVY_CASING = casing("heavy_warhead_casing", "Heavy Warhead Casing", SizeClass.L);
    /** Phase 8: the lead-lined casing every nuclear core goes in (class XL). */
    public static final DeferredItem<ComponentItems.Casing> STRATEGIC_CASING = casing("strategic_warhead_casing", "Strategic Warhead Casing", SizeClass.XL);

    // --- fuzes (in Fuze order) ---
    public static final List<DeferredItem<ComponentItems.FuzeItem>> FUZES = new ArrayList<>();
    public static final DeferredItem<ComponentItems.FuzeItem> IMPACT_FUZE = fuze("impact_fuze", "Impact Fuze", Fuze.IMPACT);
    public static final DeferredItem<ComponentItems.FuzeItem> BAROMETRIC_FUZE = fuze("barometric_fuze", "Barometric Fuze", Fuze.BAROMETRIC);

    // --- missile parts (slice 7b), in size-class / enum order ---
    public static final List<DeferredItem<MissileParts.Body>> BODIES = new ArrayList<>();
    public static final DeferredItem<MissileParts.Body> TACTICAL_BODY = body("tactical_missile_body", "Tactical Missile Body", SizeClass.S);
    public static final DeferredItem<MissileParts.Body> MEDIUM_BODY = body("medium_missile_body", "Medium Missile Body", SizeClass.M);
    public static final DeferredItem<MissileParts.Body> HEAVY_BODY = body("heavy_missile_body", "Heavy Missile Body", SizeClass.L);
    /** Phase 8: the long two-stage body that carries nuclear warheads (and nothing else). */
    public static final DeferredItem<MissileParts.Body> STRATEGIC_BODY = body("strategic_missile_body", "Strategic Missile Body", SizeClass.XL);
    public static final List<DeferredItem<MissileParts.MotorItem>> MOTORS = new ArrayList<>();
    public static final DeferredItem<MissileParts.MotorItem> SRM_I = motor("Solid Rocket Motor I", MissileParts.Motor.SRM_I);
    public static final DeferredItem<MissileParts.MotorItem> SRM_II = motor("Solid Rocket Motor II", MissileParts.Motor.SRM_II);
    public static final DeferredItem<MissileParts.MotorItem> LIQUID_FUEL_STAGE = motor("Liquid-fuel Stage", MissileParts.Motor.LIQUID);
    /** Slice 9c (DESIGN.md §17.11): a turbojet and its launch booster; the missile it drives is a cruise missile. */
    public static final DeferredItem<MissileParts.MotorItem> CRUISE_ENGINE = motor("Cruise Engine", MissileParts.Motor.CRUISE);
    public static final DeferredItem<MissileParts.GuidanceItem> INERTIAL_GUIDANCE = GBItems.register("inertial_guidance_unit",
            "Inertial Guidance Unit", GBTab.MISSILES, p -> new MissileParts.GuidanceItem(p, MissileParts.Guidance.INERTIAL));
    public static final DeferredItem<MissileParts.GuidanceItem> GPS_GUIDANCE = GBItems.register("gps_guidance_unit",
            "GPS Guidance Unit", GBTab.MISSILES, p -> new MissileParts.GuidanceItem(p, MissileParts.Guidance.GPS));
    public static final DeferredItem<MissileParts.HardenedModule> HARDENED_ELECTRONICS = GBItems.register("hardened_electronics",
            "Hardened Electronics", GBTab.MISSILES, MissileParts.HardenedModule::new);
    /** An assembled missile (its parts in its component): what a Launch Silo launches. */
    public static final DeferredItem<MissileItem> MISSILE = GBItems.register("missile", "Missile", GBTab.MISSILES, MissileItem::new);

    // --- the bench ---
    /** Two blocks wide: the bench top and hoist over both, the side half to the right of its front. */
    public static final WithItem<WideMachineBlock> ASSEMBLY_BENCH = GBBlocks.registerWithItem("assembly_bench", "Ordnance Assembly Bench",
            GBTab.ORDNANCE, p -> new WideMachineBlock(p, GBAssembly.BENCH_ENTITY, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 15, 16)), Shapes.or(Block.box(0, 0, 0, 16, 15, 16))),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .pushReaction(PushReaction.IMMOVEABLE),
            (block, props) -> new GeoBlockItem(block, props, GroundBurst.id("assembly_bench"), 0.5f));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyBenchBlockEntity>> BENCH_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("assembly_bench", () -> new BlockEntityType<>(AssemblyBenchBlockEntity::new,
                    ASSEMBLY_BENCH.block().get()));

    private GBAssembly() {}

    /** Loads the registrations (and the language keys of the fuze and size-class names). */
    public static void init() {
        Fuze.values();
        SizeClass.values();
        WarheadAssembly.Problem.values();
        MissileAssembly.Problem.values();
        MissileParts.Motor.values();
        MissileParts.Guidance.values();
    }

    private static DeferredItem<ComponentItems.PayloadCore> core(String id, String english, Supplier<? extends ChargeBlock> warhead, SizeClass size) {
        DeferredItem<ComponentItems.PayloadCore> item = GBItems.register(id, english, GBTab.ORDNANCE, p -> new ComponentItems.PayloadCore(p, warhead, size));
        CORES.add(item);
        return item;
    }

    private static DeferredItem<MissileParts.Body> body(String id, String english, SizeClass size) {
        DeferredItem<MissileParts.Body> item = GBItems.register(id, english, GBTab.MISSILES, p -> new MissileParts.Body(p, size));
        BODIES.add(item);
        return item;
    }

    private static DeferredItem<MissileParts.MotorItem> motor(String english, MissileParts.Motor motor) {
        DeferredItem<MissileParts.MotorItem> item = GBItems.register(motor.id, english, GBTab.MISSILES, p -> new MissileParts.MotorItem(p, motor));
        MOTORS.add(item);
        return item;
    }

    private static DeferredItem<ComponentItems.Casing> casing(String id, String english, SizeClass size) {
        DeferredItem<ComponentItems.Casing> item = GBItems.register(id, english, GBTab.ORDNANCE, p -> new ComponentItems.Casing(p, size));
        CASINGS.add(item);
        return item;
    }

    private static DeferredItem<ComponentItems.FuzeItem> fuze(String id, String english, Fuze fuze) {
        DeferredItem<ComponentItems.FuzeItem> item = GBItems.register(id, english, GBTab.ORDNANCE, p -> new ComponentItems.FuzeItem(p, fuze));
        FUZES.add(item);
        return item;
    }
}
