package dev.groundburst.content.artillery;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.blast.Blasts;
import dev.groundburst.content.effect.EmpFields;
import dev.groundburst.content.effect.Frost;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.Shrapnel;
import dev.groundburst.content.ordnance.ThrownExplosive;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import dev.groundburst.port.ValueInput;
import dev.groundburst.port.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An artillery shell in flight (DESIGN.md §20.1): a true parabola (Ballistics), bursting on whatever it meets with
 * what it holds. While it flies it keeps the chunk it is in and the one it is about to reach ticking (beyond the
 * simulation distance it would stop in the air). Not saved: a shell in the air when the world closes is gone.
 */
public class ArtilleryShell extends Projectile {
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(ArtilleryShell.class, EntityDataSerializers.INT);
    /** Client hook, each tick a shell flies (its whistle, set by the client). */
    public static Consumer<ArtilleryShell> clientHook = s -> {};

    /** Smoke: canisters in a line across the line of fire, this far apart. */
    public static final int SMOKE_CANISTERS = 3;
    public static final double SMOKE_SPACING = 4.5;
    /** Chemical: chlorine canisters. */
    public static final int GAS_CANISTERS = 2;
    /** Cryo and EMP: how far round, for how long. */
    public static final float CRYO_RADIUS = 7, EMP_RADIUS = 16;
    public static final int CRYO_TICKS = 240, EMP_TICKS = 400;

    private final LongOpenHashSet held = new LongOpenHashSet();

    public ArtilleryShell(EntityType<? extends ArtilleryShell> type, Level level) {
        super(type, level);
    }

    /** A {@code calibre} shell of {@code type} away from {@code at} on the curve with launch velocity {@code u}. */
    public static ArtilleryShell launch(ServerLevel level, Vec3 at, Vec3 u, Calibre calibre, ShellType type, @Nullable Entity owner) {
        ArtilleryShell s = new ArtilleryShell(GBArtillery.SHELL.get(), level);
        s.setOwner(owner);
        s.entityData.set(TYPE, calibre.ordinal() * 16 + type.ordinal());
        s.setPos(at);
        s.setDeltaMovement(u.add(0, Ballistics.G / 2, 0));     // half a tick's gravity up: its ticks lie on the curve
        s.orient();
        s.xRotO = s.getXRot();
        s.yRotO = s.getYRot();
        s.hold(level);
        level.addFreshEntity(s);
        return s;
    }

    public ShellType type() {
        return ShellType.of(this.entityData.get(TYPE) & 15);
    }

