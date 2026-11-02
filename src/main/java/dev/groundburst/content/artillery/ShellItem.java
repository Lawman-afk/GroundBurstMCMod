package dev.groundburst.content.artillery;

import net.minecraft.world.item.Item;

/** An artillery shell (DESIGN.md §20.1): loaded into a gun by hand or by pipe, or fired from its crew's inventory. */
public class ShellItem extends Item {
    private final ShellType type;

    public ShellItem(Properties properties, ShellType type) {
        super(properties);
        this.type = type;
    }

    public ShellType type() {
        return this.type;
    }
}
