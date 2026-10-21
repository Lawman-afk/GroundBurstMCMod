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
    public static final WithItem<MultiblockPartBlock> ANTENNA = part("antenna", "Antenna", MapColor.METAL, SoundType.METAL, true);

    // --- controllers ---
    public static final WithItem<MultiblockControllerBlock> SILO_CONTROLLER = GZBlocks.register("launch_silo", "Launch Silo Controller",
            GZTab.MISSILES, p -> new MultiblockControllerBlock(p, GZLaunch.SILO_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<LaunchComputerBlock> COMPUTER_CONTROLLER = GZBlocks.register("launch_computer", "Launch Computer",
            GZTab.MISSILES, p -> new LaunchComputerBlock(p, GZLaunch.COMPUTER_ENTITY),
            p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchSiloBlockEntity>> SILO_ENTITY =
            GZBlockEntities.BLOCK_ENTITIES.register("launch_silo", () -> new BlockEntityType<>(LaunchSiloBlockEntity::new, SILO_CONTROLLER.block().get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchComputerBlockEntity>> COMPUTER_ENTITY =
            GZBlockEntities.BLOCK_ENTITIES.register("launch_computer", () -> new BlockEntityType<>(LaunchComputerBlockEntity::new,
                    COMPUTER_CONTROLLER.block().get()));

    // --- the missile ---
    public static final DeferredItem<Item> BASIC_MISSILE = GZItems.simple("basic_missile", "Basic Missile", GZTab.MISSILES, p -> p.stacksTo(1));
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

    private GZLaunch() {}

    public static void init() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent event) -> event.register(TICKETS));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> Launches.clear());
    }

    private static WithItem<MultiblockPartBlock> part(String id, String english, MapColor color, SoundType sound, boolean see) {
        return GZBlocks.register(id, english, GZTab.MISSILES, MultiblockPartBlock::new, p -> {
            p.mapColor(color).strength(4f, 8f).requiresCorrectToolForDrops().sound(sound).pushReaction(PushReaction.IMMOVEABLE);
            return see ? p.noOcclusion() : p;
        });
    }
}
