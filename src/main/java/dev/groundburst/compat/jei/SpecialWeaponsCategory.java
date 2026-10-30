package dev.groundburst.compat.jei;

import java.util.List;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.GBAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.WarheadAssembly;
import dev.groundburst.content.strategic.GBStrategic;
import dev.groundburst.content.strategic.SpecialWeapons;
import dev.groundburst.content.strategic.SpecialWeaponsBlockEntity;
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
 * JEI category for the Special Weapons Assembler (DESIGN.md §16.15): an exotic core (an Antimatter Trap, a Singularity
 * Core or a Storm Core) and an Intercontinental Booster, with the guidance and Hardened Electronics optional, make that
 * weapon's intercontinental missile. Laid out like the assembler's screen.
 */
final class SpecialWeaponsCategory extends AbstractRecipeCategory<SpecialWeaponsCategory.Recipe> {
    /** One weapon the assembler builds, from its core. */
    record Recipe(ItemStack core) {}

    static final IRecipeType<Recipe> TYPE = IRecipeType.create(GroundBurst.id("special_weapons"), Recipe.class);
    private static final int WIDTH = 150, HEIGHT = 50;

    SpecialWeaponsCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable(SpecialWeapons.JEI_TITLE), gui.createDrawableItemLike(GBStrategic.SPECIAL_CONTROLLER.item().get()),
                WIDTH, HEIGHT);
    }

    static List<Recipe> recipes() {
        return SpecialWeapons.cores().stream().map(Recipe::new).toList();
    }

    /** The missile a recipe makes (with GPS guidance): also listed in JEI, one per warhead, for its recipe. */
    static ItemStack output(Recipe recipe) {
        return SpecialWeapons.assemble(recipe.core(), new ItemStack(GBAssembly.GPS_GUIDANCE.get()), ItemStack.EMPTY);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(19, 1).setStandardSlotBackground().add(recipe.core());
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
        builder.addAnimatedRecipeArrowWidget(SpecialWeapons.TICKS).setPosition(63, 11);
        Component info = Component.translatable(dev.groundburst.content.strategic.StrategicAssembly.JEI_INFO, MissileParts.Motor.ICBM.range,
                SpecialWeaponsBlockEntity.ENERGY_PER_TICK);
        builder.addText(info, WIDTH, 10).setPosition(0, HEIGHT - 10).setColor(0xFF606060);
    }
}
