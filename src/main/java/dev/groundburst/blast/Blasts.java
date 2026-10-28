package dev.groundburst.blast;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Setting off blasts (DESIGN.md §11): the one entry point every weapon uses. */
public final class Blasts {
    public static final ResourceKey<DamageType> BLAST_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, GroundBurst.id("blast"));

    private Blasts() {}

    /** A blast profile by id (e.g. {@code groundburst:test/medium}). */
    public static ResourceKey<BlastProfile> profile(String path) {
        return ResourceKey.create(BlastProfile.REGISTRY, GroundBurst.id(path));
    }

    /**
     * Sets off a blast of the given profile at {@code center}. Returns null if the profile does not exist. The radii
     * are scaled by the server's radius multiplier and capped at its maximum; terrain is left alone when the server
     * says so (master switch, protected dimension) and the spawn area is spared when protected.
     */
    public static @Nullable Blast detonate(ServerLevel level, Vec3 center, ResourceKey<BlastProfile> key, @Nullable Entity source) {
        return detonate(level, center, key, source, 1f);
    }

    /** As {@link #detonate(ServerLevel, Vec3, ResourceKey, Entity)}, with every radius scaled by {@code scale} too (stacked C4). */
    public static @Nullable Blast detonate(ServerLevel level, Vec3 center, ResourceKey<BlastProfile> key, @Nullable Entity source, float scale) {
        Optional<BlastProfile> found = level.registryAccess().lookupOrThrow(BlastProfile.REGISTRY).getOptional(key);
        if (found.isEmpty()) {
            return null;
        }
        BlastProfile profile = found.get().scaled(GBServerConfig.RADIUS_MULTIPLIER.get().floatValue() * scale, GBServerConfig.MAX_RADIUS.get());
        boolean terrain = GBServerConfig.DESTROY_BLOCKS.get()
                && !GBServerConfig.PROTECTED_DIMENSIONS.get().contains(level.dimension().identifier().toString());
        BlockPos spawn = null;
        int spawnRadius = 0;
        if (GBServerConfig.PROTECT_SPAWN.get() && level.dimension() == Level.OVERWORLD) {
            spawn = level.getRespawnData().pos();
            spawnRadius = level.getServer() instanceof DedicatedServer server ? server.spawnProtectionRadius() : 16;
        }
        BlastJob job = new BlastJob(level, java.util.UUID.randomUUID(), key.identifier(), level.getGameTime(), center, profile,
                level.getRandom().nextLong(), source, terrain, spawn, spawnRadius);
        // Sound and visuals are the clients' (ClientBlasts, Spectacle), from the blast_start packet.
        BlastManager.of(level).add(job);
        return job;
    }

    /** The damage a blast deals: {@code groundburst:blast} (an explosion, so Blast Protection counts), caused by whoever set it off. */
    public static DamageSource damageSource(ServerLevel level, @Nullable Entity source, Vec3 center) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BLAST_DAMAGE), null, source, center);
    }
}
