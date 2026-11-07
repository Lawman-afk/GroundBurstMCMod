package dev.groundburst.client.fx;

import java.util.List;

import dev.groundburst.content.construction.BunkBedBlock;
import dev.groundburst.content.construction.BunkerLampBlock;
import dev.groundburst.content.construction.FuseBoxBlock;
import dev.groundburst.content.construction.GBConstruction;
import dev.groundburst.content.construction.PipeValveBlock;
import dev.groundburst.content.construction.SteelLockerBlock;
import dev.groundburst.content.construction.VentilationDuctBlock;
import dev.groundburst.content.construction.WallPipesBlock;
import dev.groundburst.content.launch.GBLaunch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The bunker fittings, for the site photographer ({@code -Psite=fittings}; DESIGN.md §22.5): a concrete room open to the
 * sky (for the light) and at its front; along its back wall a run of Wall Pipes through a Pipe Valve, two Fuse Boxes (one
 * on), Bunker Lamps (one off), Ventilation Duct along its top with Vent Grilles under it; Steel Lockers down its east side
 * (one held open by a lever), two Bunk Beds down its west, a Fire Extinguisher by its front; from all round, and close up.
 */
final class FittingsBoard {
    private FittingsBoard() {}

    static BoundingBox build(ServerLevel level, BlockPos o) {
        int x = o.getX(), y = o.getY() + 1, z = o.getZ();
        BlockState concrete = GBLaunch.REINFORCED_CONCRETE.block().get().defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(x - 7, y - 1, z - 6, x + 7, y - 1, z + 7)) {
            level.setBlock(p, concrete, 2);
        }
        for (BlockPos p : BlockPos.betweenClosed(x - 7, y, z - 6, x + 7, y + 3, z + 7)) {
            if (p.getX() == x - 7 || p.getX() == x + 7 || p.getZ() == z + 7) {
                level.setBlock(p, concrete, 2);
            }
        }
        // The pipe run along the back wall, the valve in it open; the lamps over it, one off.
        for (int i = -5; i <= 1; i++) {
            Block b = i == -2 ? GBConstruction.PIPE_VALVE.block().get() : GBConstruction.WALL_PIPES.block().get();
            BlockState s = b.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
            level.setBlock(new BlockPos(x + i, y + 1, z + 6), i == -2 ? s.setValue(PipeValveBlock.OPEN, true) : s, 3);
        }
        for (int i = -5; i <= 1; i++) {                    // (set, not placed: each joined to its neighbours as placing would)
            BlockPos at = new BlockPos(x + i, y + 1, z + 6);
            BlockState s = level.getBlockState(at);
            level.setBlock(at, ((WallPipesBlock) s.getBlock()).joined(s, level, at), 3);
        }
        // Each lamp lit by a lever on the wall beside it (the middle one's thrown off).
        for (int i : new int[] {-5, -1, 3}) {
            level.setBlock(new BlockPos(x + i - 1, y + 3, z + 6), net.minecraft.world.level.block.Blocks.LEVER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                    .setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.NORTH)
                    .setValue(net.minecraft.world.level.block.LeverBlock.POWERED, i != -1), 3);
            level.setBlock(new BlockPos(x + i, y + 3, z + 6), GBConstruction.BUNKER_LAMP.block().get().defaultBlockState()
                    .setValue(DirectionalBlock.FACING, Direction.NORTH).setValue(BunkerLampBlock.LIT, i != -1), 3);
        }
        // Two fuse boxes on the back wall, one on; the duct along the wall's top, grilles under it.
        for (int i = 0; i < 2; i++) {
            level.setBlock(new BlockPos(x + 3 + i, y + 1, z + 6), GBConstruction.FUSE_BOX.block().get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(FuseBoxBlock.POWERED, i == 0), 3);
        }
        VentilationDuctBlock duct = GBConstruction.VENTILATION_DUCT.block().get();
        for (int i = 4; i <= 6; i++) {
            level.setBlock(new BlockPos(x + i, y + 3, z + 6), duct.defaultBlockState(), 3);
        }
        for (int dz = 2; dz <= 5; dz++) {
            level.setBlock(new BlockPos(x + 6, y + 3, z + dz), duct.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(x + 6, y + 2, z + 2), GBConstruction.VENT_GRILLE.block().get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.DOWN), 3);
        level.setBlock(new BlockPos(x - 6, y + 2, z + 5), GBConstruction.VENT_GRILLE.block().get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.EAST), 3);
        // Ducts join as they are placed by hand: here they are set, so each is joined to what stands round it now.
        for (BlockPos p : BlockPos.betweenClosed(x + 3, y + 2, z + 1, x + 7, y + 4, z + 7)) {
            BlockState s = level.getBlockState(p);
            if (s.is(duct)) {
                level.setBlock(p, duct.joined(s, level, p), 3);
            }
        }
        // The lockers down the east side, facing into the room, the second held open by a lever on the wall over it (a
        // locker stands open while anyone has it open or a signal reaches it; set open, the next block update shuts it).
        SteelLockerBlock locker = GBConstruction.STEEL_LOCKER.block().get();
        for (int i = 0; i < 4; i++) {
            BlockPos at = new BlockPos(x + 6, y, z - 2 + i);
            BlockState s = locker.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST);
            level.setBlock(at, s, 3);
            locker.setPlacedBy(level, at, s, null, ItemStack.EMPTY);
        }
        level.setBlock(new BlockPos(x + 6, y + 2, z - 1), net.minecraft.world.level.block.Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                .setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.LeverBlock.POWERED, true), 3);
        // Two bunks down the west side, their heads to the back wall.
        BunkBedBlock bunk = GBConstruction.BUNK_BED.block().get();
        for (int i = 0; i < 2; i++) {
            BlockPos foot = new BlockPos(x - 6, y, z - 2 + i * 3);
            BlockState s = bunk.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
            level.setBlock(foot, s, 3);
            bunk.setPlacedBy(level, foot, s, null, ItemStack.EMPTY);
        }
        // An extinguisher by the front, its label to the room's open front; another hung on the east wall.
        level.setBlock(new BlockPos(x + 4, y, z - 5), GBConstruction.FIRE_EXTINGUISHER.block().get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        level.setBlock(new BlockPos(x + 6, y + 1, z + 4), GBConstruction.FIRE_EXTINGUISHER.block().get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST).setValue(dev.groundburst.content.construction.FireExtinguisherBlock.WALL, true), 3);
        return new BoundingBox(x - 7, o.getY(), z - 6, x + 7, y + 4, z + 7);
    }

    /** Close pictures: the room from its open front, the pipes and the valve, the fuse boxes and the duct, the lockers, the bunks, the extinguisher. */
    static List<BunkerDoorBoard.CloseUp> closeUps(BlockPos o) {
        double x = o.getX() + 0.5, y = o.getY() + 1, z = o.getZ() + 0.5;
        return List.of(
                new BunkerDoorBoard.CloseUp("fittings_front", new Vec3(x, y + 3.2, z - 9.0), 0, 22),
                new BunkerDoorBoard.CloseUp("pipes", new Vec3(x - 2.0, y + 2.0, z + 3.0), 0, 8),
                new BunkerDoorBoard.CloseUp("fuse_duct", new Vec3(x + 3.6, y + 2.2, z + 2.6), 20, 14),
                new BunkerDoorBoard.CloseUp("lockers", new Vec3(x + 2.4, y + 1.7, z - 0.2), -90, 12),
                new BunkerDoorBoard.CloseUp("bunks", new Vec3(x - 2.4, y + 1.9, z + 0.6), 90, 12),
                new BunkerDoorBoard.CloseUp("extinguisher", new Vec3(x + 3.9, y + 1.1, z - 7.0), 0, 18),
                new BunkerDoorBoard.CloseUp("extinguisher_wall", new Vec3(x + 3.6, y + 1.8, z + 4.5), -90, 10),
                new BunkerDoorBoard.CloseUp("lamps", new Vec3(x - 1.5, y + 2.9, z + 3.6), 0, 6),
                new BunkerDoorBoard.CloseUp("pipe_end", new Vec3(x - 5.8, y + 0.18, z + 4.6), -45, 5),
                new BunkerDoorBoard.CloseUp("bunk_ladder", new Vec3(x - 3.0, y - 0.42, z - 1.6), 90, 5));
    }
}
