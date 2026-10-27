package dev.groundzero.content.radiation;

import java.util.List;

import dev.groundzero.content.nuclear.GZEffects;
import dev.groundzero.registry.GZDataComponents;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import dev.groundzero.registry.GZTab;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** The radiation instruments and medicine (DESIGN.md §21.8), in the Guns & Gear tab. */
public final class RadiationItems {
    public static final DeferredItem<GeigerCounterItem> GEIGER_COUNTER = GZItems.register("geiger_counter", "Geiger Counter", GZTab.GEAR,
            GeigerCounterItem::new);
    public static final DeferredItem<DosimeterItem> DOSIMETER_BADGE = GZItems.register("dosimeter_badge", "Dosimeter Badge", GZTab.GEAR,
            DosimeterItem::new);
    public static final DeferredItem<LeadContainerItem> LEAD_CONTAINER = GZItems.register("lead_container", "Lead Container", GZTab.GEAR,
            LeadContainerItem::new);

    /** Rad-Away: an IV bag hung and run in (1.6 s): 150 rad drawn out over 30 s. */
    public static final DeferredItem<Item> RAD_AWAY = GZItems.register("rad_away", "Rad-Away", GZTab.GEAR, Item::new,
            p -> p.stacksTo(16).component(DataComponents.CONSUMABLE, Consumable.builder().consumeSeconds(1.6f)
                    .animation(ItemUseAnimation.DRINK).sound(SoundEvents.HONEY_DRINK).hasConsumeParticles(false)
                    .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(GZEffects.RAD_AWAY, 600))).build()));
    /** Potassium Iodide: a tablet (0.8 s): half the dose taken up for 10 minutes. */
    public static final DeferredItem<Item> POTASSIUM_IODIDE = GZItems.register("potassium_iodide", "Potassium Iodide Tablets", GZTab.GEAR,
            Item::new, p -> p.stacksTo(32).component(DataComponents.CONSUMABLE, Consumable.builder().consumeSeconds(0.8f)
                    .animation(ItemUseAnimation.EAT).sound(SoundEvents.GENERIC_EAT).hasConsumeParticles(false)
                    .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(GZEffects.POTASSIUM_IODIDE, 12_000))).build()));
    /** The Anti-Rad Injector: pressed to the thigh (0.6 s): 400 rad off at once, a minute's weakness. */
    public static final DeferredItem<AntiRadInjectorItem> ANTI_RAD_INJECTOR = GZItems.register("anti_rad_injector", "Anti-Rad Injector",
            GZTab.GEAR, AntiRadInjectorItem::new, p -> p.stacksTo(8).component(DataComponents.CONSUMABLE, Consumable.builder()
                    .consumeSeconds(0.6f).animation(ItemUseAnimation.NONE).sound(Holder.direct(SoundEvents.EMPTY))
                    .soundAfterConsume(GZSounds.MEDICAL_INJECTOR).hasConsumeParticles(false)
                    .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(MobEffects.WEAKNESS, 1_200)))).build()));

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<LeadContainerMenu>> LEAD_CONTAINER_MENU =
            GZMenus.MENUS.register("lead_container", () -> IMenuTypeExtension.create(LeadContainerMenu::new));

    /** Recipe-viewer info pages (DESIGN.md §21.8). */
    public static final String INFO_GEIGER = dev.groundzero.registry.GZLang.add(dev.groundzero.GroundZero.MODID + ".info.geiger_counter",
            "Held in either hand, the Geiger Counter clicks faster the stronger the radiation where you stand and shows its meter in "
                    + "a corner of the screen (a needle from 0.1 to 10,000 RAD/h, and the reading); anywhere in the hotbar it clicks "
                    + "and shows the reading alone. Sneak and use it to set its alarm (1, 10, 100 or 1,000 RAD/h, or off). It runs "
                    + "on its own charge, 5 FE a second while on: charge it in a Battery Charger. An EMP knocks it out.");
    public static final String INFO_DOSIMETER = dev.groundzero.registry.GZLang.add(dev.groundzero.GroundZero.MODID + ".info.dosimeter",
            "Carried anywhere in your inventory, the Dosimeter Badge keeps the dose you have taken: its film darkens from white "
                    + "through yellow, orange and red to black, and the dose shows in rad under the Geiger reading. Sickness starts "
                    + "at 100 rad and grows worse at 200, 400 and 600; 1,000 rad is lethal. The dose falls by itself, 1 rad a minute, "
                    + "below 10 RAD/h; Rad-Away and the Anti-Rad Injector take it down faster, and the film clears with it.");
    public static final String INFO_LEAD_CONTAINER = dev.groundzero.registry.GZLang.add(dev.groundzero.GroundZero.MODID + ".info.lead_container",
            "Radioactive items you carry dose you as surely as a crater does: a stack of uranium ingots brings sickness within "
                    + "half an hour, a spent fuel rod within three minutes. Nothing inside a Lead Container reaches you. It holds nine stacks of radioactive items and nothing else; "
                    + "use it to open it.");
    public static final String INFO_MEDICINE = dev.groundzero.registry.GZLang.add(dev.groundzero.GroundZero.MODID + ".info.radiation_medicine",
            "Potassium Iodide halves the dose you take for 10 minutes: take it before going in. Rad-Away draws 150 rad out over "
                    + "30 seconds. The Anti-Rad Injector takes 400 rad off at once, but leaves you weak for a minute.");

    private RadiationItems() {}

    public static void init() {}

    public static void register(IEventBus modBus) {
        // The Geiger Counter's charge, filled by the Battery Charger (or any mod's charger) like a Battery's.
        modBus.addListener((RegisterCapabilitiesEvent event) -> event.registerItem(Capabilities.Energy.ITEM,
                (stack, access) -> new net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler(access, GZDataComponents.ENERGY.get(),
                        GeigerCounterItem.CAPACITY, GeigerCounterItem.TRANSFER), GEIGER_COUNTER.get()));
    }
}
