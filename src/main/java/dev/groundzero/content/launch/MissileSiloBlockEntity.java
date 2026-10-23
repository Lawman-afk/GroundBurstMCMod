package dev.groundzero.content.launch;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.registry.GZLang;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Missile Silo (DESIGN.md §17.2, §17.10; Phase 9 slice 9b): the hardened launcher, dug into the ground. A 5x5 shaft
 * 14 deep lined with Reinforced Concrete, a Flame Deflector in the middle of its floor, and at the top a ring of Hazard
 * Concrete round a Silo Hatch, with this controller in the middle of the ring's front and an Energy Port in the middle
 * of its back. It fires any missile, nuclear and intercontinental ones too. A Launch Computer aims and arms it
 * like any launcher. Its countdown runs 20 s: at T-15 the hatch's doors slide apart (the launch holds at T-1 until they
 * are open); at T-0 the missile is lit at the bottom of the shaft and climbs straight up out of it, the exhaust
 * bursting out of the shaft and the four corner vents; the doors shut again 10 s after. Its screen's DOORS button opens
 * or shuts them at other times, to look at the missile. Shut, the hatch keeps everything out; you can walk on it.
 */
public class MissileSiloBlockEntity extends LaunchSiloBlockEntity {
    public static final int SILO_ENERGY = 500_000, SILO_COST = 250_000, SILO_COUNTDOWN = 400;
    /** The doors start to open this many ticks before ignition (T-15). */
    public static final int DOORS_AT = 300;
    /** The doors: unlatched for half a second, then sliding apart for three (ticks, shut to open). */
    public static final int UNLATCH = 10, DOOR_TICKS = 70;
    /** How far each door leaf slides out to open (model pixels). */
    public static final float SLIDE = 24;
    /** After lift-off the doors stay open this long; smoke keeps pouring out of the silo this long. */
    public static final int CLOSE_AFTER = 200, SMOKE_TICKS = 120;
    /** Blocks over the hatch's middle that must be clear for the missile to climb out. */
    public static final int CLEAR_ABOVE = 6;
    /** Its tail stands this high on the launch ring above the shaft's floor (the model's 3 px). */
    private static final double RING = 3 / 16.0;

    private static final String[] T_DOORS = {
            GZLang.add(GroundZero.MODID + ".silo.doors.shut", "Doors shut"),
            GZLang.add(GroundZero.MODID + ".silo.doors.opening", "Doors opening"),
            GZLang.add(GroundZero.MODID + ".silo.doors.open", "Doors open"),
            GZLang.add(GroundZero.MODID + ".silo.doors.shutting", "Doors shutting")};
    /** The DOORS button's tooltip (the layout's control names it). */
    public static final String T_DOORS_BUTTON = GZLang.add(GroundZero.MODID + ".gui.silo_doors", "Open or shut the silo doors");

    /** The doors: 0 shut .. DOOR_TICKS open, moving a tick at a time toward where they are wanted. */
    private int doorPos;
    private boolean doorsWanted;
    /** When the doors were last sent the other way (game time), and where they were then: clients work out the rest. */
    private long doorSince;
    private int doorFrom;
    /** Opened from the screen (to look in); held open after a launch; smoke still pouring out after a launch. */
    private boolean manualOpen;
    private int holdOpen;
    private int smoke;

    public MissileSiloBlockEntity(BlockPos pos, BlockState state) {
        super(GZLaunch.MISSILE_SILO_ENTITY.get(), pos, state, SILO_ENERGY);
    }

    @Override
    public MultiblockDefinition definition() {
        return GZLaunch.MISSILE_SILO;
    }

    /** Any missile at all, as the Launch Complex (an intercontinental one too, since the user's look at 9b). */
    @Override
    public boolean accepts(MissileSpec spec) {
        return true;
    }

    @Override
    public int launchCost() {
        return SILO_COST;
    }

    @Override
    protected int countdownTicks() {
        return SILO_COUNTDOWN;
    }

    /** The bottom of the shaft, over the Flame Deflector, where the missile stands. */
    @Override
    public BlockPos padCentre() {
        return definition().cellPos(this.worldPosition, controllerFacing(), 2, 1, 2);
    }

    /** The hatch's middle block (the top layer's middle). */
    public BlockPos hatchCentre() {
        return definition().cellPos(this.worldPosition, controllerFacing(), 2, 13, 2);
    }

    @Override
    protected Vec3 launchPoint(BlockPos pad) {
        return Vec3.atBottomCenterOf(pad).add(0, RING, 0);
    }

