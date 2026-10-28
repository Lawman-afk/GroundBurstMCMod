package dev.groundburst.blast;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.fallout.GBFallout;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * What the shock does to blocks past the crater (DESIGN.md §11.1 terrain table): in the structural band pressure rays
 * blow light blocks out and carry on through the gap; in the scorch band the ground pass scorches the ground the
 * fireball can see (§11.4c). A blast profile names its table. The nuclear table (slice 12b, §21.8) turns the land to the
 * aftermath's blocks: scorched earth and stone, glassed sand, charred wood; ash where the fire band burned; and it lines
 * the crater: slag where it was rock, trinitite where it was sand, irradiated soil where it was earth.
 */
public enum TerrainTable {
    /** Conventional explosives: blown-out glass and plants, grass burnt to dirt. */
    CONVENTIONAL_TABLE(GroundBurst.id("conventional"), false),
    /** Nuclear and other big blasts: as conventional, but the ground is scorched harder. */
    NUCLEAR_TABLE(GroundBurst.id("nuclear"), true);

    public static final Identifier CONVENTIONAL = GroundBurst.id("conventional");
    public static final Identifier NUCLEAR = GroundBurst.id("nuclear");
    private static final Map<Identifier, TerrainTable> BY_ID = Map.of(CONVENTIONAL, CONVENTIONAL_TABLE, NUCLEAR, NUCLEAR_TABLE);

    private final Identifier id;
    private final boolean harsh;

    TerrainTable(Identifier id, boolean harsh) {
        this.id = id;
        this.harsh = harsh;
    }

    public Identifier id() {
        return id;
    }

    public static TerrainTable byId(Identifier id) {
        return BY_ID.getOrDefault(id, CONVENTIONAL_TABLE);
    }

    /** Whether the shock blows this block out of the way (glass, leaves, plants, doors, torches, snow layers). */
    public boolean blowsOut(BlockState state) {
        return state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.SNOW) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN)
                || (state.canBeReplaced() && state.getFluidState().isEmpty() && !state.isAir());
    }

    /** Whether the fire band leaves ash on the scorched ground, and the crater is lined ({@link #lining}). */
    public boolean nuclear() {
        return this.harsh;
    }

    /** What an exposed surface becomes in the scorch band, or null if it stays as it is. */
    public @Nullable BlockState scorched(BlockState state) {
        if (this.harsh) {
            BlockState burnt = burnt(state);
            if (burnt != null) {
                return burnt;
            }
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL)) {
            return this.harsh ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        }
        if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)) {
            return this.harsh ? Blocks.WATER.defaultBlockState() : null;
        }
        if (state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)) {
            return Blocks.DIRT.defaultBlockState();
        }
        return null;
    }

    /**
     * The nuclear fireball's scorching: earth to Scorched Earth, stone to Scorched Stone, sand and sandstone glassed,
     * wood charred.
     */
    private static @Nullable BlockState burnt(BlockState state) {
        if (earth(state) || state.is(Blocks.DIRT_PATH) || state.is(BlockTags.MUD)) {
            return GBFallout.SCORCHED_EARTH.block().get().defaultBlockState();
        }
        if (rock(state)) {
            return GBFallout.SCORCHED_STONE.block().get().defaultBlockState();
        }
        if (sand(state)) {
            return GBFallout.GLASSED_SAND.block().get().defaultBlockState();
        }
        if (state.is(BlockTags.LOGS) && state.hasProperty(RotatedPillarBlock.AXIS)) {
            String path = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
            RotatedPillarBlock charred = path.endsWith("_wood") || path.endsWith("_hyphae") ? GBFallout.CHARRED_WOOD.block().get()
                    : GBFallout.CHARRED_LOG.block().get();
            return charred.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
        }
        if (state.is(BlockTags.PLANKS) && !state.is(GBFallout.CHARRED_PLANKS.block().get())) {
            return GBFallout.CHARRED_PLANKS.block().get().defaultBlockState();
        }
        return null;
    }

    /**
     * What the nuclear crater's wall becomes where it stands next to the hole (null: as it was): rock melts to Slag,
     * sand fuses to Trinitite, earth takes up the fallout as Irradiated Soil.
     */
    public @Nullable BlockState lining(BlockState state) {
        if (!this.harsh) {
            return null;
        }
        if (rock(state) || state.is(GBFallout.SCORCHED_STONE.block().get())) {
            return GBFallout.SLAG.block().get().defaultBlockState();
        }
        if (sand(state) || state.is(GBFallout.GLASSED_SAND.block().get())) {
            return GBFallout.TRINITITE.block().get().defaultBlockState();
        }
        if (earth(state) || state.is(GBFallout.SCORCHED_EARTH.block().get())) {
            return GBFallout.IRRADIATED_SOIL.block().get().defaultBlockState();
        }
        return null;
    }

    /** Rock: the overworld's stone (stone, granite, diorite, andesite, tuff, deepslate) and cobblestone. */
    private static boolean rock(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.BlockItems.COBBLESTONES.block());
    }

    /** Sand and sandstone (a desert's ground is sandstone a few blocks down, and flat worlds' is on top). */
    private static boolean sand(BlockState state) {
        return state.is(BlockTags.SAND) || state.is(Tags.BlockItems.SANDSTONE_BLOCKS.block());
    }

    /** Earth: dirt, grass blocks (grass, podzol, mycelium, and dead grass), moss, farmland. */
    private static boolean earth(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.GRASS_BLOCKS) || state.is(BlockTags.MOSS_BLOCKS) || state.is(Blocks.FARMLAND);
    }
}
