package dev.groundzero.content.machine;

import java.util.function.Consumer;

import dev.groundzero.GroundZero;
import dev.groundzero.content.pipe.PipeBlock;
import dev.groundzero.content.fluid.FluidTankBlock;
import dev.groundzero.content.multiblock.MultiblockControllerBlock;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import dev.groundzero.content.multiblock.PortBlock;
import dev.groundzero.content.power.CableBlock;
import dev.groundzero.content.power.CableNetwork;
import dev.groundzero.registry.GZLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Wrench (DESIGN.md §25), following the common conventions (Mekanism's configurator, Pipez's wrench, Thermal's
 * hammer):
 * <ul>
 * <li>right-click a side of a fluid or item pipe: normal -> pull -> off -> normal (pipe-to-pipe joins: on / off);</li>
 * <li>right-click a side of a cable: connected / off (cables have no push or pull);</li>
 * <li>right-click a machine: turn it a quarter clockwise (not a multiblock controller: its structure fixes it);</li>
 * <li>sneak-right-click a pipe, cable, machine, tank or structure block: pick it up (drops as an item).</li>
 * </ul>
 * The side is the arm you click on, or the face of the pipe's core you click.
 */
public class WrenchItem extends Item {
    public static final String TOOLTIP = GZLang.add(GroundZero.MODID + ".wrench.tooltip",
            "Right-click a pipe or cable side to change its connection, a machine to turn it. Sneak-right-click to pick up.");
    public static final String PIPE_MODE = GZLang.add(GroundZero.MODID + ".wrench.pipe", "Pipe %s side: %s");
    public static final String CABLE_MODE = GZLang.add(GroundZero.MODID + ".wrench.cable", "Cable %s side: %s");
    public static final String NOTHING = GZLang.add(GroundZero.MODID + ".wrench.nothing", "Nothing to connect on the %s side");
    public static final String FIXED = GZLang.add(GroundZero.MODID + ".wrench.fixed", "A structure's controller cannot be turned");
    private static final String MODE_NORMAL = GZLang.add(GroundZero.MODID + ".wrench.mode.normal", "Normal (in and out)");
    private static final String MODE_PULL = GZLang.add(GroundZero.MODID + ".wrench.mode.pull", "Pull (draws out of it)");
    private static final String MODE_OFF = GZLang.add(GroundZero.MODID + ".wrench.mode.off", "Disconnected");
    private static final String MODE_ON = GZLang.add(GroundZero.MODID + ".wrench.mode.on", "Connected");

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        Block block = state.getBlock();
        if (player != null && player.isSecondaryUseActive() && dismantlable(block)) {
            if (!level.isClientSide()) {
                level.destroyBlock(pos, true, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (block instanceof PipeBlock) {
            if (!level.isClientSide()) {
                configurePipe(level, pos, state, side(ctx, pos, true), player);
            }
            return InteractionResult.SUCCESS;
        }
        if (block instanceof CableBlock) {
            if (!level.isClientSide()) {
                configureCable(level, pos, state, side(ctx, pos, false), player);
            }
            return InteractionResult.SUCCESS;
        }
        if (block instanceof MultiblockControllerBlock) {
            if (!level.isClientSide() && player != null) {
                player.sendOverlayMessage(Component.translatable(FIXED).withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResult.SUCCESS;
        }
        if (block instanceof MachineBlock) {
            if (!level.isClientSide()) {
                rotate(level, pos, state);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static boolean dismantlable(Block block) {
        return block instanceof MachineBlock || block instanceof PipeBlock || block instanceof CableBlock
                || block instanceof FluidTankBlock || block instanceof dev.groundzero.content.fluid.CreativeFluidTankBlock || block instanceof MultiblockPartBlock || block instanceof PortBlock;
    }

    /** The side the player means: the arm under the cursor, else the face of the core they clicked. */
    private static Direction side(UseOnContext ctx, BlockPos pos, boolean pipe) {
        Vec3 local = ctx.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        for (Direction d : Direction.values()) {
            VoxelShape arm = pipe ? PipeBlock.arm(d) : CableBlock.arm(d);
            if (arm.bounds().inflate(0.01).contains(local)) {
                return d;
            }
        }
        return ctx.getClickedFace();
    }

    private static void configurePipe(Level level, BlockPos pos, BlockState state, Direction d, Player player) {
        BlockPos other = pos.relative(d);
        BlockState there = level.getBlockState(other);
        PipeBlock.Mode current = PipeBlock.mode(state, d);
        PipeBlock.Mode next;
        if (there.getBlock() instanceof PipeBlock && ((PipeBlock) state.getBlock()).isSameKind(there)) {
            // Pipe-to-pipe: join or split the run, on both pipes.
            next = current == PipeBlock.Mode.DISABLED ? PipeBlock.Mode.NORMAL : PipeBlock.Mode.DISABLED;
            BlockState otherState = there.setValue(PipeBlock.SIDES.get(d.getOpposite()), next);
            level.setBlock(other, otherState, Block.UPDATE_ALL);
            PipeBlock.changed(level, other, otherState);
        } else {
            next = switch (current) {
                case NORMAL -> PipeBlock.Mode.PULL;
                case PULL -> PipeBlock.Mode.DISABLED;
                case DISABLED -> PipeBlock.Mode.NORMAL;
                case NONE -> PipeBlock.Mode.NONE;
            };
            if (next == PipeBlock.Mode.NONE) {
                player.sendOverlayMessage(Component.translatable(NOTHING, d.getSerializedName()));
                return;
            }
        }
        BlockState updated = state.setValue(PipeBlock.SIDES.get(d), next);
        level.setBlock(pos, updated, Block.UPDATE_ALL);
        // Re-enabling a side with nothing there any more settles back to "none".
        level.neighborChanged(pos, state.getBlock(), null);
        PipeBlock.changed(level, pos, level.getBlockState(pos));
        String mode = switch (PipeBlock.mode(level.getBlockState(pos), d)) {
            case NORMAL -> MODE_NORMAL;
            case PULL -> MODE_PULL;
            default -> MODE_OFF;
        };
        player.sendOverlayMessage(Component.translatable(PIPE_MODE, d.getSerializedName(), Component.translatable(mode)));
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.6f);
    }

    private static void configureCable(Level level, BlockPos pos, BlockState state, Direction d, Player player) {
        BlockPos other = pos.relative(d);
        BlockState there = level.getBlockState(other);
        CableBlock.Side current = CableBlock.side(state, d);
        if (current == CableBlock.Side.NONE && !(there.getBlock() instanceof CableBlock)) {
            player.sendOverlayMessage(Component.translatable(NOTHING, d.getSerializedName()));
            return;
        }
        CableBlock.Side next = current == CableBlock.Side.DISABLED ? CableBlock.Side.CONNECTED : CableBlock.Side.DISABLED;
        if (there.getBlock() instanceof CableBlock) {
            level.setBlock(other, there.setValue(CableBlock.SIDES.get(d.getOpposite()), next), Block.UPDATE_ALL);
            level.invalidateCapabilities(other);
        }
        level.setBlock(pos, state.setValue(CableBlock.SIDES.get(d), next), Block.UPDATE_ALL);
        level.neighborChanged(pos, state.getBlock(), null);
        level.invalidateCapabilities(pos);
        CableNetwork.invalidate(level);
        boolean on = CableBlock.side(level.getBlockState(pos), d) == CableBlock.Side.CONNECTED;
        player.sendOverlayMessage(Component.translatable(CABLE_MODE, d.getSerializedName(), Component.translatable(on ? MODE_ON : MODE_OFF)));
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.6f);
    }

    /** A quarter turn clockwise; tall machines turn both halves. Side settings stay with the machine's faces. */
    private static void rotate(Level level, BlockPos pos, BlockState state) {
        BlockPos base = TallMachineBlock.base(pos, state);
        BlockState baseState = level.getBlockState(base);
        Direction facing = baseState.getValue(MachineBlock.FACING).getClockWise();
        level.setBlock(base, baseState.setValue(MachineBlock.FACING, facing), Block.UPDATE_ALL);
        if (baseState.getBlock() instanceof TallMachineBlock) {
            BlockState upper = level.getBlockState(base.above());
            if (upper.is(baseState.getBlock()) && upper.getValue(TallMachineBlock.HALF) == DoubleBlockHalf.UPPER) {
                level.setBlock(base.above(), upper.setValue(MachineBlock.FACING, facing), Block.UPDATE_ALL);
            }
        }
        if (level.getBlockEntity(base) instanceof MachineBlockEntity machine) {
            machine.changeSides(c -> {});            // faces moved: refresh capabilities and connected pipes and cables
        }
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(TOOLTIP).withStyle(ChatFormatting.GRAY));
    }
}
