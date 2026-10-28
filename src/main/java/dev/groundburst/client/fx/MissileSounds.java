package dev.groundburst.client.fx;

import dev.groundburst.content.launch.MissileEntity;
import dev.groundburst.registry.GBSounds;
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
 * A cruise missile (slice 9c) roars only while its booster burns; after, its jet whines.
 */
final class MissileSounds {
    /** The speed of sound, blocks a tick. */
    private static final double SOUND = 17.15;

    /** What a following sound is: a rocket's roar, a cruise missile's jet, or a nose coming back down. */
    private enum Kind { ENGINE, JET, REENTRY }

    /** One sound following one missile: its engine, its jet, or its re-entry. */
    private static final class Follow extends AbstractTickableSoundInstance {
        private final MissileEntity missile;
        private final boolean reentry;
        private final Kind kind;
        private final float loudness;
        private double lastDistance = -1;
        private float fade;

        Follow(MissileEntity missile, SoundEvent event, Kind kind, float loudness) {
            super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.missile = missile;
            this.kind = kind;
            this.reentry = kind == Kind.REENTRY;
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
            return !this.missile.isRemoved() && switch (this.kind) {
                case REENTRY -> this.missile.reentry() > 0.03f;
                case JET -> jet(this.missile);
                case ENGINE -> rocket(this.missile);
            };
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

    private static final Int2ObjectMap<Follow> ENGINES = new Int2ObjectOpenHashMap<>(), REENTRIES = new Int2ObjectOpenHashMap<>(),
            JETS = new Int2ObjectOpenHashMap<>();

    private MissileSounds() {}

    /** A rocket burning: a ballistic missile's engine, or a cruise missile's booster (until it drops). */
    private static boolean rocket(MissileEntity missile) {
        return missile.burning() && !jet(missile);
    }

    /** A cruise missile's jet, once its booster has dropped. */
    private static boolean jet(MissileEntity missile) {
        return missile.cruise() && missile.dropped() >= 1;
    }

    /** Each client tick per missile: start its roar, its jet or its re-entry when they begin (each runs and fades by itself). */
    static void tick(MissileEntity missile) {
        var sounds = Minecraft.getInstance().getSoundManager();
        float loudness = switch (missile.body()) {
            case XXL -> 7f;
            case XL -> 5.5f;
            case L -> 4.5f;
            default -> 3.5f;
        };
        Follow engine = ENGINES.get(missile.getId());
        if (rocket(missile) && (engine == null || engine.isStopped())) {
            engine = new Follow(missile, GBSounds.MISSILE_ENGINE.get(), Kind.ENGINE, loudness);
            ENGINES.put(missile.getId(), engine);
            sounds.play(engine);
        }
        Follow jet = JETS.get(missile.getId());
        if (jet(missile) && (jet == null || jet.isStopped())) {
            jet = new Follow(missile, GBSounds.CRUISE_ENGINE.get(), Kind.JET, 2.6f);
            JETS.put(missile.getId(), jet);
            sounds.play(jet);
        }
        Follow reentry = REENTRIES.get(missile.getId());
        if (missile.reentry() > 0.03f && (reentry == null || reentry.isStopped())) {
            reentry = new Follow(missile, GBSounds.MISSILE_REENTRY.get(), Kind.REENTRY, loudness);
            REENTRIES.put(missile.getId(), reentry);
            sounds.play(reentry);
        }
    }

    /** A stage parts from {@code missile} (where it parted). */
    static void staging(MissileEntity missile, Vec3 at) {
        missile.level().playLocalSound(at.x, at.y, at.z, GBSounds.MISSILE_STAGING.get(), SoundSource.BLOCKS, 6f,
                0.9f + missile.level().getRandom().nextFloat() * 0.2f, true);
    }

    /** Forget sounds for missiles that are gone. */
    static void clean() {
        ENGINES.values().removeIf(Follow::isStopped);
        REENTRIES.values().removeIf(Follow::isStopped);
        JETS.values().removeIf(Follow::isStopped);
    }

    static void clear() {
        ENGINES.clear();
        REENTRIES.clear();
        JETS.clear();
    }
}
