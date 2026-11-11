package dev.groundburst.legacy.net;

import net.minecraft.resources.ResourceLocation;

/**
 * Minecraft 1.21's custom payload: a record the mod sends, with its type. On 1.20.1 every one goes over the mod's one
 * Forge channel ({@link Network}).
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    static <T extends CustomPacketPayload> Type<T> createType(String id) {
        return new Type<>(new ResourceLocation(id));
    }

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
