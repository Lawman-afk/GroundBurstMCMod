package dev.groundburst.legacy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Minecraft 1.21 names an attribute modifier by an id; 1.20.1 by a UUID and a name: the UUID made from the id (the same
 * id always gives the same UUID, so a modifier is found and taken off again).
 */
public final class Attrs {
    private Attrs() {}

    public static String uuid(ResourceLocation id) {
        return uuidOf(id).toString();
    }

    public static UUID uuidOf(ResourceLocation id) {
        return UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static AttributeModifier modifier(ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(uuidOf(id), id.toString(), amount, operation);
    }
}
