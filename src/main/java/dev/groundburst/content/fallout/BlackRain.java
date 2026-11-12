package dev.groundburst.content.fallout;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBServerConfig;
import dev.groundburst.content.gear.GBArmor;
import dev.groundburst.content.radiation.RadiationSource;
import dev.groundburst.content.radiation.RadiationSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Black rain (DESIGN.md §21.10). After Hiroshima a black rain fell from twenty to forty-five minutes on, for more than two
 * hours: the fireball's updraught and the fires' drew up soot and dust that the air's moisture brought down, black with
 * soot and fine radioactive particles, most of it downwind, in places like mud. Here rain is black where the land is
 * heavily irradiated, and a nuclear ground burst's own cloud rains it:
 * <ul>
 * <li>where it rains (the level raining, a rainy biome, the open sky) and a radiation source's field at the ground is
 * {@link #GREY} RAD/h or more (grey there, black from {@link #BLACK}); over the Nuclear Wasteland while any source still
 * gives it {@link #WASTELAND}; under a fallout plume;</li>
 * <li>the burst's own: a nuclear ground burst's plume rains round its centre, over its radius, from {@link #OWN_FROM}
 * seconds after the burst for {@link #OWN_FOR}, whatever the weather.</li>
 * </ul>
 * Out in it, once a second: fallout washed down ({@link #DOSE} RAD/h at full strength, a suit's radiation protection
 * counts; the dose's own step, {@code Radiation}), acid burns ({@code groundburst:acid}, a chance of {@link #BURN} a
 * second at full strength, a suit's chemical protection counts) and worn armour corroding. Plants under it die (columns
 * at random round each player, {@link #AREA_PER_COLUMN}): leaves to Dead Leaves, crops withered, flowers and grass gone,
 * now and then a grass block to Dead Grass; copper weathers a step. Remembered to heal as {@link #WEAPON}, the nuclear
 * kind. The client draws it ({@code BlackRainFx}) from the same sources and plumes the server sends it.
 */
public final class BlackRain {
    /** Dose rates at the ground, RAD/h: the rain is grey from the first, black from the second. */
    public static final float GREY = 5, BLACK = 200;
    /** Over the Nuclear Wasteland the rain is dark while a source still gives this much there, RAD/h. */
    public static final float WASTELAND = 0.5f;
    /** How black the rain is over the wasteland. */
    public static final float WASTELAND_SHARE = 0.4f;
    /** The burst's own rain: from this many seconds after the burst, for this many. */
    public static final int OWN_FROM = 40, OWN_FOR = 360;
    /** Out in it at full strength: RAD/h of fallout washed down; the acid's chance of a burn a second. */
    public static final float DOSE = 100, BURN = 0.25f;
    /** A worn piece's chance of losing a point of durability a second at full strength (about once a minute). */
    public static final float CORRODE = 1 / 60f;
    /** Plants: a column a second for each this many square blocks round a player, at full strength, out to {@link #REACH}. */
    public static final float AREA_PER_COLUMN = 1024;
    static final int REACH = 48;
    /** Its damage, remembered to heal as the nuclear kind's (DESIGN.md §21.9). */
    public static final ResourceLocation WEAPON = GroundBurst.id("black_rain");

    private BlackRain() {}

    static void register() {
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.LevelTickEvent e) -> {if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            if (((Object) e.level) instanceof ServerLevel level && level.getGameTime() % 20 == 7) {
                tick(level);
            }
        });
    }

    // --- where it falls (shared with the client) --------------------------------------------------------------------------------
    /**
     * How black the rain that falls at (x, y, z) is, 0 (plain rain) to 1, from the level's radiation sources, its plumes
     * and the biome: the most of the field's share, the wasteland's and the plumes'. The burst's own rain is
     * {@link #ownRain}.
     */
    public static float strength(Level level, List<RadiationSource> sources, List<Plumes.Cloud> clouds, double x, double y, double z, long now) {
        float rate = 0;
        for (RadiationSource s : sources) {
            double dx = x - s.center().x, dy = y - s.center().y, dz = z - s.center().z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < (double) s.radius() * s.radius()) {
                rate += s.rateAt(now, Math.sqrt(d2));
            }
        }
        float k = 0;
        if (rate >= GREY) {
            k = 0.25f + 0.75f * Mth.clamp((float) (Math.log(rate / GREY) / Math.log(BLACK / GREY)), 0, 1);
        }
        if (rate >= WASTELAND && k < WASTELAND_SHARE && level.getBiome(BlockPos.containing(x, y, z)).is(Wasteland.BIOME)) {
            k = WASTELAND_SHARE;
        }
        for (Plumes.Cloud c : clouds) {
            double dx = x - c.x(), dz = z - c.z();
            if (dx * dx + dz * dz < (double) c.radius() * c.radius()) {
                k = Math.max(k, 0.3f + 0.7f * c.share());
            }
        }
        return k;
    }

    /** How black the burst's own rain is at (x, z), 0 where none falls (whatever the weather: DESIGN.md §21.10). */
    public static float ownRain(List<Plumes.Cloud> clouds, double x, double z) {
        float k = 0;
        for (Plumes.Cloud c : clouds) {
            if (c.rains() && c.age() >= OWN_FROM && c.age() < OWN_FROM + OWN_FOR) {
                double dx = x - c.x(), dz = z - c.z();
                double r = c.radius();
                double d2 = dx * dx + dz * dz;
                if (d2 < r * r) {
                    // Heaviest under its middle, thinning to its edge, and easing in and out over half a minute.
                    float age = c.age() - OWN_FROM;
                    float ease = Math.min(1, Math.min(age, OWN_FOR - age) / 30f);
                    k = Math.max(k, ease * (0.6f + 0.4f * c.share()) * (float) (1 - 0.5 * d2 / (r * r)));
                }
            }
        }
        return k;
    }

    /** Whether it rains at {@code pos} (the open sky over it, the level raining, a rainy biome there). */
    static boolean raining(Level level, BlockPos pos) {
        if (!level.isRaining() || !level.canSeeSky(pos)) {
            return false;
        }
        return level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.RAIN;
    }

    /** How black the rain falling on {@code pos} is now (0: none, or plain rain), on the server. */
    public static float fallingAt(ServerLevel level, BlockPos pos, List<RadiationSource> sources, List<Plumes.Cloud> clouds) {
        if (!GBServerConfig.BLACK_RAIN.get()) {
            return 0;
        }
        float own = level.canSeeSky(pos) ? ownRain(clouds, pos.getX() + 0.5, pos.getZ() + 0.5) : 0;
        float weather = raining(level, pos) ? strength(level, sources, clouds, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getGameTime()) : 0;
        return Math.max(own, weather);
    }

    // --- what it does -------------------------------------------------------------------------------------------------------------
    private static void tick(ServerLevel level) {
        if (!GBServerConfig.BLACK_RAIN.get()) {
            return;
        }
        List<RadiationSource> sources = RadiationSources.of(level).sources();
        List<Plumes.Cloud> clouds = Plumes.clouds(level);
        if (sources.isEmpty() && clouds.isEmpty()) {
            return;                                        // nowhere irradiated: no black rain anywhere
        }
        boolean harms = GBServerConfig.BLACK_RAIN_HARMS.get();
        RandomSource random = level.getRandom();
        if (harms) {
            List<LivingEntity> out = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (((Object) e) instanceof LivingEntity body && body.isAlive() && !(((Object) e) instanceof Player p && (p.isCreative() || p.isSpectator()))) {
                    out.add(body);
                }
            }
            for (LivingEntity body : out) {
                float k = fallingAt(level, BlockPos.containing(body.getEyePosition()), sources, clouds);
                if (k > 0) {
                    soak(level, body, k, random);
                }
            }
        }
        for (ServerPlayer player : level.players()) {
            if (fallingAt(level, player.blockPosition(), sources, clouds) <= 0 && !anyNear(level, player, sources, clouds)) {
                continue;
            }
            int columns = Mth.floor((float) (Math.PI * REACH * REACH / AREA_PER_COLUMN) + random.nextFloat());
            for (int i = 0; i < columns; i++) {
                int x = player.getBlockX() + random.nextInt(2 * REACH + 1) - REACH, z = player.getBlockZ() + random.nextInt(2 * REACH + 1) - REACH;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                BlockPos at = new BlockPos(x, top, z);
                if (!level.isLoaded(at)) {
                    continue;
                }
                float k = fallingAt(level, at, sources, clouds);
                if (k > 0 && random.nextFloat() < k) {
                    rot(level, x, z, random);
                }
            }
        }
    }

    /** Whether black rain falls anywhere about the player (four points at the edge of their reach), for the plants. */
    private static boolean anyNear(ServerLevel level, ServerPlayer player, List<RadiationSource> sources, List<Plumes.Cloud> clouds) {
        for (int[] d : new int[][] {{REACH, 0}, {-REACH, 0}, {0, REACH}, {0, -REACH}}) {
            int x = player.getBlockX() + d[0], z = player.getBlockZ() + d[1];
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
            if (level.isLoaded(at) && fallingAt(level, at, sources, clouds) > 0) {
                return true;
            }
        }
        return false;
    }

    /** A second out in black rain of strength {@code k}: acid burns and worn armour corroding (the dose is the radiation's step). */
    public static void soak(ServerLevel level, LivingEntity body, float k, RandomSource random) {
        float keptOut = GBArmor.chemicalProtection(body);
        if (random.nextFloat() < BURN * k * (1 - keptOut)) {
            DamageSource acid = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(dev.groundburst.content.fluid.ChemLiquidBlock.ACID));
            body.hurt(acid, 1);
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = body.getItemBySlot(slot);
            if (!worn.isEmpty() && worn.isDamageableItem() && random.nextFloat() < CORRODE * k) {
                worn.hurtAndBreak(1, body, e -> e.broadcastBreakEvent(slot));
            }
        }
    }

    /**
     * The radiation black rain washes onto a body out in it, RAD/h (before its suit: what comes from outside), for the dose's
     * own step ({@code Radiation.rateAt}).
     */
    public static float rate(ServerLevel level, LivingEntity body) {
        if (!GBServerConfig.BLACK_RAIN.get() || !GBServerConfig.BLACK_RAIN_HARMS.get()) {
            return 0;
        }
        List<RadiationSource> sources = RadiationSources.of(level).sources();
        List<Plumes.Cloud> clouds = Plumes.clouds(level);
        if (sources.isEmpty() && clouds.isEmpty()) {
            return 0;
        }
        return DOSE * fallingAt(level, BlockPos.containing(body.getEyePosition()), sources, clouds);
    }

    /**
     * What the rain kills in one column, from the top down: leaves die on the branch (and what is under them is looked at
     * too), crops wither, grass and flowers go, a grass block dies now and then, copper weathers a step; anything else ends it.
     */
    public static void rot(ServerLevel level, int x, int z, RandomSource random) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, level.getMinBuildHeight(), z);
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        dev.groundburst.heal.Scars.Recorder scars = dev.groundburst.heal.Scars.slowRecorder(level, new Vec3(x + 0.5, top, z + 0.5), WEAPON,
                dev.groundburst.heal.HealSettings.Kind.NUCLEAR);
        int flags = Block.UPDATE_ALL;
        for (int y = top; y > top - Withering.DEPTH && y >= level.getMinBuildHeight(); y--) {
            at.setY(y);
            BlockState s = level.getBlockState(at);
            if (s.isAir() || s.is(GBFallout.DEAD_LEAVES.block().get()) || s.is(GBFallout.WITHERED_CROPS.block().get()) || s.is(Blocks.DEAD_BUSH)) {
                continue;
            }
            if (s.is(BlockTags.LEAVES)) {
                scars.set(at, GBFallout.DEAD_LEAVES.block().get().defaultBlockState(), flags);
                continue;
            }
            if (s.is(BlockTags.CROPS)) {
                scars.set(at, GBFallout.WITHERED_CROPS.block().get().defaultBlockState(), flags);
                continue;
            }
            if (s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN) || s.is(BlockTags.FLOWERS)
                    || s.is(BlockTags.SMALL_FLOWERS)) {
                scars.set(at, Blocks.AIR.defaultBlockState(), flags);
                continue;
            }
            if ((s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(Blocks.PODZOL)) && random.nextFloat() < 0.25f) {
                scars.set(at, GBFallout.DEAD_GRASS_BLOCK.block().get().defaultBlockState(), flags);
                return;
            }
            if (((Object) s.getBlock()) instanceof WeatheringCopper copper) {
                copper.getNext(s).ifPresent(next -> scars.set(at, next, flags));
            }
            return;
        }
    }
}
