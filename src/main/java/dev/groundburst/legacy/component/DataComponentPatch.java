package dev.groundburst.legacy.component;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Minecraft 1.21's changes to an item's (or a fluid's) components over its own: on 1.20.1 its tag (where the components
 * are kept: {@link DataComponentType}). The port writes {@code stack.getComponentsPatch()} as {@code DataComponentPatch.of(stack)}.
 */
public final class DataComponentPatch {
    public static final DataComponentPatch EMPTY = new DataComponentPatch(null);
    public static final Codec<DataComponentPatch> CODEC = CompoundTag.CODEC.xmap(DataComponentPatch::of,
            p -> p.tag == null ? new CompoundTag() : p.tag.copy());
    public static final StreamCodec<FriendlyByteBuf, DataComponentPatch> STREAM_CODEC = StreamCodec.of((b, p) -> b.writeNbt(p.tag),
            b -> of(b.readNbt()));

    private final @Nullable CompoundTag tag;

    private DataComponentPatch(@Nullable CompoundTag tag) {
        this.tag = tag;
    }

    public static DataComponentPatch of(@Nullable CompoundTag tag) {
        return tag == null || tag.isEmpty() ? EMPTY : new DataComponentPatch(tag.copy());
    }

    public static DataComponentPatch of(ItemStack stack) {
        return of(stack.getTag());
    }

    public static DataComponentPatch of(FluidStack stack) {
        return of(stack.getTag());
    }

    /** A transfer resource's changes (the port writes 1.21's {@code x.getComponentsPatch()} so for any receiver). */
    public static DataComponentPatch of(dev.groundburst.port.transfer.item.ItemResource resource) {
        return resource.getComponentsPatch();
    }

    public static DataComponentPatch of(dev.groundburst.port.transfer.fluid.FluidResource resource) {
        return resource.getComponentsPatch();
    }

    /** Its tag (a copy; null when it changes nothing). */
    public @Nullable CompoundTag tag() {
        return this.tag == null ? null : this.tag.copy();
    }

    public boolean isEmpty() {
        return this.tag == null;
    }

    /** Puts these changes on {@code stack} (over what its tag has). */
    public void applyTo(ItemStack stack) {
        if (this.tag != null) {
            stack.getOrCreateTag().merge(this.tag.copy());
        }
    }

    /**
     * A stack of {@code item} with these changes (1.21's ItemStack(item, count, patch)). Its tag is set after it is made:
     * Forge 1.20.1's ItemStack(item, count, tag) takes its capabilities' data, not its tag.
     */
    public static ItemStack stack(Holder<Item> item, int count, DataComponentPatch patch) {
        return stack(item.value(), count, patch);
    }

    public static ItemStack stack(ItemLike item, int count, DataComponentPatch patch) {
        ItemStack stack = new ItemStack(item, count);
        if (!stack.isEmpty()) {
            stack.setTag(patch.tag());
        }
        return stack;
    }

    /** A fluid stack with these changes (NeoForge's FluidStack(fluid, amount, patch)). */
    public static FluidStack fluid(Holder<Fluid> fluid, int amount, DataComponentPatch patch) {
        return new FluidStack(fluid.value(), amount, patch.tag());
    }

    public static FluidStack fluid(Fluid fluid, int amount, DataComponentPatch patch) {
        return new FluidStack(fluid, amount, patch.tag());
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof DataComponentPatch p && Objects.equals(this.tag, p.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.tag);
    }

    @Override
    public String toString() {
        return this.tag == null ? "{}" : this.tag.toString();
    }
}