    /** From its tail on the launch ring up to the ground at the mouth. */
    @Override
    protected double shaftHeight() {
        return this.worldPosition.getY() + 1 - launchPoint(padCentre()).y;
    }

    /** Hold the launch at T-1 until the doors are all the way open. */
    @Override
    protected boolean holdLaunch(ServerLevel level) {
        return this.doorPos < DOOR_TICKS;
    }

    /** Anything solid over the middle of the hatch, in the missile's way out. */
    @Override
    protected boolean blocked() {
        if (this.level == null) {
            return false;
        }
        BlockPos top = hatchCentre();
        for (int k = 1; k <= CLEAR_ABOVE; k++) {
            BlockPos p = top.above(k);
            if (!this.level.getBlockState(p).getCollisionShape(this.level, p).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onLiftOff(ServerLevel level, BlockPos pad) {
        this.holdOpen = CLOSE_AFTER;
        this.smoke = SMOKE_TICKS;
        setChanged();
    }

    // --- the doors --------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        boolean running = super.tickMachine(level);           // the countdown and the lift-off
        if (this.holdOpen > 0) {
            this.holdOpen--;
        }
        int count = countdown();
        boolean want = isFormed() && (this.manualOpen || (count >= 0 && count <= DOORS_AT) || this.holdOpen > 0);
        if (want != this.doorsWanted) {
            this.doorFrom = this.doorPos;
            this.doorSince = level.getGameTime();
            this.doorsWanted = want;
            if (isFormed()) {
                level.playSound(null, hatchCentre().above(), GZSounds.SILO_DOORS.get(), SoundSource.BLOCKS, 4f, want ? 1f : 0.94f);
            }
            sync();
        }
        int before = this.doorPos;
        this.doorPos = Mth.clamp(this.doorPos + (want ? 1 : -1), 0, DOOR_TICKS);
        // The hatch is open (nothing there) from when the leaves start to slide apart until they are back over it.
        boolean open = this.doorPos > UNLATCH;
        if ((before > UNLATCH) != open || (level.getGameTime() + this.worldPosition.hashCode()) % 20 == 0) {
            setHatch(level, open);
        }
        if (this.smoke > 0) {
            this.smoke--;
            smokeOut(level);
            running = true;
        }
        if (this.doorPos != before) {
            setChanged();
            running = true;
        }
        return running;
    }

    /** Sets the hatch's nine blocks open or shut (only those still there, and only if they are not already). */
    private void setHatch(ServerLevel level, boolean open) {
        BlockPos c = hatchCentre();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = c.offset(dx, 0, dz);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof SiloHatchBlock && s.getValue(SiloHatchBlock.OPEN) != open) {
                    level.setBlock(p, s.setValue(SiloHatchBlock.OPEN, open), Block.UPDATE_ALL);
                }
            }
        }
    }

    /**
     * After a launch, once the exhaust's own billows (the client's) thin out: wisps of smoke still rising out of the open
     * shaft and the four corner vents, fewer as it goes.
     */
    private void smokeOut(ServerLevel level) {
        Vec3 c = Vec3.atBottomCenterOf(hatchCentre().above());
        float f = this.smoke / (float) SMOKE_TICKS;
        if (this.smoke % 2 == 0) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.2, c.z, 1 + (int) (2 * f), 0.7, 0.1, 0.7, 0.012);
        }
        if (this.smoke % 4 == 0) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x + sx * 2, c.y + 0.15, c.z + sz * 2, 1, 0.15, 0.05, 0.15, 0.01);
                }
            }
        }
    }

    /** The doors' position (0 shut .. DOOR_TICKS open) at {@code time} (game ticks), from where they were last sent. */
    private float doorPosAt(double time) {
        double moved = time - this.doorSince;
        return (float) Mth.clamp(this.doorsWanted ? this.doorFrom + moved : this.doorFrom - moved, 0, DOOR_TICKS);
    }

    /** How far apart the door leaves are (0 shut .. 1 open), eased in and out (read on the client to draw them). */
    public float doorOpening(float partialTick) {
        if (this.level == null) {
            return 0;
        }
        float t = Mth.clamp((doorPosAt(this.level.getGameTime() + partialTick) - UNLATCH) / (DOOR_TICKS - UNLATCH), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Shut, opening, open or shutting. */
    public int doorState() {
        if (this.doorsWanted) {
            return this.doorPos >= DOOR_TICKS ? 2 : 1;
        }
        return this.doorPos <= 0 ? 0 : 3;
    }

    /** The DOORS button: open or shut the doors (not while a launch has them). */
    @Override
    public boolean handleButton(int button) {
        if (button != 0 || countdown() >= 0 || this.holdOpen > 0 || !isFormed()) {
            return false;
        }
        this.manualOpen = !this.manualOpen;
        setChanged();
        return true;
    }

    /** For GameTests and pictures: open or shut the doors at once (as if they had finished moving). */
    public void setDoorsForTest(boolean open) {
        this.manualOpen = open;
        this.doorPos = open ? DOOR_TICKS : 0;
        this.doorFrom = this.doorPos;
        this.doorsWanted = open;
        this.doorSince = this.level == null ? 0 : this.level.getGameTime();
        if (this.level instanceof ServerLevel server) {
            setHatch(server, open);
        }
        sync();
    }

    private void sync() {
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void revalidate() {
        boolean was = isFormed();
        super.revalidate();
        if (was && !isFormed() && this.level instanceof ServerLevel server) {
            this.manualOpen = false;                           // broken up: the doors (as drawn) are gone with it
            this.doorPos = 0;
            this.doorFrom = 0;
            this.doorsWanted = false;
            setHatch(server, false);
            sync();
        }
    }

    // --- screen -------------------------------------------------------------------------------------------------------------
    private static final int DATA_DOORS = 3;

    @Override
    protected int extraDataCount() {
        return super.extraDataCount() + 1;
    }

    @Override
    protected int getExtraData(int index) {
        return index == DATA_DOORS ? doorState() : super.getExtraData(index);
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        List<Component> lines = new java.util.ArrayList<>(super.readout(menu));
        lines.add(Component.translatable(T_DOORS[Mth.clamp(menu.extra(DATA_DOORS), 0, T_DOORS.length - 1)]));
        return lines;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(dev.groundzero.registry.GZMenus.MISSILE_SILO.get(), containerId, inventory, this,
                dev.groundzero.content.machine.MachineLayouts.MISSILE_SILO, this.data);
    }

    // --- model --------------------------------------------------------------------------------------------------------------
    /**
     * The model shows the loaded missile standing in the shaft: a Strategic one is its own long model ("strategic"), an
     * intercontinental one its own too ("icbm"), any other the usual missile scaled by its body ("missile"), each
     * carrying its warhead's own model (or a bare nose, "nose_none" / "snose_none", with none).
     */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        var spec = missile();
        Set<String> hidden = new java.util.HashSet<>(List.of("missile", "strategic", "icbm"));
        SizeClass body = spec == null ? SizeClass.M : spec.body();
        if (spec != null) {
            hidden.remove(switch (body) {
                case XL -> "strategic";
                case XXL -> "icbm";
                default -> "missile";
            });
        }
        // A bare nose with no warhead (an intercontinental missile always has one); with one, the warhead's own model.
        hidden.add("nose_none");
        hidden.add("snose_none");
        if (spec != null && spec.warheadId().isEmpty()) {
            hidden.remove(body == SizeClass.XL ? "snose_none" : "nose_none");
        }
        return hidden;
    }

    @Override
    public Map<String, float[]> boneScales() {
        var spec = missile();
        return spec != null && (spec.body() == SizeClass.XL || spec.body() == SizeClass.XXL) ? Map.of() : super.boneScales();
    }

    /** The door leaves slid apart (x as GeckoLib has it: negated). */
    @Override
    public Map<String, float[]> boneOffsets(float partialTick) {
        float d = SLIDE * doorOpening(partialTick);
        return d <= 0 ? Map.of() : Map.of("door_l", new float[] {d, 0, 0}, "door_r", new float[] {-d, 0, 0});
    }

    // --- saving -------------------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("door_pos", this.doorPos);
        output.putBoolean("doors_wanted", this.doorsWanted);
        output.putLong("door_since", this.doorSince);
        output.putInt("door_from", this.doorFrom);
        output.putBoolean("manual_open", this.manualOpen);
        output.putInt("hold_open", this.holdOpen);
        output.putInt("smoke", this.smoke);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.doorPos = input.getIntOr("door_pos", 0);
        this.doorsWanted = input.getBooleanOr("doors_wanted", false);
        this.doorSince = input.getLongOr("door_since", 0L);
        this.doorFrom = input.getIntOr("door_from", 0);
        this.manualOpen = input.getBooleanOr("manual_open", false);
        this.holdOpen = input.getIntOr("hold_open", 0);
        this.smoke = input.getIntOr("smoke", 0);
    }
}
