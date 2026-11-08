package dev.groundburst.gametest;

import java.util.List;
import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.chem.GBChemicals;
import dev.groundburst.content.fluid.ChemFluid;
import dev.groundburst.content.fluid.FluidTankBlockEntity;
import dev.groundburst.content.fluid.GBFluids;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.SideConfig;
import dev.groundburst.content.machine.SideConfig.Face;
import dev.groundburst.content.machine.SideConfig.ItemMode;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.part.GBParts;
import dev.groundburst.content.pipe.GBPipes;
import dev.groundburst.content.pipe.PipeBlock;
import dev.groundburst.content.resource.GBResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * An advanced-circuit line laid out as a small factory hall, each machine piped and wired as a player builds one: the
 * GameTest that shows it runs on its own ({@code circuit_line_makes_advanced_circuits}) and the photographer's pictures
 * of it (FxPhotographer's {@code circuit_line} scenes) build this. Sized round one Clean Room printing without a break,
 * four advanced circuits a minute; {@code tools/factory/circuit_line.py} works the numbers out from the recipes.
 *
 * <p>From its origin (the first block above its floor; +x east, +z south): two rows facing each other across an aisle
 * three wide (z 1 to 3). The north row (z 0, facing south) makes the wafers: a Cryogenic Condenser piping argon into a
 * Crystal Puller, its boules piped into an Industrial Press, its wafers down into a pipe laid in the aisle's floor and
 * across under the Clean Room's Item Port (the Clean Room's store, red phosphorus and plastic, on the same run), then
 * four Diesel Generators. The south row (z 4, facing north) makes the circuits: tanks of photoresist and hydrofluoric
 * acid piped into the Clean Room's Fluid Port, the Clean Room, the advanced Circuit Fabricator (its microchips drawn out
 * of the Item Port's back and round behind it, the gold wire and rare earth from its store on that run) and the basic
 * one, whose circuits it takes beside it; advanced circuits go into a chest in front of it. Behind each row its stores,
 * and the generators' fuel tank. Insulated cable runs along both rows over the machines and bridges the aisle.
 */
public final class CircuitLine {
    private CircuitLine() {}

    /** The machines, from the origin. */
    public static final BlockPos CONDENSER = new BlockPos(0, 0, 0), PULLER = new BlockPos(2, 0, 0), PRESS = new BlockPos(4, 0, 0),
            CLEAN_ROOM = new BlockPos(4, 0, 4), ADVANCED = new BlockPos(8, 0, 4), BASIC = new BlockPos(10, 0, 4);
    /** The stores: polysilicon and crucibles; red phosphorus and plastic; gold wire and rare earth; copper wire, redstone and plastic. */
    public static final BlockPos PULLER_STORE = new BlockPos(2, 0, -2), CLEAN_ROOM_STORE = new BlockPos(6, 0, 0),
            ADVANCED_STORE = new BlockPos(7, 0, 7), BASIC_STORE = new BlockPos(10, 0, 6);
    /** The chest the advanced circuits go to, in front of the advanced fabricator. */
    public static final BlockPos OUTPUT = new BlockPos(8, 0, 3);
    /** The four generators (one more each step east), at the north row's end. */
    public static final BlockPos GENERATORS = new BlockPos(8, 0, 0);
    /** The corners of what the line takes up (the trench under its floor, its cables and tall tanks over it), from the origin. */
    public static final BlockPos MIN = new BlockPos(0, -1, -2), MAX = new BlockPos(11, 2, 7);

    /** Each machine's name and place, for logs and checks. */
    public static final Map<String, BlockPos> MACHINES = Map.of("condenser", CONDENSER, "puller", PULLER, "press", PRESS,
            "clean_room", CLEAN_ROOM, "advanced_fabricator", ADVANCED, "basic_fabricator", BASIC);

