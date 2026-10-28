package dev.groundburst.client.sound;

import java.util.HashMap;
import java.util.Map;

import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;

/** Starts each machine's work loop when it becomes active; the loop instance fades itself out. */
public final class MachineSounds {
    private static final Map<BlockPos, MachineLoopSound> PLAYING = new HashMap<>();

    private MachineSounds() {}

    public static void tick(MachineBlockEntity machine) {
        SoundEvent sound = machine.loopSound();
        if (sound == null || !machine.getBlockState().hasProperty(MachineBlock.ACTIVE) || !machine.getBlockState().getValue(MachineBlock.ACTIVE)) {
            return;
        }
        BlockPos pos = machine.getBlockPos();
        MachineLoopSound current = PLAYING.get(pos);
        var manager = Minecraft.getInstance().getSoundManager();
        if (current != null && current.belongsTo(machine) && !current.isStopped() && manager.isActive(current)) {
            return;
        }
        if (current != null && !current.isStopped() && manager.isActive(current)) {
            return;   // a loop from a replaced block entity is still fading out
        }
        PLAYING.values().removeIf(MachineLoopSound::isStopped);
        MachineLoopSound loop = new MachineLoopSound(machine, sound);
        PLAYING.put(pos.immutable(), loop);
        manager.play(loop);
    }
}
