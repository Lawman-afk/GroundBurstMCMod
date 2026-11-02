package dev.groundburst.content.artillery;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Where a gun's crewman sits (DESIGN.md §20.1): an invisible seat beside the gun. It goes as soon as nobody sits in it
 * or its gun is gone; Shift gets up, as from any seat.
 */
public class GunSeat extends Entity {
    private static final EntityDataAccessor<BlockPos> GUN = SynchedEntityData.defineId(GunSeat.class, EntityDataSerializers.BLOCK_POS);
    /** How far down from the seat its rider is put: enough to sit them on the ground rather than over it. */
    private static final double SINK = 0.5;

    public GunSeat(EntityType<? extends GunSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** A seat for the gun at {@code gun}, at {@code at}, facing {@code yaw} (the world's, as entities have it). */
    static GunSeat place(ServerLevel level, BlockPos gun, Vec3 at, float yaw) {
        GunSeat seat = new GunSeat(GBArtillery.SEAT.get(), level);
        seat.entityData.set(GUN, gun);
        seat.setPos(at);
        seat.setYRot(yaw);
        seat.setYHeadRot(yaw);
        level.addFreshEntity(seat);
        return seat;
    }

    /** The gun this seat is at. */
    public BlockPos gun() {
        return this.entityData.get(GUN);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GUN, BlockPos.ZERO);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && (this.tickCount > 5 && getPassengers().isEmpty()
                || !(server.getBlockEntity(gun()) instanceof MortarBlockEntity))) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0, -SINK, 0);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return position();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.entityData.set(GUN, BlockPos.of(input.getLongOr("gun", 0)));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putLong("gun", gun().asLong());
    }
}
