package dev.groundburst.port;

import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Minecraft 26.x's compound tag reads with defaults (and its codec reads and writes), over 1.21.1's tag. */
public final class Nbt {
    private Nbt() {}

    public static String getStringOr(CompoundTag tag, String key, String or) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : or;
    }

    public static int getIntOr(CompoundTag tag, String key, int or) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : or;
    }

    public static long getLongOr(CompoundTag tag, String key, long or) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : or;
    }

    public static float getFloatOr(CompoundTag tag, String key, float or) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : or;
    }

    public static double getDoubleOr(CompoundTag tag, String key, double or) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : or;
    }

    public static boolean getBooleanOr(CompoundTag tag, String key, boolean or) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : or;
    }

    public static CompoundTag getCompoundOrEmpty(CompoundTag tag, String key) {
        return tag.getCompound(key);
    }

    public static <T> Optional<T> read(CompoundTag tag, String key, Codec<T> codec) {
        Tag t = tag.get(key);
        return t == null ? Optional.empty() : codec.parse(NbtOps.INSTANCE, t).result();
    }

    public static <T> void store(CompoundTag tag, String key, Codec<T> codec, T value) {
        codec.encodeStart(NbtOps.INSTANCE, value).result().ifPresent(t -> tag.put(key, t));
    }
}
