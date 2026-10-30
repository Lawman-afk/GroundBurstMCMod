package dev.groundburst.client.sound;

import java.util.HashMap;
import java.util.Map;

import dev.groundburst.content.alarm.AlarmBlockEntity;
import dev.groundburst.content.alarm.AlarmDevice;
import dev.groundburst.registry.GBSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The alarm sounders' sounds (DESIGN.md §18.6): each a loop while its block sounds. A siren's winds up as its motor
 * spins up (pitch and loudness rising) and down as it coasts after, and swells as its turning horn sweeps past you; a
 * klaxon's and a bell's fade in and out quickly. A change of signal (a siren's All Clear) starts the new loop as the old
 * one fades.
 */
public final class AlarmSounds {
    private static final Map<BlockPos, Loop> PLAYING = new HashMap<>();

    private AlarmSounds() {}

    public static void register() {
        AlarmBlockEntity.clientHook = AlarmSounds::tick;
    }

    private static void tick(AlarmBlockEntity be) {
        if (!be.device().sounds() || !be.active()) {
            return;                                                     // a loop playing winds itself down
        }
        BlockPos pos = be.getBlockPos();
        SoundEvent want = soundFor(be);
        var manager = Minecraft.getInstance().getSoundManager();
        Loop current = PLAYING.get(pos);
        if (current != null && !current.isStopped() && manager.isActive(current)) {
            if (current.event == want && current.be == be) {
                return;
            }
            current.release();
        }
        PLAYING.values().removeIf(Loop::isStopped);
        Loop loop = new Loop(be, want);
        PLAYING.put(pos.immutable(), loop);
        manager.play(loop);
    }

    /** What a sounder plays: a siren its signal (or the All Clear, steady), a klaxon its tone, a bell its ring. */
    static SoundEvent soundFor(AlarmBlockEntity be) {
        return switch (be.device()) {
            case SIREN -> be.allClear() || be.option("signal") == 1 ? GBSounds.ALARM_SIREN_STEADY.get() : GBSounds.ALARM_SIREN_WAIL.get();
            case KLAXON -> switch (be.option("tone")) {
                case 1 -> GBSounds.ALARM_WHOOP.get();
                case 2 -> GBSounds.ALARM_EVACUATE.get();
                case 3 -> GBSounds.ALARM_HILO.get();
                case 4 -> GBSounds.ALARM_GENERAL.get();
                default -> GBSounds.ALARM_KLAXON.get();
            };
            default -> GBSounds.ALARM_BELL.get();
        };
    }

    /** One sounder's loop. */
    static final class Loop extends AbstractTickableSoundInstance {
        /** A siren's motor: ticks to spin up, to coast down; a klaxon's and a bell's fade (a share a tick). */
        private static final float SPIN_UP = 40, COAST = 70, FADE = 0.2f;

        final AlarmBlockEntity be;
        final SoundEvent event;
        private final boolean siren;
        private boolean released;
        private float level;

        Loop(AlarmBlockEntity be, SoundEvent event) {
            super(event, SoundSource.BLOCKS, RandomSource.create());
            this.be = be;
            this.event = event;
            this.siren = be.device() == AlarmDevice.SIREN;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.pitch = this.siren ? 0.5f : 1f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.x = be.getBlockPos().getX() + 0.5;
            this.y = be.getBlockPos().getY() + (this.siren ? 1.2 : 0.5);
            this.z = be.getBlockPos().getZ() + 0.5;
        }

        void release() {
            this.released = true;
        }

        @Override
        public void tick() {
            boolean on = !this.released && !this.be.isRemoved() && this.be.active() && soundFor(this.be) == this.event;
            if (this.siren) {
                this.level = on ? Math.min(1, this.level + 1 / SPIN_UP) : this.level - 1 / COAST;
            } else {
                this.level = on ? Math.min(1, this.level + FADE) : this.level - FADE;
            }
            if (this.level <= 0) {
                stop();
                return;
            }
            if (this.siren) {
                // Winding up and down with the motor; louder as the horn swings round toward you.
                this.pitch = 0.5f + 0.5f * this.level;
                float swell = 1;
                if (this.be instanceof AlarmBlockEntity.Animated a) {
                    Vec3 you = Minecraft.getInstance().gameRenderer.mainCamera().position();
                    double toYou = Math.toDegrees(Math.atan2(you.x - this.x, you.z - this.z));
                    double off = Math.toRadians(a.hornYaw() - toYou);
                    double facing = (1 + Math.cos(off)) / 2;
                    swell = (float) (0.4 + 0.6 * facing * facing);
                }
                this.volume = this.level * this.level * swell;
            } else {
                this.volume = this.level;
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
