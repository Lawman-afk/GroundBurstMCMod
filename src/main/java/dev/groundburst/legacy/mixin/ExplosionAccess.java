package dev.groundburst.legacy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.Explosion;

/** An explosion's block interaction (1.21 hands it out; 1.20.1 keeps it to itself). */
@Mixin(Explosion.class)
public interface ExplosionAccess {
    @Accessor("blockInteraction")
    Explosion.BlockInteraction groundburst$interaction();
}
