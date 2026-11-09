package dev.groundburst.content.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.content.alarm.Alarms;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.NetworkMember;
import dev.groundburst.content.launch.ScreenWatched;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
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

/**
 * The Dead Hand (DESIGN.md §17.17), after the Soviet Perimeter system: the command module of an automatic retaliation
 * system on a launch network. Loaded with a Command Rocket and armed at its console (both keys: two clicks of ARM), it
 * watches its radius for a strike (a blast of its set magnitude or more that its own network did not fire); then it goes
 * into alert (a countdown, its alarm channel raised, the network's log told); HOLD, or a redstone signal into it, stands it
 * down. At zero, if its network's command is silent (no Launch Control Station of its network working; or on any strike,
 * as set), it fires: its tube's lid opens, the command rocket climbs out (a missile with no warhead), and as it transmits
 * every launcher of the network that can fire is ordered to: at its own aim (the war plan) or at where the strike was
 * launched from (return fire). Then it is spent until a new rocket is loaded. Hardened: an EMP does not stop it. 80 FE a
 * tick.
 */
public class DeadHandBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, ScreenWatched, NetworkMember {
    public static final int ENERGY_CAPACITY = 80_000, POWER_USE = 80;
    /** Its settings: the radius it watches (blocks), the strike it answers (magnitude), its countdown (ticks). */
    public static final int[] RADII = {128, 256, 512, 1024, 2048};
    public static final float[] THRESHOLDS = {3f, 4f, 5f};
    public static final int[] COUNTDOWNS = {600, 1200, 2400};
    public static final int DEFAULT_RADIUS = 2, DEFAULT_THRESHOLD = 1;
    /** The second key must follow the first this soon (ticks); the lid's swing; the rocket transmits this long after it lifts off. */
    public static final int CONFIRM = 60, LID_TICKS = 40, TRANSMIT = 60;
    /** The command rocket's flight: this far behind the module (blocks), out of its tube two blocks deep. */
    static final double ROCKET_RANGE = 120, TUBE_DEPTH = 2;
    private static final int RECHECK_INTERVAL = 40, SCOPE_INTERVAL = 10, LOG = 8;

    /** What it is doing. */
    public enum State { SAFE, WATCHING, ALERT, FIRING, SPENT }

    /** A line of its own log (its console shows them): what happened, and its figures. */
    public enum Line { ARMED, SAFE, ALERT, STOOD_DOWN, ANSWERED, FIRED, DRILL, DRILL_ENDED, LOADED, NO_NETWORK }

