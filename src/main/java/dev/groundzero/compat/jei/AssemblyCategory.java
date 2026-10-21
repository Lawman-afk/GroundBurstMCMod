package dev.groundzero.compat.jei;

import java.util.List;
import java.util.Locale;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.AssemblyBenchBlockEntity;
import dev.groundzero.content.assembly.ComponentItems;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.assembly.WarheadAssembly;
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
 * JEI category for the Ordnance Assembly Bench (DESIGN.md §10): a payload core, a casing of its class and any fuze
 * (the five cycle) make the core's warhead. Underneath: the class, the time and the power.
 */
final class AssemblyCategory extends AbstractRecipeCategory<AssemblyCategory.Recipe> {
    /** One warhead: its core and the casing it takes. */
    record Recipe(ComponentItems.PayloadCore core, ComponentItems.Casing casing) {}

    static final IRecipeType<Recipe> TYPE = IRecipeType.create(GroundZero.id("assembly"), Recipe.class);
    private static final String T_INFO = WarheadAssembly.JEI_INFO, T_ANY_FUZE = WarheadAssembly.JEI_ANY_FUZE;
    private static final int WIDTH = 130, HEIGHT = 40;

    AssemblyCategory(IGuiHelper gui) {
        super(TYPE, GZAssembly.ASSEMBLY_BENCH.item().get().getDefaultInstance().getHoverName(),
                gui.createDrawableItemLike(GZAssembly.ASSEMBLY_BENCH.item().get()), WIDTH, HEIGHT);
    }

    /** Every warhead the bench makes, one per payload core. */
    static List<Recipe> recipes() {
        return GZAssembly.CORES.stream().map(c -> new Recipe(c.get(), WarheadAssembly.casing(c.get().sizeClass))).toList();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.core());
        builder.addInputSlot(19, 5).setStandardSlotBackground().add(recipe.casing());
        builder.addInputSlot(37, 5).setStandardSlotBackground()
                .addItemStacks(GZAssembly.FUZES.stream().map(f -> new ItemStack(f.get())).toList())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(T_ANY_FUZE).withStyle(ChatFormatting.GRAY)));
        builder.addOutputSlot(100, 5).setOutputSlotBackground().add(recipe.core().warhead());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe recipe, IFocusGroup focuses) {
        int ticks = WarheadAssembly.assemblyTicks(recipe.core().sizeClass);
        builder.addAnimatedRecipeArrowWidget(ticks).setPosition(66, 5);
        builder.addText(Component.translatable(T_INFO, recipe.core().sizeClass.name(), String.format(Locale.ROOT, "%.0f", ticks / 20f),
                AssemblyBenchBlockEntity.ENERGY_PER_TICK), WIDTH, 10).setPosition(0, HEIGHT - 10).setColor(0xFF606060);
    }
}
