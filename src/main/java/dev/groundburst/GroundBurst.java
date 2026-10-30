package dev.groundburst;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.groundburst.config.GBCommonConfig;
import dev.groundburst.config.GBServerConfig;
import dev.groundburst.content.chem.GBChemicals;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.part.GBParts;
import dev.groundburst.content.recipe.GBRecipes;
import dev.groundburst.content.resource.GBResources;
import dev.groundburst.datagen.GBDataGen;
import dev.groundburst.gametest.GBGameTests;
import dev.groundburst.network.GBNetwork;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBMenus;
import dev.groundburst.registry.GBCreativeTabs;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBSounds;
import dev.groundburst.world.GBWorldgen;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(GroundBurst.MODID)
public class GroundBurst {
    public static final String MODID = "groundburst";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GroundBurst(IEventBus modBus, ModContainer modContainer) {
        GBResources.init();
        GBMachines.init();
        GBParts.init();
        dev.groundburst.content.machine.GBUpgrades.init();
        GBChemicals.init();
        dev.groundburst.content.nuclear.GBNuclear.init();
        dev.groundburst.content.gear.GBArmor.init();
        dev.groundburst.content.radiation.RadiationItems.init();
        dev.groundburst.content.fallout.GBFallout.init();
        dev.groundburst.content.salvage.GBSalvage.init();
        dev.groundburst.content.ordnance.GBOrdnance.init();
        dev.groundburst.content.ordnance.GBOrdnance.register();
        dev.groundburst.content.launch.GBLaunch.init();
        dev.groundburst.content.defence.GBDefence.init();
        dev.groundburst.content.alarm.GBAlarms.init();
        dev.groundburst.content.assembly.GBAssembly.init();
        dev.groundburst.content.strategic.GBStrategic.init();
        dev.groundburst.content.assembler.GBAssemblers.init();
        dev.groundburst.content.superweapon.GBSuperweapons.init();
        dev.groundburst.content.effect.EffectSystems.init();
        dev.groundburst.content.fluid.GBFluids.init();
        dev.groundburst.content.pipe.GBPipes.init();
        GBMultiblocks.init();
        dev.groundburst.content.manual.GBManual.init();

        dev.groundburst.content.fluid.GBFluids.register(modBus);
        GBBlocks.BLOCKS.register(modBus);
        GBItems.ITEMS.register(modBus);
        GBBlockEntities.BLOCK_ENTITIES.register(modBus);
        dev.groundburst.registry.GBDataComponents.COMPONENTS.register(modBus);
        GBMenus.MENUS.register(modBus);
        GBRecipes.register(modBus);
        modBus.addListener(GBBlockEntities::registerCapabilities);
        GBCreativeTabs.TABS.register(modBus);
        GBSounds.SOUNDS.register(modBus);
        dev.groundburst.content.nuclear.GBEffects.EFFECTS.register(modBus);
        dev.groundburst.content.ordnance.GBOrdnance.ENTITIES.register(modBus);
        dev.groundburst.content.launch.GBLaunch.register(modBus);
        dev.groundburst.content.effect.EffectSystems.register();
        dev.groundburst.content.radiation.GBRadiation.register(modBus);
        dev.groundburst.content.radiation.RadiationItems.register(modBus);
        dev.groundburst.content.fallout.GBFallout.register(modBus);
        dev.groundburst.content.construction.GBConstruction.register();
        dev.groundburst.content.nuclear.NeutronBurst.register();
        dev.groundburst.content.exotic.Antimatter.register();
        dev.groundburst.content.nuclear.Meltdown.register();
        GBWorldgen.PLACEMENT_MODIFIERS.register(modBus);
        dev.groundburst.world.structure.GBStructures.register(modBus);

        GBNetwork.register(modBus);
        dev.groundburst.blast.GBBlast.register(modBus);
        GBDataGen.register(modBus);
        GBGameTests.register(modBus);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(dev.groundburst.content.machine.MachineFluidInteraction::onRightClickBlock);

        GBLang.add(MODID + ".configuration.title", "Ground Burst Settings");
        modContainer.registerConfig(ModConfig.Type.SYNCED, GBServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.LOCAL, GBCommonConfig.SPEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
