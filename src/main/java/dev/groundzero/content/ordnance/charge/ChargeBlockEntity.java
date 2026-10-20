package dev.groundzero.content.ordnance.charge;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundzero.content.ordnance.GZOrdnance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A placed charge's fuze (DESIGN.md §9.4): Remote (a detonator on its frequency sets it off) or Timer (arming it
 * starts the countdown), the frequency and timer, the countdown, the thermite burn, and the timed bomb's wires. Synced
 * to clients for the settings and defuse screens and the timed bomb's clock face.
 */
public class ChargeBlockEntity extends BlockEntity {
    public enum Mode { REMOTE, TIMER }

    /** Timed bomb wires: red, blue, yellow, green. One defuses it, one sets it off, the other two halve the time left. */
    public static final int WIRES = 4;

    private Mode mode = Mode.REMOTE;
    private int frequency;
    private int timer;
    /** Ticks left (-1: not counting). */
    private int countdown = -1;
    /** Thermite: ticks it has burned (-1: not lit). */
    private int burn = -1;
    private int defuseWire, boomWire, cutWires;
    private @Nullable UUID owner;

    public ChargeBlockEntity(BlockPos pos, BlockState state) {
        super(GZOrdnance.CHARGE.get(), pos, state);
        this.timer = kind().timer;
        this.mode = kind().fuzeChoice ? Mode.REMOTE : Mode.TIMER;
    }

    public ChargeKind kind() {
        return getBlockState().getBlock() instanceof ChargeBlock b ? b.kind : ChargeKind.C4;
    }

    public Mode mode() {
        return this.mode;
    }

    public int frequency() {
        return this.frequency;
    }

    public int timer() {
        return this.timer;
    }

    public int countdown() {
        return this.countdown;
    }

    public boolean counting() {
        return this.countdown >= 0;
    }

    public boolean burning() {
        return this.burn >= 0;
    }

    public int cutWires() {
        return this.cutWires;
    }

    public boolean armed() {
        return getBlockState().getValue(ChargeBlock.ARMED);
    }

    public @Nullable UUID owner() {
        return this.owner;
    }

    /** Fuze settings, from the settings screen. Only while safe. */
    public void configure(Mode mode, int frequency, int timer) {
        if (armed()) {
            return;
        }
        this.mode = kind().fuzeChoice ? mode : Mode.TIMER;
        this.frequency = Math.clamp(frequency, 0, ChargeKind.MAX_FREQUENCY);
        this.timer = Math.clamp(timer, 1, ChargeKind.MAX_TIMER);
        sync();
    }

    /** For GameTests and the detonator: sets the frequency regardless. */
    public void setFrequency(int frequency) {
        this.frequency = Math.clamp(frequency, 0, ChargeKind.MAX_FREQUENCY);
        sync();
    }

    // --- arming ----------------------------------------------------------------------------------------------------------
    /** Arms it (a timer fuze starts counting down). */
    public void arm(ServerLevel level, @Nullable Entity by) {
        if (armed() || burning()) {
            return;
        }
        this.owner = by == null ? null : by.getUUID();
        level.setBlock(this.worldPosition, getBlockState().setValue(ChargeBlock.ARMED, true), Block.UPDATE_ALL);
        level.playSound(null, this.worldPosition, GZOrdnance.CHARGE_ARM.get(), SoundSource.BLOCKS, 0.8f, 1f);
        if (this.mode == Mode.TIMER) {
            startCountdown(level);
        }
        sync();
    }

    /** Makes it safe again; false if it cannot be (a timed bomb counting down has to be defused). */
    public boolean disarm(ServerLevel level) {
        if (!armed()) {
            return true;
        }
        if (burning() || kind() == ChargeKind.TIMED_BOMB && counting()) {
            return false;
        }
        this.countdown = -1;
        level.setBlock(this.worldPosition, getBlockState().setValue(ChargeBlock.ARMED, false), Block.UPDATE_ALL);
        level.playSound(null, this.worldPosition, GZOrdnance.CHARGE_DISARM.get(), SoundSource.BLOCKS, 0.8f, 1f);
        sync();
        return true;
    }

    private void startCountdown(ServerLevel level) {
        this.countdown = this.timer * 20;
        this.cutWires = 0;
        this.defuseWire = level.getRandom().nextInt(WIRES);
        this.boomWire = (this.defuseWire + 1 + level.getRandom().nextInt(WIRES - 1)) % WIRES;
        sync();
    }

    /** Set off (redstone, a detonator, a timer running out): a timer starts counting, anything else goes off. */
    public void trigger(ServerLevel level, @Nullable Entity by) {
        if (!armed() || burning()) {
            return;
        }
        if (this.mode == Mode.TIMER && !counting()) {
            startCountdown(level);
        } else if (this.mode != Mode.TIMER || this.countdown == 0) {
            goOff(level, by);
        }
    }

