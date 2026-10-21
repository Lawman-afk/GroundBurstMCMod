package dev.groundzero.registry;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.ArcFurnaceBlockEntity;
import dev.groundzero.content.machine.ChemicalPlantBlockEntity;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.PortBlockEntity;
import dev.groundzero.content.machine.CryogenicCondenserBlockEntity;
import dev.groundzero.content.machine.ElectrolyticSeparatorBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.IndustrialPressBlockEntity;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.OreCrusherBlockEntity;
import dev.groundzero.content.machine.TallMachineBlock;
import dev.groundzero.content.power.CombustionGeneratorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity types, plus the capabilities they expose. */
public final class GZBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, GroundZero.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OreCrusherBlockEntity>> ORE_CRUSHER =
            BLOCK_ENTITIES.register("ore_crusher", () -> new BlockEntityType<>(OreCrusherBlockEntity::new, GZMachines.ORE_CRUSHER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CombustionGeneratorBlockEntity>> COMBUSTION_GENERATOR =
            BLOCK_ENTITIES.register("combustion_generator", () -> new BlockEntityType<>(CombustionGeneratorBlockEntity::new, GZMachines.COMBUSTION_GENERATOR.block().get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcFurnaceBlockEntity>> ARC_FURNACE =
            BLOCK_ENTITIES.register("arc_furnace", () -> new BlockEntityType<>(ArcFurnaceBlockEntity::new, GZMachines.ARC_FURNACE.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IndustrialPressBlockEntity>> INDUSTRIAL_PRESS =
            BLOCK_ENTITIES.register("industrial_press", () -> new BlockEntityType<>(IndustrialPressBlockEntity::new, GZMachines.INDUSTRIAL_PRESS.block().get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectrolyticSeparatorBlockEntity>> ELECTROLYTIC_SEPARATOR =
            BLOCK_ENTITIES.register("electrolytic_separator", () -> new BlockEntityType<>(ElectrolyticSeparatorBlockEntity::new, GZMachines.ELECTROLYTIC_SEPARATOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.machine.WaterPumpBlockEntity>> WATER_PUMP =
            BLOCK_ENTITIES.register("water_pump", () -> new BlockEntityType<>(dev.groundzero.content.machine.WaterPumpBlockEntity::new, GZMachines.WATER_PUMP.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.fluid.FluidTankBlockEntity>> FLUID_TANK =
            BLOCK_ENTITIES.register("fluid_tank", () -> new BlockEntityType<>(dev.groundzero.content.fluid.FluidTankBlockEntity::new,
                    dev.groundzero.content.fluid.GZFluids.FLUID_TANK.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.fluid.CreativeFluidTankBlockEntity>> CREATIVE_FLUID_TANK =
            BLOCK_ENTITIES.register("creative_fluid_tank", () -> new BlockEntityType<>(dev.groundzero.content.fluid.CreativeFluidTankBlockEntity::new,
                    dev.groundzero.content.fluid.GZFluids.CREATIVE_FLUID_TANK.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CryogenicCondenserBlockEntity>> CRYOGENIC_CONDENSER =
            BLOCK_ENTITIES.register("cryogenic_condenser", () -> new BlockEntityType<>(CryogenicCondenserBlockEntity::new, GZMachines.CRYOGENIC_CONDENSER.block().get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.machine.CircuitFabricatorBlockEntity>> CIRCUIT_FABRICATOR =
            BLOCK_ENTITIES.register("circuit_fabricator", () -> new BlockEntityType<>(dev.groundzero.content.machine.CircuitFabricatorBlockEntity::new,
                    GZMachines.CIRCUIT_FABRICATOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.machine.GasCentrifugeBlockEntity>> GAS_CENTRIFUGE =
            BLOCK_ENTITIES.register("gas_centrifuge", () -> new BlockEntityType<>(dev.groundzero.content.machine.GasCentrifugeBlockEntity::new,
                    GZMachines.GAS_CENTRIFUGE.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.machine.FusionFuelSynthesizerBlockEntity>> FUSION_FUEL_SYNTHESIZER =
            BLOCK_ENTITIES.register("fusion_fuel_synthesizer", () -> new BlockEntityType<>(dev.groundzero.content.machine.FusionFuelSynthesizerBlockEntity::new,
                    GZMachines.FUSION_FUEL_SYNTHESIZER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.machine.CanningMachineBlockEntity>> CANNING_MACHINE =
            BLOCK_ENTITIES.register("canning_machine", () -> new BlockEntityType<>(dev.groundzero.content.machine.CanningMachineBlockEntity::new,
                    GZMachines.CANNING_MACHINE.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.machine.ExoticMatterFabricatorBlockEntity>> EXOTIC_MATTER_FABRICATOR =
            BLOCK_ENTITIES.register("exotic_matter_fabricator", () -> new BlockEntityType<>(dev.groundzero.content.machine.ExoticMatterFabricatorBlockEntity::new,
                    GZMachines.EXOTIC_MATTER_FABRICATOR.block().get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.power.DieselGeneratorBlockEntity>> DIESEL_GENERATOR =
            BLOCK_ENTITIES.register("diesel_generator", () -> new BlockEntityType<>(dev.groundzero.content.power.DieselGeneratorBlockEntity::new,
                    GZMachines.DIESEL_GENERATOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.power.RtgBlockEntity>> RTG =
            BLOCK_ENTITIES.register("rtg", () -> new BlockEntityType<>(dev.groundzero.content.power.RtgBlockEntity::new, GZMachines.RTG.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.power.BatteryChargerBlockEntity>> BATTERY_CHARGER =
            BLOCK_ENTITIES.register("battery_charger", () -> new BlockEntityType<>(dev.groundzero.content.power.BatteryChargerBlockEntity::new,
                    GZMachines.BATTERY_CHARGER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.machine.DecontaminationShowerBlockEntity>> DECONTAMINATION_SHOWER =
            BLOCK_ENTITIES.register("decontamination_shower", () -> new BlockEntityType<>(dev.groundzero.content.machine.DecontaminationShowerBlockEntity::new,
                    GZMachines.DECONTAMINATION_SHOWER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.power.CapacitorBankBlockEntity>> CAPACITOR_BANK = bank(0);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.power.CapacitorBankBlockEntity>> ADVANCED_CAPACITOR_BANK = bank(1);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.power.CapacitorBankBlockEntity>> SUPERCONDUCTING_CAPACITOR_BANK = bank(2);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.power.CapacitorBankBlockEntity>> CREATIVE_CAPACITOR_BANK = bank(3);

    /** One block entity type per bank tier, so each tier gets its own GeckoLib model. */
    @SuppressWarnings("unchecked")
    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.power.CapacitorBankBlockEntity>> bank(int ordinal) {
        var tier = dev.groundzero.content.power.CapacitorBankBlockEntity.Tier.values()[ordinal];
        DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.power.CapacitorBankBlockEntity>>[] self = new DeferredHolder[1];
        self[0] = BLOCK_ENTITIES.register(tier.id(), () -> new BlockEntityType<>(
                (pos, state) -> new dev.groundzero.content.power.CapacitorBankBlockEntity(self[0].get(), tier, pos, state),
                GZMachines.CAPACITOR_BANKS.get(ordinal).block().get()));
        return self[0];
    }

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.power.CapacitorBankBlockEntity>> capacitorBank(
            dev.groundzero.content.power.CapacitorBankBlockEntity.Tier tier) {
        return switch (tier) {
            case BASIC -> CAPACITOR_BANK;
            case ADVANCED -> ADVANCED_CAPACITOR_BANK;
            case SUPERCONDUCTING -> SUPERCONDUCTING_CAPACITOR_BANK;
            case CREATIVE -> CREATIVE_CAPACITOR_BANK;
        };
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChemicalPlantBlockEntity>> CHEMICAL_PLANT =
            BLOCK_ENTITIES.register("chemical_plant", () -> new BlockEntityType<>(ChemicalPlantBlockEntity::new, GZMachines.CHEMICAL_PLANT.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.machine.FusionReactorBlockEntity>> FUSION_REACTOR =
            BLOCK_ENTITIES.register("fusion_reactor", () -> new BlockEntityType<>(dev.groundzero.content.machine.FusionReactorBlockEntity::new,
                    GZMachines.FUSION_REACTOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.machine.SingularityReactorBlockEntity>> SINGULARITY_REACTOR =
            BLOCK_ENTITIES.register("singularity_reactor", () -> new BlockEntityType<>(dev.groundzero.content.machine.SingularityReactorBlockEntity::new,
                    GZMachines.SINGULARITY_REACTOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.machine.FissionReactorBlockEntity>> FISSION_REACTOR =
            BLOCK_ENTITIES.register("fission_reactor", () -> new BlockEntityType<>(dev.groundzero.content.machine.FissionReactorBlockEntity::new,
                    GZMachines.FISSION_REACTOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundzero.content.machine.ParticleAcceleratorBlockEntity>> PARTICLE_ACCELERATOR =
            BLOCK_ENTITIES.register("particle_accelerator", () -> new BlockEntityType<>(dev.groundzero.content.machine.ParticleAcceleratorBlockEntity::new,
                    GZMachines.PARTICLE_ACCELERATOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortBlockEntity>> ITEM_PORT =
            BLOCK_ENTITIES.register("item_port", () -> new BlockEntityType<>(PortBlockEntity::new, GZMultiblocks.ITEM_PORT.block().get(),
                    GZMultiblocks.ENERGY_PORT.block().get(), GZMultiblocks.FLUID_PORT.block().get()));

    private GZBlockEntities() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        machine(event, ORE_CRUSHER.get());
        machine(event, COMBUSTION_GENERATOR.get());
        machine(event, ELECTROLYTIC_SEPARATOR.get());
        machine(event, CHEMICAL_PLANT.get());
        machine(event, WATER_PUMP.get());
        machine(event, CIRCUIT_FABRICATOR.get());
        machine(event, FUSION_FUEL_SYNTHESIZER.get());
        machine(event, CANNING_MACHINE.get());
        machine(event, DIESEL_GENERATOR.get());
        machine(event, RTG.get());
        machine(event, BATTERY_CHARGER.get());
        machine(event, CAPACITOR_BANK.get());
        machine(event, ADVANCED_CAPACITOR_BANK.get());
        machine(event, SUPERCONDUCTING_CAPACITOR_BANK.get());
        machine(event, CREATIVE_CAPACITOR_BANK.get());
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler(access,
                GZDataComponents.ENERGY.get(), dev.groundzero.content.power.BatteryItem.CAPACITY, dev.groundzero.content.power.BatteryItem.TRANSFER),
                dev.groundzero.content.part.GZParts.BATTERY.get());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_TANK.get(), (be, side) -> be.handler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, CREATIVE_FLUID_TANK.get(), (be, side) -> be.handler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ITEM_PORT.get(), PortBlockEntity::items);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ITEM_PORT.get(), PortBlockEntity::fluids);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ITEM_PORT.get(), PortBlockEntity::energy);
        machine(event, FISSION_REACTOR.get());
        machine(event, FUSION_REACTOR.get());
        machine(event, SINGULARITY_REACTOR.get());
        machine(event, PARTICLE_ACCELERATOR.get());
        machine(event, dev.groundzero.content.launch.GZLaunch.SILO_ENTITY.get());
        machine(event, dev.groundzero.content.launch.GZLaunch.COMPUTER_ENTITY.get());
        machine(event, dev.groundzero.content.launch.GZLaunch.RADAR_ENTITY.get());
        tallMachine(event, GZMachines.ARC_FURNACE.block().get());
        tallMachine(event, GZMachines.CRYOGENIC_CONDENSER.block().get());
        tallMachine(event, GZMachines.INDUSTRIAL_PRESS.block().get());
        tallMachine(event, GZMachines.GAS_CENTRIFUGE.block().get());
        tallMachine(event, GZMachines.EXOTIC_MATTER_FABRICATOR.block().get());
        tallMachine(event, GZMachines.DECONTAMINATION_SHOWER.block().get());
        GZMachines.registerCableCapabilities(event);
        dev.groundzero.content.fluid.GZFluids.registerCapabilities(event);
        dev.groundzero.content.pipe.GZPipes.registerCapabilities(event);
    }

    /** Both halves of a tall machine expose the lower half's block entity (cables and pipes may attach to either). */
    private static void tallMachine(RegisterCapabilitiesEvent event, TallMachineBlock block) {
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, be, side) ->
                level.getBlockEntity(TallMachineBlock.base(pos, state)) instanceof MachineBlockEntity m ? m.energyFor(side) : null, block);
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, be, side) ->
                level.getBlockEntity(TallMachineBlock.base(pos, state)) instanceof MachineBlockEntity m ? m.itemsFor(side) : null, block);
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, be, side) ->
                level.getBlockEntity(TallMachineBlock.base(pos, state)) instanceof MachineBlockEntity m ? m.fluidsFor(side) : null, block);
    }

    private static <T extends MachineBlockEntity> void machine(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, type, MachineBlockEntity::energyFor);
        event.registerBlockEntity(Capabilities.Item.BLOCK, type, MachineBlockEntity::itemsFor);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, type, MachineBlockEntity::fluidsFor);
    }
}
