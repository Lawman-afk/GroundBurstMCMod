package dev.groundburst.content.ordnance;

import java.util.function.Supplier;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBSounds;
import dev.groundburst.registry.GBTab;
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
public final class GBOrdnance {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(GroundBurst.MODID);

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
    public static final DeferredItem<GrenadeItem> CRYO_GRENADE = grenade(GrenadeType.CRYO, "Cryo Grenade");
    public static final DeferredItem<GrenadeItem> EMP_GRENADE = grenade(GrenadeType.EMP, "EMP Grenade");
    public static final DeferredItem<GrenadeItem> MOLOTOV_COCKTAIL = grenade(GrenadeType.MOLOTOV, "Molotov Cocktail");
    /** A cluster bomb's bomblet (thrown, it goes off on impact like any grenade). */
    public static final DeferredItem<GrenadeItem> CLUSTER_BOMBLET = grenade(GrenadeType.BOMBLET, "Cluster Bomblet");

    /** The film of toxin a toxin grenade leaves on the ground. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<ToxicSludgeBlock> TOXIC_SLUDGE = dev.groundburst.registry.GBBlocks.blockOnly(
            "toxic_sludge", "Toxic Sludge", ToxicSludgeBlock::new,
            p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GREEN).replaceable().noCollision().noOcclusion()
                    .instabreak().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)
                    .sound(net.minecraft.world.level.block.SoundType.SLIME_BLOCK).emissiveRendering(s -> true)
                    .lightLevel(s -> 7 - s.getValue(ToxicSludgeBlock.AGE)));

    /** The glowing residue a toxin SCUD spreads over the ground (DESIGN.md §19.5). */
    public static final net.neoforged.neoforge.registries.DeferredBlock<AnthraxResidueBlock> ANTHRAX_RESIDUE = dev.groundburst.registry.GBBlocks.blockOnly(
            "anthrax_residue", "Anthrax Residue", AnthraxResidueBlock::new,
            p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GREEN).replaceable().noCollision().noOcclusion()
                    .instabreak().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)
                    .sound(net.minecraft.world.level.block.SoundType.SLIME_BLOCK).emissiveRendering(s -> true)
                    .lightLevel(s -> 10 - 2 * s.getValue(ToxicSludgeBlock.AGE)));

    // --- placed charges (slice 6b) ---
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> C4 =
            dev.groundburst.registry.GBBlocks.register("c4", "C4 Charge", GBTab.ORDNANCE, dev.groundburst.content.ordnance.charge.ChargeBlock.C4::new,
                    p -> p.mapColor(net.minecraft.world.level.material.MapColor.SAND).strength(0.5f).noOcclusion()
                            .sound(net.minecraft.world.level.block.SoundType.SLIME_BLOCK).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> BREACHING_CHARGE =
            dev.groundburst.registry.GBBlocks.register("breaching_charge", "Breaching Charge", GBTab.ORDNANCE,
                    p -> new dev.groundburst.content.ordnance.charge.ChargeBlock(dev.groundburst.content.ordnance.charge.ChargeKind.BREACHING, p),
                    p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_GRAY).strength(1f).noOcclusion()
                            .sound(net.minecraft.world.level.block.SoundType.METAL).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> THERMITE_CHARGE =
            dev.groundburst.registry.GBBlocks.register("thermite_charge", "Thermite Charge", GBTab.ORDNANCE,
                    dev.groundburst.content.ordnance.charge.ChargeBlock.Thermite::new,
                    p -> p.mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_ORANGE).strength(1f).noOcclusion()
                            .sound(net.minecraft.world.level.block.SoundType.DECORATED_POT).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)
                            .lightLevel(s -> s.getValue(dev.groundburst.content.ordnance.charge.ChargeBlock.LIT) ? 15 : 0));
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> TIMED_BOMB =
            dev.groundburst.registry.GBBlocks.register("timed_bomb", "Timed Bomb", GBTab.ORDNANCE,
                    p -> new dev.groundburst.content.ordnance.charge.ChargeBlock(dev.groundburst.content.ordnance.charge.ChargeKind.TIMED_BOMB, p),
                    p -> p.mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(2f).noOcclusion()
                            .sound(net.minecraft.world.level.block.SoundType.METAL).pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE));
    // --- mines (slice 6c) ---
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> LANDMINE = mine("landmine",
            "Landmine", dev.groundburst.content.ordnance.charge.ChargeKind.LANDMINE);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> BOUNCING_MINE = mine("bouncing_mine",
            "Bouncing Mine", dev.groundburst.content.ordnance.charge.ChargeKind.BOUNCING_MINE);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> CLAYMORE = mine("claymore",
            "Claymore", dev.groundburst.content.ordnance.charge.ChargeKind.CLAYMORE);

    // --- warheads (slice 6d; bombs until they became missile warheads) ---
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> HE_WARHEAD = bomb("he_warhead",
            "HE Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.HE_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> HEAVY_WARHEAD = bomb("heavy_warhead",
            "Heavy HE Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.HEAVY_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> THERMOBARIC_WARHEAD = bomb(
            "thermobaric_warhead", "Thermobaric Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.THERMOBARIC_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> CLUSTER_WARHEAD = bomb("cluster_warhead",
            "Cluster Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.CLUSTER_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> BUNKER_BUSTER = bomb("bunker_buster_warhead",
            "Bunker Buster Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.BUNKER_BUSTER);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> NAPALM_WARHEAD = bomb("napalm_warhead",
            "Napalm Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.NAPALM_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> MASSIVE_WARHEAD = bomb("massive_warhead",
            "Massive Ordnance Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.MASSIVE_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> GAS_WARHEAD = bomb("gas_warhead",
            "Poison Gas Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.GAS_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> SMOKE_WARHEAD = bomb("smoke_warhead",
            "Smoke Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.SMOKE_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> CRYO_WARHEAD = bomb("cryo_warhead",
            "Cryo Cluster Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.CRYO_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> EMP_WARHEAD = bomb("emp_warhead",
            "EMP Cluster Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.EMP_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> TOXIC_WASTE_WARHEAD = bomb(
            "toxic_waste_warhead", "Toxic Waste Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.TOXIC_WASTE_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> CHEMICAL_WARHEAD = bomb(
            "chemical_warhead", "Chemical Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.CHEMICAL_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> TACTICAL_NUCLEAR_WARHEAD = bomb(
            "tactical_nuclear_warhead", "Tactical Nuclear Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.TACTICAL_NUCLEAR_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> FISSION_WARHEAD = bomb(
            "fission_warhead", "Fission Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.FISSION_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> BOOSTED_FISSION_WARHEAD = bomb(
            "boosted_fission_warhead", "Boosted Fission Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.BOOSTED_FISSION_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> NEUTRON_WARHEAD = bomb(
            "neutron_warhead", "Neutron Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.NEUTRON_WARHEAD);
    /** Slice 8c: only ever built whole into an intercontinental missile (the Strategic Assembly Hall). */
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> THERMONUCLEAR_WARHEAD = bomb(
            "thermonuclear_warhead", "Thermonuclear Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.THERMONUCLEAR_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> COBALT_WARHEAD = bomb(
            "cobalt_warhead", "Cobalt Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.COBALT_WARHEAD);
    /** Slice 8d: the Tsar, built whole like the thermonuclear warhead. */
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> TSAR_WARHEAD = bomb(
            "tsar_warhead", "Tsar Mega-bomb", dev.groundburst.content.ordnance.charge.ChargeKind.TSAR_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> DIRTY_WARHEAD = bomb(
            "dirty_warhead", "Dirty Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.DIRTY_WARHEAD);
    /** DESIGN.md §19.5: the superweapons' warheads, only ever built whole into their ammunition. */
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> SCUD_STORM_WARHEAD = bomb(
            "scud_storm_warhead", "Toxin SCUD Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.SCUD_STORM_WARHEAD);
    public static final dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> NUCLEAR_MISSILE_WARHEAD = bomb(
            "nuclear_missile_warhead", "Nuclear Missile Warhead", dev.groundburst.content.ordnance.charge.ChargeKind.NUCLEAR_MISSILE_WARHEAD);
    /** Every warhead, in the order the creative tab and the Field Manual show them. */
    public static final java.util.List<dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock>> WARHEADS =
            java.util.List.of(HE_WARHEAD, HEAVY_WARHEAD, THERMOBARIC_WARHEAD, CLUSTER_WARHEAD, BUNKER_BUSTER, NAPALM_WARHEAD, MASSIVE_WARHEAD,
                    GAS_WARHEAD, SMOKE_WARHEAD, CRYO_WARHEAD, EMP_WARHEAD, TOXIC_WASTE_WARHEAD, CHEMICAL_WARHEAD, DIRTY_WARHEAD, TACTICAL_NUCLEAR_WARHEAD,
                    FISSION_WARHEAD, BOOSTED_FISSION_WARHEAD, NEUTRON_WARHEAD, THERMONUCLEAR_WARHEAD, COBALT_WARHEAD, TSAR_WARHEAD,
                    SCUD_STORM_WARHEAD, NUCLEAR_MISSILE_WARHEAD);
    /** Burning napalm from a napalm warhead. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<NapalmFireBlock> NAPALM_FIRE = dev.groundburst.registry.GBBlocks.blockOnly(
            "napalm_fire", "Napalm Fire", NapalmFireBlock::new,
            p -> p.mapColor(net.minecraft.world.level.material.MapColor.FIRE).replaceable().noCollision().instabreak().noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED).sound(net.minecraft.world.level.block.SoundType.WOOL)
                    .lightLevel(s -> 15).emissiveRendering(s -> true));

    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<dev.groundburst.content.ordnance.charge.ChargeBlockEntity>> CHARGE =
            dev.groundburst.registry.GBBlockEntities.BLOCK_ENTITIES.register("charge", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    dev.groundburst.content.ordnance.charge.ChargeBlockEntity::new, C4.block().get(), BREACHING_CHARGE.block().get(),
                    THERMITE_CHARGE.block().get(), TIMED_BOMB.block().get(), LANDMINE.block().get(), BOUNCING_MINE.block().get(),
                    CLAYMORE.block().get(), HE_WARHEAD.block().get(), HEAVY_WARHEAD.block().get(), THERMOBARIC_WARHEAD.block().get(),
                    CLUSTER_WARHEAD.block().get(), BUNKER_BUSTER.block().get(), NAPALM_WARHEAD.block().get(), MASSIVE_WARHEAD.block().get(),
                    GAS_WARHEAD.block().get(), SMOKE_WARHEAD.block().get(), CRYO_WARHEAD.block().get(), EMP_WARHEAD.block().get(),
                    TOXIC_WASTE_WARHEAD.block().get(),
                    CHEMICAL_WARHEAD.block().get(), TACTICAL_NUCLEAR_WARHEAD.block().get(), FISSION_WARHEAD.block().get(),
                    BOOSTED_FISSION_WARHEAD.block().get(), NEUTRON_WARHEAD.block().get(), DIRTY_WARHEAD.block().get(),
                    THERMONUCLEAR_WARHEAD.block().get(), COBALT_WARHEAD.block().get(), TSAR_WARHEAD.block().get(),
                    SCUD_STORM_WARHEAD.block().get(), NUCLEAR_MISSILE_WARHEAD.block().get()));
    public static final DeferredItem<dev.groundburst.content.ordnance.charge.RemoteDetonatorItem> REMOTE_DETONATOR = GBItems.register("remote_detonator",
            "Remote Detonator", GBTab.ORDNANCE, dev.groundburst.content.ordnance.charge.RemoteDetonatorItem::new, p -> p.stacksTo(1));

    /** {@code groundburst:shrapnel}: what fragments deal. */
    public static final ResourceKey<DamageType> SHRAPNEL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, GroundBurst.id("shrapnel"));
    /** {@code groundburst:toxin}: poison gas, toxin and toxic chemicals (see {@link Toxins}). */
    public static final ResourceKey<DamageType> TOXIN_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, GroundBurst.id("toxin"));

    public static final Supplier<SoundEvent> GRENADE_PIN = GBSounds.GRENADE_PIN;
    public static final Supplier<SoundEvent> DYNAMITE_LIGHT = GBSounds.DYNAMITE_LIGHT;
    public static final Supplier<SoundEvent> STICKY_STICK = GBSounds.STICKY_STICK;
    public static final Supplier<SoundEvent> SMOKE_HISS = GBSounds.SMOKE_HISS;
    public static final Supplier<SoundEvent> CHARGE_ARM = GBSounds.CHARGE_ARM;
    public static final Supplier<SoundEvent> CHARGE_DISARM = GBSounds.CHARGE_DISARM;
    public static final Supplier<SoundEvent> BOMB_BEEP = GBSounds.BOMB_BEEP;
    public static final Supplier<SoundEvent> THERMITE_BURN = GBSounds.THERMITE_BURN;
    public static final Supplier<SoundEvent> DETONATOR_CLICK = GBSounds.DETONATOR_CLICK;
    public static final Supplier<SoundEvent> WIRE_SNIP = GBSounds.WIRE_SNIP;
    public static final Supplier<SoundEvent> MINE_CLICK = GBSounds.MINE_CLICK;
    public static final Supplier<SoundEvent> MINE_POP = GBSounds.MINE_POP;
    public static final Supplier<SoundEvent> FUEL_DISPERSE = GBSounds.FUEL_DISPERSE;
    public static final Supplier<SoundEvent> BOMB_DRILL = GBSounds.BOMB_DRILL;
    public static final Supplier<SoundEvent> BOMB_WHISTLE = GBSounds.BOMB_WHISTLE;

    static {
        dev.groundburst.registry.GBLang.add("death.attack." + GroundBurst.MODID + ".shrapnel", "%1$s was shredded by shrapnel");
        dev.groundburst.registry.GBLang.add("death.attack." + GroundBurst.MODID + ".shrapnel.player", "%1$s was shredded by %2$s's shrapnel");
        dev.groundburst.registry.GBLang.add("death.attack." + GroundBurst.MODID + ".toxin", "%1$s succumbed to toxic chemicals");
        dev.groundburst.registry.GBLang.add("death.attack." + GroundBurst.MODID + ".toxin.player", "%1$s succumbed to %2$s's toxic chemicals");
        dev.groundburst.registry.GBLang.add("entity." + GroundBurst.MODID + ".thrown_explosive", "Thrown Explosive");
        dev.groundburst.registry.GBLang.add("entity." + GroundBurst.MODID + ".shrapnel", "Shrapnel");
    }

    private GBOrdnance() {}

    private static dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> bomb(String id, String english,
            dev.groundburst.content.ordnance.charge.ChargeKind kind) {
        return dev.groundburst.registry.GBBlocks.registerWithItem(id, english, GBTab.ORDNANCE,
                p -> (dev.groundburst.content.ordnance.charge.ChargeBlock) new dev.groundburst.content.ordnance.charge.BombBlock(kind, p),
                p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN).strength(2f).noOcclusion()
                        .sound(net.minecraft.world.level.block.SoundType.METAL).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED),
                dev.groundburst.content.assembly.WarheadItem::new);
    }

    private static dev.groundburst.registry.GBBlocks.WithItem<dev.groundburst.content.ordnance.charge.ChargeBlock> mine(String id, String english,
            dev.groundburst.content.ordnance.charge.ChargeKind kind) {
        return dev.groundburst.registry.GBBlocks.register(id, english, GBTab.ORDNANCE, p -> new dev.groundburst.content.ordnance.charge.ChargeBlock(kind, p),
                p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN).strength(1f).noOcclusion()
                        .sound(net.minecraft.world.level.block.SoundType.METAL).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
    }

    public static void init() {}

    /**
     * Smoke (and gas) clouds hide whoever is in them: a mob notices someone inside one, or sees out of one, at a tenth
     * of its usual range (a zombie's 16 blocks becomes under 2), on top of the cloud breaking targets it already has.
     */
    public static void register() {
        dev.groundburst.content.ordnance.charge.Charges.register();
        ToxinSpreads.register();
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
        return GBItems.register(type.id, english, GBTab.ORDNANCE, p -> new GrenadeItem(p, type), p -> p.stacksTo(16));
    }
}
