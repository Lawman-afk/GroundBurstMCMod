package dev.groundburst.content.construction;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Large Silo Door's middle (DESIGN.md §22.2): it moves the leaves toward open while the door has a redstone signal
 * and toward shut while it has none, a tick at a time ({@link #TRAVEL} ticks end to end, as the Titan II's door took
 * seventeen to twenty seconds, quickened), with its own grinding sound as long as that as it starts; sets its blocks' gap as
 * the leaves pass each block; and tells the client where the leaves started from and when, so it draws them sliding
 * smoothly. A door the world's old sites left {@code stuck} stays where it rusted, whatever the signal.
 */
public class SiloDoorBlockEntity extends BlockEntity implements AnimatedMachine {
    /** Ticks from shut to open. */
    public static final int TRAVEL = 160;
    /** How far each leaf slides to be fully open (model pixels): three blocks and a half, onto its rails. */
    public static final float SLIDE = 56f;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Where the leaves are: 0 shut .. TRAVEL open. */
    private int position;
    private boolean wanted;
    private boolean stuck;
    /** Where they were and when, when they last set off (for the client to draw them moving). */
    private int from;
    private long since;
    private int lastGap = -1;

    public SiloDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Looks again at the redstone round the door (any of its blocks). */
    public void checkPower() {
        if (!(this.level instanceof ServerLevel level) || this.stuck) {
            return;
        }
        boolean powered = SiloDoorBlock.powered(level, this.worldPosition, getBlockState().getValue(SiloDoorBlock.FACING));
        if (powered != this.wanted) {
            this.from = this.position;
            this.since = level.getGameTime();
            this.wanted = powered;
            // Its own sound, as long as the leaves take end to end; at one pitch both ways, so the leaves seat as it ends.
            level.playSound(null, this.worldPosition, GBSounds.LARGE_SILO_DOOR.get(), SoundSource.BLOCKS, 3f, 1f);
            setChanged();
            sync();
        }
    }

    /** Leaves the door rusted where it stands, {@code position} of {@link #TRAVEL} open (the world's old silos; previews). */
    public void setStuck(int position) {
        this.position = Mth.clamp(position, 0, TRAVEL);
        this.from = this.position;
        this.stuck = true;
        setChanged();
        sync();
    }

    void serverTick(ServerLevel level) {
        int before = this.position;
        if (!this.stuck) {
            this.position = Mth.clamp(this.position + (this.wanted ? 1 : -1), 0, TRAVEL);
        }
        int gap = gap(this.position);
        if (gap != this.lastGap) {
            setGap(level, gap);
            this.lastGap = gap;
        }
        if (this.position != before) {
            setChanged();
        }
    }

    /** How far apart the leaves stand in whole blocks either side (0 shut .. 4 open) at a position. */
    public static int gap(int position) {
        return Math.round(eased(position / (float) TRAVEL) * 4f);
    }

    private static float eased(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Every block of the door told how far apart the leaves now stand (only those that are not already). */
    private void setGap(ServerLevel level, int gap) {
        Direction facing = getBlockState().getValue(SiloDoorBlock.FACING);
        for (int u = 0; u <= 2 * SiloDoorBlock.HALF_LENGTH; u++) {
            for (int v = -SiloDoorBlock.HALF_WIDTH; v <= SiloDoorBlock.HALF_WIDTH; v++) {
                BlockPos p = SiloDoorBlock.cell(this.worldPosition, facing, u, v);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof SiloDoorBlock && s.getValue(SiloDoorBlock.GAP) != gap) {
                    level.setBlock(p, s.setValue(SiloDoorBlock.GAP, gap), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    // --- drawing (the client) -----------------------------------------------------------------------------------------------------
    /** Where the leaves are (0 shut .. TRAVEL open) at {@code time}, from where they last set off. */
    private float positionAt(double time) {
        if (this.stuck) {
            return this.position;
        }
        double moved = time - this.since;
        return (float) Mth.clamp(this.wanted ? this.from + moved : this.from - moved, 0, TRAVEL);
    }

    /** How far apart the leaves are (0 shut .. 1 open), eased in and out. */
    public float opening(float partialTick) {
        return this.level == null ? 0 : eased(positionAt(this.level.getGameTime() + partialTick) / TRAVEL);
    }

    /** The two leaves slid apart along the model's x (as GeckoLib has it: negated). */
    @Override
    public Map<String, float[]> boneOffsets(float partialTick) {
        float d = SLIDE * opening(partialTick);
        return d <= 0 ? Map.of() : Map.of("leaf_w", new float[] {d, 0, 0}, "leaf_e", new float[] {-d, 0, 0});
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // --- saving and the client's copy ------------------------------------------------------------------------------------------------
    private void sync() {
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("door_pos", this.position);
        output.putBoolean("wanted", this.wanted);
        output.putBoolean("stuck", this.stuck);
        output.putInt("from", this.from);
        output.putLong("since", this.since);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.position = Mth.clamp(input.getIntOr("door_pos", 0), 0, TRAVEL);
        this.wanted = input.getBooleanOr("wanted", false);
        this.stuck = input.getBooleanOr("stuck", false);
        this.from = input.getIntOr("from", this.position);
        this.since = input.getLongOr("since", 0L);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
