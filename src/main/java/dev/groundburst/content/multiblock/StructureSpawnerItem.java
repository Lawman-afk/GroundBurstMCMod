package dev.groundburst.content.multiblock;

import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Creative-only Structure Spawner (DESIGN.md §23.1): places a complete multiblock against the clicked face, its
 * controller facing the player, and forms it. Refuses if anything solid is in the way. A structure that is dug in (the
 * Missile Silo) goes into the ground instead: its controller takes the place of the block clicked on top, and it
 * digs out whatever is in its way (refusing only at blocks nothing can break, or that hold things).
 */
public class StructureSpawnerItem extends Item {
    public static final String TOOLTIP = GBLang.add(GroundBurst.MODID + ".structure_spawner.tooltip",
            "Creative only. Use on a block to build the complete structure, controller facing you.");
    public static final String BLOCKED = GBLang.add(GroundBurst.MODID + ".structure_spawner.blocked",
            "Not enough room: something is in the way at %s, %s, %s");
    public static final String CREATIVE_ONLY = GBLang.add(GroundBurst.MODID + ".structure_spawner.creative_only",
            "Structure Spawners only work in creative mode");

    private final Supplier<MultiblockDefinition> definition;
    private final boolean dugIn;

    public StructureSpawnerItem(Properties properties, Supplier<MultiblockDefinition> definition) {
        this(properties, definition, false);
    }

    public StructureSpawnerItem(Properties properties, Supplier<MultiblockDefinition> definition, boolean dugIn) {
        super(properties);
        this.definition = definition;
        this.dugIn = dugIn;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!player.hasInfiniteMaterials()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable(CREATIVE_ONLY).withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos controller = this.dugIn && context.getClickedFace() == Direction.UP ? context.getClickedPos()
                : context.getClickedPos().relative(context.getClickedFace());
        Direction facing = context.getHorizontalDirection().getOpposite();
        return spawn(level, controller, facing) ? InteractionResult.SUCCESS_SERVER : blocked(level, player, controller, facing);
    }

    private InteractionResult blocked(Level level, Player player, BlockPos controller, Direction facing) {
        BlockPos at = firstObstruction(level, controller, facing);
        if (at != null) {
            player.sendOverlayMessage(Component.translatable(BLOCKED, at.getX(), at.getY(), at.getZ()).withStyle(ChatFormatting.RED));
        }
        return InteractionResult.FAIL;
    }

    private BlockPos firstObstruction(Level level, BlockPos controller, Direction facing) {
        for (MultiblockDefinition.Cell cell : this.definition.get().cells(controller, facing)) {
            BlockState state = level.getBlockState(cell.pos());
            boolean inTheWay = this.dugIn ? state.getDestroySpeed(level, cell.pos()) < 0 || level.getBlockEntity(cell.pos()) != null
                    : !state.canBeReplaced();
            if (inTheWay) {
                return cell.pos();
            }
        }
        return null;
    }

    /** Places and forms the structure; false (and nothing placed) if a cell is obstructed. */
    public boolean spawn(Level level, BlockPos controller, Direction facing) {
        if (firstObstruction(level, controller, facing) != null) {
            return false;
        }
        MultiblockDefinition def = this.definition.get();
        for (MultiblockDefinition.Cell cell : buildOrder(def, controller, facing)) {
            BlockState state;
            if (cell.key() == '.') {
                state = Blocks.AIR.defaultBlockState();
            } else {
                state = def.legend().get(cell.key()).preview().get();
                if (cell.key() == MultiblockDefinition.CONTROLLER && state.hasProperty(MachineBlock.FACING)) {
                    state = state.setValue(MachineBlock.FACING, facing);
                }
            }
            level.setBlock(cell.pos(), state, Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(controller) instanceof MultiblockController c) {
            c.revalidate();
        }
        return true;
    }

    /**
     * The cells in the order to set them: as laid out (bottom layer first), but the edges of any 3x3 spread (a Silo
     * Hatch, a Large Radar Dish) after everything else, so each finds its middle already there when its neighbours
     * change (an edge without one takes itself away).
     */
    public static java.util.List<MultiblockDefinition.Cell> buildOrder(MultiblockDefinition def, BlockPos controller, Direction facing) {
        java.util.List<MultiblockDefinition.Cell> first = new java.util.ArrayList<>(), last = new java.util.ArrayList<>();
        for (MultiblockDefinition.Cell cell : def.cells(controller, facing)) {
            boolean edge = cell.key() != '.' && cell.key() != MultiblockDefinition.CONTROLLER
                    && def.legend().get(cell.key()).preview().get() instanceof BlockState s && s.getBlock() instanceof SpreadPartBlock
                    && s.getValue(SpreadPartBlock.PART) == SpreadPartBlock.Part.EDGE;
            (edge ? last : first).add(cell);
        }
        first.addAll(last);
        return first;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(TOOLTIP).withStyle(ChatFormatting.GRAY));
    }
}
