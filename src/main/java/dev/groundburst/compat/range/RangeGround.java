package dev.groundburst.compat.range;

import static dev.groundburst.compat.range.TestRange.cruise;
import static dev.groundburst.compat.range.TestRange.fill;
import static dev.groundburst.compat.range.TestRange.item;
import static dev.groundburst.compat.range.TestRange.load;
import static dev.groundburst.compat.range.TestRange.missile;
import static dev.groundburst.compat.range.TestRange.set;
import static dev.groundburst.compat.range.TestRange.sign;
import static dev.groundburst.compat.range.TestRange.state;
import static dev.groundburst.compat.range.TestRange.structure;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.CruiseLauncherBlockEntity;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.machine.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The test range's ground: its pads, paths and lake, the command post, the launch row and artillery, the target range. */
final class RangeGround {
    private RangeGround() {}

    private static final BlockState CONCRETE = Blocks.GRAY_CONCRETE.defaultBlockState(), PAD = Blocks.SMOOTH_STONE.defaultBlockState(),
            PATH = Blocks.GRAVEL.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState();

    /** Pads and paths; the lake (six deep, a sand shore); the airfield. */
    static void groundwork(ServerLevel level) {
        fill(level, -15, -1, -11, 15, -1, 12, CONCRETE);                       // the command post
        fill(level, -58, -1, -54, 50, -1, -30, PAD);                           // the launch row and artillery
        fill(level, -1, -1, -30, 1, -1, -12, PATH);                            // the path north to it
        fill(level, 16, -1, -1, 46, -1, 1, PATH);                              // and east to the contraptions
        fill(level, 46, -1, -22, 102, -1, 14, Blocks.POLISHED_ANDESITE.defaultBlockState());
        fill(level, 20, -1, 40, 74, -1, 112, PAD);                             // the airfield
        fill(level, -1, -1, 13, 1, -1, 40, PATH);                              // and the path south to it
        // The lake: sand round it, water six deep.
        fill(level, -114, -1, 16, -46, -1, 104, Blocks.SAND.defaultBlockState());
        fill(level, -110, -7, 20, -50, -7, 100, Blocks.SAND.defaultBlockState());
        fill(level, -110, -6, 20, -50, -1, 100, Blocks.WATER.defaultBlockState());
        fill(level, -46, -1, -1, -16, -1, 1, PATH);                            // the path west to it
        GroundBurst.LOGGER.info("Test range: ground laid");
    }

