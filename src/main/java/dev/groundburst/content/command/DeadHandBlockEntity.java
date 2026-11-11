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
import net.minecraft.core.particles.ParticleTypes;
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
 * The Dead Hand (DESIGN.md §17.17, §17.19), after the Soviet Perimeter system: the command module of an automatic
 * retaliation system on a launch network. Loaded with a Command Rocket and armed at its console (both keys: two clicks of
 * ARM), it watches its radius for a strike (a blast of its set magnitude or more that its own network did not fire); then
 * it goes into alert: a countdown, its alarm channel raised, the network's log told, and every launcher of the network
 * readied to stand by (aimed, armed, its countdown run to its last tick by zero). HOLD, or a redstone signal into it,
 * stands it and them down. Its tube's lid opens in the countdown's last two seconds; at zero, if its network's command is
 * silent (no Launch Control Station of its network working; or on any strike, as set), its command rocket leaves the tube
 * and flies a course over the network's launchers ({@link CommandRocket}), each firing as its signal reaches it (the
 * network listens: {@link LaunchNetwork#commandRocketAway}). Then it is spent until a new rocket is loaded. Hardened: an
 * EMP does not stop it. 80 FE a tick.
 */
public class DeadHandBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, ScreenWatched, NetworkMember {
    public static final int ENERGY_CAPACITY = 80_000, POWER_USE = 80;
    /** Its settings: the radius it watches (blocks), the strike it answers (magnitude), its countdown (ticks). */
    public static final int[] RADII = {128, 256, 512, 1024, 2048};
    public static final float[] THRESHOLDS = {3f, 4f, 5f};
    public static final int[] COUNTDOWNS = {600, 1200, 2400};
    public static final int DEFAULT_RADIUS = 2, DEFAULT_THRESHOLD = 1;
    /** The second key must follow the first this soon (ticks); the lid's swing (it starts this long before zero). */
    public static final int CONFIRM = 60, LID_TICKS = 40;
    /** Its console's plan of the rocket's course is worked out again this often while it is watched, ticks. */
    private static final int COURSE_INTERVAL = 40;
    private static final int RECHECK_INTERVAL = 40, SCOPE_INTERVAL = 10, LOG = 8;

    /** What it is doing: FIRING while its command rocket flies, SPENT once it is down. */
    public enum State { SAFE, WATCHING, ALERT, FIRING, SPENT }

    /** A line of its own log (its console shows them): what happened, and its figures (new kinds go at the end). */
    public enum Line { ARMED, SAFE, ALERT, STOOD_DOWN, ANSWERED, FIRED, DRILL, DRILL_ENDED, LOADED, NO_NETWORK, ROCKET_DOWN }

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
    /** Firing: its command rocket's flight, and how many launchers have taken the order from it. */
    private @Nullable UUID rocketId;
    private int sent;
    /** Its lid swinging shut (game time it started, -1: not), from how far open (radians). */
    private long lidShutAt = -1;
    private float lidShutFrom;
    /** The first key's turn (game time), waiting for the second. */
    private long armAsked = -1;
    /** Its console's plan of the rocket's course (not saved), and when it was worked out. */
    private CommandRocket.@Nullable Course course;
    private long courseAt = -1;

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

    /** Its command rocket's flight while it flies (null: none). */
    public @Nullable UUID rocketFlight() {
        return this.rocketId;
    }

    /** How many launchers have taken the order from its command rocket. */
    public int sent() {
        return this.sent;
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
            shutLid(level.getGameTime(), LID_OPEN);
        }
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

    /** Broken up: its rocket dropped, its alarm lowered, the launchers standing by for it stood down. */
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
            standDownLaunchers(server);
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

    /**
     * Into alert: the countdown, its alarm, the network told and its launchers readied to stand by for the rocket (a drill
     * too, but it readies nothing and nothing fires at its end).
     */
    private void alert(ServerLevel level, long now, @Nullable BlockPos from, int x, int z, float m, int d) {
        this.state = State.ALERT;
        this.alertUntil = now + COUNTDOWNS[this.countdown];
        this.strikeX = x;
        this.strikeZ = z;
        this.strikeMagnitude = m;
        this.strikeDistance = d;
        this.origin = from;
        raise(level);
        if (!this.drill) {
            LaunchNetwork nets = LaunchNetwork.of(level);
            LaunchNetwork.Network n = nets.networkOf(this.worldPosition);
            if (n != null) {
                nets.standBy(level, n.id, this.worldPosition, this.targets == 1 ? this.origin : null, this.alertUntil);
            }
            note(level, LaunchNetwork.EventKind.DEAD_HAND_ALERT, x, z, COUNTDOWNS[this.countdown] / 20);
        }
        playSound(level, this.worldPosition, GBSounds.DEAD_HAND_ALERT.get(), SoundSource.BLOCKS, 1.4f, 1f);
        changed(level);
    }

    private void alertTick(ServerLevel level, long now) {
        if ((now + this.worldPosition.hashCode()) % Alarms.REFRESH == 0) {
            raise(level);
        }
        long left = this.alertUntil - now;
        if (left % 20 == 0) {
            playSound(level, this.worldPosition, GBSounds.DEAD_HAND_ALERT.get(), SoundSource.BLOCKS, 1.1f, left <= 200 ? 1.25f : 1f);
        }
        if (now % 5 == 0 && heldByRedstone(level)) {
            holdNow(level);
            return;
        }
        if (left == LID_TICKS && !this.drill && this.rocket) {
            // The lid swings open over the countdown's last two seconds: the rocket leaves at zero.
            playSound(level, this.worldPosition, GBSounds.DEAD_HAND_LID.get(), SoundSource.BLOCKS, 1.6f, 1f);
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
            standDown(level);
            this.state = State.WATCHING;
            line(level, Line.NO_NETWORK, 0, 0);
            changed(level);
            return;
        }
        if (this.command == 0 && nets.stationAnswers(level, n.id)) {
            // Its link check: a station of its network answers, so the command lives; it stands down, its launchers too.
            standDown(level);
            this.state = State.WATCHING;
            line(level, Line.ANSWERED, 0, 0);
            note(level, LaunchNetwork.EventKind.DEAD_HAND_STOOD_DOWN, this.strikeX, this.strikeZ, 1);
            changed(level);
            return;
        }
        if (!this.powered || !this.rocket) {
            return;                                              // it waits for power (its launchers stand by)
        }
        fire(level, n);
    }

    /** Whether it was armed (watching) before a drill took it into alert. */
    private boolean lastArmed;

    /** Zero: its command rocket leaves the tube, transmitting; the launchers by it take the order at once. */
    private void fire(ServerLevel level, LaunchNetwork.Network n) {
        LaunchNetwork nets = LaunchNetwork.of(level);
        int standing = nets.standingFor(n.id, this.worldPosition);
        this.state = State.FIRING;
        this.rocket = false;
        this.sent = 0;
        Flights.Flight f = launchRocket(level, n);
        this.rocketId = f.id();
        Alarms.lower(level, this.channel, this.worldPosition);
        line(level, Line.FIRED, 0, standing);
        note(level, LaunchNetwork.EventKind.DEAD_HAND_FIRED, this.strikeX, this.strikeZ, standing);
        nets.commandRocketAway(level, this.worldPosition, f);
        this.sent = nets.reachedBy(f.id());
        changed(level);
    }

    /** Firing: its rocket flies, the network following it (what it reaches fires); once it is down, it is spent. */
    private void firingTick(ServerLevel level, long now) {
        if (now % SCOPE_INTERVAL != 0) {
            return;
        }
        UUID id = this.rocketId;
        boolean flying = id != null && Flights.of(level).all().stream().anyMatch(f -> f.id().equals(id));
        if (flying) {
            int s = Math.max(this.sent, LaunchNetwork.of(level).reachedBy(id));
            if (s != this.sent) {
                this.sent = s;
                setChanged();
            }
            return;
        }
        this.rocketId = null;
        this.state = State.SPENT;
        line(level, Line.ROCKET_DOWN, 0, this.sent);
        changed(level);
    }

    /** The tube's foot in the world, where the rocket's tail stands. */
    private Vec3 pad() {
        List<BlockPos> tube = definition().positionsOf('K', this.worldPosition, controllerFacing());
        BlockPos foot = tube.stream().min(java.util.Comparator.comparingInt(BlockPos::getY)).orElse(this.worldPosition);
        return inWorld(Vec3.atBottomCenterOf(foot));
    }

    /** The command rocket: ejected from its tube and away over the network's launchers, a missile with no warhead. */
    private Flights.Flight launchRocket(ServerLevel level, LaunchNetwork.Network n) {
        Vec3 pad = pad();
        CommandRocket.Course course = CommandRocket.plan(pad, LaunchNetwork.of(level).commandable(n.id), controllerFacing().getOpposite());
        Flights.Flight f = Flights.launch(level, pad, course.target(), ItemStack.EMPTY, SizeClass.S, true, null, CommandRocket.TUBE_DEPTH,
                CommandRocket.TUBE_BURN);
        LaunchNetwork.launched(level, this.worldPosition, f);
        // The gas charge's burst out of the tube's mouth as it is thrown up.
        Vec3 mouth = pad.add(0, CommandRocket.TUBE_DEPTH + 0.3, 0);
        particles(level, ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 14, 0.3, 0.15, 0.3, 0.06);
        particles(level, ParticleTypes.LARGE_SMOKE, mouth.x, mouth.y + 0.4, mouth.z, 8, 0.25, 0.25, 0.25, 0.04);
        return f;
    }

    /** What its console shows of the course its rocket would fly now (worked out again every two seconds while watched). */
    private CommandRocket.Course course(ServerLevel level, LaunchNetwork.Network n) {
        long now = level.getGameTime();
        if (this.course == null || now - this.courseAt >= COURSE_INTERVAL || now < this.courseAt) {
            this.course = CommandRocket.plan(pad(), LaunchNetwork.of(level).commandable(n.id), controllerFacing().getOpposite());
            this.courseAt = now;
        }
        return this.course;
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

    /**
     * Out of alert: the countdown and the alarm stopped, the lid shut again if it had begun to open, the launchers standing
     * by stood down, and the strikes it has had meanwhile taken as answered.
     */
    private void standDown(ServerLevel level) {
        long now = level.getGameTime();
        float open = lidAngle(now);
        if (open > 0) {
            shutLid(now, open);
        }
        this.alertUntil = -1;
        this.drill = false;
        Alarms.lower(level, this.channel, this.worldPosition);
        standDownLaunchers(level);
        skip(level, now);
    }

    /** Every launcher of its network standing by for it stood down. */
    private void standDownLaunchers(ServerLevel level) {
        LaunchNetwork nets = LaunchNetwork.of(level);
        LaunchNetwork.Network n = nets.networkOf(this.worldPosition);
        if (n != null) {
            nets.standDown(level, n.id, this.worldPosition);
        }
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
     * Its report: incomplete, no power, no rocket (READY only with one), counting in alert (its countdown) and firing (0);
     * armed while watching, in alert or firing; the strike's place in alert; its radius; its rocket (as what it holds).
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
        LaunchNetwork nets = LaunchNetwork.of(level);
        LaunchNetwork.Network n = nets.networkOf(this.worldPosition);
        String why = this.state == State.SAFE ? cannotArm(level) : null;
        long now = level.getGameTime();
        CommandRocket.Course c = n == null || this.state == State.FIRING || this.state == State.SPENT ? null : course(level, n);
        PacketDistributor.sendToPlayer(player, new DeadHandPayloads.Scope(this.worldPosition, now, isFormed(), this.powered, this.state.ordinal(),
                this.rocket, this.drill, this.alertUntil, this.strikeX, this.strikeZ, this.strikeMagnitude, this.strikeDistance,
                this.origin != null, n == null ? "" : n.code(), why == null ? "" : why, this.armAsked >= 0 && now - this.armAsked <= CONFIRM,
                this.radius, this.threshold, this.countdown, this.command, this.targets, this.channel,
                n == null ? 0 : nets.standingFor(n.id, this.worldPosition), c == null ? 0 : c.reached().size(), c == null ? 0 : c.of(), this.sent,
                List.copyOf(this.log)));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;                                       // its console is its own (DeadHandBlock opens it)
    }

    // --- the model (tools/assets/dead_hand.py) ----------------------------------------------------------------------------
    /** The tube's lid open, radians. */
    static final float LID_OPEN = (float) Math.toRadians(112);

    /**
     * How far its lid stands open at {@code now} (radians): swinging open over the countdown's last two seconds, open once
     * the rocket has gone, swinging shut again after a stand-down or a new rocket.
     */
    float lidAngle(double now) {
        double u = this.lidShutAt < 0 ? 1 : Mth.clamp((now - this.lidShutAt) / LID_TICKS, 0, 1);
        float closing = (float) (this.lidShutFrom * (1 - ease(u)));
        return switch (this.state) {
            case ALERT -> this.drill || !this.rocket ? closing
                    : Math.max(closing, (float) (LID_OPEN * ease(Mth.clamp((now - (this.alertUntil - LID_TICKS)) / LID_TICKS, 0, 1))));
            case FIRING, SPENT -> LID_OPEN;
            default -> closing;
        };
    }

    /** The lid starts to swing shut from {@code from} radians open. */
    private void shutLid(long now, float from) {
        this.lidShutAt = now;
        this.lidShutFrom = from;
    }

    private static double ease(double u) {
        return u * u * (3 - 2 * u);
    }

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
        // The command link's light: a slow blink, fast while its rocket is in the air.
        if (!on || (this.state == State.FIRING ? now % 6 >= 3 : now % 40 >= 4)) {
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
        float lid = lidAngle(this.level.getGameTime() + partialTick);
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
        if (this.rocketId != null) {
            output.putString("rocket_flight", this.rocketId.toString());
        }
        output.putInt("sent", this.sent);
        output.putLong("lid_shut_at", this.lidShutAt);
        output.putFloat("lid_shut_from", this.lidShutFrom);
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
        try {
            this.rocketId = input.getString("rocket_flight").map(UUID::fromString).orElse(null);
        } catch (IllegalArgumentException e) {
            this.rocketId = null;
        }
        this.sent = input.getIntOr("sent", 0);
        this.lidShutAt = input.getLongOr("lid_shut_at", -1);
        this.lidShutFrom = input.getFloatOr("lid_shut_from", 0);
        this.log.clear();
        try {
            input.read("log", Entry.CODEC.listOf()).ifPresent(this.log::addAll);
        } catch (IllegalArgumentException ignored) {
            // a line from a later version: the log starts again
        }
    }
}
