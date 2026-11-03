package dev.groundburst.port.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * What this branch's item models need from code (tools/backport/port.py writes the models from main's item model
 * definitions, and {@link ItemModelTable} with them): the constant tints, and the item properties their overrides read.
 * A property's value is passed as it is (26.x's range dispatch thresholds are not 0 to 1).
 */
public final class ItemModels {
    @FunctionalInterface
    public interface Property {
        float get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed);
    }

    private static final Map<String, Property> PROPERTIES = new HashMap<>();

    private ItemModels() {}

    /** A property main's models select on (26.x registers it with the item model's codec). */
    public static void property(ResourceLocation id, Property property) {
        PROPERTIES.put(id.toString(), property);
    }

    public static void register(IEventBus modBus) {
        property(ResourceLocation.fromNamespaceAndPath("groundburst", "using_item"),
                (stack, level, owner, seed) -> owner != null && owner.isUsingItem() && owner.getUseItem() == stack ? 1 : 0);
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(ItemModels::registerProperties));
        modBus.addListener((RegisterColorHandlersEvent.Item event) -> ItemModelTable.TINTS.forEach((id, colours) ->
                event.register((stack, tintIndex) -> tintIndex >= 0 && tintIndex < colours.length ? colours[tintIndex] : -1, item(id))));
    }

    private static void registerProperties() {
        for (Map.Entry<String, List<String>> e : ItemModelTable.PROPERTIES.entrySet()) {
            Property property = PROPERTIES.get(e.getKey());
            if (property == null) {
                continue;
            }
            ClampedItemPropertyFunction function = new ClampedItemPropertyFunction() {
                @Override
                public float unclampedCall(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                    return property.get(stack, level, entity, seed);
                }

                @Override
                @SuppressWarnings("deprecation")
                public float call(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                    return unclampedCall(stack, level, entity, seed);
                }
            };
            for (String id : e.getValue()) {
                ItemProperties.register(item(id), ResourceLocation.parse(e.getKey()), function);
            }
        }
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }
}
