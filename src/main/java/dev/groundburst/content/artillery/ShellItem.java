package dev.groundburst.content.artillery;

import net.minecraft.world.item.Item;

/** An artillery shell (DESIGN.md §20.1): loaded into its gun by hand or by pipe, or fired from its crew's inventory. */
public class ShellItem extends Item {
    private final Calibre calibre;
    private final ShellType type;

    public ShellItem(Properties properties, Calibre calibre, ShellType type) {
        super(properties);
        this.calibre = calibre;
        this.type = type;
    }

    public Calibre calibre() {
        return this.calibre;
    }

    public ShellType type() {
        return this.type;
    }
}
