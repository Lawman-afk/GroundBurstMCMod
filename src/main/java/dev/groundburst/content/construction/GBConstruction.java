package dev.groundburst.content.construction;

import java.util.List;
import java.util.function.Supplier;

import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WaterloggedTransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;

/**
 * The Construction set (DESIGN.md §22.2): cosmetic blocks for building Cold War sites, the world's and the player's.
 * Each drops itself (so an old site gives no free iron) and is made from steel plates and rods, white concrete, or sand
 * and string; in a creative tab of its own, Ground Burst: Construction.
 */
public final class GBConstruction {
    private static final GBTab TAB = GBTab.CONSTRUCTION;

    // --- fencing ------------------------------------------------------------------------------------------------------------
    /** Galvanised mesh between posts, joining its neighbours as iron bars do. */
    public static final WithItem<IronBarsBlock> CHAIN_LINK_FENCE = GBBlocks.register("chain_link_fence", "Chain-link Fence", TAB,
            IronBarsBlock::new, p -> metal(p, MapColor.METAL).strength(3f, 6f).sound(SoundType.CHAIN).noOcclusion());
    /** Coils of barbed wire, along a fence's top or on the ground: anything walking through is caught and scratched. */
    public static final WithItem<BarbedWireBlock> BARBED_WIRE = GBBlocks.register("barbed_wire", "Barbed Wire", TAB,
            BarbedWireBlock::new, p -> metal(p, MapColor.METAL).strength(2f, 4f).sound(SoundType.CHAIN).noOcclusion().noCollision());

    // --- sheeting and plate -----------------------------------------------------------------------------------------------
    public static final WithItem<Block> CORRUGATED_STEEL = GBBlocks.register("corrugated_steel", "Corrugated Steel", TAB,
            Block::new, p -> metal(p, MapColor.METAL));
    public static final WithItem<StairBlock> CORRUGATED_STEEL_STAIRS = stairs("corrugated_steel_stairs", "Corrugated Steel Stairs",
            () -> CORRUGATED_STEEL.block().get(), MapColor.METAL);
    public static final WithItem<SlabBlock> CORRUGATED_STEEL_SLAB = GBBlocks.register("corrugated_steel_slab", "Corrugated Steel Slab", TAB,
            SlabBlock::new, p -> metal(p, MapColor.METAL));
    public static final WithItem<Block> RUSTED_CORRUGATED_STEEL = GBBlocks.register("rusted_corrugated_steel", "Rusted Corrugated Steel", TAB,
            Block::new, p -> metal(p, MapColor.TERRACOTTA_ORANGE));
    public static final WithItem<StairBlock> RUSTED_CORRUGATED_STEEL_STAIRS = stairs("rusted_corrugated_steel_stairs",
            "Rusted Corrugated Steel Stairs", () -> RUSTED_CORRUGATED_STEEL.block().get(), MapColor.TERRACOTTA_ORANGE);
    public static final WithItem<SlabBlock> RUSTED_CORRUGATED_STEEL_SLAB = GBBlocks.register("rusted_corrugated_steel_slab",
            "Rusted Corrugated Steel Slab", TAB, SlabBlock::new, p -> metal(p, MapColor.TERRACOTTA_ORANGE));
    /** Riveted plates in grey paint. */
    public static final WithItem<Block> STEEL_PANEL = GBBlocks.register("steel_panel", "Steel Panel", TAB,
            Block::new, p -> metal(p, MapColor.COLOR_GRAY));
    public static final WithItem<Block> RUSTED_STEEL_PANEL = GBBlocks.register("rusted_steel_panel", "Rusted Steel Panel", TAB,
            Block::new, p -> metal(p, MapColor.TERRACOTTA_BROWN));
    /**
     * Open steel grid: catwalks and floors you see through. Grating next to grating (or lattice) shows only the outside,
     * from both sides (SeeThrough).
     */
    public static final WithItem<WaterloggedTransparentBlock> STEEL_GRATING = GBBlocks.register("steel_grating", "Steel Grating", TAB,
            SeeThrough.Grating::new, p -> seeThrough(metal(p, MapColor.METAL)).sound(SoundType.COPPER_GRATE));
    public static final WithItem<SlabBlock> STEEL_GRATING_SLAB = GBBlocks.register("steel_grating_slab", "Steel Grating Slab", TAB,
            SeeThrough.GratingSlab::new, p -> seeThrough(metal(p, MapColor.METAL)).sound(SoundType.COPPER_GRATE));

