package dev.groundburst.compat.jei;

import java.util.List;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.GBAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.WarheadAssembly;
import dev.groundburst.content.strategic.GBStrategic;
import dev.groundburst.content.strategic.StrategicAssembly;
import dev.groundburst.content.strategic.StrategicAssemblyBlockEntity;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * JEI category for the ICBM Assembly Hall (DESIGN.md §15.2): a Boosted Fission Core, a Fusion Secondary and an
 * Intercontinental Booster (with the guidance and Hardened Electronics optional) make an intercontinental missile with
 * a Thermonuclear Warhead; a Cobalt-59 Jacket as well makes it a Cobalt Warhead, a Tertiary Stage (with a Twin Boosted
 * Core for the primary) the Tsar Mega-bomb. An Antimatter Trap or a Singularity Core and the booster alone make an
 * exotic one.
 * Laid out like the hall's screen.
 */
final class StrategicCategory extends AbstractRecipeCategory<StrategicCategory.Recipe> {
    enum Weapon { THERMONUCLEAR, COBALT, TSAR, ANTIMATTER, SINGULARITY, STORM }

    /** One weapon the hall builds. */
    record Recipe(Weapon weapon) {
        /** What goes in the jacket slot for it (nothing for the thermonuclear missile). */
        ItemStack option() {
            return switch (this.weapon) {
                case COBALT -> new ItemStack(GBStrategic.COBALT_JACKET.get());
                case TSAR -> new ItemStack(GBStrategic.TERTIARY_STAGE.get());
                default -> ItemStack.EMPTY;
            };
        }

        /** What goes in the primary slot for it. */
        ItemStack primary() {
            return switch (this.weapon) {
                case ANTIMATTER -> StrategicAssembly.exoticCores().get(0);
                case SINGULARITY -> StrategicAssembly.exoticCores().get(1);
                case STORM -> StrategicAssembly.exoticCores().get(2);
                default -> StrategicAssembly.primaryFor(option());
            };
        }

        boolean exotic() {
            return this.weapon == Weapon.ANTIMATTER || this.weapon == Weapon.SINGULARITY || this.weapon == Weapon.STORM;
        }
    }

    static final IRecipeType<Recipe> TYPE = IRecipeType.create(GroundBurst.id("strategic_assembly"), Recipe.class);
    private static final int WIDTH = 150, HEIGHT = 50;

    StrategicCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable(StrategicAssembly.JEI_TITLE), gui.createDrawableItemLike(GBStrategic.HALL_CONTROLLER.item().get()),
                WIDTH, HEIGHT);
    }

    static List<Recipe> recipes() {
        return java.util.Arrays.stream(Weapon.values()).map(Recipe::new).toList();
    }

    /** The missile a recipe makes (with GPS guidance): also listed in JEI, one per warhead, for its recipe. */
    static ItemStack output(Recipe recipe) {
        return StrategicAssembly.assemble(recipe.primary(), recipe.option(), new ItemStack(GBAssembly.GPS_GUIDANCE.get()), ItemStack.EMPTY);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        ItemStack option = recipe.option();
        builder.addInputSlot(1, 1).setStandardSlotBackground().add(recipe.primary());
        if (!recipe.exotic()) {
            builder.addInputSlot(19, 1).setStandardSlotBackground().add(GBStrategic.FUSION_SECONDARY.get());
        }
        if (!option.isEmpty()) {
            builder.addInputSlot(37, 1).setStandardSlotBackground().add(option);
        }
        builder.addInputSlot(1, 21).setStandardSlotBackground().add(GBStrategic.INTERCONTINENTAL_BOOSTER.get());
        builder.addInputSlot(19, 21).setStandardSlotBackground()
                .addItemStacks(List.of(new ItemStack(GBAssembly.INERTIAL_GUIDANCE.get()), new ItemStack(GBAssembly.GPS_GUIDANCE.get())))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(WarheadAssembly.JEI_OPTIONAL).withStyle(ChatFormatting.GRAY)));
        builder.addInputSlot(37, 21).setStandardSlotBackground().add(GBAssembly.HARDENED_ELECTRONICS.get())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(WarheadAssembly.JEI_OPTIONAL).withStyle(ChatFormatting.GRAY)));
        builder.addOutputSlot(100, 11).setOutputSlotBackground().add(output(recipe));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrowWidget(StrategicAssembly.ticks(recipe.primary(), recipe.option())).setPosition(63, 11);
        Component info = Component.translatable(StrategicAssembly.JEI_INFO, MissileParts.Motor.ICBM.range, StrategicAssemblyBlockEntity.ENERGY_PER_TICK);
        builder.addText(info, WIDTH, 10).setPosition(0, HEIGHT - 10).setColor(0xFF606060);
    }
}
