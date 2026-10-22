package dev.groundzero.client.fx;

import dev.groundzero.content.launch.MissileEntity;
import dev.groundzero.registry.GZSounds;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A missile's sound in flight (DESIGN.md §17.5, slice 9a), the client's own and following it: its engine's roar while
 * an engine burns, and the roar of its nose coming back down; each pitched by the missile's speed toward or away from
 * the listener (sound travels 17.15 blocks a tick), fading in and out. A stage dropping is a bang from where it parts.
 */
final class MissileSounds {
    /** The speed of sound, blocks a tick. */
    private static final double SOUND = 17.15;

    /** One sound following one missile: its engine, or its re-entry. */
    private static final class Follow extends AbstractTickableSoundInstance {
        private final MissileEntity missile;
        private final boolean reentry;
        private final float loudness;
        private double lastDistance = -1;
        private float fade;

        Follow(MissileEntity missile, SoundEvent event, boolean reentry, float loudness) {
            super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.missile = missile;
            this.reentry = reentry;
            this.loudness = loudness;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            place();
        }

        private void place() {
            Vec3 p = this.missile.position();
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
        }

        boolean wanted() {
            return !this.missile.isRemoved() && (this.reentry ? this.missile.reentry() > 0.03f : this.missile.burning());
        }

        @Override
        public void tick() {
            this.fade = wanted() ? Math.min(1, this.fade + 0.12f) : this.fade - 0.08f;
            if (this.fade <= 0 && !wanted()) {
                stop();
                return;
            }
            place();
            this.volume = Math.max(0.01f, this.loudness * this.fade * (this.reentry ? 0.4f + 0.6f * this.missile.reentry() : 1));
            // Doppler: higher coming toward the listener, lower going away.
            Vec3 ear = Minecraft.getInstance().gameRenderer.mainCamera().position();
            double d = ear.distanceTo(this.missile.position());
            if (this.lastDistance >= 0) {
                double closing = this.lastDistance - d;
                this.pitch = (float) Math.clamp(1 / (1 - Math.clamp(closing / SOUND, -0.45, 0.45)), 0.65, 1.6) * (this.reentry ? 0.9f : 1);
            }
            this.lastDistance = d;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    private static final Int2ObjectMap<Follow> ENGINES = new Int2ObjectOpenHashMap<>(), REENTRIES = new Int2ObjectOpenHashMap<>();

    private MissileSounds() {}

    /** Each client tick per missile: start its roar or its re-entry when they begin (each runs and fades by itself). */
    static void tick(MissileEntity missile) {
        var sounds = Minecraft.getInstance().getSoundManager();
        float loudness = switch (missile.body()) {
            case XXL -> 7f;
            case XL -> 5.5f;
            case L -> 4.5f;
            default -> 3.5f;
        };
        Follow engine = ENGINES.get(missile.getId());
        if (missile.burning() && (engine == null || engine.isStopped())) {
            engine = new Follow(missile, GZSounds.MISSILE_ENGINE.get(), false, loudness);
            ENGINES.put(missile.getId(), engine);
            sounds.play(engine);
        }
        Follow reentry = REENTRIES.get(missile.getId());
        if (missile.reentry() > 0.03f && (reentry == null || reentry.isStopped())) {
            reentry = new Follow(missile, GZSounds.MISSILE_REENTRY.get(), true, loudness);
            REENTRIES.put(missile.getId(), reentry);
            sounds.play(reentry);
        }
    }

    /** A stage parts from {@code missile} (where it parted). */
    static void staging(MissileEntity missile, Vec3 at) {
        missile.level().playLocalSound(at.x, at.y, at.z, GZSounds.MISSILE_STAGING.get(), SoundSource.BLOCKS, 6f,
                0.9f + missile.level().getRandom().nextFloat() * 0.2f, true);
    }

    /** Forget sounds for missiles that are gone. */
    static void clean() {
        ENGINES.values().removeIf(Follow::isStopped);
        REENTRIES.values().removeIf(Follow::isStopped);
    }

    static void clear() {
        ENGINES.clear();
        REENTRIES.clear();
    }
}
