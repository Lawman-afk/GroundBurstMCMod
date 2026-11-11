package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.chem.GBChemicals;
import dev.groundburst.content.fluid.GBFluids;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.StructureSpawnerItem;
import dev.groundburst.content.part.GBParts;
import dev.groundburst.content.resource.GBResources;
import dev.groundburst.registry.GBBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

/**
 * The semiconductor chain, for the site photographer ({@code -Psite=chips}; DESIGN.md §8.1): on a concrete pad, fronts
 * north, in the order the work goes, each fed and powered so it runs: an Arc Furnace (quartz and coal to silicon), an
 * Electrolytic Separator (hydrogen through its membrane), the Chemical Plant (formed, making sulfuric acid), a Cryogenic
 * Condenser (argon), a Crystal Puller growing a boule, an Industrial Press sawing boules into wafers, and the Clean Room
 * (formed) printing microchips; from all round, and close up.
 */
final class ChipsBoard {
    private ChipsBoard() {}

    /** Blocks between one stop and the next. */
    private static final int GAP = 2;

    /**
     * One stop on the line: its block (a multiblock's controller), the multiblock if formed, and where it stands: its
     * controller's x and z from the board's origin, its west edge and width, its depth back from the line's front.
     */
    private record Stop(String name, GBBlocks.WithItem<? extends Block> block, @Nullable MultiblockDefinition def, int x, int z, int west,
            int width, int depth) {}

    private static List<Stop> line() {
        record Entry(String name, GBBlocks.WithItem<? extends Block> block, @Nullable MultiblockDefinition def) {}
        List<Entry> entries = List.of(
                new Entry("arc_furnace", GBMachines.ARC_FURNACE, null),
                new Entry("separator", GBMachines.ELECTROLYTIC_SEPARATOR, null),
                new Entry("chemical_plant", GBMachines.CHEMICAL_PLANT, GBMultiblocks.CHEMICAL_PLANT),
                new Entry("condenser", GBMachines.CRYOGENIC_CONDENSER, null),
                new Entry("puller", GBMachines.CRYSTAL_PULLER, null),
                new Entry("press", GBMachines.INDUSTRIAL_PRESS, null),
                new Entry("clean_room", GBMachines.CLEAN_ROOM, GBMultiblocks.CLEAN_ROOM));
        int total = -GAP;
        int[][] spans = new int[entries.size()][];
        for (int i = 0; i < entries.size(); i++) {
            int lo = 0, hi = 0, front = 0, back = 0;
            if (entries.get(i).def() != null) {
                for (MultiblockDefinition.Cell cell : entries.get(i).def().cells(BlockPos.ZERO, Direction.NORTH)) {
                    lo = Math.min(lo, cell.pos().getX());
                    hi = Math.max(hi, cell.pos().getX());
                    front = Math.min(front, cell.pos().getZ());
                    back = Math.max(back, cell.pos().getZ());
                }
            }
            spans[i] = new int[] {lo, hi, front, back};
            total += hi - lo + 1 + GAP;
        }
        List<Stop> out = new ArrayList<>();
        int west = -total / 2;
        for (int i = 0; i < entries.size(); i++) {
            int[] s = spans[i];
            Entry e = entries.get(i);
            out.add(new Stop(e.name(), e.block(), e.def(), west - s[0], -s[2], west, s[1] - s[0] + 1, s[3] - s[2] + 1));
            west += s[1] - s[0] + 1 + GAP;
        }
        return out;
    }

