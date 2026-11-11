package dev.groundburst.legacy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;

/** A GameTest's bounds (1.21 hands them out; 1.20.1 keeps them to itself). */
@Mixin(GameTestHelper.class)
public interface GameTestHelperAccess {
    @Invoker("getBounds")
    AABB groundburst$bounds();
}
