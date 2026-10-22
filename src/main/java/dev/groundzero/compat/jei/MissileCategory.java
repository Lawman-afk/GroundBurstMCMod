package dev.groundzero.compat.jei;

import java.util.List;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.AssemblyBenchBlockEntity;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.assembly.MissileAssembly;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.content.assembly.WarheadAssembly;
import dev.groundzero.content.ordnance.GZOrdnance;
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
 * JEI category for the Assembly Bench's missile page (DESIGN.md §10): one recipe per body, with the warheads it takes,
 * the motors that fit it, and the optional guidance units and hardened electronics, making a missile.
 */
final class MissileCategory extends AbstractRecipeCategory<MissileCategory.Recipe> {
    /** One body and what goes with it. */
    record Recipe(SizeClass body) {}

    static final IRecipeType<Recipe> TYPE = IRecipeType.create(GroundZero.id("missile_assembly"), Recipe.class);
    private static final int WIDTH = 150, HEIGHT = 40;

    MissileCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable(WarheadAssembly.JEI_MISSILES), gui.createDrawableItemLike(GZAssembly.MISSILE.get()), WIDTH, HEIGHT);
    }

    static List<Recipe> recipes() {
        return java.util.Arrays.stream(SizeClass.values()).filter(s -> s != SizeClass.XXL).map(Recipe::new).toList();   // XXL: the hall's
    }

    private static List<ItemStack> warheads(SizeClass body) {
        return GZOrdnance.WARHEADS.stream().map(w -> new ItemStack(w.item().get()))
                .filter(s -> WarheadAssembly.isWarhead(s) && MissileAssembly.fit(s, body) == MissileAssembly.Problem.NONE).toList();
    }

    private static List<MissileParts.Motor> motors(SizeClass body) {
        return java.util.Arrays.stream(MissileParts.Motor.values()).filter(m -> m.fits(body)).toList();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(1, 5).setStandardSlotBackground().addItemStacks(warheads(recipe.body()));
        builder.addInputSlot(19, 5).setStandardSlotBackground().add(GZAssembly.BODIES.get(recipe.body().ordinal()).get());
        builder.addInputSlot(37, 5).setStandardSlotBackground()
                .addItemStacks(motors(recipe.body()).stream().map(m -> new ItemStack(GZAssembly.MOTORS.get(m.ordinal()).get())).toList());
        builder.addInputSlot(55, 5).setStandardSlotBackground()
                .addItemStacks(List.of(new ItemStack(GZAssembly.INERTIAL_GUIDANCE.get()), new ItemStack(GZAssembly.GPS_GUIDANCE.get())))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(WarheadAssembly.JEI_OPTIONAL).withStyle(ChatFormatting.GRAY)));
        builder.addInputSlot(73, 5).setStandardSlotBackground().add(GZAssembly.HARDENED_ELECTRONICS.get())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(WarheadAssembly.JEI_OPTIONAL).withStyle(ChatFormatting.GRAY)));
        ItemStack warhead = warheads(recipe.body()).getLast();
        MissileParts.Motor motor = motors(recipe.body()).getLast();
        builder.addOutputSlot(128, 5).setOutputSlotBackground()
                .add(MissileAssembly.missile(new MissileSpec(warhead, recipe.body(), motor, MissileParts.Guidance.GPS, false)));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrowWidget(MissileAssembly.TICKS).setPosition(96, 5);
        List<MissileParts.Motor> motors = motors(recipe.body());
        Component info = recipe.body() == SizeClass.XL
                ? Component.translatable(WarheadAssembly.JEI_STRATEGIC_INFO, motors.getLast().range, AssemblyBenchBlockEntity.ENERGY_PER_TICK)
                : Component.translatable(WarheadAssembly.JEI_MISSILE_INFO, recipe.body().name(), motors.getFirst().range, motors.getLast().range,
                        AssemblyBenchBlockEntity.ENERGY_PER_TICK);
        builder.addText(info, WIDTH, 10).setPosition(0, HEIGHT - 10).setColor(0xFF606060);
    }
}
