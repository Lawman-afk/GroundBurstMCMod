package dev.groundzero.content.multiblock;

import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.registry.GZLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import dev.groundzero.content.machine.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A multiblock controller: a machine block that looks like a control cabinet on its own and, once its structure
 * forms, hides itself while its block entity renders the whole structure as one animated model. Formed, it takes its
 * cell's cut ({@link MultiblockPartBlock#CUT}) like the parts, so it can be walked on where the model is a deck.
 */
public class MultiblockControllerBlock extends MachineBlock {
    public static final String STATUS = GZLang.add(GroundZero.MODID + ".multiblock.status",
            "Structure incomplete: %s missing, %s wrong. Sneak-right-click to open the controller.");

    /** False for a structure built of ordinary blocks that stay as they are once formed (the Fission Reactor). */
    private final boolean hidesWhenFormed;

    public MultiblockControllerBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type) {
        this(properties, type, true);
    }

    public MultiblockControllerBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type,
            boolean hidesWhenFormed) {
        super(properties, type, false, Shapes.block());
        this.hidesWhenFormed = hidesWhenFormed;
        registerDefaultState(defaultBlockState().setValue(MultiblockPartBlock.FORMED, false).setValue(MultiblockPartBlock.CUT, PartCut.NONE));
    }

    private boolean hidden(BlockState state) {
        return this.hidesWhenFormed && state.getValue(MultiblockPartBlock.FORMED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MultiblockPartBlock.FORMED, MultiblockPartBlock.CUT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return hidden(state) ? state.getValue(MultiblockPartBlock.CUT).shape() : super.getShape(state, level, pos, context);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return hidden(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return hidden(state) ? Shapes.empty() : super.getOcclusionShape(state);
    }

    /**
     * Right-clicking an unfinished structure's controller toggles its ghost preview and reports what is missing;
     * once formed (or when sneaking) it opens the machine screen as usual.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(MultiblockPartBlock.FORMED) || player.isSecondaryUseActive()) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (level.isClientSide()) {
            MultiblockClientHooks.togglePreview.accept(pos);
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MultiblockController controller) {
            controller.revalidate();
            if (controller.isFormed()) {
                return super.useWithoutItem(level.getBlockState(pos), level, pos, player, hit);
            }
            Component problem = controller.problem(level);
            if (problem != null) {
                player.sendOverlayMessage(problem.copy().withStyle(ChatFormatting.YELLOW));
            } else {
                List<MultiblockDefinition.Mismatch> wrong = controller.mismatches(level);
                long missing = wrong.stream().filter(MultiblockDefinition.Mismatch::empty).count();
                player.sendOverlayMessage(Component.translatable(STATUS, missing, wrong.size() - missing).withStyle(ChatFormatting.YELLOW));
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MultiblockController controller) {
            controller.revalidate();
        }
    }
}
