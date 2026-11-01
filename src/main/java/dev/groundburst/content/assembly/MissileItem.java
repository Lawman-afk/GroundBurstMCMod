package dev.groundburst.content.assembly;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * An assembled missile (DESIGN.md §10): what goes in a Launch Silo. Everything about it is in its
 * {@code groundburst:missile} component; its tooltip lists the warhead, body, range, guidance and hardening.
 */
public class MissileItem extends Item {
    private static final String T_EMPTY = GBLang.add(GroundBurst.MODID + ".missile.tip.empty", "Assemble one at an Ordnance Assembly Bench");
    private static final String T_WARHEAD = GBLang.add(GroundBurst.MODID + ".missile.tip.warhead", "Warhead: %s");
    private static final String T_RANGE = GBLang.add(GroundBurst.MODID + ".missile.tip.range", "Range: %s blocks");
    private static final String T_GUIDANCE = GBLang.add(GroundBurst.MODID + ".missile.tip.guidance", "Guidance: %s");
    private static final String T_HARDENED = GBLang.add(GroundBurst.MODID + ".missile.tip.hardened", "Hardened against EMP");
    private static final String T_NUCLEAR = GBLang.add(GroundBurst.MODID + ".missile.tip.nuclear", "Nuclear: fires from a Launch Complex or a Missile Silo");
    private static final String T_EXOTIC = GBLang.add(GroundBurst.MODID + ".missile.tip.exotic", "Exotic: fires from a Launch Complex or a Missile Silo");
    private static final String T_CRUISE = GBLang.add(GroundBurst.MODID + ".missile.tip.cruise",
            "Cruise missile: flies low over the ground; fires from a Cruise Missile Launcher");
    static final String T_NAMED = GBLang.add(GroundBurst.MODID + ".missile.named", "%s Missile");
    /** An intercontinental missile is a whole weapon, named by its warhead (so a search for "Tsar" finds it). */
    static final String T_NAMED_ICBM = GBLang.add(GroundBurst.MODID + ".missile.named_icbm", "Intercontinental Missile (%s)");

    public MissileItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static @Nullable MissileSpec spec(ItemStack stack) {
        return stack.get(GBDataComponents.MISSILE.get());
    }

    /**
     * Each warhead's own missile, by name (DESIGN.md §10.4): its look (the warhead's id, or "<id>_cruise" for that
     * warhead's cruise missile, §17.11) to its name's key.
     */
    private static final java.util.Map<String, String> NAMES = new java.util.HashMap<>();

    private static void name(String warhead, String english) {
        NAMES.put(warhead, GBLang.add(GroundBurst.MODID + ".missile.name." + warhead, english));
    }

    /** A Light or Medium warhead's cruise missile ("HE Cruise Missile"). */
    private static void cruise(String warhead, String english) {
        name(warhead + MissileSpec.CRUISE_LOOK, english);
    }

    static {
        name("he_warhead", "HE Missile");
        name("napalm_warhead", "Napalm Missile");
        name("chemical_warhead", "Chemical Missile");
        name("toxic_waste_warhead", "Toxic Waste Missile");
        name("dirty_warhead", "Dirty Missile");
        name("heavy_warhead", "Heavy HE Missile");
        name("cluster_warhead", "Cluster Missile");
        name("gas_warhead", "Poison Gas Missile");
        name("smoke_warhead", "Smoke Missile");
        name("cryo_warhead", "Cryo Missile");
        name("thermobaric_warhead", "Thermobaric Missile");
        name("bunker_buster_warhead", "Bunker Buster Missile");
        name("emp_warhead", "EMP Missile");
        name("massive_warhead", "Massive Ordnance Missile");
        name("tactical_nuclear_warhead", "Tactical Nuclear Missile");
        name("fission_warhead", "Fission Missile");
        name("boosted_fission_warhead", "Boosted Fission Missile");
        name("neutron_warhead", "Neutron Missile");
        name("thermonuclear_warhead", "Thermonuclear ICBM");
        name("cobalt_warhead", "Cobalt ICBM");
        name("tsar_warhead", "Tsar ICBM");
        name("antimatter_warhead", "Antimatter ICBM");
        name("singularity_warhead", "Singularity ICBM");
        name("storm_warhead", "Storm ICBM");
        name("heavy_mirv_bus", "Heavy MIRV");                  // DESIGN.md §17.14: the bus's id is its missile's look
        name("strategic_mirv_bus", "Strategic MIRV");
        cruise("he_warhead", "HE Cruise Missile");
        cruise("napalm_warhead", "Napalm Cruise Missile");
        cruise("chemical_warhead", "Chemical Cruise Missile");
        cruise("toxic_waste_warhead", "Toxic Waste Cruise Missile");
        cruise("dirty_warhead", "Dirty Cruise Missile");
        cruise("heavy_warhead", "Heavy HE Cruise Missile");
        cruise("cluster_warhead", "Cluster Cruise Missile");
        cruise("gas_warhead", "Poison Gas Cruise Missile");
        cruise("smoke_warhead", "Smoke Cruise Missile");
        cruise("cryo_warhead", "Cryo Cruise Missile");
    }

    /**
     * Named by its warhead, every missile being its warhead's own: "HE Missile", "Tsar ICBM" (a search for "Tsar" finds
     * it); a missile whose warhead has no missile of its own by its body: "Heavy Missile".
     */
    @Override
    public Component getName(ItemStack stack) {
        MissileSpec s = spec(stack);
        if (s == null) {
            return super.getName(stack);
        }
        String key = NAMES.get(s.look());
        if (key != null) {
            return Component.translatable(key);
        }
        return s.intercontinental() && !s.warhead().isEmpty() ? Component.translatable(T_NAMED_ICBM, s.warhead().getHoverName())
                : Component.translatable(T_NAMED, Component.translatable(MissileParts.bodyName(s.body())));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        MissileSpec s = spec(stack);
        if (s == null) {
            tooltip.accept(Component.translatable(T_EMPTY).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.accept(Component.translatable(T_WARHEAD, s.warhead().getHoverName()).withStyle(ChatFormatting.GRAY));
        if (s.mirv()) {
            Mirv.describe(s.warhead()).forEach(line -> tooltip.accept(Component.literal("  ").append(line)));
        } else {
            FuzeSettings fuze = s.warhead().getOrDefault(GBDataComponents.FUZE.get(), FuzeSettings.IMPACT);
            tooltip.accept(Component.literal("  ").append(WarheadItem.fuzeLine(fuze)).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.accept(Component.translatable(T_RANGE, s.range()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(T_GUIDANCE, Component.translatable(MissileParts.guidanceName(s.guidance()))).withStyle(ChatFormatting.GRAY));
        if (s.hardened()) {
            tooltip.accept(Component.translatable(T_HARDENED).withStyle(ChatFormatting.AQUA));
        }
        if (s.nuclear()) {
            tooltip.accept(Component.translatable(T_NUCLEAR).withStyle(ChatFormatting.YELLOW));
        } else if (s.exotic()) {
            tooltip.accept(Component.translatable(T_EXOTIC).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (s.cruise()) {
            tooltip.accept(Component.translatable(T_CRUISE).withStyle(ChatFormatting.AQUA));
        }
    }
}
