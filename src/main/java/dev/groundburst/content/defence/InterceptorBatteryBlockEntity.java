package dev.groundburst.content.defence;

import java.util.HashMap;
import java.util.HashSet;
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
 * The Interceptor Battery (DESIGN.md §18.2, Phase 10): the anti-missile defence. A launcher turret on a two-block
 * pedestal, four interceptors in its cells, loaded one at a time from its store ({@link #RELOAD} ticks each). While it
 * has power it watches the sky: a missile coming down within its reach (256 blocks where a Radar Array tracks it, 96 on
 * its own seeker) draws an interceptor, two for the big ones, fired the moment one can meet it in time
 * ({@link Interception#solve}); the turret swings round to it as it fires. Missiles flying out from near it, bound
 * elsewhere, it lets go.
 */
public class InterceptorBatteryBlockEntity extends MachineBlockEntity implements AnimatedMachine, InterceptorLauncher {
    public static final int SLOT_STORE = 0;
    /** Cells, ticks to load one, how often it looks for threats. */
    public static final int CELLS = 4, RELOAD = 60, SCAN_INTERVAL = 4;
    public static final int ENERGY_CAPACITY = 200_000, POWER_USE = 40, LAUNCH_ENERGY = 4_000;
    /** How long at least it holds its aim after firing before it settles back, and the launcher's raise at rest (degrees). */
    static final int HOLD_AIM = 60;
    static final float REST_PITCH = 35, MIN_PITCH = 15, MAX_PITCH = 85;
    /** How fast the turret swings and the launcher raises on the client (degrees a tick). */
    private static final float SLEW = 12, ELEVATE = 6;

    private static final String T_READY = GBLang.add(GroundBurst.MODID + ".interceptor.ready", "Ready: %s of %s, %s in store");
    private static final String T_RADAR = GBLang.add(GroundBurst.MODID + ".interceptor.radar", "Radar tracking: reach %s");
    private static final String T_SEEKER = GBLang.add(GroundBurst.MODID + ".interceptor.seeker", "No radar: seeker, reach %s");
    private static final String T_TALLY = GBLang.add(GroundBurst.MODID + ".interceptor.tally", "Threats %s, shot down %s, missed %s");
    private static final String T_NO_POWER = GBLang.add(GroundBurst.MODID + ".interceptor.no_power", "Needs power: %s FE a tick");
    static final String T_EMP = GBLang.add(GroundBurst.MODID + ".interceptor.emp", "Knocked out by an EMP");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Interceptors in the cells, and ticks into loading the next. */
    private int ready, reload;
    /** Where it last fired (world yaw, as atan2(dx, dz), and degrees over level), and until when it holds that aim. */
    private float aimYaw, aimPitch = REST_PITCH;
    private long aimUntil;
    /** What its screen shows: threats in reach at its last look, whether a radar tracks its sky, its tally. */
    private int threats, kills, misses;
    private boolean radar;

    /** For GameTests: only the missiles it may engage (neighbouring tests' missiles fly past); null in play. */
    private java.util.function.@org.jspecify.annotations.Nullable Predicate<Flights.Flight> testFilter;

    /** The client's turret as drawn (it swings toward its aim at its own pace): yaw from its front, pitch; and when. */
    private float shownYaw = Float.NaN, shownPitch = REST_PITCH;
    private double shownAt;

    public InterceptorBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(GBDefence.BATTERY_ENTITY.get(), pos, state, ENERGY_CAPACITY, 4_000, 0, 1, new int[] {SLOT_STORE}, new int[0]);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return slot == SLOT_STORE && stack.is(GBDefence.INTERCEPTOR_MISSILE.get());
    }

    /** Interceptors in its cells. */
    public int ready() {
        return this.ready;
    }

    /** Its tally: missiles shot down, interceptors that missed (fried ones too). */
    public int kills() {
        return this.kills;
    }

    public int misses() {
        return this.misses;
    }

    /** For GameTests: its cells filled at once. */
    public void fillForTest() {
        this.ready = CELLS;
        sync();
    }

    /** For GameTests: it engages only the missiles {@code filter} lets through (not the next tests' over the fence). */
    public void engageOnlyForTest(java.util.function.Predicate<Flights.Flight> filter) {
        this.testFilter = filter;
    }

    /** It takes upgrade chips: speed loads its cells faster, efficiency spends less power (DESIGN.md §7.3). */
    @Override
    public boolean acceptsUpgrades() {
        return true;
    }

    /** Told how an interceptor of its own came out (from Interceptors): one that never got to its missile counts for nothing. */
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
        int cost = workEnergy(POWER_USE);
        if (!this.energy.consume(cost)) {
            this.status = MachineStatus.NO_POWER;
            return false;
        }
        this.status = MachineStatus.RUNNING;
        ItemStack store = this.inventory.getItem(SLOT_STORE);
        if (this.ready < CELLS && !store.isEmpty()) {
            this.reload += workSteps();
            if (this.reload >= RELOAD) {
                this.reload = 0;
                store.shrink(1);
                this.ready++;
                level.playSound(null, this.worldPosition.above(), GBSounds.ASSEMBLY_CLANK.get(), SoundSource.BLOCKS, 0.6f, 1.5f);
                sync();
            }
        }
        if ((level.getGameTime() + this.worldPosition.hashCode()) % SCAN_INTERVAL == 0) {
            engage(level);
        }
        setChanged();
        return true;
    }

    /** Where its interceptors leave the launcher: over the turret, a block and a half up. */
    private Vec3 launcher() {
        return Vec3.atBottomCenterOf(this.worldPosition).add(0, 1.6, 0);
    }

    /** One look at the sky: a missile coming down within reach that wants another interceptor, and one that can meet it, fired. */
    private void engage(ServerLevel level) {
        Vec3 from = launcher();
        this.radar = Interception.tracked(level, from);
        int seen = 0;
        for (Flights.Flight f : Flights.of(level).all()) {
            if (f.progress() <= 0 || f.ticksToImpact() <= 1 || this.testFilter != null && !this.testFilter.test(f)) {
                continue;                                          // still on its pad, or down already
            }
            boolean tracked = Interception.tracked(level, f.position());
            double reach = tracked ? Interception.RADAR_RANGE : Interception.SEEKER_RANGE;
            Vec3 t = f.target();
            if (Math.hypot(t.x - from.x, t.z - from.z) > reach) {
                continue;                                          // coming down elsewhere (or going out from here)
            }
            seen++;
            if (this.ready <= 0 || this.energy.stored() < LAUNCH_ENERGY
                    || Interceptors.assigned(level, f.id()) >= Interception.salvo(f.body())) {
                continue;
            }
            Interception.Solution s = Interception.solve(f, from, reach);
            if (s != null) {
                fire(level, f, s, tracked);
                break;                                             // one a look: a salvo's second goes on the next
            }
        }
        if (seen != this.threats) {
            this.threats = seen;
            setChanged();
        }
    }

    private void fire(ServerLevel level, Flights.Flight f, Interception.Solution s, boolean tracked) {
        Vec3 from = launcher();
        Vec3 d = s.at().subtract(from);
        this.aimYaw = (float) Math.toDegrees(Math.atan2(d.x, d.z));
        this.aimPitch = (float) Math.clamp(Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))), MIN_PITCH, MAX_PITCH);
        this.aimUntil = level.getGameTime() + Math.max(HOLD_AIM, s.ticks() + 20);      // while its interceptor flies, and a moment
        this.ready--;
        this.energy.consume(LAUNCH_ENERGY);
        double kill = Interception.killOverride >= 0 ? Interception.killOverride : Interception.killChance(f, tracked);
        Interceptors.fire(level, this.worldPosition, from, s, f, kill);
        level.playSound(null, from.x, from.y, from.z, GBSounds.CRUISE_LAUNCH.get(), SoundSource.BLOCKS, 3f, 1.35f);
        sync();
    }

    private void sync() {
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
        int store = this.inventory.getItem(SLOT_STORE).getCount();
        Component first = Component.translatable(T_READY, menu.extra(DATA_READY), CELLS, store);
        Component second = menu.status() == MachineStatus.EMP ? Component.translatable(T_EMP).withStyle(ChatFormatting.RED)
                : menu.status() == MachineStatus.NO_POWER
                ? Component.translatable(T_NO_POWER, workEnergy(POWER_USE)).withStyle(ChatFormatting.RED)
                : menu.extra(DATA_RADAR) > 0 ? Component.translatable(T_RADAR, (int) Interception.RADAR_RANGE)
                : Component.translatable(T_SEEKER, (int) Interception.SEEKER_RANGE);
        Component third = Component.translatable(T_TALLY, menu.extra(DATA_THREATS), menu.extra(DATA_KILLS), menu.extra(DATA_MISSES));
        return List.of(first, second, third);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.INTERCEPTOR_BATTERY.get(), containerId, inventory, this, MachineLayouts.INTERCEPTOR_BATTERY, this.data);
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

    /** Its empty cells' interceptors not drawn. */
    @Override
    public Set<String> hiddenBones() {
        Set<String> hidden = new HashSet<>();
        for (int i = this.ready; i < CELLS; i++) {
            hidden.add("cell" + i);
        }
        return hidden;
    }

    /** The world yaw (atan2(dx, dz), degrees) its front faces. */
    private float frontYaw() {
        Direction facing = getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        return switch (facing) {
            case SOUTH -> 0;
            case EAST -> 90;
            case WEST -> -90;
            default -> 180;
        };
    }

    /**
     * The turret swung round toward its aim (while it holds it) or back to rest (facing its front, the launcher
     * raised {@link #REST_PITCH}), at its own pace from where it was drawn last: the turret about its upright, the
     * launcher about its trunnions.
     */
    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        if (this.level == null) {
            return Map.of();
        }
        double now = this.level.getGameTime() + partialTick;
        boolean aiming = now < this.aimUntil;
        float wantYaw = aiming ? wrap(this.aimYaw - frontYaw()) : 0, wantPitch = aiming ? this.aimPitch : REST_PITCH;
        if (Float.isNaN(this.shownYaw)) {
            this.shownYaw = wantYaw;
            this.shownPitch = wantPitch;
        } else {
            float dt = (float) Math.clamp(now - this.shownAt, 0, 10);
            this.shownYaw += Math.clamp(wrap(wantYaw - this.shownYaw), -SLEW * dt, SLEW * dt);
            this.shownPitch += Math.clamp(wantPitch - this.shownPitch, -ELEVATE * dt, ELEVATE * dt);
        }
        this.shownAt = now;
        Map<String, float[]> out = new HashMap<>();
        out.put("turret", new float[] {0, (float) Math.toRadians(this.shownYaw), 0});
        out.put("launcher", new float[] {(float) Math.toRadians(this.shownPitch), 0, 0});
        return out;
    }

    private static float wrap(float degrees) {
        float d = degrees % 360;
        return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
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
        output.putFloat("aim_yaw", this.aimYaw);
        output.putFloat("aim_pitch", this.aimPitch);
        output.putLong("aim_until", this.aimUntil);
        output.putInt("kills", this.kills);
        output.putInt("misses", this.misses);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ready = input.getIntOr("ready", 0);
        this.reload = input.getIntOr("reload", 0);
        this.aimYaw = input.getFloatOr("aim_yaw", 0);
        this.aimPitch = input.getFloatOr("aim_pitch", REST_PITCH);
        this.aimUntil = input.getLongOr("aim_until", 0);
        this.kills = input.getIntOr("kills", 0);
        this.misses = input.getIntOr("misses", 0);
    }
}
