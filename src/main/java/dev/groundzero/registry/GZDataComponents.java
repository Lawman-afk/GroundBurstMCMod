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

    /** A warhead's fuze and its setting (fitted at the Assembly Bench, DESIGN.md §9.4). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<dev.groundzero.content.assembly.FuzeSettings>> FUZE =
            COMPONENTS.registerComponentType("fuze", b -> b.persistent(dev.groundzero.content.assembly.FuzeSettings.CODEC)
                    .networkSynchronized(dev.groundzero.content.assembly.FuzeSettings.STREAM_CODEC));

    /** An assembled missile: its warhead, body, motor, guidance and hardening (DESIGN.md §10). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<dev.groundzero.content.assembly.MissileSpec>> MISSILE =
            COMPONENTS.registerComponentType("missile", b -> b.persistent(dev.groundzero.content.assembly.MissileSpec.CODEC)
                    .networkSynchronized(dev.groundzero.content.assembly.MissileSpec.STREAM_CODEC));

    /** How far a uranium fuel rod has burned in a reactor (DESIGN.md §7). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<dev.groundzero.content.nuclear.FuelBurnup>> FUEL_BURNUP =
            COMPONENTS.registerComponentType("fuel_burnup", b -> b.persistent(dev.groundzero.content.nuclear.FuelBurnup.CODEC)
                    .networkSynchronized(dev.groundzero.content.nuclear.FuelBurnup.STREAM_CODEC));

    /** A Geiger Counter's alarm threshold, as an index into its settings (DESIGN.md §21.8; 0: off). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GEIGER_ALARM = COMPONENTS.registerComponentType("geiger_alarm",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Until when (game time) a powered item lies dead after an EMP (DESIGN.md §21.4): set while its holder stands in a
     * field, so it comes back 10 s after they leave.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> EMP_UNTIL = COMPONENTS.registerComponentType("emp_until",
            b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    private GZDataComponents() {}
}
