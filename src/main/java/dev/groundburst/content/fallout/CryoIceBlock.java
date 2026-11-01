package dev.groundburst.content.fallout;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Water a Cryo ICBM's front froze where it stood (DESIGN.md §16.16): frozen in an instant, it is milky with the air it
 * trapped, not clear like ice that set slowly. It is slippery as vanilla's ice and melts by a lamp as it does; but where
 * it is not cold enough for snow to lie it also thaws on its own ({@link #THAW_CHANCE} a random tick, about twenty
 * minutes on average). Ice that was still water ({@link #SOURCE}) melts back to water; ice that was running water (a
 * stream, a fall) melts away, its water run off, so a thaw floods nothing.
 */
public class CryoIceBlock extends HalfTransparentBlock {
    /** Whether it was still water (a source) when it froze: only that melts back to water. */
    public static final BooleanProperty SOURCE = BooleanProperty.create("source");
    public static final float THAW_CHANCE = 1f / 18;

    public CryoIceBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(SOURCE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SOURCE);
    }

    /** Frozen water: still water ({@code source}) or running. */
    public BlockState frozen(boolean source) {
        return defaultBlockState().setValue(SOURCE, source);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 11 - state.getLightDampening()
                || random.nextFloat() < THAW_CHANCE && !FrostedBlock.cold(level, pos)) {
            melt(state, level, pos);
        }
    }

    /** It melts: back to water if it was still water (but where water boils away), else away. */
    protected void melt(BlockState state, Level level, BlockPos pos) {
        if (!state.getValue(SOURCE) || level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
            level.removeBlock(pos, false);
            return;
        }
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        level.neighborChanged(pos, Blocks.WATER, null);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));
    }

    /** Broken over water (or over what vanilla's ice melts on), still water's ice leaves its water, as vanilla's does. */
    @Override
    public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
            ItemStack destroyedWith) {
        super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
        if (!state.getValue(SOURCE) || EnchantmentHelper.hasTag(destroyedWith, EnchantmentTags.PREVENTS_ICE_MELTING)
                || level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
            return;
        }
        BlockState below = level.getBlockState(pos.below());
        if (below.is(BlockTags.ICE_MELTS_WHEN_DESTROYED_ABOVE) || below.liquid()) {
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        }
    }
}
