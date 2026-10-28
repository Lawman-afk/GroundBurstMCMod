package dev.groundburst.content.fallout;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.GroundBurst;
import dev.groundburst.network.GBNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The wind (DESIGN.md §21.8, slice 12b): one per dimension, as the level's saved data ({@code wind.dat}), told to its
 * players as it changes. A heading (radians, 0 = south, as the radar's: the way it blows toward) and a speed (blocks a
 * second). Each second it eases toward a target, {@link #TURN} radians and {@link #GUST} blocks a second at most; every
 * 3 to 8 minutes it picks a new target, turned about 40 degrees (never more than 90), as fast as the weather has it:
 * 1 to 3.5 blocks a second in clear weather, 2.5 to 5 in rain, 4 to 8 in a thunderstorm.
 */
public final class Wind extends SavedData {
    public static final float TURN = (float) Math.toRadians(1), GUST = 0.05f;
    private static final Codec<Wind> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("heading").forGetter(w -> w.heading),
            Codec.FLOAT.fieldOf("speed").forGetter(w -> w.speed),
            Codec.FLOAT.fieldOf("target_heading").forGetter(w -> w.targetHeading),
            Codec.FLOAT.fieldOf("target_speed").forGetter(w -> w.targetSpeed),
            Codec.LONG.fieldOf("next_change").forGetter(w -> w.nextChange),
            Codec.BOOL.optionalFieldOf("held", false).forGetter(w -> w.held)
    ).apply(i, Wind::new));
    static final SavedDataType<Wind> TYPE = new SavedDataType<>(GroundBurst.id("wind"), Wind::new, CODEC);
    private static final Map<ServerLevel, Wind> BY_LEVEL = new WeakHashMap<>();

    private float heading, speed, targetHeading, targetSpeed;
    private long nextChange;
    /** For GameTests: the wind stays as set. */
    private boolean held;
    private float sentHeading = Float.NaN, sentSpeed = Float.NaN;

    private Wind() {
        RandomSource random = RandomSource.create();
        this.heading = this.targetHeading = random.nextFloat() * Mth.TWO_PI;
        this.speed = this.targetSpeed = 2;
        this.nextChange = 0;
    }

    private Wind(float heading, float speed, float targetHeading, float targetSpeed, long nextChange, boolean held) {
        this.heading = heading;
        this.speed = speed;
        this.targetHeading = targetHeading;
        this.targetSpeed = targetSpeed;
        this.nextChange = nextChange;
        this.held = held;
    }

    public static Wind of(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, l -> l.getDataStorage().computeIfAbsent(TYPE));
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 0) {
                of(level).tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                BY_LEVEL.remove(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> tell(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> tell(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> tell(event.getEntity()));
    }

    private static void tell(Player player) {
        if (player instanceof ServerPlayer p) {
            Wind w = of((ServerLevel) p.level());
            PacketDistributor.sendToPlayer(p, new Sync(w.heading, w.speed));
        }
    }

    /** Radians, 0 = south (+z): the way it blows toward. */
    public float heading() {
        return this.heading;
    }

    /** Blocks a second. */
    public float speed() {
        return this.speed;
    }

    /** Where the wind carries things in a second, blocks. */
    public Vec3 vector() {
        return vector(this.heading, this.speed);
    }

    public static Vec3 vector(float heading, float speed) {
        return new Vec3(Math.sin(heading) * speed, 0, Math.cos(heading) * speed);
    }

    /** For GameTests: sets the wind and holds it there. */
    public void hold(float heading, float speed) {
        this.heading = this.targetHeading = heading;
        this.speed = this.targetSpeed = speed;
        this.held = true;
        setDirty();
    }

    /** For GameTests: lets the wind change again. */
    public void release() {
        this.held = false;
        setDirty();
    }

    private void tick(ServerLevel level) {
        if (!this.held) {
            long now = level.getGameTime();
            RandomSource random = level.getRandom();
            if (now >= this.nextChange) {
                float turn = Mth.clamp((float) random.nextGaussian() * (float) Math.toRadians(40), (float) -Math.PI / 2, (float) Math.PI / 2);
                this.targetHeading = Mth.wrapDegrees((float) Math.toDegrees(this.heading + turn)) * Mth.DEG_TO_RAD;
                float lo = level.isThundering() ? 4 : level.isRaining() ? 2.5f : 1, hi = level.isThundering() ? 8 : level.isRaining() ? 5 : 3.5f;
                this.targetSpeed = lo + random.nextFloat() * (hi - lo);
                this.nextChange = now + 20L * 60 * (3 + random.nextInt(6));
            }
            float diff = Mth.wrapDegrees((this.targetHeading - this.heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            this.heading = Mth.wrapDegrees((this.heading + Mth.clamp(diff, -TURN, TURN)) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            this.speed += Mth.clamp(this.targetSpeed - this.speed, -GUST, GUST);
            setDirty();
        }
        if (Float.isNaN(this.sentHeading) || Math.abs(Mth.wrapDegrees((this.heading - this.sentHeading) * Mth.RAD_TO_DEG)) > 2
                || Math.abs(this.speed - this.sentSpeed) > 0.1f) {
            this.sentHeading = this.heading;
            this.sentSpeed = this.speed;
            PacketDistributor.sendToPlayersInDimension(level, new Sync(this.heading, this.speed));
        }
    }

    /** The wind in the receiving player's level. */
    public record Sync(float heading, float speed) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = GBNetwork.type("wind");
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Sync::heading, ByteBufCodecs.FLOAT, Sync::speed, Sync::new);
        /** Client hook (set by the client). */
        public static Consumer<Sync> hook = s -> {};

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }
}
