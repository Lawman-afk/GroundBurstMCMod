package dev.groundburst.port;

import java.io.File;
import java.util.List;

import dev.groundburst.GroundBurst;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * This branch's check that the mod draws on 1.21.1 ({@code gradlew runPortCheck}): makes a flat creative world, sets the
 * mod's blocks out in a row (machines, launchers, guns, charges, warheads; a Warning Beacon lit by redstone; buckets in
 * the hotbar), photographs them, opens the Field Manual, sets a blast off and photographs that, brings a Particle Uplink
 * beam down beside the row and photographs it and its trench, lays sea mines in a walled sea and photographs them from
 * under the water and from above, sets a depth charge off under them and photographs its column, then quits. The pictures go to
 * run/screenshots/port_*.png; anything that goes wrong is in the log.
 */
public final class PortCheck {
    private static final boolean ON = Boolean.getBoolean("groundburst.portCheck");
    private static final String WORLD = "port_check";
    private static final List<String> BLOCKS = List.of("assembly_bench", "combustion_generator", "arc_furnace", "capacitor_bank",
            "air_raid_siren", "alert_display", "c4", "he_warhead", "howitzer", "barbed_wire", "corrugated_steel", "dead_grass_block",
            "cryo_crystal", "chain_link_fence", "emergency_light");
    private static boolean started;
    private static int ticks = -1;

    private PortCheck() {}

