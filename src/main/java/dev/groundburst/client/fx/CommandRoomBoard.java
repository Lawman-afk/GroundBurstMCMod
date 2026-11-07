package dev.groundburst.client.fx;

import java.util.List;

import dev.groundburst.content.construction.FilingCabinetBlock;
import dev.groundburst.content.construction.GBConstruction;
import dev.groundburst.content.construction.MapTableBlock;
import dev.groundburst.content.construction.TapeComputerBlock;
import dev.groundburst.content.launch.GBLaunch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The command room, for the site photographer ({@code -Psite=room}; DESIGN.md §22.4): a concrete room open to the sky
 * (for the light), its back wall lined with Tape Computers, a row of Control Consoles down one side, two Command Desks,
 * two Filing Cabinets (one open), the Situation Map Table in its middle; from all round, and close up.
 */
final class CommandRoomBoard {
    private CommandRoomBoard() {}

    static BoundingBox build(ServerLevel level, BlockPos o) {
        int x = o.getX(), y = o.getY() + 1, z = o.getZ();
        BlockState concrete = GBLaunch.REINFORCED_CONCRETE.block().get().defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(x - 7, y - 1, z - 6, x + 7, y - 1, z + 7)) {
            level.setBlock(p, concrete, 2);
        }
        for (BlockPos p : BlockPos.betweenClosed(x - 7, y, z - 6, x + 7, y + 3, z + 7)) {
            boolean wall = p.getX() == x - 7 || p.getX() == x + 7 || p.getZ() == z + 7;
            if (wall) {
                level.setBlock(p, concrete, 2);
            }
        }
        // The tape wall: five drives along the back, facing into the room.
        TapeComputerBlock tape = GBConstruction.TAPE_COMPUTER.block().get();
        for (int i = 0; i < 5; i++) {
            BlockPos at = new BlockPos(x - 4 + i * 2 - (i > 2 ? 1 : 0), y, z + 6);
            BlockState s = tape.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(TapeComputerBlock.ACTIVE, i != 3)
                    .setValue(TapeComputerBlock.SWITCHED, i != 3);
            level.setBlock(at, s, 3);
            tape.setPlacedBy(level, at, s, null, ItemStack.EMPTY);
        }
        // The consoles down the west side, facing east into the room.
        for (int i = 0; i < 4; i++) {
            level.setBlock(new BlockPos(x - 6, y, z - 1 + i), GBConstruction.CONTROL_CONSOLE.block().get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST), 2);
        }
        // Two desks by the east side, facing the room's front; the cabinets by the east wall, one open.
        for (int i = 0; i < 2; i++) {
            level.setBlock(new BlockPos(x + 3 + i, y, z + 1), GBConstruction.COMMAND_DESK.block().get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 2);
            level.setBlock(new BlockPos(x + 6, y, z + 3 + i), GBConstruction.FILING_CABINET.block().get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST).setValue(FilingCabinetBlock.OPEN, i == 0), 2);
        }
        // The map table in the middle.
        MapTableBlock table = GBConstruction.SITUATION_MAP_TABLE.block().get();
        BlockPos base = new BlockPos(x - 1, y, z - 1);
        BlockState ts = table.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        level.setBlock(base, ts, 3);
        table.setPlacedBy(level, base, ts, null, ItemStack.EMPTY);
        return new BoundingBox(x - 7, o.getY(), z - 6, x + 7, y + 4, z + 7);
    }

    /** Close pictures: the room from its open front, the tape wall, the consoles, the desks, the cabinets, the table. */
    static List<BunkerDoorBoard.CloseUp> closeUps(BlockPos o) {
        double x = o.getX() + 0.5, y = o.getY() + 1, z = o.getZ() + 0.5;
        return List.of(
                new BunkerDoorBoard.CloseUp("room_front", new Vec3(x, y + 3.2, z - 9.0), 0, 22),
                new BunkerDoorBoard.CloseUp("tape_wall", new Vec3(x - 1.5, y + 1.7, z + 1.5), 0, 4),
                new BunkerDoorBoard.CloseUp("tape_reels", new Vec3(x - 2.5, y + 1.6, z + 2.6), 0, 0),
                new BunkerDoorBoard.CloseUp("consoles", new Vec3(x - 2.6, y + 1.9, z - 3.4), 58, 26),
                new BunkerDoorBoard.CloseUp("desks", new Vec3(x + 3.0, y + 1.7, z - 2.0), 0, 22),
                new BunkerDoorBoard.CloseUp("cabinets", new Vec3(x + 3.6, y + 1.6, z + 3.8), -90, 18),
                new BunkerDoorBoard.CloseUp("map_table", new Vec3(x - 0.5, y + 3.4, z - 4.6), 0, 48));
    }
}
