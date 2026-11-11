package dev.groundburst.content.ordnance;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import dev.groundburst.port.ValueInput;
import dev.groundburst.port.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A shrapnel fragment (DESIGN.md §14, frag grenade, bouncing mine, claymore): a real projectile, fast and short-lived.
 * It deals 6 damage ({@code groundburst:shrapnel}) to the first creature it hits and stops at the first block (with a
 * spark). It lasts at most 14 ticks and is never saved. A claymore's fragments are heavier (7 damage) and every one
 * that hits counts, even several in the same instant (they ignore the target's moment of invulnerability).
 */
public class Shrapnel extends Projectile {
    public static final float DAMAGE = 6, CLAYMORE_DAMAGE = 7;
    private static final int LIFE = 14;

    private float damage = DAMAGE;
    private boolean everyHitCounts;

    public Shrapnel(EntityType<? extends Shrapnel> type, Level level) {
        super(type, level);
    }

    public Shrapnel(Level level, @Nullable Entity owner, Vec3 at, Vec3 velocity) {
        this(GBOrdnance.SHRAPNEL.get(), level);
        setOwner(owner);
        setPos(at);
        setDeltaMovement(velocity);
    }

    /** A claymore's fragment: heavier, and every one that hits counts. */
    public Shrapnel claymore() {
        this.damage = CLAYMORE_DAMAGE;
        this.everyHitCounts = true;
        return this;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > LIFE) {
            discard();
            return;
        }
        Vec3 v = getDeltaMovement().add(0, -0.01, 0).scale(0.96);
        setDeltaMovement(v);
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() == HitResult.Type.MISS) {
            setPos(position().add(v));
            return;
        }
        setPos(hit.getLocation());
        if (((Object) level()) instanceof ServerLevel server) {
            if (((Object) hit) instanceof EntityHitResult e) {
                if (this.everyHitCounts && ((Object) e.getEntity()) instanceof net.minecraft.world.entity.LivingEntity living) {
                    living.invulnerableTime = 0;
                }
                e.getEntity().hurt(dev.groundburst.legacy.Game.source(this, GBOrdnance.SHRAPNEL_DAMAGE, this, getOwner()), this.damage);
            } else {
                server.sendParticles(ParticleTypes.CRIT, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 3, 0.05, 0.05, 0.05, 0.1);
            }
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        // Fragments do not care who threw the grenade; they only pass through other ordnance.
        return entity.isAlive() && entity.isPickable() && !entity.isSpectator() && !(entity instanceof Shrapnel)
                && !(entity instanceof ThrownExplosive);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag_) { ValueOutput output = new ValueOutput(tag_, dev.groundburst.legacy.Regs.access());}

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag_) { ValueInput input = new ValueInput(tag_, dev.groundburst.legacy.Regs.access());}

    @Override
    public boolean hurt(DamageSource source, float damage) { if (!(((Object) this.level()) instanceof ServerLevel level)) return false;
        return false;
    }
}
