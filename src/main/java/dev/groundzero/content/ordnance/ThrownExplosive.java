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
import net.minecraft.world.level.block.state.BlockState;
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
 * lies and pours out smoke for 30 s, during which mobs in or targeting something in the cloud lose their target; a
 * poison gas grenade does the same with chlorine, which also poisons. A toxin grenade splashes toxin over the ground,
 * and a Molotov cocktail bursts into fire with no blast.
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
    public static final float MOLOTOV_RADIUS = 3;
    public static final float TOXIN_RADIUS = 3.5f;

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
                    if (type() == GrenadeType.POISON_GAS) {
                        gas(server);
                    }
                }
                if (this.smokeTicks % 60 == 1) {
                    server.playSound(null, getX(), getY(), getZ(), GZOrdnance.SMOKE_HISS.get(), SoundSource.NEUTRAL, 0.7f, 1f);
                }
                if (this.smokeTicks >= type().cloudTicks()) {
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
        if (type.cloudTicks() > 0) {
            this.entityData.set(SMOKING, true);
            unstick();
            return;
        }
        Vec3 at = position().add(0, 0.15, 0);
        if (type == GrenadeType.TOXIN) {
            splash(level, at);
            discard();
            return;
        }
        if (type == GrenadeType.MOLOTOV) {
            // No blast: the bottle breaks and the fuel goes up.
            fireCarpet(level, at, MOLOTOV_RADIUS);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 50, 1.2, 0.3, 1.2, 0.06);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 12, 0.8, 0.3, 0.8, 0.02);
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1f, 0.9f);
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1f, 0.8f);
            discard();
            return;
        }
        Blasts.detonate(level, at, Blasts.profile(type.id), getOwner());
        if (type == GrenadeType.FRAG) {
            shrapnel(level, at);
        } else if (type == GrenadeType.INCENDIARY) {
            fireCarpet(level, at, FIRE_RADIUS);
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

    /** Fire on every open surface within {@code radius} (most of them), and everything alive there set burning for 8 s. */
    private void fireCarpet(ServerLevel level, Vec3 at, float radius) {
        int r = (int) Math.ceil(radius);
        var pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius || this.random.nextFloat() > 0.85f) {
                    continue;
                }
                floor(level, at, dx, dz, pos);
                if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
                    level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
                }
            }
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius))) {
            if (e.position().distanceTo(at) <= radius && !e.fireImmune()) {
                e.igniteForSeconds(8);
            }
        }
    }

    /**
     * The open space on the floor under the burst, {@code dx, dz} blocks across from it, into {@code pos}: searched
     * down from just above the burst, so a roof overhead does not count.
     */
    private static void floor(ServerLevel level, Vec3 at, int dx, int dz, BlockPos.MutableBlockPos pos) {
        pos.set((int) Math.floor(at.x) + dx, (int) Math.floor(at.y) + 1, (int) Math.floor(at.z) + dz);
        for (int i = 0; i < 6 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) {
            pos.move(Direction.DOWN);
        }
    }

    /**
     * The toxin grenade bursting: toxin splashed over most of the floor within 3.5 blocks (a film of sludge that
     * poisons whatever walks in it), and a heavy dose for anything caught in the splash.
     */
    private void splash(ServerLevel level, Vec3 at) {
        int r = (int) Math.ceil(TOXIN_RADIUS);
        var pos = new BlockPos.MutableBlockPos();
        BlockState sludge = GZOrdnance.TOXIC_SLUDGE.get().defaultBlockState();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                float d2 = dx * dx + dz * dz;
                // Solid near the middle, spattered toward the edge.
                if (d2 > TOXIN_RADIUS * TOXIN_RADIUS || this.random.nextFloat() > 1.0f - 0.45f * d2 / (TOXIN_RADIUS * TOXIN_RADIUS)) {
                    continue;
                }
                floor(level, at, dx, dz, pos);
                BlockState here = level.getBlockState(pos);
                if ((here.isAir() || here.canBeReplaced() && here.getFluidState().isEmpty() && !here.is(GZOrdnance.TOXIC_SLUDGE.get()))
                        && sludge.canSurvive(level, pos)) {
                    level.setBlockAndUpdate(pos, sludge);
                }
            }
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(TOXIN_RADIUS))) {
            if (e.position().distanceTo(at) <= TOXIN_RADIUS) {
                Toxins.expose(level, e, 3f, 160, 1);
            }
        }
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0x8CE040, 1.6f), at.x, at.y + 0.3, at.z, 60,
                1.4, 0.3, 1.4, 0.05);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME, at.x, at.y + 0.3, at.z, 30, 0.8, 0.3, 0.8, 0.2);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.SPLASH_POTION_BREAK, SoundSource.NEUTRAL, 1f, 0.8f);
        level.playSound(null, at.x, at.y, at.z, GZOrdnance.STICKY_STICK.get(), SoundSource.NEUTRAL, 1f, 0.7f);
    }

    /** The poison gas cloud: everything alive in it breathes a dose every half second (less in a suit, none in a full one). */
    private void gas(ServerLevel level) {
        float radius = smokeRadius();
        Vec3 c = position();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius + 1))) {
            if (e.getEyePosition().distanceTo(c) <= radius || e.position().distanceTo(c) <= radius) {
                Toxins.expose(level, e, 1f, 100, 1);
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

    /** Whether {@code pos} is inside a smoking grenade's cloud (smoke or gas: neither can be seen through). */
    public static boolean inCloud(Level level, Vec3 pos) {
        for (ThrownExplosive g : level.getEntitiesOfClass(ThrownExplosive.class, new AABB(pos, pos).inflate(SMOKE_RADIUS + 1),
                ThrownExplosive::smoking)) {
            if (g.position().distanceTo(pos) <= g.smokeRadius()) {
                return true;
            }
        }
        return false;
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
            Vec3 p = position().add(0, 0.35, 0);
            level().addParticle(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 0, 0.01, 0);
            level().addParticle(ParticleTypes.SMOKE, p.x, p.y + 0.05, p.z, 0, 0.02, 0);
            if (this.random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, (this.random.nextFloat() - 0.5) * 0.2,
                        this.random.nextFloat() * 0.15, (this.random.nextFloat() - 0.5) * 0.2);
            }
        }
        if (type == GrenadeType.MOLOTOV) {
            // The burning rag in the neck.
            Vec3 p = position().add(0, 0.35, 0);
            level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0.02, 0);
            if (this.random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.SMOKE, p.x, p.y + 0.1, p.z, 0, 0.03, 0);
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
