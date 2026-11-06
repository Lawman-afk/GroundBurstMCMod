package dev.groundburst.content.construction;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Blast Door's hinges' lowest block (DESIGN.md §22.3): it runs the door a tick at a time from shut and bolted
 * ({@code position} 0) to open ({@link #TRAVEL}): first the handwheel turns and draws the bolts ({@link #UNLOCK} ticks),
 * then the leaf swings out ({@link #SWING}); shutting, the same backwards. It lays the leaf's blocks in front of the hinges
 * as it starts to open and takes them away when it has shut, tells its doorway's blocks when the leaf stands clear, plays
 * each stage's sound as it starts, and tells the client where it set off from and when, so it draws the door moving
 * smoothly. The figures are the model script's (tools/assets/blast_door.py).
 */
public class BlastDoorBlockEntity extends BlockEntity implements AnimatedMachine {
    /** Ticks the handwheel takes to draw the bolts (or throw them), and the leaf to swing between shut and open. */
    public static final int UNLOCK = 24, SWING = 46, TRAVEL = UNLOCK + SWING;
    /** How far the leaf turns open (degrees), how far the bolts slide (model pixels), how many turns the wheel makes. */
    static final float OPEN_ANGLE = 90f, BOLT_TRAVEL = 3f, WHEEL_TURNS = 1.25f;
    public static final String T_BLOCKED = GBLang.add(GroundBurst.MODID + ".blast_door.blocked",
            "Something stands where the door would swing: clear two blocks in front of its hinges");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Where the door is: 0 shut and bolted .. UNLOCK bolts drawn .. TRAVEL open. */
    private int position;
    private boolean wanted;
    /** The redstone round it when it last looked (a signal coming on opens it, going off shuts it). */
    private boolean powered;
    /** Where it was and when, when it last set off (for the client to draw it moving). */
    private int from;
    private long since;

    public BlastDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Whether it is open, opening, or not yet bolted shut: its leaf's blocks stand in front of its hinges. */
    public boolean needsSwing() {
        return this.wanted || this.position > 0;
    }

    /** Whether it is opening (or open), not shutting (or shut). */
    public boolean opening() {
        return this.wanted;
    }

    /** Where it is, 0 shut and bolted .. {@link #TRAVEL} open. */
    public int position() {
        return this.position;
    }

    /** A click: opens it if it is shut or shutting, shuts it if it is open or opening. */
    public void toggle(ServerLevel level, @Nullable Player player) {
        setWanted(level, !this.wanted, player);
    }

    /** Looks again at the redstone round its doorway: a signal coming on opens it, one going off shuts it. */
    public void lookAtPower(ServerLevel level) {
        boolean now = BlastDoorBlock.powered(level, this.worldPosition, getBlockState());
        if (now != this.powered) {
            this.powered = now;
            setChanged();
            setWanted(level, now, null);
        }
    }

    private void setWanted(ServerLevel level, boolean open, @Nullable Player player) {
        if (open == this.wanted || !(getBlockState().getBlock() instanceof BlastDoorBlock door)) {
            return;
        }
        if (open) {
            if (!door.swingFree(level, this.worldPosition, getBlockState())) {
                level.playSound(null, this.worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 0.55f);
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable(T_BLOCKED));
                }
                return;
            }
            door.laySwing(level, this.worldPosition, getBlockState());
        }
        this.from = this.position;
        this.since = level.getGameTime();
        this.wanted = open;
        // The sound of the stage it sets off in: the wheel, or the leaf swinging.
        if (open) {
            cue(level, this.position < UNLOCK ? GBSounds.BLAST_DOOR_UNLOCK.get() : GBSounds.BLAST_DOOR_SWING.get());
        } else {
            cue(level, this.position > UNLOCK ? GBSounds.BLAST_DOOR_SWING.get() : GBSounds.BLAST_DOOR_LOCK.get());
        }
        setChanged();
        sync();
    }

    void serverTick(ServerLevel level) {
        int before = this.position;
        this.position = Mth.clamp(this.position + (this.wanted ? 1 : -1), 0, TRAVEL);
        if (this.position == before || !(getBlockState().getBlock() instanceof BlastDoorBlock door)) {
            return;
        }
        if (this.wanted && before < UNLOCK && this.position >= UNLOCK) {
            cue(level, GBSounds.BLAST_DOOR_SWING.get());                // the bolts drawn: the leaf swings out
        } else if (!this.wanted && before > UNLOCK && this.position <= UNLOCK) {
            cue(level, GBSounds.BLAST_DOOR_SEAT.get());                 // the leaf home on its frame, and the wheel throws the bolts
            cue(level, GBSounds.BLAST_DOOR_LOCK.get());
        }
        door.setOpen(level, this.worldPosition, getBlockState(), clear(this.position));
        if (this.position == 0 && !this.wanted) {
            door.clearSwing(level, this.worldPosition, getBlockState());
        }
        setChanged();
    }

    /** Whether the leaf stands clear of the doorway (swung more than half open). */
    static boolean clear(int position) {
        return position > UNLOCK + SWING / 2;
    }

    private void cue(ServerLevel level, SoundEvent sound) {
        level.playSound(null, this.worldPosition.relative(BlastDoorBlock.across(getBlockState())).above(), sound, SoundSource.BLOCKS, 1.2f,
                0.95f + level.getRandom().nextFloat() * 0.1f);
    }

    // --- drawing (the client) -----------------------------------------------------------------------------------------------------
    /** Where it is (0 shut .. TRAVEL open) at {@code time}, from where it last set off. */
    private float positionAt(double time) {
        double moved = time - this.since;
        return (float) Mth.clamp(this.wanted ? this.from + moved : this.from - moved, 0, TRAVEL);
    }

    private static float eased(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** How far the bolts are drawn (0 thrown .. 1 drawn), the wheel turned with them. */
    static float unbolted(float position) {
        return eased(position / UNLOCK);
    }

    /** How far the leaf has swung (0 shut .. 1 open). */
    static float swung(float position) {
        return eased((position - UNLOCK) / SWING);
    }

    /** The leaf turned out about its hinges, the wheels turned (the same shaft through the leaf: opposite ways in their frames). */
    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        if (this.level == null) {
            return Map.of();
        }
        float p = positionAt(this.level.getGameTime() + partialTick);
        float leaf = BlastDoorBlock.hand(getBlockState()) * (float) Math.toRadians(OPEN_ANGLE * swung(p));
        float wheel = (float) (2 * Math.PI * WHEEL_TURNS * unbolted(p));
        return Map.of("leaf", new float[] {0, leaf, 0}, "wheel_f", new float[] {0, wheel, 0}, "wheel_b", new float[] {0, -wheel, 0});
    }

    /** The bolts drawn back toward the gearbox (x as GeckoLib has it: negated). */
    @Override
    public Map<String, float[]> boneOffsets(float partialTick) {
        if (this.level == null) {
            return Map.of();
        }
        float d = BOLT_TRAVEL * unbolted(positionAt(this.level.getGameTime() + partialTick));
        return d <= 0 ? Map.of() : Map.of("bolt_top", new float[] {0, -d, 0}, "bolt_bottom", new float[] {0, d, 0},
                "bolt_side", new float[] {BlastDoorBlock.hand(getBlockState()) * d, 0, 0});
    }

    /** The mirrored door (hinges on the left) is its own model, drawn mirrored, not turned. */
    @Override
    public @Nullable String modelVariant() {
        return BlastDoorBlock.hand(getBlockState()) < 0 ? "blast_door_mirrored" : null;
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
        output.putBoolean("powered", this.powered);
        output.putInt("from", this.from);
        output.putLong("since", this.since);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.position = Mth.clamp(input.getIntOr("door_pos", 0), 0, TRAVEL);
        this.wanted = input.getBooleanOr("wanted", false);
        this.powered = input.getBooleanOr("powered", false);
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
