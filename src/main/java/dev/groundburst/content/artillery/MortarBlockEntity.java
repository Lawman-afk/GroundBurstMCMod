package dev.groundburst.content.artillery;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineInventory;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The 81 mm Mortar (DESIGN.md §20.1): crewed, laid on where its crewman aims at its own pace, firing a shell where its
 * tube points, spread as World of Tanks spreads its shots.
 *
 * <p>Laying: each aim is solved for the lowest of its four charges that reaches it on the high arc (45 to 85 degrees;
 * 16 to 200 blocks); the tube turns toward it at {@link #TRAVERSE} and {@link #ELEVATE} degrees a tick. Spread: its
 * aiming circle's radius is {@link #ACCURACY} blocks per 100 of range times its dispersion, which shrinks to a third
 * each {@link #AIM_TIME} ticks down to its least, the root of 1 + (k w)^2 while the tube turns at w degrees a second; a
 * shot multiplies it by {@link #BLOOM} (at most {@link #MAX_BLOOM}, as it is when just manned).
 */
public class MortarBlockEntity extends BlockEntity implements AnimatedMachine {
    // --- the gun -----------------------------------------------------------------------------------------------------
    /** The four charges' muzzle speeds, blocks a tick: 50, 100, 150 and 200 blocks at 45 degrees on the flat. */
    public static final double[] CHARGES = {1.58, 2.24, 2.74, 3.16};
    public static final float MIN_ELEVATION = 45, MAX_ELEVATION = 85, REST_ELEVATION = 60;
    public static final double MIN_RANGE = 16, MAX_RANGE = 200;
    /** How fast it is laid: degrees a tick round (40 a second) and up or down (20 a second). */
    public static final float TRAVERSE = 2f, ELEVATE = 1f;
    /** Ticks from one shot until it can fire again. */
    public static final int RELOAD = 50;
    /** Its aiming circle's radius fully aimed, blocks per 100 blocks of range. */
    public static final double ACCURACY = 1.6;
    /** Ticks for its circle to shrink to a third. */
    public static final double AIM_TIME = 40;
    /** A shot multiplies its spread by this, up to the most it can be (as it is when just manned). */
    public static final double BLOOM = 4, MAX_BLOOM = 6;
    /** How much laying it blooms its spread: per degree a second it turns. */
    public static final double TURN_BLOOM = 0.06;
    /** Shells of each kind its rack holds. */
    public static final int RACK = 16;
    /** Its tube's socket (blocks over its floor, and back from the block's middle toward its rest), the tube's length. */
    public static final double SOCKET_Y = 2.2 / 16, SOCKET_BACK = 2.0 / 16, TUBE = 20.0 / 16;
    /** The tube's collar (where the bipod's screw holds it), model pixels along the tube from the socket. */
    static final double COLLAR = 12;
    /**
     * The bipod's feet line and its screw's foot at rest (model pixels, y up and z back of the block's middle). The
     * bipod is built upright on its feet line and leaned back {@link #LEAN} to put the screw's foot there.
     */
    static final double FEET_Z = -8, SCREW_Y = 7, SCREW_Z = -4;
    static final double LEAN = Math.atan2(SCREW_Z - FEET_Z, SCREW_Y);
    private static final double SHRINK = Math.pow(1 / 3.0, 1 / AIM_TIME);
    /** Ticks a shell may fly while its fall is worked out. */
    static final int MAX_FLIGHT = 400;

    public static final String T_RACK_FULL = GBLang.add(GroundBurst.MODID + ".artillery.rack_full", "Its rack holds no more of these");
    public static final String T_MANNED = GBLang.add(GroundBurst.MODID + ".artillery.manned", "Someone is already manning it");
    public static final String T_NO_SHELLS = GBLang.add(GroundBurst.MODID + ".artillery.no_shells", "No %s shells: load some, or carry them");
    public static final String T_OUT_OF_RANGE = GBLang.add(GroundBurst.MODID + ".artillery.out_of_range", "Out of range");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final MachineInventory rack = new MachineInventory(ShellType.values().length,
            (slot, stack) -> stack.getItem() instanceof ShellItem s && s.type().ordinal() == slot, slot -> setChanged(), slot -> RACK);

    /** Its crewman (server; not saved: a seat does not outlast a restart). */
    private @Nullable ServerPlayer crew;
    /** Where its crewman wants it laid (null: nowhere yet). */
    private @Nullable Vec3 aim;
    /** The tube as it is laid: world yaw (atan2(dx, dz), degrees; NaN: at rest, its front) and elevation. */
    private float yaw = Float.NaN, elevation = REST_ELEVATION;
    private int charge = 2;
    private float dispersion = (float) MAX_BLOOM;
    private int reload;
    private ShellType selected = ShellType.HE;
    private boolean reachable;
    private int shots;
    private boolean dirty = true;

    // --- the client's copy ------------------------------------------------------------------------------------------------
    private int crewId = -1;
    private int[] counts = new int[ShellType.values().length];
    /** The tube as drawn (it swings toward the last state at the gun's own pace) and when it was last drawn. */
    private float shownYaw = Float.NaN, shownElevation = REST_ELEVATION;
    private double shownAt;
    /** When the client saw its last shot (game time; it kicks the tube). */
    private long firedAt = Long.MIN_VALUE / 2;

    public MortarBlockEntity(BlockPos pos, BlockState state) {
        super(GBArtillery.MORTAR_ENTITY.get(), pos, state);
    }

    public MachineInventory rack() {
        return this.rack;
    }

    // --- where it is --------------------------------------------------------------------------------------------------
    private Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(MortarBlock.FACING) ? s.getValue(MortarBlock.FACING) : Direction.NORTH;
    }

    /** The world yaw (atan2(dx, dz), degrees) its front faces: where its tube rests. */
    public float frontYaw() {
        return switch (facing()) {
            case SOUTH -> 0;
            case EAST -> 90;
            case WEST -> -90;
            default -> 180;
        };
    }

    /** The tube's socket on its baseplate. */
    public Vec3 socket() {
        Direction f = facing();
        return Vec3.atBottomCenterOf(this.worldPosition).add(-f.getStepX() * SOCKET_BACK, SOCKET_Y, -f.getStepZ() * SOCKET_BACK);
    }

    /** The muzzle with the tube laid at {@code yaw}, {@code elevation}. */
    public Vec3 muzzle(double yaw, double elevation) {
        return socket().add(Ballistics.velocity(TUBE, yaw, elevation));
    }

    /** Where its crewman sits: behind it and to its left as it rests. */
    private Vec3 seat() {
        Direction f = facing();
        return Vec3.atBottomCenterOf(this.worldPosition).add(-f.getStepX() * 0.75 + f.getStepZ() * 0.45, 0, -f.getStepZ() * 0.75 - f.getStepX() * 0.45);
    }

    // --- laying -------------------------------------------------------------------------------------------------------
    /** How it would be laid on {@code target}: the lowest charge that reaches it, the yaw and elevation, and whether it can. */
    public record Solution(int charge, float yaw, float elevation, boolean reachable) {}

    public Solution solve(Vec3 target) {
        Vec3 base = socket();
        float yaw = Ballistics.yaw(base, target);
        double h = Math.hypot(target.x - base.x, target.z - base.z);
        if (h < MIN_RANGE) {
            return new Solution(0, yaw, MAX_ELEVATION, false);
        }
        for (int k = 0; k < CHARGES.length; k++) {
            double e = REST_ELEVATION;
            boolean ok = false;
            for (int pass = 0; pass < 2; pass++) {               // from the muzzle as it would be laid (twice: it moves a little)
                Vec3 from = muzzle(yaw, e);
                double ek = Ballistics.highAngle(CHARGES[k], Math.hypot(target.x - from.x, target.z - from.z), target.y - from.y);
                ok = !Double.isNaN(ek) && ek >= MIN_ELEVATION && ek <= MAX_ELEVATION;
                if (!ok) {
                    break;
                }
                e = ek;
            }
            if (ok) {
                return new Solution(k, yaw, (float) e, h <= MAX_RANGE);
            }
        }
        return new Solution(CHARGES.length - 1, yaw, MIN_ELEVATION, false);
    }

    /** Its aiming circle's radius at {@code range} blocks, as it is now. */
    public double radius(double range) {
        return ACCURACY / 100 * range * this.dispersion;
    }

    // --- the crew ---------------------------------------------------------------------------------------------------------
    private @Nullable ServerPlayer crewman(ServerLevel level) {
        ServerPlayer p = this.crew;
        return p != null && seated(p, level) ? p : null;
    }

    /** Whether {@code p} sits in this gun's seat. */
    private boolean seated(ServerPlayer p, ServerLevel level) {
        return !p.isRemoved() && p.isAlive() && p.level() == level && p.getVehicle() instanceof GunSeat seat && seat.gun().equals(this.worldPosition);
    }

    /**
     * Whether {@code player} is its crewman: the one who manned it, or (nobody else manning it) whoever sits in its seat
     * (a crewman saved seated comes back seated; the gun does not keep its crew over a restart).
     */
    private boolean isCrew(ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level;
        ServerPlayer now = crewman(level);
        if (now == null && seated(player, level)) {
            this.crew = now = player;
            this.dispersion = (float) MAX_BLOOM;
            this.dirty = true;
        }
        return now == player;
    }

    /** Whether someone mans it now. */
    public boolean manned() {
        return this.level instanceof ServerLevel server ? crewman(server) != null : this.crewId >= 0;
    }

    /** A line over {@code player}'s hotbar (none for a player with no connection: a test's). */
    private static void tell(ServerPlayer player, Component line) {
        if (player.connection != null) {
            player.sendOverlayMessage(line);
        }
    }

    /** {@code player} mans it: they sit in its seat (if nobody else is manning it). */
    public boolean man(ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level;
        if (crewman(level) != null) {
            tell(player, Component.translatable(T_MANNED));
            return false;
        }
        GunSeat seat = GunSeat.place(level, this.worldPosition, seat(), -frontYaw());
        if (!player.startRiding(seat, true, true)) {
            seat.discard();
            return false;
        }
        this.crew = player;
        this.dispersion = (float) MAX_BLOOM;
        if (Float.isNaN(this.yaw)) {
            this.yaw = frontYaw();
        }
        Ballistics.Fall fall = Ballistics.fall(level, muzzle(this.yaw, this.elevation), Ballistics.velocity(CHARGES[this.charge], this.yaw, this.elevation),
                null, MAX_FLIGHT);
        this.aim = fall != null ? fall.at() : null;
        this.dirty = true;
        level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.BLOCKS, 0.7f, 0.9f);
        return true;
    }

    /** Its crewman's aim: a point on the ground. */
    public void aim(ServerPlayer player, Vec3 at) {
        if (!isCrew(player)) {
            return;
        }
        Vec3 base = socket();
        double h = Math.hypot(at.x - base.x, at.z - base.z);
        if (h > MAX_RANGE * 2 || Math.abs(at.y - base.y) > 400) {
            return;
        }
        this.aim = at;
    }

    /** Its crewman picks the shell to fire next. */
    public void select(ServerPlayer player, int shell) {
        if (isCrew(player)) {
            this.selected = ShellType.of(shell);
            this.dirty = true;
        }
    }

    // --- loading ---------------------------------------------------------------------------------------------------------
    /** Puts {@code stack} into its rack; returns what did not fit. */
    public ItemStack load(ItemStack stack) {
        if (!(stack.getItem() instanceof ShellItem shell)) {
            return stack;
        }
        ItemStack left = this.rack.insertDirect(shell.type().ordinal(), stack.copy(), false);
        this.dirty = true;
        return left;
    }

    /** Shells of {@code type} it can fire for {@code crewman}: its rack's and theirs (-1: without end, in creative). */
    private int count(@Nullable ServerPlayer crewman, ShellType type) {
        if (crewman != null && crewman.getAbilities().instabuild) {
            return -1;
        }
        int n = this.rack.getItem(type.ordinal()).getCount();
        if (crewman != null) {
            Inventory inv = crewman.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.getItem() instanceof ShellItem si && si.type() == type) {
                    n += s.getCount();
                }
            }
        }
        return n;
    }

    /** Takes one shell of {@code type}: from its rack, else from its crewman (none needed in creative). */
    private boolean take(ServerPlayer crewman, ShellType type) {
        if (crewman.getAbilities().instabuild) {
            return true;
        }
        int slot = type.ordinal();
        ItemStack in = this.rack.getItem(slot);
        if (!in.isEmpty()) {
            this.rack.setItem(slot, in.getCount() > 1 ? in.copyWithCount(in.getCount() - 1) : ItemStack.EMPTY);
            return true;
        }
        Inventory inv = crewman.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ShellItem si && si.type() == type) {
                s.shrink(1);
                inv.setChanged();
                return true;
            }
        }
        return false;
    }

    // --- firing ------------------------------------------------------------------------------------------------------
    /** Its crewman fires: a shell where the tube points, spread over its aiming circle. */
    public void fire(ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level;
        if (!isCrew(player) || this.reload > 0 || Float.isNaN(this.yaw)) {
            return;
        }
        if (!this.reachable) {
            tell(player, Component.translatable(T_OUT_OF_RANGE));
            return;
        }
        if (!take(player, this.selected)) {
            tell(player, Component.translatable(T_NO_SHELLS, this.selected.english));
            level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6f, 1.2f);
            return;
        }
        shoot(level, player, this.selected);
    }

    /** A shell of {@code type} away, spread over its circle as it is now (its crewman's: {@code by}). */
    void shoot(ServerLevel level, @Nullable ServerPlayer by, ShellType type) {
        double speed = CHARGES[this.charge];
        Vec3 muzzle = muzzle(this.yaw, this.elevation);
        Vec3 u = Ballistics.velocity(speed, this.yaw, this.elevation);
        Ballistics.Fall fall = Ballistics.fall(level, muzzle, u, null, MAX_FLIGHT);
        Vec3 launch = u;
        if (fall != null) {
            Vec3 laid = fall.at();
            double range = Math.hypot(laid.x - muzzle.x, laid.z - muzzle.z);
            Vec3 off = Ballistics.scatter(level.getRandom(), radius(range), fall.stretch(), this.yaw);
            double x = laid.x + off.x, z = laid.z + off.z;
            Vec3 target = new Vec3(x, Ballistics.ground(level, x, z, laid.y), z);
            float yaw2 = Ballistics.yaw(muzzle, target);
            double e2 = Ballistics.highAngle(speed, Math.hypot(target.x - muzzle.x, target.z - muzzle.z), target.y - muzzle.y);
            if (!Double.isNaN(e2) && Math.abs(e2 - this.elevation) < 15) {
                launch = Ballistics.velocity(speed, yaw2, e2);
            }
        }
        ArtilleryShell.launch(level, muzzle, launch, type, by);
        this.dispersion = (float) Math.min(MAX_BLOOM, this.dispersion * BLOOM);
        this.reload = RELOAD;
        this.shots++;
        this.dirty = true;
        setChanged();
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, GBSounds.MORTAR_FIRE.get(), SoundSource.BLOCKS, 6f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        Vec3 d = launch.normalize();
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 10, 0.15, 0.15, 0.15, 0.04);
        level.sendParticles(ParticleTypes.CLOUD, muzzle.x + d.x * 0.3, muzzle.y + d.y * 0.3, muzzle.z + d.z * 0.3, 10, 0.18, 0.18, 0.18, 0.06);
        level.sendParticles(ParticleTypes.POOF, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.1, this.worldPosition.getZ() + 0.5, 6, 0.4, 0.02, 0.4, 0.02);
    }

    /** Laid at once on {@code target}, fully aimed (the photographer's: no crewman). */
    public void layForTest(Vec3 target) {
        Solution s = solve(target);
        this.yaw = s.yaw();
        this.elevation = s.elevation();
        this.charge = s.charge();
        this.reachable = s.reachable();
        this.dispersion = 1;
        this.dirty = true;
    }

    /** A shell of {@code type} away now, as it is laid (the photographer's). */
    public void shootForTest(ServerLevel level, ShellType type) {
        shoot(level, null, type);
    }

    // --- ticking ---------------------------------------------------------------------------------------------------------
    public void serverTick(ServerLevel level) {
        if (Float.isNaN(this.yaw)) {
            this.yaw = frontYaw();
            this.dirty = true;
        }
        ServerPlayer crewman = crewman(level);
        if (crewman == null && this.crew != null) {
            this.crew = null;
            this.aim = null;
            this.dirty = true;
        }
        float lastYaw = this.yaw, lastElevation = this.elevation;
        if (crewman != null && this.aim != null) {
            Solution s = solve(this.aim);
            if (s.charge() != this.charge || s.reachable() != this.reachable) {
                this.dirty = true;
            }
            this.charge = s.charge();
            this.reachable = s.reachable();
            this.yaw = wrap(this.yaw + Mth.clamp(wrap(s.yaw() - this.yaw), -TRAVERSE, TRAVERSE));
            this.elevation += Mth.clamp(s.elevation() - this.elevation, -ELEVATE, ELEVATE);
        }
        double turning = Math.hypot(wrap(this.yaw - lastYaw), this.elevation - lastElevation) * 20;
        double least = Math.sqrt(1 + Mth.square(TURN_BLOOM * turning));
        this.dispersion = (float) Math.max(least, this.dispersion * SHRINK);
        if (this.reload > 0) {
            this.reload--;
        }
        if (crewman != null || turning > 0 || this.dirty) {
            int[] n = new int[ShellType.values().length];
            for (ShellType t : ShellType.values()) {
                n[t.ordinal()] = count(crewman, t);
            }
            PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(this.worldPosition), new ArtilleryPayloads.State(this.worldPosition,
                    this.yaw, this.elevation, this.charge, this.dispersion, this.reload, this.selected.ordinal(), this.reachable, this.shots,
                    crewman == null ? -1 : crewman.getId(), n));
            this.dirty = false;
        }
        if (turning > 0) {
            setChanged();
        }
    }

    /** The state the server sent (the client's copy). */
    public void applyState(ArtilleryPayloads.State s) {
        this.yaw = s.yaw();
        this.elevation = s.elevation();
        this.charge = Mth.clamp(s.charge(), 0, CHARGES.length - 1);
        this.dispersion = s.dispersion();
        this.reload = s.reload();
        this.selected = ShellType.of(s.selected());
        this.reachable = s.reachable();
        this.crewId = s.crew();
        if (s.counts().length == this.counts.length) {
            this.counts = s.counts();
        }
        if (s.shots() != this.shots && this.level != null) {
            if (this.shots != 0 || s.shots() == 1) {
                this.firedAt = this.level.getGameTime();
            }
            this.shots = s.shots();
        }
    }

    // --- what the client reads --------------------------------------------------------------------------------------------
    public float yaw() {
        return Float.isNaN(this.yaw) ? frontYaw() : this.yaw;
    }

    public float elevation() {
        return this.elevation;
    }

    public int charge() {
        return this.charge;
    }

    public float dispersion() {
        return this.dispersion;
    }

    public int reload() {
        return this.reload;
    }

    public ShellType selected() {
        return this.selected;
    }

    public boolean reachable() {
        return this.reachable;
    }

    public int crewId() {
        return this.crewId;
    }

    /** How many shots it has fired. */
    public int shots() {
        return this.shots;
    }

    /** Shells of {@code type} its crewman can fire (the last state's; -1: without end). */
    public int shells(ShellType type) {
        return this.counts[type.ordinal()];
    }

    /** When the client saw its last shot (game time). */
    public long firedAt() {
        return this.firedAt;
    }

    /** The tube as drawn this frame: yaw and elevation, swung toward the last state at the gun's own pace. */
    public float shownYaw(float partialTick) {
        track(partialTick);
        return this.shownYaw;
    }

    public float shownElevation(float partialTick) {
        track(partialTick);
        return this.shownElevation;
    }

    private void track(float partialTick) {
        if (this.level == null) {
            return;
        }
        double now = this.level.getGameTime() + partialTick;
        if (Float.isNaN(this.shownYaw)) {
            this.shownYaw = yaw();
            this.shownElevation = this.elevation;
        } else if (now != this.shownAt) {
            float dt = (float) Mth.clamp(now - this.shownAt, 0, 10);
            this.shownYaw = wrap(this.shownYaw + Mth.clamp(wrap(yaw() - this.shownYaw), -TRAVERSE * 1.5f * dt, TRAVERSE * 1.5f * dt));
            this.shownElevation += Mth.clamp(this.elevation - this.shownElevation, -ELEVATE * 1.5f * dt, ELEVATE * 1.5f * dt);
        }
        this.shownAt = now;
    }

    // --- model --------------------------------------------------------------------------------------------------------
    /**
     * The tube turned toward its yaw about the socket and raised (it is built standing straight up); the bipod leaned
     * about its feet so its screw, stretched, meets the tube's collar (see {@link #boneScales}).
     */
    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        if (this.level == null) {
            return Map.of();
        }
        float yaw = shownYaw(partialTick), e = shownElevation(partialTick);
        Map<String, float[]> out = new HashMap<>();
        out.put("traverse", new float[] {0, (float) Math.toRadians(wrap(yaw - frontYaw())), 0});
        double[] b = bipod(e);
        out.put("tube", new float[] {(float) Math.toRadians(-(90 - e)), 0, 0});
        out.put("bipod", new float[] {(float) (b[0] + LEAN), 0, 0});
        out.put("screw_base", new float[] {(float) -LEAN, 0, 0});
        return out;
    }

    /** The model at rest (as an item shows it): the tube laid at {@link #REST_ELEVATION} over its front. */
    public static Map<String, float[]> restPose() {
        double[] b = bipod(REST_ELEVATION);
        return Map.of("tube", new float[] {(float) Math.toRadians(-(90 - REST_ELEVATION)), 0, 0},
                "bipod", new float[] {(float) (b[0] + LEAN), 0, 0}, "screw_base", new float[] {(float) -LEAN, 0, 0});
    }

    /** The screw stretched to the collar. */
    @Override
    public Map<String, float[]> boneScales() {
        if (this.level == null) {
            return Map.of();
        }
        float e = this.shownElevation;
        return Map.of("screw", new float[] {1, (float) bipod(e)[1], 1});
    }

    /** As it fires, the whole mortar is driven down into the ground a little and springs back. */
    @Override
    public Map<String, float[]> boneOffsets(float partialTick) {
        if (this.level == null) {
            return Map.of();
        }
        double since = this.level.getGameTime() + partialTick - this.firedAt;
        if (since < 0 || since > 10) {
            return Map.of();
        }
        float kick = (float) (since < 1.5 ? since / 1.5 : Math.max(0, 1 - (since - 1.5) / 8.5)) * 0.9f;
        return Map.of("frame", new float[] {0, -kick, 0});
    }

    /**
     * The bipod at elevation {@code e}: how far it leans about its feet (radians, a turn about x) and how long its screw
     * is against its length at rest. Its feet stay put; its screw's top meets the collar ({@link #COLLAR} along the
     * tube from the socket).
     */
    static double[] bipod(double e) {
        double r = Math.toRadians(e);
        double socketY = SOCKET_Y * 16, socketZ = SOCKET_BACK * 16;
        double qy = socketY + COLLAR * Math.sin(r), qz = socketZ - COLLAR * Math.cos(r);   // the collar
        double rest = Math.toRadians(REST_ELEVATION);
        double ry = socketY + COLLAR * Math.sin(rest);
        double restLength = ry - SCREW_Y;                                                 // the screw's length at rest
        double dz = SCREW_Z - FEET_Z;                                                     // its foot ahead of the feet line
        double toY = qy, toZ = qz - FEET_Z;
        double length = Math.sqrt(Math.max(0, toY * toY + toZ * toZ - dz * dz)) - SCREW_Y;
        double lean = Math.atan2(toZ, toY) - Math.atan2(dz, SCREW_Y + length);
        return new double[] {lean, Math.max(0.2, length / restLength)};
    }

    private static float wrap(float degrees) {
        return Mth.wrapDegrees(degrees);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.rack.serialize(output.child("rack"));
        if (!Float.isNaN(this.yaw)) {
            output.putFloat("yaw", this.yaw);
        }
        output.putFloat("elevation", this.elevation);
        output.putInt("charge", this.charge);
        output.putInt("selected", this.selected.ordinal());
        output.putInt("reload", this.reload);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.rack.deserialize(input.childOrEmpty("rack"));
        this.yaw = input.getFloatOr("yaw", Float.NaN);
        this.elevation = Mth.clamp(input.getFloatOr("elevation", REST_ELEVATION), MIN_ELEVATION, MAX_ELEVATION);
        this.charge = Mth.clamp(input.getIntOr("charge", 2), 0, CHARGES.length - 1);
        this.selected = ShellType.of(input.getIntOr("selected", 0));
        this.reload = input.getIntOr("reload", 0);
    }

    /** Broken: its shells drop, and its crewman gets up. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            Containers.dropContents(this.level, pos, this.rack);
        }
    }
}
