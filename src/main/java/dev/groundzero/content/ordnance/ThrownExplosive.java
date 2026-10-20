package dev.groundzero.content.ordnance;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundzero.blast.Blasts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown explosive in flight (DESIGN.md §14), one entity for every {@link GrenadeType}, told apart by the item it
 * carries. It flies under gravity and drag and, on hitting something, bounces (losing most of its speed into the
 * surface and some along it, coming to rest on the ground), sticks (to a block, or riding a creature), or goes off,
 * as its type says. The fuse counts from the throw. Going off is the explosion engine's ({@link Blasts}), plus the
 * type's own extra: shrapnel for the frag, a carpet of fire for the incendiary. A smoke grenade instead stays where it
 * lies and pours out smoke for 30 s, during which mobs in or targeting something in the cloud lose their target.
 */
public class ThrownExplosive extends Projectile implements ItemSupplier {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(ThrownExplosive.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> SMOKING = SynchedEntityData.defineId(ThrownExplosive.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(ThrownExplosive.class, EntityDataSerializers.BOOLEAN);

    private static final double GRAVITY = 0.045;
    /** Of the speed into a surface, how much comes back out; of the speed along it, how much is kept. */
    private static final double RESTITUTION = 0.35, FRICTION = 0.7;
    public static final int SMOKE_TICKS = 600;
    public static final float SMOKE_RADIUS = 5;
    public static final int SHRAPNEL = 24;
    public static final float FIRE_RADIUS = 4;

    /** Client hook: the Spectacle pours smoke out of a smoking grenade (set by the client; a no-op on servers). */
    public static Consumer<ThrownExplosive> smokeHook = e -> {};

    private int fuse;
    private int smokeTicks;
    private @Nullable Entity riding;
    private Vec3 ridingOffset = Vec3.ZERO;
    /** Client only: the tumble angle and its last value, for the renderer. */
    public float spin, prevSpin;

    public ThrownExplosive(EntityType<? extends ThrownExplosive> type, Level level) {
        super(type, level);
    }

    public ThrownExplosive(Level level, LivingEntity owner, ItemStack stack) {
        this(GZOrdnance.THROWN_EXPLOSIVE.get(), level);
        setOwner(owner);
        setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        setItem(stack.copyWithCount(1));
        this.fuse = type().fuse;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(SMOKING, false);
        builder.define(STUCK, false);
    }

    public void setItem(ItemStack stack) {
        this.entityData.set(ITEM, stack);
    }

    @Override
    public ItemStack getItem() {
        return this.entityData.get(ITEM);
    }

    public GrenadeType type() {
        return getItem().getItem() instanceof GrenadeItem g ? g.type() : GrenadeType.FRAG;
    }

    public boolean smoking() {
        return this.entityData.get(SMOKING);
    }

    public boolean stuck() {
        return this.entityData.get(STUCK);
    }

    public int smokeTicks() {
        return this.smokeTicks;
    }

    /** For GameTests: the ticks left on the fuse. */
    public int fuse() {
        return this.fuse;
    }

    /** For GameTests: sets the ticks left on the fuse. */
    public void setFuseForTest(int ticks) {
        this.fuse = ticks;
    }

    @Override
    public void tick() {
        super.tick();
        this.prevSpin = this.spin;
        if (smoking()) {
            this.smokeTicks++;
            setDeltaMovement(Vec3.ZERO);
            if (level() instanceof ServerLevel server) {
                if (this.smokeTicks % 10 == 0) {
                    blindMobs(server);
                }
                if (this.smokeTicks % 60 == 1) {
                    server.playSound(null, getX(), getY(), getZ(), GZOrdnance.SMOKE_HISS.get(), SoundSource.NEUTRAL, 0.7f, 1f);
                }
                if (this.smokeTicks >= SMOKE_TICKS) {
                    discard();
                }
            } else {
                smokeHook.accept(this);
            }
            return;
        }
        if (stuck()) {
            if (this.riding != null) {
                if (!this.riding.isAlive()) {
                    unstick();
                } else {
                    setPos(this.riding.position().add(this.ridingOffset));
                }
            }
        } else {
            move();
        }
        if (level().isClientSide()) {
            clientEffects();
        } else if (this.fuse > 0 && --this.fuse == 0) {
            goOff((ServerLevel) level());
        }
    }

    // --- flight --------------------------------------------------------------------------------------------------------
    private void move() {
        Vec3 v = getDeltaMovement().add(0, -GRAVITY, 0).scale(isInWater() ? 0.8 : 0.985);
        setDeltaMovement(v);
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
            if (hit instanceof BlockHitResult block) {
                hitBlock(block, v);
            } else if (hit instanceof EntityHitResult entity) {
                hitEntity(entity, v);
            }
        } else {
            setPos(position().add(v));
        }
        if (level().isClientSide()) {
            this.spin += (float) getDeltaMovement().length() * 45;
        }
    }

    private void hitBlock(BlockHitResult hit, Vec3 v) {
        Direction face = hit.getDirection();
        Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        switch (type().behaviour) {
            case IMPACT -> {
                setPos(hit.getLocation().add(n.scale(0.1)));
                if (level() instanceof ServerLevel server) {
                    goOff(server);
                }
            }
            case STICK -> {
                setPos(hit.getLocation().add(n.scale(0.08)));
                setDeltaMovement(Vec3.ZERO);
                this.entityData.set(STUCK, true);
                playSound(GZOrdnance.STICKY_STICK.get(), 0.8f, 1f);
            }
            case BOUNCE -> {
                double into = v.dot(n);
                Vec3 along = v.subtract(n.scale(into));
                Vec3 out = along.scale(FRICTION).subtract(n.scale(into * RESTITUTION));
                if (face == Direction.UP && out.lengthSqr() < 0.004) {
                    out = Vec3.ZERO;                       // come to rest
                }
                setDeltaMovement(out);
                setPos(hit.getLocation().add(n.scale(0.03)));
                if (Math.abs(into) > 0.12) {
                    playSound(GZOrdnance.GRENADE_BOUNCE.get(), (float) Math.min(1, Math.abs(into) * 2), 0.9f + this.random.nextFloat() * 0.2f);
                }
            }
        }
    }

    private void hitEntity(EntityHitResult hit, Vec3 v) {
        Entity target = hit.getEntity();
        switch (type().behaviour) {
            case IMPACT -> {
                setPos(hit.getLocation());
                if (level() instanceof ServerLevel server) {
                    goOff(server);
                }
            }
            case STICK -> {
                this.riding = target;
                this.ridingOffset = hit.getLocation().subtract(target.position());
                setDeltaMovement(Vec3.ZERO);
                this.entityData.set(STUCK, true);
                playSound(GZOrdnance.STICKY_STICK.get(), 0.8f, 1f);
            }
            case BOUNCE -> {
                if (level() instanceof ServerLevel server) {
                    target.hurtServer(server, damageSources().thrown(this, getOwner()), 1f);   // a thrown can on the head
                }
                setDeltaMovement(v.scale(-0.25));
                setPos(hit.getLocation().add(v.normalize().scale(-0.1)));
            }
        }
    }

    private void unstick() {
        this.riding = null;
        this.entityData.set(STUCK, false);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof ThrownExplosive) && !(entity instanceof Shrapnel);
    }

