package dev.groundburst.legacy.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla 1.21's item data components the mod touches, each kept where Minecraft 1.20.1 keeps the same thing in an item's
 * tag (so vanilla reads them: a model's custom model data, a leather piece's colour, a container's or a bundle's items).
 */
public final class DataComponents {
    private DataComponents() {}

    /** 1.20.1's "CustomModelData" (the item models' overrides pick a look by it). */
    public static final DataComponentType<CustomModelData> CUSTOM_MODEL_DATA = new Tagged<>("custom_model_data",
            stack -> {
                CompoundTag tag = stack.getTag();
                return tag != null && tag.contains("CustomModelData", Tag.TAG_ANY_NUMERIC) ? new CustomModelData(tag.getInt("CustomModelData")) : null;
            },
            (stack, value) -> {
                if (value == null) {
                    removeKey(stack, "CustomModelData");
                } else {
                    stack.getOrCreateTag().putInt("CustomModelData", value.value());
                }
            });

    /** 1.20.1's display colour of a dyed (leather) item. */
    public static final DataComponentType<DyedItemColor> DYED_COLOR = new Tagged<>("dyed_color",
            stack -> {
                CompoundTag display = stack.getTagElement("display");
                return display != null && display.contains("color", Tag.TAG_ANY_NUMERIC) ? new DyedItemColor(display.getInt("color"), true) : null;
            },
            (stack, value) -> {
                if (value == null) {
                    CompoundTag display = stack.getTagElement("display");
                    if (display != null) {
                        display.remove("color");
                    }
                } else {
                    stack.getOrCreateTagElement("display").putInt("color", value.rgb());
                }
            });

    /** 1.20.1's block entity data's items (a shulker box's; a Lead Container's too). */
    public static final DataComponentType<ItemContainerContents> CONTAINER = new Tagged<>("container",
            stack -> {
                CompoundTag be = stack.getTagElement("BlockEntityTag");
                if (be == null || !be.contains("Items", Tag.TAG_LIST)) {
                    return null;
                }
                return ItemContainerContents.load(be.getList("Items", Tag.TAG_COMPOUND));
            },
            (stack, value) -> {
                if (value == null) {
                    CompoundTag be = stack.getTagElement("BlockEntityTag");
                    if (be != null) {
                        be.remove("Items");
                    }
                } else {
                    stack.getOrCreateTagElement("BlockEntityTag").put("Items", value.save());
                }
            });

    /** 1.20.1's bundle items. */
    public static final DataComponentType<BundleContents> BUNDLE_CONTENTS = new Tagged<>("bundle_contents",
            stack -> {
                CompoundTag tag = stack.getTag();
                if (tag == null || !tag.contains("Items", Tag.TAG_LIST)) {
                    return null;
                }
                ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    items.add(ItemStack.of(list.getCompound(i)));
                }
                return new BundleContents(items);
            },
            (stack, value) -> {
                if (value == null) {
                    removeKey(stack, "Items");
                } else {
                    ListTag list = new ListTag();
                    for (ItemStack s : value.items()) {
                        list.add(s.save(new CompoundTag()));
                    }
                    stack.getOrCreateTag().put("Items", list);
                }
            });

    /** An item's whole custom tag. */
    public static final DataComponentType<CustomData> CUSTOM_DATA = new Tagged<>("custom_data",
            stack -> stack.getTag() == null ? null : CustomData.of(stack.getTag()),
            (stack, value) -> stack.setTag(value == null || value.isEmpty() ? null : value.copyTag()));

    private static void removeKey(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(key);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
    }

    /** A vanilla component in its 1.20.1 place in an item's tag. */
    private static final class Tagged<T> extends DataComponentType<T> {
        private final Function<ItemStack, @Nullable T> reader;
        private final BiConsumer<ItemStack, @Nullable T> writer;

        Tagged(String name, Function<ItemStack, @Nullable T> reader, BiConsumer<ItemStack, @Nullable T> writer) {
            super(new ResourceLocation("minecraft", name), null, null);
            this.reader = reader;
            this.writer = writer;
        }

        @Override
        public @Nullable T read(ItemStack stack) {
            return this.reader.apply(stack);
        }

        @Override
        public void write(ItemStack stack, @Nullable T value) {
            this.writer.accept(stack, value);
        }

        @Override
        public boolean present(ItemStack stack) {
            return read(stack) != null;
        }
    }
}
