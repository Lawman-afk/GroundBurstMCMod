package dev.groundburst.content.machine;

import java.util.ArrayList;
import java.util.List;

import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBTab;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The upgrade chips (DESIGN.md §7.3): a speed, an efficiency and a balanced chip on each circuit tier, basic to
 * quantum ({@code <tier>_<kind>_chip}).
 */
public final class GBUpgrades {
    /** JEI's page for the chips. */
    public static final String INFO = dev.groundburst.registry.GBLang.add(dev.groundburst.GroundBurst.MODID + ".jei.info.upgrade_chips",
            "Upgrade chips go in the Upgrades tab on the left of a processing machine's or an assembler's screen: three slots, "
                    + "and the chips in them add up. A speed chip makes it work 10%% faster a circuit tier (40%% at quantum); an "
                    + "efficiency chip makes it use 5%% less power a tier (20%% at quantum); a balanced chip does half of each. Two "
                    + "balanced chips are made from a speed and an efficiency chip.");

    /** Every chip, kind by kind, each kind's tiers in order (speed basic to quantum, then efficiency, then balanced). */
    public static final List<DeferredItem<UpgradeChipItem>> CHIPS = new ArrayList<>();

    static {
        for (UpgradeChipItem.Kind kind : UpgradeChipItem.Kind.values()) {
            for (UpgradeChipItem.Tier tier : UpgradeChipItem.Tier.values()) {
                CHIPS.add(GBItems.register(tier.id + "_" + kind.id + "_chip", tier.english + " " + kind.english + " Chip", GBTab.MACHINES,
                        p -> new UpgradeChipItem(p, kind, tier), p -> p));
            }
        }
    }

    private GBUpgrades() {}

    /** Loads the registrations. */
    public static void init() {}

    /** The chip of a kind and tier. */
    public static DeferredItem<UpgradeChipItem> chip(UpgradeChipItem.Kind kind, UpgradeChipItem.Tier tier) {
        return CHIPS.get(kind.ordinal() * UpgradeChipItem.Tier.values().length + tier.ordinal());
    }
}
