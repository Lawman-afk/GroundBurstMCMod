package dev.groundzero.content.launch;

import dev.groundzero.content.multiblock.MultiblockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Launch Complex (DESIGN.md §17.2, Phase 8): the heavy launcher, and the only one that fires nuclear missiles. A
 * 5x5 deck with a flame trench under the missile (the Flame Deflector at its middle) and a service tower 13 high beside
 * it, two umbilical arms reaching in to the missile. Run like a Launch Silo from a Launch Computer, with a 15 s
 * countdown: the umbilicals swing away, the deluge soaks the trench for the last five seconds, and at ignition steam
 * and smoke billow out of the trench's open end.
 */
public class LaunchComplexBlockEntity extends LaunchSiloBlockEntity {
    public static final int COMPLEX_ENERGY = 500_000, COMPLEX_COST = 250_000, COMPLEX_COUNTDOWN = 300;
    /** The deluge runs this many ticks before ignition; smoke keeps pouring out of the trench this long after. */
    public static final int DELUGE_TICKS = 100, SMOKE_TICKS = 70;

    /** With no missile loaded the umbilical arms rest swung clear (as they are left after a launch). */
    private static final com.geckolib.animation.RawAnimation CLEAR = com.geckolib.animation.RawAnimation.begin().thenPlayAndHold("clear");

    private int smoke;

    public LaunchComplexBlockEntity(BlockPos pos, BlockState state) {
        super(GZLaunch.COMPLEX_ENTITY.get(), pos, state, COMPLEX_ENERGY);
    }

    @Override
    public MultiblockDefinition definition() {
        return GZLaunch.COMPLEX;
    }

    @Override
    public boolean firesNuclear() {
        return true;
    }

    @Override
    public int launchCost() {
        return COMPLEX_COST;
    }

    @Override
    protected int countdownTicks() {
        return COMPLEX_COUNTDOWN;
    }

    /** The middle of the deck's top, over the flame trench, where the missile stands. */
    @Override
    public BlockPos padCentre() {
        return definition().cellPos(this.worldPosition, controllerFacing(), 2, 1, 2);
    }

    /** The trench's open end: the deck edge away from the service tower, level with the missile. */
    private Vec3 trenchMouth() {
        BlockPos mouth = definition().cellPos(this.worldPosition, controllerFacing(), 0, 0, 2);
        Direction out = controllerFacing().getCounterClockWise();        // away from the service tower (columns grow clockwise)
        return Vec3.atCenterOf(mouth).add(out.getStepX() * 0.7, 0.3, out.getStepZ() * 0.7);
    }

    @Override
    protected void onCountdown(ServerLevel level, BlockPos pad, int left) {
        if (left <= DELUGE_TICKS) {
            // The deluge: water sprayed into the trench and over the deck round the missile.
            Vec3 c = Vec3.atBottomCenterOf(pad);
            level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.2, c.z, 40, 1.4, 0.2, 1.4, 0.3);
            level.sendParticles(ParticleTypes.FALLING_WATER, c.x, c.y + 0.6, c.z, 12, 1.2, 0.3, 1.2, 0);
            if (left % 20 == 0) {
                level.playSound(null, pad, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 2f, 0.6f);
            }
        }
    }

    @Override
    protected void onLiftOff(ServerLevel level, BlockPos pad) {
        this.smoke = SMOKE_TICKS;
        setChanged();
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        boolean running = super.tickMachine(level);
        if (this.smoke > 0) {
            // Ignition: steam and exhaust smoke billowing out of the trench's open end, and up round the deck.
            this.smoke--;
            Vec3 mouth = trenchMouth();
            Direction out = controllerFacing().getCounterClockWise();
            for (int i = 0; i < 3; i++) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, mouth.x, mouth.y, mouth.z, 0,
                        out.getStepX() * 0.5, 0.06, out.getStepZ() * 0.5, 1);
            }
            level.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y + 0.5, mouth.z, 8, 1.2, 0.8, 1.2, 0.08);
            Vec3 c = Vec3.atBottomCenterOf(padCentre());
            level.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.3, c.z, 10, 2.2, 0.3, 2.2, 0.05);
            running = true;
        }
        return running;
    }

    /**
     * The model shows the loaded missile: a Strategic one is its own long model ("strategic", noses "snose_*"), any other
     * the usual missile scaled by its body.
     */
    @Override
    public java.util.Set<String> hiddenBones() {
        if (!isFormed()) {
            return java.util.Set.of("frame");
        }
        var spec = missile();
        java.util.Set<String> hidden = new java.util.HashSet<>();
        if (spec == null) {
            hidden.add("missile");
            hidden.add("strategic");
            return hidden;
        }
        boolean strategic = spec.body() == dev.groundzero.content.assembly.SizeClass.XL;
        hidden.add(strategic ? "missile" : "strategic");
        String id = spec.warheadId().isEmpty() ? "none" : spec.warheadId();
        String shown = (strategic ? "snose_" : "nose_") + id;
        for (String nose : NOSES) {
            for (String bone : new String[] {nose, "s" + nose}) {
                if (!bone.equals(shown)) {
                    hidden.add(bone);
                }
            }
        }
        return hidden;
    }

    @Override
    public java.util.Map<String, float[]> boneScales() {
        var spec = missile();
        return spec != null && spec.body() == dev.groundzero.content.assembly.SizeClass.XL ? java.util.Map.of() : super.boneScales();
    }

    @Override
    public com.geckolib.animation.RawAnimation idleAnimation() {
        return this.inventory.getItem(SLOT_MISSILE).isEmpty() ? CLEAR : IDLE;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("smoke", this.smoke);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.smoke = input.getIntOr("smoke", 0);
    }
}
