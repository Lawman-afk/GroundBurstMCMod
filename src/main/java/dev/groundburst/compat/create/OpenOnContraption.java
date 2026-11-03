package dev.groundburst.compat.create;

import org.apache.commons.lang3.tuple.MutablePair;
import org.jspecify.annotations.Nullable;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import dev.groundburst.content.launch.LaunchComputerBlock;
import dev.groundburst.content.launch.LaunchStationBlock;
import dev.groundburst.content.launch.RadarArrayBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Right-clicking one of the mod's machines on a Create contraption (main's DESIGN.md §17.16): its menu opens as it would in
 * the world (the server opens it for the carried machine, named by the place it was taken from); the Launch Computer, the
 * Launch Control Station and the Radar Array open their screens (on the client, by that same place). A formed structure's
 * part opens its controller's; either half of a two-block machine opens the machine's.
 */
final class OpenOnContraption extends MovingInteractionBehaviour {
    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos localPos, AbstractContraptionEntity entity) {
        Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return false;
        }
        BlockPos at = machineAt(contraption, localPos, player);
        StructureBlockInfo info = at == null ? null : contraption.getBlocks().get(at);
        if (info == null) {
            return false;
        }
        BlockPos home = contraption.anchor.offset(at);
        Block block = info.state().getBlock();
        boolean screen = block instanceof LaunchComputerBlock || block instanceof LaunchStationBlock || block instanceof RadarArrayBlock;
        if (player.level().isClientSide()) {
            if (screen && info.state().getValue(MultiblockPartBlock.FORMED)) {
                if (block instanceof LaunchComputerBlock) {
                    LaunchComputerBlock.screenHook.accept(home);
                } else if (block instanceof LaunchStationBlock) {
                    LaunchStationBlock.screenHook.accept(home);
                } else {
                    RadarArrayBlock.screenHook.accept(home);
                }
            }
            return true;
        }
        if (screen || !(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return true;
        }
        MutablePair<StructureBlockInfo, MovementContext> actor = contraption.getActorAt(at);
        MachineBlockEntity machine = actor == null ? null : CarriedMachine.machine(actor.right, level);
        if (machine == null) {
            return false;
        }
        serverPlayer.openMenu(machine, home);
        return true;
    }

    /**
     * Which of the contraption's places holds the machine a click at {@code local} means: a machine itself, the main half
     * of a two-block one, or the controller of a formed structure the part belongs to (null: none).
     */
    private static @Nullable BlockPos machineAt(Contraption contraption, BlockPos local, Player player) {
        StructureBlockInfo info = contraption.getBlocks().get(local);
        if (info == null) {
            return null;
        }
        BlockState state = info.state();
        if (contraption.getActorAt(local) != null) {
            return local;
        }
        if (state.getBlock() instanceof TallMachineBlock) {
            BlockPos base = TallMachineBlock.base(local, state);
            return contraption.getActorAt(base) != null ? base : null;
        }
        if (!(state.getBlock() instanceof MultiblockPartBlock) || !state.getValue(MultiblockPartBlock.FORMED)) {
            return null;
        }
        // The controller whose structure takes this place in (on the client, by the copies the contraption draws).
        for (MutablePair<StructureBlockInfo, MovementContext> actor : contraption.getActors()) {
            BlockPos c = actor.left.pos();
            if (player.level().isClientSide()) {
                BlockEntity shown = contraption.getOrCreateClientContraptionLazy().getBlockEntity(c);
                if (shown instanceof MultiblockController mc && mc.covers(local)) {
                    return c;
                }
            } else if (player.level() instanceof ServerLevel level
                    && CarriedMachine.machine(actor.right, level) instanceof MultiblockController mc && mc.covers(contraption.anchor.offset(local))) {
                return c;
            }
        }
        return null;
    }
}
