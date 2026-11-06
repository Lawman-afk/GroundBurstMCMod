package dev.groundburst.compat;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.compat.sable.SableScenes;
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
 * The Create check's Depth Charge Rack scenes ({@link CreateCheck}; main's DESIGN.md §14.6): a rack on the ground loaded
 * by a Create funnel on its Item Port (depth charges dropped on the funnel); then a rack aboard a Create Aeronautics
 * airship turned 30 degrees, its screen's button rolling a charge off from where the ship has the rack, the way the ship
 * points it, lying across as it lay; and a redstone block put beside it aboard rolling the last one off.
 */
final class RackCheck {
    static final int START = 1800, END = 1960;
    /** The rack on the ground (facing north: rails and Item Port behind it to the south) and its funnel on the port. */
    private static final BlockPos GROUND = new BlockPos(200, -60, 20), FUNNEL = new BlockPos(200, -59, 23);
    /** The airship: levitite under a deck of planks, the rack on the deck at its north end. */
    private static final BlockPos SHIP_MIN = new BlockPos(210, -53, 18), SHIP_MAX = new BlockPos(212, -51, 24), SHIP_RACK = new BlockPos(211, -51, 18);
    private static final Vec3 SHIP_TO = new Vec3(211.5, -49, 23.5);

    private static @Nullable Object ship;
    private static @Nullable DepthChargeRackBlockEntity aboard;
    /** Where the airship's middle was last seen (the camera, not flying, is put by it again just before each picture). */
    private static volatile Vec3 seen = SHIP_TO;

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
                var rack = level.getBlockEntity(GROUND) instanceof DepthChargeRackBlockEntity r ? r : null;
                int left = level.getEntitiesOfClass(ItemEntity.class, new AABB(FUNNEL).inflate(2)).size();
                CreateCheck.check(rack != null && rack.isFormed() && rack.loaded() == 3, "a Create funnel on the rack's Item Port loaded the charges dropped on it ("
                        + (rack == null ? "no rack" : rack.loaded() + " on the rack, formed " + rack.isFormed()) + ", " + left + " items left lying)");
            });
            case 1852 -> CreateCheck.shot(mc, "rack_funnel");
            // --- a rack aboard an airship -----------------------------------------------------------------------------------
            case 1860 -> CreateCheck.look(mc, new Vec3(204.5, -47.5, 21.5), new Vec3(211.5, -50.5, 20.5));
            case 1865 -> CreateCheck.onServer(mc, level -> {
                for (BlockPos p : BlockPos.betweenClosed(SHIP_MIN, new BlockPos(SHIP_MAX.getX(), SHIP_MIN.getY(), SHIP_MAX.getZ()))) {
                    level.setBlock(p, block("aeronautics:levitite"), Block.UPDATE_ALL);
                    level.setBlock(p.above(), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
                }
                build(level, SHIP_RACK, 2, 1);
            });
            case 1870 -> CreateCheck.onServer(mc, level -> CreateCheck.serverCommand(level.getServer(), "sable assemble area " + SHIP_MIN.getX() + " "
                    + SHIP_MIN.getY() + " " + SHIP_MIN.getZ() + " " + SHIP_MAX.getX() + " " + SHIP_MAX.getY() + " " + SHIP_MAX.getZ()));
            case 1880 -> CreateCheck.onServer(mc, level -> CreateCheck.serverCommand(level.getServer(),
                    "sable teleport @l " + SHIP_TO.x + " " + SHIP_TO.y + " " + SHIP_TO.z + " 30 0"));   // turned 30 degrees
            case 1900 -> CreateCheck.onServer(mc, level -> {
                ship = SableScenes.shipIn(level, new AABB(SHIP_TO, SHIP_TO).inflate(8));
                List<DepthChargeRackBlockEntity> racks = ship == null ? List.of() : SableScenes.blockEntities(level, ship, DepthChargeRackBlockEntity.class);
                aboard = racks.isEmpty() ? null : racks.getFirst();
                if (ship != null) {
                    seen = SableScenes.box(ship).getCenter();
                }
                GroundBurst.LOGGER.info("Create check: rack ship {}", ship == null ? "none" : SableScenes.describe(ship));
                CreateCheck.check(aboard != null && aboard.isFormed() && aboard.loaded() == 2, "the rack is aboard the airship, formed, two charges on it ("
                        + (aboard == null ? "none found" : "at " + aboard.getBlockPos() + ", where the ship has it " + aboard.worldBlockPos() + ", " + aboard.loaded()
                        + " charges") + ")");
                if (aboard != null) {
                    aboard.releaseOne();                                  // its screen's button
                }
            });
            case 1912 -> CreateCheck.onServer(mc, level -> {
                var rack = aboard;
                List<DepthCharge> charges = ship == null ? List.of() : level.getEntitiesOfClass(DepthCharge.class, SableScenes.box(ship).inflate(4));
                DepthCharge c = charges.isEmpty() ? null : charges.getFirst();
                if (rack == null || c == null) {
                    CreateCheck.check(false, "the rack aboard rolled a charge off (" + charges.size() + " charges by the ship)");
                    return;
                }
                Frame frame = rack.frame();
                Direction f = rack.controllerFacing(), left = f.getCounterClockWise();
                Vec3 out = frame.directionToWorld(new Vec3(f.getStepX(), 0, f.getStepZ()));
                Vec3 axis = frame.directionToWorld(new Vec3(left.getStepX(), 0, left.getStepZ()));
                Vec3 from = rack.inWorld(rack.releasePoint());
                Vec3 middle = c.position().add(0, DepthCharge.MIDDLE, 0);
                Vec3 v = c.getDeltaMovement();
                double along = (v.x * out.x + v.z * out.z) / Math.max(1e-6, Math.hypot(v.x, v.z) * Math.hypot(out.x, out.z));
                double turn = Math.abs(Math.toDegrees(Math.atan2(Math.sin(Math.toRadians(c.axisYaw()) - Math.atan2(axis.x, axis.z)),
                        Math.cos(Math.toRadians(c.axisYaw()) - Math.atan2(axis.x, axis.z)))));
                CreateCheck.check(charges.size() == 1 && middle.distanceTo(from) < 2, "the rack aboard rolled one charge off from where the ship has it ("
                        + charges.size() + " charges, " + round(middle.distanceTo(from)) + " blocks from its release point in the world " + from + ")");
                CreateCheck.check(along > 0.8, "out the way the ship points the rack (" + round(along) + " along it; the ship's heading for it " + out + ")");
                CreateCheck.check(c.lying() && turn < 3, "lying across, its axis turned with the ship (" + (c.lying() ? round(turn) + " degrees off" : "tumbling") + ")");
            });
            case 1910 -> CreateCheck.look(mc, seen.add(-7, 2.5, 3), seen.add(0, -1.5, -2));
            case 1913 -> CreateCheck.shot(mc, "rack_ship");
            case 1920 -> CreateCheck.onServer(mc, level -> {
                if (aboard != null) {
                    level.setBlockAndUpdate(aboard.getBlockPos().east(), Blocks.REDSTONE_BLOCK.defaultBlockState());   // beside it aboard
                }
            });
            case 1945 -> CreateCheck.onServer(mc, level -> {
                int charges = ship == null ? 0 : level.getEntitiesOfClass(DepthCharge.class, SableScenes.box(ship).inflate(16)).size();
                CreateCheck.check(aboard != null && aboard.loaded() == 0, "a redstone block put beside the rack aboard rolled its last charge off ("
                        + (aboard == null ? "-" : aboard.loaded() + " left, " + charges + " charges below the ship") + ")");
            });
            case 1943 -> CreateCheck.look(mc, seen.add(-7, 2.5, 3), seen.add(0, -3, -2));
            case 1946 -> CreateCheck.shot(mc, "rack_ship_redstone");
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
        if (level.getBlockEntity(at) instanceof DepthChargeRackBlockEntity rack) {
            rack.revalidate();
            for (int i = 0; i < charges; i++) {
                rack.inventory().setItem(i, new ItemStack(GBNaval.DEPTH_CHARGE.get()));
            }
            for (int i = 0; i < depthPresses; i++) {
                rack.handleButton(2);
            }
        }
    }

    private static String round(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
    }
}
