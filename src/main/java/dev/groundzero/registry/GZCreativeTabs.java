package dev.groundzero.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.groundzero.GroundZero;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One creative tab per {@link GZTab}, chained after vanilla's Combat tab. A tab's icon is the first item
 * registered to it, and a tab with no items is hidden by vanilla.
 */
public final class GZCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GroundZero.MODID);

    private static final Map<GZTab, DeferredHolder<CreativeModeTab, CreativeModeTab>> BY_TAB = new EnumMap<>(GZTab.class);

    static {
        ResourceKey<CreativeModeTab> previous = CreativeModeTabs.COMBAT;
        for (GZTab tab : GZTab.values()) {
            ResourceKey<CreativeModeTab> after = previous;
            DeferredHolder<CreativeModeTab, CreativeModeTab> holder = TABS.register(tab.id(), () -> CreativeModeTab.builder()
                    .title(Component.translatable(tab.translationKey()))
                    .withTabsBefore(after)
                    .icon(() -> iconFor(tab))
                    .displayItems((parameters, output) -> GZItems.contents(tab).forEach(item -> output.accept(item.get())))
                    .build());
            BY_TAB.put(tab, holder);
            previous = holder.key();
        }
    }

    private GZCreativeTabs() {}

    private static ItemStack iconFor(GZTab tab) {
        List<DeferredItem<? extends Item>> contents = GZItems.contents(tab);
        // Empty tabs are never shown, so the fallback is never visible.
        return contents.isEmpty() ? new ItemStack(Items.BARRIER) : contents.getFirst().get().getDefaultInstance();
    }

    public static DeferredHolder<CreativeModeTab, CreativeModeTab> get(GZTab tab) {
        return BY_TAB.get(tab);
    }
}
