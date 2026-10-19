package dev.groundzero.content.multiblock;

import java.util.Map;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.multiblock.MultiblockDefinition.Entry;
import dev.groundzero.registry.GZBlocks;
import dev.groundzero.registry.GZBlocks.WithItem;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZTab;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredItem;

/** Structural blocks and multiblock patterns (DESIGN.md §23). */
public final class GZMultiblocks {
    public static final WithItem<MultiblockPartBlock> MACHINE_CASING = GZBlocks.register("machine_casing", "Machine Casing", GZTab.MACHINES,
            MultiblockPartBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<GlassPartBlock> REACTOR_GLASS = GZBlocks.register("reactor_glass", "Reactor Glass", GZTab.MACHINES,
            GlassPartBlock::new, p -> p.mapColor(MapColor.COLOR_GRAY).strength(3f, 12f).requiresCorrectToolForDrops().sound(SoundType.GLASS)
                    .noOcclusion().isViewBlocking((s, l, pos, box) -> false).isSuffocating((s, l, pos) -> false).pushReaction(PushReaction.IMMOVEABLE));
    public static final WithItem<PortBlock> ITEM_PORT = GZBlocks.register("item_port", "Item Port", GZTab.MACHINES,
            PortBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));

    /**
     * Chemical Plant (2x2x2): controller front-left with casing beside and behind it and the item port at the back
     * right; a roof of reactor glass. The formed model shows two windowed tanks, a manifold and a stirrer.
     */
    public static final MultiblockDefinition CHEMICAL_PLANT = new MultiblockDefinition(GroundZero.id("chemical_plant"),
            new String[][] {
                    {"XM", "MI"},
                    {"GG", "GG"},
            },
            Map.of('M', Entry.of(MACHINE_CASING.block()), 'I', Entry.of(ITEM_PORT.block()), 'G', Entry.of(REACTOR_GLASS.block()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> GZMachines.CHEMICAL_PLANT.block().get())));

    public static final DeferredItem<HoloProjectorItem> HOLO_PROJECTOR = GZItems.register("holo_projector", "Holo-Projector", GZTab.MACHINES,
            HoloProjectorItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<StructureSpawnerItem> CHEMICAL_PLANT_SPAWNER = GZItems.register("chemical_plant_spawner",
            "Structure Spawner: Chemical Plant", GZTab.MACHINES, p -> new StructureSpawnerItem(p, () -> CHEMICAL_PLANT),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

    private GZMultiblocks() {}

    public static void init() {}
}