    // --- structure ------------------------------------------------------------------------------------------------------------
    /** A steel girder, set along any axis as a log is. */
    public static final WithItem<RotatedPillarBlock> I_BEAM = GBBlocks.register("i_beam", "I-Beam", TAB,
            RotatedPillarBlock::new, p -> metal(p, MapColor.COLOR_GRAY).noOcclusion());
    /**
     * An open braced steel cube: towers, masts and gantries. Lattice next to lattice (or grating) shows only the outside,
     * from both sides: looking into a stack you see its far side (SeeThrough).
     */
    public static final WithItem<TransparentBlock> LATTICE_FRAME = GBBlocks.register("lattice_frame", "Lattice Frame", TAB,
            SeeThrough.Lattice::new, p -> seeThrough(metal(p, MapColor.METAL)));

    // --- radome ---------------------------------------------------------------------------------------------------------------
    /** White fibreglass panels for a radar's dome. */
    public static final WithItem<Block> RADOME_PANEL = GBBlocks.register("radome_panel", "Radome Panel", TAB,
            Block::new, GBConstruction::radome);
    public static final WithItem<StairBlock> RADOME_PANEL_STAIRS = GBBlocks.register("radome_panel_stairs", "Radome Panel Stairs", TAB,
            p -> new StairBlock(RADOME_PANEL.block().get().defaultBlockState(), p), GBConstruction::radome);
    public static final WithItem<SlabBlock> RADOME_PANEL_SLAB = GBBlocks.register("radome_panel_slab", "Radome Panel Slab", TAB,
            SlabBlock::new, GBConstruction::radome);

    // --- fittings ----------------------------------------------------------------------------------------------------------------
    /** A lamp head turned out from a wall, a post or the ground. */
    public static final WithItem<FloodlightBlock> FLOODLIGHT = GBBlocks.register("floodlight", "Floodlight", TAB,
            FloodlightBlock::new, p -> metal(p, MapColor.COLOR_GRAY).strength(2f, 4f).noOcclusion().lightLevel(s -> 15));
    /** Bags of sand stacked into a wall: they soak up blasts better than stone. */
    public static final WithItem<Block> SANDBAGS = GBBlocks.register("sandbags", "Sandbags", TAB,
            Block::new, GBConstruction::sandbags);
    public static final WithItem<SlabBlock> SANDBAG_SLAB = GBBlocks.register("sandbag_slab", "Sandbag Slab", TAB,
            SlabBlock::new, GBConstruction::sandbags);
    public static final WithItem<WarningSignBlock> RADIATION_SIGN = sign("radiation_sign", "Radiation Sign", MapColor.COLOR_YELLOW);
    public static final WithItem<WarningSignBlock> HIGH_VOLTAGE_SIGN = sign("high_voltage_sign", "High Voltage Sign", MapColor.COLOR_YELLOW);
    public static final WithItem<WarningSignBlock> KEEP_OUT_SIGN = sign("keep_out_sign", "Keep Out Sign", MapColor.COLOR_RED);
    public static final WithItem<WarningSignBlock> TOXIC_SIGN = sign("toxic_sign", "Toxic Hazard Sign", MapColor.COLOR_ORANGE);

