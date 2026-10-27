package dev.groundzero.content.fallout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import dev.groundzero.GroundZero;
import dev.groundzero.config.GZServerConfig;
import dev.groundzero.content.radiation.RadiationSource;
import dev.groundzero.content.radiation.RadiationSources;
import dev.groundzero.network.GZNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Every fallout plume in a level (DESIGN.md §21.8, slice 12b), as its saved data ({@code fallout_plumes.dat}). Once a
 * second each moves with the {@link Wind} (meandering a little across it), lays a radiation source each time it has
 * come {@link #DEPOSIT_EVERY} of its radius, and lets Fallout Ash fall in loaded chunks under it; when it has gone its
 * reach it is spent. Players in the level are told where the plumes are each second while there are any (for the ash
 * they see falling).
 */
public final class Plumes extends SavedData {
    /** A plume lays a source each time it has come this share of its radius. */
    public static final float DEPOSIT_EVERY = 0.6f;
    /** How far a plume wanders across the wind, as a share of the distance it is carried. */
    private static final float MEANDER = 0.15f;
    static final SavedDataType<Plumes> TYPE = new SavedDataType<>(GroundZero.id("fallout_plumes"), Plumes::new,
            FalloutPlume.CODEC.listOf().xmap(Plumes::new, p -> List.copyOf(p.plumes)));
    private static final Map<ServerLevel, Plumes> BY_LEVEL = new WeakHashMap<>();

    private final List<FalloutPlume> plumes = new ArrayList<>();
    private boolean told;

    private Plumes() {}

    private Plumes(List<FalloutPlume> plumes) {
        this.plumes.addAll(plumes);
    }

    public static Plumes of(ServerLevel level) {
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
    }

    private static void tell(Player player) {
        if (player instanceof ServerPlayer p) {
            PacketDistributor.sendToPlayer(p, Sync.of(of((ServerLevel) p.level()).plumes));
        }
    }

    /**
     * Starts a plume at {@code at}: {@code radius} blocks across at first, its first source {@code peak} RAD/h, its
     * sources halving every {@code halfLife} ticks (the burst's own), {@code reach} blocks downwind.
     */
    public static void start(ServerLevel level, Vec3 at, float radius, float peak, int halfLife, float reach, float ashRate) {
        of(level).add(new FalloutPlume(at.x, at.y, at.z, radius, reach, 0, 0, peak, halfLife, ashRate));
    }

    /** The half-life of a burst whose centre, {@code peak} RAD/h at first, falls safe after {@code lasts} ticks. */
    public static int halfLife(float peak, int lasts) {
        return RadiationSource.lasting(Vec3.ZERO, 1, peak, lasts, 0, GZServerConfig.RADIATION_DECAY_MULTIPLIER.get()).halfLife();
    }

    public void add(FalloutPlume plume) {
        this.plumes.add(plume);
        setDirty();
    }

    public List<FalloutPlume> plumes() {
        return List.copyOf(this.plumes);
    }

    /** For GameTests: drops every plume. */
    public void clear() {
        this.plumes.clear();
        setDirty();
    }

    /** For GameTests: removes the plumes now within {@code within} blocks of {@code centre} (across the ground). */
    public void removeNear(Vec3 centre, double within) {
        if (this.plumes.removeIf(p -> Math.hypot(p.x() - centre.x, p.z() - centre.z) <= within)) {
            setDirty();
        }
    }

    /**
     * One second of every plume. On the GameTest server they stay where they started (tests run side by side, and a
     * plume's ash and fallout would drift onto its neighbours' plots); a test moves its own with {@link #step}.
     */
    void tick(ServerLevel level) {
        if (level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;
        }
        if (this.plumes.isEmpty()) {
            if (this.told) {
                this.told = false;
                PacketDistributor.sendToPlayersInDimension(level, Sync.of(List.of()));
            }
            return;
        }
        Vec3 wind = Wind.of(level).vector();
        RandomSource random = level.getRandom();
        long now = level.getGameTime();
        for (int i = 0; i < this.plumes.size(); i++) {
            this.plumes.set(i, step(level, this.plumes.get(i), wind, random, now));
        }
        this.plumes.removeIf(FalloutPlume::spent);
        setDirty();
        this.told = true;
        PacketDistributor.sendToPlayersInDimension(level, Sync.of(this.plumes));
    }

    /** A second of one plume: carried by {@code wind} (blocks a second), its fallout laid, its ash let fall. */
    public static FalloutPlume step(ServerLevel level, FalloutPlume plume, Vec3 wind, RandomSource random, long now) {
        double across = (random.nextDouble() * 2 - 1) * MEANDER;
        FalloutPlume moved = plume.moved(wind.x - wind.z * across, wind.z + wind.x * across);
        float radius = moved.radius();
        if (moved.sinceDeposit() >= DEPOSIT_EVERY * radius || moved.spent()) {
            float peak = moved.peak0() * moved.share();
            if (peak > RadiationSource.SAFE) {
                RadiationSources.of(level).add(new RadiationSource(new Vec3(moved.x(), moved.y(), moved.z()), radius, peak, moved.halfLife(), now));
            }
            moved = moved.deposited();
        }
        // Ash: a whole number of layers a second, the fraction by chance.
        float rate = moved.ashRate() * moved.share();
        int n = (int) rate + (random.nextFloat() < rate - (int) rate ? 1 : 0);
        for (int k = 0; k < n; k++) {
            double a = random.nextDouble() * Mth.TWO_PI, d = radius * Math.sqrt(random.nextDouble());
            settle(level, Mth.floor(moved.x() + Math.cos(a) * d), Mth.floor(moved.z() + Math.sin(a) * d));
        }
        return moved;
    }

    /** A layer of Fallout Ash on whatever is uppermost at (x, z), if it is loaded and ash can lie there. */
    public static void settle(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, level.getMinY(), z);
        if (!level.isLoaded(column)) {
            return;
        }
        BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        FalloutAshBlock fallout = GZFallout.FALLOUT_ASH.block().get();
        // One layer of ash does not block motion, so it lies at the heightmap's height; a deeper one blocks it and lies just under.
        for (BlockPos p : new BlockPos[] {at, at.below()}) {
            BlockState here = level.getBlockState(p);
            if (here.getBlock() instanceof AshLayerBlock) {
                int layers = here.getValue(AshLayerBlock.LAYERS);
                if (layers < AshLayerBlock.MAX_HEIGHT) {
                    level.setBlock(p, fallout.layers(layers + 1), Block.UPDATE_ALL);
                }
                return;
            }
        }
        BlockState state = fallout.layers(1);
        if (level.getBlockState(at).isAir() && state.canSurvive(level, at)) {
            level.setBlock(at, state, Block.UPDATE_ALL);
        }
    }

    /** Where each plume is (for the falling ash): its centre, the ground under its start, its radius, its strength. */
    public record Cloud(float x, float y, float z, float radius, float share) {
        static final StreamCodec<io.netty.buffer.ByteBuf, Cloud> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Cloud::x, ByteBufCodecs.FLOAT, Cloud::y, ByteBufCodecs.FLOAT, Cloud::z,
                ByteBufCodecs.FLOAT, Cloud::radius, ByteBufCodecs.FLOAT, Cloud::share, Cloud::new);
    }

    /** Every plume in the receiving player's level (empty: none). */
    public record Sync(List<Cloud> clouds) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = GZNetwork.type("fallout_plumes");
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = Cloud.CODEC.apply(ByteBufCodecs.list())
                .map(Sync::new, Sync::clouds).cast();
        /** Client hook (set by the client). */
        public static Consumer<Sync> hook = s -> {};

        static Sync of(List<FalloutPlume> plumes) {
            List<Cloud> clouds = new ArrayList<>();
            for (FalloutPlume p : plumes) {
                clouds.add(new Cloud((float) p.x(), (float) p.y(), (float) p.z(), p.radius(), p.share()));
            }
            return new Sync(clouds);
        }

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }
}
