package dev.groundburst.compat.create;


import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.content.machine.WideMachineBlock;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Create's contraptions (main's DESIGN.md §17.16): what Create may move of ours, what holds together, and the machines that
 * work while they ride one.
 * <ul>
 * <li><b>Movable</b>: pistons may not push most of our machines and structures (a piston would split them), but Create
 * moves what is glued, chassised or attached together as one, so it may move every block of ours.</li>
 * <li><b>Held together</b>: a formed structure's blocks hold to one another, and a two-block machine's halves to each
 * other, so whatever moves one part of a machine takes all of it.</li>
 * <li><b>Carried</b>: every machine (launchers, assemblers, generators, computers...) runs on the contraption
 * ({@link CarriedMachine}), sharing power and its pipes with the others aboard ({@link Aboard}).</li>
 * <li><b>Opened</b>: right-clicking one (or any part of a formed structure) opens its menu or screen
 * ({@link OpenOnContraption}).</li>
 * </ul>
 */
public final class CreateCompat {
    private CreateCompat() {}

    public static void register() {
        BlockMovementChecks.registerMovementAllowedCheck((state, level, pos) -> ours(state) ? CheckResult.SUCCESS : CheckResult.PASS);
        BlockMovementChecks.registerAttachedCheck(CreateCompat::attached);
        CarriedMachine carried = new CarriedMachine();
        OpenOnContraption open = new OpenOnContraption();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!ours(block.defaultBlockState())) {
                continue;
            }
            boolean machine = machine(block);
            if (machine) {
                MovementBehaviour.REGISTRY.register(block, carried);
            }
            if (machine || block instanceof MultiblockPartBlock || block instanceof TallMachineBlock) {
                com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.register(block, open);
            }
        }
    }

    /** Whether a block of ours is a machine: its block entity is one. */
    private static boolean machine(Block block) {
        return block instanceof net.minecraft.world.level.block.EntityBlock entityBlock
                && entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) instanceof dev.groundburst.content.machine.MachineBlockEntity;
    }

    /** For the in-game check: told after each tick of a launcher a contraption carries. */
    public static void afterCarriedTick(java.util.function.@org.jspecify.annotations.Nullable BiConsumer<net.minecraft.server.level.ServerLevel,
            dev.groundburst.content.machine.MachineBlockEntity> check) {
        CarriedMachine.afterTick = check;
    }

    private static boolean ours(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(GroundBurst.MODID);
    }

    /** Formed: a structure's part or controller while its structure stands whole. */
    private static boolean formed(BlockState state) {
        return ours(state) && state.hasProperty(MultiblockPartBlock.FORMED) && state.getValue(MultiblockPartBlock.FORMED);
    }

    /**
     * Whether our block at {@code pos} holds to its neighbour toward {@code direction}: a formed structure's blocks to
     * each other, a tall machine's halves, a wide machine's halves.
     */
    private static CheckResult attached(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (!ours(state)) {
            return CheckResult.PASS;
        }
        if (state.hasProperty(TallMachineBlock.HALF) && state.getBlock() instanceof TallMachineBlock) {
            Direction other = state.getValue(TallMachineBlock.HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
            if (direction == other) {
                return CheckResult.SUCCESS;
            }
        }
        if (state.getBlock() instanceof WideMachineBlock && state.hasProperty(WideMachineBlock.PART) && state.hasProperty(WideMachineBlock.FACING)) {
            Direction side = WideMachineBlock.sideward(state.getValue(WideMachineBlock.FACING));
            Direction other = state.getValue(WideMachineBlock.PART) == WideMachineBlock.Part.MAIN ? side : side.getOpposite();
            if (direction == other) {
                return CheckResult.SUCCESS;
            }
        }
        if (formed(state) && formed(level.getBlockState(pos.relative(direction)))) {
            return CheckResult.SUCCESS;
        }
        return CheckResult.PASS;
    }
}
