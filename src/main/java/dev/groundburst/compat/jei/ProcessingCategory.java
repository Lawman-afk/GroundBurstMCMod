package dev.groundburst.compat.jei;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import dev.groundburst.content.machine.MachineText;
import dev.groundburst.content.recipe.ProcessingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * JEI category for one processing machine: item and fluid inputs (with amounts) and an optional die on the left, an
 * animated arrow, item and fluid products on the right (byproducts show their chance), and the time / power cost
 * underneath.
 */
final class ProcessingCategory extends AbstractRecipeCategory<RecipeHolder<ProcessingRecipe>> {
    private static final int WIDTH = 150, HEIGHT = 66;
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.ROOT);

    ProcessingCategory(IRecipeHolderType<ProcessingRecipe> type, ItemLike machine, IGuiHelper gui) {
        super(type, machine.asItem().getDefaultInstance().getHoverName(), gui.createDrawableItemLike(machine), WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ProcessingRecipe> holder, IFocusGroup focuses) {
        ProcessingRecipe recipe = holder.value();
        // Left: item inputs, then fluid inputs, then the catalyst, two to a row.
        int cell = 0;
        for (SizedIngredient in : recipe.inputs()) {
            builder.addInputSlot(cellX(cell), cellY(cell)).setStandardSlotBackground().addItemStacks(stacks(in.ingredient(), in.count()));
            cell++;
        }
        for (SizedFluidIngredient fluid : recipe.fluids()) {
            IRecipeSlotBuilder slot = builder.addInputSlot(cellX(cell), cellY(cell)).setStandardSlotBackground();
            // Tags hold flowing variants too; showing them would make the slot blink through an invisible fluid.
            for (var f : fluid.ingredient().fluids()) {
                if (f.value().isSource(f.value().defaultFluidState())) {
                    slot.add(f.value(), fluid.amount());
                }
            }
            cell++;
        }
        if (recipe.catalyst().isPresent()) {
            builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, cellX(cell), cellY(cell)).setStandardSlotBackground().add(recipe.catalyst().get())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(MachineText.RECIPE_NOT_CONSUMED).withStyle(ChatFormatting.GRAY)));
            cell++;
        }
        // Right: item products, then fluid products, in a row level with the arrow.
        int y = arrowY(cell);
        int x = 77;
        for (ProcessingRecipe.Output out : recipe.outputs()) {
            IRecipeSlotBuilder slot = builder.addOutputSlot(x, y);
            slot.setOutputSlotBackground().add(out.item());
            if (out.chance() < 1f) {
                int pct = Math.round(out.chance() * 100);
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(MachineText.RECIPE_CHANCE, pct).withStyle(ChatFormatting.GOLD)));
            }
            x += 26;
        }
        for (FluidStackTemplate out : recipe.fluidOutputs()) {
            builder.addOutputSlot(x, y).setOutputSlotBackground().add(out.fluid().value(), out.amount());
            x += 26;
        }
    }

    private static int cellX(int cell) {
        return 1 + (cell % 2) * 18;
    }

    private static int cellY(int cell) {
        return 1 + (cell / 2) * 18;
    }

    /** The arrow (and products) sit level with the middle of the input block. */
    private static int arrowY(int cells) {
        int rows = Math.max(1, (cells + 1) / 2);
        return rows >= 3 ? 19 : rows == 2 ? 11 : 1;
    }

    private static int cellCount(ProcessingRecipe recipe) {
        return recipe.inputs().size() + recipe.fluids().size() + (recipe.catalyst().isPresent() ? 1 : 0);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<ProcessingRecipe> holder, IFocusGroup focuses) {
        ProcessingRecipe recipe = holder.value();
        builder.addAnimatedRecipeArrowWidget(recipe.time()).setPosition(45, arrowY(cellCount(recipe)));
        String seconds = String.format(Locale.ROOT, "%.1f", recipe.time() / 20f);
        builder.addText(Component.translatable(MachineText.RECIPE_TIME_ENERGY, seconds, NUM.format(recipe.energyPerTick())), WIDTH, 10)
                .setPosition(0, HEIGHT - 10)
                .setColor(0xFF606060)
                .setTooltip(Component.translatable(MachineText.RECIPE_TOTAL_ENERGY, NUM.format((long) recipe.time() * recipe.energyPerTick())));
    }

    private static List<ItemStack> stacks(Ingredient ingredient, int count) {
        return ingredient.items().map(item -> new ItemStack(item, count)).toList();
    }
}
