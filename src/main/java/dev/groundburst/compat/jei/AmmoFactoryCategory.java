package dev.groundburst.compat.jei;

import java.util.Arrays;
import java.util.List;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.superweapon.AmmoFactoryBlockEntity;
import dev.groundburst.content.superweapon.GBSuperweapons;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * JEI category for the superweapons' ammunition factories (DESIGN.md §19.5): the Arms Dealer's Toxin SCUD and the War
 * Factory's Nuclear Missile, each from its own parts. Laid out like the factories' screens: the parts in two rows of
 * three, the arrow, the round; how long it takes and the power it draws under them.
 */
final class AmmoFactoryCategory extends AbstractRecipeCategory<AmmoFactoryBlockEntity.Kind> {
    static final IRecipeType<AmmoFactoryBlockEntity.Kind> TYPE = IRecipeType.create(GroundBurst.id("ammo_factory"), AmmoFactoryBlockEntity.Kind.class);
    static final String TITLE = GBSuperweapons.JEI_AMMO_TITLE, INFO = GBSuperweapons.JEI_AMMO_INFO;
    private static final int WIDTH = 150, HEIGHT = 52;

    AmmoFactoryCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable(TITLE), gui.createDrawableItemLike(GBSuperweapons.ARMS_DEALER_CONTROLLER.item().get()), WIDTH, HEIGHT);
    }

    static List<AmmoFactoryBlockEntity.Kind> recipes() {
        return Arrays.asList(AmmoFactoryBlockEntity.Kind.values());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AmmoFactoryBlockEntity.Kind kind, IFocusGroup focuses) {
        for (int i = 0; i < kind.inputs.size(); i++) {
            AmmoFactoryBlockEntity.Input in = kind.inputs.get(i);
            builder.addInputSlot(1 + (i % 3) * 18, 1 + (i / 3) * 20).setStandardSlotBackground().add(new ItemStack(in.item().get(), in.count()));
        }
        builder.addOutputSlot(100, 11).setOutputSlotBackground().add(new ItemStack(kind.output()));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, AmmoFactoryBlockEntity.Kind kind, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrowWidget(kind.ticks).setPosition(66, 11);
        Component where = (kind == AmmoFactoryBlockEntity.Kind.ARMS_DEALER ? GBSuperweapons.ARMS_DEALER_CONTROLLER : GBSuperweapons.WAR_FACTORY_CONTROLLER)
                .block().get().getName();
        builder.addText(Component.translatable(INFO, where, kind.ticks / 20, kind.energyPerTick), WIDTH, 10).setPosition(0, HEIGHT - 10)
                .setColor(0xFF606060);
    }
}
