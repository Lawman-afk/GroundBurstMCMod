package dev.groundzero.compat.jei;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import dev.groundzero.content.machine.MachineText;
import dev.groundzero.content.recipe.ProcessingRecipe;
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

/**
 * JEI category for one processing machine: inputs (with counts) and an optional die on the left, an animated arrow,
 * products on the right (byproducts show their chance), and the time / power cost underneath.
 */
final class ProcessingCategory extends AbstractRecipeCategory<RecipeHolder<ProcessingRecipe>> {
    private static final int WIDTH = 132, HEIGHT = 58;
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.ROOT);

    ProcessingCategory(IRecipeHolderType<ProcessingRecipe> type, ItemLike machine, IGuiHelper gui) {
        super(type, machine.asItem().getDefaultInstance().getHoverName(), gui.createDrawableItemLike(machine), WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ProcessingRecipe> holder, IFocusGroup focuses) {
        ProcessingRecipe recipe = holder.value();
        List<SizedIngredient> inputs = recipe.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            SizedIngredient in = inputs.get(i);
            int x = 1 + (i % 2) * 18, y = 1 + (i / 2) * 18;
            builder.addInputSlot(x, y).setStandardSlotBackground().addItemStacks(stacks(in.ingredient(), in.count()));
        }
        recipe.fluid().ifPresent(fluid -> {
            IRecipeSlotBuilder slot = builder.addInputSlot(1 + 18 * Math.min(2, inputs.size()), 1).setStandardSlotBackground();
            for (var f : fluid.ingredient().fluids()) {
                slot.add(f.value(), fluid.amount());
            }
        });
        recipe.catalyst().ifPresent(die -> builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 1, 21 + ((inputs.size() - 1) / 2) * 18)
                .setStandardSlotBackground().add(die)
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(MachineText.RECIPE_NOT_CONSUMED).withStyle(ChatFormatting.GRAY))));
        for (int i = 0; i < recipe.outputs().size(); i++) {
            ProcessingRecipe.Output out = recipe.outputs().get(i);
            IRecipeSlotBuilder slot = builder.addOutputSlot(i == 0 ? 77 : 77 + 26 * i, 11);
            slot.setOutputSlotBackground().add(out.item());
            if (out.chance() < 1f) {
                int pct = Math.round(out.chance() * 100);
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(MachineText.RECIPE_CHANCE, pct).withStyle(ChatFormatting.GOLD)));
            }
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<ProcessingRecipe> holder, IFocusGroup focuses) {
        ProcessingRecipe recipe = holder.value();
        builder.addAnimatedRecipeArrowWidget(recipe.time()).setPosition(45, 11);
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
