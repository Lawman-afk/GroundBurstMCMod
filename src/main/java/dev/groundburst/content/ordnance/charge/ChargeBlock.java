package dev.groundburst.content.ordnance.charge;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A placed charge (DESIGN.md §9.4, §14): safe when placed, armed from its screen (right-click it with an empty hand;
 * an armed charge blinks a red lamp), set off by its fuze (a Remote Detonator on its frequency, or its timer), by
 * redstone once armed, or by another blast whatever its state. Right-clicking a timed bomb that is counting down
 * opens its wires instead, to try to defuse it. {@code FACING} points out of the face it is stuck to; the timed bomb faces its front.
 */
public class ChargeBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    /** C4 only: how many are stacked on the spot (1-4). */
    public static final IntegerProperty COUNT = BlockStateProperties.CANDLES;
    /** Thermite only: burning. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    // Messages for arming and making safe (here, not in the packet class, so the language data always has them).
    public static final String MSG_ARMED = dev.groundburst.registry.GBLang.add(dev.groundburst.GroundBurst.MODID + ".charge.armed", "Armed: frequency %s");
    public static final String MSG_ARMED_TIMER = dev.groundburst.registry.GBLang.add(dev.groundburst.GroundBurst.MODID + ".charge.armed_timer",
            "Armed: frequency %s, %s s timer");
    public static final String MSG_ARMED_MINE = dev.groundburst.registry.GBLang.add(dev.groundburst.GroundBurst.MODID + ".charge.armed_mine", "Armed: live");
    public static final String MSG_SAFE = dev.groundburst.registry.GBLang.add(dev.groundburst.GroundBurst.MODID + ".charge.safe", "Safe");
    public static final String MSG_MUST_DEFUSE = dev.groundburst.registry.GBLang.add(dev.groundburst.GroundBurst.MODID + ".charge.must_defuse",
            "It is counting down: cut the right wire to stop it");

    /** Client hook: opens the settings or defuse screen for the charge at a position (set by the client). */
    public static Consumer<BlockPos> screenHook = p -> {};

    public final ChargeKind kind;

    public ChargeBlock(ChargeKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        BlockState s = this.stateDefinition.any().setValue(FACING, kind.anyFace ? Direction.UP : Direction.NORTH).setValue(ARMED, false);
        if (s.hasProperty(COUNT)) {
            s = s.setValue(COUNT, 1);
        }
        if (s.hasProperty(LIT)) {
            s = s.setValue(LIT, false);
        }
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ARMED);
    }

    /** The block it is stuck to (or, for the timed bomb, standing on) is this way. */
    public Direction attached(BlockState state) {
        return this.kind.anyFace ? state.getValue(FACING).getOpposite() : Direction.DOWN;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = defaultBlockState().setValue(FACING, this.kind.anyFace ? context.getClickedFace()
                : this.kind.facesAway() ? context.getHorizontalDirection() : context.getHorizontalDirection().getOpposite());
        return s.canSurvive(context.getLevel(), context.getClickedPos()) ? s : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction to = attached(state);
        BlockPos on = pos.relative(to);
        return level.getBlockState(on).isFaceSturdy(level, on, to.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        // A burning thermite charge stays where it is while it eats away what it was stuck to.
        boolean burning = state.hasProperty(LIT) && state.getValue(LIT);
        return direction == attached(state) && !burning && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** Known to the Remote Detonator from the moment it is placed. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level instanceof ServerLevel server) {
            Charges.track(server, pos);
        }
    }

    // --- shapes ----------------------------------------------------------------------------------------------------------
    /** The shape lying on the floor (16 = a block), turned to face the way it points. */
    protected VoxelShape floorShape(BlockState state) {
        return switch (this.kind) {
            case C4 -> Block.box(3, 0, 5, 13, 2.5 * state.getValue(COUNT) + 1.2, 11);
            case BREACHING -> Block.box(1, 0, 1, 15, 2, 15);
            case THERMITE -> Block.box(4.5, 0, 4.5, 11.5, 8, 11.5);
            case TIMED_BOMB -> Block.box(1, 0, 2, 15, 11, 14);
            case LANDMINE -> Block.box(3, 0, 3, 13, 1.5, 13);
            case BOUNCING_MINE -> Block.box(4, 0, 4, 12, 2.5, 12);
            case CLAYMORE -> Block.box(2, 0, 6.6, 14, 7, 9.7);
            // Warheads stand nose up on their mounting rings (the tall ones run on above their block): each box is the
            // widest of its ring and body (tools/assets/warheads.py) and as tall as it stands, up to its block's top.
            case HE_WARHEAD -> Block.box(3.25, 0, 3.25, 12.75, 16, 12.75);
            case NAPALM_WARHEAD -> Block.box(3, 0, 3, 13, 14, 13);
            case TACTICAL_NUCLEAR_WARHEAD -> Block.box(3.3, 0, 3.3, 12.7, 15, 12.7);
            case FISSION_WARHEAD -> Block.box(1.1, 0, 1.1, 14.9, 16, 14.9);
            case BOOSTED_FISSION_WARHEAD -> Block.box(1.7, 0, 1.7, 14.3, 16, 14.3);
            case NEUTRON_WARHEAD -> Block.box(4.1, 0, 4.1, 11.9, 16, 11.9);
            case DIRTY_WARHEAD -> Block.box(2.4, 0, 2.4, 13.6, 14, 13.6);
            case THERMONUCLEAR_WARHEAD, COBALT_WARHEAD -> Block.box(1, 0, 1, 15, 16, 15);
            case TSAR_WARHEAD -> Block.box(0.6, 0, 0.6, 15.4, 16, 15.4);
            case SCUD_STORM_WARHEAD -> Block.box(3, 0, 3, 13, 16, 13);
            case NUCLEAR_MISSILE_WARHEAD -> Block.box(1.5, 0, 1.5, 14.5, 16, 14.5);
            case ANTIMATTER_WARHEAD -> Block.box(2.8, 0, 2.8, 13.2, 16, 13.2);
            case SINGULARITY_WARHEAD -> Block.box(1.5, 0, 1.5, 14.5, 16, 14.5);
            case STORM_WARHEAD -> Block.box(1.5, 0, 1.5, 14.5, 16, 14.5);
            case CRYOGENIC_WARHEAD -> Block.box(1.2, 0, 1.2, 14.8, 16, 14.8);
            case THERMOBARIC_WARHEAD -> Block.box(1.7, 0, 1.7, 14.3, 15, 14.3);
            case TOXIC_WASTE_WARHEAD -> Block.box(2.4, 0, 2.4, 13.6, 15, 13.6);
            case CLUSTER_WARHEAD, GAS_WARHEAD, SMOKE_WARHEAD, CRYO_WARHEAD, EMP_WARHEAD -> Block.box(2.7, 0, 2.7, 13.3, 16, 13.3);
            case CHEMICAL_WARHEAD -> Block.box(3.1, 0, 3.1, 12.9, 16, 12.9);
            case BUNKER_BUSTER -> Block.box(4.7, 0, 4.7, 11.3, 16, 11.3);
            case HEAVY_WARHEAD -> Block.box(1.25, 0, 1.25, 14.75, 16, 14.75);
            case MASSIVE_WARHEAD -> Block.box(0, 0, 0, 16, 16, 16);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape floor = floorShape(state);
        return this.kind.anyFace ? turn(floor, state.getValue(FACING)) : turnFlat(floor, state.getValue(FACING));
    }

    /** A shape whose front faces north turned to face {@code facing} (as the blockstate turns the model clockwise). */
    static VoxelShape turnFlat(VoxelShape shape, Direction facing) {
        if (facing.getAxis() == Direction.Axis.Y || facing == Direction.NORTH) {
            return shape;
        }
        VoxelShape[] out = {Shapes.empty()};
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double[] a = flat(facing, x0, z0), b = flat(facing, x1, z1);
            out[0] = Shapes.or(out[0], Shapes.box(Math.min(a[0], b[0]), y0, Math.min(a[1], b[1]), Math.max(a[0], b[0]), y1, Math.max(a[1], b[1])));
        });
        return out[0];
    }

    private static double[] flat(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[] {1 - z, x};
            case SOUTH -> new double[] {1 - x, 1 - z};
            case WEST -> new double[] {z, 1 - x};
            default -> new double[] {x, z};
        };
    }

    /** A floor shape turned so its top points {@code facing} (as the blockstate turns the model). */
    static VoxelShape turn(VoxelShape shape, Direction facing) {
        if (facing == Direction.UP) {
            return shape;
        }
        VoxelShape[] out = {Shapes.empty()};
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double[] a = map(facing, x0, y0, z0), b = map(facing, x1, y1, z1);
            out[0] = Shapes.or(out[0], Shapes.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                    Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])));
        });
        return out[0];
    }

    /** Where a point of the floor model ends up (in blocks) when the model is turned to face {@code facing}. */
    private static double[] map(Direction facing, double x, double y, double z) {
        return switch (facing) {
            case DOWN -> new double[] {x, 1 - y, 1 - z};
            case NORTH -> new double[] {x, z, 1 - y};
            case SOUTH -> new double[] {1 - x, z, y};
            case EAST -> new double[] {y, z, x};
            case WEST -> new double[] {1 - y, z, 1 - x};
            default -> new double[] {x, y, z};
        };
    }

    // --- the block entity ------------------------------------------------------------------------------------------------
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != dev.groundburst.content.ordnance.GBOrdnance.CHARGE.get()) {
            return null;
        }
        return level.isClientSide() ? (l, p, s, be) -> ((ChargeBlockEntity) be).clientTick()
                : (l, p, s, be) -> ((ChargeBlockEntity) be).serverTick((ServerLevel) l);
    }

    public static @Nullable ChargeBlockEntity at(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ChargeBlockEntity be ? be : null;
    }

    // --- using it --------------------------------------------------------------------------------------------------------
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (this.kind == ChargeKind.C4 && stack.is(asItem()) && !player.isSecondaryUseActive()) {
            // Stack another block of C4 on this one.
            int n = state.getValue(COUNT);
            if (n >= 4 || state.getValue(ARMED)) {
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(COUNT, n + 1), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 0.9f);
                stack.consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (this.kind.bomb) {
            return InteractionResult.PASS;          // a warhead has no settings of its own: its fuze works in the missile
        }
        if (level.isClientSide()) {
            screenHook.accept(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level instanceof ServerLevel server && state.getValue(ARMED) && server.hasNeighborSignal(pos)) {
            ChargeBlockEntity be = at(level, pos);
            if (be != null) {
                be.trigger(server, null);
            }
        }
    }

    /** Caught in a vanilla explosion (TNT, a creeper): it goes off too, a moment later. */
    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
            java.util.function.BiConsumer<ItemStack, BlockPos> onHit) {
        Charges.caughtInBlast(level, pos, state);
        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    /** Breaking a timed bomb that is counting down sets it off. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        ChargeBlockEntity be = at(level, pos);
        if (level instanceof ServerLevel server && be != null && be.counting() && this.kind == ChargeKind.TIMED_BOMB && !player.isCreative()) {
            Charges.caughtInBlast(server, pos, state);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** A burning thermite charge throws sparks and drips molten slag. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.hasProperty(LIT) || !state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.5,
                    (random.nextDouble() - 0.5) * 0.6);
        }
        level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
        level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, 0, 0.05, 0);
    }

    /** C4: one block can hold up to four stacked. */
    public static class C4 extends ChargeBlock {
        public C4(Properties properties) {
            super(ChargeKind.C4, properties);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(COUNT);
        }
    }

    /** Thermite: lit while it burns. */
    public static class Thermite extends ChargeBlock {
        public Thermite(Properties properties) {
            super(ChargeKind.THERMITE, properties);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(LIT);
        }
    }
}
