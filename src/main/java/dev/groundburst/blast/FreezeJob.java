package dev.groundburst.blast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundburst.config.GBServerConfig;
import dev.groundburst.content.effect.EffectSystems;
import dev.groundburst.content.effect.Frost;
import dev.groundburst.content.effect.FrozenEffect;
import dev.groundburst.content.fallout.CryoCrystalBlock;
import dev.groundburst.content.fallout.CryoIceBlock;
import dev.groundburst.content.fallout.CryoSnowBlock;
import dev.groundburst.content.fallout.FrostedBlock;
import dev.groundburst.content.fallout.FrozenPlantBlock;
import dev.groundburst.content.fallout.GBFallout;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * One deep freeze (DESIGN.md §16.16): the cryogenic warhead's heat sink let go where its Cryo ICBM struck.
 * <ul>
 * <li>Its <b>front</b>, a white wall of frozen air, runs out over the land to {@link #radius} in {@link #frontTicks}
 * ({@link Freezes#front}: fast at first, slowing), column by column, nearest first, waiting where the world is still
 * loading. Where it passes, open water freezes to milky {@link CryoIceBlock} (deeper near the middle, ponds frozen
 * solid), lava crusts over to obsidian, fires go out, stone and grass rime over, plants freeze solid and the land, the
 * treetops and the new ice are laid with {@link CryoSnowBlock} (deeper near the middle). Its cold air pools in low
 * ground: it climbs no more than {@link #climb} over where it struck, so hilltops are spared.</li>
 * <li>Every creature it catches freezes solid ({@link FrozenEffect}, from 20 s at its heart's edge to 4 s at its rim)
 * and is hurt by the cold; under a roof, half as much. Within its <b>heart</b> ({@link #core} round where it struck)
 * nothing lives: everything is frozen through and shatters. All through the heart's sphere, underground too, water and
 * lava freeze, fires go out, glass shatters with the cold, cave floors rime and grow crystals.</li>
 * <li><b>Spires</b> of packed ice round a core of blue ice burst out of the ground in the heart as the front passes, a
 * great one at the middle and a ring leaning outward, growing for a second or two (they stay when the rest thaws).</li>
 * <li>Its <b>cold snap</b> then holds the region for {@link #life} ticks ({@link Freezes#cold}): whatever stands in it
 * and can freeze (vanilla's rule: leather keeps the cold out) freezes as in powder snow, faster near the middle and in
 * the open, and once frozen through takes more cold damage than powder snow gives.</li>
 * </ul>
 * Chunks are kept loaded until the front has run out and the spires have grown. Not saved.
 */
final class FreezeJob {
    /** Work its front may do a tick: a column of the land costs a few, one through the heart's sphere a block each. */
    private static final int BUDGET = 24_000;
    /** Frozen at its heart's edge and at its rim, ticks; damage from the cold there (under a roof, half). */
    private static final int FROZEN_INNER = 400, FROZEN_OUTER = 80;
    private static final float HURT_INNER = 12, HURT_OUTER = 2;

    final int netId;
    final ServerLevel level;
    final Vec3 center;
    final float radius, core, climb;
    final int frontTicks, life;
    final long startTick;
    final @Nullable UUID by;
    private final UUID owner = UUID.randomUUID();
    private final BlastChunks chunks;
    private final RandomSource random = RandomSource.create();
    private final int cx, cz;
    /** Every column within its reach, nearest first: distance squared, then dx and dz (each offset by 32768). */
    private final long[] columns;
    private int cursor;
    /** Ticks its front has been free to run (it waits while the ground ahead loads), and how far it has run. */
    private double clock;
    private float front;
    private boolean frontTold;
    private final Set<UUID> caught = new HashSet<>();
    private final List<Spire> spires = new ArrayList<>();
    private boolean released;
    private int shattered;
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
    /**
     * Six waves of differing length and heading over the land (x and z steps, phase each): the snow's drifts, so it lies
     * in swells (after the first look from the air: three regular waves drew petals round the middle).
     */
    private final double[] waves = new double[18];

    FreezeJob(int netId, ServerLevel level, Vec3 center, float radius, int life, @Nullable Entity by) {
        this.netId = netId;
        this.level = level;
        this.center = center;
        this.radius = Math.max(4, radius);
        this.core = Freezes.core(this.radius);
        this.climb = Freezes.climb(this.radius);
        this.frontTicks = Freezes.frontTicks(this.radius);
        this.life = Math.max(this.frontTicks, life);
        this.startTick = level.getGameTime();
        this.by = by == null ? null : by.getUUID();
        this.cx = (int) Math.floor(center.x);
        this.cz = (int) Math.floor(center.z);
        this.chunks = new BlastChunks(level, this.owner, center, this.radius + 2, GBServerConfig.GENERATE_CHUNKS.get(), h -> {});
        int reach = (int) Math.ceil(this.radius);
        LongArrayList list = new LongArrayList();
        double r2 = (double) this.radius * this.radius;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                long d2 = (long) dx * dx + (long) dz * dz;
                if (d2 <= r2) {
                    list.add(d2 << 32 | (long) (dx + 32768) << 16 | (dz + 32768));
                }
            }
        }
        this.columns = list.toLongArray();
        Arrays.sort(this.columns);
        for (int i = 0; i < 6; i++) {
            double a = this.random.nextDouble() * 2 * Math.PI, f = 2 * Math.PI / (8 + 28 * this.random.nextDouble());
            this.waves[3 * i] = Math.cos(a) * f;
            this.waves[3 * i + 1] = Math.sin(a) * f;
            this.waves[3 * i + 2] = this.random.nextDouble() * 2 * Math.PI;
        }
        plan();
    }

    int age() {
        return (int) (this.level.getGameTime() - this.startTick);
    }

    FreezePayloads.Start startPayload() {
        return new FreezePayloads.Start(this.netId, this.center, this.radius, this.core, this.frontTicks, this.life, age(), this.climb);
    }

    /** Its front has run all the way out and its spires have grown (its chunks may go). */
    boolean settled() {
        return this.front >= this.radius && this.spires.stream().allMatch(Spire::grown);
    }

    /** A tick of it; false once its cold snap is over. */
    boolean step() {
        int age = age();
        this.shattered = 0;
        if (this.front < this.radius) {
            advance();
        }
        grow(age);
        if (age % 2 == 0) {
            chill(age);
        }
        if (this.front < this.radius ? age % 5 == 0 : !this.frontTold) {
            this.frontTold = this.front >= this.radius;
            PacketDistributor.sendToPlayersInDimension(this.level, new FreezePayloads.Front(this.netId, this.front));
        }
        boolean settled = settled();
        if (settled && !this.released) {
            this.released = this.chunks.release(16);
        }
        if (age >= this.life && settled && this.released) {
            PacketDistributor.sendToPlayersInDimension(this.level, new FreezePayloads.End(this.netId));
            return false;
        }
        return true;
    }

    /** The level is going: let every chunk go now. */
    void releaseAll() {
        this.chunks.release(Integer.MAX_VALUE);
    }

    private @Nullable Entity source() {
        return this.by == null ? null : this.level.getEntity(this.by);
    }

    // --- the front ----------------------------------------------------------------------------------------------------------
    /** The front runs on a tick (once the ground ahead of it is loaded), freezing every column it passes, and catches whatever it reaches. */
    private void advance() {
        double next = Freezes.front(this.radius, this.frontTicks, this.clock + 1);
        if (!this.chunks.prepare((int) Math.ceil(next), true)) {
            return;                                               // the ground ahead is still loading: wait
        }
        this.clock++;
        int budget = BUDGET;
        while (this.cursor < this.columns.length && budget > 0) {
            long c = this.columns[this.cursor];
            double d = Math.sqrt(c >>> 32);
            if (d > next) {
                break;
            }
            int dx = (int) (c >>> 16 & 0xFFFF) - 32768, dz = (int) (c & 0xFFFF) - 32768;
            budget -= column(this.cx + dx, this.cz + dz, d);
            this.cursor++;
        }
        this.front = this.cursor >= this.columns.length ? this.radius : (float) Math.min(next, Math.sqrt(this.columns[this.cursor] >>> 32));
        catchUp();
    }

    /** One column of the land frozen as the front passes it, {@code d} from the middle; the work it took. */
    private int column(int x, int z, double d) {
        if (!this.level.hasChunk(x >> 4, z >> 4)) {
            return 1;
        }
        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int work = 4;
        if (d <= this.core) {
            work += heart(x, z, d, top);
        }
        if (top - this.center.y <= this.climb) {                  // above where its cold climbs, a hilltop is spared
            surface(x, z, top, 1 - d / this.radius, d <= this.core);
        }
        return work;
    }

    /**
     * The snow's drift at a column: about -1 to 1, swelling and falling over ten to thirty blocks, a little rough block
     * to block (so a drift's edge is not drawn with a compass).
     */
    private double drift(int x, int z) {
        double s = 0;
        for (int i = 0; i < 6; i++) {
            s += Math.sin(x * this.waves[3 * i] + z * this.waves[3 * i + 1] + this.waves[3 * i + 2]);
        }
        long h = x * 3_129_871L ^ z * 116_129_781L;
        h = h * h * 42_317_861L + h * 11L;
        return s / 2.4 + 0.3 * (((h >> 16) & 0xFFFF) / 65_535.0 - 0.5);
    }

    /**
     * The open surface of a column: what stands on it (plants frozen solid, fires out), then the ground (water frozen
     * down a few blocks, lava crusted, stone and grass rimed), then snow over it in smooth drifts, deeper near the middle
     * and patchy toward the rim ({@code k}: 1 at the middle, 0 at the rim), in patches on new ice.
     */
    private void surface(int x, int z, int top, double k, boolean heart) {
        BlockPos.MutableBlockPos p = this.at.set(x, top, z);
        BlockState above = this.level.getBlockState(p);
        boolean snow = above.isAir();
        if (above.getBlock() instanceof BaseFireBlock || above.is(dev.groundburst.content.ordnance.GBOrdnance.NAPALM_FIRE.get())) {
            this.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            snow = true;
        } else if (Frost.plant(above) || above.is(BlockTags.CROPS)) {
            freezePlant(p, above);
        }
        p.setY(top - 1);
        BlockState ground = this.level.getBlockState(p);
        FluidState fluid = ground.getFluidState();
        double drift = drift(x, z);
        double depth = 3.2 * k * k + 1.1 + 0.8 * drift - 0.5 * (1 - k);
        if (fluid.is(FluidTags.WATER)) {
            freezeWater(x, top - 1, z, 1 + (int) Math.round(5 * k * k) + (heart ? 3 : 0));
            depth = drift > 0.25 ? 1 : 0;                        // snow lies on the new ice in patches
        } else if (fluid.is(FluidTags.LAVA)) {
            crust(x, top - 1, z, 1 + (int) Math.round(2 * k));
            depth = 0;
        } else if (Frost.frostable(ground) && this.random.nextFloat() < 0.55f + 0.45f * k) {
            this.level.setBlock(p, (ground.is(Blocks.STONE) ? GBFallout.FROSTED_STONE : GBFallout.FROSTED_GRASS_BLOCK).block().get()
                    .defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        if (snow && depth >= 0.75) {
            int layers = Math.min(6, Math.max(1, (int) Math.round(depth)) + (heart ? 1 : 0));
            BlockState laid = GBFallout.CRYO_SNOW.block().get().defaultBlockState().setValue(CryoSnowBlock.LAYERS, layers);
            p.setY(top);
            if (laid.canSurvive(this.level, p)) {
                this.level.setBlock(p, laid, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * The column's slice through the heart's sphere (underground too, up to the surface): water and lava frozen, fires
     * out, glass shattered by the cold, plants frozen, open stone and grass rimed with now and then a crystal on top. The
     * blocks it looked at.
     */
    private int heart(int x, int z, double d, int top) {
        double h = Math.sqrt(Math.max(0, this.core * this.core - d * d));
        int y0 = Math.max(this.level.getMinY(), (int) Math.floor(this.center.y - h));
        int y1 = Math.min(Math.min(this.level.getMaxY() - 1, (int) Math.ceil(this.center.y + h)), top - 1);
        if (y1 < y0) {
            return 0;
        }
        BlockPos.MutableBlockPos p = this.at;
        boolean airAbove = this.level.getBlockState(p.set(x, y1 + 1, z)).isAir();
        for (int y = y1; y >= y0; y--) {
            p.set(x, y, z);
            BlockState s = this.level.getBlockState(p);
            boolean air = s.isAir();
            if (!air) {
                BlockState ice = frozenWater(s);
                if (ice != null) {
                    this.level.setBlock(p, ice, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                } else if (s.is(Blocks.LAVA)) {
                    this.level.setBlock(p, (s.getFluidState().isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                } else if (s.getBlock() instanceof BaseFireBlock || s.is(dev.groundburst.content.ordnance.GBOrdnance.NAPALM_FIRE.get())) {
                    this.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    air = true;
                } else if (s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES)) {
                    shatterGlass(p, s);
                    air = true;
                } else if (Frost.plant(s) || s.is(BlockTags.CROPS)) {
                    freezePlant(p, s);
                } else if (airAbove && Frost.frostable(s) && this.random.nextFloat() < 0.6f) {
                    this.level.setBlock(p, (s.is(Blocks.STONE) ? GBFallout.FROSTED_STONE : GBFallout.FROSTED_GRASS_BLOCK).block().get()
                            .defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (y < top - 1 && this.random.nextFloat() < 0.035f) {
                        BlockPos up = p.above();
                        BlockState crystal = GBFallout.CRYO_CRYSTAL.block().get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
                        if (crystal.canSurvive(this.level, up)) {
                            this.level.setBlock(up, crystal, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
            airAbove = air;
        }
        return y1 - y0 + 1;
    }

    /** Water (and the plants that grow in it) frozen to cryo ice; null for anything else (a waterlogged block keeps its water). */
    private static @Nullable BlockState frozenWater(BlockState s) {
        FluidState f = s.getFluidState();
        if (!f.is(FluidTags.WATER)) {
            return null;
        }
        CryoIceBlock ice = GBFallout.CRYO_ICE.block().get();
        if (s.is(Blocks.WATER)) {
            return ice.frozen(f.isSource());
        }
        if (s.is(Blocks.BUBBLE_COLUMN) || s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT) || s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS)) {
            return ice.frozen(true);
        }
        return null;
    }

    /** Open water frozen from its surface {@code y} down {@code depth} blocks (or to its bed). */
    private void freezeWater(int x, int y, int z, int depth) {
        BlockPos.MutableBlockPos p = this.at;
        for (int i = 0; i < depth; i++) {
            BlockState ice = frozenWater(this.level.getBlockState(p.set(x, y - i, z)));
            if (ice == null) {
                return;
            }
            this.level.setBlock(p, ice, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** Lava crusted over from its surface {@code y} down {@code depth} blocks: obsidian where it stood still, cobblestone where it ran. */
    private void crust(int x, int y, int z, int depth) {
        BlockPos.MutableBlockPos p = this.at;
        for (int i = 0; i < depth; i++) {
            BlockState s = this.level.getBlockState(p.set(x, y - i, z));
            if (!s.is(Blocks.LAVA)) {
                return;
            }
            this.level.setBlock(p, (s.getFluidState().isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** A plant (or a crop) frozen solid: a frozen plant where one can stand, else gone; a tall plant's top half goes. */
    private void freezePlant(BlockPos.MutableBlockPos p, BlockState s) {
        boolean upper = s.hasProperty(DoublePlantBlock.HALF) && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
        BlockState frozen = GBFallout.FROZEN_PLANT.block().get().defaultBlockState();
        if (!upper && frozen.canSurvive(this.level, p)) {
            this.level.setBlock(p, frozen, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        } else {
            this.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (!upper && s.hasProperty(DoublePlantBlock.HALF)) {
            BlockPos up = p.above();
            if (this.level.getBlockState(up).is(s.getBlock())) {
                this.level.setBlock(up, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    /** Glass in the heart cracks with the cold and falls apart (a few of them heard a tick). */
    private void shatterGlass(BlockPos p, BlockState s) {
        this.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (this.shattered++ < 3) {
            this.level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(s));
            this.level.playSound(null, p, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.2f, 0.8f + 0.3f * this.random.nextFloat());
        }
    }

    // --- creatures ----------------------------------------------------------------------------------------------------------
    private static boolean spared(Entity e) {
        return e instanceof Player p && (p.isCreative() || p.isSpectator()) || e instanceof dev.groundburst.content.launch.MissileEntity;
    }

    private double across(Vec3 p) {
        double dx = p.x - this.center.x, dz = p.z - this.center.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private AABB box(double reach) {
        return new AABB(this.center.x - reach, this.level.getMinY(), this.center.z - reach, this.center.x + reach, this.level.getMaxY(),
                this.center.z + reach);
    }

    /**
     * Whatever the front has reached since the last tick: in its heart frozen through (it shatters: nothing lives there);
     * beyond, frozen solid for a while and hurt by the cold (both less toward its rim, half under a roof). A boss is hurt,
     * not frozen; above where its cold climbs, a creature is passed over.
     */
    private void catchUp() {
        Entity source = source();
        for (LivingEntity e : this.level.getEntitiesOfClass(LivingEntity.class, box(this.front + 1), e -> e.isAlive() && !spared(e))) {
            double d = across(e.position());
            if (d > this.front || this.caught.contains(e.getUUID())) {
                continue;
            }
            this.caught.add(e.getUUID());
            if (e.getY() - this.center.y > this.climb && e.position().distanceTo(this.center) > this.core) {
                continue;
            }
            boolean boss = e.typeHolder().is(Tags.EntityTypes.BOSSES);
            e.damageCooldownTime = 0;
            if (e.position().distanceTo(this.center) <= this.core) {
                DamageSource frozen = this.level.damageSources().source(Freezes.FLASH_FROZEN, null, source);
                if (boss) {
                    e.hurtServer(this.level, frozen, 40);
                } else {
                    Frost.shatter(this.level, e);
                    e.hurtServer(this.level, frozen, 1000);
                }
                continue;
            }
            double k = 1 - (d - this.core) / Math.max(1, this.radius - this.core);      // 1 at the heart's edge, 0 at the rim
            float shelter = this.level.canSeeSky(e.blockPosition()) ? 1 : 0.5f;
            if (!boss) {
                e.addEffect(new MobEffectInstance(EffectSystems.FROZEN, (int) ((FROZEN_OUTER + (FROZEN_INNER - FROZEN_OUTER) * k) * shelter), 0, false, true, true));
                e.setTicksFrozen(e.getTicksRequiredToFreeze() + FrozenEffect.MARK);
            }
            e.hurtServer(this.level, this.level.damageSources().freeze(), (float) (HURT_OUTER + (HURT_INNER - HURT_OUTER) * k * k) * shelter);
        }
    }

    /**
     * Its cold snap (every other tick): whatever stands in the region behind the front, below where its cold climbs, and
     * can freeze (vanilla's rule: a piece of leather keeps it out; nor does it touch what is frozen solid already) frosts
     * over as in powder snow, faster near the middle and in the open; once frozen through, every second it takes cold
     * damage on top of powder snow's.
     */
    private void chill(int age) {
        float bite = Freezes.cold(age, this.life);
        if (bite <= 0) {
            return;
        }
        double reach = Math.min(this.radius, this.front + 2);
        for (LivingEntity e : this.level.getEntitiesOfClass(LivingEntity.class, box(reach), e -> e.isAlive() && !spared(e))) {
            double d = across(e.position());
            if (d > reach || e.getY() - this.center.y > this.climb || !e.canFreeze() || FrozenEffect.iceBound(e)) {
                continue;
            }
            float c = bite * (0.35f + 0.65f * (float) (1 - d / this.radius));
            if (!this.level.canSeeSky(e.blockPosition())) {
                c *= 0.5f;
            }
            int gain = Math.round(3 * c);
            if (gain <= 0) {
                continue;
            }
            // Vanilla thaws a creature out of powder snow 2 a tick: 4 + 2 gain every other tick leaves it 2 gain colder.
            e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze(), e.getTicksFrozen() + 4 + 2 * gain));
            float extra = 2 * c - 0.5f;
            if (age % 20 == 0 && e.isFullyFrozen() && extra > 0) {
                e.hurtServer(this.level, this.level.damageSources().freeze(), extra);
            }
        }
    }

    // --- spires -------------------------------------------------------------------------------------------------------------
    /**
     * A spire of ice: its foot's column, its height and the half-width of its foot, how far it leans out each block it
     * rises, how far from the middle it stands (the front must pass it first) and how long after; its six-sided section
     * turned by {@code turn}.
     */
    private static final class Spire {
        final int x, z;
        final double height, base, leanX, leanZ, turn;
        final float reach;
        final int delay;
        int passed = -1, startAge = -1, foot, layers, total;

        Spire(int x, int z, double height, double base, double leanX, double leanZ, double turn, float reach, int delay) {
            this.x = x;
            this.z = z;
            this.height = height;
            this.base = base;
            this.leanX = leanX;
            this.leanZ = leanZ;
            this.turn = turn;
            this.reach = reach;
            this.delay = delay;
        }

        boolean grown() {
            return this.startAge >= 0 && this.layers >= this.total;
        }

        /** Ticks it takes to grow (a tall one longer): a second or two at full size. */
        int growTicks() {
            return Math.max(8, (int) Math.round(10 + 1.2 * this.height));
        }
    }

    /** The heart's spires: a great one at the middle, straight up, and a ring of smaller ones leaning outward. */
    private void plan() {
        double s = this.core / (Freezes.RADIUS * Freezes.CORE_SHARE);   // 1 at full size
        double root = Math.sqrt(s);
        RandomSource r = this.random;
        this.spires.add(new Spire(this.cx, this.cz, (26 + 8 * r.nextDouble()) * s + 2, (3.6 + r.nextDouble()) * root + 0.6, 0, 0,
                r.nextDouble() * Math.PI, 0, 2));
        int n = Math.clamp(Math.round(12 * s), 2, 14);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * (i + 0.6 * r.nextDouble()) / n;
            double d = this.core * (0.18 + 0.55 * Math.pow(r.nextDouble(), 0.8));
            double h = (12 + 14 * r.nextDouble()) * s * (1 - 0.4 * d / this.core) + 2;
            double b = (1.3 + 1.7 * r.nextDouble()) * root + 0.4;
            double lean = 0.15 + 0.3 * r.nextDouble();
            this.spires.add(new Spire(this.cx + (int) Math.round(Math.cos(a) * d), this.cz + (int) Math.round(Math.sin(a) * d), h, b,
                    Math.cos(a) * lean, Math.sin(a) * lean, r.nextDouble() * Math.PI, (float) d, 2 + r.nextInt(12)));
        }
    }

    /** Each spire the front has passed starts a little after and grows its share of layers this tick. */
    private void grow(int age) {
        for (Spire sp : this.spires) {
            if (sp.grown()) {
                continue;
            }
            if (sp.startAge < 0) {
                if (this.front < sp.reach) {
                    continue;
                }
                if (sp.passed < 0) {
                    sp.passed = age;
                }
                if (age - sp.passed < sp.delay || !this.level.hasChunk(sp.x >> 4, sp.z >> 4)) {
                    continue;
                }
                sp.foot = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.x, sp.z);
                sp.total = 2 + (int) Math.ceil(sp.height);
                sp.startAge = age;
                PacketDistributor.sendToPlayersNear(this.level, null, sp.x + 0.5, sp.foot, sp.z + 0.5, 256,
                        new FreezePayloads.Spire(this.netId, new Vec3(sp.x + 0.5, sp.foot, sp.z + 0.5), (float) sp.height, sp.growTicks()));
            }
            int want = Math.min(sp.total, (int) Math.ceil((age - sp.startAge + 1) * sp.total / (double) sp.growTicks()));
            while (sp.layers < want) {
                layer(sp, sp.layers++);
            }
        }
    }

    /**
     * Layer {@code i} of a spire (from two below the ground up): a six-sided section narrowing to its tip, its axis leaning
     * out as it rises; blue ice through its middle where it is thick, packed ice round it; a cryo crystal on its tip. It
     * bursts up through loose and natural ground, plants and trees, never through anything built (a block entity, a block
     * that is not the land's own).
     */
    private void layer(Spire sp, int i) {
        int y = sp.foot - 2 + i;
        if (y < this.level.getMinY() || y >= this.level.getMaxY()) {
            return;
        }
        double up = Math.max(0, i - 2);
        double u = Math.min(1, up / Math.max(1, sp.height));
        double r = sp.base * Math.pow(1 - u, 0.85) + 0.3;
        double ox = sp.x + 0.5 + sp.leanX * up, oz = sp.z + 0.5 + sp.leanZ * up;
        int span = (int) Math.ceil(r) + 1;
        BlockState packed = Blocks.PACKED_ICE.defaultBlockState(), blue = Blocks.BLUE_ICE.defaultBlockState();
        BlockPos.MutableBlockPos p = this.at;
        int bx = (int) Math.floor(ox), bz = (int) Math.floor(oz);
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                double fx = bx + dx + 0.5 - ox, fz = bz + dz + 0.5 - oz;
                double facet = 0;
                for (int k = 0; k < 6; k++) {
                    double a = sp.turn + k * Math.PI / 3;
                    facet = Math.max(facet, fx * Math.cos(a) + fz * Math.sin(a));
                }
                boolean axis = dx == 0 && dz == 0;
                if (!axis && facet > r) {
                    continue;
                }
                p.set(bx + dx, y, bz + dz);
                BlockState s = this.level.getBlockState(p);
                if (yields(s, p)) {
                    this.level.setBlock(p, r > 1.6 && Math.sqrt(fx * fx + fz * fz) < r * 0.5 ? blue : packed, Block.UPDATE_CLIENTS);
                }
            }
        }
        if (i == sp.total - 1) {                                   // its tip: a crystal of ice on top
            p.set(bx, y + 1, bz);
            BlockState crystal = GBFallout.CRYO_CRYSTAL.block().get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
            if (this.level.getBlockState(p).isAir() && crystal.canSurvive(this.level, p)) {
                this.level.setBlock(p, crystal, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** What a spire bursts up through: air, fluids and what is replaceable, the land's own ground, trees, ice and frozen ground. */
    private boolean yields(BlockState s, BlockPos p) {
        if (s.isAir() || s.liquid() || s.canBeReplaced()) {
            return true;
        }
        if (s.hasBlockEntity() || s.getDestroySpeed(this.level, p) < 0) {
            return false;
        }
        Block b = s.getBlock();
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.ICE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CLAY) || b instanceof FrostedBlock
                || b instanceof CryoIceBlock || b instanceof FrozenPlantBlock || b instanceof CryoSnowBlock || b instanceof CryoCrystalBlock
                || b == Blocks.OBSIDIAN || b == Blocks.COBBLESTONE;
    }
}