    // --- antennas --------------------------------------------------------------------------------------------------------------
    /** A long-range search antenna for show (the Cold War stations'), turning while it has a redstone signal. */
    public static final WithItem<SearchRadarBlock> SEARCH_RADAR = GBBlocks.register("search_radar", "Search Radar", TAB,
            p -> new SearchRadarBlock(p, () -> GBConstruction.SEARCH_RADAR_ENTITY.get()),
            p -> metal(p, MapColor.METAL).strength(4f, 8f).noOcclusion());
    /** The same antenna toppled and rusting, as the world's old radar sites have it. */
    public static final WithItem<SearchRadarWreckBlock> SEARCH_RADAR_WRECK = GBBlocks.register("search_radar_wreck", "Wrecked Search Radar",
            TAB, p -> new SearchRadarWreckBlock(p, () -> GBConstruction.SEARCH_RADAR_WRECK_ENTITY.get()),
            p -> metal(p, MapColor.TERRACOTTA_BROWN).strength(4f, 8f).noOcclusion());
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<SearchRadarBlockEntity>> SEARCH_RADAR_ENTITY =
            dev.groundburst.registry.GBBlockEntities.BLOCK_ENTITIES.register("search_radar", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    (pos, state) -> new SearchRadarBlockEntity(GBConstruction.SEARCH_RADAR_ENTITY.get(), pos, state), SEARCH_RADAR.block().get()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<SearchRadarBlockEntity>> SEARCH_RADAR_WRECK_ENTITY =
            dev.groundburst.registry.GBBlockEntities.BLOCK_ENTITIES.register("search_radar_wreck", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    (pos, state) -> new SearchRadarBlockEntity(GBConstruction.SEARCH_RADAR_WRECK_ENTITY.get(), pos, state),
                    SEARCH_RADAR_WRECK.block().get()));

