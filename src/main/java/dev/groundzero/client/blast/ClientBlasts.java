package dev.groundzero.client.blast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.groundzero.blast.BlastPayloads;
import dev.groundzero.client.fx.Spectacle;
import dev.groundzero.registry.GZSounds;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client's view of blasts (DESIGN.md §12-13): what the server said about each running blast (passed on to the
 * {@link Spectacle} for the visuals), and the distance-delayed sound.
 *
 * <p><b>Sound.</b> A blast is heard {@code distance / 17.15} ticks after it goes off (sound covers 343 blocks a
 * second; the flash, in 5c, arrives at once). How it sounds depends on distance measured in its own size
 * ({@code reach}, its damage radius, at least 8): near (under 1.5 reaches) a sharp crack and roar, mid (under 4) a boom
 * and long rumble, far (under 12) a low-passed thud and rolling tail, very far (under 40) only the sub-bass rumble;
 * beyond that nothing. Vanilla would fade a sound out a few dozen blocks away, so the sound is played from a point a
 * few blocks off in the blast's true direction, at a volume from our own curve: it stays directional, and audible
 * kilometres away for the biggest blasts.
 */
public final class ClientBlasts {
    /** Blocks sound travels in a tick (343 m/s, a block being a metre). */
    public static final float SOUND_BLOCKS_PER_TICK = 17.15f;
    /** Bands in reaches: near, mid, far, very far. */
    private static final float[] BANDS = {1.5f, 4f, 12f, 40f};
    private static final float[] BAND_VOLUME = {1.0f, 0.95f, 0.85f, 0.7f};
    /** How far off the listener a blast's sound is placed (in its true direction). */
    private static final double VIRTUAL_DISTANCE = 6;
    /** A sound whose moment has passed by more than this (a late arrival) is not played. */
    private static final int LATE_LIMIT = 40;

    /** A running blast as clients know it. */
    public static final class Blast {
        public final BlastPayloads.Start start;
        public float radius;

        Blast(BlastPayloads.Start start) {
            this.start = start;
        }
    }

    private record Pending(long at, BlastPayloads.Start blast, int band, float volume) {}

    private static final Map<Integer, Blast> ACTIVE = new Int2ObjectOpenHashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private ClientBlasts() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> {
            event.register(BlastPayloads.Start.TYPE, (payload, context) -> started(payload));
            event.register(BlastPayloads.Front.TYPE, (payload, context) -> {
                Blast b = ACTIVE.get(payload.id());
                if (b != null) {
                    b.radius = payload.radius();
                }
                Spectacle.front(payload.id(), payload.radius());
            });
            event.register(BlastPayloads.End.TYPE, (payload, context) -> {
                ACTIVE.remove(payload.id());
                Spectacle.end(payload.id());
            });
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ACTIVE.clear();
            PENDING.clear();
        });
    }

    /** Running blasts, for the effects. */
    public static Iterable<Blast> active() {
        return ACTIVE.values();
    }

    public static @Nullable Blast get(int id) {
        return ACTIVE.get(id);
    }

    private static void started(BlastPayloads.Start start) {
        ACTIVE.put(start.id(), new Blast(start));
        Spectacle.start(start);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 ear = mc.gameRenderer.mainCamera().position();
        double distance = ear.distanceTo(start.center());
        double reach = Math.max(8, start.reach());
        double k = distance / reach;
        int band = 0;
        while (band < BANDS.length && k >= BANDS[band]) {
            band++;
        }
        if (band >= BANDS.length) {
            return;                                        // too far to hear
        }
        double lower = band == 0 ? 0 : BANDS[band - 1];
        double through = (k - lower) / (BANDS[band] - lower);
        float volume = BAND_VOLUME[band] * (float) (1 - 0.35 * through);
        long arrives = start.startTick() + Math.round(distance / SOUND_BLOCKS_PER_TICK);
        if (mc.level.getGameTime() - arrives > LATE_LIMIT) {
            return;                                        // we only heard of it long after the sound went by
        }
        PENDING.add(new Pending(arrives, start, band, volume));
        dev.groundzero.GroundZero.LOGGER.debug("Blast {} ({}) heard {} blocks away: band {}, in {} ticks", start.id(), start.soundSet(),
                Math.round(distance), band, arrives - mc.level.getGameTime());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || PENDING.isEmpty()) {
            return;
        }
        long now = mc.level.getGameTime();
        Vec3 ear = mc.gameRenderer.mainCamera().position();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.at() > now) {
                continue;
            }
            it.remove();
            Vec3 toward = p.blast().center().subtract(ear);
            double d = toward.length();
            Vec3 at = d < 1e-3 ? ear : ear.add(toward.scale(Math.min(d, VIRTUAL_DISTANCE) / d));
            dev.groundzero.GroundZero.LOGGER.debug("Blast {} sound plays (band {})", p.blast().id(), p.band());
            mc.getSoundManager().play(new SimpleSoundInstance(GZSounds.blast(p.blast().soundSet(), p.band()), SoundSource.BLOCKS,
                    p.volume(), 0.94f + RANDOM.nextFloat() * 0.12f, RANDOM, at.x, at.y, at.z));
        }
    }
}