    static BoundingBox build(ServerLevel level, BlockPos o) {
        int x = o.getX(), y = o.getY() + 1, z = o.getZ();
        List<Stop> line = line();
        int west = dev.groundburst.legacy.J21.first(line).west() - 3, east = dev.groundburst.legacy.J21.last(line).west() + dev.groundburst.legacy.J21.last(line).width() + 2;
        int back = line.stream().mapToInt(Stop::depth).max().orElse(1) + 2;
        BlockState concrete = GBLaunch.REINFORCED_CONCRETE.block().get().defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(x + west, y - 1, z - 4, x + east, y - 1, z + back)) {
            level.setBlock(p, concrete, 2);
        }
        for (Stop s : line) {
            BlockPos at = new BlockPos(x + s.x(), y, z + s.z());
            if (s.def() != null) {
                StructureSpawnerItem spawner = s.def() == GBMultiblocks.CLEAN_ROOM ? GBMultiblocks.CLEAN_ROOM_SPAWNER.get()
                        : GBMultiblocks.CHEMICAL_PLANT_SPAWNER.get();
                spawner.spawn(level, at, Direction.NORTH);
            } else {
                Block block = s.block().block().get();
                BlockState state = block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH);
                level.setBlockAndUpdate(at, state);
                block.setPlacedBy(level, at, state, null, ItemStack.EMPTY);
            }
            if (((Object) level.getBlockEntity(at)) instanceof MachineBlockEntity machine) {
                feed(s.name(), machine);
            }
        }
        return new BoundingBox(x + west, o.getY(), z - 4, x + east, y + 4, z + back);
    }

    /** What each runs on, enough to keep it busy through the pictures. */
    private static void feed(String name, MachineBlockEntity m) {
        m.energy().generate(Integer.MAX_VALUE);
        switch (name) {
            case "arc_furnace" -> {
                m.inventory().setItem(0, new ItemStack(Items.QUARTZ, 64));
                m.inventory().setItem(1, new ItemStack(Items.COAL, 64));
            }
            case "separator" -> {
                m.tank(0).fillInternal(new FluidStack(Fluids.WATER, 8_000));
                m.inventory().setItem(0, new ItemStack(GBParts.PROTON_EXCHANGE_MEMBRANE.get()));
            }
            case "chemical_plant" -> {
                m.tank(0).fillInternal(new FluidStack(Fluids.WATER, 8_000));
                m.inventory().setItem(0, new ItemStack(GBResources.SULFUR_DUST.get(), 64));
            }
            case "condenser" -> m.inventory().setItem(0, new ItemStack(GBParts.ARGON_SIEVE.get()));
            case "puller" -> {
                m.inventory().setItem(0, new ItemStack(GBParts.POLYSILICON.get(), 16));
                m.inventory().setItem(1, new ItemStack(GBParts.QUARTZ_CRUCIBLE.get(), 4));
                m.tank(0).fillInternal(new FluidStack(GBFluids.ARGON.source().get(), 4_000));
            }
            case "press" -> {
                m.inventory().setItem(0, new ItemStack(GBParts.SILICON_BOULE.get(), 16));
                m.inventory().setItem(1, new ItemStack(GBParts.DIAMOND_WIRE_SAW.get()));
            }
            case "clean_room" -> {
                m.inventory().setItem(0, new ItemStack(GBParts.SILICON_WAFER.get(), 16));
                m.inventory().setItem(1, new ItemStack(GBChemicals.RED_PHOSPHORUS.get(), 16));
                m.inventory().setItem(2, new ItemStack(GBChemicals.PLASTIC_SHEET.get(), 16));
                m.tank(0).fillInternal(new FluidStack(GBFluids.PHOTORESIST.source().get(), 4_000));
                m.tank(1).fillInternal(new FluidStack(GBFluids.HYDROFLUORIC_ACID.source().get(), 4_000));
            }
            default -> {}
        }
    }

    /**
     * Close pictures: the line from its west end (and at midnight, its machines' lights), each stop from the front a
     * little off to one side, the puller's window, and the line from its east end.
     */
    static List<BunkerDoorBoard.CloseUp> closeUps(BlockPos o) {
        double x = o.getX(), y = o.getY() + 1, z = o.getZ();
        List<Stop> line = line();
        List<BunkerDoorBoard.CloseUp> out = new ArrayList<>();
        Stop first = dev.groundburst.legacy.J21.first(line), last = dev.groundburst.legacy.J21.last(line);
        out.add(new BunkerDoorBoard.CloseUp("line_west", new Vec3(x + first.west() - 2.0, y + 0.2, z - 4.0), -52, 9));
        out.add(new BunkerDoorBoard.CloseUp("line_west_night", new Vec3(x + first.west() - 2.0, y + 0.2, z - 4.0), -52, 9));
        for (Stop s : line) {
            double cx = x + s.west() + s.width() / 2.0;
            boolean big = s.def() != null;
            double reach = big ? 2.4 + 0.8 * s.width() : 2.3;
            out.add(new BunkerDoorBoard.CloseUp(s.name(), new Vec3(cx - reach * 0.36, y + (big ? 0.5 : 0.0), z - reach), -20, big ? 15 : 12));
        }
        Stop puller = line.stream().filter(s -> s.name().equals("puller")).findFirst().orElseThrow();
        out.add(new BunkerDoorBoard.CloseUp("puller_window", new Vec3(x + puller.x() + 0.5, y - 0.3, z + puller.z() - 1.5), 0, -4));
        out.add(new BunkerDoorBoard.CloseUp("line_east", new Vec3(x + last.west() + last.width() + 2.0, y + 0.2, z - 4.0), 52, 9));
        return out;
    }
}
