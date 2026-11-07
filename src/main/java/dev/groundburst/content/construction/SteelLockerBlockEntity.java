package dev.groundburst.content.construction;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Steel Locker's 27 stacks (DESIGN.md §22.5), kept in its lower block as the Filing Cabinet keeps its own: its door
 * standing open while anyone has it open (the upper block follows the lower), a light steel door's creak and clang.
 */
public class SteelLockerBlockEntity extends FilingCabinetBlockEntity {
    public static final String T_NAME = GBLang.add("container." + GroundBurst.MODID + ".steel_locker", "Steel Locker");

    public SteelLockerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(T_NAME);
    }

    /** Held open by a signal at either of its blocks. */
    @Override
    protected boolean held() {
        return this.level != null && (this.level.hasNeighborSignal(this.worldPosition) || this.level.hasNeighborSignal(this.worldPosition.above()));
    }

    @Override
    protected SoundEvent openSound() {
        return GBSounds.STEEL_LOCKER_OPEN.get();
    }

    @Override
    protected SoundEvent closeSound() {
        return GBSounds.STEEL_LOCKER_CLOSE.get();
    }
}
