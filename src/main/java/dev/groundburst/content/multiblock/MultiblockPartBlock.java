package dev.groundburst.content.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A structural multiblock block (casing, reactor glass, ...). Placing or breaking one tells nearby controllers to
 * re-check. While its structure is formed it is invisible (the controller draws one model for the whole thing),
 * stops occluding its neighbours, and right-clicking it opens the controller. A formed part on a structure's sloped
 * edge has a corner cut away ({@link #CUT}) so its outline and collision follow the formed model.
 */
public class MultiblockPartBlock extends Block {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    /** Set by the controller while formed (see MultiblockDefinition#cuts); NONE otherwise. */
    public static final EnumProperty<PartCut> CUT = EnumProperty.create("cut", PartCut.class);

    public MultiblockPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FORMED, false).setValue(CUT, PartCut.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, CUT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FORMED) ? state.getValue(CUT).shape() : super.getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, net.minecraft.world.level.BlockGetter level_, BlockPos pos_) {
        return state.getValue(FORMED) ? Shapes.empty() : super.getOcclusionShape(state, level_, pos_);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && !movedByPiston) {           // moved with its structure: its controller sees to it
            Multiblocks.notifyChanged(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level_l, BlockPos pos, BlockState newState_, boolean movedByPiston) { super.onRemove(state, level_l, pos, newState_, movedByPiston); if (!(level_l instanceof net.minecraft.server.level.ServerLevel level) || state.is(newState_.getBlock())) return;
        
        if (!movedByPiston) {                                 // carried off with its structure (a Create contraption, a ship)
            Multiblocks.notifyChanged(level, pos);
        }
    }

    /** Buckets on any formed part pour into (or sneaking, fill from) the controller's tanks. */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!state.getValue(FORMED)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return dev.groundburst.content.machine.MachineBlock.useFluidContainer(state, level, pos, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(FORMED)) {
            return InteractionResult.PASS;
        }
        MultiblockController controller = Multiblocks.formedControllerOf(level, pos);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        BlockPos c = controller.controllerPos();
        return level.getBlockState(c).useWithoutItem(level, player, hit.withPosition(c));
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? 1.0f : super.getShadeBrightness(state, level, pos);
    }
}
