package dev.groundzero.content.ordnance;

import java.util.function.Supplier;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZSounds;
import dev.groundzero.registry.GZTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Ordnance (DESIGN.md §14): thrown explosives so far (Phase 6 slice 6a), their entities, sounds, damage types and the toxin's sludge. */
public final class GZOrdnance {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(GroundZero.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownExplosive>> THROWN_EXPLOSIVE = ENTITIES.registerEntityType(
            "thrown_explosive", ThrownExplosive::new, MobCategory.MISC, b -> b.sized(0.3f, 0.3f).clientTrackingRange(6).updateInterval(5));
    public static final DeferredHolder<EntityType<?>, EntityType<Shrapnel>> SHRAPNEL = ENTITIES.registerEntityType(
            "shrapnel", Shrapnel::new, MobCategory.MISC, b -> b.sized(0.1f, 0.1f).clientTrackingRange(4).updateInterval(1).noSave());

    public static final DeferredItem<GrenadeItem> DYNAMITE = grenade(GrenadeType.DYNAMITE, "Dynamite");
    public static final DeferredItem<GrenadeItem> FRAG_GRENADE = grenade(GrenadeType.FRAG, "Frag Grenade");
    public static final DeferredItem<GrenadeItem> STICKY_GRENADE = grenade(GrenadeType.STICKY, "Sticky Grenade");
    public static final DeferredItem<GrenadeItem> INCENDIARY_GRENADE = grenade(GrenadeType.INCENDIARY, "Incendiary Grenade");
    public static final DeferredItem<GrenadeItem> SMOKE_GRENADE = grenade(GrenadeType.SMOKE, "Smoke Grenade");
    public static final DeferredItem<GrenadeItem> GAS_GRENADE = grenade(GrenadeType.POISON_GAS, "Poison Gas Grenade");
    public static final DeferredItem<GrenadeItem> TOXIN_GRENADE = grenade(GrenadeType.TOXIN, "Toxin Grenade");
    public static final DeferredItem<GrenadeItem> MOLOTOV_COCKTAIL = grenade(GrenadeType.MOLOTOV, "Molotov Cocktail");

    /** The film of toxin a toxin grenade leaves on the ground. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<ToxicSludgeBlock> TOXIC_SLUDGE = dev.groundzero.registry.GZBlocks.blockOnly(
            "toxic_sludge", "Toxic Sludge", ToxicSludgeBlock::new,
            p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GREEN).replaceable().noCollision().noOcclusion()
                    .instabreak().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)
                    .sound(net.minecraft.world.level.block.SoundType.SLIME_BLOCK).emissiveRendering(s -> true)
                    .lightLevel(s -> 7 - s.getValue(ToxicSludgeBlock.AGE)));

    /** {@code groundzero:shrapnel}: what fragments deal. */
    public static final ResourceKey<DamageType> SHRAPNEL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, GroundZero.id("shrapnel"));
    /** {@code groundzero:toxin}: poison gas, toxin and toxic chemicals (see {@link Toxins}). */
    public static final ResourceKey<DamageType> TOXIN_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, GroundZero.id("toxin"));

    public static final Supplier<SoundEvent> GRENADE_PIN = GZSounds.GRENADE_PIN;
    public static final Supplier<SoundEvent> DYNAMITE_LIGHT = GZSounds.DYNAMITE_LIGHT;
    public static final Supplier<SoundEvent> STICKY_STICK = GZSounds.STICKY_STICK;
    public static final Supplier<SoundEvent> SMOKE_HISS = GZSounds.SMOKE_HISS;

    static {
        dev.groundzero.registry.GZLang.add("death.attack." + GroundZero.MODID + ".shrapnel", "%1$s was shredded by shrapnel");
        dev.groundzero.registry.GZLang.add("death.attack." + GroundZero.MODID + ".shrapnel.player", "%1$s was shredded by %2$s's shrapnel");
        dev.groundzero.registry.GZLang.add("death.attack." + GroundZero.MODID + ".toxin", "%1$s succumbed to toxic chemicals");
        dev.groundzero.registry.GZLang.add("death.attack." + GroundZero.MODID + ".toxin.player", "%1$s succumbed to %2$s's toxic chemicals");
        dev.groundzero.registry.GZLang.add("entity." + GroundZero.MODID + ".thrown_explosive", "Thrown Explosive");
        dev.groundzero.registry.GZLang.add("entity." + GroundZero.MODID + ".shrapnel", "Shrapnel");
    }

    private GZOrdnance() {}

    public static void init() {}

    /**
     * Smoke (and gas) clouds hide whoever is in them: a mob notices someone inside one, or sees out of one, at a tenth
     * of its usual range (a zombie's 16 blocks becomes under 2), on top of the cloud breaking targets it already has.
     */
    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingEvent.LivingVisibilityEvent e) -> {
            var seen = e.getEntity();
            var looker = e.getLookingEntity();
            if (seen.level().isClientSide()) {
                return;
            }
            if (ThrownExplosive.inCloud(seen.level(), seen.getEyePosition()) || looker != null && ThrownExplosive.inCloud(looker.level(), looker.getEyePosition())) {
                e.modifyVisibility(0.1);
            }
        });
    }

    private static DeferredItem<GrenadeItem> grenade(GrenadeType type, String english) {
        return GZItems.register(type.id, english, GZTab.ORDNANCE, p -> new GrenadeItem(p, type), p -> p.stacksTo(16));
    }
}
