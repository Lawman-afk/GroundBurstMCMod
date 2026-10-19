package dev.groundzero.content.power;

import java.util.function.Supplier;

import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

/** A capacitor bank block: a machine that also gives a comparator signal from its charge. */
public class CapacitorBankBlock extends MachineBlock {
    private final CapacitorBankBlockEntity.Tier tier;

    public CapacitorBankBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type, CapacitorBankBlockEntity.Tier tier) {
        super(properties, type, true, Shapes.block());
        this.tier = tier;
    }

    public CapacitorBankBlockEntity.Tier tier() {
        return this.tier;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CapacitorBankBlockEntity bank ? bank.signal() : 0;
    }
}
