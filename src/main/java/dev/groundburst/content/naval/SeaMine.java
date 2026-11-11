package dev.groundburst.content.naval;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.BlastProfile;
import dev.groundburst.blast.Blasts;
import dev.groundburst.blast.Hulls;
import dev.groundburst.blast.ShockReceiver;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import dev.groundburst.port.ValueInput;
import dev.groundburst.port.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A sea mine in the water (DESIGN.md §14.6). A moored one is laid with its sinker: the two sink together to the bottom,
 * the sinker settles, and the mine rises on its chain until its top is its depth under the surface, where the chain
 * holds it (a current pushes it aside, and it dips as it swings). A drifting one floats awash, carried by currents and a
 * tenth of the wind; after an hour adrift it floods and sinks (the Hague Convention's rule for mines adrift). Either is
 * live once its salt plug has dissolved, 5 s after it settles: then anything that touches its horns sets it off (a
 * boat, a creature but the sea's own, a block of another mod's ship), as does a projectile, a punch, or a blast close by;
 * a blast a little further off parts a moored mine's chain and it bobs up to drift. Used, it is made safe or armed again;
 * punched while safe, it is taken up. The server moves it; clients draw it where they are told it is.
 */
public class SeaMine extends Entity implements ShockReceiver {
    public enum Stage { SINKING, RISING, MOORED, ADRIFT }

    private static final EntityDataAccessor<Byte> STAGE = SynchedEntityData.defineId(SeaMine.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> MOORED = SynchedEntityData.defineId(SeaMine.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> ANCHOR_X = SynchedEntityData.defineId(SeaMine.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ANCHOR_Y = SynchedEntityData.defineId(SeaMine.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ANCHOR_Z = SynchedEntityData.defineId(SeaMine.class, EntityDataSerializers.FLOAT);

    // --- the figures (blocks), the models' (tools/assets/naval.py) -------------------------------------------------------
    /** The mine's centre over its position (its box is a block each way). */
    public static final double CENTRE = 0.5;
    /** The chain's shackle under the mine's centre. */
    public static final double SHACKLE = 8.7 / 16;
    /** Where the chain leaves the sinker (the top of its drum), over the sinker's bottom. */
    public static final double SINKER_TOP = 8.8 / 16;
    /** The mine's centre over the sinker's bottom while it sits on the sinker. */
    public static final double ON_SINKER = 18.0 / 16;
    /** The top horn's tip over the mine's centre. */
    public static final double TOP = 9.5 / 16;
    /** How fast mine and sinker sink, and the mine rises on its chain (blocks a tick). */
    static final double SINK = 0.25, RISE = 0.15;
    /** The salt plug: ticks from settling (or being armed again) to live. */
    public static final int ARM_TICKS = 100;
    /** Ticks adrift before it floods and sinks (an hour). */
    public static final int ADRIFT_LIFE = 72_000;
    /** Its depth settings: its top this many blocks under the surface (0: awash, where boats strike it). */
    public static final int[] DEPTHS = {0, 1, 2, 3, 5, 8};
    /** Its centre under the surface adrift (the top of it and its horns above the water). */
    static final double AWASH = 0.1;

    static final String T_SAFE = GBLang.add("message." + GroundBurst.MODID + ".sea_mine.safe", "Sea mine made safe");
    static final String T_ARMED = GBLang.add("message." + GroundBurst.MODID + ".sea_mine.armed", "Sea mine armed: live in 5 seconds");

    private boolean armed;
    /** Ticks until it is live once armed (0: live; -1: not counting, safe or still being laid). */
    private int plug = -1;
    private int depth;
    /** Its mooring: the chain's length from the sinker's top to the shackle, taut. */
    private double chain;
    private int adrift;
    /** Ticks to a sympathetic detonation (-1: none). */
    private int fuse = -1;
    private @Nullable UUID owner;

    public SeaMine(EntityType<? extends SeaMine> type, Level level) {
        super(type, level);
    }

    /**
     * Lays a mine with its centre at {@code at} (in or on water): a moored one (with its depth setting) sinks to the
     * bottom with its sinker first; a drifting one floats up awash. Armed: live 5 s after it settles.
     */
    public static SeaMine lay(ServerLevel level, Vec3 at, boolean moored, int depth, @Nullable Entity owner) {
        SeaMine m = new SeaMine(GBNaval.SEA_MINE.get(), level);
        m.setPos(at.x, at.y - CENTRE, at.z);
        m.setYRot(level.getRandom().nextFloat() * 360);
        m.entityData.set(MOORED, moored);
        m.depth = depth;
        m.owner = owner == null ? null : owner.getUUID();
        m.armed = true;
        m.setStage(moored ? Stage.SINKING : Stage.ADRIFT);
        if (!moored) {
            m.plug = ARM_TICKS;
        }
        level.addFreshEntity(m);
        return m;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(STAGE, (byte) Stage.ADRIFT.ordinal());
        this.entityData.define(MOORED, false);
        this.entityData.define(ANCHOR_X, 0f);
        this.entityData.define(ANCHOR_Y, 0f);
        this.entityData.define(ANCHOR_Z, 0f);
    }

    public Stage stage() {
        return Stage.values()[Mth.clamp(this.entityData.get(STAGE), 0, 3)];
    }

    private void setStage(Stage stage) {
        this.entityData.set(STAGE, (byte) stage.ordinal());
    }

    /** Laid with a sinker (a Moored Sea Mine), whether or not it is still moored. */
    public boolean moored() {
        return this.entityData.get(MOORED);
    }

    /** Where its sinker lies (the middle of its bottom). */
    public Vec3 anchor() {
        return new Vec3(this.entityData.get(ANCHOR_X), this.entityData.get(ANCHOR_Y), this.entityData.get(ANCHOR_Z));
    }

    private void setAnchor(Vec3 at) {
        this.entityData.set(ANCHOR_X, (float) at.x);
        this.entityData.set(ANCHOR_Y, (float) at.y);
        this.entityData.set(ANCHOR_Z, (float) at.z);
    }

    public Vec3 centre() {
        return position().add(0, CENTRE, 0);
    }

    public boolean armed() {
        return this.armed;
    }

    /** Whether touching it now sets it off: armed, its plug dissolved, settled in its place. */
    public boolean live() {
        return this.armed && this.plug == 0 && (stage() == Stage.MOORED || stage() == Stage.ADRIFT);
    }

    public int depth() {
        return this.depth;
    }

    public double chain() {
        return this.chain;
    }

    /** For GameTests: its plug dissolved now (live at once, if armed and settled). */
    public void dissolvePlugForTest() {
        if (this.armed) {
            this.plug = 0;
        }
    }

    // --- each tick --------------------------------------------------------------------------------------------------------
    @Override
    public void tick() {
        super.tick();                                      // fire, water, and the current's push (flowing water)
        if (!(((Object) level()) instanceof ServerLevel level) || isRemoved()) {
            return;
        }
        switch (stage()) {
            case SINKING -> sink(level);
            case RISING -> rise(level);
            case MOORED -> ride();
            case ADRIFT -> drift(level);
        }
        if (isRemoved()) {
            return;
        }
        if (this.fuse > 0 && --this.fuse == 0) {
            goOff(level);
            return;
        }
        if (this.armed && this.plug > 0 && (stage() == Stage.MOORED || stage() == Stage.ADRIFT) && --this.plug == 0) {
            level.playSound(null, getX(), getY(), getZ(), GBOrdnance.MINE_CLICK.get(), SoundSource.NEUTRAL, 0.5f, 0.7f);
        }
        if (live() && touched(level)) {
            goOff(level);
        }
    }

    /** Mine and sinker going down together, until the sinker lies on the bottom. */
    private void sink(ServerLevel level) {
        double bottom = getY() + CENTRE - ON_SINKER;
        double step = isInWater() ? SINK : 0.4;
        double floor = Water.floor(level, new Vec3(getX(), bottom, getZ()), (int) Math.ceil(step) + 1);
        if (!Double.isNaN(floor) && bottom - step <= floor) {
            setPos(getX(), floor + ON_SINKER - CENTRE, getZ());
            setAnchor(new Vec3(getX(), floor, getZ()));
            setStage(Stage.RISING);
            level.playSound(null, getX(), floor, getZ(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 0.8f, 0.6f);
            return;
        }
        setPos(getX(), getY() - step, getZ());
        if (getY() < level.getMinBuildHeight() - 16) {
            discard();
        }
    }

    /** The mine rising on its chain, the chain paying out off the sinker's drum, to the height it rides at. */
    private void rise(ServerLevel level) {
        Vec3 anchor = anchor();
        double target = rideHeight(level, anchor);
        double y = getY() + CENTRE;
        if (y + RISE >= target) {
            setPos(anchor.x, target - CENTRE, anchor.z);
            this.chain = Math.max(0, target - SHACKLE - (anchor.y + SINKER_TOP));
            setStage(Stage.MOORED);
            this.plug = this.armed ? ARM_TICKS : -1;
            return;
        }
        setPos(anchor.x, y + RISE - CENTRE, anchor.z);
        if (this.tickCount % 3 == 0) {
            level.playSound(null, getX(), anchor.y + 0.6, getZ(), SoundEvents.CHAIN_STEP, SoundSource.NEUTRAL, 0.7f,
                    0.75f + this.random.nextFloat() * 0.3f);
        }
    }

    /**
     * Where its centre rides on its chain: its top {@link #depth} blocks under the surface over its sinker, or sitting on
     * the sinker where the water is too shallow for its chain (or there is none).
     */
    private double rideHeight(ServerLevel level, Vec3 anchor) {
        double lowest = anchor.y + ON_SINKER;
        double surface = Water.surface(level, anchor.add(0, 0.5, 0));
        return Double.isNaN(surface) ? lowest : Math.max(lowest, surface - this.depth - TOP - 0.1);
    }

    /** Moored: buoyed up on its taut chain, pushed aside by a current, never further from the sinker than the chain lets it. */
    private void ride() {
        Vec3 anchor = anchor();
        if (this.chain <= 0.01) {
            setPos(anchor.x, anchor.y + ON_SINKER - CENTRE, anchor.z);
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 top = anchor.add(0, SINKER_TOP, 0);
        Vec3 v = getDeltaMovement().scale(0.8).add(0, 0.05, 0);
        Vec3 off = position().add(0, CENTRE - SHACKLE, 0).add(v).subtract(top);
        if (off.lengthSqr() > this.chain * this.chain) {
            off = off.normalize().scale(this.chain);
        }
        Vec3 at = top.add(off).add(0, SHACKLE - CENTRE, 0);
        Vec3 moved = at.subtract(position());
        setPos(at);
        setDeltaMovement(moved);
    }

    /** Adrift: awash and bobbing, carried by the current and the wind; out of the water it falls and rolls to a stop. */
    private void drift(ServerLevel level) {
        if (++this.adrift > ADRIFT_LIFE) {
            // As the Hague Convention asks of a mine adrift: it floods and sinks, harmless.
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, getX(), getY() + CENTRE, getZ(), 30, 0.4, 0.3, 0.4, 0.05);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.NEUTRAL, 0.8f, 0.7f);
            discard();
            return;
        }
        Vec3 v = getDeltaMovement();
        double surface = Water.surface(level, centre());
        if (!Double.isNaN(surface)) {
            double want = surface - AWASH + 0.04 * Math.sin((this.tickCount + getId() * 7) * 0.12);
            double vy = Mth.clamp((want - (getY() + CENTRE)) * 0.25, -0.1, 0.12);
            // It settles to a tenth of the wind's speed (blocks a tick), on top of the current's push.
            Vec3 wind = dev.groundburst.content.fallout.Wind.of(level).vector().scale(0.1 / 20);
            v = new Vec3(v.x * 0.9 + wind.x * 0.1, vy, v.z * 0.9 + wind.z * 0.1);
        } else {
            v = new Vec3(v.x * 0.7, v.y - 0.04, v.z * 0.7);
        }
        setDeltaMovement(v);
        move(MoverType.SELF, v);
    }

    // --- going off --------------------------------------------------------------------------------------------------------
    /** Whether anything touches its horns: a box round them, and the blocks of other mods' ships and contraptions. */
    private boolean touched(ServerLevel level) {
        Vec3 c = centre();
        AABB horns = new AABB(c.x - 0.62, c.y - 0.62, c.z - 0.62, c.x + 0.62, c.y + TOP + 0.15, c.z + 0.62);
        if (!level.getEntities(this, horns, SeaMine::strikes).isEmpty()) {
            return true;
        }
        if (!Hulls.any()) {
            return false;
        }
        for (Hulls.Hull hull : Hulls.near(level, c, 2)) {
            AABB local = Hulls.toLocal(hull, horns);
            boolean[] hit = {false};
            hull.blocks(local, (BlockPos pos, BlockState state) -> {
                if (!hit[0]) {
                    for (AABB b : state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos).toAabbs()) {
                        hit[0] |= b.move(pos).intersects(local);
                    }
                }
            });
            if (hit[0]) {
                return true;
            }
        }
        return false;
    }

    /**
     * What sets a mine off by touching it: a boat, a minecart, a player, any creature but the sea's own (fish, squid,
     * dolphins and axolotls slip past its horns). Not items, orbs, other mines and charges, or projectiles (they set it off
     * by striking it: {@link #hurtServer}).
     */
    static boolean strikes(Entity e) {
        if (!e.isAlive() || e.isSpectator() || e instanceof SeaMine || e instanceof DepthCharge || e instanceof ItemEntity
                || e instanceof ExperienceOrb || e instanceof Projectile) {
            return false;
        }
        MobCategory c = e.getType().getCategory();
        return c != MobCategory.WATER_CREATURE && c != MobCategory.WATER_AMBIENT && c != MobCategory.UNDERGROUND_WATER_CREATURE
                && c != MobCategory.AXOLOTLS;
    }

    private void goOff(ServerLevel level) {
        Vec3 c = centre();
        Entity by = this.owner == null ? null : level.getEntity(this.owner);
        discard();
        Blasts.detonate(level, c, Blasts.profile("sea_mine"), by);
    }

    /** A blast's shock: close by it sets this mine off a moment later; a little further off it parts a moored mine's chain. */
    @Override
    public void shocked(ServerLevel level, Vec3 center, double distance, BlastProfile profile) {
        if (isRemoved() || this.fuse > 0) {
            return;
        }
        if (distance <= profile.lethal()) {
            this.fuse = 2 + this.random.nextInt(4);
        } else if (distance <= profile.damage() && moored() && stage() != Stage.ADRIFT) {
            setStage(Stage.ADRIFT);
            this.chain = 0;
            this.adrift = 0;
            if (this.armed && this.plug < 0) {
                this.plug = ARM_TICKS;
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1f, 0.7f);
        }
    }

    /** Live, anything striking it sets it off; safe, a punch takes it up (its item, its sinker and chain with it). */
    @Override
    public boolean hurt(DamageSource source, float damage) { if (!(((Object) this.level()) instanceof ServerLevel level)) return false;
        if (isRemoved()) {
            return false;
        }
        if (live() && (source.getDirectEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION))) {
            goOff(level);
            return true;
        }
        if (!this.armed && ((Object) source.getDirectEntity()) instanceof Player player && source.getEntity() == player) {
            if (!player.getAbilities().instabuild) {
                GBNaval.drop(level, centre(), item());
            }
            discard();
            return true;
        }
        return false;
    }

    /** Used: made safe, or armed again (live 5 s later). */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (((Object) level()) instanceof ServerLevel level) {
            this.armed = !this.armed;
            this.plug = this.armed ? ARM_TICKS : -1;
            level.playSound(null, getX(), getY(), getZ(), (this.armed ? GBOrdnance.CHARGE_ARM : GBOrdnance.CHARGE_DISARM).get(), SoundSource.PLAYERS,
                    0.8f, 1f);
            player.displayClientMessage(Component.translatable(this.armed ? T_ARMED : T_SAFE), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Its item, as it was laid (a moored one with its depth setting). */
    public ItemStack item() {
        ItemStack s = new ItemStack(moored() ? GBNaval.MOORED_MINE.get() : GBNaval.DRIFTING_MINE.get());
        if (moored()) {
            dev.groundburst.legacy.component.Comps.set(s, GBDataComponents.NAVAL_DEPTH.get(), this.depth);
        }
        return s;
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        return item();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

        protected double getDefaultGravity() {
        return 0;
    }

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag_) { ValueOutput output = new ValueOutput(tag_, dev.groundburst.legacy.Regs.access());
        output.putInt("stage", stage().ordinal());
        output.putBoolean("moored", moored());
        output.putBoolean("armed", this.armed);
        output.putInt("plug", this.plug);
        output.putInt("depth", this.depth);
        output.putDouble("chain", this.chain);
        output.putInt("adrift", this.adrift);
        output.putInt("fuse", this.fuse);
        output.store("anchor", Vec3.CODEC, anchor());
        output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag_) { ValueInput input = new ValueInput(tag_, dev.groundburst.legacy.Regs.access());
        setStage(Stage.values()[Mth.clamp(input.getIntOr("stage", Stage.ADRIFT.ordinal()), 0, 3)]);
        this.entityData.set(MOORED, input.getBooleanOr("moored", false));
        this.armed = input.getBooleanOr("armed", true);
        this.plug = input.getIntOr("plug", -1);
        this.depth = input.getIntOr("depth", 0);
        this.chain = input.getDoubleOr("chain", 0);
        this.adrift = input.getIntOr("adrift", 0);
        this.fuse = input.getIntOr("fuse", -1);
        input.read("anchor", Vec3.CODEC).ifPresent(this::setAnchor);
        this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }
}
