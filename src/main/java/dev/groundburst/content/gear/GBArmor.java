package dev.groundburst.content.gear;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Protective suits (DESIGN.md §21.5). "Hazmat" (hazardous materials) covers radioactive contamination as well as
 * chemicals: a sealed suit keeps radioactive dust off the skin and, with its filter, out of the lungs, but does little
 * against penetrating radiation; lead plates stop much more of it, at the cost of weight.
 *
 * <ul>
 * <li><b>Hazmat Suit</b> (yellow plastic, filter mask): 75% radiation protection as a full set; little armour.</li>
 * <li><b>Lead-Lined Hazmat Suit</b> (orange, lead-plated): 92%, a little more armour, 5% slower as a full set.</li>
 * </ul>
 * Pieces share the set's protection: hood 20%, coat 35%, trousers 30%, boots 15%. Radiation Sickness from
 * contamination is reduced by it ({@link #radiationProtection}); the radiation field of Phase 12 will use the same.
 */
public final class GBArmor {
    public static final TagKey<Item> REPAIRS_HAZMAT = TagKey.create(Registries.ITEM, GroundBurst.id("repairs_hazmat"));
    public static final TagKey<Item> REPAIRS_LEAD_HAZMAT = TagKey.create(Registries.ITEM, GroundBurst.id("repairs_lead_lined_hazmat"));
    public static final ResourceKey<EquipmentAsset> HAZMAT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, GroundBurst.id("hazmat"));
    public static final ResourceKey<EquipmentAsset> LEAD_HAZMAT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, GroundBurst.id("lead_lined_hazmat"));

    public static final ArmorMaterial HAZMAT = new ArmorMaterial(10, defense(1, 2, 2, 1), 10, SoundEvents.ARMOR_EQUIP_LEATHER, 0f, 0f,
            REPAIRS_HAZMAT, HAZMAT_ASSET);
    public static final ArmorMaterial LEAD_HAZMAT = new ArmorMaterial(18, defense(1, 3, 4, 2), 8, SoundEvents.ARMOR_EQUIP_IRON, 0f, 0.05f,
            REPAIRS_LEAD_HAZMAT, LEAD_HAZMAT_ASSET);

    /** Share of a full set's protection each piece gives. */
    private static final Map<ArmorType, Float> SHARE = Map.of(ArmorType.HELMET, 0.20f, ArmorType.CHESTPLATE, 0.35f,
            ArmorType.LEGGINGS, 0.30f, ArmorType.BOOTS, 0.15f);
    /** Radiation protection each piece gives (its share of its set's), by item once they exist, and as registered. */
    private static final Map<Item, Float> PROTECTION = new IdentityHashMap<>();
    private static final Map<DeferredItem<Item>, Float> PENDING = new java.util.LinkedHashMap<>();

    public static final DeferredItem<Item> HAZMAT_HOOD = piece("hazmat_hood", "Hazmat Hood", HAZMAT, ArmorType.HELMET, 0.75f, 0);
    public static final DeferredItem<Item> HAZMAT_COAT = piece("hazmat_coat", "Hazmat Coat", HAZMAT, ArmorType.CHESTPLATE, 0.75f, 0);
    public static final DeferredItem<Item> HAZMAT_TROUSERS = piece("hazmat_trousers", "Hazmat Trousers", HAZMAT, ArmorType.LEGGINGS, 0.75f, 0);
    public static final DeferredItem<Item> HAZMAT_BOOTS = piece("hazmat_boots", "Hazmat Boots", HAZMAT, ArmorType.BOOTS, 0.75f, 0);
    public static final DeferredItem<Item> LEAD_HAZMAT_HOOD = piece("lead_lined_hazmat_hood", "Lead-Lined Hazmat Hood", LEAD_HAZMAT,
            ArmorType.HELMET, 0.92f, 0.05f);
    public static final DeferredItem<Item> LEAD_HAZMAT_COAT = piece("lead_lined_hazmat_coat", "Lead-Lined Hazmat Coat", LEAD_HAZMAT,
            ArmorType.CHESTPLATE, 0.92f, 0.05f);
    public static final DeferredItem<Item> LEAD_HAZMAT_TROUSERS = piece("lead_lined_hazmat_trousers", "Lead-Lined Hazmat Trousers", LEAD_HAZMAT,
            ArmorType.LEGGINGS, 0.92f, 0.05f);
    public static final DeferredItem<Item> LEAD_HAZMAT_BOOTS = piece("lead_lined_hazmat_boots", "Lead-Lined Hazmat Boots", LEAD_HAZMAT,
            ArmorType.BOOTS, 0.92f, 0.05f);

    public static final List<DeferredItem<Item>> HAZMAT_SET = List.of(HAZMAT_HOOD, HAZMAT_COAT, HAZMAT_TROUSERS, HAZMAT_BOOTS);
    public static final List<DeferredItem<Item>> LEAD_HAZMAT_SET = List.of(LEAD_HAZMAT_HOOD, LEAD_HAZMAT_COAT, LEAD_HAZMAT_TROUSERS, LEAD_HAZMAT_BOOTS);

    private GBArmor() {}

    public static void init() {}

    private static Map<ArmorType, Integer> defense(int boots, int legs, int chest, int helm) {
        Map<ArmorType, Integer> m = new EnumMap<>(ArmorType.class);
        m.put(ArmorType.BOOTS, boots);
        m.put(ArmorType.LEGGINGS, legs);
        m.put(ArmorType.CHESTPLATE, chest);
        m.put(ArmorType.HELMET, helm);
        m.put(ArmorType.BODY, 0);
        return m;
    }

    /**
     * One suit piece: vanilla armour properties, plus (for heavy suits) a share of the set's slowdown on the movement
     * speed, and its share of the set's radiation protection.
     */
    private static DeferredItem<Item> piece(String name, String english, ArmorMaterial material, ArmorType type, float protection, float slowdown) {
        float share = protection * SHARE.get(type);
        DeferredItem<Item> item = GBItems.register(name, english, GBTab.GEAR, p -> new SuitPiece(p, share, protection, slowdown), p -> {
            Item.Properties props = p.humanoidArmor(material, type);
            if (slowdown > 0) {
                var attributes = material.createAttributes(type).withModifierAdded(Attributes.MOVEMENT_SPEED,
                        new AttributeModifier(GroundBurst.id("suit_weight." + type.getName()), -slowdown * SHARE.get(type),
                                AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        EquipmentSlotGroup.bySlot(type.getSlot()));
                props.attributes(attributes);
            }
            return props;
        });
        PENDING.put(item, share);
        return item;
    }

    private static final String TIP_RADIATION = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".tooltip.radiation_protection",
            "Radiation protection: %s%% (%s%% as a full set)");
    private static final String TIP_CHEMICAL = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".tooltip.chemical_protection",
            "Chemical protection: %s%% (full set: proof against gas and toxins)");
    private static final String TIP_WEIGHT = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".tooltip.suit_weight",
            "Heavy: %s%% slower as a full set");

    /** A suit piece: its tooltip gives its share of the set's protection, and the set's figure. */
    private static final class SuitPiece extends Item {
        private final float share, set, slowdown;

        SuitPiece(Properties properties, float share, float set, float slowdown) {
            super(properties);
            this.share = share;
            this.set = set;
            this.slowdown = slowdown;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
            var green = net.minecraft.ChatFormatting.GREEN;
            tooltip.accept(net.minecraft.network.chat.Component.translatable(TIP_RADIATION, Math.round(this.share * 100), Math.round(this.set * 100))
                    .withStyle(green));
            tooltip.accept(net.minecraft.network.chat.Component.translatable(TIP_CHEMICAL, Math.round(this.share / this.set * 100)).withStyle(green));
            if (this.slowdown > 0) {
                tooltip.accept(net.minecraft.network.chat.Component.translatable(TIP_WEIGHT, Math.round(this.slowdown * 100))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }

    /**
     * How much of a chemical (gas, toxin) reaching {@code entity} its suit keeps out: the pieces' shares of a sealed suit
     * (hood 20%, coat 35%, trousers 30%, boots 15%); a full set of either suit is proof against it.
     */
    public static float chemicalProtection(LivingEntity entity) {
        if (PROTECTION.isEmpty()) {
            PENDING.forEach((item, value) -> PROTECTION.put(item.get(), value));
        }
        float total = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            Item item = entity.getItemBySlot(slot).getItem();
            if (PROTECTION.containsKey(item) && item instanceof SuitPiece piece) {
                total += piece.share / piece.set;
            }
        }
        return Math.min(1, total);
    }

    /** How much of the radiation reaching {@code entity} its suit stops (0 to 0.92). */
    public static float radiationProtection(LivingEntity entity) {
        if (PROTECTION.isEmpty()) {
            PENDING.forEach((item, value) -> PROTECTION.put(item.get(), value));
        }
        float total = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            Float value = PROTECTION.get(stack.getItem());
            if (value != null) {
                total += value;
            }
        }
        return Math.min(0.98f, total);
    }
}