    /** The Launch Control Station, the Launch Computer and the Radar Array, powered; the armory; the signposts. */
    static void commandPost(ServerLevel level) {
        structure(level, TestRange.def("station"), TestRange.STATION, Direction.SOUTH, true);
        structure(level, TestRange.def("computer"), TestRange.COMPUTER, Direction.SOUTH, true);
        structure(level, TestRange.def("radar"), TestRange.RADAR, Direction.SOUTH, true);
        sign(level, TestRange.STATION.south(2), Direction.SOUTH, "Launch Control", "Station:", "the network's", "launchers");
        sign(level, TestRange.COMPUTER.south(2), Direction.SOUTH, "Launch", "Computer:", "aim and fire", "nearby launchers");
        sign(level, TestRange.RADAR.south(2), Direction.SOUTH, "Radar Array", "tracks flights", "", "");

        // The armory, facing the spawn.
        int z = 3;
        TestRange.chest(level, new BlockPos(-9, 0, z), Direction.SOUTH, "Cruise missiles",
                cruise("he_warhead"), cruise("he_warhead"), cruise("he_warhead"), cruise("he_warhead"), cruise("thermobaric_warhead"),
                cruise("thermobaric_warhead"), cruise("napalm_warhead"), cruise("napalm_warhead"), cruise("cluster_warhead"), cruise("gas_warhead"),
                cruise("emp_warhead"), cruise("smoke_warhead"));
        TestRange.chest(level, new BlockPos(-6, 0, z), Direction.SOUTH, "Missiles",
                missile("he_warhead", SizeClass.M, MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS),
                missile("he_warhead", SizeClass.M, MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS),
                missile("he_warhead", SizeClass.M, MissileParts.Motor.SRM_II, MissileParts.Guidance.LASER),
                missile("he_warhead", SizeClass.M, MissileParts.Motor.SRM_II, MissileParts.Guidance.LASER),
                missile("thermobaric_warhead", SizeClass.M, MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS),
                missile("cluster_warhead", SizeClass.M, MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS),
                missile("gas_warhead", SizeClass.M, MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS),
                missile("heavy_warhead", SizeClass.L, MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS),
                missile("bunker_buster_warhead", SizeClass.L, MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS),
                missile("massive_warhead", SizeClass.L, MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS),
                missile("he_warhead", SizeClass.S, MissileParts.Motor.SRM_I, MissileParts.Guidance.GPS),
                missile("he_warhead", SizeClass.S, MissileParts.Motor.SRM_I, MissileParts.Guidance.GPS));
        TestRange.chest(level, new BlockPos(-3, 0, z), Direction.SOUTH, "Strategic",
                missile("tactical_nuclear_warhead", SizeClass.XL, MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS),
                missile("fission_warhead", SizeClass.XL, MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS),
                missile("thermonuclear_warhead", SizeClass.XL, MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS),
                missile("antimatter_warhead", SizeClass.XL, MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS),
                missile("cryo_warhead", SizeClass.XL, MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS),
                missile("nuclear_missile_warhead", SizeClass.XXL, MissileParts.Motor.ICBM, MissileParts.Guidance.GPS),
                item("groundburst:singularity_warhead", 1), item("groundburst:storm_warhead", 1));
        TestRange.chest(level, new BlockPos(3, 0, z), Direction.SOUTH, "Shells",
                item("groundburst:mortar_shell_he", 32), item("groundburst:mortar_shell_incendiary", 16), item("groundburst:mortar_shell_smoke", 16),
                item("groundburst:mortar_shell_chemical", 16), item("groundburst:mortar_shell_cryo", 16), item("groundburst:mortar_shell_emp", 16),
                item("groundburst:howitzer_shell_he", 32), item("groundburst:howitzer_shell_incendiary", 16));
        TestRange.chest(level, new BlockPos(6, 0, z), Direction.SOUTH, "Explosives",
                item("groundburst:c4", 32), item("groundburst:remote_detonator", 1), item("groundburst:dynamite", 32), item("groundburst:frag_grenade", 16),
                item("groundburst:sticky_grenade", 16), item("groundburst:incendiary_grenade", 16), item("groundburst:molotov_cocktail", 16),
                item("groundburst:smoke_grenade", 16), item("groundburst:gas_grenade", 16), item("groundburst:emp_grenade", 16),
                item("groundburst:cryo_grenade", 16), item("groundburst:landmine", 16), item("groundburst:claymore", 16),
                item("groundburst:thermite_charge", 16), item("groundburst:breaching_charge", 16), item("groundburst:timed_bomb", 8));
        TestRange.chest(level, new BlockPos(9, 0, z), Direction.SOUTH, "Gear",
                item("groundburst:laser_designator", 1), item("groundburst:field_manual", 1), item("groundburst:wrench", 1),
                item("groundburst:geiger_counter", 1), item("groundburst:dosimeter_badge", 1), item("groundburst:hazmat_hood", 1),
                item("groundburst:hazmat_coat", 1), item("groundburst:hazmat_trousers", 1), item("groundburst:hazmat_boots", 1),
                item("groundburst:battery", 4), item("create:wrench", 1), item("create:goggles", 1));
        set(level, new BlockPos(12, 0, z), state("groundburst:battery_charger"));
        set(level, new BlockPos(13, 0, z), state("groundburst:creative_capacitor_bank"));

        // Signposts.
        sign(level, new BlockPos(-2, 0, 10), Direction.SOUTH, "N: launch row", "E: contraptions", "W: the lake,", "the missile boat");
        sign(level, new BlockPos(2, 0, 10), Direction.SOUTH, "S: airfield", "and airships", "Target range:", "X 0  Z -420");
        GroundBurst.LOGGER.info("Test range: command post built");
    }

