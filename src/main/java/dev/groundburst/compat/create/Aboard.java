package dev.groundburst.compat.create;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.Contraption;

import dev.groundburst.content.fluid.FluidPipeBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineEnergy;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.pipe.ItemPipeBlock;
import dev.groundburst.content.pipe.ItemPipeTier;
import dev.groundburst.content.pipe.PipeBlock;
import dev.groundburst.port.transfer.Adapters;
import dev.groundburst.port.transfer.Resource;
import dev.groundburst.port.transfer.ResourceHandler;
import dev.groundburst.port.transfer.ResourceHandlerUtil;
import dev.groundburst.port.transfer.fluid.FluidResource;
import dev.groundburst.port.transfer.item.ItemResource;
import dev.groundburst.port.transfer.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * What a Create contraption's machines share (main's DESIGN.md §17.16), once a tick for the whole contraption:
 * <ul>
 * <li><b>Power</b>: its generators and capacitor banks feed its other machines, as if cabled together (no cables needed:
 * a contraption has no world for them to run in); generators fill capacitor banks too.</li>
 * <li><b>Pipes</b>: its item and fluid pipes work as in the world. A side set to pull draws from what it touches aboard
 * (a chest, a vault, a tank, a machine) and hands what it draws round its run's other ends: machines (as their sides
 * allow), a structure's Item or Fluid Port, chests, vaults and tanks aboard; never back where it came from, at the run's
 * weakest tier (items a batch every window, fluids a rate every tick).</li>
 * </ul>
 * Positions here are the contraption's own (its blocks' places in it).
 */
final class Aboard {
    private static final Map<Contraption, Aboard> ALL = new WeakHashMap<>();

    private final Contraption contraption;
    /** Its carried machines, by their place in it. */
    private final Map<BlockPos, MachineBlockEntity> machines = new HashMap<>();
    private long ticked = Long.MIN_VALUE;
    private @Nullable List<Run> runs;

    private Aboard(Contraption contraption) {
        this.contraption = contraption;
    }

    static Aboard of(Contraption contraption) {
        return ALL.computeIfAbsent(contraption, Aboard::new);
    }

    void carried(BlockPos local, MachineBlockEntity machine) {
        this.machines.put(local.immutable(), machine);
    }

    void forget(BlockPos local) {
        this.machines.remove(local);
    }

