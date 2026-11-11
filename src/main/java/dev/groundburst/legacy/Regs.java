package dev.groundburst.legacy;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The registries a save or a load is given on 1.21 (1.20.1's save and load are given none): the running server's, or
 * else the built-in ones (the client finds its own: dev.groundburst.legacy.client.ClientLifecycle).
 */
public final class Regs {
    private Regs() {}

    private static final RegistryAccess BUILT_IN = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    /** The client's registries, when it has a world (set on the client only). */
    public static java.util.function.Supplier<RegistryAccess> client = () -> null;

    public static RegistryAccess access() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        RegistryAccess c = client.get();
        return c != null ? c : BUILT_IN;
    }
}