    /** One of each launcher, loaded, charged and kept charged; the mortar and the howitzer. */
    static void launchRow(ServerLevel level) {
        structure(level, TestRange.def("silo"), TestRange.SILO, Direction.NORTH, true);
        if (TestRange.silo(level, TestRange.SILO) instanceof LaunchSiloBlockEntity s) {
            load(s, LaunchSiloBlockEntity.SLOT_MISSILE, missile("he_warhead", SizeClass.M, MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS));
        }
        TestRange.launcher(TestRange.SILO);
        sign(level, TestRange.SILO.offset(-3, 0, 1), Direction.SOUTH, "Launch Silo", "medium HE", "", "");

        structure(level, TestRange.def("cruise"), TestRange.CRUISE, Direction.NORTH, true);
        if (TestRange.cruiseAt(level, TestRange.CRUISE) instanceof CruiseLauncherBlockEntity c) {
            load(c, CruiseLauncherBlockEntity.SLOT_A, cruise("he_warhead"));
            load(c, CruiseLauncherBlockEntity.SLOT_B, cruise("thermobaric_warhead"));
        }
        TestRange.launcher(TestRange.CRUISE);
        sign(level, TestRange.CRUISE.offset(-2, 0, 1), Direction.SOUTH, "Cruise Missile", "Launcher", "", "");

        structure(level, TestRange.def("vls"), TestRange.VLS, Direction.NORTH, true);
        if (level.getBlockEntity(TestRange.VLS) instanceof MachineBlockEntity vls) {
            for (int i = 0; i < 16; i++) {
                load(vls, i, missile(i % 4 == 3 ? "cluster_warhead" : "he_warhead", i % 2 == 0 ? SizeClass.S : SizeClass.M,
                        i % 2 == 0 ? MissileParts.Motor.SRM_I : MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS));
            }
        }
        TestRange.launcher(TestRange.VLS);
        sign(level, TestRange.VLS.offset(-3, 0, 1), Direction.SOUTH, "Vertical Launch", "Battery", "16 cells", "");

        structure(level, TestRange.def("complex"), TestRange.COMPLEX, Direction.NORTH, true);
        if (TestRange.silo(level, TestRange.COMPLEX) instanceof LaunchSiloBlockEntity s) {
            load(s, LaunchSiloBlockEntity.SLOT_MISSILE, missile("massive_warhead", SizeClass.XL, MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS));
        }
        TestRange.launcher(TestRange.COMPLEX);
        sign(level, TestRange.COMPLEX.offset(-4, 0, 1), Direction.SOUTH, "Launch Complex", "strategic", "missiles", "");

        structure(level, TestRange.def("missile_silo"), TestRange.MISSILE_SILO, Direction.NORTH, true);
        if (TestRange.silo(level, TestRange.MISSILE_SILO) instanceof LaunchSiloBlockEntity s) {
            load(s, LaunchSiloBlockEntity.SLOT_MISSILE, missile("nuclear_missile_warhead", SizeClass.XXL, MissileParts.Motor.ICBM, MissileParts.Guidance.GPS));
        }
        TestRange.launcher(TestRange.MISSILE_SILO);
        sign(level, TestRange.MISSILE_SILO.offset(-4, 0, 1), Direction.SOUTH, "Missile Silo", "an ICBM:", "nuclear! aim", "far away");

        // Artillery: the 81 mm Mortar and the 155 mm Howitzer, facing north, their shells between them.
        set(level, TestRange.MORTAR, state("groundburst:mortar", "facing=north"));
        set(level, TestRange.HOWITZER, state("groundburst:howitzer", "facing=north"));
        TestRange.chest(level, TestRange.MORTAR.offset(4, 0, 2), Direction.SOUTH, "Shells",
                item("groundburst:mortar_shell_he", 64), item("groundburst:mortar_shell_incendiary", 32), item("groundburst:mortar_shell_smoke", 16),
                item("groundburst:howitzer_shell_he", 64), item("groundburst:howitzer_shell_incendiary", 32));
        sign(level, TestRange.MORTAR.south(2), Direction.SOUTH, "81 mm Mortar", "right-click", "to crew it", "");
        sign(level, TestRange.HOWITZER.south(2), Direction.SOUTH, "155 mm Howitzer", "reaches 600", "blocks", "");
        GroundBurst.LOGGER.info("Test range: launch row built");
    }

