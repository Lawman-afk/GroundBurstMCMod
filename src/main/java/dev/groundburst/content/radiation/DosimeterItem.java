package dev.groundburst.content.radiation;

import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The Dosimeter Badge (DESIGN.md §21.3, §21.8): a film badge carried anywhere in the inventory. Its film darkens with the
 * dose its wearer has taken (white, yellow, orange, red, black: the item's model, by the client's
 * {@code groundburst:dose} property), its tooltip gives the dose in rad and the stage of sickness it brings, and the HUD
 * shows it under the Geiger readout.
 */
public class DosimeterItem extends Item {
    static final String DOSE = GBLang.add(GroundBurst.MODID + ".tooltip.dose", "Dose taken: %s rad");
    static final String HINT = GBLang.add(GroundBurst.MODID + ".tooltip.dosimeter_hint",
            "Anywhere in your inventory: its film darkens with the dose you take.");
    /** Names of the stages of sickness by {@link RadiationState#stage()}. */
    public static final String[] STAGES = {
            GBLang.add(GroundBurst.MODID + ".dose.stage0", "no sickness"),
            GBLang.add(GroundBurst.MODID + ".dose.stage1", "Radiation Sickness I"),
            GBLang.add(GroundBurst.MODID + ".dose.stage2", "Radiation Sickness II"),
            GBLang.add(GroundBurst.MODID + ".dose.stage3", "Radiation Sickness III"),
            GBLang.add(GroundBurst.MODID + ".dose.stage4", "Radiation Sickness IV"),
            GBLang.add(GroundBurst.MODID + ".dose.stage5", "a lethal dose")};
    /** Colours of the stages, for tooltips and the HUD. */
    public static final int[] STAGE_COLOURS = {0xFFB8C4B0, 0xFFE6D45A, 0xFFE89A3C, 0xFFE0603A, 0xFFC8303A, 0xFF8A1A2A};

    /** The local player's radiation (set by the client; the tooltip shows the dose of whoever reads it). */
    public static Supplier<RadiationState> local = () -> null;

    public DosimeterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        RadiationState state = local.get();
        if (state != null) {
            int stage = state.stage();
            builder.accept(Component.translatable(DOSE, Math.round(state.dose())).withColor(STAGE_COLOURS[stage])
                    .append(Component.literal(" - ").append(Component.translatable(STAGES[stage]))));
        }
        builder.accept(Component.translatable(HINT).withColor(0xFF7A8088));
    }
}
