package dev.groundburst.content.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Coils of barbed wire (DESIGN.md §22.2): it joins its neighbours as iron bars do, but has nothing to stand on or bump
 * into; anything alive that walks into it is caught, as in a berry bush, and scratched while it struggles.
 */
public class BarbedWireBlock extends IronBarsBlock {
    public BarbedWireBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        entity.makeStuckInBlock(state, new Vec3(0.6, 0.7, 0.6));
        if (level instanceof ServerLevel serverLevel) {
            Vec3 movement = entity.isClientAuthoritative() ? entity.getKnownMovement() : entity.oldPosition().subtract(entity.position());
            if (Math.abs(movement.x()) >= 0.003 || Math.abs(movement.z()) >= 0.003) {
                entity.hurtServer(serverLevel, level.damageSources().sweetBerryBush(), 1.0F);
            }
        }
    }
}
