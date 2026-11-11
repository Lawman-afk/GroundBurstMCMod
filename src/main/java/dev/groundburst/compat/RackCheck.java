package dev.groundburst.compat;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.Frame;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.naval.DepthCharge;
import dev.groundburst.content.naval.DepthChargeRackBlockEntity;
import dev.groundburst.content.naval.GBNaval;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Create check's Depth Charge Rack scene ({@link CreateCheck}; main's DESIGN.md §14.6): a rack on the ground loaded
 * by a Create funnel on its Item Port (depth charges dropped on the funnel). (The 1.21.1 build's rack aboard a Create
 * Aeronautics airship is left out: Create Aeronautics has no 1.20.1 build.)
 */
final class RackCheck {
    static final int START = 1800, END = 1960;
    /** The rack on the ground (facing north: rails and Item Port behind it to the south) and its funnel on the port. */
    private static final BlockPos GROUND = new BlockPos(200, -60, 20), FUNNEL = new BlockPos(200, -59, 23);

    private RackCheck() {}

    static void tick(Minecraft mc, int ticks) {
        switch (ticks) {
            // --- a Create funnel loads a rack through its Item Port ---------------------------------------------------------
            case 1800 -> CreateCheck.look(mc, new Vec3(196.5, -57.5, 21.5), new Vec3(200.5, -59.5, 21.5));
            case 1810 -> CreateCheck.onServer(mc, level -> {
                build(level, GROUND, 0, -1);
                CreateCheck.serverCommand(level.getServer(), "setblock " + FUNNEL.getX() + " " + FUNNEL.getY() + " " + FUNNEL.getZ()
                        + " create:andesite_funnel[facing=up]");
            });
            case 1815 -> CreateCheck.onServer(mc, level -> {
                for (int i = 0; i < 3; i++) {
                    level.addFreshEntity(new ItemEntity(level, FUNNEL.getX() + 0.5, FUNNEL.getY() + 0.7 + i * 0.3, FUNNEL.getZ() + 0.5,
                            new ItemStack(GBNaval.DEPTH_CHARGE.get()), 0, 0, 0));
                }
            });
            case 1850 -> CreateCheck.onServer(mc, level -> {
                var rack = ((Object) level.getBlockEntity(GROUND)) instanceof DepthChargeRackBlockEntity r ? r : null;
                int left = level.getEntitiesOfClass(ItemEntity.class, new AABB(FUNNEL).inflate(2)).size();
                CreateCheck.check(rack != null && rack.isFormed() && rack.loaded() == 3, "a Create funnel on the rack's Item Port loaded the charges dropped on it ("
                        + (rack == null ? "no rack" : rack.loaded() + " on the rack, formed " + rack.isFormed()) + ", " + left + " items left lying)");
            });
            case 1852 -> CreateCheck.shot(mc, "rack_funnel");
            default -> {
            }
        }
    }

    /** A formed rack facing north at {@code at}, {@code charges} on it, its depth button pressed {@code depthPresses} times. */
    private static void build(ServerLevel level, BlockPos at, int charges, int depthPresses) {
        level.setBlockAndUpdate(at, GBNaval.RACK.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        for (var cell : GBNaval.RACK_STRUCTURE.cells(at, Direction.NORTH)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER) {
                level.setBlockAndUpdate(cell.pos(), GBNaval.RACK_STRUCTURE.legend().get(cell.key()).preview().get());
            }
        }
        if (((Object) level.getBlockEntity(at)) instanceof DepthChargeRackBlockEntity rack) {
            rack.revalidate();
            for (int i = 0; i < charges; i++) {
                rack.inventory().setItem(i, new ItemStack(GBNaval.DEPTH_CHARGE.get()));
            }
            for (int i = 0; i < depthPresses; i++) {
                rack.handleButton(2);
            }
        }
    }
}