    /**
     * Floors the hall (one block under its origin, a block round it) with reinforced concrete, the aisle's edges striped
     * with hazard concrete; the grass and flowers on it cleared first (set without shape updates, so nothing standing on
     * it breaks and drops).
     */
    public static void floor(ServerLevel level, BlockPos o) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState concrete = GBLaunch.REINFORCED_CONCRETE.block().get().defaultBlockState();
        BlockState stripe = GBLaunch.HAZARD_CONCRETE.block().get().defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(o.offset(MIN.getX() - 1, -1, MIN.getZ() - 1), o.offset(MAX.getX() + 1, -1, MAX.getZ() + 1))) {
            BlockPos above = p.above();
            BlockState on = level.getBlockState(above);
            if (!on.isAir() && on.getCollisionShape(level, above).isEmpty()) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), flags);
            }
            int dz = p.getZ() - o.getZ(), dx = p.getX() - o.getX();
            boolean edge = (dz == 1 || dz == 3) && dx >= MIN.getX() - 1 && dx <= MAX.getX() + 1;
            level.setBlock(p, edge ? stripe : concrete, flags);
        }
    }

    /**
     * Builds the line at {@code o}, stocked to run: its stores and tanks, the condenser's sieve and the press's saw, and a
     * little work in hand, as a line that has been running has (a boule in the press, wafers in the Clean Room, argon in
     * the puller, basic circuits and microchips at the advanced fabricator).
     */
    public static void build(ServerLevel level, BlockPos o) {
        // The machines first, their faces set, so that the pipes and cables laid after them join the right faces.
        MachineBlockEntity condenser = machine(level, o.offset(CONDENSER), GBMachines.CRYOGENIC_CONDENSER.block().get(), Direction.SOUTH);
        MachineBlockEntity puller = machine(level, o.offset(PULLER), GBMachines.CRYSTAL_PULLER.block().get(), Direction.SOUTH);
        MachineBlockEntity press = machine(level, o.offset(PRESS), GBMachines.INDUSTRIAL_PRESS.block().get(), Direction.SOUTH);
        MachineBlockEntity advanced = machine(level, o.offset(ADVANCED), GBMachines.CIRCUIT_FABRICATOR.block().get(), Direction.NORTH);
        MachineBlockEntity basic = machine(level, o.offset(BASIC), GBMachines.CIRCUIT_FABRICATOR.block().get(), Direction.NORTH);
        for (var cell : GBMultiblocks.CLEAN_ROOM.cells(o.offset(CLEAN_ROOM), Direction.NORTH)) {
            if (!level.getBlockState(cell.pos()).isAir()) {                       // its room cleared first, as a player clears it
                level.setBlock(cell.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        if (!GBMultiblocks.CLEAN_ROOM_SPAWNER.get().spawn(level, o.offset(CLEAN_ROOM), Direction.NORTH)) {
            GroundBurst.LOGGER.warn("Circuit line: the Clean Room could not be set down at {}", o.offset(CLEAN_ROOM));
        }
        for (int i = 0; i < 4; i++) {
            machine(level, o.offset(GENERATORS).east(i), GBMachines.DIESEL_GENERATOR.block().get(), Direction.SOUTH);
        }
        // Facing south: its left is west, its right east. Facing north: its left is east, its right west.
        faces(condenser, Map.of());                                                           // argon out through its east side
        faces(puller, Map.of(Face.BACK, ItemMode.INPUT, Face.RIGHT, ItemMode.OUTPUT));         // polysilicon in behind, boules out east
        faces(press, Map.of(Face.LEFT, ItemMode.INPUT, Face.RIGHT, ItemMode.OUTPUT));          // boules in from the west, wafers out east
        faces(advanced, Map.of(Face.BACK, ItemMode.INPUT, Face.LEFT, ItemMode.INPUT, Face.FRONT, ItemMode.OUTPUT));
        faces(basic, Map.of(Face.BACK, ItemMode.INPUT, Face.RIGHT, ItemMode.OUTPUT));          // its store behind, circuits out west

        chest(level, o.offset(PULLER_STORE), new ItemStack(GBParts.POLYSILICON.get(), 64), new ItemStack(GBParts.QUARTZ_CRUCIBLE.get(), 16));
        chest(level, o.offset(CLEAN_ROOM_STORE), new ItemStack(GBChemicals.RED_PHOSPHORUS.get(), 64),
                new ItemStack(GBChemicals.PLASTIC_SHEET.get(), 64));
        chest(level, o.offset(ADVANCED_STORE), new ItemStack(GBParts.GOLD_WIRE.get(), 64), new ItemStack(GBResources.RARE_EARTH_DUST.get(), 64));
        chest(level, o.offset(BASIC_STORE), new ItemStack(GBParts.COPPER_WIRE.get(), 64), new ItemStack(GBParts.COPPER_WIRE.get(), 64),
                new ItemStack(Items.REDSTONE, 64), new ItemStack(Items.REDSTONE, 64), new ItemStack(GBChemicals.PLASTIC_SHEET.get(), 64));
        chest(level, o.offset(OUTPUT));
        tank(level, o.offset(1, 0, 4), 2, GBFluids.PHOTORESIST);
        tank(level, o.offset(3, 0, 4), 2, GBFluids.HYDROFLUORIC_ACID);
        tank(level, o.offset(9, 0, -2), 3, GBFluids.SYNTHETIC_FUEL);

        // The north row: argon from the condenser into the puller; boules from the puller into the press; the puller's store.
        fluidPipe(level, o.offset(1, 0, 0));
        itemPipe(level, o.offset(3, 0, 0));
        itemPipe(level, o.offset(2, 0, -1));
        // Wafers from the press down into the aisle's floor and across under the Clean Room's Item Port (its underside),
        // its store's phosphorus and plastic drawn into the same run.
        itemPipe(level, o.offset(5, 0, 0));
        for (int z = 0; z <= 5; z++) {
            itemPipe(level, o.offset(5, -1, z));
        }
        // Photoresist and hydrofluoric acid behind their tanks, into the Clean Room's Fluid Port.
        for (int x = 1; x <= 3; x++) {
            fluidPipe(level, o.offset(x, 0, 5));
        }
        // Microchips drawn out of the Item Port's back and round behind the row to the advanced fabricator's; the gold wire
        // and rare earth from its store join them on the way.
        for (BlockPos p : List.of(new BlockPos(5, 0, 6), new BlockPos(6, 0, 6), new BlockPos(7, 0, 6), new BlockPos(8, 0, 6), new BlockPos(8, 0, 5))) {
            itemPipe(level, o.offset(p));
        }
        itemPipe(level, o.offset(9, 0, 4));                                    // basic circuits into the advanced fabricator's side
        itemPipe(level, o.offset(10, 0, 5));                                   // the basic fabricator's store
        for (int i = 0; i < 4; i++) {
            fluidPipe(level, o.offset(8 + i, 0, -1));                          // fuel along the generators' backs
        }
        // The pipes' pull sides, once everything they touch is down.
        pull(level, o.offset(2, 0, -1), Direction.NORTH);
        pull(level, o.offset(5, 0, 0), Direction.EAST);
        pull(level, o.offset(1, 0, 5), Direction.NORTH);
        pull(level, o.offset(3, 0, 5), Direction.NORTH);
        pull(level, o.offset(5, 0, 6), Direction.NORTH);
        pull(level, o.offset(7, 0, 6), Direction.SOUTH);
        pull(level, o.offset(10, 0, 5), Direction.SOUTH);
        pull(level, o.offset(9, 0, -1), Direction.NORTH);

        // Insulated cable (1,024 FE/t; the line draws 700 at most): along the north row on the tall machines' tops and down
        // onto the generators', across the aisle overhead, along the south row on the Clean Room's glass and down to the
        // fabricators and the Clean Room's Energy Port.
        BlockState cable = GBMachines.CABLES.get(1).block().get().defaultBlockState();
        for (int x = 0; x <= 7; x++) {
            level.setBlockAndUpdate(o.offset(x, 2, 0), cable);
        }
        for (int x = 7; x <= 11; x++) {
            level.setBlockAndUpdate(o.offset(x, 1, 0), cable);
        }
        for (int z = 1; z <= 3; z++) {
            level.setBlockAndUpdate(o.offset(7, 2, z), cable);
        }
        for (int x = 4; x <= 10; x++) {
            level.setBlockAndUpdate(o.offset(x, 2, 4), cable);
        }
        for (BlockPos p : List.of(new BlockPos(7, 1, 4), new BlockPos(7, 0, 4), new BlockPos(10, 1, 4))) {
            level.setBlockAndUpdate(o.offset(p), cable);
        }

        // Work in hand.
        condenser.inventory().setItem(0, new ItemStack(GBParts.ARGON_SIEVE.get()));
        puller.tank(0).fillInternal(new FluidStack(GBFluids.ARGON.source().get(), 1_000));
        press.inventory().setItem(0, new ItemStack(GBParts.SILICON_BOULE.get()));
        press.inventory().setItem(1, new ItemStack(GBParts.DIAMOND_WIRE_SAW.get()));         // its catalyst slot
        if (level.getBlockEntity(o.offset(CLEAN_ROOM)) instanceof MachineBlockEntity room) {
            room.inventory().setItem(0, new ItemStack(GBParts.SILICON_WAFER.get(), 4));
        }
        advanced.inventory().setItem(0, new ItemStack(GBParts.BASIC_CIRCUIT.get(), 2));
        advanced.inventory().setItem(1, new ItemStack(GBParts.MICROCHIP.get(), 2));
    }

    /** How many of an item a chest of the line holds. */
    public static int count(ServerLevel level, BlockPos chest, net.minecraft.world.item.Item item) {
        int n = 0;
        if (level.getBlockEntity(chest) instanceof Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                n += c.getItem(i).is(item) ? c.getItem(i).getCount() : 0;
            }
        }
        return n;
    }

    private static MachineBlockEntity machine(ServerLevel level, BlockPos pos, Block block, Direction facing) {
        BlockState state = block.defaultBlockState().setValue(MachineBlock.FACING, facing);
        level.setBlockAndUpdate(pos, state);
        block.setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
        return (MachineBlockEntity) level.getBlockEntity(pos);
    }

    /** Its faces' item modes (the rest none, a tall machine's upper faces too) and auto-eject on. */
    private static void faces(MachineBlockEntity machine, Map<Face, ItemMode> modes) {
        machine.changeSides(c -> {
            for (Face f : Face.values()) {
                set(c, f, modes.getOrDefault(f, ItemMode.NONE));
            }
            c.toggleAutoEject();
        });
        if (machine.getBlockState().getBlock() instanceof dev.groundburst.content.machine.TallMachineBlock) {
            machine.changeSides(true, c -> {
                for (Face f : Face.values()) {
                    set(c, f, ItemMode.NONE);
                }
            });
        }
    }

    private static void set(SideConfig c, Face face, ItemMode mode) {
        while (c.items(face) != mode) {
            c.cycleItems(face, true);
        }
    }

    private static void chest(ServerLevel level, BlockPos pos, ItemStack... contents) {
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof Container c) {
            for (int i = 0; i < contents.length; i++) {
                c.setItem(i, contents[i]);
            }
        }
    }

    /**
     * A Fluid Tank {@code height} blocks tall, full: each block filled as it is set down, since they join into one tank a
     * tick later (which then holds what they held).
     */
    private static void tank(ServerLevel level, BlockPos pos, int height, ChemFluid fluid) {
        for (int i = 0; i < height; i++) {
            level.setBlockAndUpdate(pos.above(i), GBFluids.FLUID_TANK.block().get().defaultBlockState());
            if (level.getBlockEntity(pos.above(i)) instanceof FluidTankBlockEntity tank) {
                try (Transaction tx = Transaction.openRoot()) {
                    tank.handler().insert(FluidResource.of(fluid.source().get()), FluidTankBlockEntity.CAPACITY_PER_BLOCK, tx);
                    tx.commit();
                }
            }
        }
    }

    private static void itemPipe(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, GBPipes.ITEM_PIPE.block().get().defaultBlockState());
    }

    private static void fluidPipe(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, GBFluids.COPPER_FLUID_PIPE.block().get().defaultBlockState());
    }

    /** Sets a pipe's sides to pull from what they touch (as the Wrench does). */
    private static void pull(ServerLevel level, BlockPos pos, Direction... sides) {
        BlockState state = level.getBlockState(pos);
        for (Direction d : sides) {
            if (PipeBlock.mode(state, d) != PipeBlock.Mode.NORMAL) {
                GroundBurst.LOGGER.warn("Circuit line: the pipe at {} does not reach anything {}", pos, d);
                continue;
            }
            state = state.setValue(PipeBlock.SIDES.get(d), PipeBlock.Mode.PULL);
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        PipeBlock.changed(level, pos, state);
    }
}
