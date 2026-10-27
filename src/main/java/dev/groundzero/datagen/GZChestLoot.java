package dev.groundzero.datagen;

import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.chem.GZChemicals;
import dev.groundzero.content.fallout.GZFallout;
import dev.groundzero.content.gear.GZArmor;
import dev.groundzero.content.nuclear.GZNuclear;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.part.GZParts;
import dev.groundzero.content.radiation.RadiationItems;
import dev.groundzero.content.resource.GZResources;
import dev.groundzero.content.salvage.GZSalvage;
import dev.groundzero.world.structure.GZStructures;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemDamageFunction;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * The loot of the world's old sites (DESIGN.md §22, Phase 15; no blueprints or lore notes, at the user's word): what each
 * kind of room would have held, decades on. Rare parts and salvage are rare; ordinary stores are common; gear found is
 * worn (damaged). A spent fuel rod in the waste store is deadly to carry (a Lead Container is in the same store, now
 * and then).
 */
public final class GZChestLoot implements LootTableSubProvider {
    private final LootTableSubProvider.Context output;

    public GZChestLoot(LootTableSubProvider.Context output) {
        this.output = output;
    }

    private static UniformContainerBase.Builder<?> item(ItemLike item, int weight, int min, int max) {
        var b = LootItem.lootTableItem(item).setWeight(weight);
        return max > 1 ? b.apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max))) : b;
    }

    private static UniformContainerBase.Builder<?> worn(ItemLike item, int weight) {
        return LootItem.lootTableItem(item).setWeight(weight).apply(SetItemDamageFunction.setDamage(ContextFloatProviders.between(0.25f, 0.75f)));
    }

    private static UniformContainerBase.Builder<?> nothing(int weight) {
        return EmptyLootItem.emptyItem().setWeight(weight);
    }

    private void table(String name, LootPool.Builder... pools) {
        LootTable.Builder t = LootTable.lootTable();
        for (LootPool.Builder p : pools) {
            t.withPool(p);
        }
        this.output.accept(GZStructures.loot(name), t);
    }

    private static LootPool.Builder pool(int min, int max) {
        return LootPool.lootPool().setRolls(min == max ? ContextIntProviders.exactly(min) : ContextIntProviders.between(min, max));
    }

    @Override
    public void run() {
        // A silo's launch control centre: the crew's consoles stripped, their stores and their instruments.
        table("silo_control",
                pool(1, 2).add(item(GZParts.BASIC_CIRCUIT, 30, 1, 3)).add(item(GZParts.ADVANCED_CIRCUIT, 15, 1, 2))
                        .add(item(GZParts.MILITARY_CIRCUIT, 5, 1, 1)).add(item(GZAssembly.INERTIAL_GUIDANCE, 4, 1, 1))
                        .add(item(GZAssembly.HARDENED_ELECTRONICS, 3, 1, 1)).add(item(GZAssembly.GPS_GUIDANCE, 1, 1, 1)),
                pool(1, 2).add(item(RadiationItems.DOSIMETER_BADGE, 10, 1, 1)).add(item(RadiationItems.RAD_AWAY, 10, 1, 2))
                        .add(item(RadiationItems.POTASSIUM_IODIDE, 12, 1, 3)).add(item(RadiationItems.GEIGER_COUNTER, 4, 1, 1))
                        .add(nothing(10)),
                pool(2, 5).add(item(Items.PAPER, 20, 2, 6)).add(item(Items.REDSTONE, 15, 2, 6)).add(item(GZParts.COPPER_WIRE, 15, 2, 6))
                        .add(item(Items.BREAD, 15, 1, 3)).add(item(Items.BAKED_POTATO, 10, 1, 3)).add(item(GZParts.STEEL_PLATE, 10, 1, 3))
                        .add(item(Items.IRON_INGOT, 10, 1, 4)).add(item(Items.TORCH, 8, 2, 6)));
        // The foot of a silo's shaft: what fell from the missile and its stage, the warhead most of all.
        table("silo_shaft",
                pool(1, 1).add(item(GZSalvage.DAMAGED_WARHEAD, 3, 1, 1)).add(nothing(2)),
                pool(1, 3).add(item(GZParts.STEEL_PLATE, 20, 2, 5)).add(item(GZParts.DENSE_STEEL_PLATE, 10, 1, 2))
                        .add(item(GZChemicals.SOLID_PROPELLANT, 12, 1, 4)).add(item(GZAssembly.SRM_I, 4, 1, 1))
                        .add(item(GZParts.SERVO, 8, 1, 2)).add(item(GZParts.MILITARY_CIRCUIT, 4, 1, 1)).add(item(GZAssembly.INERTIAL_GUIDANCE, 3, 1, 1)));
        // A command bunker's armoury.
        table("bunker_armory",
                pool(2, 4).add(item(GZOrdnance.FRAG_GRENADE, 20, 2, 6)).add(item(GZOrdnance.SMOKE_GRENADE, 15, 1, 4))
                        .add(item(GZOrdnance.DYNAMITE, 15, 2, 6)).add(item(GZOrdnance.C4.item(), 8, 1, 3))
                        .add(item(GZOrdnance.LANDMINE.item(), 8, 1, 3)).add(item(GZOrdnance.CLAYMORE.item(), 6, 1, 2))
                        .add(item(GZOrdnance.REMOTE_DETONATOR, 4, 1, 1)).add(item(GZOrdnance.GAS_GRENADE, 6, 1, 3))
                        .add(item(Items.GUNPOWDER, 12, 2, 8)),
                pool(0, 2).add(worn(GZArmor.HAZMAT_SET.get(0).get(), 4)).add(worn(GZArmor.HAZMAT_SET.get(1).get(), 4))
                        .add(worn(GZArmor.HAZMAT_SET.get(2).get(), 4)).add(worn(GZArmor.HAZMAT_SET.get(3).get(), 4))
                        .add(worn(Items.IRON_HELMET, 3)).add(worn(Items.CROSSBOW, 3)).add(item(Items.ARROW, 6, 4, 12)));
        // A command bunker's stores: food, medicine, instruments, the radio room's spares.
        table("bunker_stores",
                pool(3, 6).add(item(Items.BREAD, 20, 2, 5)).add(item(Items.COOKED_BEEF, 10, 1, 3)).add(item(Items.BAKED_POTATO, 12, 2, 4))
                        .add(item(Items.APPLE, 10, 1, 3)).add(item(Items.DRIED_KELP, 8, 3, 8)).add(item(Items.PAPER, 10, 2, 6))
                        .add(item(Items.CANDLE, 8, 1, 3)).add(item(Items.TORCH, 10, 3, 8)).add(item(Items.MAP, 3, 1, 1)),
                pool(1, 3).add(item(RadiationItems.RAD_AWAY, 12, 1, 3)).add(item(RadiationItems.POTASSIUM_IODIDE, 15, 2, 4))
                        .add(item(RadiationItems.ANTI_RAD_INJECTOR, 5, 1, 1)).add(item(RadiationItems.DOSIMETER_BADGE, 10, 1, 2))
                        .add(item(RadiationItems.GEIGER_COUNTER, 3, 1, 1)).add(item(GZParts.BATTERY, 6, 1, 2))
                        .add(item(GZParts.BASIC_CIRCUIT, 8, 1, 3)).add(item(GZParts.ADVANCED_CIRCUIT, 4, 1, 1)));
        // A radar outpost's equipment shed and generator shed.
        table("radar_outpost",
                pool(2, 4).add(item(GZParts.BASIC_CIRCUIT, 20, 1, 4)).add(item(GZParts.ADVANCED_CIRCUIT, 10, 1, 2))
                        .add(item(GZParts.SENSOR_ARRAY, 3, 1, 1)).add(item(GZParts.COPPER_WIRE, 20, 3, 8)).add(item(GZParts.ELECTRIC_MOTOR, 8, 1, 2))
                        .add(item(GZParts.SERVO, 8, 1, 2)).add(item(Items.COPPER_INGOT, 12, 2, 6)).add(item(Items.REDSTONE, 15, 2, 8))
                        .add(item(GZParts.BATTERY, 6, 1, 1)),
                pool(1, 3).add(item(Items.BREAD, 15, 1, 3)).add(item(Items.COAL, 15, 2, 8)).add(item(Items.IRON_INGOT, 10, 1, 4))
                        .add(item(GZParts.STEEL_PLATE, 8, 1, 3)).add(item(Items.SPYGLASS, 2, 1, 1)).add(item(Items.COMPASS, 3, 1, 1)));
        // A test site's observation bunker: the instruments, the shielding, and what was picked up off the ground.
        table("test_site",
                pool(2, 4).add(item(RadiationItems.DOSIMETER_BADGE, 15, 1, 3)).add(item(RadiationItems.GEIGER_COUNTER, 5, 1, 1))
                        .add(item(GZParts.LEAD_PLATE, 15, 1, 4)).add(item(GZResources.LEAD_INGOT, 12, 2, 6))
                        .add(item(GZFallout.TRINITITE.item(), 12, 1, 4)).add(item(Items.PAPER, 15, 2, 8)).add(item(Items.CLOCK, 3, 1, 1))
                        .add(item(Items.SPYGLASS, 3, 1, 1)),
                pool(0, 1).add(item(RadiationItems.LEAD_CONTAINER, 2, 1, 1)).add(item(GZResources.URANIUM_DUST, 3, 1, 3)).add(nothing(10)));
        // The wreck of a crashed missile: its guidance, its motor, its broken warhead.
        table("crashed_missile",
                pool(1, 1).add(item(GZSalvage.DAMAGED_WARHEAD, 2, 1, 1)).add(nothing(1)),
                pool(1, 3).add(item(GZAssembly.INERTIAL_GUIDANCE, 6, 1, 1)).add(item(GZAssembly.GPS_GUIDANCE, 2, 1, 1))
                        .add(item(GZAssembly.HARDENED_ELECTRONICS, 3, 1, 1)).add(item(GZParts.MILITARY_CIRCUIT, 5, 1, 2))
                        .add(item(GZAssembly.SRM_I, 3, 1, 1)).add(item(GZChemicals.SOLID_PROPELLANT, 10, 1, 4))
                        .add(item(GZParts.STEEL_PLATE, 15, 2, 6)).add(item(GZParts.DENSE_STEEL_PLATE, 8, 1, 2)).add(item(GZParts.SERVO, 8, 1, 2)));
        // The waste store: hazmat gear by the decontamination rooms, lead, waste, now and then a spent rod.
        table("waste_storage",
                pool(1, 3).add(worn(GZArmor.HAZMAT_SET.get(0).get(), 6)).add(worn(GZArmor.HAZMAT_SET.get(1).get(), 6))
                        .add(worn(GZArmor.HAZMAT_SET.get(2).get(), 6)).add(worn(GZArmor.HAZMAT_SET.get(3).get(), 6))
                        .add(worn(GZArmor.LEAD_HAZMAT_SET.get(0).get(), 2)).add(worn(GZArmor.LEAD_HAZMAT_SET.get(1).get(), 2))
                        .add(worn(GZArmor.LEAD_HAZMAT_SET.get(2).get(), 2)).add(worn(GZArmor.LEAD_HAZMAT_SET.get(3).get(), 2)),
                pool(2, 4).add(item(GZParts.LEAD_PLATE, 15, 2, 5)).add(item(GZResources.LEAD_INGOT, 15, 2, 8))
                        .add(item(GZNuclear.NUCLEAR_WASTE, 12, 1, 4)).add(item(RadiationItems.RAD_AWAY, 10, 1, 3))
                        .add(item(RadiationItems.POTASSIUM_IODIDE, 10, 1, 4)).add(item(RadiationItems.ANTI_RAD_INJECTOR, 4, 1, 1))
                        .add(item(RadiationItems.DOSIMETER_BADGE, 8, 1, 2)).add(item(RadiationItems.GEIGER_COUNTER, 3, 1, 1))
                        .add(item(RadiationItems.LEAD_CONTAINER, 2, 1, 1)).add(item(GZNuclear.SPENT_FUEL_ROD, 1, 1, 1)));
        // The research station in the End: what its laboratories worked with.
        table("research_station",
                pool(2, 4).add(item(GZResources.EXOTIC_SHARD, 20, 2, 6)).add(item(GZNuclear.STABILISED_EXOTIC_MATTER, 8, 1, 2))
                        .add(item(GZNuclear.CONTAINMENT_CELL, 8, 1, 2)).add(item(GZParts.SUPERCONDUCTING_COIL, 6, 1, 2))
                        .add(item(GZParts.QUANTUM_CIRCUIT, 3, 1, 1)).add(item(GZParts.MILITARY_CIRCUIT, 6, 1, 2))
                        .add(item(GZNuclear.ANTIMATTER_CELL, 1, 1, 1)).add(item(GZNuclear.SINGULARITY_SEED, 1, 1, 1)),
                pool(1, 3).add(item(Items.ENDER_PEARL, 10, 1, 3)).add(item(Items.CHORUS_FRUIT, 10, 2, 6)).add(item(Items.END_ROD, 8, 1, 4))
                        .add(item(Items.EXPERIENCE_BOTTLE, 6, 1, 3)).add(item(Items.SHULKER_SHELL, 2, 1, 1)));
    }
}