    /** A small town to aim at: houses round a square, a bunker, a water tower, a row of "tanks". */
    static void targetRange(ServerLevel level) {
        BlockPos c = TestRange.RANGE;
        fill(level, c.getX() - 40, -1, c.getZ() - 40, c.getX() + 40, -1, c.getZ() + 40, Blocks.COARSE_DIRT.defaultBlockState());
        fill(level, c.getX() - 3, -1, c.getZ() - 40, c.getX() + 3, -1, c.getZ() + 40, PATH);
        fill(level, c.getX() - 40, -1, c.getZ() - 3, c.getX() + 40, -1, c.getZ() + 3, PATH);
        int[][] houses = {{-20, -20}, {-20, 8}, {8, -20}, {8, 8}, {-34, -6}, {22, -6}, {-6, 22}, {-6, -34}};
        BlockState[] walls = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.BRICKS.defaultBlockState(),
                Blocks.WHITE_TERRACOTTA.defaultBlockState()};
        for (int i = 0; i < houses.length; i++) {
            house(level, c.getX() + houses[i][0], c.getZ() + houses[i][1], 10, 8, 5 + i % 3, walls[i % walls.length]);
        }
        // A concrete bunker with an obsidian core, half sunk.
        fill(level, c.getX() + 26, -4, c.getZ() + 18, c.getX() + 36, 3, c.getZ() + 28, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        fill(level, c.getX() + 29, -3, c.getZ() + 21, c.getX() + 33, 2, c.getZ() + 25, Blocks.OBSIDIAN.defaultBlockState());
        fill(level, c.getX() + 30, -2, c.getZ() + 22, c.getX() + 32, 1, c.getZ() + 24, AIR);
        // A water tower.
        for (int[] leg : new int[][] {{-30, 26}, {-26, 26}, {-30, 30}, {-26, 30}}) {
            fill(level, c.getX() + leg[0], 0, c.getZ() + leg[1], c.getX() + leg[0], 10, c.getZ() + leg[1], Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        fill(level, c.getX() - 31, 11, c.getZ() + 25, c.getX() - 25, 16, c.getZ() + 31, Blocks.SPRUCE_PLANKS.defaultBlockState());
        fill(level, c.getX() - 30, 12, c.getZ() + 26, c.getX() - 26, 16, c.getZ() + 30, Blocks.WATER.defaultBlockState());
        // "Tanks": iron hulls with a turret.
        for (int i = 0; i < 4; i++) {
            int x = c.getX() - 12 + i * 8, z = c.getZ() + 34;
            fill(level, x, 0, z, x + 3, 1, z + 5, Blocks.IRON_BLOCK.defaultBlockState());
            fill(level, x + 1, 2, z + 1, x + 2, 2, z + 3, Blocks.IRON_BLOCK.defaultBlockState());
            fill(level, x + 1, 2, z - 2, x + 1, 2, z, Blocks.IRON_BARS.defaultBlockState());
        }
        sign(level, c.offset(0, 0, 4), Direction.SOUTH, "TARGET RANGE", "X " + c.getX() + "  Z " + c.getZ(), "", "");
        GroundBurst.LOGGER.info("Test range: target range built at {}", c);
    }

    /**
     * Scenery: a jetty out to the missile boat, a mooring mast behind the airship, and trees (a wood west of the launch row,
     * groves round the lake and the airfield, a few in the target town to blow away).
     */
    static void scenery(ServerLevel level) {
        // The jetty: a spruce deck on log piles from the east shore out to the boat.
        fill(level, -79, 0, 46, -48, 0, 48, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int x = -78; x <= -48; x += 6) {
            fill(level, x, -7, 45, x, 0, 45, Blocks.SPRUCE_LOG.defaultBlockState());
            fill(level, x, -7, 49, x, 0, 49, Blocks.SPRUCE_LOG.defaultBlockState());
            set(level, new BlockPos(x, 1, 45), Blocks.LANTERN.defaultBlockState());
        }
        // The mooring mast: a lattice tower behind the airship's stern, a platform on top.
        fill(level, 40, 0, 86, 42, 25, 88, Blocks.IRON_BARS.defaultBlockState());
        fill(level, 41, 0, 87, 41, 25, 87, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
        fill(level, 39, 26, 85, 43, 26, 89, Blocks.SPRUCE_SLAB.defaultBlockState());
        set(level, new BlockPos(41, 27, 87), Blocks.LANTERN.defaultBlockState());
        // Trees.
        var trees = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE);
        var kinds = java.util.List.of(net.minecraft.data.worldgen.features.TreeFeatures.OAK, net.minecraft.data.worldgen.features.TreeFeatures.FANCY_OAK,
                net.minecraft.data.worldgen.features.TreeFeatures.BIRCH, net.minecraft.data.worldgen.features.TreeFeatures.SPRUCE);
        var random = net.minecraft.util.RandomSource.create(20261104L);
        int[][] groves = {{-100, -70, -64, -10}, {-124, -112, 10, 110}, {-46, -36, 50, 110}, {78, 96, 40, 110}, {-40, 40, -470, -455}};
        int placed = 0;
        for (int[] g : groves) {
            for (int x = g[0]; x <= g[1]; x += 6) {
                for (int z = g[2]; z <= g[3]; z += 6) {
                    BlockPos at = new BlockPos(x + random.nextInt(4) - 2, 0, z + random.nextInt(4) - 2);
                    if (!level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK) || random.nextInt(4) == 0) {
                        continue;
                    }
                    var kind = trees.getHolder(kinds.get(random.nextInt(kinds.size())));
                    if (kind.isPresent() && kind.get().value().place(level, level.getChunkSource().getGenerator(), random, at)) {
                        placed++;
                    }
                }
            }
        }
        GroundBurst.LOGGER.info("Test range: scenery: {} trees", placed);
    }

    /** A house: walls with windows and a door, a flat roof with a rim. */
    private static void house(ServerLevel level, int x, int z, int w, int d, int h, BlockState wall) {
        fill(level, x, 0, z, x + w - 1, h - 1, z + d - 1, wall);
        fill(level, x + 1, 0, z + 1, x + w - 2, h - 1, z + d - 2, AIR);
        for (int i = 2; i < w - 2; i += 3) {
            set(level, new BlockPos(x + i, 2, z), Blocks.GLASS_PANE.defaultBlockState());
            set(level, new BlockPos(x + i, 2, z + d - 1), Blocks.GLASS_PANE.defaultBlockState());
        }
        fill(level, x + w / 2, 0, z + d - 1, x + w / 2, 1, z + d - 1, AIR);
        fill(level, x, h, z, x + w - 1, h, z + d - 1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        fill(level, x + 1, h + 1, z + 1, x + w - 2, h + 1, z + d - 2, Blocks.DARK_OAK_SLAB.defaultBlockState());
    }
}
