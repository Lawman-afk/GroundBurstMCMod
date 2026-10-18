package dev.groundzero.client.sound;

import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineBlockEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A machine's work loop: fades in while the machine is ACTIVE and out (then stops) when it idles or is removed. */
public class MachineLoopSound extends AbstractTickableSoundInstance {
    private static final float FADE_PER_TICK = 0.1f;

    private final MachineBlockEntity machine;
    private float level;

    public MachineLoopSound(MachineBlockEntity machine, SoundEvent event) {
        super(event, SoundSource.BLOCKS, RandomSource.create());
        this.machine = machine;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = machine.getBlockPos().getX() + 0.5;
        this.y = machine.getBlockPos().getY() + 0.5;
        this.z = machine.getBlockPos().getZ() + 0.5;
    }

    boolean belongsTo(MachineBlockEntity machine) {
        return this.machine == machine;
    }

    @Override
    public void tick() {
        boolean running = !this.machine.isRemoved()
                && this.machine.getBlockState().hasProperty(MachineBlock.ACTIVE)
                && this.machine.getBlockState().getValue(MachineBlock.ACTIVE);
        this.level = running ? Math.min(1f, this.level + FADE_PER_TICK) : this.level - FADE_PER_TICK;
        if (this.level <= 0) {
            stop();
            return;
        }
        this.volume = this.level;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
