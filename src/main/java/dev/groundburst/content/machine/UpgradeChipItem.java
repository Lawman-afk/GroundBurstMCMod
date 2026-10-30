package dev.groundburst.content.machine;

import java.util.Locale;
import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * An upgrade chip (DESIGN.md §7.3): slotted into a machine's or an assembler's Upgrades tab (three slots), it makes the
 * machine work faster or on less power. A speed chip makes it work 10% faster a level of its circuit tier, basic to
 * quantum (drawing that much more power a tick, so the same per job: it needs power fed in faster); an efficiency chip
 * makes it use 5% less power a level (after the user's look: half the 10% it first gave, so three can never make a
 * machine run free); a balanced chip does half of each. A quantum speed chip: 40% faster; a quantum efficiency chip:
 * 20% less; a quantum balanced chip: 20% faster on 10% less. Chips in the three slots add up.
 */
public class UpgradeChipItem extends Item {
    public enum Kind {
        SPEED("speed", "Speed"), EFFICIENCY("efficiency", "Efficiency"), BALANCED("balanced", "Balanced");

        public final String id, english;

        Kind(String id, String english) {
            this.id = id;
            this.english = english;
        }
    }

    /** The circuit tier it is built on: its level (1 to 4) is the tenths it gives. */
    public enum Tier {
        BASIC("basic", "Basic"), ADVANCED("advanced", "Advanced"), MILITARY("military", "Military"), QUANTUM("quantum", "Quantum");

        public final String id, english;

        Tier(String id, String english) {
            this.id = id;
            this.english = english;
        }

        public int level() {
            return ordinal() + 1;
        }
    }

    static final String T_SPEED = GBLang.add(GroundBurst.MODID + ".upgrade.speed", "+%s%% speed");
    static final String T_EFFICIENCY = GBLang.add(GroundBurst.MODID + ".upgrade.efficiency", "+%s%% efficiency");
    static final String T_WHERE = GBLang.add(GroundBurst.MODID + ".upgrade.where", "Slotted in a machine's or assembler's Upgrades tab");

    public final Kind kind;
    public final Tier tier;

    public UpgradeChipItem(Properties properties, Kind kind, Tier tier) {
        super(properties.stacksTo(16));
        this.kind = kind;
        this.tier = tier;
    }

    /** How much faster it makes a machine work (0.4: 40% faster). */
    public float speedBonus() {
        return switch (this.kind) {
            case SPEED -> 0.1f * this.tier.level();
            case BALANCED -> 0.05f * this.tier.level();
            case EFFICIENCY -> 0;
        };
    }

    /** How much less power it makes a machine use (0.2: 20% less). */
    public float efficiency() {
        return switch (this.kind) {
            case EFFICIENCY -> 0.05f * this.tier.level();
            case BALANCED -> 0.025f * this.tier.level();
            case SPEED -> 0;
        };
    }

    /** A share as a percentage: whole ("40"), or to a tenth where it has one (a balanced chip's "7.5"). */
    public static String percent(float share) {
        float pct = share * 100;
        return Math.abs(pct - Math.round(pct)) < 0.01f ? String.format(Locale.ROOT, "%d", Math.round(pct))
                : String.format(Locale.ROOT, "%.1f", pct);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        if (speedBonus() > 0) {
            tooltip.accept(Component.translatable(T_SPEED, percent(speedBonus())).withStyle(ChatFormatting.GOLD));
        }
        if (efficiency() > 0) {
            tooltip.accept(Component.translatable(T_EFFICIENCY, percent(efficiency())).withStyle(ChatFormatting.GREEN));
        }
        tooltip.accept(Component.translatable(T_WHERE).withStyle(ChatFormatting.DARK_GRAY));
    }
}
