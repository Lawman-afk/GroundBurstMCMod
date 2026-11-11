package dev.groundburst.legacy.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

/** A container's loot table, still to be rolled (1.21 hands it out; 1.20.1 keeps it to itself). */
@Mixin(RandomizableContainerBlockEntity.class)
public interface RandomizableContainerAccess {
    @Accessor("lootTable")
    @Nullable ResourceLocation groundburst$lootTable();
}
