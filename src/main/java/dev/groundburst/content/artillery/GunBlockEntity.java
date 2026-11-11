package dev.groundburst.content.artillery;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineInventory;
import dev.groundburst.registry.GBLang;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import dev.groundburst.port.ValueInput;
import dev.groundburst.port.ValueOutput;
import net.minecraft.world.phys.Vec3;
import dev.groundburst.legacy.net.PacketDistributor;

/**
 * A crewed gun (DESIGN.md §20.1), laid and fired as World of Tanks lays and fires its artillery; its figures are its
 * {@link GunSpec}, its model its own (the subclasses).
 *
 * <p>Laying: the gun is laid on a point on the ground, which slides toward its crewman's aim at the gun's own pace
 * (round it by {@code traverseRate} degrees a tick, out or in by {@code rangeRate} blocks a tick), never jumping; for
 * each point it is laid on, the lowest charge that reaches it is taken (its tube drawn swinging to the new elevation).
 * Spread: the aiming circle's radius is {@code accuracy} blocks per 100 of range times the dispersion, which shrinks to a
 * third each {@code aimTime} ticks down to its least, the root of 1 + (k w)^2 while the laid point moves at w degrees a
 * second (a block of range a second counting half a degree); a shot multiplies it by {@code bloom} (at most
 * {@code maxBloom}, as it is when just manned). A shot falls where the tube points, spread over the circle laid on the
 * ground (Ballistics.scatter).
 */
public abstract class GunBlockEntity extends BlockEntity implements AnimatedMachine, net.minecraft.world.Clearable {
    /** Ticks a shell may fly while its fall is worked out. */
    public static final int MAX_FLIGHT = 900;

    public static final String T_RACK_FULL = GBLang.add(GroundBurst.MODID + ".artillery.rack_full", "Its rack holds no more of these");
    public static final String T_WRONG_SHELL = GBLang.add(GroundBurst.MODID + ".artillery.wrong_shell", "This gun does not fire these");
    public static final String T_MANNED = GBLang.add(GroundBurst.MODID + ".artillery.manned", "Someone is already manning it");
    public static final String T_NO_SHELLS = GBLang.add(GroundBurst.MODID + ".artillery.no_shells", "No %s shells: load some, or carry them");
    public static final String T_OUT_OF_RANGE = GBLang.add(GroundBurst.MODID + ".artillery.out_of_range", "Out of range");

    public final GunSpec spec;
    private final double shrink;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final MachineInventory rack;

    /** Its crewman (server; not saved: a seat does not outlast a restart). */
    private @Nullable ServerPlayer crew;
    /** Where its crewman wants it laid (null: nowhere yet), and the point it is laid on now. */
    private @Nullable Vec3 aim, laid;
    /** The tube as laid: world yaw (atan2(dx, dz), degrees; NaN: at rest, its front), elevation, charge. */
    private float yaw = Float.NaN, elevation;
    private int charge;
    private float dispersion;
    private int reload;
    /** The shell picked: its place in the spec's shells. */
    private int selected;
    private boolean reachable;
    private int shots;
    private boolean dirty = true;

    // --- the client's copy ------------------------------------------------------------------------------------------------
    private int crewId = -1;
    private int[] counts;
    /** The tube as drawn and the point it is laid on as drawn (each moving toward the last state at the gun's own pace). */
    private float shownYaw = Float.NaN, shownElevation;
    private @Nullable Vec3 shownLaid;
    private double shownAt, laidAt;
    /** When the client saw its last shot (game time; it kicks the tube). */
    private long firedAt = Long.MIN_VALUE / 2;

