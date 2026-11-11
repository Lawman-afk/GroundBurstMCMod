package dev.groundburst.port;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import dev.groundburst.legacy.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import dev.groundburst.legacy.net.ByteBufCodecs;
import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft 26.x's item stack template (an item, a count and a component patch, fixed: what a recipe makes), written
 * as 1.21.1 writes an item stack ({"id", "count", "components"}, or the bare id).
 */
public record ItemStackTemplate(Holder<Item> item, int count, DataComponentPatch components) {
    private static final Codec<Holder<Item>> ITEM_CODEC = BuiltInRegistries.ITEM.holderByNameCodec();

    public static final MapCodec<ItemStackTemplate> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ITEM_CODEC.fieldOf("id").forGetter(ItemStackTemplate::item),
            ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(ItemStackTemplate::count),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStackTemplate::components)
    ).apply(i, ItemStackTemplate::new));
    public static final Codec<ItemStackTemplate> CODEC = dev.groundburst.legacy.Codecs2.withAlternative(MAP_CODEC.codec(), ITEM_CODEC, ItemStackTemplate::new);
    public static final StreamCodec<FriendlyByteBuf, ItemStackTemplate> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(net.minecraft.core.registries.Registries.ITEM), ItemStackTemplate::item,
            ByteBufCodecs.VAR_INT, ItemStackTemplate::count,
            DataComponentPatch.STREAM_CODEC, ItemStackTemplate::components,
            ItemStackTemplate::new);

    public ItemStackTemplate(Item item) {
        this(item.builtInRegistryHolder(), 1, DataComponentPatch.EMPTY);
    }

    public ItemStackTemplate(Item item, int count) {
        this(item.builtInRegistryHolder(), count, DataComponentPatch.EMPTY);
    }

    public ItemStackTemplate(Item item, DataComponentPatch patch) {
        this(item.builtInRegistryHolder(), 1, patch);
    }

    public ItemStackTemplate(Item item, int count, DataComponentPatch patch) {
        this(item.builtInRegistryHolder(), count, patch);
    }

    public ItemStackTemplate(Holder<Item> item) {
        this(item, 1, DataComponentPatch.EMPTY);
    }

    public ItemStackTemplate(Holder<Item> item, int count) {
        this(item, count, DataComponentPatch.EMPTY);
    }

    public ItemStackTemplate(Holder<Item> item, DataComponentPatch patch) {
        this(item, 1, patch);
    }

    /** 1.21.1's stacks (a bundle's, a container's: 26.x keeps templates there) as templates. */
    public static java.util.List<ItemStackTemplate> of(Iterable<ItemStack> stacks) {
        java.util.List<ItemStackTemplate> out = new java.util.ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                out.add(fromNonEmptyStack(stack));
            }
        }
        return out;
    }

    public static ItemStackTemplate fromNonEmptyStack(ItemStack stack) {
        return new ItemStackTemplate(stack.getItemHolder(), stack.getCount(), dev.groundburst.legacy.component.DataComponentPatch.of(stack));
    }

    public static ItemStackTemplate fromNonEmptyStack(ItemStack stack, int count) {
        return new ItemStackTemplate(stack.getItemHolder(), count, dev.groundburst.legacy.component.DataComponentPatch.of(stack));
    }

    public ItemStackTemplate withCount(int count) {
        return new ItemStackTemplate(this.item, count, this.components);
    }

    public ItemStack create() {
        return dev.groundburst.legacy.component.DataComponentPatch.stack(this.item, this.count, this.components);
    }

    public ItemStack apply(DataComponentPatch additionalPatch) {
        ItemStack stack = create();
        additionalPatch.applyTo(stack);
        return stack;
    }

    public Holder<Item> typeHolder() {
        return this.item;
    }

    public Item getItem() {
        return this.item.value();
    }

    public boolean is(Item item) {
        return this.item.value() == item;
    }
}
