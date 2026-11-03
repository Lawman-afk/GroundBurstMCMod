package dev.groundburst.port;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/** Command argument reads that 1.21.1 keeps private. */
public final class Args {
    private Args() {}

    /** A resource key argument as a key of {@code registry}, or {@code unknown} if it is another registry's. */
    public static <T> ResourceKey<T> getRegistryKey(CommandContext<CommandSourceStack> ctx, String name, ResourceKey<Registry<T>> registry,
            DynamicCommandExceptionType unknown) throws CommandSyntaxException {
        ResourceKey<?> key = ctx.getArgument(name, ResourceKey.class);
        var cast = key.cast(registry);
        if (cast.isEmpty()) {
            throw unknown.create(key);
        }
        return cast.get();
    }
}
