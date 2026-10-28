package dev.groundburst.content.fallout;

import dev.groundburst.content.radiation.Radiation;
import dev.groundburst.content.radiation.RadiationSource;
import dev.groundburst.content.radiation.RadiationSources;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Plants dying in strong radiation (DESIGN.md §21.2, §21.8 slice 12b). Once a second, columns at random where a source
 * gives {@link #RATE} RAD/h or more (in loaded chunks; about one a second per {@link #AREA_PER_COLUMN} square blocks of
 * it, {@link #MAX_COLUMNS} at most) wither from the top down ({@link #wither}). The neutron burst withers with the same
 * column (NeutronBurst).
 */
public final class Withering {
    /** The dose rate that kills plants, RAD/h. */
    public static final float RATE = 1_000;
    public static final float AREA_PER_COLUMN = 64;
    public static final int MAX_COLUMNS = 1_500;
    /** How deep a column is looked through from its top. */
    private static final int DEPTH = 32;

    private Withering() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 5) {
                tick(level);
            }
        });
    }

    /** Not on the GameTest server (its tests' sources would wither each other's plots); tests wither their own columns. */
    private static void tick(ServerLevel level) {
        if (!Radiation.enabled() || level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;
        }
        long now = level.getGameTime();
        RandomSource random = level.getRandom();
        int budget = MAX_COLUMNS;
        for (RadiationSource s : RadiationSources.of(level).sources()) {
            double reach = reach(s, now);
            if (reach <= 0) {
                continue;
            }
            int n = Math.min(budget, (int) Math.ceil(Math.PI * reach * reach / AREA_PER_COLUMN));
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Mth.TWO_PI, d = reach * Math.sqrt(random.nextDouble());
                wither(level, Mth.floor(s.center().x + Math.cos(a) * d), Mth.floor(s.center().z + Math.sin(a) * d), Block.UPDATE_ALL);
            }
            budget -= n;
            if (budget <= 0) {
                return;
            }
        }
    }

    /** How far from its centre a source kills plants now, blocks (0: nowhere): its dose rate, peak x (1 - d/r)^2, is
     * {@link #RATE} or more within r x (1 - sqrt(RATE / peak)). */
    public static double reach(RadiationSource s, long now) {
        float peak = s.peakAt(now);
        return peak < RATE ? 0 : s.radius() * (1 - Math.sqrt(RATE / peak));
    }

    /**
     * One column, from the top down: leaves die on the branch (and what is under them is looked at too), crops wither,
     * grass, ferns, flowers and vines go, saplings become dead bushes, and the grass block, mycelium, podzol or moss
     * under them dies. Anything else (ground, a trunk, a roof) ends it: what is under it is spared.
     */
    public static void wither(ServerLevel level, int x, int z, int flags) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, level.getMinY(), z);
        if (!level.isLoaded(at)) {
            return;
        }
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        for (int y = top; y > top - DEPTH && y >= level.getMinY(); y--) {
            at.setY(y);
            BlockState s = level.getBlockState(at);
            if (s.isAir() || s.is(GBFallout.DEAD_LEAVES.block().get()) || s.is(GBFallout.WITHERED_CROPS.block().get()) || s.is(Blocks.DEAD_BUSH)) {
                continue;
            }
            if (s.is(BlockTags.LEAVES)) {
                level.setBlock(at, GBFallout.DEAD_LEAVES.block().get().defaultBlockState(), flags);
                continue;
            }
            if (s.is(BlockTags.CROPS)) {
                level.setBlock(at, GBFallout.WITHERED_CROPS.block().get().defaultBlockState(), flags);
                continue;
            }
            if (s.is(Blocks.VINE) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
                    || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(BlockTags.SMALL_FLOWERS) || s.is(BlockTags.FLOWERS) || s.is(Blocks.MOSS_CARPET)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), flags);
                continue;
            }
            if (s.is(BlockTags.SAPLINGS)) {
                level.setBlock(at, Blocks.DEAD_BUSH.defaultBlockState(), flags);
                continue;
            }
            if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(Blocks.PODZOL) || s.is(Blocks.MOSS_BLOCK)) {
                level.setBlock(at, GBFallout.DEAD_GRASS_BLOCK.block().get().defaultBlockState(), flags);
            }
            return;
        }
    }
}
