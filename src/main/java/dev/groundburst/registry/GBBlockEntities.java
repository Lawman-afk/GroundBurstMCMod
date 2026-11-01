package dev.groundburst.registry;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.ArcFurnaceBlockEntity;
import dev.groundburst.content.machine.ChemicalPlantBlockEntity;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.PortBlockEntity;
import dev.groundburst.content.machine.CryogenicCondenserBlockEntity;
import dev.groundburst.content.machine.ElectrolyticSeparatorBlockEntity;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.IndustrialPressBlockEntity;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.OreCrusherBlockEntity;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.content.power.CombustionGeneratorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity types, plus the capabilities they expose. */
public final class GBBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, GroundBurst.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OreCrusherBlockEntity>> ORE_CRUSHER =
            BLOCK_ENTITIES.register("ore_crusher", () -> new BlockEntityType<>(OreCrusherBlockEntity::new, GBMachines.ORE_CRUSHER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CombustionGeneratorBlockEntity>> COMBUSTION_GENERATOR =
            BLOCK_ENTITIES.register("combustion_generator", () -> new BlockEntityType<>(CombustionGeneratorBlockEntity::new, GBMachines.COMBUSTION_GENERATOR.block().get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcFurnaceBlockEntity>> ARC_FURNACE =
            BLOCK_ENTITIES.register("arc_furnace", () -> new BlockEntityType<>(ArcFurnaceBlockEntity::new, GBMachines.ARC_FURNACE.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IndustrialPressBlockEntity>> INDUSTRIAL_PRESS =
            BLOCK_ENTITIES.register("industrial_press", () -> new BlockEntityType<>(IndustrialPressBlockEntity::new, GBMachines.INDUSTRIAL_PRESS.block().get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectrolyticSeparatorBlockEntity>> ELECTROLYTIC_SEPARATOR =
            BLOCK_ENTITIES.register("electrolytic_separator", () -> new BlockEntityType<>(ElectrolyticSeparatorBlockEntity::new, GBMachines.ELECTROLYTIC_SEPARATOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.WaterPumpBlockEntity>> WATER_PUMP =
            BLOCK_ENTITIES.register("water_pump", () -> new BlockEntityType<>(dev.groundburst.content.machine.WaterPumpBlockEntity::new, GBMachines.WATER_PUMP.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.fluid.FluidTankBlockEntity>> FLUID_TANK =
            BLOCK_ENTITIES.register("fluid_tank", () -> new BlockEntityType<>(dev.groundburst.content.fluid.FluidTankBlockEntity::new,
                    dev.groundburst.content.fluid.GBFluids.FLUID_TANK.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.fluid.CreativeFluidTankBlockEntity>> CREATIVE_FLUID_TANK =
            BLOCK_ENTITIES.register("creative_fluid_tank", () -> new BlockEntityType<>(dev.groundburst.content.fluid.CreativeFluidTankBlockEntity::new,
                    dev.groundburst.content.fluid.GBFluids.CREATIVE_FLUID_TANK.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CryogenicCondenserBlockEntity>> CRYOGENIC_CONDENSER =
            BLOCK_ENTITIES.register("cryogenic_condenser", () -> new BlockEntityType<>(CryogenicCondenserBlockEntity::new, GBMachines.CRYOGENIC_CONDENSER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.CrystalPullerBlockEntity>> CRYSTAL_PULLER =
            BLOCK_ENTITIES.register("crystal_puller", () -> new BlockEntityType<>(dev.groundburst.content.machine.CrystalPullerBlockEntity::new,
                    GBMachines.CRYSTAL_PULLER.block().get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.CircuitFabricatorBlockEntity>> CIRCUIT_FABRICATOR =
            BLOCK_ENTITIES.register("circuit_fabricator", () -> new BlockEntityType<>(dev.groundburst.content.machine.CircuitFabricatorBlockEntity::new,
                    GBMachines.CIRCUIT_FABRICATOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.GasCentrifugeBlockEntity>> GAS_CENTRIFUGE =
            BLOCK_ENTITIES.register("gas_centrifuge", () -> new BlockEntityType<>(dev.groundburst.content.machine.GasCentrifugeBlockEntity::new,
                    GBMachines.GAS_CENTRIFUGE.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.FusionFuelSynthesizerBlockEntity>> FUSION_FUEL_SYNTHESIZER =
            BLOCK_ENTITIES.register("fusion_fuel_synthesizer", () -> new BlockEntityType<>(dev.groundburst.content.machine.FusionFuelSynthesizerBlockEntity::new,
                    GBMachines.FUSION_FUEL_SYNTHESIZER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.CanningMachineBlockEntity>> CANNING_MACHINE =
            BLOCK_ENTITIES.register("canning_machine", () -> new BlockEntityType<>(dev.groundburst.content.machine.CanningMachineBlockEntity::new,
                    GBMachines.CANNING_MACHINE.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.ExoticMatterFabricatorBlockEntity>> EXOTIC_MATTER_FABRICATOR =
            BLOCK_ENTITIES.register("exotic_matter_fabricator", () -> new BlockEntityType<>(dev.groundburst.content.machine.ExoticMatterFabricatorBlockEntity::new,
                    GBMachines.EXOTIC_MATTER_FABRICATOR.block().get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.power.DieselGeneratorBlockEntity>> DIESEL_GENERATOR =
            BLOCK_ENTITIES.register("diesel_generator", () -> new BlockEntityType<>(dev.groundburst.content.power.DieselGeneratorBlockEntity::new,
                    GBMachines.DIESEL_GENERATOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.power.RtgBlockEntity>> RTG =
            BLOCK_ENTITIES.register("rtg", () -> new BlockEntityType<>(dev.groundburst.content.power.RtgBlockEntity::new, GBMachines.RTG.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.power.BatteryChargerBlockEntity>> BATTERY_CHARGER =
            BLOCK_ENTITIES.register("battery_charger", () -> new BlockEntityType<>(dev.groundburst.content.power.BatteryChargerBlockEntity::new,
                    GBMachines.BATTERY_CHARGER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.DecontaminationShowerBlockEntity>> DECONTAMINATION_SHOWER =
            BLOCK_ENTITIES.register("decontamination_shower", () -> new BlockEntityType<>(dev.groundburst.content.machine.DecontaminationShowerBlockEntity::new,
                    GBMachines.DECONTAMINATION_SHOWER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.power.CapacitorBankBlockEntity>> CAPACITOR_BANK = bank(0);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.power.CapacitorBankBlockEntity>> ADVANCED_CAPACITOR_BANK = bank(1);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.power.CapacitorBankBlockEntity>> SUPERCONDUCTING_CAPACITOR_BANK = bank(2);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.power.CapacitorBankBlockEntity>> CREATIVE_CAPACITOR_BANK = bank(3);

    /** One block entity type per bank tier, so each tier gets its own GeckoLib model. */
    @SuppressWarnings("unchecked")
    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.power.CapacitorBankBlockEntity>> bank(int ordinal) {
        var tier = dev.groundburst.content.power.CapacitorBankBlockEntity.Tier.values()[ordinal];
        DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.power.CapacitorBankBlockEntity>>[] self = new DeferredHolder[1];
        self[0] = BLOCK_ENTITIES.register(tier.id(), () -> new BlockEntityType<>(
                (pos, state) -> new dev.groundburst.content.power.CapacitorBankBlockEntity(self[0].get(), tier, pos, state),
                GBMachines.CAPACITOR_BANKS.get(ordinal).block().get()));
        return self[0];
    }

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.power.CapacitorBankBlockEntity>> capacitorBank(
            dev.groundburst.content.power.CapacitorBankBlockEntity.Tier tier) {
        return switch (tier) {
            case BASIC -> CAPACITOR_BANK;
            case ADVANCED -> ADVANCED_CAPACITOR_BANK;
            case SUPERCONDUCTING -> SUPERCONDUCTING_CAPACITOR_BANK;
            case CREATIVE -> CREATIVE_CAPACITOR_BANK;
        };
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChemicalPlantBlockEntity>> CHEMICAL_PLANT =
            BLOCK_ENTITIES.register("chemical_plant", () -> new BlockEntityType<>(ChemicalPlantBlockEntity::new, GBMachines.CHEMICAL_PLANT.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.CleanRoomBlockEntity>> CLEAN_ROOM =
            BLOCK_ENTITIES.register("clean_room", () -> new BlockEntityType<>(dev.groundburst.content.machine.CleanRoomBlockEntity::new,
                    GBMachines.CLEAN_ROOM.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.FusionReactorBlockEntity>> FUSION_REACTOR =
            BLOCK_ENTITIES.register("fusion_reactor", () -> new BlockEntityType<>(dev.groundburst.content.machine.FusionReactorBlockEntity::new,
                    GBMachines.FUSION_REACTOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.SingularityReactorBlockEntity>> SINGULARITY_REACTOR =
            BLOCK_ENTITIES.register("singularity_reactor", () -> new BlockEntityType<>(dev.groundburst.content.machine.SingularityReactorBlockEntity::new,
                    GBMachines.SINGULARITY_REACTOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.FissionReactorBlockEntity>> FISSION_REACTOR =
            BLOCK_ENTITIES.register("fission_reactor", () -> new BlockEntityType<>(dev.groundburst.content.machine.FissionReactorBlockEntity::new,
                    GBMachines.FISSION_REACTOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.groundburst.content.machine.ParticleAcceleratorBlockEntity>> PARTICLE_ACCELERATOR =
            BLOCK_ENTITIES.register("particle_accelerator", () -> new BlockEntityType<>(dev.groundburst.content.machine.ParticleAcceleratorBlockEntity::new,
                    GBMachines.PARTICLE_ACCELERATOR.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortBlockEntity>> ITEM_PORT =
            BLOCK_ENTITIES.register("item_port", () -> new BlockEntityType<>(PortBlockEntity::new, GBMultiblocks.ITEM_PORT.block().get(),
                    GBMultiblocks.ENERGY_PORT.block().get(), GBMultiblocks.FLUID_PORT.block().get()));

    private GBBlockEntities() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        machine(event, ORE_CRUSHER.get());
        machine(event, COMBUSTION_GENERATOR.get());
        machine(event, ELECTROLYTIC_SEPARATOR.get());
        machine(event, CHEMICAL_PLANT.get());
        machine(event, CLEAN_ROOM.get());
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
                GBDataComponents.ENERGY.get(), dev.groundburst.content.power.BatteryItem.CAPACITY, dev.groundburst.content.power.BatteryItem.TRANSFER),
                dev.groundburst.content.part.GBParts.BATTERY.get());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_TANK.get(), (be, side) -> be.handler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, CREATIVE_FLUID_TANK.get(), (be, side) -> be.handler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ITEM_PORT.get(), PortBlockEntity::items);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ITEM_PORT.get(), PortBlockEntity::fluids);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ITEM_PORT.get(), PortBlockEntity::energy);
        machine(event, FISSION_REACTOR.get());
        machine(event, FUSION_REACTOR.get());
        machine(event, SINGULARITY_REACTOR.get());
        machine(event, PARTICLE_ACCELERATOR.get());
        machine(event, dev.groundburst.content.launch.GBLaunch.SILO_ENTITY.get());
        machine(event, dev.groundburst.content.launch.GBLaunch.CRUISE_ENTITY.get());
        machine(event, dev.groundburst.content.superweapon.GBSuperweapons.SCUD_STORM_ENTITY.get());
        machine(event, dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE_ENTITY.get());
        machine(event, dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER_ENTITY.get());
        machine(event, dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY_ENTITY.get());
        machine(event, dev.groundburst.content.launch.GBLaunch.COMPUTER_ENTITY.get());
        machine(event, dev.groundburst.content.launch.GBLaunch.STATION_ENTITY.get());
        machine(event, dev.groundburst.content.launch.GBLaunch.RADAR_ENTITY.get());
        machine(event, dev.groundburst.content.defence.GBDefence.COMPLEX_ENTITY.get());
        tallMachine(event, GBMachines.ARC_FURNACE.block().get());
        tallMachine(event, GBMachines.CRYOGENIC_CONDENSER.block().get());
        tallMachine(event, GBMachines.CRYSTAL_PULLER.block().get());
        tallMachine(event, GBMachines.INDUSTRIAL_PRESS.block().get());
        tallMachine(event, GBMachines.GAS_CENTRIFUGE.block().get());
        tallMachine(event, GBMachines.EXOTIC_MATTER_FABRICATOR.block().get());
        tallMachine(event, GBMachines.DECONTAMINATION_SHOWER.block().get());
        tallMachine(event, dev.groundburst.content.assembly.GBAssembly.ASSEMBLY_BENCH.block().get());
        tallMachine(event, dev.groundburst.content.defence.GBDefence.BATTERY.block().get());
        GBMachines.registerCableCapabilities(event);
        dev.groundburst.content.fluid.GBFluids.registerCapabilities(event);
        dev.groundburst.content.pipe.GBPipes.registerCapabilities(event);
    }

    /**
     * Both halves of a tall or wide machine expose the main half's block entity (cables and pipes may attach to either);
     * the second half (a wide machine's side, a tall machine's top) answers through its own faces.
     */
    private static void tallMachine(RegisterCapabilitiesEvent event, dev.groundburst.content.machine.MachineBlock block) {
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, be, side) ->
                level.getBlockEntity(TallMachineBlock.base(pos, state)) instanceof MachineBlockEntity m ? m.energyFor(side, sideHalf(state)) : null, block);
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, be, side) ->
                level.getBlockEntity(TallMachineBlock.base(pos, state)) instanceof MachineBlockEntity m ? m.itemsFor(side, sideHalf(state)) : null, block);
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, be, side) ->
                level.getBlockEntity(TallMachineBlock.base(pos, state)) instanceof MachineBlockEntity m ? m.fluidsFor(side, sideHalf(state)) : null, block);
    }

    /** Whether this is a two-block machine's second half (a wide one's side half, a tall one's upper half). */
    private static boolean sideHalf(net.minecraft.world.level.block.state.BlockState state) {
        if (state.getBlock() instanceof TallMachineBlock) {
            return state.getValue(TallMachineBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
        }
        return state.getBlock() instanceof dev.groundburst.content.machine.WideMachineBlock
                && state.getValue(dev.groundburst.content.machine.WideMachineBlock.PART) == dev.groundburst.content.machine.WideMachineBlock.Part.SIDE;
    }

    private static <T extends MachineBlockEntity> void machine(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, type, MachineBlockEntity::energyFor);
        event.registerBlockEntity(Capabilities.Item.BLOCK, type, MachineBlockEntity::itemsFor);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, type, MachineBlockEntity::fluidsFor);
    }
}
