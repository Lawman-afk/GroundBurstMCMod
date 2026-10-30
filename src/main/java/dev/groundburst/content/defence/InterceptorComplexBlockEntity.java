package dev.groundburst.content.defence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineLayouts;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.machine.SideConfig;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBMenus;
import dev.groundburst.registry.GBSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
 * The Interceptor Complex (DESIGN.md §18.5; the user's request after the Interceptor Battery: a multiblock version, more
 * reliable, two and a half times the reach, bigger volleys). A 5x5 deck three high: a vertical launch system of six
 * Launch Cells (four cells each under armoured hatches, {@link #CELLS} in all) in front of a deckhouse whose face is a
 * fire-control radar of Phased Array Panels. Its cells fill from its store, a cell a second. Powered, it watches the
 * sky: a missile coming down within 640 blocks of it where a Radar Array tracks it, or 240 that its own radar alone
 * sees, draws a volley ({@link Interception#volley}: two, four for class L and up), fired two at a time out of its
 * hatches, straight up and over onto the missile; its radar guides every interceptor all the way in
 * ({@link Interception#COMPLEX_KILL}, and never the seeker's six tenths). If the volley misses and there is time it
 * fires another. Missiles flying out from near it, bound elsewhere, it lets go. An EMP over it knocks it out.
 */
public class InterceptorComplexBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, InterceptorLauncher {
    /** Its store's slots: interceptor missiles waiting for a cell. */
    public static final int STORE = 3;
    /** Cells, ticks to load one, how often it looks for threats, how many it fires at a look. */
    public static final int CELLS = 24, RELOAD = 20, SCAN_INTERVAL = 4, PER_LOOK = 2;
    public static final int ENERGY_CAPACITY = 1_000_000, POWER_USE = 160, LAUNCH_ENERGY = 4_000;
    /** How high its interceptors climb at most before they turn over (higher than a battery's: they leave straight up). */
    static final double CLIMB = 100;
    /** How long a hatch stands open after its cell fires, and how long it takes to swing open and shut (ticks). */
    static final int HATCH_OPEN = 34, HATCH_SWING = 5;
    /** How far a hatch swings open (degrees). */
    static final float HATCH_ANGLE = 105;
    private static final int RECHECK_INTERVAL = 40;
    /**
     * Its cells' middles over the deck, model pixels for a structure facing north (+x east, +z south, the controller's
     * block at 0): six across the three Launch Cells' width, four deep over their two rows; the hatches' tops at
     * {@link #HATCH_TOP}.
     */
    static final double[] CELL_X = {-20, -12, -4, 4, 12, 20}, CELL_Z = {12, 20, 28, 36};
    static final double HATCH_TOP = 16.5;
    /** The order its cells fire in, spread round the launcher. */
    private static final int[] ORDER = {0, 23, 11, 12, 5, 18, 6, 17, 2, 21, 9, 14, 3, 20, 8, 15, 1, 22, 10, 13, 4, 19, 7, 16};

    private static final String T_READY = GBLang.add(GroundBurst.MODID + ".interceptor_complex.ready", "Cells ready: %s of %s, %s in store");
    private static final String T_RADAR = GBLang.add(GroundBurst.MODID + ".interceptor_complex.radar", "Radar Array tracking: reach %s");
    private static final String T_OWN = GBLang.add(GroundBurst.MODID + ".interceptor_complex.own", "Own radar alone: reach %s");
    private static final String T_INCOMPLETE = GBLang.add(GroundBurst.MODID + ".interceptor_complex.incomplete",
            "Finish building it first (the Holo-Projector shows what is missing)");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Interceptors in the cells, ticks into loading the next, and the next cell in {@link #ORDER} to fire. */
    private int ready, reload, cursor;
    /** When each cell last fired (game time): its hatch stands open for a moment after. */
    private final long[] opened = new long[CELLS];
    /** What its screen shows: threats in reach at its last look, whether a radar tracks its sky, its tally. */
    private int threats, kills, misses;
    private boolean radar;
    /** For GameTests: only the missiles it may engage (neighbouring tests' missiles fly past); null in play. */
    private java.util.function.@org.jspecify.annotations.Nullable Predicate<Flights.Flight> testFilter;

    public InterceptorComplexBlockEntity(BlockPos pos, BlockState state) {
        super(GBDefence.COMPLEX_ENTITY.get(), pos, state, ENERGY_CAPACITY, 20_000, 0, STORE, new int[] {0, 1, 2}, new int[0]);
        java.util.Arrays.fill(this.opened, Long.MIN_VALUE / 2);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return slot < STORE && stack.is(GBDefence.INTERCEPTOR_MISSILE.get());
    }

    /** Interceptors in its cells. */
    public int ready() {
        return this.ready;
    }

    /** For GameTests: its cells filled at once. */
    public void fillForTest() {
        this.ready = CELLS;
        setChanged();
    }

    /** For GameTests: it engages only the missiles {@code filter} lets through. */
    public void engageOnlyForTest(java.util.function.Predicate<Flights.Flight> filter) {
        this.testFilter = filter;
    }

    /** Its tally: missiles shot down, interceptors that missed. */
    public int kills() {
        return this.kills;
    }

    @Override
    public boolean acceptsUpgrades() {
        return true;
    }

    @Override
    public void reported(Outcome outcome) {
        switch (outcome) {
            case KILLED -> this.kills++;
            case MISSED, FRIED -> this.misses++;
            case SPENT -> { }
        }
        setChanged();
    }

    // --- working ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            return false;
        }
        if (!this.energy.consume(workEnergy(POWER_USE))) {
            this.status = MachineStatus.NO_POWER;
            return false;
        }
        this.status = MachineStatus.RUNNING;
        if (this.ready < CELLS) {
            int slot = storeSlot();
            if (slot >= 0) {
                this.reload += workSteps();
                if (this.reload >= RELOAD) {
                    this.reload = 0;
                    this.inventory.getItem(slot).shrink(1);
                    this.ready++;
                    Vec3 c = middle();
                    level.playSound(null, c.x, c.y, c.z, GBSounds.ASSEMBLY_CLANK.get(), SoundSource.BLOCKS, 0.5f, 1.3f);
                }
            }
        }
        if ((level.getGameTime() + this.worldPosition.hashCode()) % SCAN_INTERVAL == 0) {
            engage(level);
        }
        setChanged();
        return true;
    }

    private int storeSlot() {
        for (int i = 0; i < STORE; i++) {
            if (!this.inventory.getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** One look at the sky: each missile coming down within reach that wants more of its volley, as many as it can fire this look. */
    private void engage(ServerLevel level) {
        Vec3 middle = middle();
        this.radar = Interception.tracked(level, middle);
        int seen = 0, fired = 0;
        for (Flights.Flight f : Flights.of(level).all()) {
            if (f.progress() <= 0 || f.ticksToImpact() <= 1 || this.testFilter != null && !this.testFilter.test(f)) {
                continue;                                          // still on its pad, or down already
            }
            double reach = Interception.tracked(level, f.position()) ? Interception.COMPLEX_RADAR_RANGE : Interception.COMPLEX_OWN_RANGE;
            Vec3 t = f.target();
            if (Math.hypot(t.x - middle.x, t.z - middle.z) > reach) {
                continue;                                          // coming down elsewhere (or going out from here)
            }
            seen++;
            while (fired < PER_LOOK && this.ready > 0 && this.energy.stored() >= LAUNCH_ENERGY
                    && Interceptors.assigned(level, f.id()) < Interception.volley(f.body())) {
                int cell = ORDER[this.cursor % CELLS];
                Vec3 from = cellTop(cell);
                Interception.Solution s = Interception.solve(f, from, reach);
                if (s == null) {
                    break;                                         // it cannot meet this one in time
                }
                fire(level, f, s, cell, from);
                fired++;
            }
        }
        if (seen != this.threats) {
            this.threats = seen;
            setChanged();
        }
    }

    private void fire(ServerLevel level, Flights.Flight f, Interception.Solution s, int cell, Vec3 from) {
        this.cursor = (this.cursor + 1) % CELLS;
        this.ready--;
        this.energy.consume(LAUNCH_ENERGY);
        this.opened[cell] = level.getGameTime();
        double kill = Interception.killOverride >= 0 ? Interception.killOverride : Interception.killChance(Interception.COMPLEX_KILL, f, true);
        Interceptors.fire(level, this.worldPosition, from, s, f, kill, CLIMB);
        level.playSound(null, from.x, from.y, from.z, GBSounds.CRUISE_LAUNCH.get(), SoundSource.BLOCKS, 3.5f, 1.25f);
        sync();
    }

    private void sync() {
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** A point {@code x}, {@code y}, {@code z} model pixels from the controller's block's foot (as drawn facing north), in the world. */
    private Vec3 at(double x, double y, double z) {
        Direction facing = controllerFacing();
        Direction right = facing.getClockWise(), back = facing.getOpposite();
        Vec3 foot = Vec3.atBottomCenterOf(this.worldPosition);
        return foot.add(right.getStepX() * x / 16 + back.getStepX() * z / 16, y / 16, right.getStepZ() * x / 16 + back.getStepZ() * z / 16);
    }

    /** Where a cell's interceptor leaves it: the middle of its hatch, a little over it. */
    Vec3 cellTop(int cell) {
        return at(CELL_X[cell % 6], HATCH_TOP + 1, CELL_Z[cell / 6]);
    }

    /** The middle of the launcher, over its hatches (where its reach is reckoned from). */
    private Vec3 middle() {
        return at(0, HATCH_TOP + 1, 24);
    }

    /** It goes dark when an EMP field covers its controller or its radar's deckhouse (the middle of its back). */
    @Override
    protected boolean isEmpDisabled() {
        return super.isEmpDisabled() || this.level != null && dev.groundburst.content.effect.EmpFields.inField(this.level, at(0, 24, 56));
    }

    @Override
    public SoundEvent loopSound() {
        return GBSounds.MACHINE_BATTERY_CHARGER.get();          // its electronics' hum
    }

    // --- screen -----------------------------------------------------------------------------------------------------------
    public static final int DATA_READY = 0, DATA_THREATS = 1, DATA_KILLS = 2, DATA_MISSES = 3, DATA_RADAR = 4;

    @Override
    protected int extraDataCount() {
        return 5;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_READY -> this.ready;
            case DATA_THREATS -> this.threats;
            case DATA_KILLS -> this.kills;
            case DATA_MISSES -> this.misses;
            default -> this.radar ? 1 : 0;
        };
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        int store = 0;
        for (int i = 0; i < STORE; i++) {
            store += this.inventory.getItem(i).getCount();
        }
        Component first = Component.translatable(T_READY, menu.extra(DATA_READY), CELLS, store);
        Component second = menu.status() == MachineStatus.INCOMPLETE ? Component.translatable(T_INCOMPLETE).withStyle(ChatFormatting.RED)
                : menu.status() == MachineStatus.EMP ? Component.translatable(InterceptorBatteryBlockEntity.T_EMP).withStyle(ChatFormatting.RED)
                : menu.status() == MachineStatus.NO_POWER
                ? Component.translatable(GroundBurst.MODID + ".interceptor.no_power", workEnergy(POWER_USE)).withStyle(ChatFormatting.RED)
                : menu.extra(DATA_RADAR) > 0 ? Component.translatable(T_RADAR, (int) Interception.COMPLEX_RADAR_RANGE)
                : Component.translatable(T_OWN, (int) Interception.COMPLEX_OWN_RANGE);
        Component third = Component.translatable(GroundBurst.MODID + ".interceptor.tally", menu.extra(DATA_THREATS), menu.extra(DATA_KILLS),
                menu.extra(DATA_MISSES));
        return List.of(first, second, third);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.INTERCEPTOR_COMPLEX.get(), containerId, inventory, this, MachineLayouts.INTERCEPTOR_COMPLEX, this.data);
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GBDefence.COMPLEX;
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

    // --- model ------------------------------------------------------------------------------------------------------------
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Unformed, nothing (the controller is its own block); formed, the whole complex. */
    @Override
    public Set<String> hiddenBones() {
        return isFormed() ? Set.of() : Set.of("frame");
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
        output.putInt("ready", this.ready);
        output.putInt("reload", this.reload);
        output.putInt("cursor", this.cursor);
        output.putInt("kills", this.kills);
        output.putInt("misses", this.misses);
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
        this.ready = input.getIntOr("ready", 0);
        this.reload = input.getIntOr("reload", 0);
        this.cursor = input.getIntOr("cursor", 0);
        this.kills = input.getIntOr("kills", 0);
        this.misses = input.getIntOr("misses", 0);
        for (int i = 0; i < CELLS; i++) {
            this.opened[i] = input.getLongOr("hatch" + i, Long.MIN_VALUE / 2);
        }
    }
}
