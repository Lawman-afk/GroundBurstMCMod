package dev.groundburst.content.naval;

import org.jspecify.annotations.Nullable;

import dev.groundburst.blast.BlastProfile;
import dev.groundburst.blast.Blasts;
import dev.groundburst.blast.ShockReceiver;
import dev.groundburst.registry.GBDataComponents;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A depth charge thrown or rolled into the sea (DESIGN.md §14.6): through the air it flies as a heavy grenade does; in
 * water it sinks at 0.2 blocks a tick, tumbling, and its hydrostatic pistol fires it at its depth under the surface where
 * it went in, or on the bottom if that comes first. Out of water the pistol never fires: on dry ground it comes to rest
 * as an item. Caught under water in another blast's lethal reach, it goes off too.
 */
public class DepthCharge extends Projectile implements ItemSupplier, ShockReceiver {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(DepthCharge.class, EntityDataSerializers.ITEM_STACK);
    /** Rolled off a rack: where its axis points (degrees round, its dial that way); NaN, tumbling. */
    private static final EntityDataAccessor<Float> AXIS = SynchedEntityData.defineId(DepthCharge.class, EntityDataSerializers.FLOAT);

    private static final double GRAVITY = 0.045;
    /** In water: its motion's decay a tick and what it gains sinking, so it settles to {@link #SINK} blocks a tick down. */
    private static final double WATER_DRAG = 0.8;
    public static final double SINK = 0.2;
    /** Its depth settings (blocks under the surface), and the one it has before any is set. */
    public static final int[] DEPTHS = {3, 5, 8, 12, 16, 24};
    public static final int DEFAULT_DEPTH = 5;
    /** Its middle over its position (its box is 0.6 a side). */
    public static final double MIDDLE = 0.3;
    /** How fast a charge rolled off a rack rolls on in the air (degrees a tick). */
    public static final float ROLLING = 30;

    /** The surface where it went into the water (NaN until it does). */
    private double surface = Double.NaN;
    private int fuse = -1;
    /** Client only: the tumble angle and its last value, for the renderer. */
    public float spin, prevSpin;

    public DepthCharge(EntityType<? extends DepthCharge> type, Level level) {
        super(type, level);
    }

    public DepthCharge(Level level, LivingEntity owner, ItemStack stack) {
        this(GBNaval.DEPTH_CHARGE_ENTITY.get(), level);
        setOwner(owner);
        setPos(owner.getX(), owner.getEyeY() - 0.2, owner.getZ());
        setItem(stack.copyWithCount(1));
    }

    /** A depth charge let go at {@code at} moving at {@code velocity} (dropped from a dispenser): it tumbles as it goes. */
    public static DepthCharge release(ServerLevel level, Vec3 at, Vec3 velocity, ItemStack stack, @Nullable Entity owner) {
        return release(level, at, velocity, stack, owner, Float.NaN);
    }

    /**
     * The same rolled off a rack: lying on its side, its axis {@code axisYaw} degrees round (its dial that way), rolling on
     * as it rolled off (NaN: tumbling). (Its own data, not its rotation: 1.21.1's client turns a new projectile's rotation
     * to its motion.)
     */
    public static DepthCharge release(ServerLevel level, Vec3 at, Vec3 velocity, ItemStack stack, @Nullable Entity owner, float axisYaw) {
        DepthCharge d = new DepthCharge(GBNaval.DEPTH_CHARGE_ENTITY.get(), level);
        d.setOwner(owner);
        d.setItem(stack.copyWithCount(1));
        d.setPos(at.x, at.y - MIDDLE, at.z);
        d.setDeltaMovement(velocity);
        d.entityData.set(AXIS, axisYaw);
        level.addFreshEntity(d);
        return d;
    }

    /** Rolled off a rack: lying on its side, rolling about its own axis, not tumbling. */
    public boolean lying() {
        return !Float.isNaN(axisYaw());
    }

