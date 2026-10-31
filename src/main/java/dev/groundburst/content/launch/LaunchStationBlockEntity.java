package dev.groundburst.content.launch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.machine.SideConfig;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

/**
 * The Launch Control Station (DESIGN.md §17.12): a console desk and instrument bank with an uplink on top, running its
 * network's launchers anywhere in the dimension ({@link LaunchNetwork}). 40 FE a tick. Its screen is fed while open (the
 * members, the missiles in the air, the log); its model shows the network's state on its big screen and status board.
 */
public class LaunchStationBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, ScreenWatched, MapSource {
    public static final int ENERGY_CAPACITY = 50_000, POWER_USE = 40;
    private static final int RECHECK_INTERVAL = 40, SCOPE_INTERVAL = 10, SUMMARY_INTERVAL = 10, PRINT_GAP = 12;
    /** Lamps on the status board, one for each of the first members. */
    public static final int LAMPS = 16;

    /** What a status board lamp shows (its ordinal goes to clients). */
    public enum Lamp { OFF, READY, ARMED, BUSY, FAULT }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> viewers = new HashSet<>();
    private long network;
    private boolean powered, surveyed;
    private long lastPrint = Long.MIN_VALUE / 2;
    // What the model shows (synced): the code, the counts, the soonest countdown and impact, the lamps.
    private String shownCode = "";
    private int linked, armedCount, flying, soonestCount = -1, soonestImpact = -1;
    private byte[] lamps = new byte[LAMPS];
    private boolean shownPowered;

