package dev.groundzero.compat.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembler.AssemblerClass;
import dev.groundzero.content.assembler.GZAssemblers;
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
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * JEI category for the missile assemblers (DESIGN.md §10.4): one recipe per warhead, each the warhead's own missile:
 * the assembler of its class, the warhead, its class's body, the rocket motors that fit it and the optional guidance
 * units and Hardened Electronics. A Light or Medium warhead has a second recipe, its cruise missile (§17.11): the Cruise
 * Engine in the motor's place.
 */
final class MissileCategory extends AbstractRecipeCategory<MissileCategory.Recipe> {
    /** One warhead's missile (or its cruise missile), and the assembler that builds it. */
    record Recipe(ItemStack warhead, AssemblerClass assembler, boolean cruise) {}

    static final IRecipeType<Recipe> TYPE = IRecipeType.create(GroundZero.id("missile_assembly"), Recipe.class);
    private static final int WIDTH = 156, HEIGHT = 48;

    MissileCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable(WarheadAssembly.JEI_MISSILES), gui.createDrawableItemLike(GZAssembly.MISSILE.get()), WIDTH, HEIGHT);
    }

    /** Every warhead an assembler builds into a missile, the smallest class first, each followed by its cruise missile. */
    static List<Recipe> recipes() {
        List<Recipe> out = new ArrayList<>();
        GZOrdnance.WARHEADS.stream().map(w -> new ItemStack(w.item().get())).filter(WarheadAssembly::isWarhead)
                .map(s -> {
                    SizeClass size = MissileAssembly.classOf(s);
                    AssemblerClass assembler = size == null ? null : AssemblerClass.of(size);
                    return assembler == null ? null : new Recipe(s, assembler, false);
                })
                .filter(java.util.Objects::nonNull).sorted(Comparator.comparingInt(r -> r.assembler().ordinal()))
                .forEach(r -> {
                    out.add(r);
                    if (MissileParts.Motor.CRUISE.fits(r.assembler().size)) {
                        out.add(new Recipe(r.warhead(), r.assembler(), true));
                    }
                });
        return out;
    }

    /** The motors a recipe takes: the rocket motors that fit, or the Cruise Engine. */
    private static List<MissileParts.Motor> motors(Recipe recipe) {
        return recipe.cruise() ? List.of(MissileParts.Motor.CRUISE) : MissileAssembly.rocketsFor(recipe.assembler().size);
    }

    /** The missile a recipe makes (its longest-range motor, GPS guidance): also listed in JEI, one per warhead and kind. */
    static ItemStack output(Recipe recipe) {
        return MissileAssembly.missile(new MissileSpec(recipe.warhead(), recipe.assembler().size, motors(recipe).getLast(),
                MissileParts.Guidance.GPS, false));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        SizeClass size = recipe.assembler().size;
        builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 1, 5).setStandardSlotBackground()
                .add(GZAssemblers.controller(recipe.assembler()).item().get());
        builder.addInputSlot(23, 5).setStandardSlotBackground().add(recipe.warhead());
        builder.addInputSlot(41, 5).setStandardSlotBackground().add(GZAssembly.BODIES.get(size.ordinal()).get());
        builder.addInputSlot(59, 5).setStandardSlotBackground()
                .addItemStacks(motors(recipe).stream().map(m -> new ItemStack(GZAssembly.MOTORS.get(m.ordinal()).get())).toList());
        builder.addInputSlot(77, 5).setStandardSlotBackground()
                .addItemStacks(List.of(new ItemStack(GZAssembly.INERTIAL_GUIDANCE.get()), new ItemStack(GZAssembly.GPS_GUIDANCE.get())))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(WarheadAssembly.JEI_OPTIONAL).withStyle(ChatFormatting.GRAY)));
        builder.addInputSlot(95, 5).setStandardSlotBackground().add(GZAssembly.HARDENED_ELECTRONICS.get())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(WarheadAssembly.JEI_OPTIONAL).withStyle(ChatFormatting.GRAY)));
        builder.addOutputSlot(136, 5).setOutputSlotBackground().add(output(recipe));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe recipe, IFocusGroup focuses) {
        AssemblerClass a = recipe.assembler();
        builder.addAnimatedRecipeArrowWidget(a.ticks).setPosition(114, 5);
        List<MissileParts.Motor> motors = motors(recipe);
        Component info = recipe.cruise() ? Component.translatable(WarheadAssembly.JEI_CRUISE_INFO, a.size.name(), MissileParts.Motor.CRUISE.range)
                : Component.translatable(WarheadAssembly.JEI_MISSILE_INFO, a.size.name(), motors.getFirst().range, motors.getLast().range);
        builder.addText(info, WIDTH, 10).setPosition(0, HEIGHT - 20).setColor(0xFF606060);
        builder.addText(Component.translatable(WarheadAssembly.JEI_MISSILE_POWER, a.energyPerTick, a.ticks / 20), WIDTH, 10)
                .setPosition(0, HEIGHT - 10).setColor(0xFF606060);
    }
}
