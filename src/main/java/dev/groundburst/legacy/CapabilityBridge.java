package dev.groundburst.legacy;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

/**
 * The mod's blocks and items as Forge 1.20.1 sees them: each of the mod's block entities and items answers Forge's item,
 * fluid and energy capabilities with what the mod registered for NeoForge's ({@link Capabilities}), so hoppers and other
 * mods' pipes, tanks and chargers reach them.
 */
public final class CapabilityBridge {
    private CapabilityBridge() {}

    private static final ResourceLocation ID = new ResourceLocation(GroundBurst.MODID, "handlers");

    static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, CapabilityBridge::attachBlockEntity);
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, CapabilityBridge::attachItem);
    }

    static boolean isBridged(BlockEntity be) {
        ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return id != null && GroundBurst.MODID.equals(id.getNamespace());
    }

    static boolean isBridged(Item item) {
        return GroundBurst.MODID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace());
    }

    private static void attachBlockEntity(AttachCapabilitiesEvent<BlockEntity> event) {
        BlockEntity be = event.getObject();
        if (!isBridged(be)) {
            return;
        }
        BlockProvider provider = new BlockProvider(be);
        event.addCapability(ID, provider);
        event.addListener(provider::invalidate);
    }

    private static void attachItem(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (stack.isEmpty() || !isBridged(stack.getItem())) {
            return;
        }
        event.addCapability(ID, new ItemProvider(stack));
    }

    /** One LazyOptional per handler handed out, renewed when the handler changes and ended when the block entity goes. */
    private abstract static class Handed implements net.minecraftforge.common.capabilities.ICapabilityProvider {
        private final Map<Object, Object[]> handed = new HashMap<>();

        @SuppressWarnings("unchecked")
        protected <T> LazyOptional<T> hand(Object key, @Nullable Object handler) {
            Object[] was = this.handed.get(key);
            if (handler == null) {
                if (was != null) {
                    ((LazyOptional<?>) was[1]).invalidate();
                    this.handed.remove(key);
                }
                return LazyOptional.empty();
            }
            if (was != null && was[0] == handler) {
                return (LazyOptional<T>) was[1];
            }
            if (was != null) {
                ((LazyOptional<?>) was[1]).invalidate();
            }
            Object h = handler;
            LazyOptional<T> optional = LazyOptional.of(() -> (T) h);
            this.handed.put(key, new Object[] {handler, optional});
            return optional;
        }

        void invalidate() {
            for (Object[] h : this.handed.values()) {
                ((LazyOptional<?>) h[1]).invalidate();
            }
            this.handed.clear();
        }
    }

    private static final class BlockProvider extends Handed {
        private final BlockEntity be;

        BlockProvider(BlockEntity be) {
            this.be = be;
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            Level level = this.be.getLevel();
            if (level == null || this.be.isRemoved()) {
                return LazyOptional.empty();
            }
            BlockCapability<?, @Nullable Direction> ours = cap == ForgeCapabilities.ITEM_HANDLER ? Capabilities.ItemHandler.BLOCK
                    : cap == ForgeCapabilities.FLUID_HANDLER ? Capabilities.FluidHandler.BLOCK
                    : cap == ForgeCapabilities.ENERGY ? Capabilities.EnergyStorage.BLOCK : null;
            if (ours == null) {
                return LazyOptional.empty();
            }
            Object handler = ours.fromProviders(level, this.be.getBlockPos(), this.be.getBlockState(), this.be, side);
            return hand(java.util.List.of(cap, side == null ? "none" : side), handler);
        }
    }

    private static final class ItemProvider extends Handed {
        private final ItemStack stack;

        ItemProvider(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            ItemCapability<?, @Nullable Void> ours = cap == ForgeCapabilities.ITEM_HANDLER ? Capabilities.ItemHandler.ITEM
                    : cap == ForgeCapabilities.FLUID_HANDLER_ITEM ? Capabilities.FluidHandler.ITEM
                    : cap == ForgeCapabilities.ENERGY ? Capabilities.EnergyStorage.ITEM : null;
            if (ours == null) {
                return LazyOptional.empty();
            }
            return hand(cap, ours.fromProviders(this.stack, null));
        }
    }
}