    /** A lying charge's axis, degrees round (its dial that way); NaN if it tumbles. */
    public float axisYaw() {
        return this.entityData.get(AXIS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(AXIS, Float.NaN);
    }

    public void setItem(ItemStack stack) {
        this.entityData.set(ITEM, stack);
    }

    @Override
    public ItemStack getItem() {
        return this.entityData.get(ITEM);
    }

    /** Its pistol's depth, blocks under the surface. */
    public int depth() {
        return depthOf(getItem());
    }

    public static int depthOf(ItemStack stack) {
        return stack.getOrDefault(GBDataComponents.NAVAL_DEPTH.get(), DEFAULT_DEPTH);
    }

    /** The surface it went in at (NaN until it has). */
    public double surface() {
        return this.surface;
    }

    @Override
    public void tick() {
        super.tick();
        this.prevSpin = this.spin;
        if (level() instanceof ServerLevel server && this.fuse > 0 && --this.fuse == 0) {
            goOff(server);
            return;
        }
        boolean wet = Water.water(level(), net.minecraft.core.BlockPos.containing(position().add(0, MIDDLE, 0)));
        if (wet && Double.isNaN(this.surface)) {
            this.surface = Water.surface(level(), position().add(0, MIDDLE, 0));
            if (!level().isClientSide()) {
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 1.2f, 0.7f);
            }
        }
        Vec3 v = wet ? getDeltaMovement().scale(WATER_DRAG).add(0, -SINK * (1 - WATER_DRAG), 0)
                : getDeltaMovement().add(0, -GRAVITY, 0).scale(0.985);
        setDeltaMovement(v);
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult b = (BlockHitResult) hit;
            if (wet) {
                setPos(b.getLocation().add(0, 0.05, 0));
                if (level() instanceof ServerLevel server) {
                    goOff(server);                         // on the bottom before its depth
                }
                return;
            }
            land(b, v);
        } else if (hit instanceof EntityHitResult e && !wet) {
            if (level() instanceof ServerLevel server) {
                e.getEntity().hurtServer(server, damageSources().thrown(this, getOwner()), 2f);   // a heavy drum on the head
            }
            setDeltaMovement(v.scale(-0.2));
        } else {
            setPos(position().add(v));
        }
        if (level().isClientSide()) {
            this.spin += (float) (wet ? 4 : lying() ? ROLLING : getDeltaMovement().length() * 30);
            if (wet && this.random.nextInt(3) == 0) {
                Vec3 p = position().add(0, MIDDLE, 0);
                level().addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE, p.x, p.y, p.z, 0, 0.05, 0);
            }
            return;
        }
        if (wet && !Double.isNaN(this.surface) && this.surface - (getY() + MIDDLE) >= depth()) {
            goOff((ServerLevel) level());                  // the pistol fires at its depth
        }
    }

    /** On dry ground: it bounces once or twice and comes to rest, then lies there as an item (its pistol never fires). */
    private void land(BlockHitResult hit, Vec3 v) {
        Direction face = hit.getDirection();
        Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        double into = v.dot(n);
        Vec3 out = v.subtract(n.scale(into)).scale(0.5).subtract(n.scale(into * 0.2));
        setPos(hit.getLocation().add(n.scale(0.03)));
        if (face == Direction.UP && out.lengthSqr() < 0.004) {
            if (level() instanceof ServerLevel server) {
                GBNaval.drop(server, position().add(0, 0.1, 0), getItem().copy());
                discard();
            }
            return;
        }
        setDeltaMovement(out);
        if (!level().isClientSide()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.25f, 1.6f);
        }
    }

    private void goOff(ServerLevel level) {
        Vec3 at = position().add(0, MIDDLE, 0);
        Entity by = getOwner();
        discard();
        Blasts.detonate(level, at, Blasts.profile("depth_charge"), by);
    }

    /** Another blast close by under water sets it off a moment later. */
    @Override
    public void shocked(ServerLevel level, Vec3 center, double distance, BlastProfile profile) {
        if (!isRemoved() && this.fuse < 0 && distance <= profile.lethal() && !Double.isNaN(this.surface)) {
            this.fuse = 2 + this.random.nextInt(4);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof SeaMine) && !(entity instanceof DepthCharge);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("item", ItemStack.CODEC, getItem());
        output.putDouble("surface", Double.isNaN(this.surface) ? -1e9 : this.surface);
        output.putInt("fuse", this.fuse);
        if (lying()) {
            output.putFloat("axis", axisYaw());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("item", ItemStack.CODEC).ifPresent(this::setItem);
        double s = input.getDoubleOr("surface", -1e9);
        this.surface = s < -1e8 ? Double.NaN : s;
        this.fuse = input.getIntOr("fuse", -1);
        this.entityData.set(AXIS, input.getFloatOr("axis", Float.NaN));
    }
}
