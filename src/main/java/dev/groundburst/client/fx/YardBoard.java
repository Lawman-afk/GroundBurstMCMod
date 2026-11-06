package dev.groundburst.client.fx;

import java.util.List;

import dev.groundburst.content.construction.AmmoCratesBlock;
import dev.groundburst.content.construction.DrumBlock;
import dev.groundburst.content.construction.GBConstruction;
import dev.groundburst.content.construction.SearchlightBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The outdoor defences and the yard, for the site photographer ({@code -Psite=yard}; DESIGN.md §22.6): on the grass, a
 * row of Jersey Barriers, two staggered rows of Dragon's Teeth, three Czech Hedgehogs, a Searchlight switched on, Ammo
 * Crates on their pallets (one open) and the drums (four, three, two); from all round and close up, and the searchlight
 * at midnight (a picture whose name ends "_night" is taken then).
 */
final class YardBoard {
    private YardBoard() {}

    static BoundingBox build(ServerLevel level, BlockPos o) {
        int x = o.getX(), y = o.getY() + 1, z = o.getZ();
        for (int i = 0; i < 6; i++) {
            level.setBlock(new BlockPos(x - 7 + i, y, z - 5), GBConstruction.JERSEY_BARRIER.block().get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        }
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < 4; i++) {
                level.setBlock(new BlockPos(x - 7 + i * 2 + row, y, z - 2 + row), GBConstruction.DRAGONS_TEETH.block().get().defaultBlockState(), 3);
            }
        }
        int[][] hedgehogs = {{2, -5}, {5, -4}, {3, -2}};
        Direction[] turns = {Direction.NORTH, Direction.EAST, Direction.WEST};
        for (int i = 0; i < hedgehogs.length; i++) {
            level.setBlock(new BlockPos(x + hedgehogs[i][0], y, z + hedgehogs[i][1]), GBConstruction.CZECH_HEDGEHOG.block().get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, turns[i]), 3);
        }
        SearchlightBlock light = GBConstruction.SEARCHLIGHT.block().get();
        BlockPos at = new BlockPos(x + 4, y, z + 2);
        BlockState ls = light.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(SearchlightBlock.ACTIVE, true);
        level.setBlock(at, ls, 3);
        light.setPlacedBy(level, at, ls, null, ItemStack.EMPTY);
        for (int i = 0; i < 2; i++) {
            level.setBlock(new BlockPos(x - 6 + i, y, z + 3), GBConstruction.AMMO_CRATES.block().get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(AmmoCratesBlock.OPEN, i == 1), 3);
        }
        var drums = List.of(GBConstruction.OIL_DRUM, GBConstruction.FUEL_DRUM, GBConstruction.RUSTY_DRUM);
        for (int i = 0; i < drums.size(); i++) {
            level.setBlock(new BlockPos(x - 3 + i, y, z + 3), drums.get(i).block().get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(DrumBlock.DRUMS, 4 - i), 3);
        }
        return new BoundingBox(x - 8, o.getY(), z - 6, x + 7, y + 3, z + 5);
    }

    /** Close pictures: the yard from the north, the barriers and teeth, the hedgehogs, the light by day and at midnight, the stores. */
    static List<BunkerDoorBoard.CloseUp> closeUps(BlockPos o) {
        double x = o.getX() + 0.5, y = o.getY() + 1, z = o.getZ() + 0.5;
        return List.of(
                new BunkerDoorBoard.CloseUp("yard_front", new Vec3(x, y + 5.0, z - 15.0), 0, 22),
                new BunkerDoorBoard.CloseUp("barriers_teeth", new Vec3(x - 3.5, y + 2.2, z - 9.5), 10, 14),
                new BunkerDoorBoard.CloseUp("hedgehogs", new Vec3(x + 2.5, y + 2.0, z - 9.0), -15, 12),
                new BunkerDoorBoard.CloseUp("searchlight", new Vec3(x + 1.5, y + 1.8, z - 1.5), -35, 4),
                new BunkerDoorBoard.CloseUp("stores", new Vec3(x - 3.0, y + 2.4, z - 1.0), 0, 30),
                new BunkerDoorBoard.CloseUp("searchlight_night", new Vec3(x - 8.0, y + 3.0, z - 14.0), -20, -30));
    }
}