    protected GunBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, GunSpec spec) {
        super(type, pos, state);
        this.spec = spec;
        this.shrink = Math.pow(1 / 3.0, 1 / spec.aimTime());
        this.elevation = this.shownElevation = spec.restElevation();
        this.charge = spec.charges().length / 2;
        this.dispersion = (float) spec.maxBloom();
        this.counts = new int[spec.shells().size()];
        this.rack = new MachineInventory(spec.shells().size(), (slot, stack) -> slotFor(stack) == slot, slot -> setChanged(), slot -> spec.rackSize());
    }

    public MachineInventory rack() {
        return this.rack;
    }

    /** The rack slot a shell goes in (-1: this gun does not fire it). */
    public int slotFor(ItemStack stack) {
        return ((Object) stack.getItem()) instanceof ShellItem s && s.calibre() == this.spec.calibre() ? this.spec.shells().indexOf(s.type()) : -1;
    }

    // --- where it is --------------------------------------------------------------------------------------------------
    protected Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(GunBlock.FACING) ? s.getValue(GunBlock.FACING) : Direction.NORTH;
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

    /** The tube's pivot. */
    public Vec3 socket() {
        Direction f = facing();
        return Vec3.atBottomCenterOf(this.worldPosition).add(-f.getStepX() * this.spec.socketBack(), this.spec.socketY(),
                -f.getStepZ() * this.spec.socketBack());
    }

    /** The muzzle with the tube laid at {@code yaw}, {@code elevation}. */
    public Vec3 muzzle(double yaw, double elevation) {
        return socket().add(Ballistics.velocity(this.spec.tube(), yaw, elevation));
    }

    /** Where its crewman stands: behind it and to its left as it rests. */
    private Vec3 seat() {
        Direction f = facing();
        double b = this.spec.seatBack(), l = this.spec.seatLeft();
        return Vec3.atBottomCenterOf(this.worldPosition).add(-f.getStepX() * b + f.getStepZ() * l, 0, -f.getStepZ() * b - f.getStepX() * l);
    }

    // --- laying -------------------------------------------------------------------------------------------------------
    /** How it would be laid on {@code target}: the lowest charge that reaches it, the yaw and elevation, and whether it can. */
    public record Solution(int charge, float yaw, float elevation, boolean reachable) {}

    public Solution solve(Vec3 target) {
        GunSpec g = this.spec;
        Vec3 base = socket();
        float yaw = Ballistics.yaw(base, target);
        double h = Math.hypot(target.x - base.x, target.z - base.z);
        boolean inReach = h >= g.minRange() && h <= g.maxRange() && withinTraverse(yaw);
        for (int k = 0; k < g.charges().length; k++) {
            for (boolean high : new boolean[] {true, false}) {
                double e = g.restElevation();
                boolean ok = false;
                for (int pass = 0; pass < 2; pass++) {           // from the muzzle as it would be laid (twice: it moves a little)
                    Vec3 from = muzzle(yaw, e);
                    double hx = Math.hypot(target.x - from.x, target.z - from.z), dy = target.y - from.y;
                    double ek = high ? Ballistics.highAngle(g.charges()[k], hx, dy) : Ballistics.lowAngle(g.charges()[k], hx, dy);
                    ok = !Double.isNaN(ek) && ek >= g.minElevation() && ek <= g.maxElevation();
                    if (!ok) {
                        break;
                    }
                    e = ek;
                }
                if (ok) {
                    return new Solution(k, yaw, (float) e, inReach);
                }
            }
        }
        boolean near = h < g.minRange();
        return new Solution(near ? 0 : g.charges().length - 1, yaw, near ? g.maxElevation() : Math.min(45, g.maxElevation()), false);
    }

    /** Whether it can be turned to {@code yaw} (all round, or within its traverse of its front). */
    public boolean withinTraverse(float yaw) {
        return this.spec.allRound() || Math.abs(Mth.wrapDegrees(yaw - frontYaw())) <= this.spec.traverseLimit() + 1e-3;
    }

    private float clampBearing(float yaw) {
        if (this.spec.allRound()) {
            return yaw;
        }
        float rel = Mth.clamp(Mth.wrapDegrees(yaw - frontYaw()), -this.spec.traverseLimit(), this.spec.traverseLimit());
        return Mth.wrapDegrees(frontYaw() + rel);
    }

    /** Its aiming circle's radius at {@code range} blocks, as it is now. */
    public double radius(double range) {
        return this.spec.accuracy() / 100 * range * this.dispersion;
    }

    /**
     * The point it is laid on, moved one tick toward its aim at its own pace; returns how fast it moved, degrees a
     * second (a block of range a second counting half a degree).
     */
    private double layToward(ServerLevel level, Vec3 aim) {
        Vec3 base = socket();
        if (this.laid == null) {
            this.laid = aim;
        }
        float lb = Ballistics.yaw(base, this.laid), ab = clampBearing(Ballistics.yaw(base, aim));
        double lr = Math.hypot(this.laid.x - base.x, this.laid.z - base.z);
        double ar = Mth.clamp(Math.hypot(aim.x - base.x, aim.z - base.z), this.spec.minRange(), this.spec.maxRange());
        float db = Mth.clamp(Mth.wrapDegrees(ab - lb), -this.spec.traverseRate(), this.spec.traverseRate());
        double dr = Mth.clamp(ar - lr, -this.spec.rangeRate(), this.spec.rangeRate());
        double nb = Math.toRadians(lb + db), nr = Mth.clamp(lr + dr, this.spec.minRange(), this.spec.maxRange());
        double x = base.x + Math.sin(nb) * nr, z = base.z + Math.cos(nb) * nr;
        double y;
        boolean there = Math.abs(Mth.wrapDegrees(ab - (lb + db))) < 1e-3 && Math.abs(ar - nr) < 1e-3;
        if (there && Ballistics.yaw(base, aim) == ab && Math.hypot(aim.x - base.x, aim.z - base.z) == ar) {
            x = aim.x;
            z = aim.z;
            y = aim.y;
        } else {
            double left = Math.hypot(aim.x - this.laid.x, aim.z - this.laid.z), step = Math.hypot(x - this.laid.x, z - this.laid.z);
            double toward = this.laid.y + (aim.y - this.laid.y) * (left < 1e-6 ? 1 : Math.min(1, step / left));
            y = Ballistics.ground(level, x, z, toward);
        }
        this.laid = new Vec3(x, y, z);
        return Math.hypot(db * 20, dr * 20 * 0.5);
    }

    // --- the crew ---------------------------------------------------------------------------------------------------------
    private @Nullable ServerPlayer crewman(ServerLevel level) {
        ServerPlayer p = this.crew;
        return p != null && seated(p, level) ? p : null;
    }

    /** Whether {@code p} stands in this gun's crew place. */
    private boolean seated(ServerPlayer p, ServerLevel level) {
        return !p.isRemoved() && p.isAlive() && p.level() == level && ((Object) p.getVehicle()) instanceof GunSeat seat && seat.gun().equals(this.worldPosition);
    }

    /**
     * Whether {@code player} is its crewman: the one who manned it, or (nobody else manning it) whoever stands in its
     * crew place (a crewman saved there comes back there; the gun does not keep its crew over a restart).
     */
    private boolean isCrew(ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level;
        ServerPlayer now = crewman(level);
        if (now == null && seated(player, level)) {
            this.crew = now = player;
            this.dispersion = (float) this.spec.maxBloom();
            this.dirty = true;
        }
        return now == player;
    }

    /** Whether someone mans it now. */
    public boolean manned() {
        return ((Object) this.level) instanceof ServerLevel server ? crewman(server) != null : this.crewId >= 0;
    }

    /** A line over {@code player}'s hotbar (none for a player with no connection: a test's). */
    protected static void tell(ServerPlayer player, Component line) {
        if (player.connection != null) {
            player.displayClientMessage(line, true);
        }
    }

    /** {@code player} mans it: they take its crew place (if nobody else is manning it). */
    public boolean man(ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level;
        if (crewman(level) != null) {
            tell(player, Component.translatable(T_MANNED));
            return false;
        }
        GunSeat seat = GunSeat.place(level, this.worldPosition, seat(), -frontYaw());
        if (!player.startRiding(seat, true)) {
            seat.discard();
            return false;
        }
        this.crew = player;
        this.dispersion = (float) this.spec.maxBloom();
        if (Float.isNaN(this.yaw)) {
            this.yaw = frontYaw();
        }
        Ballistics.Fall fall = Ballistics.fall(level, muzzle(this.yaw, this.elevation),
                Ballistics.velocity(this.spec.charges()[this.charge], this.yaw, this.elevation), null, MAX_FLIGHT);
        this.laid = this.aim = startingPoint(level, fall == null ? null : fall.at());
        this.dirty = true;
        level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.BLOCKS, 0.7f, 0.9f);
        return true;
    }

    /**
     * Where a gun just manned is laid: where its shell would come down as it is laid now ({@code fall}; null, not known),
     * brought within its ranges and its traverse, so the laid point never has to jump there (a wall or a roof beside it
     * stops a shell short; a barrel gun at rest is laid lower than it fires).
     */
    private Vec3 startingPoint(ServerLevel level, @Nullable Vec3 fall) {
        Vec3 base = socket();
        double range = fall == null ? this.spec.minRange() * 2 : Math.hypot(fall.x - base.x, fall.z - base.z);
        double r = Mth.clamp(range, this.spec.minRange(), this.spec.maxRange());
        if (fall != null && r == range && withinTraverse(Ballistics.yaw(base, fall))) {
            return fall;
        }
        double b = Math.toRadians(clampBearing(fall == null ? frontYaw() : Ballistics.yaw(base, fall)));
        double x = base.x + Math.sin(b) * r, z = base.z + Math.cos(b) * r;
        return new Vec3(x, Ballistics.ground(level, x, z, fall == null ? base.y : fall.y), z);
    }

    /** Its crewman's aim: a point on the ground. */
    public void aim(ServerPlayer player, Vec3 at) {
        if (!isCrew(player)) {
            return;
        }
        Vec3 base = socket();
        double h = Math.hypot(at.x - base.x, at.z - base.z);
        if (h > this.spec.maxRange() * 2 || Math.abs(at.y - base.y) > 600) {
            return;
        }
        this.aim = at;
    }

    /** Its crewman picks the shell to fire next (its place in the rack). */
    public void select(ServerPlayer player, int shell) {
        if (isCrew(player) && shell >= 0 && shell < this.spec.shells().size()) {
            this.selected = shell;
            this.dirty = true;
        }
    }

    // --- loading ---------------------------------------------------------------------------------------------------------
    /** Puts {@code stack} into its rack; returns what did not fit (all of it, if this gun does not fire it). */
    public ItemStack load(ItemStack stack) {
        int slot = slotFor(stack);
        if (slot < 0) {
            return stack;
        }
        ItemStack left = this.rack.insertDirect(slot, stack.copy(), false);
        this.dirty = true;
        return left;
    }

    /** Shells in rack slot {@code slot} it can fire for {@code crewman}: its rack's and theirs (-1: without end, in creative). */
    private int count(@Nullable ServerPlayer crewman, int slot) {
        if (crewman != null && crewman.getAbilities().instabuild) {
            return -1;
        }
        int n = this.rack.getItem(slot).getCount();
        if (crewman != null) {
            Inventory inv = crewman.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (slotFor(s) == slot) {
                    n += s.getCount();
                }
            }
        }
        return n;
    }

    /** Takes one shell for rack slot {@code slot}: from its rack, else from its crewman (none needed in creative). */
    private boolean take(ServerPlayer crewman, int slot) {
        if (crewman.getAbilities().instabuild) {
            return true;
        }
        ItemStack in = this.rack.getItem(slot);
        if (!in.isEmpty()) {
            this.rack.setItem(slot, in.getCount() > 1 ? in.copyWithCount(in.getCount() - 1) : ItemStack.EMPTY);
            return true;
        }
        Inventory inv = crewman.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (slotFor(s) == slot) {
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
        ShellType type = selectedType();
        if (!take(player, this.selected)) {
            tell(player, Component.translatable(T_NO_SHELLS, type.english));
            level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6f, 1.2f);
            return;
        }
        shoot(level, player, type);
    }

    /** A shell of {@code type} away, spread over its circle as it is now (its crewman's: {@code by}). */
    protected void shoot(ServerLevel level, @Nullable ServerPlayer by, ShellType type) {
        double speed = this.spec.charges()[this.charge];
        Vec3 muzzle = muzzle(this.yaw, this.elevation);
        Vec3 u = Ballistics.velocity(speed, this.yaw, this.elevation);
        Ballistics.Fall fall = Ballistics.fall(level, muzzle, u, null, MAX_FLIGHT);
        if (fall == null && this.laid != null) {
            // Over ground the server has not loaded (most long shots): where it is laid, its flight worked out to there.
            fall = Ballistics.flightTo(muzzle, u, this.laid);
        }
        Vec3 launch = u;
        this.lastSpread = null;
        if (fall != null) {
            Vec3 laidOn = fall.at();
            double range = Math.hypot(laidOn.x - muzzle.x, laidOn.z - muzzle.z);
            Vec3 off = Ballistics.scatter(level.getRandom(), radius(range), fall.stretch(), this.yaw);
            double x = laidOn.x + off.x, z = laidOn.z + off.z;
            // On a ship (above the world's own ground, which its heightmap knows), at the height it was laid on.
            Vec3 target = new Vec3(x, fall.aboard() ? laidOn.y : Ballistics.ground(level, x, z, laidOn.y), z);
            this.lastSpread = target;
            float yaw2 = Ballistics.yaw(muzzle, target);
            double hx = Math.hypot(target.x - muzzle.x, target.z - muzzle.z), dy = target.y - muzzle.y;
            double high = Ballistics.highAngle(speed, hx, dy), low = Ballistics.lowAngle(speed, hx, dy);
            double e2 = Double.isNaN(high) ? Double.NaN : Math.abs(high - this.elevation) <= Math.abs(low - this.elevation) ? high : low;
            if (!Double.isNaN(e2) && Math.abs(e2 - this.elevation) < 15) {
                launch = Ballistics.velocity(speed, yaw2, e2);
            }
        }
        ArtilleryShell.launch(level, muzzle, launch, this.spec.calibre(), type, by);
        this.dispersion = (float) Math.min(this.spec.maxBloom(), this.dispersion * this.spec.bloom());
        this.reload = this.spec.reload();
        this.shots++;
        this.dirty = true;
        setChanged();
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, this.spec.fireSound().get(), SoundSource.BLOCKS, this.spec.fireVolume(),
                0.95f + level.getRandom().nextFloat() * 0.1f);
        Vec3 d = launch.normalize();
        float k = this.spec.smoke();
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, (int) (10 * k), 0.15 * k, 0.15 * k, 0.15 * k, 0.04);
        level.sendParticles(ParticleTypes.CLOUD, muzzle.x + d.x * 0.3 * k, muzzle.y + d.y * 0.3 * k, muzzle.z + d.z * 0.3 * k, (int) (10 * k),
                0.18 * k, 0.18 * k, 0.18 * k, 0.06 * k);
        level.sendParticles(ParticleTypes.POOF, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.1, this.worldPosition.getZ() + 0.5,
                (int) (6 * k), 0.4 * k, 0.02, 0.4 * k, 0.02);
    }

    /** Laid at once on {@code target}, fully aimed (the photographer's: no crewman). */
    public void layForTest(Vec3 target) {
        Solution s = solve(target);
        this.laid = target;
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

    /** Where its last shot was spread to (for GameTests: the point it was sent to, on the ground there). */
    private @Nullable Vec3 lastSpread;

    public @Nullable Vec3 lastSpreadForTest() {
        return this.lastSpread;
    }

    // --- the far ground's map ----------------------------------------------------------------------------------------------
    /** A tile of the far ground's map being made for its crewman: its corner, for whom, since when. */
    private record TileJob(int ox, int oz, ServerPlayer to, long since) {}

    private @Nullable TileJob tile;
    /** The tiles asked for after it (a few at most: the newest, nearest the aim as it is now, made first; the oldest dropped). */
    private final java.util.ArrayDeque<TileJob> tiles = new java.util.ArrayDeque<>();
    private static final int TILES_WAITING = 8;

    /** Who holds the chunks a tile is made from (one owner a gun, so its tickets are its own). */
    private java.util.UUID tileOwner() {
        return java.util.UUID.nameUUIDFromBytes(("groundburst:gun_map/" + this.worldPosition.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Its crewman's strategic view wants the map of a tile (ground their game has not loaded): its chunks are loaded
     * (in the background; generated if they never were) and its ground sampled once they are. One tile at a time,
     * within the gun's reach.
     */
    public void mapTile(ServerPlayer player, int ox, int oz) {
        if (!isCrew(player)) {
            return;
        }
        int t = ArtilleryPayloads.TILE;
        ox = Math.floorDiv(ox, t) * t;
        oz = Math.floorDiv(oz, t) * t;
        Vec3 base = socket();
        double cx = ox + t / 2.0, cz = oz + t / 2.0;
        if (Math.hypot(cx - base.x, cz - base.z) > this.spec.maxRange() + t) {
            return;
        }
        if (this.tile != null && this.tile.ox() == ox && this.tile.oz() == oz) {
            return;
        }
        for (TileJob waiting : this.tiles) {
            if (waiting.ox() == ox && waiting.oz() == oz) {
                return;
            }
        }
        if (this.tiles.size() >= TILES_WAITING) {
            this.tiles.pollFirst();
        }
        this.tiles.addLast(new TileJob(ox, oz, player, 0));
        if (this.tile == null) {
            nextTile((ServerLevel) this.level);
        }
    }

    /** Starts on the tile asked for last (the aim has moved on from the older ones): its chunks loaded (not ticking) until it is made. */
    private void nextTile(ServerLevel level) {
        TileJob next = this.tiles.pollLast();
        if (next == null) {
            return;
        }
        int t = ArtilleryPayloads.TILE;
        this.tile = new TileJob(next.ox(), next.oz(), next.to(), level.getGameTime());
        for (int x = next.ox() >> 4; x <= (next.ox() + t - 1) >> 4; x++) {
            for (int z = next.oz() >> 4; z <= (next.oz() + t - 1) >> 4; z++) {
                GBArtillery.TICKETS.forceChunk(level, tileOwner(), x, z, true, false);
            }
        }
    }

    /** Lets go of the chunks a tile was being made from. */
    private void dropTile(ServerLevel level) {
        TileJob job = this.tile;
        if (job == null) {
            return;
        }
        int t = ArtilleryPayloads.TILE;
        for (int x = job.ox() >> 4; x <= (job.ox() + t - 1) >> 4; x++) {
            for (int z = job.oz() >> 4; z <= (job.oz() + t - 1) >> 4; z++) {
                GBArtillery.TICKETS.forceChunk(level, tileOwner(), x, z, false, false);
            }
        }
        this.tile = null;
    }

    /** The tile being made, once its chunks are in (or after ten seconds, with what is): sampled and sent. */
    private void makeTile(ServerLevel level) {
        TileJob job = this.tile;
        if (crewman(level) != job.to()) {
            dropTile(level);                                   // its crewman has gone
            this.tiles.clear();
            return;
        }
        int t = ArtilleryPayloads.TILE, step = ArtilleryPayloads.STEP, n = ArtilleryPayloads.SAMPLES;
        boolean all = true;
        for (int x = job.ox() >> 4; x <= (job.ox() + t - 1) >> 4 && all; x++) {
            for (int z = job.oz() >> 4; z <= (job.oz() + t - 1) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    all = false;
                    break;
                }
            }
        }
        if (!all && level.getGameTime() - job.since() < 200) {
            return;
        }
        short[] heights = new short[n * n];
        int[] colours = new int[n * n];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int x = job.ox() + i * step, z = job.oz() + j * step;
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                    heights[j * n + i] = ArtilleryPayloads.UNKNOWN;
                    continue;
                }
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                heights[j * n + i] = (short) y;
                pos.set(x, y - 1, z);
                BlockState top = level.getBlockState(pos);
                colours[j * n + i] = top.getMapColor(level, pos).col & 0xFFFFFF;
            }
        }
        if (job.to().connection != null && !job.to().isRemoved()) {
            PacketDistributor.sendToPlayer(job.to(), new ArtilleryPayloads.Terrain(job.ox(), job.oz(), heights, colours));
        }
        dropTile(level);
        nextTile(level);
    }

    // --- ticking ---------------------------------------------------------------------------------------------------------
    public void serverTick(ServerLevel level) {
        if (this.tile != null) {
            makeTile(level);
        }
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
        double moving = 0;
        if (crewman != null && this.aim != null) {
            moving = layToward(level, this.aim);
            Solution s = solve(this.laid);
            boolean reach = solve(this.aim).reachable();
            if (s.charge() != this.charge || reach != this.reachable || s.yaw() != this.yaw || s.elevation() != this.elevation) {
                this.dirty = true;
            }
            this.yaw = s.yaw();
            this.elevation = s.elevation();
            this.charge = s.charge();
            this.reachable = reach;
        }
        double least = Math.sqrt(1 + Mth.square(this.spec.turnBloom() * moving));
        this.dispersion = (float) Math.max(least, this.dispersion * this.shrink);
        if (this.reload > 0) {
            this.reload--;
        }
        if (crewman != null || this.dirty) {
            int[] n = new int[this.spec.shells().size()];
            for (int i = 0; i < n.length; i++) {
                n[i] = count(crewman, i);
            }
            Vec3 l = this.laid == null ? Vec3.ZERO : this.laid;
            PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(this.worldPosition), new ArtilleryPayloads.State(this.worldPosition,
                    this.yaw, this.elevation, this.charge, this.dispersion, this.reload, this.selected, this.reachable, this.shots,
                    crewman == null ? -1 : crewman.getId(), n, this.laid != null, l.x, l.y, l.z));
            this.dirty = false;
        }
        if (moving > 0) {
            setChanged();
        }
    }

    /** The state the server sent (the client's copy). */
    public void applyState(ArtilleryPayloads.State s) {
        this.yaw = s.yaw();
        this.elevation = s.elevation();
        this.charge = Mth.clamp(s.charge(), 0, this.spec.charges().length - 1);
        this.dispersion = s.dispersion();
        this.reload = s.reload();
        this.selected = Mth.clamp(s.selected(), 0, this.spec.shells().size() - 1);
        this.reachable = s.reachable();
        this.crewId = s.crew();
        if (s.counts().length == this.counts.length) {
            this.counts = s.counts();
        }
        this.laid = s.laid() ? new Vec3(s.x(), s.y(), s.z()) : null;
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

    /** The shell picked: its place in the rack. */
    public int selected() {
        return this.selected;
    }

    public ShellType selectedType() {
        return this.spec.shells().get(Mth.clamp(this.selected, 0, this.spec.shells().size() - 1));
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

    /** The point it is laid on (null: none yet). */
    public @Nullable Vec3 laid() {
        return this.laid;
    }

    /** Shells in rack slot {@code slot} its crewman can fire (the last state's; -1: without end). */
    public int shells(int slot) {
        return slot >= 0 && slot < this.counts.length ? this.counts[slot] : 0;
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

    /** The point it is laid on as drawn this frame: moving toward the last state's at the gun's own pace (null: none). */
    public @Nullable Vec3 shownLaid(float partialTick) {
        if (this.level == null || this.laid == null) {
            return this.laid;
        }
        double now = this.level.getGameTime() + partialTick;
        if (this.shownLaid == null) {
            this.shownLaid = this.laid;
        } else if (now > this.laidAt) {
            double dt = Mth.clamp(now - this.laidAt, 0, 10);
            Vec3 base = socket();
            float sb = Ballistics.yaw(base, this.shownLaid), tb = Ballistics.yaw(base, this.laid);
            double sr = Math.hypot(this.shownLaid.x - base.x, this.shownLaid.z - base.z), tr = Math.hypot(this.laid.x - base.x, this.laid.z - base.z);
            double b = Math.toRadians(sb + Mth.clamp(Mth.wrapDegrees(tb - sb), -this.spec.traverseRate() * 1.5 * dt, this.spec.traverseRate() * 1.5 * dt));
            double r = sr + Mth.clamp(tr - sr, -this.spec.rangeRate() * 1.5 * dt, this.spec.rangeRate() * 1.5 * dt);
            double y = this.shownLaid.y + (this.laid.y - this.shownLaid.y) * Math.min(1, 0.35 * dt);
            Vec3 next = new Vec3(base.x + Math.sin(b) * r, y, base.z + Math.cos(b) * r);
            this.shownLaid = next.distanceToSqr(this.laid) < 1e-4 ? this.laid : next;
        }
        this.laidAt = Math.max(this.laidAt, now);
        return this.shownLaid;
    }

    private void track(float partialTick) {
        if (this.level == null) {
            return;
        }
        double now = this.level.getGameTime() + partialTick;
        if (Float.isNaN(this.shownYaw)) {
            this.shownYaw = yaw();
            this.shownElevation = this.elevation;
        } else if (now > this.shownAt) {
            float dt = (float) Mth.clamp(now - this.shownAt, 0, 10);
            float turn = Math.max(this.spec.traverseRate(), 2f) * 1.5f * dt, lift = this.spec.elevateRate() * 1.5f * dt;
            this.shownYaw = Mth.wrapDegrees(this.shownYaw + Mth.clamp(Mth.wrapDegrees(yaw() - this.shownYaw), -turn, turn));
            this.shownElevation += Mth.clamp(this.elevation - this.shownElevation, -lift, lift);
        }
        this.shownAt = Math.max(this.shownAt, now);
    }

    /** Ticks since the client saw its last shot, with the frame's part (large: long ago). */
    protected double sinceFired(float partialTick) {
        return this.level == null ? 1e9 : this.level.getGameTime() + partialTick - this.firedAt;
    }

    /** Its turret's turn from rest this frame, radians (as GeckoLib turns a bone about y). */
    protected float traverseRadians(float partialTick) {
        return (float) Math.toRadians(Mth.wrapDegrees(shownYaw(partialTick) - frontYaw()));
    }

    @Override
    public abstract Map<String, float[]> boneRotations(float partialTick);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = dev.groundburst.legacy.Regs.access();
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag_) { net.minecraft.core.HolderLookup.Provider registries_ = dev.groundburst.legacy.Regs.access(); ValueOutput output = new ValueOutput(tag_, registries_);
        super.saveAdditional(output.tag());
        this.rack.serialize(output.child("rack"));
        if (!Float.isNaN(this.yaw)) {
            output.putFloat("yaw", this.yaw);
        }
        output.putFloat("elevation", this.elevation);
        output.putInt("charge", this.charge);
        output.putInt("selected", this.selected);
        output.putInt("reload", this.reload);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag_) { net.minecraft.core.HolderLookup.Provider registries_ = dev.groundburst.legacy.Regs.access(); ValueInput input = new ValueInput(tag_, registries_);
        super.load(input.tag());
        this.rack.deserialize(input.childOrEmpty("rack"));
        this.yaw = input.getFloatOr("yaw", Float.NaN);
        this.elevation = Mth.clamp(input.getFloatOr("elevation", this.spec.restElevation()), this.spec.minElevation(), this.spec.maxElevation());
        this.charge = Mth.clamp(input.getIntOr("charge", this.spec.charges().length / 2), 0, this.spec.charges().length - 1);
        this.selected = Mth.clamp(input.getIntOr("selected", 0), 0, this.spec.shells().size() - 1);
        this.reload = input.getIntOr("reload", 0);
    }

    /** Broken: its shells drop (its crewman's place goes with it). */
        public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null) {
            Containers.dropContents(this.level, pos, this.rack);
        }
        if (((Object) this.level) instanceof ServerLevel server) {
            dropTile(server);
            this.tiles.clear();
        }
    }

    /** Emptied before its block goes without being broken (a ship taking it aboard after saving it): its rack drops nothing. */
    @Override
    public void clearContent() {
        this.rack.clearContent();
    }

    private boolean unloading_;

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.unloading_ = true;
    }

    @Override
    public void setRemoved() {
        if (!this.unloading_ && this.level != null && !this.level.isClientSide()
                && !this.level.getBlockState(this.worldPosition).is(this.getBlockState().getBlock())) {
            preRemoveSideEffects(this.worldPosition, this.getBlockState());
        }
        super.setRemoved();
    }
}