    private void goOff(ServerLevel level, @Nullable Entity by) {
        if (kind() == ChargeKind.THERMITE) {
            this.countdown = -1;
            this.burn = 0;
            level.setBlock(this.worldPosition, getBlockState().setValue(ChargeBlock.LIT, true), Block.UPDATE_ALL);
            level.playSound(null, this.worldPosition, GZOrdnance.THERMITE_BURN.get(), SoundSource.BLOCKS, 1.2f, 1f);
            sync();
            return;
        }
        Charges.detonate(level, this.worldPosition, getBlockState(), by != null ? by : ownerEntity(level));
    }

    private @Nullable Entity ownerEntity(ServerLevel level) {
        return this.owner == null ? null : level.getEntity(this.owner);
    }

    /** The defuse screen: cutting a wire. 1 defused, 0 sped up (or already cut), -1 set off. */
    public int cut(ServerLevel level, int wire) {
        if (kind() != ChargeKind.TIMED_BOMB || !counting() || wire < 0 || wire >= WIRES || (this.cutWires & 1 << wire) != 0) {
            return 0;
        }
        this.cutWires |= 1 << wire;
        level.playSound(null, this.worldPosition, GZOrdnance.WIRE_SNIP.get(), SoundSource.BLOCKS, 1f, 1f);
        if (wire == this.defuseWire) {
            this.countdown = -1;
            level.setBlock(this.worldPosition, getBlockState().setValue(ChargeBlock.ARMED, false), Block.UPDATE_ALL);
            level.playSound(null, this.worldPosition, GZOrdnance.CHARGE_DISARM.get(), SoundSource.BLOCKS, 1f, 0.8f);
            sync();
            return 1;
        }
        if (wire == this.boomWire) {
            Charges.detonate(level, this.worldPosition, getBlockState(), ownerEntity(level));
            return -1;
        }
        this.countdown = Math.max(60, this.countdown / 2);
        level.playSound(null, this.worldPosition, GZOrdnance.BOMB_BEEP.get(), SoundSource.BLOCKS, 1.2f, 0.6f);
        sync();
        return 0;
    }

    /** For GameTests: which wire defuses it. */
    public int defuseWireForTest() {
        return this.defuseWire;
    }

    /** For GameTests: which wire sets it off. */
    public int boomWireForTest() {
        return this.boomWire;
    }

    // --- ticking ---------------------------------------------------------------------------------------------------------
    public void serverTick(ServerLevel level) {
        if (this.burn >= 0) {
            this.burn++;
            if (Charges.thermite(level, this.worldPosition, getBlockState(), this.burn)) {
                this.burn = -1;
            }
            return;
        }
        if (this.countdown > 0) {
            this.countdown--;
            int every = kind() != ChargeKind.TIMED_BOMB ? 20 : this.countdown < 60 ? 4 : this.countdown < 200 ? 10 : 20;
            if (this.countdown % every == 0 && this.countdown > 0) {
                level.playSound(null, this.worldPosition, GZOrdnance.BOMB_BEEP.get(), SoundSource.BLOCKS, 0.7f,
                        this.countdown < 60 ? 1.3f : 1f);
            }
            if (this.countdown == 0) {
                goOff(level, null);
            }
        }
    }

    /** Clients count down too, for the clock face. */
    public void clientTick() {
        if (this.countdown > 0) {
            this.countdown--;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel server) {
            Charges.track(server, this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level instanceof ServerLevel server) {
            Charges.untrack(server, this.worldPosition);
        }
    }

    // --- saving and syncing ----------------------------------------------------------------------------------------------
    private void sync() {
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("mode", this.mode.name());
        output.putInt("frequency", this.frequency);
        output.putInt("timer", this.timer);
        output.putInt("countdown", this.countdown);
        output.putInt("burn", this.burn);
        output.putInt("defuse_wire", this.defuseWire);
        output.putInt("boom_wire", this.boomWire);
        output.putInt("cut_wires", this.cutWires);
        if (this.owner != null) {
            output.putString("owner", this.owner.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.mode = input.getString("mode").map(s -> s.equals("TIMER") ? Mode.TIMER : Mode.REMOTE).orElse(this.mode);
        this.frequency = input.getIntOr("frequency", 0);
        this.timer = input.getIntOr("timer", this.timer);
        this.countdown = input.getIntOr("countdown", -1);
        this.burn = input.getIntOr("burn", -1);
        this.defuseWire = input.getIntOr("defuse_wire", 0);
        this.boomWire = input.getIntOr("boom_wire", 1);
        this.cutWires = input.getIntOr("cut_wires", 0);
        this.owner = input.getString("owner").map(UUID::fromString).orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
