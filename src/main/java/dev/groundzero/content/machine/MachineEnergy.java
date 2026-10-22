package dev.groundzero.content.machine;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/** A machine's FE buffer; marks the block entity dirty on change and lets machine logic draw energy directly. */
public class MachineEnergy extends SimpleEnergyHandler {
    private final Runnable onChanged;

    public MachineEnergy(int capacity, int maxInsert, int maxExtract, Runnable onChanged) {
        super(capacity, maxInsert, maxExtract);
        this.onChanged = onChanged;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        this.onChanged.run();
    }

    public int stored() {
        return this.energy;
    }

    public int capacity() {
        return this.capacity;
    }

    /** Resizes the buffer (a structure that changed size); energy above the new capacity is lost. */
    public void setCapacity(int capacity) {
        this.capacity = Math.max(0, capacity);
        if (this.energy > this.capacity) {
            set(this.capacity);
        } else {
            this.onChanged.run();
        }
    }

    /** Machine-internal draw that ignores the external extract limit. Returns whether the full amount was taken. */
    public boolean consume(int amount) {
        if (this.energy < amount) {
            return false;
        }
        set(this.energy - amount);
        return true;
    }

    /** Machine-internal generation that ignores the external insert limit. Returns the amount accepted. */
    public int generate(int amount) {
        int accepted = Math.min(amount, this.capacity - this.energy);
        if (accepted > 0) {
            set(this.energy + accepted);
        }
        return accepted;
    }
}
