package dev.groundzero.registry;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.OreCrusherBlockEntity;
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

    private GZBlockEntities() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        machine(event, ORE_CRUSHER.get());
        machine(event, COMBUSTION_GENERATOR.get());
        GZMachines.registerCableCapabilities(event);
    }

    private static <T extends MachineBlockEntity> void machine(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, type, MachineBlockEntity::energyFor);
        event.registerBlockEntity(Capabilities.Item.BLOCK, type, MachineBlockEntity::itemsFor);
    }
}
