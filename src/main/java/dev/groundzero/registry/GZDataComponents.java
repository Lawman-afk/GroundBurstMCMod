package dev.groundzero.registry;

import com.mojang.serialization.Codec;

import dev.groundzero.GroundZero;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components. */
public final class GZDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, GroundZero.MODID);

    /** Stored FE: batteries, and capacitor banks picked up with their charge. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY = COMPONENTS.registerComponentType("energy",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A Remote Detonator's frequency (0-9999). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FREQUENCY = COMPONENTS.registerComponentType("frequency",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private GZDataComponents() {}
}
