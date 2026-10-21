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
    /** A cluster bomb's bomblet (thrown, it goes off on impact like any grenade). */
    public static final DeferredItem<GrenadeItem> CLUSTER_BOMBLET = grenade(GrenadeType.BOMBLET, "Cluster Bomblet");

    /** The film of toxin a toxin grenade leaves on the ground. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<ToxicSludgeBlock> TOXIC_SLUDGE = dev.groundzero.registry.GZBlocks.blockOnly(
            "toxic_sludge", "Toxic Sludge", ToxicSludgeBlock::new,
            p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GREEN).replaceable().noCollision().noOcclusion()
                    .instabreak().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)
                    .sound(net.minecraft.world.level.block.SoundType.SLIME_BLOCK).emissiveRendering(s -> true)
                    .lightLevel(s -> 7 - s.getValue(ToxicSludgeBlock.AGE)));

    // --- placed charges (slice 6b) ---
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> C4 =
            dev.groundzero.registry.GZBlocks.register("c4", "C4 Charge", GZTab.ORDNANCE, dev.groundzero.content.ordnance.charge.ChargeBlock.C4::new,
                    p -> p.mapColor(net.minecraft.world.level.material.MapColor.SAND).strength(0.5f).noOcclusion()
                            .sound(net.minecraft.world.level.block.SoundType.SLIME_BLOCK).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> BREACHING_CHARGE =
            dev.groundzero.registry.GZBlocks.register("breaching_charge", "Breaching Charge", GZTab.ORDNANCE,
                    p -> new dev.groundzero.content.ordnance.charge.ChargeBlock(dev.groundzero.content.ordnance.charge.ChargeKind.BREACHING, p),
                    p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_GRAY).strength(1f).noOcclusion()
                            .sound(net.minecraft.world.level.block.SoundType.METAL).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> THERMITE_CHARGE =
            dev.groundzero.registry.GZBlocks.register("thermite_charge", "Thermite Charge", GZTab.ORDNANCE,
                    dev.groundzero.content.ordnance.charge.ChargeBlock.Thermite::new,
                    p -> p.mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_ORANGE).strength(1f).noOcclusion()
                            .sound(net.minecraft.world.level.block.SoundType.DECORATED_POT).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)
                            .lightLevel(s -> s.getValue(dev.groundzero.content.ordnance.charge.ChargeBlock.LIT) ? 15 : 0));
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> TIMED_BOMB =
            dev.groundzero.registry.GZBlocks.register("timed_bomb", "Timed Bomb", GZTab.ORDNANCE,
                    p -> new dev.groundzero.content.ordnance.charge.ChargeBlock(dev.groundzero.content.ordnance.charge.ChargeKind.TIMED_BOMB, p),
                    p -> p.mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(2f).noOcclusion()
                            .sound(net.minecraft.world.level.block.SoundType.METAL).pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE));
    // --- mines (slice 6c) ---
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> LANDMINE = mine("landmine",
            "Landmine", dev.groundzero.content.ordnance.charge.ChargeKind.LANDMINE);
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> BOUNCING_MINE = mine("bouncing_mine",
            "Bouncing Mine", dev.groundzero.content.ordnance.charge.ChargeKind.BOUNCING_MINE);
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> CLAYMORE = mine("claymore",
            "Claymore", dev.groundzero.content.ordnance.charge.ChargeKind.CLAYMORE);

    // --- warheads (slice 6d; bombs until they became missile warheads) ---
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> HE_WARHEAD = bomb("he_warhead",
            "HE Warhead", dev.groundzero.content.ordnance.charge.ChargeKind.HE_WARHEAD);
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> HEAVY_WARHEAD = bomb("heavy_warhead",
            "Heavy HE Warhead", dev.groundzero.content.ordnance.charge.ChargeKind.HEAVY_WARHEAD);
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> THERMOBARIC_WARHEAD = bomb(
            "thermobaric_warhead", "Thermobaric Warhead", dev.groundzero.content.ordnance.charge.ChargeKind.THERMOBARIC_WARHEAD);
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> CLUSTER_WARHEAD = bomb("cluster_warhead",
            "Cluster Warhead", dev.groundzero.content.ordnance.charge.ChargeKind.CLUSTER_WARHEAD);
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> BUNKER_BUSTER = bomb("bunker_buster_warhead",
            "Bunker Buster Warhead", dev.groundzero.content.ordnance.charge.ChargeKind.BUNKER_BUSTER);
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> NAPALM_WARHEAD = bomb("napalm_warhead",
            "Napalm Warhead", dev.groundzero.content.ordnance.charge.ChargeKind.NAPALM_WARHEAD);
    public static final dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> MASSIVE_WARHEAD = bomb("massive_warhead",
            "Massive Ordnance Warhead", dev.groundzero.content.ordnance.charge.ChargeKind.MASSIVE_WARHEAD);
    /** Burning napalm from a napalm warhead. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<NapalmFireBlock> NAPALM_FIRE = dev.groundzero.registry.GZBlocks.blockOnly(
            "napalm_fire", "Napalm Fire", NapalmFireBlock::new,
            p -> p.mapColor(net.minecraft.world.level.material.MapColor.FIRE).replaceable().noCollision().instabreak().noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED).sound(net.minecraft.world.level.block.SoundType.WOOL)
                    .lightLevel(s -> 15).emissiveRendering(s -> true));

    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<dev.groundzero.content.ordnance.charge.ChargeBlockEntity>> CHARGE =
            dev.groundzero.registry.GZBlockEntities.BLOCK_ENTITIES.register("charge", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    dev.groundzero.content.ordnance.charge.ChargeBlockEntity::new, C4.block().get(), BREACHING_CHARGE.block().get(),
                    THERMITE_CHARGE.block().get(), TIMED_BOMB.block().get(), LANDMINE.block().get(), BOUNCING_MINE.block().get(),
                    CLAYMORE.block().get(), HE_WARHEAD.block().get(), HEAVY_WARHEAD.block().get(), THERMOBARIC_WARHEAD.block().get(),
                    CLUSTER_WARHEAD.block().get(), BUNKER_BUSTER.block().get(), NAPALM_WARHEAD.block().get(), MASSIVE_WARHEAD.block().get()));
    public static final DeferredItem<dev.groundzero.content.ordnance.charge.RemoteDetonatorItem> REMOTE_DETONATOR = GZItems.register("remote_detonator",
            "Remote Detonator", GZTab.ORDNANCE, dev.groundzero.content.ordnance.charge.RemoteDetonatorItem::new, p -> p.stacksTo(1));

    /** {@code groundzero:shrapnel}: what fragments deal. */
    public static final ResourceKey<DamageType> SHRAPNEL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, GroundZero.id("shrapnel"));
    /** {@code groundzero:toxin}: poison gas, toxin and toxic chemicals (see {@link Toxins}). */
    public static final ResourceKey<DamageType> TOXIN_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, GroundZero.id("toxin"));

    public static final Supplier<SoundEvent> GRENADE_PIN = GZSounds.GRENADE_PIN;
    public static final Supplier<SoundEvent> DYNAMITE_LIGHT = GZSounds.DYNAMITE_LIGHT;
    public static final Supplier<SoundEvent> STICKY_STICK = GZSounds.STICKY_STICK;
    public static final Supplier<SoundEvent> SMOKE_HISS = GZSounds.SMOKE_HISS;
    public static final Supplier<SoundEvent> CHARGE_ARM = GZSounds.CHARGE_ARM;
    public static final Supplier<SoundEvent> CHARGE_DISARM = GZSounds.CHARGE_DISARM;
    public static final Supplier<SoundEvent> BOMB_BEEP = GZSounds.BOMB_BEEP;
    public static final Supplier<SoundEvent> THERMITE_BURN = GZSounds.THERMITE_BURN;
    public static final Supplier<SoundEvent> DETONATOR_CLICK = GZSounds.DETONATOR_CLICK;
    public static final Supplier<SoundEvent> WIRE_SNIP = GZSounds.WIRE_SNIP;
    public static final Supplier<SoundEvent> MINE_CLICK = GZSounds.MINE_CLICK;
    public static final Supplier<SoundEvent> MINE_POP = GZSounds.MINE_POP;
    public static final Supplier<SoundEvent> FUEL_DISPERSE = GZSounds.FUEL_DISPERSE;
    public static final Supplier<SoundEvent> BOMB_DRILL = GZSounds.BOMB_DRILL;
    public static final Supplier<SoundEvent> BOMB_WHISTLE = GZSounds.BOMB_WHISTLE;

    static {
        dev.groundzero.registry.GZLang.add("death.attack." + GroundZero.MODID + ".shrapnel", "%1$s was shredded by shrapnel");
        dev.groundzero.registry.GZLang.add("death.attack." + GroundZero.MODID + ".shrapnel.player", "%1$s was shredded by %2$s's shrapnel");
        dev.groundzero.registry.GZLang.add("death.attack." + GroundZero.MODID + ".toxin", "%1$s succumbed to toxic chemicals");
        dev.groundzero.registry.GZLang.add("death.attack." + GroundZero.MODID + ".toxin.player", "%1$s succumbed to %2$s's toxic chemicals");
        dev.groundzero.registry.GZLang.add("entity." + GroundZero.MODID + ".thrown_explosive", "Thrown Explosive");
        dev.groundzero.registry.GZLang.add("entity." + GroundZero.MODID + ".shrapnel", "Shrapnel");
    }

    private GZOrdnance() {}

    private static dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> bomb(String id, String english,
            dev.groundzero.content.ordnance.charge.ChargeKind kind) {
        return dev.groundzero.registry.GZBlocks.register(id, english, GZTab.ORDNANCE, p -> new dev.groundzero.content.ordnance.charge.BombBlock(kind, p),
                p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN).strength(2f).noOcclusion()
                        .sound(net.minecraft.world.level.block.SoundType.METAL).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
    }

    private static dev.groundzero.registry.GZBlocks.WithItem<dev.groundzero.content.ordnance.charge.ChargeBlock> mine(String id, String english,
            dev.groundzero.content.ordnance.charge.ChargeKind kind) {
        return dev.groundzero.registry.GZBlocks.register(id, english, GZTab.ORDNANCE, p -> new dev.groundzero.content.ordnance.charge.ChargeBlock(kind, p),
                p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN).strength(1f).noOcclusion()
                        .sound(net.minecraft.world.level.block.SoundType.METAL).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
    }

    public static void init() {}

    /**
     * Smoke (and gas) clouds hide whoever is in them: a mob notices someone inside one, or sees out of one, at a tenth
     * of its usual range (a zombie's 16 blocks becomes under 2), on top of the cloud breaking targets it already has.
     */
    public static void register() {
        dev.groundzero.content.ordnance.charge.Charges.register();
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
