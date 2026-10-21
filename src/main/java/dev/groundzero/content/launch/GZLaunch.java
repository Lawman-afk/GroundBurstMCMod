package dev.groundzero.content.launch;

import java.util.Map;
import java.util.function.Supplier;

import dev.groundzero.GroundZero;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockControllerBlock;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.MultiblockDefinition.Entry;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZBlocks;
import dev.groundzero.registry.GZBlocks.WithItem;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZSounds;
import dev.groundzero.registry.GZTab;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Launching (DESIGN.md §17, a basic launch site built ahead of Phase 9): the gantry Launch Silo, the Launch Computer
 * with its radar, their component blocks, the Basic Missile and the missile in flight.
 */
public final class GZLaunch {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(GroundZero.MODID);

    // --- component blocks (used to build the two structures) ---
    public static final WithItem<MultiblockPartBlock> LAUNCH_PAD = part("launch_pad", "Launch Pad", MapColor.COLOR_GRAY, SoundType.STONE, false);
    public static final WithItem<MultiblockPartBlock> GANTRY_FRAME = part("gantry_frame", "Gantry Frame", MapColor.COLOR_RED, SoundType.METAL, true);
    public static final WithItem<MultiblockPartBlock> GANTRY_ARM = part("gantry_arm", "Gantry Arm", MapColor.COLOR_RED, SoundType.METAL, true);
    public static final WithItem<MultiblockPartBlock> RADAR_MAST = part("radar_mast", "Radar Mast", MapColor.METAL, SoundType.METAL, true);
    public static final WithItem<MultiblockPartBlock> RADAR_DISH = part("radar_dish", "Radar Dish", MapColor.METAL, SoundType.METAL, true);
    /** The Radar Array's crown: one item that places a 3x3 dish. */
    public static final WithItem<LargeRadarDishBlock> LARGE_RADAR_DISH = GZBlocks.register("large_radar_dish", "Large Radar Dish", GZTab.MISSILES,
            LargeRadarDishBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE).noOcclusion());
    public static final WithItem<MultiblockPartBlock> ANTENNA = part("antenna", "Antenna", MapColor.METAL, SoundType.METAL, true);

    // --- controllers ---
    public static final WithItem<MultiblockControllerBlock> SILO_CONTROLLER = GZBlocks.register("launch_silo", "Launch Silo Controller",
            GZTab.MISSILES, p -> new MultiblockControllerBlock(p, GZLaunch.SILO_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<LaunchComputerBlock> COMPUTER_CONTROLLER = GZBlocks.register("launch_computer", "Launch Computer",
            GZTab.MISSILES, p -> new LaunchComputerBlock(p, GZLaunch.COMPUTER_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<RadarArrayBlock> RADAR_CONTROLLER = GZBlocks.register("radar_array", "Radar Array",
            GZTab.MISSILES, p -> new RadarArrayBlock(p, GZLaunch.RADAR_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarArrayBlockEntity>> RADAR_ENTITY =
            GZBlockEntities.BLOCK_ENTITIES.register("radar_array", () -> new BlockEntityType<>(RadarArrayBlockEntity::new,
                    RADAR_CONTROLLER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchSiloBlockEntity>> SILO_ENTITY =
            GZBlockEntities.BLOCK_ENTITIES.register("launch_silo", () -> new BlockEntityType<>(LaunchSiloBlockEntity::new, SILO_CONTROLLER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchComputerBlockEntity>> COMPUTER_ENTITY =
            GZBlockEntities.BLOCK_ENTITIES.register("launch_computer", () -> new BlockEntityType<>(LaunchComputerBlockEntity::new,
                    COMPUTER_CONTROLLER.block().get()));

    // --- the missile ---
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> MISSILE = ENTITIES.registerEntityType("missile", MissileEntity::new,
            MobCategory.MISC, b -> b.sized(0.9f, 0.9f).clientTrackingRange(32).updateInterval(1));

    public static final Supplier<SoundEvent> SIREN = GZSounds.LAUNCH_SIREN;
    public static final Supplier<SoundEvent> MISSILE_LAUNCH = GZSounds.MISSILE_LAUNCH;
    public static final Supplier<SoundEvent> MISSILE_ENGINE = GZSounds.MISSILE_ENGINE;

    /** Keeps a missile's chunk, and its target's, loaded while it flies. */
    public static final TicketController TICKETS = new TicketController(GroundZero.id("missile"), (level, tickets) -> {
        for (var owner : java.util.List.copyOf(tickets.getEntityTickets().keySet())) {
            tickets.removeAllTickets(owner);
        }
    });

    /**
     * The Launch Silo (gantry type): a 3x3 Launch Pad with the controller at the front middle and an Energy Port at the
     * back middle; a Gantry Frame tower 9 tall beside the pad's middle row, its Gantry Arm reaching in at the sixth
     * layer; the missile's column over the pad's middle kept clear 6 blocks up.
     */
    public static final MultiblockDefinition SILO = new MultiblockDefinition(GroundZero.id("launch_silo"),
            new String[][] {
                    {"PXP ", "PPPG", "PEP "},
                    {"    ", " . G", "    "},
                    {"    ", " . G", "    "},
                    {"    ", " . G", "    "},
                    {"    ", " . G", "    "},
                    {"    ", " . G", "    "},
                    {"    ", " .AG", "    "},
                    {"    ", "   G", "    "},
                    {"    ", "   G", "    "},
            },
            Map.of('P', Entry.of(() -> LAUNCH_PAD.block().get()), 'G', Entry.of(() -> GANTRY_FRAME.block().get()),
                    'A', Entry.of(() -> GANTRY_ARM.block().get()), 'E', Entry.of(() -> GZMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> SILO_CONTROLLER.block().get())));

    /**
     * The Launch Computer: the terminal (controller) with an Energy Port beside it and an Antenna on top; behind it a
     * Radar Mast three high with the Radar Dish on top.
     */
    public static final MultiblockDefinition COMPUTER = new MultiblockDefinition(GroundZero.id("launch_computer"),
            new String[][] {
                    {"XE", "M "},
                    {"N ", "M "},
                    {"  ", "M "},
                    {"  ", "D "},
            },
            Map.of('M', Entry.of(() -> RADAR_MAST.block().get()), 'D', Entry.of(() -> RADAR_DISH.block().get()),
                    'N', Entry.of(() -> ANTENNA.block().get()), 'E', Entry.of(() -> GZMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> COMPUTER_CONTROLLER.block().get())));

    /**
     * The Radar Array: a 3x3 deck of Machine Casing with the controller at the front middle and an Energy Port at the
     * back middle; a Radar Mast three high from the deck's middle; on top a Large Radar Dish (its middle over the mast;
     * formed, it turns).
     */
    public static final MultiblockDefinition RADAR = new MultiblockDefinition(GroundZero.id("radar_array"),
            new String[][] {
                    {"CXC", "CCC", "CEC"},
                    {"   ", " M ", "   "},
                    {"   ", " M ", "   "},
                    {"   ", " M ", "   "},
                    {"ddd", "dDd", "ddd"},
            },
            Map.of('C', Entry.of(() -> GZMultiblocks.MACHINE_CASING.block().get()), 'M', Entry.of(() -> RADAR_MAST.block().get()),
                    'D', largeDish(LargeRadarDishBlock.Part.CENTER), 'd', largeDish(LargeRadarDishBlock.Part.EDGE),
                    'E', Entry.of(() -> GZMultiblocks.ENERGY_PORT.block().get()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> RADAR_CONTROLLER.block().get())));

    /** A cell of the Large Radar Dish: its middle, or one of the parts round it. */
    private static Entry largeDish(LargeRadarDishBlock.Part part) {
        return new Entry(s -> s.is(LARGE_RADAR_DISH.block().get()) && s.getValue(LargeRadarDishBlock.PART) == part,
                () -> LARGE_RADAR_DISH.block().get().defaultBlockState().setValue(LargeRadarDishBlock.PART, part));
    }

    private GZLaunch() {}

    public static void init() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent event) -> event.register(TICKETS));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> Launches.clear());
        // Missiles in the air move on every tick, loaded or not (Flights).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                Flights.of(level).tick(level);
                TerrainSurvey.of(level).tick(level);
            }
        });
        // The terrain survey hears about chunks coming and going (it maps those near a Launch Computer).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                TerrainSurvey.of(level).chunkLoaded(e.getChunk().getPos().pack());
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Unload e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                    && e.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
                TerrainSurvey.of(level).chunkUnloading(level, chunk);
            }
        });
    }

    private static WithItem<MultiblockPartBlock> part(String id, String english, MapColor color, SoundType sound, boolean see) {
        return GZBlocks.register(id, english, GZTab.MISSILES, MultiblockPartBlock::new, p -> {
            p.mapColor(color).strength(4f, 8f).requiresCorrectToolForDrops().sound(sound).pushReaction(PushReaction.IMMOVEABLE);
            return see ? p.noOcclusion() : p;
        });
    }
}
