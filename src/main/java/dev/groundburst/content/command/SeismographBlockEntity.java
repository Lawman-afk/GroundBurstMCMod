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
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.content.alarm.Alarms;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.NetworkMember;
import dev.groundburst.content.launch.ScreenWatched;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * The Seismograph (DESIGN.md §17.17): every blast it feels ({@link Seismic}) recorded with what one station can tell of
 * it, its magnitude, distance and bearing (each to within what a signal that strong allows), the yield worked back from
 * the magnitude. Its record is kept here (the last {@value #KEEP}) and drawn by its screen as the drum's paper; an
 * arrival may raise its alarm channel (a Blast alert) and gives a comparator its magnitude for a moment; on a network
 * (Launch Codes), it reports what it records there. 10 FE a tick; no power or an EMP, it records nothing. Unloaded, it
 * catches up as it loads (what arrived then recorded, without alarms).
 */
public class SeismographBlockEntity extends MachineBlockEntity implements AnimatedMachine, ScreenWatched, NetworkMember {
    public static final int ENERGY_CAPACITY = 20_000, POWER_USE = 10;
    /** Records kept; how often it listens (ticks); an arrival this old when it is read is not fresh (no alarm, no sound). */
    public static final int KEEP = 16, LISTEN = 5, FRESH = 600;
    /** How long its alarm stays raised and its comparator lit after an arrival (ticks); how often viewers are sent its record. */
    public static final int ALARM_TICKS = 600, COMPARATOR_TICKS = 40, VIEW_INTERVAL = 10;
    /** What it records, by its setting: everything felt this hard or harder. */
    public static final float[] SENSITIVITY = {0f, 1f, 2f, 3f};
    /** Its alarm, by its setting: off, or at these magnitudes and up. */
    public static final float[] ALARM_AT = {0f, 2f, 3f, 4f, 5f};
    public static final int DEFAULT_SENSITIVITY = 1;

    /**
     * One arrival: the event's id, its first wave's arrival (game time) and its second's, how hard it was felt, and what
     * this station made of it: the magnitude, the distance along the ground (blocks), the bearing (degrees clockwise from
     * north), how far the bearing and distance may be off, and how long it shook the ground (ticks; 0 a blast).
     */
    public record Record(long event, long arrival, long second, float felt, float magnitude, int distance, float bearing, float bearingSpread,
            int distanceSpread, int duration) {
        public static final Codec<Record> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("event").forGetter(Record::event), Codec.LONG.fieldOf("arrival").forGetter(Record::arrival),
                Codec.LONG.fieldOf("second").forGetter(Record::second), Codec.FLOAT.fieldOf("felt").forGetter(Record::felt),
                Codec.FLOAT.fieldOf("magnitude").forGetter(Record::magnitude), Codec.INT.fieldOf("distance").forGetter(Record::distance),
                Codec.FLOAT.fieldOf("bearing").forGetter(Record::bearing), Codec.FLOAT.fieldOf("bearing_spread").forGetter(Record::bearingSpread),
                Codec.INT.fieldOf("distance_spread").forGetter(Record::distanceSpread), Codec.INT.fieldOf("duration").forGetter(Record::duration))
                .apply(i, Record::new));

        /** The yield its magnitude stands for, tonnes of TNT. */
        public double tonnes() {
            return Seismic.tonnes(this.magnitude);
        }
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> viewers = new HashSet<>();
    private final List<Record> records = new ArrayList<>();
    /** The last event it took (-1: not yet: it starts after the newest when first ticked). */
    private long lastEvent = -1;
    private int sensitivity = DEFAULT_SENSITIVITY, alarmAt, channel;
    private boolean powered;
    /** Its alarm raised until (game time), for a blast this big this far off. */
    private long alarmUntil = -1;
    private float alarmMagnitude;
    private int alarmDistance;
    private int comparator;
    private long comparatorUntil = -1;
    // What the model shows (synced): the last fresh arrival, how hard it was felt, its magnitude, and how long it shook.
    private long shownArrival = -1, shownSecond = -1;
    private float shownFelt, shownMagnitude = -9;
    private int shownDuration;

    public SeismographBlockEntity(BlockPos pos, BlockState state) {
        super(GBCommand.SEISMOGRAPH_ENTITY.get(), pos, state, ENERGY_CAPACITY, 1_000, 0, 0, new int[0], new int[0]);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    public List<Record> records() {
        return List.copyOf(this.records);
    }

    public int sensitivity() {
        return this.sensitivity;
    }

    public int alarmAt() {
        return this.alarmAt;
    }

    public int channel() {
        return this.channel;
    }

    public boolean powered() {
        return this.powered;
    }

    /** What a comparator reads: twice the last arrival's magnitude (to 15), for {@value #COMPARATOR_TICKS} ticks after it. */
    public int comparator() {
        return this.comparator;
    }

    /** Its settings, from its screen: what it records, its alarm, its channel. */
    public void applySettings(ServerLevel level, int sensitivity, int alarmAt, int channel) {
        int c = Math.clamp(channel, 0, Alarms.MAX_CHANNEL);
        if (c != this.channel) {
            Alarms.lower(level, this.channel, this.worldPosition);     // off its old channel at once
        }
        this.sensitivity = Math.clamp(sensitivity, 0, SENSITIVITY.length - 1);
        this.alarmAt = Math.clamp(alarmAt, 0, ALARM_AT.length - 1);
        this.channel = c;
        if (this.alarmAt == 0 && this.alarmUntil >= 0) {
            this.alarmUntil = -1;
            Alarms.lower(level, this.channel, this.worldPosition);
        }
        setChanged();
        for (UUID id : List.copyOf(this.viewers)) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer p) {
                send(level, p);
            }
        }
    }

    // --- listening -------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        long now = level.getGameTime();
        Seismic seismic = Seismic.of(level);
        if (this.lastEvent < 0) {
            this.lastEvent = seismic.latest();                      // set down now: it records from now on
            setChanged();
        }
        this.powered = this.energy.consume(POWER_USE);
        this.status = this.powered ? MachineStatus.RUNNING : MachineStatus.NO_POWER;
        if (this.powered) {
            if ((now + this.worldPosition.hashCode()) % LISTEN == 0) {
                listen(level, seismic, now);
            }
        } else {
            skip(level, seismic, now);
        }
        keepAlarm(level, now);
        if (this.comparatorUntil >= 0 && now >= this.comparatorUntil) {
            this.comparatorUntil = -1;
            setComparator(level, 0);
        }
        LaunchNetwork.of(level).tickMember(level, this);
        if (!this.viewers.isEmpty() && now % VIEW_INTERVAL == 0) {
            for (UUID id : List.copyOf(this.viewers)) {
                if (level.getPlayerByUUID(id) instanceof ServerPlayer p && p.position().distanceTo(inWorld(Vec3.atCenterOf(this.worldPosition))) < 16) {
                    send(level, p);
                } else {
                    this.viewers.remove(id);
                }
            }
        }
        return this.powered;
    }

    /** In an EMP field it goes dark: it records nothing that arrives meanwhile. */
    @Override
    protected void onEmp(ServerLevel level) {
        this.powered = false;
        skip(level, Seismic.of(level), level.getGameTime());
        keepAlarm(level, level.getGameTime());
    }

    /** Every event that has reached it by now and that it feels at its setting, recorded. */
    private void listen(ServerLevel level, Seismic seismic, long now) {
        Vec3 here = inWorld(Vec3.atCenterOf(this.worldPosition));
        for (Seismic.Event e : seismic.after(this.lastEvent)) {
            long arrival = e.arrival(here);
            if (arrival > now) {
                break;                                           // on its way (the next ones may wait with it a tick or two)
            }
            this.lastEvent = e.id();
            setChanged();
            double d = e.distance(here);
            float felt = Seismic.felt(e.magnitude(), d);
            if (felt < SENSITIVITY[this.sensitivity]) {
                continue;
            }
            record(level, e, here, felt, d, now - arrival <= FRESH);
        }
    }

    /** Dark: what reaches it now passes unrecorded. */
    private void skip(ServerLevel level, Seismic seismic, long now) {
        Vec3 here = inWorld(Vec3.atCenterOf(this.worldPosition));
        for (Seismic.Event e : seismic.after(this.lastEvent)) {
            if (e.arrival(here) > now) {
                break;
            }
            this.lastEvent = e.id();
            setChanged();
        }
    }

    /**
     * What one station makes of an arrival: the bearing to within 2 to 14 degrees, the distance (from the S-P time) to
     * within 3 to 15%, the magnitude to within 0.05 to 0.3, the stronger it is felt the closer (the same each time for
     * one event at one place).
     */
    private void record(ServerLevel level, Seismic.Event e, Vec3 here, float felt, double d, boolean fresh) {
        double dx = e.x() - here.x, dz = e.z() - here.z, along = Math.hypot(dx, dz);
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        long h = mix(e.id() * 0x9E3779B97F4A7C15L ^ this.worldPosition.asLong());
        double u1 = unit(h), u2 = unit(mix(h)), u3 = unit(mix(mix(h)));
        double strength = Math.max(0, felt - 1);
        double bSpread = Mth.clamp(14 - 4 * strength, 2, 14), dShare = Mth.clamp(0.15 - 0.03 * strength, 0.03, 0.15);
        double mSpread = Mth.clamp(0.3 - 0.07 * strength, 0.05, 0.3);
        double b = Mth.positiveModulo(bearing + u1 * bSpread, 360.0);
        double dist = Math.max(0, along * (1 + u2 * dShare));
        float m = (float) (e.magnitude() + u3 * mSpread);
        long second = e.time() + (long) Math.ceil(d / Seismic.S_SPEED);
        Record r = new Record(e.id(), e.arrival(here), second, felt, m, (int) Math.round(dist), (float) b, (float) bSpread,
                (int) Math.round(along * dShare), e.duration());
        this.records.add(r);
        while (this.records.size() > KEEP) {
            this.records.removeFirst();
        }
        setChanged();
        double rb = Math.toRadians(b);
        double ex = here.x + Math.sin(rb) * dist, ez = here.z - Math.cos(rb) * dist;
        LaunchNetwork.of(level).felt(level, this.worldPosition, e.id(), e.time(), e.at(), m, ex, ez, here, b, bSpread, dist, along * dShare);
        if (!fresh) {
            return;                                              // caught up as it loaded: no alarm, no sound
        }
        long now = level.getGameTime();
        this.shownArrival = r.arrival();
        this.shownSecond = second;
        this.shownFelt = felt;
        this.shownMagnitude = m;
        this.shownDuration = e.duration();
        level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        playSound(level, this.worldPosition, GBSounds.SEISMOGRAPH_EVENT.get(), SoundSource.BLOCKS, 0.6f, 1f);
        this.comparatorUntil = now + COMPARATOR_TICKS;
        setComparator(level, Mth.clamp(Math.round(m * 2), 1, 15));
        if (this.alarmAt > 0 && m >= ALARM_AT[this.alarmAt]) {
            if (this.alarmUntil <= now || m >= this.alarmMagnitude) {  // a new alarm, or a bigger blast than the one it is for
                this.alarmMagnitude = m;
                this.alarmDistance = (int) Math.round(dist);
            }
            this.alarmUntil = now + ALARM_TICKS;
            Alarms.raise(level, this.channel, this.worldPosition, new Alarms.Alert(Alarms.Kind.BLAST, this.alarmDistance, -1, this.alarmMagnitude));
        }
    }

    /** Its alarm raised while it lasts (refreshed as senders do), lowered when it ends. */
    private void keepAlarm(ServerLevel level, long now) {
        if (this.alarmUntil < 0) {
            return;
        }
        if (now >= this.alarmUntil) {
            this.alarmUntil = -1;
            Alarms.lower(level, this.channel, this.worldPosition);
        } else if ((now + this.worldPosition.hashCode()) % Alarms.REFRESH == 0) {
            Alarms.raise(level, this.channel, this.worldPosition, new Alarms.Alert(Alarms.Kind.BLAST, this.alarmDistance, -1, this.alarmMagnitude));
        }
    }

    private void setComparator(ServerLevel level, int value) {
        if (value != this.comparator) {
            this.comparator = value;
            level.updateNeighbourForOutputSignal(this.worldPosition, getBlockState().getBlock());
        }
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A number in [-1, 1) from a hash. */
    private static double unit(long h) {
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel level) {
            Alarms.lower(level, this.channel, this.worldPosition);
        }
        super.setRemoved();
    }

    // --- on a network (DESIGN.md §17.17) ---------------------------------------------------------------------------------
    @Override
    public LaunchNetwork.Role networkRole() {
        return LaunchNetwork.Role.SEISMOGRAPH;
    }

    /**
     * Its report: listening (READY), dark (no power, EMP); where it makes its last blast out to be, and that blast's
     * magnitude in tenths (in the report's reach, which a seismograph has none of).
     */
    @Override
    public LaunchNetwork.Report networkReport(ServerLevel level) {
        LaunchSiloBlockEntity.Readiness r = emped() ? LaunchSiloBlockEntity.Readiness.EMP
                : this.powered ? LaunchSiloBlockEntity.Readiness.READY : LaunchSiloBlockEntity.Readiness.NO_POWER;
        Optional<BlockPos> last = Optional.empty();
        if (!this.records.isEmpty()) {
            Record rec = this.records.getLast();
            Vec3 here = inWorld(Vec3.atCenterOf(this.worldPosition));
            double rb = Math.toRadians(rec.bearing());
            last = Optional.of(BlockPos.containing(here.x + Math.sin(rb) * rec.distance(), here.y, here.z - Math.cos(rb) * rec.distance()));
        }
        return LaunchNetwork.Report.of(r.ordinal(), -1, false, last, Math.round(this.records.isEmpty() ? 0 : this.records.getLast().magnitude() * 10),
                ItemStack.EMPTY, this.powered);
    }

    // --- its screen ------------------------------------------------------------------------------------------------------------
    @Override
    public void addViewer(ServerPlayer player) {
        this.viewers.add(player.getUUID());
        send((ServerLevel) player.level(), player);
    }

    @Override
    public void removeViewer(ServerPlayer player) {
        this.viewers.remove(player.getUUID());
    }

    private void send(ServerLevel level, ServerPlayer player) {
        LaunchNetwork.Network n = LaunchNetwork.of(level).networkOf(this.worldPosition);
        PacketDistributor.sendToPlayer(player, new SeismographPayloads.View(this.worldPosition, this.powered && !emped(), level.getGameTime(),
                this.sensitivity, this.alarmAt, this.channel, n == null ? "" : n.code(), List.copyOf(this.records)));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;                                       // its screen is its own (SeismographBlock opens it)
    }

    @Override
    public @Nullable SoundEvent loopSound() {
        return GBSounds.SEISMOGRAPH_LOOP.get();
    }

    // --- the model (tools/assets/seismograph.py) -------------------------------------------------------------------------
    /** The drum turns once a minute; the pen's carriage crosses the drum in twelve. */
    public static final int DRUM_TICKS = 1200, CARRIAGE_TICKS = 14400;
    /** The carriage's travel along the drum (model pixels), the pen's widest swing (radians). */
    static final float TRAVEL = 8.0f, PEN_SWING = 0.26f;

    /** Client: what the model shows. */
    public long shownArrival() {
        return this.shownArrival;
    }

    public float shownMagnitude() {
        return this.shownMagnitude;
    }

    public boolean shownPowered() {
        return getBlockState().getValue(dev.groundburst.content.machine.MachineBlock.ACTIVE);
    }

    /** How far the pen swings now (-1 to 1): the ground's faint noise, a blast's first and second arrivals and its coda. */
    public double trace(double now) {
        double noise = 0.06 * Math.sin(now * 0.37) * Math.sin(now * 1.13 + 0.7);
        if (this.shownArrival < 0) {
            return noise;
        }
        return noise + Trace.pen(now, this.shownArrival, this.shownSecond, this.shownFelt, this.shownDuration);
    }

    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        if (this.level == null) {
            return Map.of();
        }
        double now = this.level.getGameTime() + partialTick;
        boolean on = shownPowered();
        float drum = on ? (float) (now % DRUM_TICKS / DRUM_TICKS * Math.PI * 2) : 0;
        float pen = on ? (float) (Mth.clamp(trace(now), -1, 1) * PEN_SWING) : 0;
        float needle = on ? (float) (Mth.clamp(trace(now), -1, 1) * 0.9) : -0.9f;
        return Map.of("drum", new float[] {drum, 0, 0}, "pen", new float[] {0, 0, pen}, "needle", new float[] {0, 0, needle});
    }

    @Override
    public Map<String, float[]> boneOffsets(float partialTick) {
        if (this.level == null) {
            return Map.of();
        }
        double now = this.level.getGameTime() + partialTick;
        float u = (float) (now % CARRIAGE_TICKS / CARRIAGE_TICKS);
        return Map.of("carriage", new float[] {-(u - 0.5f) * TRAVEL, 0, 0});        // (x as GeckoLib has it: negated)
    }

    /** Dark: its lamps and readout off; the event lamp blinks for two seconds after an arrival. */
    @Override
    public Set<String> hiddenBones() {
        Set<String> hidden = new HashSet<>();
        boolean on = shownPowered();
        long now = this.level == null ? 0 : this.level.getGameTime();
        if (!on) {
            hidden.add("lamp_power");
            hidden.add("readout");
        }
        if (!on || this.shownArrival < 0 || now - this.shownArrival > 40 || now % 8 >= 5) {
            hidden.add("lamp_event");
        }
        return hidden;
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
        CompoundTag tag = saveCustomOnly(registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("last_event", this.lastEvent);
        output.putInt("sensitivity", this.sensitivity);
        output.putInt("alarm_at", this.alarmAt);
        output.putInt("channel", this.channel);
        output.store("records", Record.CODEC.listOf(), List.copyOf(this.records));
        output.putLong("shown_arrival", this.shownArrival);
        output.putLong("shown_second", this.shownSecond);
        output.putFloat("shown_felt", this.shownFelt);
        output.putFloat("shown_magnitude", this.shownMagnitude);
        output.putInt("shown_duration", this.shownDuration);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.lastEvent = input.getLongOr("last_event", -1);
        this.sensitivity = Math.clamp(input.getIntOr("sensitivity", DEFAULT_SENSITIVITY), 0, SENSITIVITY.length - 1);
        this.alarmAt = Math.clamp(input.getIntOr("alarm_at", 0), 0, ALARM_AT.length - 1);
        this.channel = input.getIntOr("channel", 0);
        this.records.clear();
        input.read("records", Record.CODEC.listOf()).ifPresent(this.records::addAll);
        this.shownArrival = input.getLongOr("shown_arrival", -1);
        this.shownSecond = input.getLongOr("shown_second", -1);
        this.shownFelt = input.getFloatOr("shown_felt", 0);
        this.shownMagnitude = input.getFloatOr("shown_magnitude", -9);
        this.shownDuration = input.getIntOr("shown_duration", 0);
    }
}
