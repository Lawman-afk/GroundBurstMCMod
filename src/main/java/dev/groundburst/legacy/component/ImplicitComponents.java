package dev.groundburst.legacy.component;

/**
 * Minecraft 1.21's block entity data kept in its item (a capacitor bank's charge, a bomb's fuze): handed to the item as
 * the block drops (the loot function groundburst:copy_components, dev.groundburst.legacy.loot) and back to the block
 * entity as its item is placed (dev.groundburst.legacy.mixin.BlockItemMixin). The port gives the mod's block entities that
 * have these methods this interface.
 */
public interface ImplicitComponents {
    void collectImplicitComponents(DataComponentMap.Builder components);

    void applyImplicitComponents(DataComponentInput components);
}
