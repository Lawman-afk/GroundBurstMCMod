package dev.groundburst.compat.create;

import java.util.List;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import dev.groundburst.content.machine.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * One of the mod's machines riding a Create contraption (main's DESIGN.md §17.16): launchers, assemblers, generators,
 * computers, every machine. The contraption keeps only its saved data; on the server this makes a block entity from it,
 * standing at the place it was taken from (its key: its network, its menu and its screen know it by it) in the
 * contraption's frame, ticks it each tick the contraption does ({@code carriedTick}: it works on what it holds, the power
 * the contraption's generators give it and what its pipes bring, {@link Aboard}), saves what changes back into the
 * contraption's copy (so it is set down as it is) and sends clients its looks when they change.
 */
final class CarriedMachine implements MovementBehaviour {
    /** The block entity being carried, kept with its contraption's movement context. */
    private record Carried(MachineBlockEntity machine) {}

    /** For the in-game check (dev.groundburst.compat.CreateCheck): told after each carried tick, as the contraption is then. */
    static @Nullable BiConsumer<ServerLevel, MachineBlockEntity> afterTick;

    @Override
    public void tick(MovementContext context) {
        if (!(context.world instanceof ServerLevel level) || context.contraption == null || context.contraption.entity == null) {
            return;
        }
        MachineBlockEntity machine = machine(context, level);
        if (machine == null) {
            return;
        }
        machine.carry(new ContraptionFrame(context.contraption.entity, context.contraption.anchor));
        Aboard aboard = Aboard.of(context.contraption);
        aboard.carried(context.localPos, machine);
        aboard.tick(level);                                    // once a tick for the whole contraption: power, pipes
        machine.carriedTick(level);
        if (afterTick != null) {
            afterTick.accept(level, machine);
        }
        if (machine.takeCarriedChange()) {
            save(context, machine, level);
        }
        if (machine.takeCarriedSync()) {
            sync(context, machine, level);
        }
    }

    /** It stops (set down, or the contraption is gone): what it is now goes into the contraption's copy, and it is let go. */
    @Override
    public void stopMoving(MovementContext context) {
        if (context.temporaryData instanceof Carried carried && context.world instanceof ServerLevel level) {
            save(context, carried.machine(), level);
            carried.machine().carry(null);
            context.temporaryData = null;
            if (context.contraption != null) {
                Aboard.of(context.contraption).forget(context.localPos);
            }
        }
    }

    /** It works whether or not the contraption is moving. */
    @Override
    public boolean isActive(MovementContext context) {
        return true;
    }

    /** The carried block entity: made from the contraption's copy the first time, at the place it was taken from. */
    static @Nullable MachineBlockEntity machine(MovementContext context, ServerLevel level) {
        if (context.temporaryData instanceof Carried carried) {
            return carried.machine();
        }
        CompoundTag saved = context.blockEntityData;
        if (saved == null || context.contraption == null || context.contraption.entity == null) {
            return null;
        }
        BlockPos home = context.contraption.anchor.offset(context.localPos);
        CompoundTag tag = saved.copy();
        tag.putInt("x", home.getX());
        tag.putInt("y", home.getY());
        tag.putInt("z", home.getZ());
        BlockEntity be = BlockEntity.loadStatic(home, context.state, tag, level.registryAccess());
        if (!(be instanceof MachineBlockEntity machine)) {
            return null;
        }
        machine.setLevel(level);
        machine.carry(new ContraptionFrame(context.contraption.entity, context.contraption.anchor));
        context.temporaryData = new Carried(machine);
        Aboard.of(context.contraption).carried(context.localPos, machine);
        return machine;
    }

    /** Into the contraption's own copy of its data (the one it sets the block down with). */
    private static void save(MovementContext context, MachineBlockEntity machine, ServerLevel level) {
        CompoundTag tag = context.blockEntityData;
        if (tag == null) {
            return;
        }
        CompoundTag fresh = machine.saveWithFullMetadata(level.registryAccess());
        fresh.remove("x");
        fresh.remove("y");
        fresh.remove("z");
        for (String key : List.copyOf(tag.getAllKeys())) {
            tag.remove(key);
        }
        tag.merge(fresh);
    }

    /** Its looks to every player who sees the contraption. */
    private static void sync(MovementContext context, MachineBlockEntity machine, ServerLevel level) {
        AbstractContraptionEntity entity = context.contraption.entity;
        PacketDistributor.sendToPlayersTrackingEntity(entity,
                new CarriedSync(entity.getId(), context.localPos, machine.getUpdateTag(level.registryAccess())));
    }
}
