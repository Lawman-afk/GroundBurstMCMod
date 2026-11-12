package dev.groundburst.blast;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The terrain tables in use (DESIGN.md §21.10): the running server's (its datapacks' {@code groundburst:terrain_table}
 * entries, read once when first asked for and let go when it stops), and the mod's own, {@link TerrainTable#builtIn},
 * for an id the server has no table for or when there is no server (datagen).
 */
final class TerrainTables {
    private static volatile @Nullable Map<Identifier, TerrainTable> server;
    private static volatile @Nullable MinecraftServer from;
    private static @Nullable Map<Identifier, TerrainTable> builtIn;

    private TerrainTables() {}

    static TerrainTable get(Identifier id) {
        Map<Identifier, TerrainTable> s = server();
        TerrainTable t = s == null ? null : s.get(id);
        if (t == null) {
            t = builtIn().get(id);
        }
        if (t == null && s != null) {
            t = s.get(TerrainTable.CONVENTIONAL);          // an id nobody has: the server's conventional table
        }
        return t != null ? t : builtIn().get(TerrainTable.CONVENTIONAL);
    }

    private static synchronized Map<Identifier, TerrainTable> builtIn() {
        if (builtIn == null) {
            Map<Identifier, TerrainTable> m = TerrainTable.builtIn();
            m.forEach((id, t) -> t.bind(id));
            builtIn = m;
        }
        return builtIn;
    }

    private static @Nullable Map<Identifier, TerrainTable> server() {
        MinecraftServer now = ServerLifecycleHooks.getCurrentServer();
        if (now == null) {
            return null;
        }
        Map<Identifier, TerrainTable> s = server;
        if (s == null || from != now) {
            Map<Identifier, TerrainTable> m = new HashMap<>();
            now.registryAccess().lookup(TerrainTable.REGISTRY).ifPresent(r -> r.listElements().forEach(h -> {
                h.value().bind(h.key().identifier());
                m.put(h.key().identifier(), h.value());
            }));
            server = s = Map.copyOf(m);
            from = now;
        }
        return s;
    }

    /** The server has stopped: its tables go with it. */
    static void clear() {
        server = null;
        from = null;
    }
}