    public LaunchStationBlockEntity(BlockPos pos, BlockState state) {
        super(GBLaunch.STATION_ENTITY.get(), pos, state, ENERGY_CAPACITY, 1_000, 0, 0, new int[0], new int[0]);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    public long networkId() {
        return this.network;
    }

    public boolean powered() {
        return this.powered;
    }

    @Override
    public boolean mapping() {
        return isFormed() && this.powered;
    }

    @Override
    public boolean boundedMap() {
        return false;
    }

    /** Its network (made the first time it is needed). */
    public LaunchNetwork.@Nullable Network network(ServerLevel level) {
        LaunchNetwork nets = LaunchNetwork.of(level);
        LaunchNetwork.Network n = this.network == 0 ? null : nets.network(this.network);
        if (n == null) {
            this.network = nets.create(level, this.worldPosition);
            setChanged();
            n = nets.network(this.network);
        }
        return n;
    }

    /** Joins another network (Launch Codes for it used on this station, sneaking): its old one keeps its members. */
    public void join(ServerLevel level, long id) {
        LaunchNetwork nets = LaunchNetwork.of(level);
        if (id == this.network || nets.network(id) == null) {
            return;
        }
        if (this.network != 0) {
            nets.stationOff(this.network, this.worldPosition);
        }
        this.network = id;
        nets.stationOn(id, this.worldPosition);
        setChanged();
        summary(level, true);
    }

    // --- orders from its screen ------------------------------------------------------------------------------------------
    private boolean ready() {
        return isFormed() && this.powered && !isEmpDisabled();
    }

    public void aim(ServerLevel level, List<BlockPos> picked, int x, int z, LaunchNetwork.Pattern pattern, int spacing) {
        if (ready() && network(level) != null) {
            LaunchNetwork.of(level).aim(level, this.network, picked, x, z, pattern, spacing);
        }
    }

    public void arm(ServerLevel level, List<BlockPos> picked, boolean on) {
        if (ready() && network(level) != null && LaunchNetwork.of(level).arm(level, this.network, picked, on) > 0) {
            level.playSound(null, this.worldPosition, GBSounds.STATION_KEY.get(), SoundSource.BLOCKS, 1f, on ? 1f : 0.9f);
        }
    }

    public int launch(ServerLevel level, boolean together, @Nullable ServerPlayer by) {
        if (!ready() || network(level) == null) {
            return 0;
        }
        int n = LaunchNetwork.of(level).launch(level, this.network, together, by);
        if (n > 0) {
            level.playSound(null, this.worldPosition, GBSounds.STATION_LAUNCH.get(), SoundSource.BLOCKS, 1.4f, 1f);
        }
        return n;
    }

    public void unlink(ServerLevel level, BlockPos member) {
        LaunchNetwork.Network n = ready() ? network(level) : null;
        if (n != null && n.member(member) != null) {
            LaunchNetwork.of(level).unlink(level, member);
        }
    }

    public void newCodes(ServerLevel level) {
        if (ready() && network(level) != null) {
            LaunchNetwork.of(level).newCodes(level, this.network);
            summary(level, true);
        }
    }

    /** A report from its network: the teletype prints it (not more than once in {@value #PRINT_GAP} ticks). */
    void heard(ServerLevel level, LaunchNetwork.EventKind kind) {
        if (!ready() || level.getGameTime() - this.lastPrint < PRINT_GAP) {
            return;
        }
        this.lastPrint = level.getGameTime();
        level.playSound(null, this.worldPosition, GBSounds.STATION_TELETYPE.get(), SoundSource.BLOCKS, 0.9f,
                kind == LaunchNetwork.EventKind.LAUNCHED ? 1f : 0.94f);
    }

    @Override
    public void addViewer(ServerPlayer player) {
        this.viewers.add(player.getUUID());
        sendScope((ServerLevel) player.level(), player);
    }

    @Override
    public void removeViewer(ServerPlayer player) {
        this.viewers.remove(player.getUUID());
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GBLaunch.STATION;
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

    /** Broken up (not unloaded): it leaves its network (an empty network with no station left goes with it). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            if (state.hasProperty(MultiblockPartBlock.FORMED) && state.getValue(MultiblockPartBlock.FORMED)) {
                MultiblockController.setPartsFormed(server, this, false);
            }
            if (this.network != 0) {
                LaunchNetwork.of(server).stationOff(this.network, this.worldPosition);
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel server && this.network != 0) {
            LaunchNetwork.of(server).stationOn(this.network, this.worldPosition);
        }
    }

    // --- ticking ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        Launches.trackMapper(level, this.worldPosition);
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            this.powered = false;
            this.surveyed = false;
        } else {
            network(level);
            if (!this.surveyed) {
                this.surveyed = true;
                TerrainSurvey.of(level).surveyAround(level, this.worldPosition, 16);
            }
            this.powered = this.energy.consume(POWER_USE);
            this.status = this.powered ? MachineStatus.RUNNING : MachineStatus.NO_POWER;
        }
        if (!this.viewers.isEmpty() && level.getGameTime() % SCOPE_INTERVAL == 0) {
            for (UUID id : List.copyOf(this.viewers)) {
                if (level.getPlayerByUUID(id) instanceof ServerPlayer p && p.position().distanceTo(Vec3.atCenterOf(this.worldPosition)) < 16) {
                    sendScope(level, p);
                } else {
                    this.viewers.remove(id);
                }
            }
        }
        if ((level.getGameTime() + 3) % SUMMARY_INTERVAL == 0) {
            summary(level, false);
        }
        return this.powered;
    }

    /** In an EMP field it goes dark: no power, so no orders go out, and its screens go blank. */
    @Override
    protected void onEmp(ServerLevel level) {
        this.powered = false;
        if (level.getGameTime() % SCOPE_INTERVAL == 0) {
            for (UUID id : List.copyOf(this.viewers)) {
                if (level.getPlayerByUUID(id) instanceof ServerPlayer p) {
                    sendScope(level, p);
                }
            }
            summary(level, false);
        }
    }

    // --- its screen ---------------------------------------------------------------------------------------------------------
    /** Ticks until each member's soonest missile in the air comes down (by flight id), from the flights in the air. */
    private static Map<UUID, Flights.Flight> flying(ServerLevel level) {
        Map<UUID, Flights.Flight> out = new HashMap<>();
        for (Flights.Flight f : Flights.of(level).all()) {
            out.put(f.id(), f);
        }
        return out;
    }

    private void sendScope(ServerLevel level, ServerPlayer player) {
        LaunchNetwork.Network n = isFormed() ? network(level) : null;
        long now = level.getGameTime();
        List<StationPayloads.MemberView> members = new ArrayList<>();
        List<StationPayloads.ContactView> contacts = new ArrayList<>();
        List<LaunchNetwork.Event> log = List.of();
        String code = "";
        if (n != null) {
            code = n.code();
            log = n.log();
            Map<UUID, Flights.Flight> flying = flying(level);
            Set<UUID> shown = new HashSet<>();
            for (LaunchNetwork.Member m : n.members()) {
                int soonest = -1;
                for (UUID id : m.flights()) {
                    Flights.Flight f = flying.get(id);
                    if (f != null) {
                        soonest = soonest < 0 ? f.ticksToImpact() : Math.min(soonest, f.ticksToImpact());
                        if (this.powered && shown.add(id)) {
                            contacts.add(contact(f, true));
                        }
                    }
                }
                members.add(StationPayloads.MemberView.of(m, now, soonest));
            }
            // What its working radars track (anyone's missiles).
            if (this.powered) {
                for (LaunchNetwork.Member m : n.members()) {
                    if (m.role != LaunchNetwork.Role.RADAR || m.lost() || !m.inContact(now) || !m.report().working()) {
                        continue;
                    }
                    for (Flights.Flight f : flying.values()) {
                        Vec3 p = f.position();
                        if (Math.abs(p.x - m.pos.getX() - 0.5) <= RadarArrayBlockEntity.RANGE && Math.abs(p.z - m.pos.getZ() - 0.5) <= RadarArrayBlockEntity.RANGE
                                && shown.add(f.id())) {
                            contacts.add(contact(f, false));
                        }
                    }
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new StationPayloads.Scope(this.worldPosition, isFormed(), this.powered && !isEmpDisabled(),
                (int) ((long) this.energy.stored() * 100 / Math.max(1, this.energy.capacity())), code, now, members, contacts, log));
    }

    private static StationPayloads.ContactView contact(Flights.Flight f, boolean own) {
        Vec3 p = f.position();
        return new StationPayloads.ContactView(f.trackId(), (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z), f.heading(),
                (int) Math.floor(f.target().x), (int) Math.floor(f.target().z), f.ticksToImpact(), own);
    }

    // --- what the model shows ---------------------------------------------------------------------------------------------
    /** Works out what its big screen and status board show, and sends it to clients if it changed (or {@code force}). */
    private void summary(ServerLevel level, boolean force) {
        boolean on = isFormed() && this.powered && !isEmpDisabled();
        LaunchNetwork.Network n = isFormed() ? network(level) : null;
        String code = n == null ? "" : n.code();
        int linked = 0, armed = 0, flying = 0, count = -1, impact = -1;
        byte[] lamps = new byte[LAMPS];
        if (n != null) {
            long now = level.getGameTime();
            Map<UUID, Flights.Flight> air = null;
            int i = 0;
            for (LaunchNetwork.Member m : n.members()) {
                if (m.role != LaunchNetwork.Role.LAUNCHER) {
                    continue;
                }
                linked++;
                LaunchNetwork.Report r = m.report();
                boolean counting = r.readiness() == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal() && r.countdown() >= 0;
                if (m.willBeArmed()) {
                    armed++;
                }
                if (counting && m.inContact(now)) {
                    count = count < 0 ? r.countdown() : Math.min(count, r.countdown());
                }
                boolean inAir = false;
                if (!m.flights().isEmpty()) {
                    if (air == null) {
                        air = flying(level);
                    }
                    for (UUID id : m.flights()) {
                        Flights.Flight f = air.get(id);
                        if (f != null) {
                            flying++;
                            inAir = true;
                            impact = impact < 0 ? f.ticksToImpact() : Math.min(impact, f.ticksToImpact());
                        }
                    }
                }
                if (i < LAMPS) {
                    Lamp lamp = m.lost() ? Lamp.FAULT
                            : counting || inAir || m.orders().launchAt() >= 0 ? Lamp.BUSY
                            : m.willBeArmed() ? Lamp.ARMED
                            : r.readiness() == LaunchSiloBlockEntity.Readiness.READY.ordinal() ? Lamp.READY
                            : r.readiness() == LaunchSiloBlockEntity.Readiness.EMP.ordinal() || r.readiness() == LaunchSiloBlockEntity.Readiness.BLOCKED.ordinal()
                                    ? Lamp.FAULT : Lamp.OFF;
                    lamps[i] = (byte) lamp.ordinal();
                }
                i++;
            }
        }
        boolean changed = force || on != this.shownPowered || !code.equals(this.shownCode) || linked != this.linked || armed != this.armedCount
                || flying != this.flying || count != this.soonestCount || impact != this.soonestImpact || !java.util.Arrays.equals(lamps, this.lamps);
        if (!changed) {
            return;
        }
        this.shownPowered = on;
        this.shownCode = code;
        this.linked = linked;
        this.armedCount = armed;
        this.flying = flying;
        this.soonestCount = count;
        this.soonestImpact = impact;
        this.lamps = lamps;
        level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Client: what its big screen shows. */
    public String shownCode() {
        return this.shownCode;
    }

    public boolean shownPowered() {
        return this.shownPowered;
    }

    public int linked() {
        return this.linked;
    }

    public int armedCount() {
        return this.armedCount;
    }

    public int flying() {
        return this.flying;
    }

    /** Ticks to the soonest launch (-1: none counting), and to the soonest impact (-1: nothing in the air), when synced. */
    public int soonestCount() {
        return this.soonestCount;
    }

    public int soonestImpact() {
        return this.soonestImpact;
    }

    public Lamp lamp(int i) {
        return i < 0 || i >= this.lamps.length ? Lamp.OFF : Lamp.values()[Math.clamp(this.lamps[i], 0, Lamp.values().length - 1)];
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        tag.putString("shown_code", this.shownCode);
        tag.putBoolean("shown_powered", this.shownPowered);
        tag.putInt("linked", this.linked);
        tag.putInt("armed_count", this.armedCount);
        tag.putInt("flying", this.flying);
        tag.putInt("soonest_count", this.soonestCount);
        tag.putInt("soonest_impact", this.soonestImpact);
        int[] lamps = new int[this.lamps.length];
        for (int i = 0; i < lamps.length; i++) {
            lamps[i] = this.lamps[i];
        }
        tag.putIntArray("lamps", lamps);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;                                   // its screen is its own (LaunchStationBlock opens it)
    }

    // --- model ------------------------------------------------------------------------------------------------------------
    /** The status board's lamps: one bone for each colour of each lamp (tools/assets/launch_station.py). */
    private static final String[][] LAMP_BONES = new String[LAMPS][];
    private static final String[] LAMP_STATES = {"ready", "armed", "busy", "fault"};

    static {
        for (int i = 0; i < LAMPS; i++) {
            LAMP_BONES[i] = new String[LAMP_STATES.length];
            for (int s = 0; s < LAMP_STATES.length; s++) {
                LAMP_BONES[i][s] = "lamp" + i + "_" + LAMP_STATES[s];
            }
        }
    }

    /**
     * Unformed, nothing (the controller's block shows); dark, its screens, lamps and beacon off; otherwise each lamp in its
     * colour (counting down or in flight: flashing amber), the beacon blinking.
     */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        long now = this.level == null ? 0 : this.level.getGameTime();
        Set<String> hidden = new HashSet<>();
        boolean on = this.shownPowered;
        if (!on) {
            hidden.add("screens");
        }
        if (!on || now % 40 >= 6) {
            hidden.add("beacon");
        }
        for (int i = 0; i < LAMPS; i++) {
            Lamp lamp = on ? lamp(i) : Lamp.OFF;
            int shown = switch (lamp) {
                case READY -> 0;
                case ARMED -> 1;
                case BUSY -> now % 12 < 7 ? 2 : -1;
                case FAULT -> now % 30 < 20 ? 3 : -1;
                case OFF -> -1;
            };
            for (int s = 0; s < LAMP_STATES.length; s++) {
                if (s != shown) {
                    hidden.add(LAMP_BONES[i][s]);
                }
            }
        }
        return hidden;
    }

    /** While anything is armed: both launch keys turned and the LAUNCH button's cover lifted. */
    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        boolean armed = this.shownPowered && this.armedCount > 0;
        if (!armed) {
            return Map.of();
        }
        float key = (float) Math.toRadians(90);
        return Map.of("key_l", new float[] {0, 0, key}, "key_r", new float[] {0, 0, key}, "cover", new float[] {(float) Math.toRadians(-105), 0, 0});
    }

    /** Nothing animates: what moves is set above. */
    @Override
    public void registerControllers(com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("network", this.network);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.network = input.getLongOr("network", 0L);
        // What the model shows comes with the update tag.
        this.shownCode = input.getStringOr("shown_code", this.shownCode);
        this.shownPowered = input.getBooleanOr("shown_powered", this.shownPowered);
        this.linked = input.getIntOr("linked", this.linked);
        this.armedCount = input.getIntOr("armed_count", this.armedCount);
        this.flying = input.getIntOr("flying", this.flying);
        this.soonestCount = input.getIntOr("soonest_count", this.soonestCount);
        this.soonestImpact = input.getIntOr("soonest_impact", this.soonestImpact);
        input.getIntArray("lamps").ifPresent(a -> {
            byte[] b = new byte[LAMPS];
            for (int i = 0; i < Math.min(a.length, LAMPS); i++) {
                b[i] = (byte) a[i];
            }
            this.lamps = b;
        });
    }
}
