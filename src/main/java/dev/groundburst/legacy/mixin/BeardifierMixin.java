package dev.groundburst.legacy.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import dev.groundburst.legacy.world.Encapsulation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A chunk's beardifier closes the mod's site parts underground in rock, as 1.21's "encapsulate" does
 * ({@link Encapsulation}): it finds them as it is made, and adds their rock to the density it gives.
 */
@Mixin(Beardifier.class)
abstract class BeardifierMixin implements Encapsulation.ClosedIn {
    /** The boxes of the mod's parts closed in rock near its chunk (none, nearly always). */
    @Unique
    private List<BoundingBox> groundburst$closedIn = List.of();

    @Override
    public void groundburst$closeIn(List<BoundingBox> boxes) {
        this.groundburst$closedIn = boxes;
    }

    @Inject(method = "forStructuresInChunk", at = @At("RETURN"))
    private static void groundburst$findClosedIn(StructureManager structures, ChunkPos chunk, CallbackInfoReturnable<Beardifier> cir) {
        List<BoundingBox> boxes = Encapsulation.near(structures, chunk);
        if (!boxes.isEmpty()) {
            ((Encapsulation.ClosedIn) cir.getReturnValue()).groundburst$closeIn(boxes);
        }
    }

    @ModifyReturnValue(method = "compute", at = @At("RETURN"))
    private double groundburst$rock(double density, DensityFunction.FunctionContext at) {
        List<BoundingBox> boxes = this.groundburst$closedIn;
        for (int i = 0; i < boxes.size(); i++) {
            density += Encapsulation.rock(boxes.get(i), at.blockX(), at.blockY(), at.blockZ());
        }
        return density;
    }
}