    /** Once a game tick (whichever of its machines ticks first): power shared, the pipes run. */
    void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now == this.ticked) {
            return;
        }
        this.ticked = now;
        sharePower(now);
        pipes(now);
    }

    // --- power ---------------------------------------------------------------------------------------------------------------
    /**
     * Each tick generators (they give and never take) feed every machine that takes power, capacitor banks among them;
     * then capacitor banks (they give and take) feed the machines that are not banks. Each giver gives at most its own
     * output a tick, each taker takes at most its own intake; what there is goes round fairly (an even share each, then
     * what is left, starting from a different machine each tick), so a big consumer never starves a small one.
     */
    private void sharePower(long now) {
        List<MachineEnergy> generators = new ArrayList<>(), stores = new ArrayList<>(), takers = new ArrayList<>(), users = new ArrayList<>();
        for (MachineBlockEntity m : this.machines.values()) {
            MachineEnergy e = m.energy();
            boolean gives = e.maxExtract() > 0, takes = e.maxInsert() > 0;
            if (gives && !takes) {
                generators.add(e);
            } else if (gives) {
                stores.add(e);
            }
            if (takes) {
                takers.add(e);
                if (!gives) {
                    users.add(e);
                }
            }
        }
        if (generators.isEmpty() && stores.isEmpty() || takers.isEmpty()) {
            return;
        }
        Map<MachineEnergy, int[]> budget = new java.util.IdentityHashMap<>();
        for (MachineEnergy giver : generators) {
            budget.put(giver, new int[] {Math.min(giver.maxExtract(), giver.stored())});
        }
        for (MachineEnergy giver : stores) {
            budget.put(giver, new int[] {Math.min(giver.maxExtract(), giver.stored())});
        }
        Map<MachineEnergy, int[]> taken = new java.util.IdentityHashMap<>();
        feed(generators, takers, budget, taken, now);
        feed(stores, users, budget, taken, now);
    }

    /** {@code givers} feed {@code takers}: an even share each first, then what is left, round from a turning start. */
    private static void feed(List<MachineEnergy> givers, List<MachineEnergy> takers, Map<MachineEnergy, int[]> budget,
            Map<MachineEnergy, int[]> taken, long now) {
        int supply = 0;
        for (MachineEnergy giver : givers) {
            supply += budget.get(giver)[0];
        }
        int n = takers.size();
        if (supply <= 0 || n == 0) {
            return;
        }
        int start = (int) Math.floorMod(now, n), share = Math.max(1, supply / n);
        for (int round = 0; round < 2; round++) {
            for (int k = 0; k < n; k++) {
                MachineEnergy taker = takers.get((start + k) % n);
                int[] had = taken.computeIfAbsent(taker, t -> new int[1]);
                int want = Math.min(taker.maxInsert() - had[0], taker.capacity() - taker.stored());
                if (round == 0) {
                    want = Math.min(want, share);
                }
                if (want > 0) {
                    had[0] += draw(givers, taker, want, budget);
                }
            }
        }
    }

    /** Moves up to {@code want} FE from {@code givers} (each within what it has left to give this tick) into {@code to}. */
    private static int draw(List<MachineEnergy> givers, MachineEnergy to, int want, Map<MachineEnergy, int[]> budget) {
        int got = 0;
        for (MachineEnergy giver : givers) {
            if (got >= want) {
                break;
            }
            int[] left = budget.get(giver);
            if (giver == to || left == null || left[0] <= 0) {
                continue;
            }
            int give = Math.min(left[0], want - got);
            if (giver.consume(give)) {
                int accepted = to.generate(give);
                if (accepted < give) {
                    giver.generate(give - accepted);              // what did not fit goes back
                }
                left[0] -= accepted;
                got += accepted;
            }
        }
        return got;
    }

    // --- pipes ---------------------------------------------------------------------------------------------------------------
    /** What a run of pipes carries. */
    private enum Kind { ITEMS, FLUIDS }

    /** A run of pipes aboard: what it carries, its pull sides, its ends (the sides that deliver), its batch. */
    private record Run(Kind kind, List<Side> pulls, List<Side> ends, int batch) {}

    /** A side of a pipe run: the block it touches and that block's face toward the run. */
    private record Side(BlockPos pos, Direction face) {}

    /** What holds a kind of resource at a place aboard, seen from a face. */
    @FunctionalInterface
    private interface Holders<T extends Resource> {
        @Nullable ResourceHandler<T> at(BlockPos local, Direction face);
    }

    /** Item runs move a batch every window, fluid runs their rate every tick, as in the world. */
    private void pipes(long now) {
        if (this.runs == null) {
            this.runs = findRuns();
        }
        for (Run run : this.runs) {
            if (run.kind == Kind.ITEMS && now % ItemPipeTier.WINDOW == 0) {
                move(run, this::items, ItemResource.EMPTY, true);
            } else if (run.kind == Kind.FLUIDS && now % FluidPipeBlock.PULL_INTERVAL == 0) {
                move(run, this::fluids, FluidResource.EMPTY, false);
            }
        }
    }

    /** Each pull side draws from what it touches and hands it round the run, up to the run's batch in all. */
    private <T extends Resource> void move(Run run, Holders<T> holders, T empty, boolean stacking) {
        int room = run.batch;
        for (Side pull : run.pulls) {
            if (room <= 0) {
                break;
            }
            ResourceHandler<T> from = holders.at(pull.pos, pull.face);
            if (from == null) {
                continue;
            }
            ResourceHandler<T> to = sink(run, pull.pos, holders, empty);
            room -= stacking ? ResourceHandlerUtil.moveStacking(from, to, r -> true, room, null)
                    : ResourceHandlerUtil.move(from, to, r -> true, room, null);
        }
    }

    /** Where a run hands what it draws: its ends round-robin, never back to {@code source}. */
    private <T extends Resource> ResourceHandler<T> sink(Run run, BlockPos source, Holders<T> holders, T empty) {
        return new ResourceHandler<>() {
            private int next;

            @Override
            public int size() {
                return 1;
            }

            @Override
            public T getResource(int index) {
                return empty;
            }

            @Override
            public long getAmountAsLong(int index) {
                return 0;
            }

            @Override
            public long getCapacityAsLong(int index, T resource) {
                return run.batch;
            }

            @Override
            public boolean isValid(int index, T resource) {
                return true;
            }

            @Override
            public int insert(int index, T resource, int amount, TransactionContext transaction) {
                int n = run.ends.size(), sent = 0;
                for (int k = 0; k < n && sent < amount; k++) {
                    Side end = run.ends.get(Math.floorMod(this.next + k, n));
                    if (end.pos.equals(source)) {
                        continue;
                    }
                    ResourceHandler<T> to = holders.at(end.pos, end.face);
                    if (to != null) {
                        sent += to.insert(resource, amount - sent, transaction);
                    }
                }
                this.next++;
                return sent;
            }

            @Override
            public int extract(int index, T resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }

    /**
     * What holds items at a place aboard, seen from {@code face}: a carried machine (as its sides allow; either half of a
     * two-block one), a structure's Item Port (its controller's launch slots), or a chest or vault (Create's storage).
     */
    private @Nullable ResourceHandler<ItemResource> items(BlockPos local, Direction face) {
        StructureBlockInfo info = this.contraption.getBlocks().get(local);
        if (info == null) {
            return null;
        }
        BlockState state = info.state();
        MachineBlockEntity machine = this.machines.get(local);
        if (machine != null) {
            return machine.itemsFor(face);
        }
        if (state.getBlock() instanceof TallMachineBlock) {
            MachineBlockEntity main = this.machines.get(TallMachineBlock.base(local, state));
            return main == null ? null : main.itemsFor(face, true);
        }
        if (state.is(GBMultiblocks.ITEM_PORT.block().get())) {
            MachineBlockEntity controller = controllerOf(local);
            return controller == null ? null : controller.portItems();
        }
        MountedItemStorage storage = this.contraption.getStorage().getAllItemStorages().get(local);
        return storage == null ? null : Adapters.foreignItems(storage);
    }

    /**
     * What holds fluids at a place aboard, seen from {@code face}: a carried machine's tanks (as its sides allow; either half
     * of a two-block one), a structure's Fluid Port (its controller's tanks), or a tank Create carries.
     */
    private @Nullable ResourceHandler<FluidResource> fluids(BlockPos local, Direction face) {
        StructureBlockInfo info = this.contraption.getBlocks().get(local);
        if (info == null) {
            return null;
        }
        BlockState state = info.state();
        MachineBlockEntity machine = this.machines.get(local);
        if (machine != null) {
            return machine.fluidsFor(face);
        }
        if (state.getBlock() instanceof TallMachineBlock) {
            MachineBlockEntity main = this.machines.get(TallMachineBlock.base(local, state));
            return main == null ? null : main.fluidsFor(face, true);
        }
        if (state.is(GBMultiblocks.FLUID_PORT.block().get())) {
            MachineBlockEntity controller = controllerOf(local);
            return controller == null || controller.tanks().isEmpty() ? null : controller.fluidHandler();
        }
        MountedFluidStorage storage = this.contraption.getStorage().getFluids().storages.get(local);
        return storage == null ? null : Adapters.foreignFluids(storage);
    }

    /** The carried structure controller whose structure takes in the place {@code local}. */
    @Nullable MachineBlockEntity controllerOf(BlockPos local) {
        BlockPos home = this.contraption.anchor.offset(local);
        for (MachineBlockEntity m : this.machines.values()) {
            if (m instanceof MultiblockController c && c.isFormed() && c.covers(home)) {
                return m;
            }
        }
        return null;
    }

    /** Whether two pipe blocks join (the same kind). */
    private static boolean sameKind(BlockState a, BlockState b) {
        return a.getBlock() instanceof PipeBlock pipe && pipe.isSameKind(b);
    }

    /** The contraption's pipe runs, item and fluid, found once (its blocks do not change while it is carried). */
    private List<Run> findRuns() {
        Map<BlockPos, StructureBlockInfo> blocks = this.contraption.getBlocks();
        List<Run> out = new ArrayList<>();
        Set<BlockPos> done = new HashSet<>();
        for (Map.Entry<BlockPos, StructureBlockInfo> entry : blocks.entrySet()) {
            BlockState first = entry.getValue().state();
            boolean itemPipe = first.getBlock() instanceof ItemPipeBlock, fluidPipe = first.getBlock() instanceof FluidPipeBlock;
            if (!itemPipe && !fluidPipe || done.contains(entry.getKey())) {
                continue;
            }
            Set<BlockPos> run = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(entry.getKey());
            run.add(entry.getKey());
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                BlockState ps = blocks.get(p).state();
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    StructureBlockInfo ni = blocks.get(n);
                    if (ni != null && !run.contains(n) && sameKind(ps, ni.state())
                            && PipeBlock.mode(ps, d) == PipeBlock.Mode.NORMAL && PipeBlock.mode(ni.state(), d.getOpposite()) == PipeBlock.Mode.NORMAL) {
                        run.add(n);
                        queue.add(n);
                    }
                }
            }
            done.addAll(run);
            List<Side> pulls = new ArrayList<>(), ends = new ArrayList<>();
            Set<BlockPos> endAt = new HashSet<>();
            int batch = Integer.MAX_VALUE;
            for (BlockPos p : run) {
                BlockState ps = blocks.get(p).state();
                batch = Math.min(batch, ps.getBlock() instanceof ItemPipeBlock item ? item.tier().batch()
                        : ((FluidPipeBlock) ps.getBlock()).tier().rate());
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    StructureBlockInfo ni = blocks.get(n);
                    if (ni == null || ni.state().getBlock() instanceof PipeBlock) {
                        continue;
                    }
                    PipeBlock.Mode mode = PipeBlock.mode(ps, d);
                    if (mode == PipeBlock.Mode.PULL) {
                        pulls.add(new Side(n.immutable(), d.getOpposite()));
                    } else if (mode == PipeBlock.Mode.NORMAL && endAt.add(n.immutable())) {
                        ends.add(new Side(n.immutable(), d.getOpposite()));
                    }
                }
            }
            if (!pulls.isEmpty() && !ends.isEmpty()) {
                out.add(new Run(itemPipe ? Kind.ITEMS : Kind.FLUIDS, List.copyOf(pulls), List.copyOf(ends), batch));
            }
        }
        return out;
    }
}
