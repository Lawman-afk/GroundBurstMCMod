package dev.groundzero.registry;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.ArcFurnaceBlockEntity;
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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CryogenicCondenserBlockEntity>> CRYOGENIC_CONDENSER =
            BLOCK_ENTITIES.register("cryogenic_condenser", () -> new BlockEntityType<>(CryogenicCondenserBlockEntity::new, GZMachines.CRYOGENIC_CONDENSER.block().get()));

    private GZBlockEntities() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        machine(event, ORE_CRUSHER.get());
        machine(event, COMBUSTION_GENERATOR.get());
        machine(event, ELECTROLYTIC_SEPARATOR.get());
        tallMachine(event, GZMachines.ARC_FURNACE.block().get());
        tallMachine(event, GZMachines.CRYOGENIC_CONDENSER.block().get());
        tallMachine(event, GZMachines.INDUSTRIAL_PRESS.block().get());
        GZMachines.registerCableCapabilities(event);
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
