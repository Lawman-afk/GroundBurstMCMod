package dev.groundburst.content.construction;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Ammo Crates' 27 stacks (DESIGN.md §22.6), kept as the Filing Cabinet keeps its own: a wooden box's lid lifted and
 * dropped (a barrel's sounds).
 */
public class AmmoCratesBlockEntity extends FilingCabinetBlockEntity {
    public static final String T_NAME = GBLang.add("container." + GroundBurst.MODID + ".ammo_crates", "Ammo Crates");

    public AmmoCratesBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(T_NAME);
    }

    @Override
    protected SoundEvent openSound() {
        return SoundEvents.BARREL_OPEN;
    }

    @Override
    protected SoundEvent closeSound() {
        return SoundEvents.BARREL_CLOSE;
    }
}
