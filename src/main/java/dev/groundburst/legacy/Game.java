package dev.groundburst.legacy;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Minecraft 1.21's game calls 1.20.1 lacks or keeps to itself, each made from what 1.20.1 has. */
public final class Game {
    private Game() {}

    /** A damage source of a type (1.20.1 keeps DamageSources.source to itself). */
    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity causing) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, causing);
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity causing) {
        return source(level, type, causing, causing);
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type) {
        return source(level, type, null, null);
    }

    public static DamageSource source(Entity entity, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity causing) {
        return source(entity.level(), type, direct, causing);
    }

    public static DamageSource source(Entity entity, ResourceKey<DamageType> type, @Nullable Entity causing) {
        return source(entity.level(), type, causing, causing);
    }

    public static DamageSource source(Entity entity, ResourceKey<DamageType> type) {
        return source(entity.level(), type, null, null);
    }

    /** 1.20.5's vehicle: a boat or a minecart. */
    public static boolean isVehicle(Object e) {
        return e instanceof Boat || e instanceof AbstractMinecart;
    }

    /** 1.20.5's undead tag: 1.20.1 asks a living thing its mob type. */
    public static boolean undead(Entity e) {
        return e instanceof LivingEntity living && living.getMobType() == MobType.UNDEAD;
    }

    /** A stack's item and data, hashed (1.21's ItemStack.hashItemAndComponents). */
    public static int hashItemAndComponents(ItemStack stack) {
        return stack.isEmpty() ? 0 : Objects.hash(stack.getItem(), stack.getTag());
    }

    /** A holder's name in its registry (1.21's getRegisteredName). */
    public static String registeredName(Holder<?> holder) {
        return holder.unwrapKey().map(k -> k.location().toString()).orElse("[unregistered]");
    }

    /** How far a player sees, in chunks (1.20.1 keeps no player's own setting on the server: the server's). */
    public static int viewDistance(ServerPlayer player) {
        return player.server.getPlayerList().getViewDistance();
    }

    /** A container block's items dropped as it is replaced by another block (1.20.5's Containers.dropContentsOnDestroy). */
    public static void dropContentsOnDestroy(BlockState state, BlockState newState, Level level, BlockPos pos) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container container) {
                Containers.dropContents(level, pos, container);
                level.updateNeighbourForOutputSignal(pos, state.getBlock());
            }
        }
    }

    /** A copy of a pose (1.20.1's has no copy of its own). */
    public static PoseStack.Pose copy(PoseStack.Pose pose) {
        PoseStack stack = new PoseStack();
        stack.last().pose().set(pose.pose());
        stack.last().normal().set(pose.normal());
        return stack.last();
    }

    /** Whether a dropped stack is destroyed by this damage (1.20.5's ItemStack.canBeHurtBy: fire spares fireproof items). */
    public static boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return !stack.getItem().isFireResistant() || !source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
    }

    /** The box round two blocks and all between them (1.20.5's AABB.encapsulatingFullBlocks). */
    public static net.minecraft.world.phys.AABB encapsulatingFullBlocks(BlockPos a, BlockPos b) {
        return new net.minecraft.world.phys.AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    /** A copy of a fluid stack with another amount (NeoForge's copyWithAmount). */
    public static net.minecraftforge.fluids.FluidStack copyWithAmount(net.minecraftforge.fluids.FluidStack stack, int amount) {
        return stack.isEmpty() ? net.minecraftforge.fluids.FluidStack.EMPTY : new net.minecraftforge.fluids.FluidStack(stack, amount);
    }

    /** A direction turned by a pose's normal matrix (1.21's Pose.transformNormal). */
    public static org.joml.Vector3f transformNormal(PoseStack.Pose pose, float x, float y, float z, org.joml.Vector3f out) {
        return pose.normal().transform(out.set(x, y, z));
    }
}
