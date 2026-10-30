package dev.groundburst.content.defence;

import java.util.function.Consumer;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An interceptor as players see it (DESIGN.md §18.2): its flight lives in {@link Interceptors}, which moves this along
 * every tick, pointed along its path. Never saved. The client trails its exhaust behind it.
 */
public class InterceptorEntity extends Entity {
    /** Client hook: its flame and smoke trail (set by the client's Spectacle). */
    public static Consumer<InterceptorEntity> exhaustHook = e -> {};

    public InterceptorEntity(EntityType<? extends InterceptorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    /** Moved along its flight from {@code was} to {@code now}, pointed the way it is going. */
    void follow(Vec3 was, Vec3 now) {
        Vec3 v = now.subtract(was);
        setDeltaMovement(v);
        setPos(now);
        if (v.lengthSqr() > 1e-6) {
            setYRot((float) Math.toDegrees(Math.atan2(v.x, v.z)));
            // Tilt from straight up (0) through level (90) to straight down (180), stored 90 lower (pitch is -90..90).
            setXRot((float) Math.toDegrees(Math.atan2(Math.sqrt(v.x * v.x + v.z * v.z), v.y)) - 90);
        }
    }

    /** Degrees from straight up: 0 climbing out of its cell, 90 level. */
    public float tilt(float partialTicks) {
        return getXRot(partialTicks) + 90;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            exhaustHook.accept(this);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512 * 512;
    }

    /** Its flight is the server's (Interceptors), never saved; the entity is only its picture. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}
}
