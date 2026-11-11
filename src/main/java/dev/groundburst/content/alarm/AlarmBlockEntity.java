package dev.groundburst.content.alarm;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.defence.Interception;
import dev.groundburst.content.effect.GasAgent;
import dev.groundburst.content.effect.GasGrid;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.radiation.Emitters;
import dev.groundburst.content.radiation.RadiationSources;
import dev.groundburst.content.radiation.Shielding;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An alarm device (DESIGN.md §18.6): its channel and its choices, a redstone signal into it, and what it is doing. A
 * sender raises its channel while its condition holds (a missile coming down within its warning radius, its Alarm Panel
 * switched on or powered, a hazard over its threshold); everything else answers its channel (or a redstone signal into
 * it): sounders sound (unless silenced), lights light, the display shows the alert, the relay gives redstone. The client
 * is told what it shows (its block state's ACTIVE; for a display, a receiver or a monitor, the alert and its figures).
 */
public class AlarmBlockEntity extends BlockEntity {
    /** A receiver's warning radii; a monitor's radiation thresholds (RAD/h). */
    public static final int[] RADII = {64, 128, 256, 512, 1024};
    public static final float[] THRESHOLDS = {10, 50, 200, 1000};
    /** How long a siren sounds the All Clear after an attack ends (ticks); how often a listener looks (ticks). */
    public static final int ALL_CLEAR = 300, LISTEN = 5;
    /** An Alarm Panel's actions (from its screen). */
    public static final int SOUND = 0, STOP = 1, SILENCE = 2, TEST = 3;
    /** How long a panel's Test sounds its channel. */
    public static final int TEST_TICKS = 100;

    /** Client hook: a device's client tick (its sounds; set by the client). */
    public static Consumer<AlarmBlockEntity> clientHook = be -> {};

    private int channel;
    private final int[] options;
    /** A redstone signal into it; an Alarm Panel switched on by hand. */
    private boolean powered, manual;
    /** What it shows: the alert's kind (-1: all clear), how many missiles, when the first comes down, a reading. */
    private int kind = -1, count;
    private long impactAt = -1;
    private float reading, gas;
    /** A siren's All Clear, until when; whether it last sounded for an attack. */
    private long allClearUntil = -1;
    private boolean attack;
    /** The redstone it gives. */
    private int output;

    public AlarmBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        AlarmDevice d = deviceOf(state);
        this.options = new int[d.options.size()];
        for (int i = 0; i < this.options.length; i++) {
            this.options[i] = d.options.get(i).def();
        }
    }

    private static AlarmDevice deviceOf(BlockState state) {
        return state.getBlock() instanceof AlarmBlock b ? b.device() : AlarmDevice.RELAY;
    }

    public AlarmDevice device() {
        return deviceOf(getBlockState());
    }

    public int channel() {
        return this.channel;
    }

    /** The value of its option at {@code index} (0 if it has none there). */
    public int option(int index) {
        return index >= 0 && index < this.options.length ? this.options[index] : 0;
    }

    public int option(String key) {
        return option(device().option(key));
    }

    public int[] options() {
        return this.options.clone();
    }

    public boolean manual() {
        return this.manual;
    }

    /** The alert it shows (-1: all clear), its count, impact time and reading (radiation RAD/h or gas %), and gas at a monitor. */
    public int kind() {
        return this.kind;
    }

    public int count() {
        return this.count;
    }

    public long impactAt() {
        return this.impactAt;
    }

    public float reading() {
        return this.reading;
    }

    public float gas() {
        return this.gas;
    }

    /** Whether a siren is sounding the All Clear (steady) now. */
    public boolean allClear() {
        return this.level != null && this.level.getGameTime() < this.allClearUntil;
    }

    /** Whether its block shows it working (sounding, lit, raised). */
    public boolean active() {
        return getBlockState().getValue(AlarmBlock.ACTIVE);
    }

    /** The redstone it gives now. */
    public int output() {
        return this.output;
    }

    public void setPowered(boolean powered) {
        if (powered != this.powered) {
            this.powered = powered;
            setChanged();
        }
    }

    /** Sets its channel and choices (from its screen). */
    public void applySettings(int channel, int[] options) {
        if (this.level instanceof ServerLevel level && channel != this.channel && device().role == AlarmDevice.Role.SENDER) {
            Alarms.lower(level, this.channel, this.worldPosition);       // off its old channel at once
        }
        this.channel = Math.clamp(channel, 0, Alarms.MAX_CHANNEL);
        AlarmDevice d = device();
        for (int i = 0; i < Math.min(options.length, this.options.length); i++) {
            this.options[i] = Math.clamp(options[i], 0, d.options.get(i).values().size() - 1);
        }
        sync();
    }

    public void setOption(int index, int value) {
        if (index >= 0 && index < this.options.length) {
            this.options[index] = value;
            sync();
        }
    }

    /** An Alarm Panel's buttons: sound (switch on), stop, silence the channel's sounders, test the channel. */
    public void panel(int action) {
        if (!(this.level instanceof ServerLevel level) || device() != AlarmDevice.PANEL) {
            return;
        }
        switch (action) {
            case SOUND -> this.manual = true;
            case STOP -> this.manual = false;
            case SILENCE -> Alarms.silence(level, this.channel);
            case TEST -> Alarms.test(level, this.channel, TEST_TICKS);
            default -> { }
        }
        level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 1f);
        sync();
    }

    // --- working ----------------------------------------------------------------------------------------------------------
    void serverTick(ServerLevel level) {
        AlarmDevice d = device();
        long now = level.getGameTime();
        int phase = (int) Math.floorMod(now + this.worldPosition.hashCode(), (long) Alarms.REFRESH);
        if (d.role == AlarmDevice.Role.SENDER) {
            if (d == AlarmDevice.PANEL) {
                panelTick(level);
            } else if (phase == 0) {
                if (d == AlarmDevice.RECEIVER) {
                    receiverTick(level, now);
                } else {
                    monitorTick(level, now);
                }
            }
        } else if (phase % LISTEN == 0) {
            listen(level, d, now);
        }
    }

    /** A Missile Warning Receiver's look: every missile a working Radar Array tracks, coming down within its radius. */
    private void receiverTick(ServerLevel level, long now) {
        int radius = RADII[option("radius")];
        Vec3 c = Vec3.atCenterOf(this.worldPosition);
        int n = 0;
        long first = Long.MAX_VALUE;
        for (Flights.Flight f : Flights.of(level).all()) {
            if (f.progress() <= 0 || f.ticksToImpact() <= 0 || !Interception.tracked(level, f.position())) {
                continue;
            }
            Vec3 t = f.target();
            if (Math.hypot(t.x - c.x, t.z - c.z) <= radius) {
                n++;
                first = Math.min(first, now + f.ticksToImpact());
            }
        }
        if (n > 0) {
            Alarms.raise(level, this.channel, this.worldPosition, new Alarms.Alert(Alarms.Kind.ATTACK, n, first, 0));
            if (n > this.count) {
                level.playSound(null, this.worldPosition, GBSounds.ALARM_ATTENTION.get(), SoundSource.BLOCKS, 1.2f, 1f);   // a new warning
            }
        } else {
            Alarms.lower(level, this.channel, this.worldPosition);
        }
        int mode = option("output");
        int out = n == 0 ? 0 : mode == 0 ? 15 : mode == 1 ? Math.min(15, n) : (int) Math.clamp(15 - (first - now) / 40, 1, 15);
        show(n > 0 ? Alarms.Kind.ATTACK.ordinal() : -1, n, n > 0 ? first : -1, 0, 0);
        setOutput(out);
        setActive(n > 0);
    }

    /** A Hazard Monitor's look: the dose rate and the gas in the air in front of it. */
    private void monitorTick(ServerLevel level, long now) {
        BlockPos front = this.worldPosition.relative(((AlarmBlock) getBlockState().getBlock()).facing(getBlockState()));
        Vec3 at = Vec3.atCenterOf(front);
        float rate = RadiationSources.of(level).rateAt(level, at);
        if (rate > 0) {
            rate *= (float) Shielding.ambient(level, at);
        }
        rate += Emitters.blocks(level, at);
        float g = 0;
        GasGrid grid = GasGrid.of(level);
        for (GasAgent agent : GasAgent.values()) {
            g = Math.max(g, grid.concentration(front, agent));
        }
        Alarms.Alert alert = null;
        if (rate >= THRESHOLDS[option("threshold")]) {
            alert = new Alarms.Alert(Alarms.Kind.RADIATION, 0, -1, rate);
        } else if (option("gas") == 0 && g >= GasGrid.VISIBLE) {
            alert = new Alarms.Alert(Alarms.Kind.GAS, 0, -1, g * 100);
        }
        if (alert != null) {
            Alarms.raise(level, this.channel, this.worldPosition, alert);
            if (now % 40 < Alarms.REFRESH) {
                level.playSound(null, this.worldPosition, GBSounds.GEIGER_ALARM.get(), SoundSource.BLOCKS, 1.2f, 1f);
            }
        } else {
            Alarms.lower(level, this.channel, this.worldPosition);
        }
        show(alert == null ? -1 : alert.kind().ordinal(), 0, -1, rate, g * 100);
        setOutput(alert == null ? 0 : 15);
        setActive(alert != null);
    }

    /** An Alarm Panel: raised while switched on by hand or powered by redstone. */
    private void panelTick(ServerLevel level) {
        boolean on = this.manual || this.powered;
        if (on) {
            Alarms.raise(level, this.channel, this.worldPosition, Alarms.Alert.of(option("raises") == 0 ? Alarms.Kind.ATTACK : Alarms.Kind.ALARM));
        } else if (active()) {
            Alarms.lower(level, this.channel, this.worldPosition);
        }
        setActive(on);
    }

    /** Everything else: answers its channel (or a redstone signal into it). */
    private void listen(ServerLevel level, AlarmDevice d, long now) {
        Alarms.Alert alert = Alarms.status(level, this.channel);
        boolean alarm = alert != null;
        switch (d.role) {
            case SOUNDER -> {
                boolean sounding = this.powered || alarm && !Alarms.silenced(level, this.channel);
                if (d == AlarmDevice.SIREN) {
                    boolean attackNow = alarm && alert.kind() == Alarms.Kind.ATTACK;
                    if (this.attack && !attackNow && !this.powered) {
                        this.allClearUntil = now + ALL_CLEAR;                 // the attack is over: the All Clear, steady
                        sync();
                    }
                    if (attackNow) {
                        this.allClearUntil = -1;
                    }
                    this.attack = attackNow;
                    sounding |= now < this.allClearUntil;
                }
                setActive(sounding);
            }
            case LIGHT -> {
                boolean on = this.powered || alarm;
                if (d == AlarmDevice.LIGHT) {
                    AlarmBlock.Lamp lamp = on ? AlarmBlock.Lamp.RED : option("lit") == 1 ? AlarmBlock.Lamp.WHITE : AlarmBlock.Lamp.OFF;
                    BlockState s = getBlockState();
                    if (s.getValue(AlarmBlock.Light.LAMP) != lamp || s.getValue(AlarmBlock.ACTIVE) != on) {
                        level.setBlock(this.worldPosition, s.setValue(AlarmBlock.Light.LAMP, lamp).setValue(AlarmBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
                    }
                } else {
                    setActive(on);
                }
            }
            case DISPLAY -> {
                if (alert == null && this.powered) {
                    alert = Alarms.Alert.of(Alarms.Kind.ALARM);
                }
                show(alert == null ? -1 : alert.kind().ordinal(), alert == null ? 0 : alert.count(), alert == null ? -1 : alert.impactAt(),
                        alert == null ? 0 : alert.reading(), 0);
                setActive(alert != null);
            }
            case RELAY -> {
                setOutput(alarm != (option("gives") == 1) ? 15 : 0);
                setActive(alarm);
            }
            default -> { }
        }
    }

    /** What it shows; the client is told when it changes (a reading only when it changes by a tenth or more). */
    private void show(int kind, int count, long impactAt, float reading, float gas) {
        boolean changed = kind != this.kind || count != this.count || impactAt != this.impactAt
                || Math.abs(reading - this.reading) > Math.max(0.05f, this.reading * 0.1f) || Math.abs(gas - this.gas) > 1;
        this.kind = kind;
        this.count = count;
        this.impactAt = impactAt;
        if (changed) {
            this.reading = reading;
            this.gas = gas;
            sync();
        }
    }

    private void setOutput(int output) {
        if (output != this.output && this.level != null) {
            this.output = output;
            this.level.updateNeighborsAt(this.worldPosition, getBlockState().getBlock());
            setChanged();
        }
    }

    private void setActive(boolean on) {
        BlockState s = getBlockState();
        if (s.getValue(AlarmBlock.ACTIVE) != on && this.level != null) {
            this.level.setBlock(this.worldPosition, s.setValue(AlarmBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
    }

    void clientTick() {
        clientHook.accept(this);
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel level && device().role == AlarmDevice.Role.SENDER) {
            Alarms.lower(level, this.channel, this.worldPosition);
        }
        super.setRemoved();
    }

    private void sync() {
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
        output.putInt("channel", this.channel);
        output.putIntArray("options", this.options.clone());
        output.putBoolean("powered", this.powered);
        output.putBoolean("manual", this.manual);
        output.putInt("kind", this.kind);
        output.putInt("count", this.count);
        output.putLong("impact_at", this.impactAt);
        output.putFloat("reading", this.reading);
        output.putFloat("gas", this.gas);
        output.putLong("all_clear_until", this.allClearUntil);
        output.putInt("output", this.output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.channel = input.getIntOr("channel", 0);
        input.getIntArray("options").ifPresent(a -> System.arraycopy(a, 0, this.options, 0, Math.min(a.length, this.options.length)));
        this.powered = input.getBooleanOr("powered", false);
        this.manual = input.getBooleanOr("manual", false);
        this.kind = input.getIntOr("kind", -1);
        this.count = input.getIntOr("count", 0);
        this.impactAt = input.getLongOr("impact_at", -1);
        this.reading = input.getFloatOr("reading", 0);
        this.gas = input.getFloatOr("gas", 0);
        this.allClearUntil = input.getLongOr("all_clear_until", -1);
        this.output = input.getIntOr("output", 0);
    }

    /**
     * The two drawn by GeckoLib: the Air-Raid Siren (its head turns while it sounds, spinning up and coasting down) and
     * the Warning Beacon (its lens turns, flashes or burns steady while lit, in its colour).
     */
    public static class Animated extends AlarmBlockEntity implements AnimatedMachine {
        /** The siren's head: its turn at full speed (degrees a tick: once in 8 s) and how fast it gets there. */
        static final float SPIN = 2.25f, SPIN_UP = SPIN / 40f;
        /** The beacon's lens: a turn a second; flashing twice a second. */
        static final float BEACON_SPIN = 18f;
        static final int FLASH = 10;
        private static final String[] COLOURS = {"red", "amber", "blue", "green", "white"};

        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        /** The siren's head (client): its turn (degrees from its front) and speed (degrees a tick), moved on each tick. */
        private float headYaw, spin;

        public Animated(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        /** Where the siren's horn points now (world yaw, as atan2(dx, dz), degrees): its front turned by its head. */
        public float hornYaw() {
            Direction f = ((AlarmBlock) getBlockState().getBlock()).facing(getBlockState());
            float front = switch (f) {
                case SOUTH -> 0;
                case EAST -> 90;
                case WEST -> -90;
                default -> 180;
            };
            return front + this.headYaw;
        }

        /** The siren's head on a client tick: spinning up while it sounds, coasting down after. */
        @Override
        void clientTick() {
            super.clientTick();
            if (device() == AlarmDevice.SIREN) {
                float want = active() ? SPIN : 0;
                this.spin += Math.clamp(want - this.spin, -SPIN_UP, SPIN_UP);
                this.headYaw = (this.headYaw + this.spin) % 360;
            }
        }

        @Override
        public Map<String, float[]> boneRotations(float partialTick) {
            if (this.level == null) {
                return Map.of();
            }
            double now = this.level.getGameTime() + partialTick;
            if (device() == AlarmDevice.SIREN) {
                return Map.of("head", new float[] {0, (float) Math.toRadians(this.headYaw + this.spin * partialTick), 0});
            }
            float yaw = active() && option("mode") == 0 ? (float) (now * BEACON_SPIN % 360) : 0;
            return Map.of("rotor", new float[] {0, (float) Math.toRadians(yaw), 0});
        }

        /** The beacon: its dark lens when off; lit, its lens with one bright side and its beams (rotating) or all lit (flashing: half the time). */
        @Override
        public Set<String> hiddenBones() {
            if (device() != AlarmDevice.BEACON || this.level == null) {
                return Set.of();
            }
            Set<String> hidden = new HashSet<>();
            int mode = option("mode");
            boolean lit = active() && (mode != 1 || this.level.getGameTime() % (2L * FLASH) < FLASH);
            if (!lit) {
                hidden.add("lens_spot");
                hidden.add("lens_full");
                hidden.add("beam");
            } else {
                hidden.add("lens");
                hidden.add(mode == 0 ? "lens_full" : "lens_spot");
                if (mode != 0) {
                    hidden.add("beam");
                }
            }
            return hidden;
        }

        /** The beacon's lens in its colour. */
        @Override
        public @Nullable Identifier textureVariant() {
            return device() == AlarmDevice.BEACON
                    ? GroundBurst.id("textures/block/warning_beacon_" + COLOURS[Math.clamp(option("colour"), 0, COLOURS.length - 1)] + ".png") : null;
        }

        /** No animations: the head and the lens are turned by {@link #boneRotations}. */
        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return this.cache;
        }
    }
}
