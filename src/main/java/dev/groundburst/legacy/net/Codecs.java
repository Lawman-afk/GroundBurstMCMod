package dev.groundburst.legacy.net;

import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;

/**
 * The stream codecs Minecraft 1.21 keeps on its own classes (BlockPos.STREAM_CODEC, ItemStack.OPTIONAL_STREAM_CODEC...),
 * which 1.20.1's classes lack: the port names them here. Also codecs 1.20.1 lacks (an ingredient's: it reads ingredients
 * from JSON by hand).
 */
public final class Codecs {
    private Codecs() {}

    public static final StreamCodec<ByteBuf, BlockPos> BLOCK_POS = StreamCodec.of((b, v) -> ByteBufCodecs.wrap(b).writeBlockPos(v),
            b -> ByteBufCodecs.wrap(b).readBlockPos());
    public static final StreamCodec<FriendlyByteBuf, ItemStack> OPTIONAL_ITEM_STACK = StreamCodec.of(FriendlyByteBuf::writeItem,
            FriendlyByteBuf::readItem);
    public static final StreamCodec<FriendlyByteBuf, ItemStack> ITEM_STACK = OPTIONAL_ITEM_STACK;
    public static final StreamCodec<FriendlyByteBuf, FluidStack> FLUID_STACK = StreamCodec.of((b, v) -> v.writeToPacket(b),
            FluidStack::readFromPacket);
    public static final StreamCodec<ByteBuf, ResourceLocation> RESOURCE_LOCATION = StreamCodec.of(
            (b, v) -> ByteBufCodecs.wrap(b).writeResourceLocation(v), b -> ByteBufCodecs.wrap(b).readResourceLocation());
    public static final StreamCodec<ByteBuf, UUID> UUID_CODEC = StreamCodec.of((b, v) -> ByteBufCodecs.wrap(b).writeUUID(v),
            b -> ByteBufCodecs.wrap(b).readUUID());
    public static final StreamCodec<FriendlyByteBuf, Component> COMPONENT = StreamCodec.of(FriendlyByteBuf::writeComponent,
            FriendlyByteBuf::readComponent);
    public static final StreamCodec<FriendlyByteBuf, Ingredient> INGREDIENT = StreamCodec.of((b, v) -> v.toNetwork(b),
            Ingredient::fromNetwork);

    /** An ingredient as 1.21 writes one in JSON ({"item": ...}, {"tag": ...} or a list of them), read by 1.20.1's own reader. */
    public static final Codec<Ingredient> INGREDIENT_CODEC = Codec.PASSTHROUGH.comapFlatMap(dynamic -> {
        try {
            JsonElement json = dynamic.convert(JsonOps.INSTANCE).getValue();
            return com.mojang.serialization.DataResult.success(Ingredient.fromJson(json, false));
        } catch (RuntimeException e) {
            return com.mojang.serialization.DataResult.error(e::getMessage);
        }
    }, ingredient -> new Dynamic<>(JsonOps.INSTANCE, ingredient.toJson()));

    /** A stack, empty allowed (1.21's ItemStack.OPTIONAL_CODEC), as 1.20.1 saves one in NBT. */
    public static final Codec<ItemStack> OPTIONAL_ITEM_STACK_CODEC = net.minecraft.nbt.CompoundTag.CODEC.xmap(ItemStack::of,
            s -> s.save(new net.minecraft.nbt.CompoundTag()));

    /** A fluid stack, empty allowed (NeoForge's FluidStack.OPTIONAL_CODEC), as Forge 1.20.1 saves one in NBT. */
    public static final Codec<FluidStack> OPTIONAL_FLUID_STACK_CODEC = net.minecraft.nbt.CompoundTag.CODEC.xmap(FluidStack::loadFluidStackFromNBT,
            s -> s.writeToNBT(new net.minecraft.nbt.CompoundTag()));

    /** The same, as 1.21's "nonempty" codec (an empty ingredient refused). */
    public static final Codec<Ingredient> INGREDIENT_NONEMPTY_CODEC = INGREDIENT_CODEC.flatXmap(
            i -> i.isEmpty() ? com.mojang.serialization.DataResult.error(() -> "Empty ingredient") : com.mojang.serialization.DataResult.success(i),
            com.mojang.serialization.DataResult::success);
}
