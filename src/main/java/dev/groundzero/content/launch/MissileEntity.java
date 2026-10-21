package dev.groundzero.content.launch;

import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundzero.content.ordnance.charge.BombBlock;
import dev.groundzero.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A missile in flight (DESIGN.md §17.5, basic version): lit on its pad, it burns there for 2 s, then flies a fixed
 * curve to its target: straight up off the pad, over the top of an arc whose height grows with the range, and down
 * onto the target from high above, speeding up as it goes. It flies through loaded chunks, keeping the chunk it is in
 * (and its target's) loaded. Whatever it hits on the way, or the ground at the target, sets off its warhead: any bomb,
 * going off as that bomb does (a bunker buster bores in first).
 */
public class MissileEntity extends Entity {
    private static final EntityDataAccessor<Boolean> BURNING = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);

    /** Ticks it burns on the pad before lifting off. */
    public static final int IGNITION = 40;
    /** Cruising speed along its path (blocks a tick), and the arc's height for a range: 35% of it, 40-220 blocks. */
    public static final double SPEED = 3.5, APEX_SHARE = 0.35, MIN_APEX = 40, MAX_APEX = 220;
    public static final int MAX_RANGE = 1024;

    /** Client hook: the Spectacle's exhaust flame and smoke trail (set by the client). */
    public static Consumer<MissileEntity> exhaustHook = e -> {};

    private Vec3 start = Vec3.ZERO, target = Vec3.ZERO;
    private ItemStack warhead = ItemStack.EMPTY;
    private @Nullable UUID owner;
    private int flightTicks = 100;
    private double apex = MIN_APEX;
    private long ticketChunk = Long.MIN_VALUE, targetChunk = Long.MIN_VALUE;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** A missile lit at {@code pad} (its tail), bound for {@code target}, carrying {@code warhead}. */
    public static MissileEntity launch(ServerLevel level, Vec3 pad, Vec3 target, ItemStack warhead, @Nullable Entity owner) {
        MissileEntity m = new MissileEntity(GZLaunch.MISSILE.get(), level);
        m.start = pad;
        m.target = target;
        m.warhead = warhead.copyWithCount(1);
        m.owner = owner == null ? null : owner.getUUID();
        double range = Math.sqrt(Mth.square(target.x - pad.x) + Mth.square(target.z - pad.z));
        m.apex = Mth.clamp(range * APEX_SHARE, MIN_APEX, MAX_APEX);
        m.flightTicks = (int) Mth.clamp((range + 2 * m.apex) / SPEED, 60, 1200);
        m.setPos(pad);
        level.addFreshEntity(m);
        level.playSound(null, pad.x, pad.y, pad.z, GZLaunch.MISSILE_LAUNCH.get(), SoundSource.BLOCKS, 8f, 1f);
        return m;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BURNING, true);
        builder.define(AGE, 0);
    }

    /** Ticks since it was lit (synced: the client draws its ignition and exhaust from it). */
    public int age() {
        return this.entityData.get(AGE);
    }

    /** Still on the pad, burning, not yet climbing. */
    public boolean igniting() {
        return age() < IGNITION;
    }

    public ItemStack warhead() {
        return this.warhead;
    }

    public Vec3 target() {
        return this.target;
    }

    // --- flight ---------------------------------------------------------------------------------------------------------
    /** Where it is {@code u} (0-1) of the way along its path: a cubic curve up off the pad and down onto the target. */
    private Vec3 path(double u) {
        Vec3 up = new Vec3(0, this.apex * 1.35, 0);
        Vec3 p1 = this.start.add(up);
        Vec3 back = new Vec3(this.start.x - this.target.x, 0, this.start.z - this.target.z).scale(0.12);
        Vec3 p2 = this.target.add(up).add(back);
        double v = 1 - u;
        return this.start.scale(v * v * v).add(p1.scale(3 * v * v * u)).add(p2.scale(3 * v * u * u)).add(this.target.scale(u * u * u));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            exhaustHook.accept(this);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        int age = age() + 1;
        this.entityData.set(AGE, age);
        keepLoaded(level);
        if (age % 40 == 1) {
            level.playSound(null, this, GZLaunch.MISSILE_ENGINE.get(), SoundSource.BLOCKS, 6f, 1f);
        }
        if (age < IGNITION) {
            return;
        }
        // The ground at the target, once its chunk is in (it is ticket-loaded from launch).
        BlockPos tc = BlockPos.containing(this.target);
        if (level.hasChunkAt(tc)) {
            this.target = new Vec3(this.target.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, tc.getX(), tc.getZ()), this.target.z);
        }
        double s = Math.min(1, (age - IGNITION) / (double) this.flightTicks);
        double u = Math.pow(s, 1.25);                         // slow off the pad, fastest coming down
        Vec3 from = position(), to = path(u);
        Vec3 v = to.subtract(from);
        if (s > 0.1) {
            BlockHitResult hit = level.clip(new ClipContext(from, to.add(v.normalize().scale(0.5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                impact(level, hit.getLocation());
                return;
            }
        }
        setDeltaMovement(v);
        setPos(to);
        if (v.lengthSqr() > 1e-6) {
            setYRot((float) Math.toDegrees(Math.atan2(v.x, v.z)));
            setXRot((float) Math.toDegrees(Math.atan2(Math.sqrt(v.x * v.x + v.z * v.z), v.y)));
        }
        if (s >= 1) {
            impact(level, this.target);
        }
    }

    /** It hits: its warhead goes off where it hit (a bunker buster bores in first; a dud of a stack does nothing). */
    private void impact(ServerLevel level, Vec3 at) {
        Entity by = this.owner == null ? null : level.getEntity(this.owner);
        if (this.warhead.getItem() instanceof BlockItem b && b.getBlock() instanceof BombBlock bomb) {
            Charges.impact(level, bomb.kind, BlockPos.containing(at.add(0, 0.5, 0)), by);
        }
        release(level);
        discard();
    }

    /** Keeps the chunk it is in, and its target's, loaded (missiles fly far beyond any player). */
    private void keepLoaded(ServerLevel level) {
        long here = net.minecraft.world.level.ChunkPos.pack(BlockPos.containing(position()).getX() >> 4, BlockPos.containing(position()).getZ() >> 4);
        if (here != this.ticketChunk) {
            if (this.ticketChunk != Long.MIN_VALUE) {
                GZLaunch.TICKETS.forceChunk(level, getUUID(), net.minecraft.world.level.ChunkPos.getX(this.ticketChunk),
                        net.minecraft.world.level.ChunkPos.getZ(this.ticketChunk), false, true);
            }
            GZLaunch.TICKETS.forceChunk(level, getUUID(), net.minecraft.world.level.ChunkPos.getX(here), net.minecraft.world.level.ChunkPos.getZ(here), true, true);
            this.ticketChunk = here;
        }
        long t = net.minecraft.world.level.ChunkPos.pack(BlockPos.containing(this.target).getX() >> 4, BlockPos.containing(this.target).getZ() >> 4);
        if (t != this.targetChunk) {
            GZLaunch.TICKETS.forceChunk(level, getUUID(), net.minecraft.world.level.ChunkPos.getX(t), net.minecraft.world.level.ChunkPos.getZ(t), true, true);
            this.targetChunk = t;
        }
    }

    private void release(ServerLevel level) {
        for (long c : new long[] {this.ticketChunk, this.targetChunk}) {
            if (c != Long.MIN_VALUE) {
                GZLaunch.TICKETS.forceChunk(level, getUUID(), net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false, true);
            }
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

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putDouble("sx", this.start.x);
        output.putDouble("sy", this.start.y);
        output.putDouble("sz", this.start.z);
        output.putDouble("tx", this.target.x);
        output.putDouble("ty", this.target.y);
        output.putDouble("tz", this.target.z);
        output.putInt("age", age());
        output.putInt("flight", this.flightTicks);
        output.putDouble("apex", this.apex);
        output.store("warhead", ItemStack.OPTIONAL_CODEC, this.warhead);
        if (this.owner != null) {
            output.putString("owner", this.owner.toString());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.start = new Vec3(input.getDoubleOr("sx", 0), input.getDoubleOr("sy", 0), input.getDoubleOr("sz", 0));
        this.target = new Vec3(input.getDoubleOr("tx", 0), input.getDoubleOr("ty", 0), input.getDoubleOr("tz", 0));
        this.entityData.set(AGE, input.getIntOr("age", 0));
        this.flightTicks = input.getIntOr("flight", 100);
        this.apex = input.getDoubleOr("apex", MIN_APEX);
        this.warhead = input.read("warhead", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.owner = input.getString("owner").map(UUID::fromString).orElse(null);
    }
}
