package dev.groundburst.content.fallout;

import java.util.List;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBTab;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The aftermath on the ground (DESIGN.md §21.7, §21.8 slice 12b): what a nuclear blast leaves of the land round it,
 * what a strong radiation field kills, and the ash a fallout plume lays as the wind carries it. In their own creative
 * tab, Ground Burst: Aftermath.
 */
public final class GBFallout {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, GroundBurst.MODID);
    /** Ash falling under a fallout plume, drifting with the wind (client: {@code FalloutClient}). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FALLOUT_ASH_PARTICLE =
            PARTICLES.register("fallout_ash", () -> new SimpleParticleType(false));

    // --- glassed and molten ground -------------------------------------------------------------------------------------------
    /** Sand the fireball fused into green glass, round a nuclear crater's rim (weakly radioactive). */
    public static final WithItem<Block> TRINITITE = GBBlocks.register("trinitite", "Trinitite", GBTab.AFTERMATH, Block::new,
            p -> p.mapColor(MapColor.COLOR_GREEN).strength(1.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.GLASS)
                    .instrument(NoteBlockInstrument.HAT));
    /** Sand in a nuclear blast's scorch band, its surface glazed. */
    public static final WithItem<Block> GLASSED_SAND = GBBlocks.register("glassed_sand", "Glassed Sand", GBTab.AFTERMATH, Block::new,
            p -> p.mapColor(MapColor.SAND).strength(1.2f, 4f).requiresCorrectToolForDrops().sound(SoundType.GLASS)
                    .instrument(NoteBlockInstrument.HAT));
    /** Rock that melted round a nuclear crater and set again: dark, glassy and full of bubbles. */
    public static final WithItem<Block> SLAG = GBBlocks.register("slag", "Slag", GBTab.AFTERMATH, Block::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.5f, 9f).requiresCorrectToolForDrops().sound(SoundType.BASALT)
                    .instrument(NoteBlockInstrument.BASEDRUM));

    // --- burnt ground -------------------------------------------------------------------------------------------------------
    public static final WithItem<Block> SCORCHED_EARTH = GBBlocks.register("scorched_earth", "Scorched Earth", GBTab.AFTERMATH, Block::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(0.6f).sound(SoundType.GRAVEL));
    public static final WithItem<Block> SCORCHED_STONE = GBBlocks.register("scorched_stone", "Scorched Stone", GBTab.AFTERMATH, Block::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM));
    /** The earth round a nuclear crater, laced with its fallout: it glows faintly and is radioactive. */
    public static final WithItem<Block> IRRADIATED_SOIL = GBBlocks.register("irradiated_soil", "Irradiated Soil", GBTab.AFTERMATH, Block::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.6f).sound(SoundType.GRAVEL).lightLevel(s -> 3));
    public static final WithItem<AshLayerBlock> ASH = GBBlocks.register("ash", "Ash", GBTab.AFTERMATH, AshLayerBlock::new,
            p -> ash(p, MapColor.COLOR_LIGHT_GRAY));
    public static final WithItem<FalloutAshBlock> FALLOUT_ASH = GBBlocks.register("fallout_ash", "Fallout Ash", GBTab.AFTERMATH,
            p -> new FalloutAshBlock(p, () -> GBFallout.ASH.block().get()), p -> ash(p, MapColor.COLOR_GRAY).randomTicks());

    // --- burnt wood ---------------------------------------------------------------------------------------------------------
    public static final WithItem<RotatedPillarBlock> CHARRED_LOG = GBBlocks.register("charred_log", "Charred Log", GBTab.AFTERMATH,
            RotatedPillarBlock::new, p -> charred(p));
    public static final WithItem<RotatedPillarBlock> CHARRED_WOOD = GBBlocks.register("charred_wood", "Charred Wood", GBTab.AFTERMATH,
            RotatedPillarBlock::new, p -> charred(p));
    public static final WithItem<Block> CHARRED_PLANKS = GBBlocks.register("charred_planks", "Charred Planks", GBTab.AFTERMATH, Block::new,
            p -> charred(p).strength(1.5f, 2.5f));

    // --- dead plants --------------------------------------------------------------------------------------------------------
    public static final WithItem<DeadGrassBlock> DEAD_GRASS_BLOCK = GBBlocks.register("dead_grass_block", "Dead Grass Block", GBTab.AFTERMATH,
            DeadGrassBlock::new, p -> p.mapColor(MapColor.COLOR_BROWN).strength(0.6f).randomTicks().sound(SoundType.GRASS));
    public static final WithItem<DeadLeavesBlock> DEAD_LEAVES = GBBlocks.register("dead_leaves", "Dead Leaves", GBTab.AFTERMATH,
            DeadLeavesBlock::new, p -> p.mapColor(MapColor.COLOR_BROWN).strength(0.2f).randomTicks().sound(SoundType.GRASS).noOcclusion()
                    .isValidSpawn(Blocks::ocelotOrParrot)
                    .isSuffocating((s, l, at) -> false).ignitedByLava()
                    .pushReaction(PushReaction.POPPED).isRedstoneConductor((s, l, at) -> false));
    public static final WithItem<WitheredCropsBlock> WITHERED_CROPS = GBBlocks.register("withered_crops", "Withered Crops", GBTab.AFTERMATH,
            WitheredCropsBlock::new, p -> p.mapColor(MapColor.COLOR_BROWN).noCollision().instabreak().randomTicks().sound(SoundType.CROP)
                    .pushReaction(PushReaction.POPPED).ignitedByLava());

    // --- frozen ground (slice 12e) ------------------------------------------------------------------------------------------
    /** Stone a cryo burst froze: rimed white; it thaws back to stone where snow would not lie. */
    public static final WithItem<FrostedBlock> FROSTED_STONE = GBBlocks.register("frosted_stone", "Frosted Stone", GBTab.AFTERMATH,
            p -> new FrostedBlock(p, () -> Blocks.STONE), p -> p.mapColor(MapColor.ICE).strength(1.5f, 6f).requiresCorrectToolForDrops()
                    .randomTicks().friction(0.9f).sound(SoundType.STONE).instrument(NoteBlockInstrument.BASEDRUM));
    /** Grass a cryo burst froze: icy, crunching underfoot; it thaws back to grass where snow would not lie. */
    public static final WithItem<FrostedBlock> FROSTED_GRASS_BLOCK = GBBlocks.register("frosted_grass_block", "Frosted Grass Block",
            GBTab.AFTERMATH, p -> new FrostedBlock(p, () -> Blocks.GRASS_BLOCK), p -> p.mapColor(MapColor.ICE).strength(0.6f).randomTicks()
                    .sound(SoundType.POWDER_SNOW));
    /** A plant frozen solid: it melts away once warm. */
    public static final WithItem<FrozenPlantBlock> FROZEN_PLANT = GBBlocks.register("frozen_plant", "Frozen Plant", GBTab.AFTERMATH,
            FrozenPlantBlock::new, p -> p.mapColor(MapColor.ICE).noCollision().instabreak().randomTicks().sound(SoundType.GLASS)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED));
    /** Ice crystals grown round a cryo burst's middle: they glow faintly and melt where it is warm. */
    public static final WithItem<CryoCrystalBlock> CRYO_CRYSTAL = GBBlocks.register("cryo_crystal", "Cryo Crystal", GBTab.AFTERMATH,
            CryoCrystalBlock::new, p -> p.mapColor(MapColor.ICE).strength(0.5f).noOcclusion().sound(SoundType.AMETHYST_CLUSTER)
                    .lightLevel(s -> 4).friction(0.98f).pushReaction(PushReaction.POPPED));

    // --- toxic ground (slice 12e) -------------------------------------------------------------------------------------------
    /** Earth soaked in anthrax: it kills what grows on it, breathes a thin haze, and is spent in about a day. */
    public static final WithItem<ToxicSoilBlock> TOXIC_SOIL = GBBlocks.register("toxic_soil", "Toxic Soil", GBTab.AFTERMATH,
            ToxicSoilBlock::new, p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.6f).randomTicks().sound(SoundType.GRAVEL));

    /** Recipe-viewer info pages. */
    public static final String INFO_FALLOUT_ASH = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".info.fallout_ash",
            "A nuclear burst on the ground, a dirty warhead or a reactor meltdown sends a plume of debris off on the wind: it lays "
                    + "radiation along its path and Fallout Ash on the ground, a layer at a time. The ash is weakly radioactive; "
                    + "shovel it away (it drops nothing), or leave it to weather into plain Ash over a couple of hours.");
    public static final String INFO_FROZEN = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".info.frozen_ground",
            "A cryo burst freezes the ground it reaches: rime on stone and grass, plants frozen solid, and ice crystals grown "
                    + "round its middle. Where snow would not lie it thaws again over the next quarter of an hour or so; in cold "
                    + "places it stays. Frozen plants and crystals melt away (silk touch keeps a crystal).");
    public static final String INFO_TOXIC_SOIL = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".info.toxic_soil",
            "Where a heavy anthrax cloud lies on the ground, or a toxin SCUD's residue settles, the soil turns. Toxic Soil kills "
                    + "what grows on it, breathes a thin anthrax haze now and then, and is spent (coarse dirt) after about a day.");
    public static final String INFO_WASTELAND = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".info.wasteland",
            "A nuclear blast leaves a wasteland over its scorched ground: the Nuclear Wasteland biome, grey-brown, its water a "
                    + "dead olive, ash drifting down and no animals. A crater fills with water where the land would flood it: "
                    + "next to the sea, a lake or a river, or deep below sea level where the ground is wet.");
    public static final String INFO_SCORCHED = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".info.scorched_ground",
            "What a nuclear blast leaves of the land: the fireball burns earth, stone, sand and wood to scorched and glassed "
                    + "ground and charred trunks, leaves ash where the fires burned, and melts the crater's wall to slag, trinitite "
                    + "and irradiated soil (both radioactive). Strong radiation kills plants: dead leaves drop away, crops wither, "
                    + "and dead grass grows back once the ground is safe again.");

    private GBFallout() {}

    private static BlockBehaviour.Properties ash(BlockBehaviour.Properties p, MapColor colour) {
        return p.mapColor(colour).replaceable().forceSolidOff().strength(0.1f).sound(SoundType.SAND)
                .isViewBlocking((s, l, pos, box) -> s.getValue(AshLayerBlock.LAYERS) >= 8).pushReaction(PushReaction.POPPED);
    }

    private static BlockBehaviour.Properties charred(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.COLOR_BLACK).strength(1.5f).sound(SoundType.WOOD).instrument(NoteBlockInstrument.BASS);
    }

    /** Every ground block, in the order the creative tab lists them. */
    public static List<WithItem<? extends Block>> blocks() {
        return List.of(TRINITITE, GLASSED_SAND, SLAG, SCORCHED_EARTH, SCORCHED_STONE, IRRADIATED_SOIL, ASH, FALLOUT_ASH,
                CHARRED_LOG, CHARRED_WOOD, CHARRED_PLANKS, DEAD_GRASS_BLOCK, DEAD_LEAVES, WITHERED_CROPS, TOXIC_SOIL, FROSTED_STONE,
                FROSTED_GRASS_BLOCK, FROZEN_PLANT, CRYO_CRYSTAL);
    }

    public static void init() {}

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
        Withering.register();
        Wind.register();
        Plumes.register();
        Wasteland.register(modBus);
    }
}
