package dev.groundburst.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;

import dev.groundburst.content.machine.Frame;
import dev.groundburst.content.machine.Frames;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * A Create contraption's frame: a carried block entity stands at the place it was taken from ({@code anchor} plus its
 * place in the contraption), and the contraption has it wherever it has moved and turned it to now. A contraption aboard
 * a Sable ship (a bearing turning a launcher on an airship) is itself in the ship's plot: the ship's frame then takes it
 * on into the world.
 */
record ContraptionFrame(AbstractContraptionEntity entity, BlockPos anchor) implements Frame {
    /** The frame the contraption itself is in: the world's, or a ship's. */
    private Frame outer() {
        return Frames.at(this.entity.level(), this.entity.blockPosition());
    }

    @Override
    public Vec3 toWorld(Vec3 local) {
        return outer().toWorld(this.entity.toGlobalVector(local.subtract(Vec3.atLowerCornerOf(this.anchor)), 1f));
    }

    @Override
    public Vec3 directionToWorld(Vec3 local) {
        return outer().directionToWorld(this.entity.applyRotation(local, 1f));
    }

    @Override
    public Vec3 toLocal(Vec3 world) {
        return this.entity.toLocalVector(outer().toLocal(world), 1f).add(Vec3.atLowerCornerOf(this.anchor));
    }

    @Override
    public Vec3 directionToLocal(Vec3 world) {
        return this.entity.reverseRotation(outer().directionToLocal(world), 1f);
    }

    @Override
    public Vec3 velocity(Vec3 local) {
        return this.entity.getDeltaMovement();
    }
}
