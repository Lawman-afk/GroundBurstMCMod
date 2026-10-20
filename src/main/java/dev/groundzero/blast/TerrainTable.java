package dev.groundzero.blast;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * What the shock does to blocks past the crater (DESIGN.md §11.1 terrain table): in the structural band pressure rays
 * blow light blocks out and carry on through the gap; in the scorch band the ground pass scorches the ground the
 * fireball can see (§11.4c). A blast profile names its table. Tables are code for now; they move
 * to a datapack registry when the aftermath blocks (scorched earth, ash, trinitite) arrive in Phase 12.
 */
public enum TerrainTable {
    /** Conventional explosives: blown-out glass and plants, grass burnt to dirt. */
    CONVENTIONAL_TABLE(GroundZero.id("conventional"), false),
    /** Nuclear and other big blasts: as conventional, but the ground is scorched harder. */
    NUCLEAR_TABLE(GroundZero.id("nuclear"), true);

    public static final Identifier CONVENTIONAL = GroundZero.id("conventional");
    public static final Identifier NUCLEAR = GroundZero.id("nuclear");
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

    /** What an exposed surface becomes in the scorch band, or null if it stays as it is. */
    public @Nullable BlockState scorched(BlockState state) {
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
}