    public static void register() {
        if (!ON) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            // Once the game has loaded (whatever screen it shows first: the title, or a warning), make the world.
            if (!started && mc.getOverlay() == null && mc.level == null && mc.screen != null) {
                started = true;
                GroundBurst.LOGGER.info("Port check: making the world (from {})", mc.screen.getClass().getSimpleName());
                createWorld();
            }
            tick(mc);
        });
    }

    private static void createWorld() {
        Minecraft mc = Minecraft.getInstance();
        try (var access = mc.getLevelSource().createAccess(WORLD)) {
            access.deleteLevel();
        } catch (Exception ignored) {
            // none yet
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261103L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.screen);
    }

    private static void command(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "port_" + name + ".png", mc.getMainRenderTarget(), message -> GroundBurst.LOGGER.info("Port check: {}", message.getString()));
    }

    /** Runs {@code action} on the server's thread, in the overworld. */
    private static void onServer(Minecraft mc, java.util.function.Consumer<net.minecraft.server.level.ServerLevel> action) {
        var server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> action.accept(server.overworld()));
        }
    }

    /** Clears the chat and the toasts a moment before a picture. */
    private static void tidy(Minecraft mc) {
        mc.gui.getChat().clearMessages(false);
        mc.getToasts().clear();
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        // Never paused (the window may be behind another): a paused game stops its server, and every picture would be the menu.
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.setScreen(null);
        }
        ticks++;
        switch (ticks) {
            case 40 -> {
                command(mc, "gamerule doDaylightCycle false");
                command(mc, "gamerule doWeatherCycle false");
                command(mc, "time set 6000");
                command(mc, "weather clear");
                command(mc, "tp @s 0 -59 -9 0 18");
            }
            case 50 -> {
                for (int i = 0; i < BLOCKS.size(); i++) {
                    int x = (i % 5) * 3 - 6, z = (i / 5) * 4;
                    command(mc, "setblock " + x + " -60 " + z + " " + GroundBurst.MODID + ":" + BLOCKS.get(i));
                }
                for (String item : List.of("geiger_counter", "dosimeter_badge", "napalm_bucket", "howitzer", "frag_grenade",
                        "laser_designator", "argon_bucket", "chlorine_bucket")) {
                    command(mc, "give @s " + GroundBurst.MODID + ":" + item);
                }
                command(mc, "setblock -2 -60 -4 " + GroundBurst.MODID + ":warning_beacon");
            }
            case 60 -> command(mc, "setblock -2 -61 -4 minecraft:redstone_block");   // lights the beacon
            case 130 -> tidy(mc);
            case 140 -> shot(mc, "blocks");
            case 145 -> command(mc, "tp @s 0 -59 -3 0 30");
            case 165 -> tidy(mc);
            case 170 -> shot(mc, "blocks_near");
            case 175 -> dev.groundburst.client.manual.ManualHotkey.open(null);
            case 200 -> shot(mc, "manual");
            case 205 -> dev.groundburst.client.manual.ManualHotkey.open("ordnance/cruise_launcher");
            case 215 -> {
                // The entry's structure is on its next spread.
                if (mc.screen != null) {
                    mc.screen.keyPressed(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT, 0, 0);
                }
            }
            case 235 -> shot(mc, "manual_structure");
            case 240 -> {
                mc.setScreen(null);
                command(mc, "tp @s 0 -59 -9 0 18");
            }
            case 250 -> command(mc, "groundburst blast groundburst:he_warhead 0 -59 30");
            case 255 -> tidy(mc);
            case 256 -> shot(mc, "blast_early");
            case 280 -> shot(mc, "blast_late");
            case 300 -> {
                var server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        dev.groundburst.blast.ParticleBeams.burnOverride = 80;
                        try {
                            dev.groundburst.blast.ParticleBeams.strike(server.overworld(), new net.minecraft.core.BlockPos(-14, -60, 18), null, false);
                        } finally {
                            dev.groundburst.blast.ParticleBeams.burnOverride = 0;
                        }
                    });
                }
            }
            case 335 -> tidy(mc);
            case 340 -> shot(mc, "beam");
            case 415 -> tidy(mc);
            case 420 -> shot(mc, "beam_after");
            case 425 -> {                                                         // over its trench, looking down into it
                command(mc, "setblock -14 -50 8 minecraft:glass");
                command(mc, "tp @s -14 -49 8 0 55");
            }
            case 445 -> tidy(mc);
            case 450 -> shot(mc, "beam_trench");
            // Sea mines and depth charges (main's DESIGN.md §14.6): a walled sea 13 deep, two moored mines (one awash, one 3
            // down) and a drifting one, from under the water and from above; then a depth charge 5 down, its column.
            case 470 -> command(mc, "fill 40 -60 -10 70 -48 20 minecraft:stone");
            case 472 -> command(mc, "fill 41 -60 -9 69 -48 19 minecraft:water");
            case 474 -> command(mc, "fill 55 -48 -3 55 -42 -3 minecraft:stone");      // a post to look down from
            case 476 -> {
                var server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        var level = server.overworld();
                        dev.groundburst.content.naval.SeaMine.lay(level, new net.minecraft.world.phys.Vec3(55.5, -48.6, 5.5), true, 0, null);
                        dev.groundburst.content.naval.SeaMine.lay(level, new net.minecraft.world.phys.Vec3(58.5, -48.6, 8.5), true, 3, null);
                        dev.groundburst.content.naval.SeaMine.lay(level, new net.minecraft.world.phys.Vec3(52.5, -48.6, 2.5), false, 0, null);
                    });
                }
            }
            case 480 -> command(mc, "tp @s 55.5 -54 -6 0 -4");
            case 640 -> tidy(mc);
            case 645 -> shot(mc, "naval_under");
            case 650 -> command(mc, "tp @s 55.5 -41 -3 0 40");
            case 670 -> tidy(mc);
            case 675 -> shot(mc, "naval_mines");
            case 680 -> command(mc, "tp @s 62.5 -47 -10 15 -6");                    // on the sea's wall, the post out of the way
            case 690 -> command(mc, "groundburst blast groundburst:depth_charge 55 -53 14");
            case 708 -> tidy(mc);
            case 710 -> shot(mc, "naval_column");
            case 735 -> shot(mc, "naval_column_late");
            // The Depth Charge Rack (main's DESIGN.md §14.6) on a quay two blocks out of the same sea, facing north: four
            // charges put on it, two more from a hopper over its Item Port; then all of them as a pattern, set to 8 blocks.
            case 740 -> command(mc, "fill 42 -60 12 44 -46 18 minecraft:stone_bricks");
            case 742 -> onServer(mc, level -> {
                BlockPos at = new BlockPos(43, -45, 12);
                level.setBlockAndUpdate(at, dev.groundburst.content.naval.GBNaval.RACK.block().get().defaultBlockState()
                        .setValue(dev.groundburst.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.NORTH));
                var def = dev.groundburst.content.naval.GBNaval.RACK_STRUCTURE;
                for (var cell : def.cells(at, net.minecraft.core.Direction.NORTH)) {
                    if (cell.key() != dev.groundburst.content.multiblock.MultiblockDefinition.CONTROLLER) {
                        level.setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
                    }
                }
                if (level.getBlockEntity(at) instanceof dev.groundburst.content.naval.DepthChargeRackBlockEntity rack) {
                    rack.revalidate();
                    for (int i = 0; i < 4; i++) {
                        rack.inventory().setItem(i, new net.minecraft.world.item.ItemStack(dev.groundburst.content.naval.GBNaval.DEPTH_CHARGE.get()));
                    }
                    for (int i = 0; i < 3; i++) {
                        rack.handleButton(2);                                        // its own depth, then 3, 5, 8
                    }
                }
                BlockPos hopper = new BlockPos(43, -44, 15);
                level.setBlockAndUpdate(hopper, net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.HopperBlock.FACING, net.minecraft.core.Direction.DOWN));
                if (level.getBlockEntity(hopper) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity h) {
                    h.setItem(0, new net.minecraft.world.item.ItemStack(dev.groundburst.content.naval.GBNaval.DEPTH_CHARGE.get(), 2));
                }
            });
            case 744 -> command(mc, "fill 40 -47 13 40 -45 13 minecraft:stone");      // a post on the sea's wall to look from
            case 746 -> command(mc, "tp @s 40.5 -44 13.5 -90 30");                   // on it, side on to the rack
            case 790 -> onServer(mc, level -> {
                var rack = level.getBlockEntity(new BlockPos(43, -45, 12)) instanceof dev.groundburst.content.naval.DepthChargeRackBlockEntity r ? r : null;
                GroundBurst.LOGGER.info("Port check: the rack formed {}, {} charges on it (4 put on, 2 from the hopper over its Item Port), depth {}: {}",
                        rack != null && rack.isFormed(), rack == null ? "-" : rack.loaded(), rack == null ? "-" : rack.depthSetting(),
                        rack != null && rack.isFormed() && rack.loaded() == 6 && rack.depthSetting() == 8 ? "OK" : "WRONG");
            });
            case 795 -> tidy(mc);
            case 800 -> shot(mc, "rack");
            case 805 -> onServer(mc, level -> {
                if (level.getBlockEntity(new BlockPos(43, -45, 12)) instanceof dev.groundburst.content.naval.DepthChargeRackBlockEntity rack) {
                    rack.releaseAll();
                }
            });
            case 809 -> shot(mc, "rack_trip");
            case 812 -> shot(mc, "rack_edge");
            case 815 -> shot(mc, "rack_off");
            case 822 -> shot(mc, "rack_down");
            case 845 -> shot(mc, "rack_second");
            case 900 -> onServer(mc, level -> {
                var rack = level.getBlockEntity(new BlockPos(43, -45, 12)) instanceof dev.groundburst.content.naval.DepthChargeRackBlockEntity r ? r : null;
                int charges = level.getEntitiesOfClass(dev.groundburst.content.naval.DepthCharge.class, new net.minecraft.world.phys.AABB(30, -64, -10, 60, -40, 30)).size();
                GroundBurst.LOGGER.info("Port check: the rack's pattern: {} left on it, {} charges in the sea: {}", rack == null ? "-" : rack.loaded(),
                        charges, rack != null && rack.loaded() <= 3 && rack.loaded() >= 1 ? "OK" : "WRONG");
            });
            case 940 -> shot(mc, "rack_column");
            // The bunker doors (main's DESIGN.md §22.3) in a concrete wall facing north: a Blast Door opened (hinges on the
            // left: the mirrored model), one shut (hinges on the right), a Pressure Door shut and one open, and before the
            // wall two floor hatches in the ground, one open.
            case 945 -> command(mc, "fill -51 -60 -30 -35 -57 -30 " + GroundBurst.MODID + ":reinforced_concrete");
            case 947 -> {
                onServer(mc, level -> {
                    var blast = dev.groundburst.content.construction.GBConstruction.BLAST_DOOR.block().get();
                    for (int i = 0; i < 2; i++) {
                        var hinge = i == 0 ? net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
                                : net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT;
                        BlockPos at = new BlockPos(i == 0 ? -48 : -44, -60, -30);
                        var state = blast.defaultBlockState().setValue(dev.groundburst.content.construction.BlastDoorBlock.FACING, net.minecraft.core.Direction.NORTH)
                                .setValue(dev.groundburst.content.construction.BlastDoorBlock.HINGE, hinge);
                        var across = dev.groundburst.content.construction.BlastDoorBlock.across(state);
                        for (int u = 0; u < 2; u++) {
                            for (int v = 0; v < 3; v++) {
                                level.setBlockAndUpdate(at.relative(across, u).above(v), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                            }
                        }
                        level.setBlockAndUpdate(at, state);
                        blast.setPlacedBy(level, at, state, null, net.minecraft.world.item.ItemStack.EMPTY);
                        if (i == 0 && level.getBlockEntity(at) instanceof dev.groundburst.content.construction.BlastDoorBlockEntity door) {
                            door.toggle(level, null);
                        }
                    }
                    var pressure = (net.minecraft.world.level.block.DoorBlock) dev.groundburst.content.construction.GBConstruction.PRESSURE_DOOR.block().get();
                    for (int i = 0; i < 2; i++) {
                        BlockPos at = new BlockPos(i == 0 ? -40 : -38, -60, -30);
                        var lower = pressure.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH)
                                .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, i == 1);
                        level.setBlockAndUpdate(at, lower);
                        level.setBlockAndUpdate(at.above(), lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
                    }
                    var hatch = dev.groundburst.content.construction.GBConstruction.BUNKER_HATCH.block().get().defaultBlockState()
                            .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, net.minecraft.core.Direction.NORTH)
                            .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);
                    for (int i = 0; i < 2; i++) {
                        BlockPos at = new BlockPos(i == 0 ? -46 : -42, -61, -34);
                        level.setBlockAndUpdate(at.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(at, hatch.setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, i == 0));
                    }
                });
            }
            case 950 -> command(mc, "tp @s -43.5 -58.4 -39.5 0 14");
            case 1030 -> tidy(mc);
            case 1035 -> shot(mc, "doors");
            case 1040 -> command(mc, "tp @s -51.5 -58.2 -35.5 -50 16");
            case 1070 -> tidy(mc);
            case 1075 -> shot(mc, "doors_side");
            case 1080 -> onServer(mc, level -> {
                var door = level.getBlockEntity(new BlockPos(-48, -60, -30)) instanceof dev.groundburst.content.construction.BlastDoorBlockEntity d ? d : null;
                int leaf = 0;
                for (int v = 0; v < 3; v++) {
                    for (int w = 1; w < 3; w++) {
                        leaf += level.getBlockState(new BlockPos(-48, -60 + v, -30 - w)).is(dev.groundburst.content.construction.GBConstruction.BLAST_DOOR.block().get()) ? 1 : 0;
                    }
                }
                boolean hatchOpen = level.getBlockState(new BlockPos(-46, -61, -34)).getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN);
                GroundBurst.LOGGER.info("Port check: the blast door at {} of {}, {} of its leaf's 6 blocks laid; the hatch open {}: {}",
                        door == null ? "-" : door.position(), dev.groundburst.content.construction.BlastDoorBlockEntity.TRAVEL, leaf, hatchOpen,
                        door != null && door.position() == dev.groundburst.content.construction.BlastDoorBlockEntity.TRAVEL && leaf == 6 && hatchOpen ? "OK" : "WRONG");
            });
            // The command room (main's DESIGN.md §22.4) on a concrete floor: consoles in a row, a desk, two filing cabinets
            // (one open), two tape computers running, the map table; the log saying whether the drives and the cabinet work.
            case 1090 -> command(mc, "fill -51 -61 -52 -38 -61 -42 " + GroundBurst.MODID + ":reinforced_concrete");
            case 1092 -> onServer(mc, level -> {
                var console = dev.groundburst.content.construction.GBConstruction.CONTROL_CONSOLE.block().get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH);
                for (int i = 0; i < 3; i++) {
                    level.setBlockAndUpdate(new BlockPos(-50 + i, -60, -44), console);
                }
                level.setBlockAndUpdate(new BlockPos(-46, -60, -44), dev.groundburst.content.construction.GBConstruction.COMMAND_DESK.block().get()
                        .defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH));
                for (int i = 0; i < 2; i++) {
                    level.setBlockAndUpdate(new BlockPos(-44 + i, -60, -44), dev.groundburst.content.construction.GBConstruction.FILING_CABINET.block().get()
                            .defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH)
                            .setValue(dev.groundburst.content.construction.FilingCabinetBlock.OPEN, i == 0));
                }
                var tape = dev.groundburst.content.construction.GBConstruction.TAPE_COMPUTER.block().get();
                for (int i = 0; i < 2; i++) {
                    BlockPos at = new BlockPos(-41 + i, -60, -44);
                    var ts = tape.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH);
                    level.setBlockAndUpdate(at, ts);
                    tape.setPlacedBy(level, at, ts, null, net.minecraft.world.item.ItemStack.EMPTY);
                }
                var table = dev.groundburst.content.construction.GBConstruction.SITUATION_MAP_TABLE.block().get();
                BlockPos base = new BlockPos(-47, -60, -47);
                var tsb = table.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH);
                level.setBlockAndUpdate(base, tsb);
                table.setPlacedBy(level, base, tsb, null, net.minecraft.world.item.ItemStack.EMPTY);
            });
            case 1095 -> command(mc, "tp @s -45.5 -57.6 -51.5 0 22");
            case 1160 -> tidy(mc);
            case 1165 -> shot(mc, "room");
            case 1170 -> command(mc, "tp @s -41.0 -58.2 -47.2 0 12");
            case 1200 -> tidy(mc);
            case 1205 -> shot(mc, "room_tape");
            case 1210 -> onServer(mc, level -> {
                boolean tape = level.getBlockEntity(new BlockPos(-41, -60, -44)) instanceof dev.groundburst.content.construction.TapeComputerBlockEntity
                        && level.getBlockState(new BlockPos(-41, -59, -44)).is(dev.groundburst.content.construction.GBConstruction.TAPE_COMPUTER.block().get());
                boolean cabinet = level.getBlockEntity(new BlockPos(-43, -60, -44)) instanceof dev.groundburst.content.construction.FilingCabinetBlockEntity c
                        && c.getContainerSize() == 27;
                boolean table = level.getBlockState(new BlockPos(-46, -60, -48)).is(dev.groundburst.content.construction.GBConstruction.SITUATION_MAP_TABLE.block().get());
                GroundBurst.LOGGER.info("Port check: the tape drive two high {}, the cabinet's 27 slots {}, the map table laid {}: {}", tape, cabinet, table,
                        tape && cabinet && table ? "OK" : "WRONG");
            });
            // The bunker fittings (main's DESIGN.md §22.5) against a concrete wall: wall pipes through an open valve, a fuse box
            // on, a lamp lit by a lever, a duct over a grille, a locker held open by a lever, a bunk bed, an extinguisher; the log
            // saying whether they stand.
            case 1220 -> command(mc, "fill -51 -61 -60 -38 -61 -54 " + GroundBurst.MODID + ":reinforced_concrete");
            case 1221 -> command(mc, "fill -51 -60 -54 -38 -57 -54 " + GroundBurst.MODID + ":reinforced_concrete");
            case 1223 -> onServer(mc, level -> {
                var facing = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
                var north = net.minecraft.core.Direction.NORTH;
                var pipes = dev.groundburst.content.construction.GBConstruction.WALL_PIPES.block().get();
                var valve = dev.groundburst.content.construction.GBConstruction.PIPE_VALVE.block().get();
                for (int i = 0; i < 4; i++) {
                    BlockPos at = new BlockPos(-50 + i, -59, -55);
                    var s = (i == 2 ? valve.defaultBlockState().setValue(dev.groundburst.content.construction.PipeValveBlock.OPEN, true)
                            : pipes.defaultBlockState()).setValue(facing, north);
                    level.setBlockAndUpdate(at, s);
                }
                for (int i = 0; i < 4; i++) {
                    BlockPos at = new BlockPos(-50 + i, -59, -55);
                    var s = level.getBlockState(at);
                    level.setBlockAndUpdate(at, ((dev.groundburst.content.construction.WallPipesBlock) s.getBlock()).joined(s, level, at));
                }
                level.setBlockAndUpdate(new BlockPos(-45, -59, -55), dev.groundburst.content.construction.GBConstruction.FUSE_BOX.block().get()
                        .defaultBlockState().setValue(facing, north).setValue(dev.groundburst.content.construction.FuseBoxBlock.POWERED, true));
                level.setBlockAndUpdate(new BlockPos(-47, -57, -55), dev.groundburst.content.construction.GBConstruction.BUNKER_LAMP.block().get()
                        .defaultBlockState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, north));
                var duct = dev.groundburst.content.construction.GBConstruction.VENTILATION_DUCT.block().get();
                for (int i = 0; i < 3; i++) {
                    level.setBlockAndUpdate(new BlockPos(-44 + i, -57, -55), duct.defaultBlockState());
                }
                level.setBlockAndUpdate(new BlockPos(-43, -58, -55), dev.groundburst.content.construction.GBConstruction.VENT_GRILLE.block().get()
                        .defaultBlockState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, net.minecraft.core.Direction.DOWN));
                for (int i = 0; i < 3; i++) {
                    BlockPos at = new BlockPos(-44 + i, -57, -55);
                    level.setBlockAndUpdate(at, duct.joined(level.getBlockState(at), level, at));
                }
                var locker = dev.groundburst.content.construction.GBConstruction.STEEL_LOCKER.block().get();
                BlockPos la = new BlockPos(-41, -60, -55);
                var ls = locker.defaultBlockState().setValue(facing, north);
                level.setBlockAndUpdate(la, ls);
                locker.setPlacedBy(level, la, ls, null, net.minecraft.world.item.ItemStack.EMPTY);
                // A lever over the locker holds it open and one beside the lamp lights it (main's DESIGN.md §22.5: the props
                // follow redstone; set open or lit, the next block update would put them back).
                var lever = net.minecraft.world.level.block.Blocks.LEVER.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                        .setValue(net.minecraft.world.level.block.LeverBlock.FACING, north)
                        .setValue(net.minecraft.world.level.block.LeverBlock.POWERED, true);
                level.setBlockAndUpdate(new BlockPos(-41, -58, -55), lever);
                level.setBlockAndUpdate(new BlockPos(-48, -57, -55), lever);
                var bunk = dev.groundburst.content.construction.GBConstruction.BUNK_BED.block().get();
                BlockPos foot = new BlockPos(-39, -60, -57);
                var bs = bunk.defaultBlockState().setValue(facing, net.minecraft.core.Direction.SOUTH);
                level.setBlockAndUpdate(foot, bs);
                bunk.setPlacedBy(level, foot, bs, null, net.minecraft.world.item.ItemStack.EMPTY);
                level.setBlockAndUpdate(new BlockPos(-46, -60, -57), dev.groundburst.content.construction.GBConstruction.FIRE_EXTINGUISHER.block().get()
                        .defaultBlockState().setValue(facing, north));
            });
            case 1226 -> command(mc, "tp @s -44.5 -58.4 -61.5 0 12");
            case 1290 -> tidy(mc);
            case 1295 -> shot(mc, "fittings");
            case 1300 -> command(mc, "tp @s -43.0 -58.2 -61.0 -31 10");
            case 1330 -> tidy(mc);
            case 1335 -> shot(mc, "fittings_locker_bunk");
            case 1340 -> onServer(mc, level -> {
                boolean pipes = level.getBlockState(new BlockPos(-50, -59, -55)).getValue(dev.groundburst.content.construction.WallPipesBlock.LEFT);
                boolean bunk = level.getBlockState(new BlockPos(-39, -59, -56)).is(dev.groundburst.content.construction.GBConstruction.BUNK_BED.block().get());
                boolean locker = level.getBlockEntity(new BlockPos(-41, -60, -55)) instanceof dev.groundburst.content.construction.SteelLockerBlockEntity l
                        && l.getContainerSize() == 27 && level.getBlockState(new BlockPos(-41, -59, -55))
                                .getValue(dev.groundburst.content.construction.SteelLockerBlock.OPEN);
                boolean lit = level.getBlockState(new BlockPos(-47, -57, -55)).getLightEmission() == 14;
                GroundBurst.LOGGER.info("Port check: the pipes joined {}, the bunk four blocks {}, the locker's 27 slots and open door {}, the lamp lit {}: {}",
                        pipes, bunk, locker, lit, pipes && bunk && locker && lit ? "OK" : "WRONG");
            });
            // The yard (main's DESIGN.md §22.6) on the grass: barriers, teeth (each face's plates turned about three axes, which
            // this branch's model loader bakes), hedgehogs (likewise), the searchlight on, crates (one open), the drums.
            case 1350 -> command(mc, "fill -51 -61 -72 -38 -61 -63 minecraft:grass_block");
            case 1352 -> onServer(mc, level -> {
                var facing = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
                var north = net.minecraft.core.Direction.NORTH;
                for (int i = 0; i < 4; i++) {
                    level.setBlockAndUpdate(new BlockPos(-50 + i, -60, -64), dev.groundburst.content.construction.GBConstruction.JERSEY_BARRIER.block().get()
                            .defaultBlockState().setValue(facing, north));
                    level.setBlockAndUpdate(new BlockPos(-50 + i * 2, -60, -67), dev.groundburst.content.construction.GBConstruction.DRAGONS_TEETH.block().get()
                            .defaultBlockState());
                }
                for (int i = 0; i < 2; i++) {
                    level.setBlockAndUpdate(new BlockPos(-44 + i * 3, -60, -66), dev.groundburst.content.construction.GBConstruction.CZECH_HEDGEHOG.block().get()
                            .defaultBlockState().setValue(facing, i == 0 ? north : net.minecraft.core.Direction.EAST));
                }
                var light = dev.groundburst.content.construction.GBConstruction.SEARCHLIGHT.block().get();
                BlockPos at = new BlockPos(-39, -60, -69);
                var ls = light.defaultBlockState().setValue(facing, north).setValue(dev.groundburst.content.construction.SearchlightBlock.ACTIVE, true);
                level.setBlockAndUpdate(at, ls);
                light.setPlacedBy(level, at, ls, null, net.minecraft.world.item.ItemStack.EMPTY);
                for (int i = 0; i < 2; i++) {
                    level.setBlockAndUpdate(new BlockPos(-50 + i, -60, -70), dev.groundburst.content.construction.GBConstruction.AMMO_CRATES.block().get()
                            .defaultBlockState().setValue(facing, north).setValue(dev.groundburst.content.construction.AmmoCratesBlock.OPEN, i == 1));
                }
                var drums = java.util.List.of(dev.groundburst.content.construction.GBConstruction.OIL_DRUM, dev.groundburst.content.construction.GBConstruction.FUEL_DRUM,
                        dev.groundburst.content.construction.GBConstruction.RUSTY_DRUM);
                for (int i = 0; i < drums.size(); i++) {
                    level.setBlockAndUpdate(new BlockPos(-47 + i, -60, -70), drums.get(i).block().get().defaultBlockState().setValue(facing, north)
                            .setValue(dev.groundburst.content.construction.DrumBlock.DRUMS, 4 - i));
                }
            });
            case 1356 -> command(mc, "tp @s -44.5 -57.4 -60.5 180 20");
            case 1420 -> tidy(mc);
            case 1425 -> shot(mc, "yard");
            case 1430 -> command(mc, "tp @s -43.0 -58.6 -63.2 160 24");
            case 1460 -> tidy(mc);
            case 1465 -> shot(mc, "yard_close");
            case 1470 -> onServer(mc, level -> {
                boolean light = level.getBlockState(new BlockPos(-39, -59, -69)).is(dev.groundburst.content.construction.GBConstruction.SEARCHLIGHT.block().get())
                        && level.getBlockState(new BlockPos(-39, -60, -69)).getLightEmission() == 15;
                boolean crates = level.getBlockEntity(new BlockPos(-50, -60, -70)) instanceof dev.groundburst.content.construction.AmmoCratesBlockEntity c
                        && c.getContainerSize() == 27;
                boolean drums = level.getBlockState(new BlockPos(-47, -60, -70)).getValue(dev.groundburst.content.construction.DrumBlock.DRUMS) == 4;
                GroundBurst.LOGGER.info("Port check: the searchlight two high and lit {}, the crates' 27 slots {}, four drums {}: {}", light, crates, drums,
                        light && crates && drums ? "OK" : "WRONG");
            });
            // The war room (main's DESIGN.md §17.17): a station, a War Room Display showing its network, a Seismograph and the
            // Dead Hand armed (on any strike), all powered; a strike of magnitude 4.5 put into the ground's record 60 blocks off;
            // the board, the readout, the console before and in alert, the board in alert; the Dead Hand hurried to zero, its
            // lid and its rocket; the log saying whether each did what it should.
            case 1480 -> command(mc, "fill -96 -61 -42 -58 -61 -12 " + GroundBurst.MODID + ":reinforced_concrete");
            case 1482 -> onServer(mc, PortCheck::warRoom);
            case 1486 -> onServer(mc, level -> {
                if (level.getBlockEntity(DEAD_HAND) instanceof dev.groundburst.content.command.DeadHandBlockEntity dh) {
                    dh.order(level, dev.groundburst.content.command.DeadHandBlockEntity.ARM, 0);
                    dh.order(level, dev.groundburst.content.command.DeadHandBlockEntity.ARM, 0);
                }
            });
            case 1488 -> command(mc, "tp @s -76.5 -60 -22.5 180 -6");
            case 1560 -> tidy(mc);
            case 1565 -> shot(mc, "war_room_board");
            case 1570 -> command(mc, "tp @s -72.6 -60 -21.5 -90 26");
            case 1610 -> tidy(mc);
            case 1615 -> shot(mc, "war_room_seismograph");
            case 1620 -> command(mc, "tp @s -65.5 -60 -26.6 180 4");
            case 1660 -> tidy(mc);
            case 1665 -> shot(mc, "war_room_dead_hand");
            case 1670 -> onServer(mc, level -> dev.groundburst.content.command.Seismic.of(level).addForTest(level,
                    new net.minecraft.world.phys.Vec3(-26, -60, -70), 4.5f, 0, null));
            case 1700 -> tidy(mc);
            case 1705 -> shot(mc, "war_room_dead_hand_alert");
            case 1710 -> command(mc, "tp @s -76.5 -60 -22.5 180 -6");
            case 1760 -> tidy(mc);
            case 1765 -> shot(mc, "war_room_attack");
            case 1770 -> onServer(mc, level -> {
                if (level.getBlockEntity(DEAD_HAND) instanceof dev.groundburst.content.command.DeadHandBlockEntity dh) {
                    dh.hurryForTest(level);
                }
            });
            case 1772 -> command(mc, "tp @s -60.5 -56 -24.5 135 22");
            case 1798 -> tidy(mc);
            case 1800 -> shot(mc, "war_room_lid");
            case 1822 -> shot(mc, "war_room_rocket");
            case 1830 -> onServer(mc, level -> {
                var d = level.getBlockEntity(DISPLAY) instanceof dev.groundburst.content.command.WarRoomDisplayBlockEntity w ? w : null;
                var s = level.getBlockEntity(SEISMOGRAPH) instanceof dev.groundburst.content.command.SeismographBlockEntity g ? g : null;
                var dh = level.getBlockEntity(DEAD_HAND) instanceof dev.groundburst.content.command.DeadHandBlockEntity h ? h : null;
                boolean board = d != null && d.isFormed() && d.shownPowered() && d.condition() == 1;
                boolean felt = s != null && s.records().stream().anyMatch(r -> Math.abs(r.magnitude() - 4.5f) < 0.35f);
                boolean fired = dh != null && (dh.state() == dev.groundburst.content.command.DeadHandBlockEntity.State.FIRING
                        || dh.state() == dev.groundburst.content.command.DeadHandBlockEntity.State.SPENT) && !dh.rocket();
                GroundBurst.LOGGER.info("Port check: the war room's board lit at ATTACK {}, the strike recorded {}, the Dead Hand fired {}: {}",
                        board, felt, fired, board && felt && fired ? "OK" : "WRONG");
            });
            case 1840 -> {
                GroundBurst.LOGGER.info("Port check: done");
                mc.stop();
            }
            default -> {
            }
        }
    }

    /** The war room's display, seismograph and Dead Hand (their controllers). */
    private static final BlockPos DISPLAY = new BlockPos(-77, -60, -30), STATION = new BlockPos(-86, -60, -24), SEISMOGRAPH = new BlockPos(-70, -60, -22),
            DEAD_HAND = new BlockPos(-66, -60, -30);

    /** On the server: the war room built, powered and linked; the display shown the station's network. */
    private static void warRoom(net.minecraft.server.level.ServerLevel level) {
        var command = dev.groundburst.content.command.GBCommand.class;
        place(level, dev.groundburst.content.command.GBCommand.WAR_ROOM_CONTROLLER.block().get(), dev.groundburst.content.command.GBCommand.WAR_ROOM,
                DISPLAY, net.minecraft.core.Direction.SOUTH);
        place(level, dev.groundburst.content.launch.GBLaunch.STATION_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.STATION,
                STATION, net.minecraft.core.Direction.SOUTH);
        place(level, dev.groundburst.content.command.GBCommand.DEAD_HAND_CONTROLLER.block().get(), dev.groundburst.content.command.GBCommand.DEAD_HAND,
                DEAD_HAND, net.minecraft.core.Direction.SOUTH);
        level.setBlockAndUpdate(SEISMOGRAPH, dev.groundburst.content.command.GBCommand.SEISMOGRAPH.block().get().defaultBlockState()
                .setValue(dev.groundburst.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.WEST));
        bank(level, SEISMOGRAPH.east(), net.minecraft.core.Direction.WEST);
        for (BlockPos at : List.of(DISPLAY, STATION, DEAD_HAND, SEISMOGRAPH)) {
            if (level.getBlockEntity(at) instanceof dev.groundburst.content.machine.MachineBlockEntity m) {
                m.energy().generate(m.energy().capacity());
            }
        }
        var card = new net.minecraft.world.item.ItemStack(dev.groundburst.content.launch.GBLaunch.LAUNCH_CODES.get());
        dev.groundburst.content.launch.LaunchCodesItem.use(level, card, level.getBlockEntity(STATION), false);
        for (BlockPos at : List.of(DISPLAY, SEISMOGRAPH, DEAD_HAND)) {
            dev.groundburst.content.launch.LaunchCodesItem.use(level, card, level.getBlockEntity(at), false);
        }
        if (level.getBlockEntity(DEAD_HAND) instanceof dev.groundburst.content.command.DeadHandBlockEntity dh) {
            dh.load(level);
            dh.order(level, dev.groundburst.content.command.DeadHandBlockEntity.SET_RADIUS, 0);
            dh.order(level, dev.groundburst.content.command.DeadHandBlockEntity.SET_THRESHOLD, 1);
            dh.order(level, dev.groundburst.content.command.DeadHandBlockEntity.SET_COMMAND, 1);
        }
        GroundBurst.LOGGER.info("Port check: the war room built ({})", command.getSimpleName());
    }

    /** A multiblock placed whole, formed, and a Creative Capacitor Bank by its Energy Port. */
    private static void place(net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.block.Block controller,
            dev.groundburst.content.multiblock.MultiblockDefinition def, BlockPos at, net.minecraft.core.Direction facing) {
        java.util.Set<BlockPos> cells = new java.util.HashSet<>();
        for (var cell : dev.groundburst.content.multiblock.StructureSpawnerItem.buildOrder(def, at, facing)) {
            cells.add(cell.pos().immutable());
            net.minecraft.world.level.block.state.BlockState s;
            if (cell.key() == '.') {
                s = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            } else if (cell.key() == dev.groundburst.content.multiblock.MultiblockDefinition.CONTROLLER) {
                s = controller.defaultBlockState().setValue(dev.groundburst.content.machine.MachineBlock.FACING, facing);
            } else {
                s = def.legend().get(cell.key()).preview().get();
            }
            level.setBlockAndUpdate(cell.pos(), s);
        }
        if (level.getBlockEntity(at) instanceof dev.groundburst.content.multiblock.MultiblockController c) {
            c.revalidate();
        }
        for (BlockPos port : def.positionsOf('E', at, facing)) {
            for (var d : List.of(facing.getOpposite(), facing.getClockWise(), facing.getCounterClockWise(), facing)) {
                BlockPos p2 = port.relative(d);
                if (!cells.contains(p2)) {
                    bank(level, p2, d.getOpposite());
                    break;
                }
            }
        }
    }

    private static void bank(net.minecraft.server.level.ServerLevel level, BlockPos at, net.minecraft.core.Direction toward) {
        level.setBlockAndUpdate(at, dev.groundburst.content.machine.GBMachines.CAPACITOR_BANKS.get(3).block().get().defaultBlockState()
                .setValue(dev.groundburst.content.machine.MachineBlock.FACING, toward));
    }

    /** Where the pictures go. */
    public static File folder() {
        return new File(Minecraft.getInstance().gameDirectory, "screenshots");
    }
}
