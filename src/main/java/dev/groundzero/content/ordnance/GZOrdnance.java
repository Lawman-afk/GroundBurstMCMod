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

/** Ordnance (DESIGN.md §14): thrown explosives so far (Phase 6 slice 6a), their entities, sounds and damage type. */
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

    /** {@code groundzero:shrapnel}: what fragments deal. */
    public static final ResourceKey<DamageType> SHRAPNEL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, GroundZero.id("shrapnel"));

    public static final Supplier<SoundEvent> GRENADE_PIN = GZSounds.GRENADE_PIN;
    public static final Supplier<SoundEvent> GRENADE_BOUNCE = GZSounds.GRENADE_BOUNCE;
    public static final Supplier<SoundEvent> DYNAMITE_LIGHT = GZSounds.DYNAMITE_LIGHT;
    public static final Supplier<SoundEvent> STICKY_STICK = GZSounds.STICKY_STICK;
    public static final Supplier<SoundEvent> SMOKE_HISS = GZSounds.SMOKE_HISS;

    static {
        dev.groundzero.registry.GZLang.add("death.attack." + GroundZero.MODID + ".shrapnel", "%1$s was shredded by shrapnel");
        dev.groundzero.registry.GZLang.add("death.attack." + GroundZero.MODID + ".shrapnel.player", "%1$s was shredded by %2$s's shrapnel");
        dev.groundzero.registry.GZLang.add("entity." + GroundZero.MODID + ".thrown_explosive", "Thrown Explosive");
        dev.groundzero.registry.GZLang.add("entity." + GroundZero.MODID + ".shrapnel", "Shrapnel");
    }

    private GZOrdnance() {}

    public static void init() {}

    private static DeferredItem<GrenadeItem> grenade(GrenadeType type, String english) {
        return GZItems.register(type.id, english, GZTab.ORDNANCE, p -> new GrenadeItem(p, type), p -> p.stacksTo(16));
    }
}
