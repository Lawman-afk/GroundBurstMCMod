package dev.groundburst.content.construction;

import java.util.List;

import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Fire Extinguisher (DESIGN.md §22.5): a red cylinder standing on the floor, its label to the front. A click gives a
 * burst from its horn toward its front: a white cloud, and the fire in the blocks before it (and on anything burning
 * there) put out.
 */
public class FireExtinguisherBlock extends DecorBlock {
    public FireExtinguisherBlock(Properties properties) {
        super(properties, List.of(new double[] {6, 0, 6, 10, 12, 10}));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            spray(server, pos, state.getValue(FACING));
            level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** The burst: its cloud, its hiss, and the fires within a block of the block before it put out (on the ground and on whatever burns). */
    public static void spray(ServerLevel level, BlockPos pos, Direction front) {
        double x = pos.getX() + 0.5 + front.getStepX() * 0.5;
        double z = pos.getZ() + 0.5 + front.getStepZ() * 0.5;
        for (int i = 1; i <= 4; i++) {
            double r = 0.15 * i;
            level.sendParticles(ParticleTypes.CLOUD, x + front.getStepX() * 0.35 * i, pos.getY() + 0.55, z + front.getStepZ() * 0.35 * i,
                    6, r, r * 0.6, r, 0.02);
        }
        level.playSound(null, pos, GBSounds.EXTINGUISHER_SPRAY.get(), SoundSource.BLOCKS, 0.8f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        BlockPos ahead = pos.relative(front);
        for (BlockPos p : BlockPos.betweenClosed(ahead.offset(-1, -1, -1), ahead.offset(1, 1, 1))) {
            if (level.getBlockState(p).is(BlockTags.FIRE)) {
                level.removeBlock(p, false);
            }
        }
        for (Entity e : level.getEntities((Entity) null, new AABB(ahead).inflate(1.0), Entity::isOnFire)) {
            e.clearFire();
        }
    }
}