    /** One line: when, what, a magnitude (tenths) and a distance or count. */
    public record Entry(long time, Line line, int a, int b) {
        static final com.mojang.serialization.Codec<Entry> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                com.mojang.serialization.Codec.LONG.fieldOf("time").forGetter(Entry::time),
                com.mojang.serialization.Codec.STRING.xmap(Line::valueOf, Line::name).fieldOf("line").forGetter(Entry::line),
                com.mojang.serialization.Codec.INT.fieldOf("a").forGetter(Entry::a), com.mojang.serialization.Codec.INT.fieldOf("b").forGetter(Entry::b))
                .apply(i, Entry::new));
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> viewers = new HashSet<>();
    private final List<Entry> log = new ArrayList<>();
    private State state = State.SAFE;
    private boolean powered, rocket, drill;
    private int radius = DEFAULT_RADIUS, threshold = DEFAULT_THRESHOLD, countdown, command, targets, channel;
    /** The last seismic event it took (-1: not yet). */
    private long lastEvent = -1;
    /** In alert: when it fires (game time), the strike's place, magnitude and distance, and where it was fired from. */
    private long alertUntil = -1;
    private int strikeX, strikeZ, strikeDistance;
    private float strikeMagnitude;
    private @Nullable BlockPos origin;
    /** Firing: when the rocket lifts off and when it transmits. */
    private long liftOff = -1, transmitAt = -1;
    /** The first key's turn (game time), waiting for the second. */
    private long armAsked = -1;
    private boolean held;

    public DeadHandBlockEntity(BlockPos pos, BlockState state) {
        super(GBCommand.DEAD_HAND_ENTITY.get(), pos, state, ENERGY_CAPACITY, 2_000, 0, 0, new int[0], new int[0]);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    public State state() {
        return this.state;
    }

    public boolean rocket() {
        return this.rocket;
    }

    public boolean powered() {
        return this.powered;
    }

    public boolean drill() {
        return this.drill;
    }

    public long alertUntil() {
        return this.alertUntil;
    }

    public int radius() {
        return this.radius;
    }

    public int threshold() {
        return this.threshold;
    }

    public int countdownSetting() {
        return this.countdown;
    }

    public int commandSetting() {
        return this.command;
    }

    public int targets() {
        return this.targets;
    }

    public int channel() {
        return this.channel;
    }

    public List<Entry> log() {
        return List.copyOf(this.log);
    }

    /** What a comparator reads: 0 safe, 5 watching, 10 in alert, 15 fired. */
    public int comparator() {
        return switch (this.state) {
            case SAFE -> 0;
            case WATCHING -> 5;
            case ALERT -> 10;
            case FIRING, SPENT -> 15;
        };
    }

    /** Hardened: an EMP field does not stop it. */
    @Override
    protected boolean isEmpDisabled() {
        return false;
    }

    // --- its console's orders -------------------------------------------------------------------------------------------
    public static final int ARM = 0, SAFE = 1, HOLD = 2, DRILL = 3, SET_RADIUS = 4, SET_THRESHOLD = 5, SET_COUNTDOWN = 6, SET_COMMAND = 7,
            SET_TARGETS = 8, SET_CHANNEL = 9;

    /** An order from its console ({@code value}: a setting's new value). */
    public void order(ServerLevel level, int action, int value) {
        long now = level.getGameTime();
        switch (action) {
            case ARM -> {
                if (this.state != State.SAFE || !canArm(level)) {
                    return;
                }
                level.playSound(null, this.worldPosition, GBSounds.STATION_KEY.get(), SoundSource.BLOCKS, 1f, this.armAsked >= 0 ? 0.9f : 1f);
                if (this.armAsked >= 0 && now - this.armAsked <= CONFIRM) {
                    this.armAsked = -1;
                    this.state = State.WATCHING;
                    this.lastEvent = Seismic.of(level).latest();       // watching from now on
                    line(level, Line.ARMED, 0, RADII[this.radius]);
                    note(level, LaunchNetwork.EventKind.DEAD_HAND_ARMED, 0, 0, RADII[this.radius]);
                    changed(level);
                } else {
                    this.armAsked = now;                                // the first key: the second must follow
                }
            }
            case SAFE -> {
                if (this.state == State.WATCHING || this.state == State.ALERT) {
                    level.playSound(null, this.worldPosition, GBSounds.STATION_KEY.get(), SoundSource.BLOCKS, 1f, 0.8f);
                    standDown(level);
                    this.state = State.SAFE;
                    line(level, Line.SAFE, 0, 0);
                    note(level, LaunchNetwork.EventKind.DEAD_HAND_SAFE, 0, 0, 0);
                    changed(level);
                }
            }
            case HOLD -> {
                if (this.state == State.ALERT) {
                    holdNow(level);
                }
            }
            case DRILL -> {
                if (this.state == State.SAFE || this.state == State.WATCHING) {
                    this.drill = true;
                    alert(level, now, null, 0, 0, 0f, 0);
                    line(level, Line.DRILL, 0, COUNTDOWNS[this.countdown] / 20);
                }
            }
            case SET_RADIUS -> this.radius = Math.clamp(value, 0, RADII.length - 1);
            case SET_THRESHOLD -> this.threshold = Math.clamp(value, 0, THRESHOLDS.length - 1);
            case SET_COUNTDOWN -> this.countdown = Math.clamp(value, 0, COUNTDOWNS.length - 1);
            case SET_COMMAND -> this.command = Math.clamp(value, 0, 1);
            case SET_TARGETS -> this.targets = Math.clamp(value, 0, 1);
            case SET_CHANNEL -> {
                int c = Math.clamp(value, 0, Alarms.MAX_CHANNEL);
                if (c != this.channel) {
                    Alarms.lower(level, this.channel, this.worldPosition);
                    this.channel = c;
                }
            }
            default -> { }
        }
        setChanged();
        sendViewers(level);
    }

    /** For GameTests: an alert's countdown run down to its last tick (it ends on the next). */
    public void hurryForTest(ServerLevel level) {
        if (this.state == State.ALERT) {
            this.alertUntil = level.getGameTime() + 1;
        }
    }

    /** Why it cannot be armed now (null: it can). */
    public @Nullable String cannotArm(ServerLevel level) {
        if (!isFormed()) {
            return "formed";
        }
        if (!this.powered) {
            return "power";
        }
        if (!this.rocket) {
            return "rocket";
        }
        if (LaunchNetwork.of(level).networkOf(this.worldPosition) == null) {
            return "network";
        }
        return null;
    }

    private boolean canArm(ServerLevel level) {
        return cannotArm(level) == null;
    }

    /** A Command Rocket used on it: loaded if it has none (true), its lid shut over it. */
    public boolean load(ServerLevel level) {
        if (this.rocket || this.state == State.FIRING) {
            return false;
        }
        this.rocket = true;
        if (this.state == State.SPENT) {
            this.state = State.SAFE;                             // a new rocket: armed again only at the console
        }
        this.liftOff = -1;
        line(level, Line.LOADED, 0, 0);
        level.playSound(null, this.worldPosition, GBSounds.DEAD_HAND_LID.get(), SoundSource.BLOCKS, 0.8f, 1.2f);
        changed(level);
        return true;
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GBCommand.DEAD_HAND;
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
        if (this.level == null || this.level.isClientSide() || isRemoved() || carried()) {
            return;
        }
        boolean complete = definition().check(this.level, this.worldPosition, controllerFacing()).isEmpty();
        if (complete != isFormed()) {
            MultiblockController.setPartsFormed(this.level, this, complete);
            this.level.setBlock(this.worldPosition, getBlockState().setValue(MultiblockPartBlock.FORMED, complete), Block.UPDATE_ALL);
            setChanged();
        }
    }

    /** Broken up: its rocket dropped, its alarm lowered. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            if (state.hasProperty(MultiblockPartBlock.FORMED) && state.getValue(MultiblockPartBlock.FORMED)) {
                MultiblockController.setPartsFormed(server, this, false);
            }
            if (this.rocket) {
                Vec3 at = inWorld(Vec3.atCenterOf(pos));
                Containers.dropItemStack(server, at.x, at.y, at.z, new ItemStack(GBCommand.COMMAND_ROCKET.get()));
                this.rocket = false;
            }
            Alarms.lower(server, this.channel, this.worldPosition);
        }
    }

    // --- ticking ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        long now = level.getGameTime();
        if ((now + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        boolean was = this.powered;
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            this.powered = false;
        } else {
            this.powered = this.energy.consume(POWER_USE);
            this.status = this.powered ? MachineStatus.RUNNING : MachineStatus.NO_POWER;
        }
        if (this.lastEvent < 0) {
            this.lastEvent = Seismic.of(level).latest();
        }
        switch (this.state) {
            case WATCHING -> watch(level, now);
            case ALERT -> alertTick(level, now);
            case FIRING -> firingTick(level, now);
            default -> skip(level, now);
        }
        if (was != this.powered) {
            changed(level);
        }
        LaunchNetwork.of(level).tickMember(level, this);
        if (!this.viewers.isEmpty() && now % SCOPE_INTERVAL == 0) {
            sendViewers(level);
        }
        return this.powered;
    }

    /** Watching: a strike reaching it (its first wave) within its radius, of its magnitude or more, not its own network's. */
    private void watch(ServerLevel level, long now) {
        if (!this.powered) {
            skip(level, now);
            return;
        }
        Vec3 here = inWorld(Vec3.atCenterOf(this.worldPosition));
        LaunchNetwork.Network own = LaunchNetwork.of(level).networkOf(this.worldPosition);
        for (Seismic.Event e : Seismic.of(level).after(this.lastEvent)) {
            if (e.arrival(here) > now) {
                break;
            }
            this.lastEvent = e.id();
            setChanged();
            double d = Math.hypot(e.x() - here.x, e.z() - here.z);
            if (d > RADII[this.radius] || e.magnitude() < THRESHOLDS[this.threshold] || own != null && e.network() == own.id) {
                continue;
            }
            BlockPos from = e.origin().map(BlockPos::containing).orElse(null);
            alert(level, now, from, Mth.floor(e.x()), Mth.floor(e.z()), e.magnitude(), (int) Math.round(d));
            line(level, Line.ALERT, Math.round(e.magnitude() * 10), (int) Math.round(d));
            return;
        }
    }

    /** Takes the events that have reached it without acting on them (not watching). */
    private void skip(ServerLevel level, long now) {
        Vec3 here = inWorld(Vec3.atCenterOf(this.worldPosition));
        for (Seismic.Event e : Seismic.of(level).after(this.lastEvent)) {
            if (e.arrival(here) > now) {
                break;
            }
            this.lastEvent = e.id();
        }
    }

    /** Into alert: the countdown, its alarm, the network told (a drill too, but nothing fires at its end). */
    private void alert(ServerLevel level, long now, @Nullable BlockPos from, int x, int z, float m, int d) {
        this.state = State.ALERT;
        this.alertUntil = now + COUNTDOWNS[this.countdown];
        this.strikeX = x;
        this.strikeZ = z;
        this.strikeMagnitude = m;
        this.strikeDistance = d;
        this.origin = from;
        this.held = false;
        raise(level);
        if (!this.drill) {
            note(level, LaunchNetwork.EventKind.DEAD_HAND_ALERT, x, z, COUNTDOWNS[this.countdown] / 20);
        }
        playSound(level, this.worldPosition, GBSounds.DEAD_HAND_ALERT.get(), SoundSource.BLOCKS, 1.4f, 1f);
        changed(level);
    }

    private void alertTick(ServerLevel level, long now) {
        if ((now + this.worldPosition.hashCode()) % Alarms.REFRESH == 0) {
            raise(level);
        }
        if ((this.alertUntil - now) % 20 == 0) {
            playSound(level, this.worldPosition, GBSounds.DEAD_HAND_ALERT.get(), SoundSource.BLOCKS, 1.1f, this.alertUntil - now <= 200 ? 1.25f : 1f);
        }
        if (now % 5 == 0 && heldByRedstone(level)) {
            holdNow(level);
            return;
        }
        if (now < this.alertUntil) {
            return;
        }
        if (this.drill) {
            standDown(level);
            this.state = this.lastArmed ? State.WATCHING : State.SAFE;
            line(level, Line.DRILL_ENDED, 0, 0);
            changed(level);
            return;
        }
        LaunchNetwork nets = LaunchNetwork.of(level);
        LaunchNetwork.Network n = nets.networkOf(this.worldPosition);
        if (n == null) {
            Alarms.lower(level, this.channel, this.worldPosition);
            this.state = State.WATCHING;
            line(level, Line.NO_NETWORK, 0, 0);
            changed(level);
            return;
        }
        if (this.command == 0 && nets.stationAnswers(level, n.id)) {
            // Its link check: a station of its network answers, so the command lives; it stands down.
            standDown(level);
            this.state = State.WATCHING;
            line(level, Line.ANSWERED, 0, 0);
            note(level, LaunchNetwork.EventKind.DEAD_HAND_STOOD_DOWN, this.strikeX, this.strikeZ, 1);
            changed(level);
            return;
        }
        if (!this.powered || !this.rocket) {
            return;                                              // it waits for power (and its rocket) to fire
        }
        this.state = State.FIRING;
        this.liftOff = now + LID_TICKS;
        playSound(level, this.worldPosition, GBSounds.DEAD_HAND_LID.get(), SoundSource.BLOCKS, 1.6f, 1f);
        changed(level);
    }

    /** Whether it was armed (watching) before a drill took it into alert. */
    private boolean lastArmed;

    /** Firing: its lid opens, the rocket lifts off, and as it transmits the network's launchers are ordered to fire. */
    private void firingTick(ServerLevel level, long now) {
        if (this.liftOff >= 0 && now >= this.liftOff) {
            this.liftOff = -1;
            this.rocket = false;
            launchRocket(level);
            this.transmitAt = now + TRANSMIT;
            changed(level);
        }
        if (this.transmitAt >= 0 && now >= this.transmitAt) {
            this.transmitAt = -1;
            LaunchNetwork nets = LaunchNetwork.of(level);
            LaunchNetwork.Network n = nets.networkOf(this.worldPosition);
            int ordered = n == null ? 0 : nets.retaliate(level, n.id, this.targets == 1 ? this.origin : null);
            Alarms.lower(level, this.channel, this.worldPosition);
            this.state = State.SPENT;
            line(level, Line.FIRED, 0, ordered);
            note(level, LaunchNetwork.EventKind.DEAD_HAND_FIRED, this.strikeX, this.strikeZ, ordered);
            changed(level);
        }
    }

    /** The command rocket: out of its tube and away behind the module, a missile with no warhead (it comes down harmlessly). */
    private void launchRocket(ServerLevel level) {
        List<BlockPos> tube = definition().positionsOf('K', this.worldPosition, controllerFacing());
        BlockPos foot = tube.stream().min(java.util.Comparator.comparingInt(BlockPos::getY)).orElse(this.worldPosition);
        Vec3 pad = inWorld(Vec3.atBottomCenterOf(foot));
        Direction back = controllerFacing().getOpposite();
        Vec3 target = pad.add(back.getStepX() * ROCKET_RANGE, 0, back.getStepZ() * ROCKET_RANGE);
        Flights.Flight f = Flights.launch(level, pad, target, ItemStack.EMPTY, SizeClass.S, true, null, TUBE_DEPTH);
        LaunchNetwork.launched(level, this.worldPosition, f);
    }

    private boolean heldByRedstone(ServerLevel level) {
        if (level.hasNeighborSignal(this.worldPosition)) {
            return true;
        }
        for (BlockPos p : definition().partPositions(this.worldPosition, controllerFacing())) {
            if (level.hasNeighborSignal(p)) {
                return true;
            }
        }
        return false;
    }

    /** HOLD: the countdown stopped, back to watching (a drill ended). */
    private void holdNow(ServerLevel level) {
        boolean wasDrill = this.drill;
        standDown(level);
        this.state = wasDrill && !this.lastArmed ? State.SAFE : State.WATCHING;
        line(level, wasDrill ? Line.DRILL_ENDED : Line.STOOD_DOWN, 0, 0);
        if (!wasDrill) {
            note(level, LaunchNetwork.EventKind.DEAD_HAND_STOOD_DOWN, this.strikeX, this.strikeZ, 0);
        }
        changed(level);
    }

    /** Out of alert: the countdown and the alarm stopped, and the strikes it has had meanwhile taken as answered. */
    private void standDown(ServerLevel level) {
        this.alertUntil = -1;
        this.drill = false;
        Alarms.lower(level, this.channel, this.worldPosition);
        skip(level, level.getGameTime());
    }

    /** Its alarm in alert: a Blast alert for the strike (a drill: an Alarm). */
    private void raise(ServerLevel level) {
        Alarms.raise(level, this.channel, this.worldPosition, this.drill ? Alarms.Alert.of(Alarms.Kind.ALARM)
                : new Alarms.Alert(Alarms.Kind.BLAST, this.strikeDistance, -1, this.strikeMagnitude));
    }

    private void line(ServerLevel level, Line line, int a, int b) {
        this.log.add(new Entry(level.getGameTime(), line, a, b));
        while (this.log.size() > LOG) {
            this.log.removeFirst();
        }
        setChanged();
    }

    private void note(ServerLevel level, LaunchNetwork.EventKind kind, int x, int z, int detail) {
        LaunchNetwork.of(level).note(level, this.worldPosition, kind, x, z, detail);
    }

    /** Its state changed: saved, clients told (the model), the network told, comparators told, the console refreshed. */
    private void changed(ServerLevel level) {
        if (this.state == State.WATCHING) {
            this.lastArmed = true;
        } else if (this.state == State.SAFE || this.state == State.SPENT) {
            this.lastArmed = false;
        }
        setChanged();
        level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        level.updateNeighbourForOutputSignal(this.worldPosition, getBlockState().getBlock());
        LaunchNetwork.of(level).reportNow(level, this);
        sendViewers(level);
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel level) {
            Alarms.lower(level, this.channel, this.worldPosition);
        }
        super.setRemoved();
    }

    // --- on a network ------------------------------------------------------------------------------------------------------
    @Override
    public LaunchNetwork.Role networkRole() {
        return LaunchNetwork.Role.DEAD_HAND;
    }

    /**
     * Its report: incomplete, no power, no rocket (READY only with one), counting in alert (its countdown); armed while
     * watching, in alert or firing; the strike's place in alert; its radius; its rocket (as what it holds).
     */
    @Override
    public LaunchNetwork.Report networkReport(ServerLevel level) {
        long now = level.getGameTime();
        LaunchSiloBlockEntity.Readiness r = !isFormed() ? LaunchSiloBlockEntity.Readiness.NOT_FORMED
                : !this.powered ? LaunchSiloBlockEntity.Readiness.NO_POWER
                : this.state == State.ALERT || this.state == State.FIRING ? LaunchSiloBlockEntity.Readiness.COUNTING
                : !this.rocket ? LaunchSiloBlockEntity.Readiness.NO_MISSILE : LaunchSiloBlockEntity.Readiness.READY;
        int count = this.state == State.ALERT ? (int) Math.max(0, this.alertUntil - now) : this.state == State.FIRING ? 0 : -1;
        boolean armed = this.state == State.WATCHING || this.state == State.ALERT && !this.drill || this.state == State.FIRING;
        Optional<BlockPos> strike = this.state == State.ALERT && !this.drill ? Optional.of(new BlockPos(this.strikeX, this.worldPosition.getY(), this.strikeZ))
                : Optional.empty();
        return LaunchNetwork.Report.of(r.ordinal(), count, armed, strike, RADII[this.radius],
                this.rocket ? new ItemStack(GBCommand.COMMAND_ROCKET.get()) : ItemStack.EMPTY, isFormed() && this.powered);
    }

    // --- its console --------------------------------------------------------------------------------------------------------
    @Override
    public void addViewer(ServerPlayer player) {
        this.viewers.add(player.getUUID());
        send((ServerLevel) player.level(), player);
    }

    @Override
    public void removeViewer(ServerPlayer player) {
        this.viewers.remove(player.getUUID());
    }

    private void sendViewers(ServerLevel level) {
        for (UUID id : List.copyOf(this.viewers)) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer p && p.position().distanceTo(inWorld(Vec3.atCenterOf(this.worldPosition))) < 16) {
                send(level, p);
            } else {
                this.viewers.remove(id);
            }
        }
    }

    private void send(ServerLevel level, ServerPlayer player) {
        LaunchNetwork.Network n = LaunchNetwork.of(level).networkOf(this.worldPosition);
        String why = this.state == State.SAFE ? cannotArm(level) : null;
        long now = level.getGameTime();
        PacketDistributor.sendToPlayer(player, new DeadHandPayloads.Scope(this.worldPosition, now, isFormed(), this.powered, this.state.ordinal(),
                this.rocket, this.drill, this.alertUntil, this.strikeX, this.strikeZ, this.strikeMagnitude, this.strikeDistance,
                this.origin != null, n == null ? "" : n.code(), why == null ? "" : why, this.armAsked >= 0 && now - this.armAsked <= CONFIRM,
                this.radius, this.threshold, this.countdown, this.command, this.targets, this.channel, List.copyOf(this.log)));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;                                       // its console is its own (DeadHandBlock opens it)
    }

    // --- the model (tools/assets/dead_hand.py) ----------------------------------------------------------------------------
    /** The tube's lid open, radians. */
    static final float LID_OPEN = (float) Math.toRadians(112);

    /** Unformed, nothing; its lamps by its state; the rocket's nose in its tube while it has one; the mast's light blinking. */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        Set<String> hidden = new HashSet<>();
        long now = this.level == null ? 0 : this.level.getGameTime();
        boolean on = this.powered;
        if (!on || this.state != State.SAFE) {
            hidden.add("lamp_safe");
        }
        if (!on || this.state != State.WATCHING) {
            hidden.add("lamp_watch");
        }
        if (!on || this.state != State.ALERT || now % 10 >= 6) {
            hidden.add("lamp_alert");
        }
        if (this.state != State.FIRING && this.state != State.SPENT) {
            hidden.add("lamp_fired");
        }
        if (!on || now % 40 >= 4) {
            hidden.add("mast_light");
        }
        if (!this.rocket) {
            hidden.add("rocket");
        }
        return hidden;
    }

    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        if (this.level == null) {
            return Map.of();
        }
        double now = this.level.getGameTime() + partialTick;
        float lid = 0;
        if (this.state == State.FIRING) {
            double u = this.liftOff >= 0 ? Mth.clamp(1 - (this.liftOff - now) / LID_TICKS, 0, 1) : 1;
            lid = (float) (LID_OPEN * (u * u * (3 - 2 * u)));
        } else if (this.state == State.SPENT) {
            lid = LID_OPEN;
        }
        boolean armed = this.state == State.WATCHING || this.state == State.ALERT || this.state == State.FIRING;
        float key = armed ? (float) Math.toRadians(90) : 0;
        return Map.of("lid", new float[] {lid, 0, 0}, "key_l", new float[] {0, 0, key}, "key_r", new float[] {0, 0, key});
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

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
        output.putString("state", this.state.name());
        output.putBoolean("powered", this.powered);
        output.putBoolean("rocket", this.rocket);
        output.putBoolean("drill", this.drill);
        output.putBoolean("last_armed", this.lastArmed);
        output.putInt("radius", this.radius);
        output.putInt("threshold", this.threshold);
        output.putInt("countdown", this.countdown);
        output.putInt("command", this.command);
        output.putInt("targets", this.targets);
        output.putInt("channel", this.channel);
        output.putLong("last_event", this.lastEvent);
        output.putLong("alert_until", this.alertUntil);
        output.putInt("strike_x", this.strikeX);
        output.putInt("strike_z", this.strikeZ);
        output.putInt("strike_distance", this.strikeDistance);
        output.putFloat("strike_magnitude", this.strikeMagnitude);
        if (this.origin != null) {
            output.putLong("origin", this.origin.asLong());
        }
        output.putLong("lift_off", this.liftOff);
        output.putLong("transmit_at", this.transmitAt);
        output.store("log", Entry.CODEC.listOf(), List.copyOf(this.log));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            this.state = State.valueOf(input.getStringOr("state", State.SAFE.name()));
        } catch (IllegalArgumentException e) {
            this.state = State.SAFE;
        }
        this.powered = input.getBooleanOr("powered", false);
        this.rocket = input.getBooleanOr("rocket", false);
        this.drill = input.getBooleanOr("drill", false);
        this.lastArmed = input.getBooleanOr("last_armed", false);
        this.radius = Math.clamp(input.getIntOr("radius", DEFAULT_RADIUS), 0, RADII.length - 1);
        this.threshold = Math.clamp(input.getIntOr("threshold", DEFAULT_THRESHOLD), 0, THRESHOLDS.length - 1);
        this.countdown = Math.clamp(input.getIntOr("countdown", 0), 0, COUNTDOWNS.length - 1);
        this.command = Math.clamp(input.getIntOr("command", 0), 0, 1);
        this.targets = Math.clamp(input.getIntOr("targets", 0), 0, 1);
        this.channel = input.getIntOr("channel", 0);
        this.lastEvent = input.getLongOr("last_event", -1);
        this.alertUntil = input.getLongOr("alert_until", -1);
        this.strikeX = input.getIntOr("strike_x", 0);
        this.strikeZ = input.getIntOr("strike_z", 0);
        this.strikeDistance = input.getIntOr("strike_distance", 0);
        this.strikeMagnitude = input.getFloatOr("strike_magnitude", 0);
        long o = input.getLongOr("origin", Long.MIN_VALUE);
        this.origin = o == Long.MIN_VALUE ? null : BlockPos.of(o);
        this.liftOff = input.getLongOr("lift_off", -1);
        this.transmitAt = input.getLongOr("transmit_at", -1);
        this.log.clear();
        try {
            input.read("log", Entry.CODEC.listOf()).ifPresent(this.log::addAll);
        } catch (IllegalArgumentException ignored) {
            // a line from a later version: the log starts again
        }
    }
}
