package dev.groundburst.client.fx;

import java.util.List;

import dev.groundburst.content.construction.GBConstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Construction set in use, for the site photographer (DESIGN.md §22.2): a corrugated shed with a gable roof, a
 * floodlight over its door and a sign by it; chain-link with barbed wire along its top; a lattice tower with a grating
 * deck and floodlights, and a lattice mast; I-beams framing a gate; a sandbag wall; the signs on a panel wall; a small
 * radome; and a two-block cube of each full block, to see how each tiles.
 */
final class ConstructionBoard {
    private ConstructionBoard() {}

    static BoundingBox build(ServerLevel level, BlockPos o) {
        int x = o.getX(), y = o.getY() + 1, z = o.getZ();
        // Two-block cubes of each full block in a row along the north.
        List<Block> cubes = List.of(GBConstruction.CORRUGATED_STEEL.block().get(), GBConstruction.RUSTED_CORRUGATED_STEEL.block().get(),
                GBConstruction.STEEL_PANEL.block().get(), GBConstruction.RUSTED_STEEL_PANEL.block().get(),
                GBConstruction.STEEL_GRATING.block().get(), GBConstruction.SANDBAGS.block().get(), GBConstruction.RADOME_PANEL.block().get(),
                GBConstruction.LATTICE_FRAME.block().get());
        for (int i = 0; i < cubes.size(); i++) {
            fill(level, x - 16 + i * 4, y, z - 12, x - 15 + i * 4, y + 1, z - 11, cubes.get(i).defaultBlockState());
        }
        // The shed: corrugated walls 3 high round 7 x 5, a door in its north wall, a gable roof of corrugated stairs.
        BlockState wall = GBConstruction.CORRUGATED_STEEL.block().get().defaultBlockState();
        int sx = x - 14, sz = z - 2;
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                if (dx == 0 || dx == 6 || dz == 0 || dz == 4) {
                    fill(level, sx + dx, y, sz + dz, sx + dx, y + 2, sz + dz, wall);
                }
            }
        }
        fill(level, sx + 3, y, sz, sx + 3, y + 1, sz, Blocks.AIR.defaultBlockState());
        BlockState stair = GBConstruction.CORRUGATED_STEEL_STAIRS.block().get().defaultBlockState();
        for (int dx = -1; dx <= 7; dx++) {
            level.setBlock(new BlockPos(sx + dx, y + 3, sz - 1), stair.setValue(StairBlock.FACING, Direction.SOUTH), 2);
            level.setBlock(new BlockPos(sx + dx, y + 3, sz), stair.setValue(StairBlock.FACING, Direction.SOUTH), 2);
            level.setBlock(new BlockPos(sx + dx, y + 4, sz + 1), stair.setValue(StairBlock.FACING, Direction.SOUTH), 2);
            level.setBlock(new BlockPos(sx + dx, y + 4, sz + 2), GBConstruction.CORRUGATED_STEEL_SLAB.block().get().defaultBlockState(), 2);
            level.setBlock(new BlockPos(sx + dx, y + 4, sz + 3), stair.setValue(StairBlock.FACING, Direction.NORTH), 2);
            level.setBlock(new BlockPos(sx + dx, y + 3, sz + 4), stair.setValue(StairBlock.FACING, Direction.NORTH), 2);
            level.setBlock(new BlockPos(sx + dx, y + 3, sz + 5), stair.setValue(StairBlock.FACING, Direction.NORTH), 2);
        }
        for (int dz = 1; dz <= 3; dz++) {
            level.setBlock(new BlockPos(sx, y + 3, sz + dz), wall, 2);
            level.setBlock(new BlockPos(sx + 6, y + 3, sz + dz), wall, 2);
        }
        level.setBlock(new BlockPos(sx + 3, y + 2, sz - 1),
                GBConstruction.FLOODLIGHT.block().get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH), 2);
        level.setBlock(new BlockPos(sx + 5, y + 1, sz - 1),
                GBConstruction.RADIATION_SIGN.block().get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 2);
        // Chain-link along the south, two high, barbed wire along its top; a gate between two I-beam posts.
        BlockState mesh = GBConstruction.CHAIN_LINK_FENCE.block().get().defaultBlockState();
        BlockState barbed = GBConstruction.BARBED_WIRE.block().get().defaultBlockState();
        for (int dx = -16; dx <= 16; dx++) {
            if (dx >= -1 && dx <= 1) {
                continue;
            }
            fill(level, x + dx, y, z + 10, x + dx, y + 1, z + 10, mesh);
            level.setBlock(new BlockPos(x + dx, y + 2, z + 10), barbed, 2);
        }
        BlockState beam = GBConstruction.I_BEAM.block().get().defaultBlockState();
        fill(level, x - 2, y, z + 10, x - 2, y + 3, z + 10, beam);
        fill(level, x + 2, y, z + 10, x + 2, y + 3, z + 10, beam);
        fill(level, x - 2, y + 4, z + 10, x + 2, y + 4, z + 10, beam.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        // A lattice tower, 3 x 3 and 10 high, a grating deck over it with a slab rail, floodlights at its corners.
        BlockState lattice = GBConstruction.LATTICE_FRAME.block().get().defaultBlockState();
        fill(level, x + 4, y, z - 1, x + 6, y + 9, z + 1, lattice);
        fill(level, x + 3, y + 10, z - 2, x + 7, y + 10, z + 2, GBConstruction.STEEL_GRATING.block().get().defaultBlockState());
        for (Direction d : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            BlockPos at = new BlockPos(x + 5, y + 11, z).relative(d, 2);
            level.setBlock(at, GBConstruction.FLOODLIGHT.block().get().defaultBlockState().setValue(DirectionalBlock.FACING, d), 2);
        }
        // A lattice mast, one wide, a floodlight on top facing up.
        fill(level, x + 10, y, z, x + 10, y + 7, z, lattice);
        level.setBlock(new BlockPos(x + 10, y + 8, z),
                GBConstruction.FLOODLIGHT.block().get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP), 2);
        // A sandbag wall, two high with slabs on top.
        fill(level, x - 4, y, z + 4, x + 1, y + 1, z + 4, GBConstruction.SANDBAGS.block().get().defaultBlockState());
        fill(level, x - 4, y + 2, z + 4, x + 1, y + 2, z + 4,
                GBConstruction.SANDBAG_SLAB.block().get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        // The signs on a steel panel wall, facing north.
        fill(level, x + 13, y, z - 4, x + 18, y + 2, z - 4, GBConstruction.STEEL_PANEL.block().get().defaultBlockState());
        List<Block> signs = List.of(GBConstruction.RADIATION_SIGN.block().get(), GBConstruction.HIGH_VOLTAGE_SIGN.block().get(),
                GBConstruction.KEEP_OUT_SIGN.block().get(), GBConstruction.TOXIC_SIGN.block().get());
        for (int i = 0; i < signs.size(); i++) {
            level.setBlock(new BlockPos(x + 14 + i, y + 1, z - 5),
                    signs.get(i).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 2);
        }
        // A small radome: a dome of panels nine across.
        BlockState panel = GBConstruction.RADOME_PANEL.block().get().defaultBlockState();
        int rx = x + 18, rz = z + 4;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = 0; dy <= 5; dy++) {
                    double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (r <= 4.6 && r > 3.6) {
                        level.setBlock(new BlockPos(rx + dx, y + dy, rz + dz), panel, 2);
                    }
                }
            }
        }
        // A Search Radar turning on a lattice tower six high, and a wreck on the ground.
        fill(level, x - 6, y, z + 20, x - 4, y + 5, z + 22, lattice);
        level.setBlock(new BlockPos(x - 5, y + 6, z + 21), GBConstruction.SEARCH_RADAR.block().get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(dev.groundburst.content.construction.SearchRadarBlock.ACTIVE, true), 2);
        level.setBlock(new BlockPos(x + 9, y, z + 22), GBConstruction.SEARCH_RADAR_WRECK.block().get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 2);
        BoundingBox box = new BoundingBox(x - 17, o.getY(), z - 13, x + 24, y + 12, z + 28);
        // Fencing and stairs join their neighbours from the blocks round them: set each again once all are down.
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock || s.getBlock() instanceof StairBlock) {
                level.setBlock(p, Block.updateFromNeighbourShapes(s, level, p), 2);
            }
        }
        return box;
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            level.setBlock(p, state, 2);
        }
    }
}