    public Calibre calibre() {
        return Calibre.of(this.entityData.get(TYPE) >> 4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TYPE, 0);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement().add(0, -Ballistics.G, 0);
        setDeltaMovement(v);
        Vec3 from = position(), to = from.add(v);
        HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hit.getType() != HitResult.Type.MISS) {
            to = hit.getLocation();
        }
        EntityHitResult struck = ProjectileUtil.getEntityHitResult(level(), this, from, to, getBoundingBox().expandTowards(v).inflate(1),
                this::canHitEntity);
        if (struck != null) {
            hit = struck;
        }
        if (hit.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
            if (level() instanceof ServerLevel server) {
                Vec3 at = hit.getLocation().subtract(v.normalize().scale(0.15));
                burst(server, at, v);
            } else {
                setPos(hit.getLocation());                          // stop where it struck until the server's word
                setDeltaMovement(Vec3.ZERO);
            }
            return;
        }
        setPos(to);
        orient();
        if (level() instanceof ServerLevel server) {
            hold(server);
            if (getY() < level().getMinBuildHeight() - 64 || this.tickCount > 1200) {
                discard();
            }
        } else {
            if (this.tickCount > 1 && this.random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
            }
            clientHook.accept(this);
        }
    }

    /** Pointed along its flight. */
    private void orient() {
        Vec3 v = getDeltaMovement();
        setYRot((float) Math.toDegrees(Math.atan2(v.x, v.z)));
        setXRot((float) Math.toDegrees(Math.atan2(v.y, v.horizontalDistance())));
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof ArtilleryShell) && !(entity instanceof GunSeat) && !(entity instanceof Shrapnel)
                && !(entity instanceof ThrownExplosive);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    // --- bursting ----------------------------------------------------------------------------------------------------
    /** What it holds, let out at {@code at} (it came in at velocity {@code v}). */
    private void burst(ServerLevel level, Vec3 at, Vec3 v) {
        Entity owner = getOwner();
        Calibre calibre = calibre();
        switch (type()) {
            case HE -> {
                Blasts.detonate(level, at, Blasts.profile(calibre.key + "_shell_he"), owner);
                fragments(level, at, owner, this.random, calibre.fragments);
            }
            case INCENDIARY -> {
                Blasts.detonate(level, at, Blasts.profile(calibre.key + "_shell_incendiary"), owner);
                napalm(level, at, calibre.napalm, this.random);
            }
            case NUCLEAR -> dev.groundburst.content.ordnance.charge.Charges.impact(level,
                    dev.groundburst.content.ordnance.charge.ChargeKind.TACTICAL_NUCLEAR_WARHEAD, BlockPos.containing(at), owner);
            case SMOKE -> canisters(level, at, v, owner, new ItemStack(GBOrdnance.SMOKE_GRENADE.get()), SMOKE_CANISTERS, SMOKE_SPACING);
            case CHEMICAL -> canisters(level, at, v, owner, new ItemStack(GBOrdnance.GAS_GRENADE.get()), GAS_CANISTERS, 2.5);
            case CRYO -> {
                pop(level, at);
                Frost.freeze(level, at, CRYO_RADIUS, CRYO_TICKS, owner);
            }
            case EMP -> {
                pop(level, at);
                EmpFields.add(level, at, EMP_RADIUS, EMP_TICKS, owner);
            }
        }
        discard();
    }

    /** Its bursting charge going off: a crack and a puff (the shells with no blast of their own). */
    private static void pop(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5f, 1.6f);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.2, at.z, 14, 0.4, 0.2, 0.4, 0.05);
    }

    /** HE: fragments flung out evenly, more sideways than up (as a frag grenade's). */
    static void fragments(ServerLevel level, Vec3 at, @Nullable Entity owner, RandomSource r, int count) {
        for (int i = 0; i < count; i++) {
            double y = 1 - 2 * (i + 0.5) / count;
            double ring = Math.sqrt(1 - y * y);
            double a = i * 2.39996 + r.nextDouble() * 0.3;
            Vec3 dir = new Vec3(Math.cos(a) * ring, Math.abs(y) * 0.6 + 0.05, Math.sin(a) * ring).normalize();
            level.addFreshEntity(new Shrapnel(level, owner, at.add(dir.scale(0.3)), dir.scale(1.9 + r.nextDouble() * 0.6)));
        }
    }

    /** Smoke or gas: canisters thrown out in a line across the line of fire, each popping as it lands. */
    private static void canisters(ServerLevel level, Vec3 at, Vec3 v, @Nullable Entity owner, ItemStack item, int n, double spacing) {
        pop(level, at);
        Vec3 across = new Vec3(v.z, 0, -v.x);
        across = across.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : across.normalize();
        for (int i = 0; i < n; i++) {
            double k = (i - (n - 1) / 2.0) * spacing;
            Vec3 p = at.add(across.scale(k)).add(0, 0.3, 0);
            Vec3 throwV = across.scale(Math.signum(k) * 0.08).add(0, 0.12, 0);
            ThrownExplosive.canister(level, p, throwV, owner, item, 4 + i * 3);
        }
    }

    /**
     * Incendiary: napalm over most of the open ground within {@code radius} (thicker toward the middle), and anything
     * alive there set alight for 10 s.
     */
    static void napalm(ServerLevel level, Vec3 at, float radius, RandomSource r) {
        int n = (int) Math.ceil(radius);
        BlockState fire = GBOrdnance.NAPALM_FIRE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -n; dx <= n; dx++) {
            for (int dz = -n; dz <= n; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius || r.nextFloat() > 0.9f - 0.45f * (float) (d / radius)) {
                    continue;
                }
                pos.set(Mth.floor(at.x) + dx, Mth.floor(at.y) + 2, Mth.floor(at.z) + dz);
                for (int i = 0; i < 7 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) {
                    pos.move(Direction.DOWN);
                }
                if (level.getBlockState(pos).isAir() && fire.canSurvive(level, pos)) {
                    level.setBlockAndUpdate(pos, fire);
                }
            }
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius))) {
            if (e.position().distanceTo(at) <= radius && !e.fireImmune()) {
                e.igniteForSeconds(10);
            }
        }
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.4, at.z, 80, radius * 0.4, 0.4, radius * 0.4, 0.06);
    }

    // --- chunks ---------------------------------------------------------------------------------------------------------
    /** Keeps the chunk it is in and the one it reaches in a few ticks ticking; lets the ones behind it go. */
    private void hold(ServerLevel level) {
        LongOpenHashSet want = new LongOpenHashSet();
        want.add(ChunkPos.asLong(Mth.floor(getX()) >> 4, Mth.floor(getZ()) >> 4));
        Vec3 ahead = position().add(getDeltaMovement().scale(6));
        want.add(ChunkPos.asLong(Mth.floor(ahead.x) >> 4, Mth.floor(ahead.z) >> 4));
        for (long c : want) {
            if (this.held.add(c)) {
                GBArtillery.TICKETS.forceChunk(level, getUUID(), ChunkPos.getX(c), ChunkPos.getZ(c), true, true);
            }
        }
        for (long c : this.held.toLongArray()) {
            if (!want.contains(c)) {
                GBArtillery.TICKETS.forceChunk(level, getUUID(), ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
                this.held.remove(c);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server) {
            for (long c : this.held.toLongArray()) {
                GBArtillery.TICKETS.forceChunk(server, getUUID(), ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
            }
            this.held.clear();
        }
        super.remove(reason);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag_) { ValueOutput output = new ValueOutput(tag_, this.registryAccess());
        super.addAdditionalSaveData(output.tag());
        output.putInt("shell", this.entityData.get(TYPE));
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag_) { ValueInput input = new ValueInput(tag_, this.registryAccess());
        super.readAdditionalSaveData(input.tag());
        this.entityData.set(TYPE, input.getIntOr("shell", 0));
    }
}
