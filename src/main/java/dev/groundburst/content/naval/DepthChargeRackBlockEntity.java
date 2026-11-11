package dev.groundburst.content.naval;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.Frame;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.machine.SideConfig;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import dev.groundburst.port.ValueInput;
import dev.groundburst.port.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Depth Charge Rack (DESIGN.md §14.6): a ship's stern rack, its charges lying across two rails sloping down to a
 * hinged trap over the water. Charges loaded at its top end (by hand, or by pipe, hopper or Create's funnels into its
 * Item Port) roll down the rails to the trap. A redstone signal on any of its blocks rolls the front one off (a pulse:
 * one; held: one every interval, a pattern, until it stops or the rack is empty); so do its screen's buttons (one, or a
 * pattern of all it holds), the only way while a Create contraption carries it (no redstone reaches it there). It can
 * set every charge it rolls off to one depth, or leave each at its own. A comparator reads how many it holds.
 * <p>
 * Its slots are its places on the rails, slot 0 against the trap: a charge with an empty place in front of it rolls on
 * (every charge behind a gap a place forward, together), and the client rolls their drums down the rails as it does.
 * Tripped, the trap drops and the front charge rolls forward over it to the edge ({@link #OFF} ticks), then leaves the
 * front lying on its side, still rolling, with whatever carries the rack (a ship's way); the rest roll down behind it.
 */
public class DepthChargeRackBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int SLOTS = 6;
    /** Ticks a charge takes to roll down one place; the least between two trips (the trap shut). */
    public static final int ROLL = 8, GAP = 12;
    /**
     * From a trip: the trap down ({@code OPEN} ticks), the front charge over the edge and gone ({@code OFF}: it rolls over
     * the trap from {@code OPEN}), the trap back up ({@code TRAP}).
     */
    public static final int OPEN = 2, OFF = 6, TRAP = 10;
    /** The intervals between a pattern's charges (ticks, trip to trip), the default first; 16 is as fast as it can go. */
    public static final int[] INTERVALS = {30, 16, 20, 40, 60};
    private static final int RECHECK = 40;

    /**
     * The model's figures (tools/assets/depth_charge_rack.py), pixels from the bottom middle of the controller's cell for a
     * controller facing north (x right, y up, z back): the charges' spacing along the rails, the rails' slope (degrees, up
     * toward the back), the incline's pivot and the front charge's middle on it, the hoops' radius (they roll on them).
     */
    public static final double SPACING = 10, SLOPE = 3, PIVOT_Y = 3.4, PIVOT_Z = -2.6, DRUM_Y = 8.4, HOOP_R = 4.95;
    /** How fast a charge leaves the trap (blocks a tick, out along the rack's front, and down): as it rolled over it. */
    public static final double PUSH = 0.16, DROP = 0.02;
    /** How far the front charge rolls over the trap to the edge, along the rails (pixels): its middle then at {@link #releasePoint}. */
    public static final double OVER = 10.27;

    private static final String P = GroundBurst.MODID + ".rack.";
    private static final String T_READY = GBLang.add(P + "ready", "READY");
    private static final String T_RELEASING = GBLang.add(P + "releasing", "RELEASING");
    private static final String T_EMPTY = GBLang.add(P + "empty", "Load depth charges");
    private static final String T_INCOMPLETE = GBLang.add(P + "incomplete", "Structure incomplete");
    private static final String T_LOADED = GBLang.add(P + "loaded", "Charges: %s / %s");
    private static final String T_DEPTH_OWN = GBLang.add(P + "depth_own", "Depth: each its own");
    private static final String T_DEPTH = GBLang.add(P + "depth", "Depth: %s blocks");
    private static final String T_INTERVAL = GBLang.add(P + "interval", "Interval: %s s");
    static {
        GBLang.add(GroundBurst.MODID + ".gui.rack_release", "Release one charge");
        GBLang.add(GroundBurst.MODID + ".gui.rack_pattern", "Release them all, one each interval (a pattern)");
        GBLang.add(GroundBurst.MODID + ".gui.rack_depth", "Depth: set every charge to one, or leave each at its own");
        GBLang.add(GroundBurst.MODID + ".gui.rack_interval", "The interval between a pattern's charges");
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The depth set on every charge rolled off (an index into {@link DepthCharge#DEPTHS}), or -1: each its own. */
    private int depth = -1;
    private int interval = INTERVALS[0];
    /** A charge asked for (a redstone pulse, the button), and how many a pattern still has to go. */
    private boolean pending;
    private int patternLeft;
    private boolean wasPowered;
    /** When it was last tripped and last rolled its charges down a place (game time), and which places they rolled into. */
    private long trippedAt = Long.MIN_VALUE / 2, rolledAt = Long.MIN_VALUE / 2;
    private int rolledMask;
    /** Tripped: its front charge going over the edge, gone {@link #OFF} ticks after {@link #trippedAt}. */
    private boolean tripping;

    public DepthChargeRackBlockEntity(BlockPos pos, BlockState state) {
        super(GBNaval.RACK_ENTITY.get(), pos, state, 0, 0, 0, SLOTS, new int[] {0, 1, 2, 3, 4, 5}, new int[0]);
    }

    // --- what it holds ------------------------------------------------------------------------------------------------------
    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return stack.is(GBNaval.DEPTH_CHARGE.get());
    }

    @Override
    protected int slotLimit(int slot) {
        return 1;
    }

    /** Charges on it. */
    public int loaded() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) {
            n += this.inventory.getItem(i).isEmpty() ? 0 : 1;
        }
        return n;
    }

    /** What a comparator reads: 0 empty, 15 full. */
    public int comparatorSignal() {
        return Mth.ceil(15.0 * loaded() / SLOTS);
    }

    /** The depth set on every charge it rolls off (blocks), or -1: each charge's own. */
    public int depthSetting() {
        return this.depth < 0 ? -1 : DepthCharge.DEPTHS[this.depth];
    }

    public int interval() {
        return this.interval;
    }

    // --- ticking ------------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        if (!carried() && (level.getGameTime() + this.worldPosition.hashCode()) % RECHECK == 0) {
            revalidate();
        }
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            this.pending = false;
            this.patternLeft = 0;
            this.tripping = false;
            return false;
        }
        long now = level.getGameTime();
        if (this.tripping && now - this.trippedAt >= OFF) {
            this.tripping = false;
            release(level);                                   // over the edge; the rest roll down behind it at once
        }
        rollDown(now);
        boolean powered = !carried() && powered(level);       // carried: no world round it, so no redstone (its screen)
        if (powered && !this.wasPowered) {
            this.pending = true;                              // a pulse: one now
        }
        this.wasPowered = powered;
        boolean due = this.pending || (this.patternLeft > 0 || powered) && now - this.trippedAt >= this.interval;
        if (this.tripping) {
            // its front charge on its way over the edge: the next waits
        } else if (this.inventory.getItem(0).isEmpty()) {
            this.pending = false;                             // nothing at the trap (an empty rack): nothing to do
            if (loaded() == 0) {
                this.patternLeft = 0;
            }
        } else if (due && now - this.trippedAt >= GAP && now - this.rolledAt >= ROLL) {
            trip(level, now);
            this.pending = false;
            if (this.patternLeft > 0) {
                this.patternLeft--;
            }
        }
        boolean busy = this.tripping || this.patternLeft > 0 || powered && loaded() > 0;
        this.status = loaded() == 0 ? MachineStatus.NO_RECIPE : busy ? MachineStatus.RUNNING : MachineStatus.IDLE;
        return busy;
    }

    /** Whether any of its blocks has a redstone signal. */
    private boolean powered(ServerLevel level) {
        for (BlockPos p : definition().positionsOf(MultiblockDefinition.CONTROLLER, this.worldPosition, controllerFacing())) {
            if (level.hasNeighborSignal(p)) {
                return true;
            }
        }
        for (char key : new char[] {'R', 'I'}) {
            for (BlockPos p : definition().positionsOf(key, this.worldPosition, controllerFacing())) {
                if (level.hasNeighborSignal(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Every charge behind an empty place rolls a place forward (together), as charges on a sloping rack do. */
    private void rollDown(long now) {
        int gap = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (this.inventory.getItem(i).isEmpty()) {
                gap = i;
                break;
            }
        }
        if (gap < 0 || now - this.rolledAt < ROLL) {
            return;
        }
        int mask = 0;
        for (int i = gap; i < SLOTS - 1; i++) {
            ItemStack behind = this.inventory.getItem(i + 1);
            if (!behind.isEmpty()) {
                mask |= 1 << i;
            }
            this.inventory.setItem(i, behind);
        }
        this.inventory.setItem(SLOTS - 1, ItemStack.EMPTY);
        if (mask != 0) {
            this.rolledAt = now;
            this.rolledMask = mask;
            setChanged();
            syncToClients();
        }
    }

    /** The trap is tripped: it drops, the lever thrown, and the front charge starts rolling over it to the edge. */
    private void trip(ServerLevel level, long now) {
        this.tripping = true;
        this.trippedAt = now;
        playSound(level, frontCharge(), GBNaval.RACK_RELEASE.get(), SoundSource.BLOCKS, 1.2f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        setChanged();
        syncToClients();
    }

    /**
     * The front charge goes over the edge into whatever is in front of it (the sea), at its depth or the rack's: lying on
     * its side across the rack's front as it lay on the rails (its dial to the rack's left), still rolling.
     */
    private void release(ServerLevel level) {
        if (this.inventory.getItem(0).isEmpty()) {
            return;                                           // taken off by hand on its way over
        }
        ItemStack charge = this.inventory.getItem(0).copyWithCount(1);
        if (this.depth >= 0) {
            dev.groundburst.legacy.component.Comps.set(charge, GBDataComponents.NAVAL_DEPTH.get(), DepthCharge.DEPTHS[this.depth]);
        }
        this.inventory.setItem(0, ItemStack.EMPTY);
        Vec3 local = releasePoint();
        Frame frame = frame();
        Direction f = controllerFacing(), left = f.getCounterClockWise();
        Vec3 push = frame.directionToWorld(new Vec3(f.getStepX() * PUSH, -DROP, f.getStepZ() * PUSH)).add(frame.velocity(local));
        Vec3 axis = frame.directionToWorld(new Vec3(left.getStepX(), 0, left.getStepZ()));
        DepthCharge.release(level, inWorld(local), push, charge, null, (float) Math.toDegrees(Math.atan2(axis.x, axis.z)));
        setChanged();
        syncToClients();
    }

    /** Where the front charge's middle is (the block's own coordinates), as the model draws it on its tilted rails. */
    public Vec3 frontCharge() {
        double a = Math.toRadians(SLOPE), dy = DRUM_Y - PIVOT_Y;
        return modelPoint(0, PIVOT_Y + dy * Math.cos(a), PIVOT_Z - dy * Math.sin(a));
    }

    /**
     * Where a charge leaves it: the front charge rolled {@link #OVER} on down the rails, over the trap to just past the
     * edge, clear of its front (a charge let go inside its block came to rest on it, as a charge on dry ground does).
     */
    public Vec3 releasePoint() {
        double a = Math.toRadians(SLOPE), dy = DRUM_Y - PIVOT_Y;
        return modelPoint(0, PIVOT_Y + dy * Math.cos(a) - OVER * Math.sin(a), PIVOT_Z - OVER * Math.cos(a) - dy * Math.sin(a));
    }

    /**
     * A point on the structure: {@code right}, {@code up} and {@code back} pixels from the bottom middle of the controller's
     * cell, as the model has them (for a controller facing north), turned with the structure.
     */
    public Vec3 modelPoint(double right, double up, double back) {
        Direction facing = controllerFacing();
        Direction r = facing.getClockWise(), b = facing.getOpposite();
        Vec3 base = Vec3.atBottomCenterOf(this.worldPosition);
        return base.add(r.getStepX() * right / 16 + b.getStepX() * back / 16, up / 16, r.getStepZ() * right / 16 + b.getStepZ() * back / 16);
    }

    /** Releases one charge now (as a redstone pulse does). */
    public void releaseOne() {
        this.pending = true;
    }

    /** Releases every charge it holds, one each interval (a pattern). */
    public void releaseAll() {
        this.patternLeft = loaded();
        this.pending = true;
    }

    // --- screen -------------------------------------------------------------------------------------------------------------
    private static final int B_RELEASE = 0, B_PATTERN = 1, B_DEPTH = 2, B_INTERVAL = 3;
    private static final int DATA_LOADED = 0, DATA_DEPTH = 1, DATA_INTERVAL = 2, DATA_STATE = 3;

    @Override
    public boolean handleButton(int button) {
        switch (button) {
            case B_RELEASE -> releaseOne();
            case B_PATTERN -> releaseAll();
            case B_DEPTH -> this.depth = this.depth + 1 >= DepthCharge.DEPTHS.length ? -1 : this.depth + 1;
            case B_INTERVAL -> {
                int i = 0;
                while (i < INTERVALS.length && INTERVALS[i] != this.interval) {
                    i++;
                }
                this.interval = INTERVALS[(i + 1) % INTERVALS.length];
            }
            default -> {
                return super.handleButton(button);
            }
        }
        setChanged();
        return true;
    }

    @Override
    protected int extraDataCount() {
        return 4;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_LOADED -> loaded();
            case DATA_DEPTH -> depthSetting();
            case DATA_INTERVAL -> this.interval;
            default -> !isFormed() ? 0 : loaded() == 0 && !this.tripping ? 1
                    : this.tripping || this.patternLeft > 0 || this.status == MachineStatus.RUNNING ? 3 : 2;
        };
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        Component state = Component.translatable(switch (menu.extra(DATA_STATE)) {
            case 0 -> T_INCOMPLETE;
            case 1 -> T_EMPTY;
            case 3 -> T_RELEASING;
            default -> T_READY;
        });
        int d = menu.extra(DATA_DEPTH);
        Component depth = d < 0 ? Component.translatable(T_DEPTH_OWN) : Component.translatable(T_DEPTH, d);
        return List.of(state, Component.translatable(T_LOADED, menu.extra(DATA_LOADED), SLOTS), depth,
                Component.translatable(T_INTERVAL, String.format(java.util.Locale.ROOT, "%.1f", menu.extra(DATA_INTERVAL) / 20.0)));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(dev.groundburst.registry.GBMenus.DEPTH_CHARGE_RACK.get(), containerId, inventory, this, dev.groundburst.content.machine.MachineLayouts.DEPTH_CHARGE_RACK,
                this.data);
    }

    @Override
    public boolean faceOpen(SideConfig.Face face) {
        return false;                                         // it takes charges through its Item Port only
    }

    @Override
    public boolean hasSideConfig() {
        return false;
    }

    @Override
    protected void onSlotChanged(int slot) {
        super.onSlotChanged(slot);
        syncToClients();
    }

    // --- multiblock ---------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GBNaval.RACK_STRUCTURE;
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
    public boolean isFormed() {
        return getBlockState().hasProperty(MultiblockPartBlock.FORMED) && getBlockState().getValue(MultiblockPartBlock.FORMED);
    }

    @Override
    public void revalidate() {
        if (this.level == null || this.level.isClientSide() || isRemoved() || carried()) {
            return;                                           // carried: no world round it to check (DESIGN.md §17.16)
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

    // --- model --------------------------------------------------------------------------------------------------------------
    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = dev.groundburst.legacy.Regs.access();
        return saveWithoutMetadata();
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Unformed, nothing (its blocks show); formed, each place's charge only while there is one in it. The front one gone
     * over the edge is hidden from then on even before word of it comes (the charge itself is in the world there), until
     * the next has rolled down into its place.
     */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        Set<String> hidden = new HashSet<>();
        for (int i = 0; i < SLOTS; i++) {
            if (this.inventory.getItem(i).isEmpty()) {
                hidden.add("slot" + i);
            }
        }
        if (this.level != null && this.trippedAt > Long.MIN_VALUE / 4 && this.level.getGameTime() - this.trippedAt >= OFF
                && this.rolledAt < this.trippedAt + OFF) {
            hidden.add("slot0");
        }
        return hidden;
    }

    /** How far the front charge has rolled over the trap toward the edge (pixels along the rails; 0 not tripped). */
    private float over(float partialTick) {
        if (this.level == null) {
            return 0;
        }
        float k = this.level.getGameTime() - this.trippedAt + partialTick;
        return k <= OPEN || k > OFF + 1 ? 0 : (float) (OVER * Math.min(1, (k - OPEN) / (OFF - OPEN)));
    }

    /** How far through its last roll down a place it is (0 just rolled, 1 done). */
    private float rollProgress(float partialTick) {
        if (this.level == null) {
            return 1;
        }
        return Mth.clamp((this.level.getGameTime() - this.rolledAt + partialTick) / ROLL, 0f, 1f);
    }

    /**
     * The charges that just rolled down a place, drawn the rest of the way back up the rails, easing in; the front one
     * tripped, rolling on over the trap to the edge.
     */
    @Override
    public Map<String, float[]> boneOffsets(float partialTick) {
        Map<String, float[]> out = new HashMap<>();
        float t = rollProgress(partialTick);
        if (t < 1 && this.rolledMask != 0) {
            float left = (float) (SPACING * (1 - t) * (1 - t));             // eased: fast off the mark, settling in
            for (int i = 0; i < SLOTS; i++) {
                if ((this.rolledMask & (1 << i)) != 0) {
                    out.put("slot" + i, new float[] {0, 0, left});
                }
            }
        }
        float over = over(partialTick);
        if (over > 0 && !out.containsKey("slot0")) {
            out.put("slot0", new float[] {0, 0, -over});
        }
        return out;
    }

    /** The rolling charges turned as they roll (a hoop's turn for the way they have still to go); the trap and the lever. */
    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        Map<String, float[]> out = new HashMap<>();
        float t = rollProgress(partialTick);
        if (t < 1 && this.rolledMask != 0) {
            float turn = (float) (SPACING * (1 - t) * (1 - t) / HOOP_R);
            for (int i = 0; i < SLOTS; i++) {
                if ((this.rolledMask & (1 << i)) != 0) {
                    out.put("slot" + i, new float[] {turn, 0, 0});
                }
            }
        }
        float over = over(partialTick);
        if (over > 0 && !out.containsKey("slot0")) {
            out.put("slot0", new float[] {(float) (-over / HOOP_R), 0, 0});
        }
        if (this.level != null) {
            float k = this.level.getGameTime() - this.trippedAt + partialTick;
            if (k >= 0 && k < TRAP) {
                // down at once, held while the charge goes over, then back up
                float open = k < OPEN ? smooth(k / OPEN) : k < OFF ? 1 : 1 - smooth((k - OFF) / (TRAP - OFF));
                out.put("trap", new float[] {open * 1.3f, 0, 0});
                out.put("lever", new float[] {open * 0.6f, 0, 0});
            }
        }
        return out;
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // --- saving -------------------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag_) { net.minecraft.core.HolderLookup.Provider registries_ = dev.groundburst.legacy.Regs.access(); ValueOutput output = new ValueOutput(tag_, registries_);
        super.saveAdditional(output.tag());
        output.putInt("depth", this.depth);
        output.putInt("interval", this.interval);
        output.putInt("pattern_left", this.patternLeft);
        output.putBoolean("was_powered", this.wasPowered);
        output.putLong("tripped_at", this.trippedAt);
        output.putBoolean("tripping", this.tripping);
        output.putLong("rolled_at", this.rolledAt);
        output.putInt("rolled_mask", this.rolledMask);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag_) { net.minecraft.core.HolderLookup.Provider registries_ = dev.groundburst.legacy.Regs.access(); ValueInput input = new ValueInput(tag_, registries_);
        super.load(input.tag());
        this.depth = Mth.clamp(input.getIntOr("depth", -1), -1, DepthCharge.DEPTHS.length - 1);
        int iv = input.getIntOr("interval", INTERVALS[0]);
        this.interval = java.util.Arrays.stream(INTERVALS).anyMatch(v -> v == iv) ? iv : INTERVALS[0];
        this.patternLeft = Math.max(0, input.getIntOr("pattern_left", 0));
        this.wasPowered = input.getBooleanOr("was_powered", false);
        this.trippedAt = input.getLongOr("tripped_at", Long.MIN_VALUE / 2);
        this.tripping = input.getBooleanOr("tripping", false);
        this.rolledAt = input.getLongOr("rolled_at", Long.MIN_VALUE / 2);
        this.rolledMask = input.getIntOr("rolled_mask", 0);
    }
}