    // --- going off -----------------------------------------------------------------------------------------------------
    private void goOff(ServerLevel level) {
        GrenadeType type = type();
        if (type == GrenadeType.SMOKE) {
            this.entityData.set(SMOKING, true);
            unstick();
            return;
        }
        Vec3 at = position().add(0, 0.15, 0);
        Blasts.detonate(level, at, Blasts.profile(type.id), getOwner());
        if (type == GrenadeType.FRAG) {
            shrapnel(level, at);
        } else if (type == GrenadeType.INCENDIARY) {
            fireCarpet(level, at);
        }
        discard();
    }

    /** 24 fragments flung out evenly in all directions (a little more sideways than up or down). */
    private void shrapnel(ServerLevel level, Vec3 at) {
        RandomSource r = this.random;
        for (int i = 0; i < SHRAPNEL; i++) {
            double y = 1 - 2 * (i + 0.5) / SHRAPNEL;          // Fibonacci sphere, flattened a little
            double ring = Math.sqrt(1 - y * y);
            double a = i * 2.39996 + r.nextDouble() * 0.3;
            Vec3 dir = new Vec3(Math.cos(a) * ring, y * 0.7, Math.sin(a) * ring).normalize();
            Shrapnel s = new Shrapnel(level, getOwner(), at.add(dir.scale(0.3)), dir.scale(1.8 + r.nextDouble() * 0.6));
            level.addFreshEntity(s);
        }
    }

