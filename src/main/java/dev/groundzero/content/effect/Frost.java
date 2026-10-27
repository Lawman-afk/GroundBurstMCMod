package dev.groundzero.content.effect;

import org.jspecify.annotations.Nullable;

import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import dev.groundzero.content.fallout.GZFallout;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Freezing (DESIGN.md §16.4): a burst of cold that freezes water to ice (packed ice near its middle), lava to obsidian
 * (flowing lava to cobblestone), puts out fire, frosts open ground with snow, and freezes every living thing in it
 * solid ({@link FrozenEffect}): it cannot move, and a mob of 40 health or less hit hard (6 or more) while frozen shatters.
 * Bosses are not frozen. Since slice 12e it leaves frozen ground (DESIGN.md §21.7): stone and grass open to the air rimed
 * (Frosted Stone and Frosted Grass, surer near its middle), plants frozen solid, and ice crystals grown round its middle;
 * all of it thaws where snow would not lie.
 */
public final class Frost {
    /** Damage that shatters a frozen mob, and the most health a mob can have to shatter. */
    public static final float SHATTER_DAMAGE = 6, SHATTER_MAX_HEALTH = 40;

    private Frost() {}

    public static void freeze(ServerLevel level, Vec3 centre, float radius, int ticks, @Nullable Entity by) {
        int r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(centre);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        var random = level.getRandom();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > radius * radius) {
                        continue;
                    }
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    var fluid = s.getFluidState();
                    if (fluid.is(FluidTags.WATER) && (s.is(Blocks.WATER) || s.canBeReplaced())) {
                        level.setBlockAndUpdate(p, (d2 < radius * radius * 0.12 ? Blocks.PACKED_ICE : Blocks.ICE).defaultBlockState());
                    } else if (fluid.is(FluidTags.LAVA) && s.is(Blocks.LAVA)) {
                        level.setBlockAndUpdate(p, (fluid.isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState());
                    } else if (s.getBlock() instanceof BaseFireBlock || s.is(dev.groundzero.content.ordnance.GZOrdnance.NAPALM_FIRE.get())) {
                        level.removeBlock(p, false);
                    } else if (frostable(s) && open(level, p) && random.nextFloat() < 1 - 0.4f * d2 / (radius * radius)) {
                        level.setBlockAndUpdate(p, (s.is(Blocks.STONE) ? GZFallout.FROSTED_STONE : GZFallout.FROSTED_GRASS_BLOCK)
                                .block().get().defaultBlockState());
                    } else if (plant(s)) {
                        BlockState frozen = GZFallout.FROZEN_PLANT.block().get().defaultBlockState();
                        if (s.hasProperty(DoublePlantBlock.HALF) && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        } else if (frozen.canSurvive(level, p)) {
                            level.setBlock(p, frozen, Block.UPDATE_ALL);
                        }
                    } else if (s.isAir() && random.nextFloat() < 0.8f) {
                        BlockPos below = p.below();
                        BlockState under = level.getBlockState(below);
                        BlockState snow = Blocks.SNOW.defaultBlockState();
                        if (!under.isAir() && under.isFaceSturdy(level, below, Direction.UP) && snow.canSurvive(level, p)) {
                            level.setBlockAndUpdate(p, snow);
                        }
                    }
                }
            }
        }
        crystals(level, c, radius, random);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius))) {
            if (e.position().distanceTo(centre) > radius || e.typeHolder().is(Tags.EntityTypes.BOSSES) || e.isSpectator()
                    || e instanceof Player pl && pl.isCreative()) {
                continue;
            }
            e.addEffect(new MobEffectInstance(EffectSystems.FROZEN, ticks, 0, false, true, true));
            e.setTicksFrozen(e.getTicksRequiredToFreeze() + FrozenEffect.MARK);
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, centre.x, centre.y + 1, centre.z, (int) (40 * radius), radius * 0.4, 1, radius * 0.4, 0.15);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), centre.x, centre.y + 0.5, centre.z,
                (int) (12 * radius), radius * 0.3, 0.6, radius * 0.3, 0.2);
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y + 0.5, centre.z, (int) (10 * radius), radius * 0.35, 0.5, radius * 0.35, 0.02);
        level.playSound(null, centre.x, centre.y, centre.z, GZSounds.CRYO_BURST.get(), SoundSource.BLOCKS, 2f, 1f);
    }

    /** Ground the frost rimes: stone, and vanilla's grass. */
    static boolean frostable(BlockState s) {
        return s.is(Blocks.STONE) || s.is(Blocks.GRASS_BLOCK);
    }

    /** Small plants it freezes solid: grass, ferns, flowers, saplings (a tall plant's upper half just goes). */
    static boolean plant(BlockState s) {
        return s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
                || s.is(BlockTags.SMALL_FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.BUSH);
    }

    /** Open to the air above (or under the snow it lays). */
    private static boolean open(ServerLevel level, BlockPos p) {
        BlockState above = level.getBlockState(p.above());
        return above.isAir() || above.is(Blocks.SNOW) || plant(above);
    }

    /**
     * Ice crystals grown round its middle: a few clusters on the floor and walls within half its radius (more for a
     * bigger burst), each on a face that can hold it.
     */
    static void crystals(ServerLevel level, BlockPos c, float radius, net.minecraft.util.RandomSource random) {
        int want = Math.max(2, Math.round(radius * 1.2f));
        BlockState crystal = GZFallout.CRYO_CRYSTAL.block().get().defaultBlockState();
        float reach = Math.max(1.5f, radius * 0.5f);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int tries = 0; tries < want * 8 && want > 0; tries++) {
            at.set(c.getX() + Math.round((random.nextFloat() * 2 - 1) * reach), c.getY() + random.nextInt(3) - 1,
                    c.getZ() + Math.round((random.nextFloat() * 2 - 1) * reach));
            for (int down = 0; down < 4 && level.getBlockState(at.below()).isAir(); down++) {
                at.move(Direction.DOWN);                    // settle onto the floor
            }
            BlockState here = level.getBlockState(at);
            if (!here.isAir() && !here.is(Blocks.SNOW)) {
                continue;
            }
            for (Direction face : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                BlockState placed = crystal.setValue(AmethystClusterBlock.FACING, face);
                if (placed.canSurvive(level, at)) {
                    level.setBlockAndUpdate(at, placed);
                    want--;
                    break;
                }
            }
        }
    }

    /** A frozen mob hit hard enough breaks apart (shards of ice, the sound of breaking glass). */
    static void shatter(ServerLevel level, LivingEntity e) {
        Vec3 at = e.position().add(0, e.getBbHeight() / 2, 0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), at.x, at.y, at.z, 60,
                e.getBbWidth() * 0.4, e.getBbHeight() * 0.35, e.getBbWidth() * 0.4, 0.25);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.7f);
    }
}
