package dev.groundzero;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.groundzero.config.GZServerConfig;
import dev.groundzero.datagen.GZDataGen;
import dev.groundzero.gametest.GZGameTests;
import dev.groundzero.network.GZNetwork;
import dev.groundzero.registry.GZBlocks;
import dev.groundzero.registry.GZCreativeTabs;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZLang;
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
        GZBlocks.BLOCKS.register(modBus);
        GZItems.ITEMS.register(modBus);
        GZCreativeTabs.TABS.register(modBus);

        GZNetwork.register(modBus);
        GZDataGen.register(modBus);
        GZGameTests.register(modBus);

        GZLang.add(MODID + ".configuration.title", "Ground Zero Settings");
        modContainer.registerConfig(ModConfig.Type.SYNCED, GZServerConfig.SPEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
