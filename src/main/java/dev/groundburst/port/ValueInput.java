package dev.groundburst.port;

import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * Minecraft 26.x's {@code ValueInput} (what a block entity or entity loads from) over 1.21.1's {@link CompoundTag}, so
 * the mod's loading code is main's own (docs/BACKPORT.md).
 */
public final class ValueInput {
    private final CompoundTag tag;
    private final HolderLookup.Provider registries;

    public ValueInput(CompoundTag tag, HolderLookup.Provider registries) {
        this.tag = tag;
        this.registries = registries;
    }

    /** The tag it reads. */
    public CompoundTag tag() {
        return this.tag;
    }

    public HolderLookup.Provider lookup() {
        return this.registries;
    }

    public <T> Optional<T> read(String name, Codec<T> codec) {
        Tag t = this.tag.get(name);
        return t == null ? Optional.empty() : codec.parse(this.registries.createSerializationContext(NbtOps.INSTANCE), t).result();
    }

    public <T> Optional<T> read(MapCodec<T> codec) {
        return codec.codec().parse(this.registries.createSerializationContext(NbtOps.INSTANCE), this.tag).result();
    }

    public Optional<ValueInput> child(String name) {
        return this.tag.contains(name, Tag.TAG_COMPOUND) ? Optional.of(new ValueInput(this.tag.getCompound(name), this.registries)) : Optional.empty();
    }

    public ValueInput childOrEmpty(String name) {
        return new ValueInput(this.tag.getCompound(name), this.registries);
    }

    public Optional<ValueInputList> childrenList(String name) {
        return this.tag.contains(name, Tag.TAG_LIST) ? Optional.of(childrenListOrEmpty(name)) : Optional.empty();
    }

    public ValueInputList childrenListOrEmpty(String name) {
        ListTag list = this.tag.getList(name, Tag.TAG_COMPOUND);
        return new ValueInputList(list, this.registries);
    }

    public <T> Optional<TypedInputList<T>> list(String name, Codec<T> codec) {
        return this.tag.contains(name, Tag.TAG_LIST) ? Optional.of(listOrEmpty(name, codec)) : Optional.empty();
    }

    public <T> TypedInputList<T> listOrEmpty(String name, Codec<T> codec) {
        Tag t = this.tag.get(name);
        ListTag list = t instanceof ListTag l ? l : new ListTag();
        return new TypedInputList<>(list, codec, this.registries);
    }

    public boolean getBooleanOr(String name, boolean defaultValue) {
        return ValueOutput.numeric(this.tag, name) ? this.tag.getBoolean(name) : defaultValue;
    }

    public byte getByteOr(String name, byte defaultValue) {
        return ValueOutput.numeric(this.tag, name) ? this.tag.getByte(name) : defaultValue;
    }

    public int getShortOr(String name, short defaultValue) {
        return ValueOutput.numeric(this.tag, name) ? this.tag.getShort(name) : defaultValue;
    }

    public Optional<Integer> getInt(String name) {
        return ValueOutput.numeric(this.tag, name) ? Optional.of(this.tag.getInt(name)) : Optional.empty();
    }

    public int getIntOr(String name, int defaultValue) {
        return ValueOutput.numeric(this.tag, name) ? this.tag.getInt(name) : defaultValue;
    }

    public long getLongOr(String name, long defaultValue) {
        return ValueOutput.numeric(this.tag, name) ? this.tag.getLong(name) : defaultValue;
    }

    public Optional<Long> getLong(String name) {
        return ValueOutput.numeric(this.tag, name) ? Optional.of(this.tag.getLong(name)) : Optional.empty();
    }

    public float getFloatOr(String name, float defaultValue) {
        return ValueOutput.numeric(this.tag, name) ? this.tag.getFloat(name) : defaultValue;
    }

    public double getDoubleOr(String name, double defaultValue) {
        return ValueOutput.numeric(this.tag, name) ? this.tag.getDouble(name) : defaultValue;
    }

    public Optional<String> getString(String name) {
        return this.tag.contains(name, Tag.TAG_STRING) ? Optional.of(this.tag.getString(name)) : Optional.empty();
    }

    public String getStringOr(String name, String defaultValue) {
        return this.tag.contains(name, Tag.TAG_STRING) ? this.tag.getString(name) : defaultValue;
    }

    public Optional<int[]> getIntArray(String name) {
        return this.tag.contains(name, Tag.TAG_INT_ARRAY) ? Optional.of(this.tag.getIntArray(name)) : Optional.empty();
    }

    /** A list of compound children, each read as a {@link ValueInput}. */
    public static final class ValueInputList {
        private final ListTag list;
        private final HolderLookup.Provider registries;

        ValueInputList(ListTag list, HolderLookup.Provider registries) {
            this.list = list;
            this.registries = registries;
        }

        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        public Stream<ValueInput> stream() {
            return this.list.stream().filter(t -> t instanceof CompoundTag).map(t -> new ValueInput((CompoundTag) t, this.registries));
        }
    }

    /** A list of values each read by a codec (those that fail to read are left out). */
    public static final class TypedInputList<T> implements Iterable<T> {
        private final ListTag list;
        private final Codec<T> codec;
        private final HolderLookup.Provider registries;

        TypedInputList(ListTag list, Codec<T> codec, HolderLookup.Provider registries) {
            this.list = list;
            this.codec = codec;
            this.registries = registries;
        }

        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        public Stream<T> stream() {
            var ops = this.registries.createSerializationContext(NbtOps.INSTANCE);
            return this.list.stream().map(t -> this.codec.parse(ops, t).result()).flatMap(Optional::stream);
        }

        @Override
        public java.util.Iterator<T> iterator() {
            return stream().iterator();
        }
    }
}
