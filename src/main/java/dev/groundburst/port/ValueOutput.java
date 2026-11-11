package dev.groundburst.port;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * Minecraft 26.x's {@code ValueOutput} (what a block entity or entity saves into) over 1.21.1's {@link CompoundTag}, so
 * the mod's saving code is main's own (docs/BACKPORT.md).
 */
public final class ValueOutput {
    private final CompoundTag tag;
    private final HolderLookup.Provider registries;

    public ValueOutput(CompoundTag tag, HolderLookup.Provider registries) {
        this.tag = tag;
        this.registries = registries;
    }

    /** The tag it writes into. */
    public CompoundTag tag() {
        return this.tag;
    }

    public HolderLookup.Provider lookup() {
        return this.registries;
    }

    public <T> void store(String name, Codec<T> codec, T value) {
        codec.encodeStart(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, this.registries), value).result().ifPresent(t -> this.tag.put(name, t));
    }

    public <T> void storeNullable(String name, Codec<T> codec, @Nullable T value) {
        if (value != null) {
            store(name, codec, value);
        }
    }

    public <T> void store(MapCodec<T> codec, T value) {
        codec.codec().encodeStart(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, this.registries), value).result()
                .ifPresent(t -> {
                    if (((Object) t) instanceof CompoundTag c) {
                        this.tag.merge(c);
                    }
                });
    }

    public void putBoolean(String name, boolean value) {
        this.tag.putBoolean(name, value);
    }

    public void putByte(String name, byte value) {
        this.tag.putByte(name, value);
    }

    public void putShort(String name, short value) {
        this.tag.putShort(name, value);
    }

    public void putInt(String name, int value) {
        this.tag.putInt(name, value);
    }

    public void putLong(String name, long value) {
        this.tag.putLong(name, value);
    }

    public void putFloat(String name, float value) {
        this.tag.putFloat(name, value);
    }

    public void putDouble(String name, double value) {
        this.tag.putDouble(name, value);
    }

    public void putString(String name, String value) {
        this.tag.putString(name, Objects.requireNonNull(value));
    }

    public void putIntArray(String name, int[] value) {
        this.tag.putIntArray(name, value);
    }

    public ValueOutput child(String name) {
        CompoundTag c = new CompoundTag();
        this.tag.put(name, c);
        return new ValueOutput(c, this.registries);
    }

    public ValueOutputList childrenList(String name) {
        ListTag list = new ListTag();
        this.tag.put(name, list);
        return new ValueOutputList(list, this.registries);
    }

    public <T> TypedOutputList<T> list(String name, Codec<T> codec) {
        ListTag list = new ListTag();
        this.tag.put(name, list);
        return new TypedOutputList<>(list, codec, this.registries);
    }

    public void discard(String name) {
        this.tag.remove(name);
    }

    public boolean isEmpty() {
        return this.tag.isEmpty();
    }

    /** A list of compound children, each written as a {@link ValueOutput}. */
    public static final class ValueOutputList {
        private final ListTag list;
        private final HolderLookup.Provider registries;

        ValueOutputList(ListTag list, HolderLookup.Provider registries) {
            this.list = list;
            this.registries = registries;
        }

        public ValueOutput addChild() {
            CompoundTag c = new CompoundTag();
            this.list.add(c);
            return new ValueOutput(c, this.registries);
        }

        public void discardLast() {
            if (!this.list.isEmpty()) {
                this.list.remove(this.list.size() - 1);
            }
        }

        public boolean isEmpty() {
            return this.list.isEmpty();
        }
    }

    /** A list of values each written by a codec. */
    public static final class TypedOutputList<T> {
        private final ListTag list;
        private final Codec<T> codec;
        private final HolderLookup.Provider registries;

        TypedOutputList(ListTag list, Codec<T> codec, HolderLookup.Provider registries) {
            this.list = list;
            this.codec = codec;
            this.registries = registries;
        }

        public void add(T value) {
            this.codec.encodeStart(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, this.registries), value).result().ifPresent(this.list::add);
        }

        public boolean isEmpty() {
            return this.list.isEmpty();
        }
    }

    /** Whether a tag holds a number (what a numeric getter can read). */
    static boolean numeric(CompoundTag tag, String name) {
        return tag.contains(name, Tag.TAG_ANY_NUMERIC);
    }
}
