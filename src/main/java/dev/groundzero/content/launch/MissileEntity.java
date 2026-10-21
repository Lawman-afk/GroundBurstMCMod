package dev.groundzero.content.launch;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A missile as players see it (DESIGN.md §17.5, basic version). The flight itself lives in {@link Flights}, which moves
 * this along every tick and puts it back wherever its chunk is loaded; the entity is never saved. Lit on its pad it
 * burns there for 2 s, then flies a fixed curve to its target: straight up off the pad, over the top of an arc whose
 * height grows with the range, and down onto the target from high above, speeding up as it goes. Its nose is painted as
 * the warhead it carries.
 */
public class MissileEntity extends Entity {
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    /** The warhead's id ("" for none): the client paints the nose cone to match. */
    private static final EntityDataAccessor<String> WARHEAD = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.STRING);

    /** Ticks it burns on the pad before lifting off. */
    public static final int IGNITION = 40;
    /** Cruising speed along its path (blocks a tick), and the arc's height for a range: 35% of it, 40-220 blocks. */
    public static final double SPEED = 3.5, APEX_SHARE = 0.35, MIN_APEX = 40, MAX_APEX = 220;
    public static final int MAX_RANGE = 1024;

    /** Client hook: the Spectacle's exhaust flame and smoke trail (set by the client). */
    public static Consumer<MissileEntity> exhaustHook = e -> {};

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** A missile lit at {@code pad} (its tail), bound for {@code target}, carrying {@code warhead} (see {@link Flights}). */
    public static Flights.Flight launch(ServerLevel level, Vec3 pad, Vec3 target, ItemStack warhead, @Nullable Entity owner) {
        return Flights.launch(level, pad, target, warhead, owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AGE, 0);
        builder.define(WARHEAD, "");
    }

    /** Ticks since it was lit (synced: the client draws its ignition and exhaust from it). */
    public int age() {
        return this.entityData.get(AGE);
    }

    /** Still on the pad, burning, not yet climbing. */
    public boolean igniting() {
        return age() < IGNITION;
    }

    /** A warhead's id for the missile's nose ("" for none or not a warhead). */
    public static String warheadId(ItemStack stack) {
        return LaunchSiloBlockEntity.isWarhead(stack) ? BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() : "";
    }

    void setWarheadShown(ItemStack warhead) {
        this.entityData.set(WARHEAD, warheadId(warhead));
    }

    /** The id of the warhead it carries, as the client knows it ("" for none). */
    public String warheadName() {
        return this.entityData.get(WARHEAD);
    }

    /** Moved along by its flight. */
    void follow(Vec3 pos, float yaw, float tilt, int age) {
        setDeltaMovement(pos.subtract(position()));
        setPos(pos);
        setYRot(yaw);
        // Tilt from straight up (0) through level (90) to straight down (180). Entity pitch is clamped to -90..90, so
        // it is stored 90 lower; tilt() adds it back.
        setXRot(tilt - 90);
        this.entityData.set(AGE, age);
    }

    /** Degrees from straight up: 0 on the pad, 90 level, up to 180 diving straight down. */
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

    /** Its flight is saved (Flights), not the entity: it is put back where its chunk loads. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}
}
