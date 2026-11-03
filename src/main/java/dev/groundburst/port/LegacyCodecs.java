package dev.groundburst.port;

import com.mojang.serialization.Codec;

import net.minecraft.world.level.block.Rotation;

/** Minecraft 26.x's codecs for values 1.21.1 saved by hand (as their names). */
public final class LegacyCodecs {
    private LegacyCodecs() {}

    /** A structure piece's rotation, as 1.21.1's template pieces save it ("NONE", "CLOCKWISE_90", ...). */
    public static final Codec<Rotation> ROTATION = Codec.STRING.xmap(name -> {
        try {
            return Rotation.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Rotation.NONE;
        }
    }, Rotation::name);
}