    /** Fire on every open surface within 4 blocks (most of them), and everything alive there set burning for 8 s. */
    private void fireCarpet(ServerLevel level, Vec3 at) {
        int r = (int) Math.ceil(FIRE_RADIUS);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > FIRE_RADIUS * FIRE_RADIUS || this.random.nextFloat() > 0.85f) {
                    continue;
                }
                // The floor under the burst (searched down from just above it, so a roof overhead does not count).
                int x = (int) Math.floor(at.x) + dx, z = (int) Math.floor(at.z) + dz;
                pos.set(x, (int) Math.floor(at.y) + 1, z);
                for (int i = 0; i < 6 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) {
                    pos.move(Direction.DOWN);
                }
                if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
                    level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
                }
            }
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(FIRE_RADIUS))) {
            if (e.position().distanceTo(at) <= FIRE_RADIUS && !e.fireImmune()) {
                e.igniteForSeconds(8);
            }
        }
    }

    /** Mobs in the cloud, or hunting something in it, lose their target: they cannot see through it. */
    private void blindMobs(ServerLevel level) {
        float radius = smokeRadius();
        Vec3 c = position();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(radius + 24))) {
            LivingEntity target = mob.getTarget();
            boolean inside = mob.position().distanceTo(c) <= radius;
            boolean hidden = target != null && target.position().distanceTo(c) <= radius;
            if (target != null && (inside || hidden)) {
                mob.setTarget(null);
            }
        }
    }

    /** How far the cloud has spread (it billows out over its first 3 s). */
    public float smokeRadius() {
        return Math.min(SMOKE_RADIUS, 1 + this.smokeTicks / 15f);
    }

    // --- client --------------------------------------------------------------------------------------------------------
    private void clientEffects() {
        GrenadeType type = type();
        if (type == GrenadeType.DYNAMITE && this.random.nextInt(2) == 0) {
            // The sputtering fuse: sparks and a thread of smoke from the fuse end.
            Vec3 p = position().add(0, 0.25, 0);
            level().addParticle(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 0, 0.01, 0);
            level().addParticle(ParticleTypes.SMOKE, p.x, p.y + 0.05, p.z, 0, 0.02, 0);
            if (this.random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, (this.random.nextFloat() - 0.5) * 0.2,
                        this.random.nextFloat() * 0.15, (this.random.nextFloat() - 0.5) * 0.2);
            }
        }
    }

    // --- saving --------------------------------------------------------------------------------------------------------
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("item", ItemStack.CODEC, getItem());
        output.putInt("fuse", this.fuse);
        output.putBoolean("smoking", smoking());
        output.putInt("smoke_ticks", this.smokeTicks);
        output.putBoolean("stuck", stuck() && this.riding == null);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("item", ItemStack.CODEC).ifPresent(this::setItem);
        this.fuse = input.getIntOr("fuse", type().fuse);
        this.entityData.set(SMOKING, input.getBooleanOr("smoking", false));
        this.smokeTicks = input.getIntOr("smoke_ticks", 0);
        this.entityData.set(STUCK, input.getBooleanOr("stuck", false));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }
}
