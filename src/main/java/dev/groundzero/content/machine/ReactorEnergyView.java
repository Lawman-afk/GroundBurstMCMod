package dev.groundzero.content.machine;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * What a big reactor's Energy Port hands cables: power goes out of the reactor's output buffer, but only above a
 * reserve the reactor keeps for itself, and power comes in only while the reactor wants it (into whichever buffer the
 * reactor names: the fusion reactor's ignition charge, the singularity cage's buffer). Both sides delegate to the real
 * buffers, so transactions roll back as usual. The GUI and comparator-style readers see the output buffer.
 */
public final class ReactorEnergyView implements EnergyHandler {
    /** Where incoming power goes right now, or null when the reactor takes none. */
    public interface Intake {
        EnergyHandler intake();
    }

    private final MachineEnergy output;
    private final Intake intake;
    private final java.util.function.IntSupplier reserve;

    public ReactorEnergyView(MachineEnergy output, Intake intake, java.util.function.IntSupplier reserve) {
        this.output = output;
        this.intake = intake;
        this.reserve = reserve;
    }

    @Override
    public long getAmountAsLong() {
        return this.output.getAmountAsLong();
    }

    @Override
    public long getCapacityAsLong() {
        return this.output.getCapacityAsLong();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        EnergyHandler target = this.intake.intake();
        return target == null ? 0 : target.insert(amount, transaction);
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        int spare = Math.max(0, this.output.stored() - this.reserve.getAsInt());
        return spare <= 0 ? 0 : this.output.extract(Math.min(amount, spare), transaction);
    }
}