    // --- the missile wreck ---------------------------------------------------------------------------------------------------------
    /** A missile broken in three where it came down, as the Crashed Missile sites have it; its model runs some eight blocks on
     *  from the block toward its facing (DESIGN.md §22.2). */
    public static final WithItem<MissileWreckBlock> MISSILE_WRECK = GBBlocks.register("missile_wreck", "Missile Wreck", TAB,
            p -> new MissileWreckBlock(p, () -> GBConstruction.MISSILE_WRECK_ENTITY.get()),
            p -> metal(p, MapColor.SNOW).strength(4f, 8f).noOcclusion());
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<PropBlockEntity>> MISSILE_WRECK_ENTITY =
            dev.groundburst.registry.GBBlockEntities.BLOCK_ENTITIES.register("missile_wreck", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    (pos, state) -> new PropBlockEntity(GBConstruction.MISSILE_WRECK_ENTITY.get(), pos, state), MISSILE_WRECK.block().get()));

    // --- the silo door ------------------------------------------------------------------------------------------------------------
    /** Two leaves over a 7x7 opening that slide apart onto rails on a redstone signal; for show (DESIGN.md §22.2). */
    public static final WithItem<SiloDoorBlock> LARGE_SILO_DOOR = GBBlocks.register("large_silo_door", "Large Silo Door", TAB,
            p -> new SiloDoorBlock(p, () -> GBConstruction.LARGE_SILO_DOOR_ENTITY.get()), GBConstruction::door);
    /** The same door left to rust, as the world's old silos have it (stuck where it stopped there). */
    public static final WithItem<SiloDoorBlock> RUSTED_LARGE_SILO_DOOR = GBBlocks.register("rusted_large_silo_door", "Rusted Large Silo Door",
            TAB, p -> new SiloDoorBlock(p, () -> GBConstruction.RUSTED_LARGE_SILO_DOOR_ENTITY.get()), GBConstruction::door);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<SiloDoorBlockEntity>> LARGE_SILO_DOOR_ENTITY =
            dev.groundburst.registry.GBBlockEntities.BLOCK_ENTITIES.register("large_silo_door", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    (pos, state) -> new SiloDoorBlockEntity(GBConstruction.LARGE_SILO_DOOR_ENTITY.get(), pos, state), LARGE_SILO_DOOR.block().get()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<SiloDoorBlockEntity>> RUSTED_LARGE_SILO_DOOR_ENTITY =
            dev.groundburst.registry.GBBlockEntities.BLOCK_ENTITIES.register("rusted_large_silo_door", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    (pos, state) -> new SiloDoorBlockEntity(GBConstruction.RUSTED_LARGE_SILO_DOOR_ENTITY.get(), pos, state),
                    RUSTED_LARGE_SILO_DOOR.block().get()));
    /** The doors (their loot is the middle's alone). */
    public static final List<WithItem<SiloDoorBlock>> DOORS = List.of(LARGE_SILO_DOOR, RUSTED_LARGE_SILO_DOOR);

    /** Every block of the set, in the creative tab's order. */
    public static final List<WithItem<? extends Block>> ALL = List.of(CHAIN_LINK_FENCE, BARBED_WIRE, CORRUGATED_STEEL,
            CORRUGATED_STEEL_STAIRS, CORRUGATED_STEEL_SLAB, RUSTED_CORRUGATED_STEEL, RUSTED_CORRUGATED_STEEL_STAIRS,
            RUSTED_CORRUGATED_STEEL_SLAB, STEEL_PANEL, RUSTED_STEEL_PANEL, STEEL_GRATING, STEEL_GRATING_SLAB, I_BEAM, LATTICE_FRAME,
            RADOME_PANEL, RADOME_PANEL_STAIRS, RADOME_PANEL_SLAB, FLOODLIGHT, SANDBAGS, SANDBAG_SLAB, RADIATION_SIGN, HIGH_VOLTAGE_SIGN,
            KEEP_OUT_SIGN, TOXIC_SIGN, SEARCH_RADAR, SEARCH_RADAR_WRECK, MISSILE_WRECK);
    /** The slabs (they drop two when doubled). */
    public static final List<WithItem<SlabBlock>> SLABS = List.of(CORRUGATED_STEEL_SLAB, RUSTED_CORRUGATED_STEEL_SLAB, STEEL_GRATING_SLAB,
            RADOME_PANEL_SLAB, SANDBAG_SLAB);
    public static final List<WithItem<StairBlock>> STAIRS = List.of(CORRUGATED_STEEL_STAIRS, RUSTED_CORRUGATED_STEEL_STAIRS, RADOME_PANEL_STAIRS);
    public static final List<WithItem<WarningSignBlock>> SIGNS = List.of(RADIATION_SIGN, HIGH_VOLTAGE_SIGN, KEEP_OUT_SIGN, TOXIC_SIGN);

    private GBConstruction() {}

    /** Loads the class, so its blocks register with the rest. */
    public static void register() {}

    private static Properties metal(Properties p, MapColor colour) {
        return p.mapColor(colour).strength(4f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    private static Properties door(Properties p) {
        return metal(p, MapColor.METAL).strength(6f, 24f).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE);
    }

    private static Properties seeThrough(Properties p) {
        return p.noOcclusion().isValidSpawn(Blocks::never).isRedstoneConductor((s, l, at) -> false).isSuffocating((s, l, at) -> false)
                .isViewBlocking((s, l, at, box) -> false);
    }

    private static Properties radome(Properties p) {
        return p.mapColor(MapColor.SNOW).strength(1.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.CALCITE);
    }

    private static Properties sandbags(Properties p) {
        return p.mapColor(MapColor.SAND).strength(1.2f, 12f).sound(SoundType.WOOL);
    }

    private static WithItem<StairBlock> stairs(String name, String english, Supplier<Block> base, MapColor colour) {
        return GBBlocks.register(name, english, TAB, p -> new StairBlock(base.get().defaultBlockState(), p), p -> metal(p, colour));
    }

    private static WithItem<WarningSignBlock> sign(String name, String english, MapColor colour) {
        return GBBlocks.register(name, english, TAB, WarningSignBlock::new,
                p -> p.mapColor(colour).strength(1f, 3f).sound(SoundType.METAL).noOcclusion().noCollision());
    }
}
