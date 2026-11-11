package dev.groundburst.legacy;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;

/**
 * NeoForge's block capability, on Forge 1.20.1: providers registered per block or per block entity type
 * ({@link RegisterCapabilitiesEvent}) and asked by place; NeoForge's own (item, fluid and energy handlers:
 * {@link Capabilities}) fall back to Forge's capability of the block entity there (a chest, another mod's machine).
 */
public final class BlockCapability<T, C> {
    private final ResourceLocation name;
    private final Class<T> typeClass;
    private final Class<C> contextClass;
    /** Forge's capability of the same thing, asked of a block entity no provider here answers for. */
    final @Nullable Capability<T> forge;
    final Map<Block, List<IBlockCapabilityProvider<T, C>>> byBlock = new IdentityHashMap<>();
    final Map<BlockEntityType<?>, List<ICapabilityProvider<BlockEntity, C, T>>> byBlockEntity = new IdentityHashMap<>();

    private BlockCapability(ResourceLocation name, Class<T> typeClass, Class<C> contextClass, @Nullable Capability<T> forge) {
        this.name = name;
        this.typeClass = typeClass;
        this.contextClass = contextClass;
        this.forge = forge;
    }

    public static <T, C> BlockCapability<T, C> create(ResourceLocation name, Class<T> typeClass, Class<C> contextClass) {
        return new BlockCapability<>(name, typeClass, contextClass, null);
    }

    public static <T> BlockCapability<T, @Nullable Direction> createSided(ResourceLocation name, Class<T> typeClass) {
        return new BlockCapability<>(name, typeClass, Direction.class, null);
    }

    public static <T> BlockCapability<T, @Nullable Void> createVoid(ResourceLocation name, Class<T> typeClass) {
        return new BlockCapability<>(name, typeClass, Void.class, null);
    }

    static <T> BlockCapability<T, @Nullable Direction> forgeSided(ResourceLocation name, Class<T> typeClass, Capability<T> forge) {
        return new BlockCapability<>(name, typeClass, Direction.class, forge);
    }

    public ResourceLocation name() {
        return this.name;
    }

    public Class<T> typeClass() {
        return this.typeClass;
    }

    public Class<C> contextClass() {
        return this.contextClass;
    }

    /** What the providers registered here answer at {@code pos} (null: none, or none for this side). */
    public @Nullable T fromProviders(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity be, C context) {
        if (state == null) {
            state = level.getBlockState(pos);
        }
        List<IBlockCapabilityProvider<T, C>> forBlock = this.byBlock.get(state.getBlock());
        if (forBlock != null) {
            if (be == null && state.hasBlockEntity()) {
                be = level.getBlockEntity(pos);
            }
            for (IBlockCapabilityProvider<T, C> p : forBlock) {
                T t = p.getCapability(level, pos, state, be, context);
                if (t != null) {
                    return t;
                }
            }
        }
        if (be == null && state.hasBlockEntity()) {
            be = level.getBlockEntity(pos);
        }
        if (be != null) {
            List<ICapabilityProvider<BlockEntity, C, T>> forType = this.byBlockEntity.get(be.getType());
            if (forType != null) {
                for (ICapabilityProvider<BlockEntity, C, T> p : forType) {
                    T t = p.getCapability(be, context);
                    if (t != null) {
                        return t;
                    }
                }
            }
        }
        return null;
    }

    /** NeoForge's lookup: the providers here, and for NeoForge's own capabilities Forge's of the block entity. */
    public @Nullable T getCapability(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity be, C context) {
        T t = fromProviders(level, pos, state, be, context);
        if (t != null || this.forge == null) {
            return t;
        }
        if (be == null) {
            be = level.getBlockEntity(pos);
        }
        if (be == null || CapabilityBridge.isBridged(be)) {
            return null;                                   // the mod's own: its providers already answered
        }
        return be.getCapability(this.forge, (Direction) context).resolve().orElse(null);
    }

    public boolean hasProviders(Block block) {
        return this.byBlock.containsKey(block);
    }

    <BE extends BlockEntity> void addBlockEntity(BlockEntityType<BE> type, ICapabilityProvider<? super BE, C, T> provider) {
        @SuppressWarnings("unchecked")
        ICapabilityProvider<BlockEntity, C, T> p = (ICapabilityProvider<BlockEntity, C, T>) provider;
        this.byBlockEntity.computeIfAbsent(type, t -> new ArrayList<>()).add(p);
    }

    void addBlock(Block block, IBlockCapabilityProvider<T, C> provider) {
        this.byBlock.computeIfAbsent(block, b -> new ArrayList<>()).add(provider);
    }

    @Override
    public String toString() {
        return "BlockCapability[" + this.name + "]";
    }
}
