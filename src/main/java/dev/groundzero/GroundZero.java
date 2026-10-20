package dev.groundzero;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.groundzero.config.GZCommonConfig;
import dev.groundzero.config.GZServerConfig;
import dev.groundzero.content.chem.GZChemicals;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.part.GZParts;
import dev.groundzero.content.recipe.GZRecipes;
import dev.groundzero.content.resource.GZResources;
import dev.groundzero.datagen.GZDataGen;
import dev.groundzero.gametest.GZGameTests;
import dev.groundzero.network.GZNetwork;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZBlocks;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZCreativeTabs;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZLang;
import dev.groundzero.registry.GZSounds;
import dev.groundzero.world.GZWorldgen;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(GroundZero.MODID)
public class GroundZero {
    public static final String MODID = "groundzero";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GroundZero(IEventBus modBus, ModContainer modContainer) {
        GZResources.init();
        GZMachines.init();
        GZParts.init();
        GZChemicals.init();
        dev.groundzero.content.nuclear.GZNuclear.init();
        dev.groundzero.content.fluid.GZFluids.init();
        GZMultiblocks.init();
        dev.groundzero.content.manual.GZManual.init();

        dev.groundzero.content.fluid.GZFluids.register(modBus);
        GZBlocks.BLOCKS.register(modBus);
        GZItems.ITEMS.register(modBus);
        GZBlockEntities.BLOCK_ENTITIES.register(modBus);
        dev.groundzero.registry.GZDataComponents.COMPONENTS.register(modBus);
        GZMenus.MENUS.register(modBus);
        GZRecipes.register(modBus);
        modBus.addListener(GZBlockEntities::registerCapabilities);
        GZCreativeTabs.TABS.register(modBus);
        GZSounds.SOUNDS.register(modBus);
        GZWorldgen.PLACEMENT_MODIFIERS.register(modBus);

        GZNetwork.register(modBus);
        GZDataGen.register(modBus);
        GZGameTests.register(modBus);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(dev.groundzero.content.machine.MachineFluidInteraction::onRightClickBlock);

        GZLang.add(MODID + ".configuration.title", "Ground Zero Settings");
        modContainer.registerConfig(ModConfig.Type.SYNCED, GZServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.LOCAL, GZCommonConfig.SPEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
