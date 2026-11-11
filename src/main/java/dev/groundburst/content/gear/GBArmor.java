package dev.groundburst.content.gear;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.eventbus.api.IEventBus;
import dev.groundburst.legacy.DeferredItem;

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
 * contamination is reduced by it ({@link #radiationProtection}).
 * <p>
 * The 1.20.1 backport's: the materials are 1.20.1's armour materials (an interface, not yet a registry: {@link Suit}),
 * the pieces are armour items, and the suits' textures are the {@code textures/models/armor} layers the 1.21.1 build
 * copies from main's equipment textures.
 */
public final class GBArmor {
    public static final TagKey<Item> REPAIRS_HAZMAT = TagKey.create(Registries.ITEM, GroundBurst.id("repairs_hazmat"));
    public static final TagKey<Item> REPAIRS_LEAD_HAZMAT = TagKey.create(Registries.ITEM, GroundBurst.id("repairs_lead_lined_hazmat"));

    /** Durability, as main's materials have it (times each piece's own factor). */
    private static final int HAZMAT_DURABILITY = 10, LEAD_HAZMAT_DURABILITY = 18;

    /** The two suits' materials (1.20.1's armour material: its textures named by getName, groundburst:textures/models/armor). */
    private enum Suit implements ArmorMaterial {
        HAZMAT_SUIT("hazmat", HAZMAT_DURABILITY, defense(1, 2, 2, 1), 10, SoundEvents.ARMOR_EQUIP_LEATHER, REPAIRS_HAZMAT, 0f, 0f),
        LEAD_HAZMAT_SUIT("lead_lined_hazmat", LEAD_HAZMAT_DURABILITY, defense(1, 3, 4, 2), 8, SoundEvents.ARMOR_EQUIP_IRON, REPAIRS_LEAD_HAZMAT,
                0f, 0.05f);

        private final String name;
        private final int durability;
        private final Map<ArmorItem.Type, Integer> defense;
        private final int enchantment;
        private final net.minecraft.sounds.SoundEvent sound;
        private final TagKey<Item> repairs;
        private final float toughness, knockback;

        Suit(String name, int durability, Map<ArmorItem.Type, Integer> defense, int enchantment, net.minecraft.sounds.SoundEvent sound,
                TagKey<Item> repairs, float toughness, float knockback) {
            this.name = name;
            this.durability = durability;
            this.defense = defense;
            this.enchantment = enchantment;
            this.sound = sound;
            this.repairs = repairs;
            this.toughness = toughness;
            this.knockback = knockback;
        }

        /** Each piece's durability: the game's own factor for its type (1.20.1 keeps it to its own materials) times the suit's. */
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            int factor = switch (type) {
                case HELMET -> 11;
                case CHESTPLATE -> 16;
                case LEGGINGS -> 15;
                case BOOTS -> 13;
            };
            return factor * this.durability;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return this.defense.getOrDefault(type, 0);
        }

        @Override
        public int getEnchantmentValue() {
            return this.enchantment;
        }

        @Override
        public net.minecraft.sounds.SoundEvent getEquipSound() {
            return this.sound;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(this.repairs);
        }

        @Override
        public String getName() {
            return GroundBurst.MODID + ":" + this.name;
        }

        @Override
        public float getToughness() {
            return this.toughness;
        }

        @Override
        public float getKnockbackResistance() {
            return this.knockback;
        }
    }

    public static final ArmorMaterial HAZMAT = Suit.HAZMAT_SUIT;
    public static final ArmorMaterial LEAD_HAZMAT = Suit.LEAD_HAZMAT_SUIT;

    /** Share of a full set's protection each piece gives. */
    private static final Map<ArmorItem.Type, Float> SHARE = Map.of(ArmorItem.Type.HELMET, 0.20f, ArmorItem.Type.CHESTPLATE, 0.35f,
            ArmorItem.Type.LEGGINGS, 0.30f, ArmorItem.Type.BOOTS, 0.15f);
    /** Radiation protection each piece gives (its share of its set's), by item once they exist, and as registered. */
    private static final Map<Item, Float> PROTECTION = new IdentityHashMap<>();
    private static final Map<DeferredItem<Item>, Float> PENDING = new java.util.LinkedHashMap<>();

    public static final DeferredItem<Item> HAZMAT_HOOD = piece("hazmat_hood", "Hazmat Hood", HAZMAT, HAZMAT_DURABILITY, ArmorItem.Type.HELMET, 0.75f, 0);
    public static final DeferredItem<Item> HAZMAT_COAT = piece("hazmat_coat", "Hazmat Coat", HAZMAT, HAZMAT_DURABILITY, ArmorItem.Type.CHESTPLATE, 0.75f, 0);
    public static final DeferredItem<Item> HAZMAT_TROUSERS = piece("hazmat_trousers", "Hazmat Trousers", HAZMAT, HAZMAT_DURABILITY,
            ArmorItem.Type.LEGGINGS, 0.75f, 0);
    public static final DeferredItem<Item> HAZMAT_BOOTS = piece("hazmat_boots", "Hazmat Boots", HAZMAT, HAZMAT_DURABILITY, ArmorItem.Type.BOOTS, 0.75f, 0);
    public static final DeferredItem<Item> LEAD_HAZMAT_HOOD = piece("lead_lined_hazmat_hood", "Lead-Lined Hazmat Hood", LEAD_HAZMAT,
            LEAD_HAZMAT_DURABILITY, ArmorItem.Type.HELMET, 0.92f, 0.05f);
    public static final DeferredItem<Item> LEAD_HAZMAT_COAT = piece("lead_lined_hazmat_coat", "Lead-Lined Hazmat Coat", LEAD_HAZMAT,
            LEAD_HAZMAT_DURABILITY, ArmorItem.Type.CHESTPLATE, 0.92f, 0.05f);
    public static final DeferredItem<Item> LEAD_HAZMAT_TROUSERS = piece("lead_lined_hazmat_trousers", "Lead-Lined Hazmat Trousers", LEAD_HAZMAT,
            LEAD_HAZMAT_DURABILITY, ArmorItem.Type.LEGGINGS, 0.92f, 0.05f);
    public static final DeferredItem<Item> LEAD_HAZMAT_BOOTS = piece("lead_lined_hazmat_boots", "Lead-Lined Hazmat Boots", LEAD_HAZMAT,
            LEAD_HAZMAT_DURABILITY, ArmorItem.Type.BOOTS, 0.92f, 0.05f);

    public static final List<DeferredItem<Item>> HAZMAT_SET = List.of(HAZMAT_HOOD, HAZMAT_COAT, HAZMAT_TROUSERS, HAZMAT_BOOTS);
    public static final List<DeferredItem<Item>> LEAD_HAZMAT_SET = List.of(LEAD_HAZMAT_HOOD, LEAD_HAZMAT_COAT, LEAD_HAZMAT_TROUSERS, LEAD_HAZMAT_BOOTS);

    private GBArmor() {}

    public static void init() {}

    /** Nothing to register on 1.20.1 (its armour materials are no registry): kept for the mod's start. */
    public static void register(IEventBus modBus) {
    }

    private static Map<ArmorItem.Type, Integer> defense(int boots, int legs, int chest, int helm) {
        Map<ArmorItem.Type, Integer> m = new EnumMap<>(ArmorItem.Type.class);
        m.put(ArmorItem.Type.BOOTS, boots);
        m.put(ArmorItem.Type.LEGGINGS, legs);
        m.put(ArmorItem.Type.CHESTPLATE, chest);
        m.put(ArmorItem.Type.HELMET, helm);
        return m;
    }

    /**
     * One suit piece: vanilla armour, plus (for heavy suits) a share of the set's slowdown on the movement speed, and its
     * share of the set's radiation protection. (Its durability is its material's, for its type.)
     */
    private static DeferredItem<Item> piece(String name, String english, ArmorMaterial material, int durability, ArmorItem.Type type,
            float protection, float slowdown) {
        float share = protection * SHARE.get(type);
        DeferredItem<Item> item = GBItems.register(name, english, GBTab.GEAR, p -> new SuitPiece(material, type, p, share, protection, slowdown),
                p -> p);
        PENDING.put(item, share);
        return item;
    }

    private static final String TIP_RADIATION = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".tooltip.radiation_protection",
            "Radiation protection: %s%% (%s%% as a full set)");
    private static final String TIP_CHEMICAL = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".tooltip.chemical_protection",
            "Chemical protection: %s%% (full set: proof against gas and toxins)");
    private static final String TIP_WEIGHT = dev.groundburst.registry.GBLang.add(GroundBurst.MODID + ".tooltip.suit_weight",
            "Heavy: %s%% slower as a full set");

    /** A suit piece: its tooltip gives its share of the set's protection, and the set's figure; a heavy one slows. */
    private static final class SuitPiece extends ArmorItem {
        private final float share, set, slowdown;

        SuitPiece(ArmorMaterial material, Type type, Properties properties, float share, float set, float slowdown) {
            super(material, type, properties);
            this.share = share;
            this.set = set;
            this.slowdown = slowdown;
        }

        @Override
        @SuppressWarnings("deprecation")
        public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier> getDefaultAttributeModifiers(
                EquipmentSlot slot) {
            var base = super.getDefaultAttributeModifiers(slot);
            if (this.slowdown <= 0 || slot != getType().getSlot()) {
                return base;
            }
            com.google.common.collect.ImmutableMultimap.Builder<net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier> out =
                    com.google.common.collect.ImmutableMultimap.builder();
            out.putAll(base);
            out.put(Attributes.MOVEMENT_SPEED, dev.groundburst.legacy.Attrs.modifier(GroundBurst.id("suit_weight." + getType().getName()),
                    -this.slowdown * SHARE.get(getType()), AttributeModifier.Operation.MULTIPLY_BASE));
            return out.build();
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context_level, List<Component> tooltip, TooltipFlag flag) { dev.groundburst.legacy.TooltipContext context = dev.groundburst.legacy.TooltipContext.of(context_level);
            var green = net.minecraft.ChatFormatting.GREEN;
            tooltip.add(Component.translatable(TIP_RADIATION, Math.round(this.share * 100), Math.round(this.set * 100)).withStyle(green));
            tooltip.add(Component.translatable(TIP_CHEMICAL, Math.round(this.share / this.set * 100)).withStyle(green));
            if (this.slowdown > 0) {
                tooltip.add(Component.translatable(TIP_WEIGHT, Math.round(this.slowdown * 100)).withStyle(net.minecraft.ChatFormatting.GRAY));
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
            if (PROTECTION.containsKey(item) && ((Object) item) instanceof SuitPiece piece) {
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
