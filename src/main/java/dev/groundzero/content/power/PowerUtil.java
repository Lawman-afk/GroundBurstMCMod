package dev.groundzero.content.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import dev.groundzero.content.machine.MachineEnergy;

/** Energy movement helpers. */
public final class PowerUtil {
    private PowerUtil() {}

    /** Offers up to {@code maxPerTick} FE from a buffer to every neighbouring energy handler, round-robin by side. */
    public static void pushToNeighbours(ServerLevel level, BlockPos pos, MachineEnergy source, int maxPerTick) {
        int budget = Math.min(maxPerTick, source.stored());
        if (budget <= 0) {
            return;
        }
        int start = (int) (level.getGameTime() % 6);
        for (int k = 0; k < 6 && budget > 0; k++) {
            Direction side = Direction.values()[(start + k) % 6];
            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(side), side.getOpposite());
            if (target == null) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                int accepted = target.insert(budget, tx);
                if (accepted > 0) {
                    tx.commit();
                    source.consume(accepted);
                    budget -= accepted;
                }
            }
        }
    }
}
