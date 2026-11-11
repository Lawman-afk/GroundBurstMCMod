package dev.groundburst.legacy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import dev.groundburst.legacy.component.DataComponentType;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

/**
 * NeoForge's deferred register, over Forge 1.20.1's: the same calls, the objects registered by Forge's register as the
 * registries fill, and handed back as NeoForge's holders ({@link DeferredHolder}, {@link DeferredItem},
 * {@link DeferredBlock}). The item data components, which 1.20.1 keeps in no registry, are made at once
 * ({@link DataComponents}).
 */
public class DeferredRegister<T> {
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String namespace;
    private final net.minecraftforge.registries.@org.jspecify.annotations.Nullable DeferredRegister<T> forge;
    private final List<DeferredHolder<T, ? extends T>> entries = new ArrayList<>();

    public static <T> DeferredRegister<T> create(Registry<T> registry, String namespace) {
        return new DeferredRegister<>(registry.key(), namespace);
    }

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String namespace) {
        // NeoForge's data attachments: no registry on Forge 1.20.1, each made and named at once (Attachments).
        return new DeferredRegister<>(key, namespace, !key.equals(Attachments.REGISTRY_KEY));
    }

    public static <B> DeferredRegister<B> create(ResourceLocation registryName, String modid) {
        return new DeferredRegister<>(ResourceKey.createRegistryKey(registryName), modid);
    }

    public static Items createItems(String modid) {
        return new Items(modid);
    }

    public static Blocks createBlocks(String modid) {
        return new Blocks(modid);
    }

    public static DataComponents createDataComponents(String modid) {
        return new DataComponents(modid);
    }

    protected DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this(registryKey, namespace, true);
    }

    private DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace, boolean forge) {
        this.registryKey = registryKey;
        this.namespace = namespace;
        this.forge = forge ? net.minecraftforge.registries.DeferredRegister.create(registryKey, namespace) : null;
    }

    @SuppressWarnings("unchecked")
    public <I extends T> DeferredHolder<T, I> register(final String name, final Supplier<? extends I> sup) {
        if (this.forge == null) {
            // Kept in no registry on 1.20.1 (data attachments): made now, and named if it needs its name.
            ResourceLocation id = new ResourceLocation(this.namespace, name);
            I value = sup.get();
            if (value instanceof AttachmentType<?> type) {
                Attachments.name(type, id);
            }
            DeferredHolder<T, I> holder = DeferredHolder.direct(ResourceKey.create((ResourceKey<Registry<T>>) this.registryKey, id), value);
            this.entries.add(holder);
            return holder;
        }
        RegistryObject<I> object = this.forge.register(name, sup);
        DeferredHolder<T, I> holder = createHolder(ResourceKey.create((ResourceKey<Registry<T>>) this.registryKey,
                new ResourceLocation(this.namespace, name)), object);
        this.entries.add(holder);
        return holder;
    }

    public <I extends T> DeferredHolder<T, I> register(final String name, final Function<ResourceLocation, ? extends I> func) {
        ResourceLocation id = new ResourceLocation(this.namespace, name);
        return register(name, () -> func.apply(id));
    }

    /** The holder handed back for what Forge's register registers as {@code object} (bound as it is registered). */
    protected <I extends T> DeferredHolder<T, I> createHolder(ResourceKey<T> key, RegistryObject<I> object) {
        return new DeferredHolder<>(key, object);
    }

    public TagKey<T> createTagKey(String path) {
        return createTagKey(new ResourceLocation(this.namespace, path));
    }

    @SuppressWarnings("unchecked")
    public TagKey<T> createTagKey(ResourceLocation location) {
        return TagKey.create((ResourceKey<Registry<T>>) this.registryKey, location);
    }

    public void register(IEventBus bus) {
        if (this.forge != null) {
            this.forge.register(bus);
        }
    }

    public Collection<DeferredHolder<T, ? extends T>> getEntries() {
        return Collections.unmodifiableList(this.entries);
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return this.registryKey;
    }

    public ResourceLocation getRegistryName() {
        return this.registryKey.location();
    }

    public String getNamespace() {
        return this.namespace;
    }

    /** NeoForge's register of blocks. */
    public static class Blocks extends DeferredRegister<Block> {
        protected Blocks(String namespace) {
            super(Registries.BLOCK, namespace);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <B extends Block> DeferredBlock<B> register(String name, Function<ResourceLocation, ? extends B> func) {
            return (DeferredBlock<B>) super.register(name, func);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <B extends Block> DeferredBlock<B> register(String name, Supplier<? extends B> sup) {
            return (DeferredBlock<B>) super.register(name, sup);
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> func,
                BlockBehaviour.Properties props) {
            return this.register(name, () -> func.apply(props));
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> func) {
            return this.registerBlock(name, func, BlockBehaviour.Properties.of());
        }

        public DeferredBlock<Block> registerSimpleBlock(String name, BlockBehaviour.Properties props) {
            return this.registerBlock(name, Block::new, props);
        }

        public DeferredBlock<Block> registerSimpleBlock(String name) {
            return this.registerSimpleBlock(name, BlockBehaviour.Properties.of());
        }

        @Override
        protected <I extends Block> DeferredBlock<I> createHolder(ResourceKey<Block> key, RegistryObject<I> object) {
            return new DeferredBlock<>(key, object);
        }
    }

    /** NeoForge's register of items. */
    public static class Items extends DeferredRegister<Item> {
        protected Items(String namespace) {
            super(Registries.ITEM, namespace);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I extends Item> DeferredItem<I> register(String name, Function<ResourceLocation, ? extends I> func) {
            return (DeferredItem<I>) super.register(name, func);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I extends Item> DeferredItem<I> register(String name, Supplier<? extends I> sup) {
            return (DeferredItem<I>) super.register(name, sup);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block, Item.Properties properties) {
            return this.register(name, () -> new BlockItem(block.get(), properties));
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block) {
            return this.registerSimpleBlockItem(name, block, new Item.Properties());
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block, Item.Properties properties) {
            return this.registerSimpleBlockItem(block.unwrapKey().orElseThrow().location().getPath(), block::value, properties);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(DeferredBlock<?> block, Item.Properties properties) {
            return this.registerSimpleBlockItem(block.getId().getPath(), block, properties);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(DeferredBlock<?> block) {
            return this.registerSimpleBlockItem(block, new Item.Properties());
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> func, Item.Properties props) {
            return this.register(name, () -> func.apply(props));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> func) {
            return this.registerItem(name, func, new Item.Properties());
        }

        public DeferredItem<Item> registerSimpleItem(String name, Item.Properties props) {
            return this.registerItem(name, Item::new, props);
        }

        public DeferredItem<Item> registerSimpleItem(String name) {
            return this.registerItem(name, Item::new);
        }

        @Override
        protected <I extends Item> DeferredItem<I> createHolder(ResourceKey<Item> key, RegistryObject<I> object) {
            return new DeferredItem<>(key, object);
        }
    }

    /**
     * NeoForge's register of item data components. Minecraft 1.20.1 has no such registry (an item keeps its data in its
     * NBT: {@link dev.groundburst.legacy.component.Comps}): each component is made as it is registered.
     */
    public static class DataComponents extends DeferredRegister<DataComponentType<?>> {
        private static final ResourceKey<Registry<DataComponentType<?>>> KEY =
                ResourceKey.createRegistryKey(new ResourceLocation("groundburst", "data_component_type"));

        protected DataComponents(String namespace) {
            super(KEY, namespace, false);
        }

        public <D> DeferredHolder<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name,
                UnaryOperator<DataComponentType.Builder<D>> builder) {
            ResourceLocation id = new ResourceLocation(getNamespace(), name);
            DataComponentType<D> type = builder.apply(DataComponentType.builder()).build(id);
            return DeferredHolder.direct(ResourceKey.create(KEY, id), type);
        }
    }
}
