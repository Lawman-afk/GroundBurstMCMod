package dev.groundburst.content.alarm;

import java.util.List;
import java.util.function.Function;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.GeoBlockItem;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The base alarm system (DESIGN.md §18.6): its devices, their block entities, and the network's upkeep. Senders: the
 * Missile Warning Receiver, the Alarm Panel, the Hazard Monitor. Sounders: the Air-Raid Siren, the Klaxon, the Alarm
 * Bell. Lights: the Warning Beacon, the Emergency Light. The Alert Display and the Alarm Relay.
 */
public final class GBAlarms {
    public static final WithItem<AlarmBlock> RECEIVER = block(AlarmDevice.RECEIVER, "Missile Warning Receiver", AlarmBlock::new, 0);
    public static final WithItem<AlarmBlock> PANEL = block(AlarmDevice.PANEL, "Alarm Panel", AlarmBlock::new, 0);
    public static final WithItem<AlarmBlock> MONITOR = block(AlarmDevice.MONITOR, "Hazard Monitor", AlarmBlock::new, 0);
    public static final WithItem<AlarmBlock> SIREN = GBBlocks.registerWithItem(AlarmDevice.SIREN.id, "Air-Raid Siren", GBTab.MISSILES,
            p -> new AlarmBlock(p, AlarmDevice.SIREN), GBAlarms::props,
            (block, props) -> new GeoBlockItem(block, props, GroundBurst.id(AlarmDevice.SIREN.id)));
    public static final WithItem<AlarmBlock> KLAXON = block(AlarmDevice.KLAXON, "Klaxon", AlarmBlock::new, 0);
    public static final WithItem<AlarmBlock> BELL = block(AlarmDevice.BELL, "Alarm Bell", AlarmBlock::new, 0);
    public static final WithItem<AlarmBlock.Six> BEACON = GBBlocks.registerWithItem(AlarmDevice.BEACON.id, "Warning Beacon", GBTab.MISSILES,
            p -> new AlarmBlock.Six(p, AlarmDevice.BEACON), p -> props(p).lightLevel(s -> s.getValue(AlarmBlock.ACTIVE) ? 15 : 0),
            (block, props) -> new GeoBlockItem(block, props, GroundBurst.id(AlarmDevice.BEACON.id)));
    public static final WithItem<AlarmBlock.Light> LIGHT = GBBlocks.register(AlarmDevice.LIGHT.id, "Emergency Light", GBTab.MISSILES,
            p -> new AlarmBlock.Light(p, AlarmDevice.LIGHT), p -> props(p).lightLevel(s -> s.getValue(AlarmBlock.Light.LAMP) == AlarmBlock.Lamp.OFF ? 0 : 15));
    public static final WithItem<AlarmBlock> DISPLAY = block(AlarmDevice.DISPLAY, "Alert Display", AlarmBlock::new, 0);
    public static final WithItem<AlarmBlock> RELAY = block(AlarmDevice.RELAY, "Alarm Relay", AlarmBlock::new, 0);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlarmBlockEntity>> ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("alarm_device", () -> new BlockEntityType<>(
                    (pos, state) -> new AlarmBlockEntity(GBAlarms.ENTITY.get(), pos, state), RECEIVER.block().get(), PANEL.block().get(),
                    MONITOR.block().get(), KLAXON.block().get(), BELL.block().get(), LIGHT.block().get(), RELAY.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlarmBlockEntity>> DISPLAY_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register(AlarmDevice.DISPLAY.id, () -> new BlockEntityType<>(
                    (pos, state) -> new AlarmBlockEntity(GBAlarms.DISPLAY_ENTITY.get(), pos, state), DISPLAY.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlarmBlockEntity.Animated>> SIREN_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register(AlarmDevice.SIREN.id, () -> new BlockEntityType<>(
                    (pos, state) -> new AlarmBlockEntity.Animated(GBAlarms.SIREN_ENTITY.get(), pos, state), SIREN.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlarmBlockEntity.Animated>> BEACON_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register(AlarmDevice.BEACON.id, () -> new BlockEntityType<>(
                    (pos, state) -> new AlarmBlockEntity.Animated(GBAlarms.BEACON_ENTITY.get(), pos, state), BEACON.block().get()));

    /** JEI's page on the alarm system. */
    public static final String INFO = GBLang.add(GroundBurst.MODID + ".jei.info.alarms",
            "Alarm devices talk over alarm channels (0 to 9999; right-click a device to set its channel and choices). A channel "
                    + "goes off while any of its senders has something wrong: a Missile Warning Receiver sees a missile a working "
                    + "Radar Array tracks coming down within its warning radius; an Alarm Panel is switched on, or powered by "
                    + "redstone; a Hazard Monitor reads radiation over its threshold, or poison gas. Then every Air-Raid Siren, "
                    + "Klaxon and Alarm Bell on the channel sounds, every Warning Beacon and Emergency Light lights, every Alert "
                    + "Display shows what is wrong and every Alarm Relay gives redstone. Any of them also goes off when powered by "
                    + "redstone itself. The receiver, the monitor and the relay give redstone out. None needs power.");

    private GBAlarms() {}

    private static BlockBehaviour.Properties props(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL).strength(2.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion();
    }

    private static WithItem<AlarmBlock> block(AlarmDevice device, String english, java.util.function.BiFunction<BlockBehaviour.Properties, AlarmDevice, AlarmBlock> factory,
            int light) {
        Function<BlockBehaviour.Properties, AlarmBlock> f = p -> factory.apply(p, device);
        return GBBlocks.register(device.id, english, GBTab.MISSILES, f, GBAlarms::props);
    }

    /** The block entity type a device's block makes. */
    public static DeferredHolder<BlockEntityType<?>, ? extends BlockEntityType<? extends AlarmBlockEntity>> type(AlarmDevice device) {
        return switch (device) {
            case SIREN -> SIREN_ENTITY;
            case BEACON -> BEACON_ENTITY;
            case DISPLAY -> DISPLAY_ENTITY;
            default -> ENTITY;
        };
    }

    /** Every device's block, in the order JEI and the creative tab show them. */
    public static List<WithItem<? extends AlarmBlock>> all() {
        return List.of(RECEIVER, PANEL, MONITOR, SIREN, KLAXON, BELL, BEACON, LIGHT, DISPLAY, RELAY);
    }

    /** Keeps the network: lapsed alerts dropped each second; a level's channels forgotten as it unloads. */
    public static void init() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                Alarms.tick(level);
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                Alarms.forget(level);
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> Alarms.clear());
    }
}
