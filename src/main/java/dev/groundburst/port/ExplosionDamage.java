package dev.groundburst.port;

import net.minecraft.world.damagesource.DamageSource;

/**
 * An explosion's damage source (every {@code Explosion} has it, through {@code mixin/ServerExplosionMixin}): healing
 * names an explosion with no entity behind it by its damage type (main's DESIGN.md §21.9; a bed's is the bad respawn
 * point's). 1.21.1's explosion keeps it to itself; main's has a getter.
 */
public interface ExplosionDamage {
    DamageSource groundburst$damageSource();
}
