package dev.groundburst.content.launch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.MissileItem;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.LaunchSiloBlockEntity.Readiness;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.machine.SideConfig;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.registry.GBLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Vertical Launch Battery (DESIGN.md §17.15, the user's volley launcher): sixteen Strike Cells in two eight-cell
 * modules, after the Navy's Mk 41 as it stands ashore. Each cell holds one Light or Medium missile, conventional only; a
 * Launch Computer, the Launch Control Station or a Laser Designator aims, arms and fires it like any launcher (it is a
 * {@link LaunchSite}). LAUNCH starts a 10 s countdown, then it ripples its whole load out, one cell every
 * {@link #RIPPLE} ticks in a spread order, each hatch swinging open as its missile lights and climbs out: every missile
 * goes to its own point of the battery's spread round the target (a point, a line across the line of attack, a ring or
 * a grid, so far apart; set on its screen), then its own guidance's scatter. 20 000 FE a missile. An EMP, a redstone
 * halt or a broken structure stops the ripple: what has not gone stays loaded.
 */
public class VerticalLaunchBatteryBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, LaunchSite {
    public static final int CELLS = 16;
    public static final int ENERGY_CAPACITY = 640_000, LAUNCH_COST = 20_000;
    /** From LAUNCH to the first missile, between missiles, and after the last before it is ready again (ticks). */
    public static final int COUNTDOWN = 200, RIPPLE = 5, LINGER = 40;
    /** How far apart the spread's points may be set (blocks), and its step. */
    public static final int MAX_SPACING = 48, SPACING_STEP = 4;
    /** How long a hatch stands open after its cell fires, how long it takes to swing, how far it swings (degrees). */
    static final int HATCH_OPEN = 30, HATCH_SWING = 5;
    static final float HATCH_ANGLE = 110;
    private static final int RECHECK_INTERVAL = 40;
    /**
     * The model's figures (tools/assets/vertical_launch_battery.py), pixels for a controller facing north (+x east, +z
     * south, the controller's block's foot at 0): the cells' middles, four across and four deep; the cells' floor and
     * the hatches' top; the modules' uptakes (between each module's two columns).
     */
    public static final double[] CELL_X = {-16, 0, 16, 32}, CELL_Z = {16, 32, 48, 64};
    public static final double CELL_FLOOR = 1, HATCH_TOP = 16.75;
    static final double[] UPTAKE_X = {-8, 24};
    /** The order its cells fire in: back and forth across the two modules, so the launcher's weight stays even. */
    private static final int[] ORDER = {0, 15, 5, 10, 3, 12, 6, 9, 1, 14, 4, 11, 2, 13, 7, 8};

    public enum Phase { IDLE, COUNTDOWN, FIRING }

    private static final String T_READY = GBLang.add(GroundBurst.MODID + ".vls.ready", "READY");
    private static final String T_ARMED = GBLang.add(GroundBurst.MODID + ".vls.armed", "ARMED");
    private static final String T_COUNTING = GBLang.add(GroundBurst.MODID + ".vls.counting", "LAUNCH IN %s");
    private static final String T_FIRING = GBLang.add(GroundBurst.MODID + ".vls.firing", "RIPPLE FIRE");
    private static final String T_EMPTY = GBLang.add(GroundBurst.MODID + ".vls.empty", "Load Light or Medium missiles");
    private static final String T_NO_POWER = GBLang.add(GroundBurst.MODID + ".vls.no_power", "No power: needs %sk FE");
    private static final String T_INCOMPLETE = GBLang.add(GroundBurst.MODID + ".vls.incomplete", "Structure incomplete");
    private static final String T_LOADED = GBLang.add(GroundBurst.MODID + ".vls.loaded", "Cells loaded: %s / 16");
    private static final String T_TARGET = GBLang.add(GroundBurst.MODID + ".vls.target", "Target: %s, %s");
    private static final String T_NO_TARGET = GBLang.add(GroundBurst.MODID + ".vls.no_target", "Aim it from a Launch Computer");
    private static final String[] T_PATTERN = {
            GBLang.add(GroundBurst.MODID + ".vls.pattern.point", "Spread: all on the point"),
            GBLang.add(GroundBurst.MODID + ".vls.pattern.line", "Spread: a line, %s apart"),
            GBLang.add(GroundBurst.MODID + ".vls.pattern.ring", "Spread: a ring of %s"),
            GBLang.add(GroundBurst.MODID + ".vls.pattern.grid", "Spread: a grid, %s apart")};
    public static final String T_BUTTON_PATTERN = GBLang.add("groundburst.gui.vls_pattern", "Spread pattern: point, line, ring or grid");
    public static final String T_BUTTON_WIDER = GBLang.add("groundburst.gui.vls_wider", "Spread wider");
    public static final String T_BUTTON_CLOSER = GBLang.add("groundburst.gui.vls_closer", "Spread closer");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private @Nullable BlockPos target;
    private boolean armed;
    private @Nullable UUID launchedBy;
    /** Its spread: the pattern and how far apart its points are (blocks). */
    private LaunchNetwork.Pattern pattern = LaunchNetwork.Pattern.GRID;
    private int spacing = 8;
    /** This launch: the cells to fire in order, each one's aim point (x, z), and how many have gone. */
    private int[] queue = new int[0], aimX = new int[0], aimZ = new int[0];
    private int shot;
    /** When each cell last fired (game time): its hatch stands open for a moment after. */
    private final long[] opened = new long[CELLS];

    public VerticalLaunchBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(GBLaunch.VLS_ENTITY.get(), pos, state, ENERGY_CAPACITY, 16_384, 0, CELLS, cellSlots(), new int[0]);
        java.util.Arrays.fill(this.opened, Long.MIN_VALUE / 2);
    }

    private static int[] cellSlots() {
        int[] slots = new int[CELLS];
        for (int i = 0; i < CELLS; i++) {
            slots[i] = i;
        }
        return slots;
    }

    // --- what it holds ------------------------------------------------------------------------------------------------------
    /** Whether a missile can go in a cell: Light or Medium, conventional (no nuclear or exotic warhead), not a cruise missile. */
    public static boolean fits(ItemStack stack) {
        MissileSpec spec = MissileItem.spec(stack);
        return spec != null && (spec.body() == SizeClass.S || spec.body() == SizeClass.M) && !spec.needsComplex() && !spec.cruise();
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < CELLS && fits(stack);
    }

    @Override
    protected int slotLimit(int slot) {
        return 1;
    }

    public @Nullable MissileSpec missile(int cell) {
        return MissileItem.spec(this.inventory.getItem(cell));
    }

    /** Missiles in its cells. */
    public int loaded() {
        int n = 0;
        for (int i = 0; i < CELLS; i++) {
            if (missile(i) != null) {
                n++;
            }
        }
        return n;
    }

    public Phase phase() {
        return this.phase;
    }

    public LaunchNetwork.Pattern pattern() {
        return this.pattern;
    }

    public int spacing() {
        return this.spacing;
    }

    /** Sets its spread (not while it fires). */
    public boolean setSpread(LaunchNetwork.Pattern p, int s) {
        if (this.phase != Phase.IDLE) {
            return false;
        }
        this.pattern = p;
        this.spacing = Mth.clamp(s, 0, MAX_SPACING);
        setChanged();
        return true;
    }

    /** How far the spread's outermost point lies from its middle, for {@code n} missiles (blocks). */
    public double spreadRadius(int n) {
        if (n <= 1 || this.spacing == 0) {
            return 0;
        }
        return switch (this.pattern) {
            case POINT -> 0;
            case LINE -> this.spacing * (n - 1) / 2.0;
            case RING -> this.spacing;
            case GRID -> {
                int cols = (int) Math.ceil(Math.sqrt(n)), rows = (n + cols - 1) / cols;
                yield Math.hypot(this.spacing * (cols - 1) / 2.0, this.spacing * (rows - 1) / 2.0);
            }
        };
    }

    // --- a launch site ---------------------------------------------------------------------------------------------------
    @Override
    public Readiness readiness() {
        if (!isFormed()) {
            return Readiness.NOT_FORMED;
        }
        if (isEmpDisabled()) {
            return Readiness.EMP;
        }
        if (redstoneHalted()) {
            return Readiness.HALTED;
        }
        if (this.phase != Phase.IDLE) {
            return Readiness.COUNTING;
        }
        int n = loaded();
        if (n == 0) {
            return Readiness.NO_MISSILE;
        }
        return this.energy.stored() < (long) LAUNCH_COST * n ? Readiness.NO_POWER : Readiness.READY;
    }

    /** Its strike: the worst of its missiles', widened by its spread. */
    @Override
    public Strike strike(ServerLevel level) {
        Strike out = Strike.NONE;
        int n = 0;
        for (int i = 0; i < CELLS; i++) {
            MissileSpec spec = missile(i);
            if (spec != null) {
                out = out.max(Strike.of(level, spec, this.worldPosition, this.target));
                n++;
            }
        }
        if (n == 0) {
            return out;
        }
        float r = (float) spreadRadius(n);
        return new Strike(out.lethal(), out.reach(), out.along() + r, out.across() + r);
    }

    @Override
    public int countdown() {
        return this.phase == Phase.COUNTDOWN ? Math.max(0, COUNTDOWN - this.phaseTicks) : this.phase == Phase.IDLE ? -1 : 0;
    }

    /** Its missiles: the first loaded, as many as it holds. */
    @Override
    public ItemStack shownStack() {
        for (int i = 0; i < CELLS; i++) {
            ItemStack s = this.inventory.getItem(i);
            if (!s.isEmpty()) {
                return s.copyWithCount(loaded());
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public Timing timing() {
        Vec3 from = middle();
        return new Timing(COUNTDOWN + 1 + MissileEntity.IGNITION, false, from.x, from.z, 0);
    }

    @Override
    public @Nullable BlockPos target() {
        return this.target;
    }

    @Override
    public boolean armed() {
        return this.armed;
    }

    /** How far its loaded missiles fly (the shortest of them), blocks; 0 with none. */
    @Override
    public int range() {
        int r = Integer.MAX_VALUE;
        for (int i = 0; i < CELLS; i++) {
            MissileSpec s = missile(i);
            if (s != null) {
                r = Math.min(r, s.range());
            }
        }
        return r == Integer.MAX_VALUE ? 0 : r;
    }

    @Override
    public boolean setTarget(int x, int z) {
        if (this.phase != Phase.IDLE) {
            return false;
        }
        this.target = new BlockPos(x, this.worldPosition.getY(), z);
        if (this.armed && !inRange(this.target)) {
            this.armed = false;
        }
        setChanged();
        return true;
    }

    private boolean inRange(BlockPos at) {
        Vec3 m = middle();
        return Math.hypot(at.getX() + 0.5 - m.x, at.getZ() + 0.5 - m.z) <= range();
    }

    @Override
    public boolean arm(boolean on) {
        if (on && (readiness() != Readiness.READY || this.target == null || !inRange(this.target))) {
            return false;
        }
        this.armed = on;
        setChanged();
        return true;
    }

    @Override
    public boolean launchArmed(ServerLevel level, @Nullable Entity by) {
        return this.armed && this.target != null && launch(level, this.target, by);
    }

    /** Starts the countdown to ripple its whole load at {@code target}, spread round it. False if it is not ready. */
    public boolean launch(ServerLevel level, BlockPos target, @Nullable Entity by) {
        if (readiness() != Readiness.READY) {
            return false;
        }
        this.target = target.immutable();
        this.armed = false;
        this.launchedBy = by == null ? null : by.getUUID();
        List<Integer> cells = new ArrayList<>();
        for (int cell : ORDER) {
            if (missile(cell) != null) {
                cells.add(cell);
            }
        }
        this.queue = cells.stream().mapToInt(Integer::intValue).toArray();
        // Every missile its own point of the spread, centred on the target (a line lies across the line of attack).
        BlockPos from = BlockPos.containing(middle());
        List<int[]> points = LaunchNetwork.points(java.util.Collections.nCopies(this.queue.length, from), target.getX(), target.getZ(),
                this.pattern, this.spacing);
        this.aimX = points.stream().mapToInt(p -> p[0]).toArray();
        this.aimZ = points.stream().mapToInt(p -> p[1]).toArray();
        this.shot = 0;
        enter(Phase.COUNTDOWN);
        level.playSound(null, this.worldPosition, GBLaunch.SIREN.get(), SoundSource.BLOCKS, 4f, 1f);
        return true;
    }

    private void enter(Phase next) {
        this.phase = next;
        this.phaseTicks = 0;
        setChanged();
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** For GameTests: jump to the end of the countdown. */
    public void skipCountdownForTest() {
        if (this.phase == Phase.COUNTDOWN) {
            this.phaseTicks = COUNTDOWN - 1;
        }
    }

    // --- ticking ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        Launches.track(level, this);
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            standDown();
            return false;
        }
        switch (this.phase) {
            case IDLE -> {
                boolean ready = readiness() == Readiness.READY;
                if (this.armed && !ready) {
                    this.armed = false;                          // power or missiles gone: safe again
                    setChanged();
                }
                this.status = ready ? MachineStatus.IDLE : MachineStatus.NO_RECIPE;
                return false;
            }
            case COUNTDOWN -> {
                this.status = MachineStatus.RUNNING;
                this.phaseTicks++;
                int left = COUNTDOWN - this.phaseTicks;
                if (left > 0 && left % 20 == 0) {
                    level.playSound(null, this.worldPosition, dev.groundburst.content.ordnance.GBOrdnance.BOMB_BEEP.get(), SoundSource.BLOCKS, 2f,
                            left <= 60 ? 1.4f : 1f);
                }
                if (left <= 0) {
                    enter(Phase.FIRING);
                }
            }
            case FIRING -> {
                this.status = MachineStatus.RUNNING;
                if (this.phaseTicks % RIPPLE == 0 && this.shot < this.queue.length) {
                    fire(level, this.shot);
                    this.shot++;
                }
                this.phaseTicks++;
                if (this.shot >= this.queue.length && this.phaseTicks >= (this.queue.length - 1) * RIPPLE + LINGER) {
                    finish();
                }
            }
        }
        return true;
    }

    /** The {@code n}th missile of the ripple goes: its hatch swings open, it lights and climbs out of its cell. */
    private void fire(ServerLevel level, int n) {
        int cell = this.queue[n];
        MissileSpec spec = missile(cell);
        if (spec == null || this.target == null || isEmpDisabled() || this.energy.stored() < LAUNCH_COST) {
            return;
        }
        this.inventory.removeItem(cell, 1);
        this.energy.consume(LAUNCH_COST);
        this.opened[cell] = level.getGameTime();
        Entity by = this.launchedBy == null ? null : level.getEntity(this.launchedBy);
        Vec3 from = cellFloor(cell);
        Vec3 point = new Vec3(this.aimX[n] + 0.5, this.target.getY(), this.aimZ[n] + 0.5);
        Vec3 aim = spec.guidance().aim(from, point, level.getRandom());
        Flights.Flight flight = MissileEntity.launch(level, from, aim, spec, by, (HATCH_TOP - CELL_FLOOR) / 16.0);
        LaunchNetwork.launched(level, this.worldPosition, flight);
        // The motor's blast vented up the module's uptake beside the cell (a short burst: sixteen missiles' own exhaust is
        // smoke enough).
        Vec3 uptake = modelPoint(UPTAKE_X[CELL_X[cell % 4] < 8 ? 0 : 1], HATCH_TOP, CELL_Z[cell / 4]);
        level.sendParticles(ParticleTypes.FLAME, uptake.x, uptake.y, uptake.z, 10, 0.15, 0.4, 0.4, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, uptake.x, uptake.y + 0.5, uptake.z, 6, 0.3, 0.5, 0.5, 0.04);
        setChanged();
        sync();
    }

    /** Back to idle, ready for the next load. */
    private void finish() {
        enter(Phase.IDLE);
        this.queue = new int[0];
        this.aimX = new int[0];
        this.aimZ = new int[0];
        this.shot = 0;
        sync();
    }

    /** Knocked out, stopped or broken: a countdown is scrubbed, a ripple stopped, an armed one made safe. */
    private void standDown() {
        if (this.armed) {
            this.armed = false;
            setChanged();
        }
        if (this.phase != Phase.IDLE) {
            finish();
        }
    }

    @Override
    protected void onEmp(ServerLevel level) {
        standDown();
    }

    @Override
    protected void onRedstoneHalt(ServerLevel level) {
        standDown();
    }

    // --- where things are -------------------------------------------------------------------------------------------------
    /**
     * A point on the structure in the world: {@code right}, {@code up} and {@code back} pixels from the bottom middle of the
     * controller's cell, as the model has them (for a controller facing north), turned with the structure.
     */
    protected Vec3 modelPoint(double right, double up, double back) {
        Direction facing = controllerFacing();
        Direction r = facing.getClockWise(), b = facing.getOpposite();
        Vec3 base = Vec3.atBottomCenterOf(this.worldPosition);
        return base.add(r.getStepX() * right / 16 + b.getStepX() * back / 16, up / 16, r.getStepZ() * right / 16 + b.getStepZ() * back / 16);
    }

    /** The floor of cell {@code cell}, under its missile's tail. */
    public Vec3 cellFloor(int cell) {
        return modelPoint(CELL_X[cell % 4], CELL_FLOOR, CELL_Z[cell / 4]);
    }

    /** The middle of the launcher, over its hatches (where its reach is reckoned from). */
    public Vec3 middle() {
        return modelPoint(8, HATCH_TOP, 40);
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GBLaunch.VLS;
    }

    @Override
    public BlockPos controllerPos() {
        return this.worldPosition;
    }

    @Override
    public Direction controllerFacing() {
        return getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
    }

    @Override
    public boolean faceOpen(SideConfig.Face face) {
        return false;
    }

    @Override
    public boolean hasSideConfig() {
        return false;
    }

    @Override
    public boolean isFormed() {
        return getBlockState().hasProperty(MultiblockPartBlock.FORMED) && getBlockState().getValue(MultiblockPartBlock.FORMED);
    }

    @Override
    public void revalidate() {
        if (this.level == null || this.level.isClientSide() || isRemoved()) {
            return;
        }
        boolean complete = definition().check(this.level, this.worldPosition, controllerFacing()).isEmpty();
        if (complete != isFormed()) {
            MultiblockController.setPartsFormed(this.level, this, complete);
            this.level.setBlock(this.worldPosition, getBlockState().setValue(MultiblockPartBlock.FORMED, complete), Block.UPDATE_ALL);
            setChanged();
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide() && state.hasProperty(MultiblockPartBlock.FORMED) && state.getValue(MultiblockPartBlock.FORMED)) {
            MultiblockController.setPartsFormed(this.level, this, false);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel server) {
            Launches.track(server, this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level instanceof ServerLevel server) {
            Launches.untrack(server, this.worldPosition);
        }
    }

    // --- screen -----------------------------------------------------------------------------------------------------------
    private static final int DATA_READINESS = 0, DATA_COUNTDOWN = 1, DATA_ARMED = 2, DATA_LOADED = 3, DATA_PHASE = 4, DATA_PATTERN = 5,
            DATA_SPACING = 6;

    @Override
    protected int extraDataCount() {
        return 7;
    }

    /** No cell can be emptied or loaded from the countdown until the ripple is over. */
    @Override
    public boolean slotLocked(int slot, MachineMenu menu) {
        return menu.extra(DATA_READINESS) == Readiness.COUNTING.ordinal();
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_READINESS -> readiness().ordinal();
            case DATA_COUNTDOWN -> Math.max(0, countdown());
            case DATA_ARMED -> this.armed ? 1 : 0;
            case DATA_LOADED -> loaded();
            case DATA_PHASE -> this.phase.ordinal();
            case DATA_PATTERN -> this.pattern.ordinal();
            default -> this.spacing;
        };
    }

    /** Its buttons: 0 the spread's pattern (round the four), 1 wider, 2 closer. */
    @Override
    public boolean handleButton(int button) {
        LaunchNetwork.Pattern[] all = LaunchNetwork.Pattern.values();
        return switch (button) {
            case 0 -> setSpread(all[(this.pattern.ordinal() + 1) % all.length], this.spacing);
            case 1 -> setSpread(this.pattern, this.spacing + SPACING_STEP);
            case 2 -> setSpread(this.pattern, this.spacing - SPACING_STEP);
            default -> false;
        };
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        Readiness r = Readiness.values()[Mth.clamp(menu.extra(DATA_READINESS), 0, Readiness.values().length - 1)];
        Phase p = Phase.values()[Mth.clamp(menu.extra(DATA_PHASE), 0, Phase.values().length - 1)];
        Component state = switch (r) {
            case NOT_FORMED -> Component.translatable(T_INCOMPLETE);
            case NO_MISSILE -> Component.translatable(T_EMPTY);
            case NO_POWER -> Component.translatable(T_NO_POWER, LAUNCH_COST * Math.max(1, menu.extra(DATA_LOADED)) / 1000);
            case READY -> Component.translatable(menu.extra(DATA_ARMED) != 0 ? T_ARMED : T_READY);
            case COUNTING -> p == Phase.COUNTDOWN ? Component.translatable(T_COUNTING, (menu.extra(DATA_COUNTDOWN) + 19) / 20)
                    : Component.translatable(T_FIRING);
            default -> Component.translatable(r == Readiness.EMP ? "groundburst.silo.state.emp" : "groundburst.silo.state.halted");
        };
        int pat = Mth.clamp(menu.extra(DATA_PATTERN), 0, T_PATTERN.length - 1);
        Component spread = Component.translatable(T_PATTERN[pat], menu.extra(DATA_SPACING));
        Component aim = this.target == null ? Component.translatable(T_NO_TARGET) : Component.translatable(T_TARGET, this.target.getX(), this.target.getZ());
        return List.of(state, Component.translatable(T_LOADED, menu.extra(DATA_LOADED)), spread, aim);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(dev.groundburst.registry.GBMenus.VERTICAL_LAUNCH_BATTERY.get(), containerId, inventory, this,
                dev.groundburst.content.machine.MachineLayouts.VERTICAL_LAUNCH_BATTERY, this.data);
    }

    // --- model ------------------------------------------------------------------------------------------------------------
    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** Unformed, nothing (the blocks show); formed, the whole launcher, its beacon lit only while it counts down and fires. */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        BlockState state = getBlockState();
        return state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) ? Set.of() : Set.of("beacon");
    }

    /** Each hatch swung open on its hinge for a moment after its cell fires: up fast, held, then shut. */
    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        if (this.level == null || !isFormed()) {
            return Map.of();
        }
        double now = this.level.getGameTime() + partialTick;
        Map<String, float[]> out = new HashMap<>();
        for (int i = 0; i < CELLS; i++) {
            double t = now - this.opened[i];
            if (t < 0 || t >= HATCH_OPEN) {
                continue;
            }
            double u = t < HATCH_SWING ? t / HATCH_SWING : t > HATCH_OPEN - HATCH_SWING ? (HATCH_OPEN - t) / HATCH_SWING : 1;
            u = u * u * (3 - 2 * u);
            out.put("hatch" + i, new float[] {(float) Math.toRadians(HATCH_ANGLE * u), 0, 0});
        }
        return out;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("phase", this.phase.name());
        output.putInt("phase_ticks", this.phaseTicks);
        output.putBoolean("armed", this.armed);
        if (this.target != null) {
            output.putLong("target", this.target.asLong());
        }
        if (this.launchedBy != null) {
            output.putString("launched_by", this.launchedBy.toString());
        }
        output.putString("pattern", this.pattern.name());
        output.putInt("spacing", this.spacing);
        output.putIntArray("queue", this.queue);
        output.putIntArray("aim_x", this.aimX);
        output.putIntArray("aim_z", this.aimZ);
        output.putInt("shot", this.shot);
        long now = this.level == null ? 0 : this.level.getGameTime();
        for (int i = 0; i < CELLS; i++) {
            if (now - this.opened[i] < HATCH_OPEN) {
                output.putLong("hatch" + i, this.opened[i]);       // only hatches still open: what a client needs
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            this.phase = Phase.valueOf(input.getStringOr("phase", Phase.IDLE.name()));
        } catch (IllegalArgumentException e) {
            this.phase = Phase.IDLE;
        }
        this.phaseTicks = input.getIntOr("phase_ticks", 0);
        this.armed = input.getBooleanOr("armed", false);
        this.target = input.getLong("target").map(BlockPos::of).orElse(null);
        this.launchedBy = input.getString("launched_by").map(UUID::fromString).orElse(null);
        try {
            this.pattern = LaunchNetwork.Pattern.valueOf(input.getStringOr("pattern", LaunchNetwork.Pattern.GRID.name()));
        } catch (IllegalArgumentException e) {
            this.pattern = LaunchNetwork.Pattern.GRID;
        }
        this.spacing = Mth.clamp(input.getIntOr("spacing", 8), 0, MAX_SPACING);
        this.queue = input.getIntArray("queue").orElse(new int[0]);
        this.aimX = input.getIntArray("aim_x").orElse(new int[0]);
        this.aimZ = input.getIntArray("aim_z").orElse(new int[0]);
        if (this.aimX.length != this.queue.length || this.aimZ.length != this.queue.length) {
            this.queue = new int[0];
            this.aimX = new int[0];
            this.aimZ = new int[0];
        }
        this.shot = input.getIntOr("shot", 0);
        for (int i = 0; i < CELLS; i++) {
            this.opened[i] = input.getLongOr("hatch" + i, Long.MIN_VALUE / 2);
        }
    }
}
