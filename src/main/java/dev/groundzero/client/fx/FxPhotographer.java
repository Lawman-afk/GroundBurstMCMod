package dev.groundzero.client.fx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import dev.groundzero.GroundZero;
import dev.groundzero.blast.Blasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid: with {@code -Dgroundzero.fxShots=true} (the {@code fxShots} Gradle run) the client makes a flat
 * creative world, and for each test blast profile stands a spectator some way off on fresh ground, sets the blast off
 * and screenshots it at a series of moments into {@code run/screenshots/fx_<profile>_<tick>.png}, then quits. Used to
 * review the Spectacle effects without playing.
 */
public final class FxPhotographer {
    /** A blast to photograph: shot name, profile, how far away to stand, camera pitch, and the moments (ticks after) to shoot. */
    private record Scene(String name, String profile, int distance, float pitch, List<Integer> moments, String time) {
        Scene(String name, String profile, int distance, float pitch, List<Integer> moments) {
            this(name, profile, distance, pitch, moments, "noon");
        }
    }

    private static final Deque<Scene> SCENES = new ArrayDeque<>();
    private static Scene scene;
    private static int step = -1;
    private static int clock;
    private static int site;
    private static boolean worldMade;

    private FxPhotographer() {}

    public static void register() {
        if (!Boolean.getBoolean("groundzero.fxShots")) {
            return;
        }
        String only = System.getProperty("groundzero.fxShots.only", "");
        List<Integer> moments = List.of(1, 3, 6, 10, 16, 25, 40, 70, 120, 200);
        for (Scene s : List.of(new Scene("small", "test/small", 14, 8, moments), new Scene("medium", "test/medium", 42, 0, moments),
                new Scene("large", "test/large", 95, -8, moments),
                new Scene("nuclear", "test/nuclear", 230, -16, List.of(1, 3, 6, 10, 20, 40, 80, 160, 300, 600, 1200, 2400)),
                new Scene("nuclear_close", "test/nuclear", 130, -32, List.of(30, 120, 300, 700)),
                new Scene("nuclear_night", "test/nuclear", 130, -32, List.of(10, 60, 300, 700), "midnight"),
                new Scene("meltdown", "reactor_meltdown", 55, 2, List.of(2, 8, 20, 60, 160, 400)),
                new Scene("warhead_he", "he_warhead", 26, 6, List.of(2, 6, 14, 40)),
                new Scene("warhead_heavy", "heavy_warhead", 45, 2, List.of(3, 10, 25, 80)),
                new Scene("warhead_thermobaric", "thermobaric_warhead", 55, 2, List.of(3, 10, 25, 80)),
                new Scene("warhead_massive", "massive_warhead", 110, -6, List.of(4, 20, 80, 300, 700)),
                new Scene("meltdown_dusk", "reactor_meltdown", 40, 14, List.of(30, 200), "13000"),
                new Scene("suits", "suits", 3, 12, List.of(15)),
                new Scene("suits_back", "suits_back", 3, 12, List.of(15)),
                new Scene("grenades", "grenades", 7, 18, List.of(4, 12, 30, 62, 74, 90, 130)),
                new Scene("chemical", "chemical", 7, 18, List.of(20, 60, 120)),
                new Scene("charges", "charges", 4, 22, List.of(10, 15, 60)),
                new Scene("charge_ui", "charges", 4, 22, List.of(30, 45, 60)),
                new Scene("mines", "mines", 5, 25, List.of(3, 6, 9, 30)),
                new Scene("warheads", "warheads", 9, 25, List.of(10, 40)),
                new Scene("napalm", "napalm", 10, 30, List.of(8, 40)),
                new Scene("launch", "launch", 26, -14, List.of(20, 120, 240, 275, 300)),
                new Scene("launch_ui", "launch", 9, 0, List.of(22, 300, 340)),
                new Scene("launch_dive", "launch_dive", 50, -20, List.of(285, 295, 302, 308)),
                new Scene("radar", "radar", 22, -16, List.of(20, 60)),
                new Scene("radar_ui", "radar", 6, 0, List.of(60, 130)))) {
            if (only.isEmpty() || s.name().startsWith(only)) {
                SCENES.add(s);
            }
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
    }

    /** Two armour stands wearing the hazmat suits, side by side, facing the camera (or turned away). */
    private static void suits(ServerLevel level, int x, int ground, boolean back) {
        var sets = List.of(dev.groundzero.content.gear.GZArmor.HAZMAT_SET, dev.groundzero.content.gear.GZArmor.LEAD_HAZMAT_SET);
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET};
        for (int i = 0; i < 2; i++) {
            var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, x + 0.5, ground, 0.5 + (i == 0 ? -0.8 : 0.8));
            stand.setYRot(back ? -90 : 90);
            stand.setYBodyRot(back ? -90 : 90);
            stand.setShowArms(true);
            for (int k = 0; k < 4; k++) {
                stand.setItemSlot(slots[k], new net.minecraft.world.item.ItemStack(sets.get(i).get(k).get()));
            }
            level.addFreshEntity(stand);
        }
    }

    /** Thrown explosives in front of the camera: one of each, in flight, landing, smoking and going off. */
    private static void grenades(ServerLevel level, int x, int ground) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.FRAG_GRENADE.get()));
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.DYNAMITE.get(), new Vec3(x - 3, ground + 0.05, -1.2), Vec3.ZERO, 400);
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.SMOKE_GRENADE.get(), new Vec3(x + 2, ground + 0.05, 2.5), Vec3.ZERO, 20);
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.FRAG_GRENADE.get(), new Vec3(x - 4, ground + 2.5, 3),
                new Vec3(0.35, 0.25, -0.45), 70);
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.INCENDIARY_GRENADE.get(), new Vec3(x + 3, ground + 6, -2.5),
                new Vec3(0, -0.05, 0), -1);
    }

    /** The chemical grenades: a poison gas cloud, a toxin splash, dynamite, and a Molotov falling; a toxin vial in hand. */
    private static void chemical(ServerLevel level, int x, int ground) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.GAS_GRENADE.get(), new Vec3(x + 2, ground + 0.05, 2.5), Vec3.ZERO, 5);
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.TOXIN_GRENADE.get(), new Vec3(x - 1, ground + 4, -2.5),
                new Vec3(0, -0.2, 0), -1);
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.DYNAMITE.get(), new Vec3(x - 4, ground + 0.05, 0.5), Vec3.ZERO, 4000);
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.MOLOTOV_COCKTAIL.get(), new Vec3(x - 5, ground + 5, 4.5),
                new Vec3(0, -0.05, 0), -1);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.TOXIN_GRENADE.get()));
    }

    /** Where the charges scene put its charges, for the charge_ui scene to open their screens: breaching, C4, timed bomb. */
    private static final net.minecraft.core.BlockPos[] UI_CHARGES = new net.minecraft.core.BlockPos[3];

    /**
     * Placed charges facing the camera: stacked C4 and a timed bomb on the ground, breaching and thermite on a wall; all
     * armed, the thermite lit and the bomb counting, except the breaching charge (left safe, for its screen).
     */
    private static void charges(ServerLevel level, int x, int ground) {
        for (int z = -3; z <= 3; z++) {
            for (int y = 0; y < 4; y++) {
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + 3, ground + y, z), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        var F = dev.groundzero.content.ordnance.charge.ChargeBlock.FACING;
        var c4 = new net.minecraft.core.BlockPos(x, ground, -1);
        level.setBlockAndUpdate(c4, dev.groundzero.content.ordnance.GZOrdnance.C4.block().get().defaultBlockState()
                .setValue(F, net.minecraft.core.Direction.UP).setValue(dev.groundzero.content.ordnance.charge.ChargeBlock.COUNT, 3));
        var bomb = new net.minecraft.core.BlockPos(x, ground, 1);
        level.setBlockAndUpdate(bomb, dev.groundzero.content.ordnance.GZOrdnance.TIMED_BOMB.block().get().defaultBlockState()
                .setValue(F, net.minecraft.core.Direction.WEST));
        var breach = new net.minecraft.core.BlockPos(x + 2, ground + 2, -2);
        level.setBlockAndUpdate(breach, dev.groundzero.content.ordnance.GZOrdnance.BREACHING_CHARGE.block().get().defaultBlockState()
                .setValue(F, net.minecraft.core.Direction.WEST));
        var thermite = new net.minecraft.core.BlockPos(x + 2, ground + 1, 2);
        level.setBlockAndUpdate(thermite, dev.groundzero.content.ordnance.GZOrdnance.THERMITE_CHARGE.block().get().defaultBlockState()
                .setValue(F, net.minecraft.core.Direction.WEST));
        UI_CHARGES[0] = breach;
        UI_CHARGES[1] = c4;
        UI_CHARGES[2] = bomb;
        for (var p : java.util.List.of(c4, bomb, thermite)) {
            var be = dev.groundzero.content.ordnance.charge.ChargeBlock.at(level, p);
            if (be != null) {
                if (p == bomb) {
                    be.configure(dev.groundzero.content.ordnance.charge.ChargeBlockEntity.Mode.TIMER, 0, 45);
                }
                be.arm(level, null);
                if (p == thermite || p == bomb) {
                    be.trigger(level, null);
                }
            }
        }
    }

    /** Mines: a landmine, a claymore with its laser across the view, and a bouncing mine set off as the shots start. */
    private static void mines(ServerLevel level, int x, int ground) {
        var F = dev.groundzero.content.ordnance.charge.ChargeBlock.FACING;
        var land = new net.minecraft.core.BlockPos(x - 1, ground, 1);
        var clay = new net.minecraft.core.BlockPos(x + 1, ground, -3);
        var bounce = new net.minecraft.core.BlockPos(x + 3, ground, 2);
        level.setBlockAndUpdate(land, dev.groundzero.content.ordnance.GZOrdnance.LANDMINE.block().get().defaultBlockState());
        level.setBlockAndUpdate(clay, dev.groundzero.content.ordnance.GZOrdnance.CLAYMORE.block().get().defaultBlockState()
                .setValue(F, net.minecraft.core.Direction.SOUTH));
        level.setBlockAndUpdate(bounce, dev.groundzero.content.ordnance.GZOrdnance.BOUNCING_MINE.block().get().defaultBlockState());
        for (var p : java.util.List.of(land, clay, bounce)) {
            var be = dev.groundzero.content.ordnance.charge.ChargeBlock.at(level, p);
            if (be != null) {
                be.arm(level, null);
                if (p == bounce) {
                    be.trigger(level, null);
                }
            }
        }
    }

    /** The warheads in a row across the view, some armed; or a napalm warhead set off. */
    private static void bombs(ServerLevel level, int x, int ground, boolean napalm) {
        var F = dev.groundzero.content.ordnance.charge.ChargeBlock.FACING;
        if (napalm) {
            var at = new net.minecraft.core.BlockPos(x, ground, -6);
            level.setBlockAndUpdate(at, dev.groundzero.content.ordnance.GZOrdnance.NAPALM_WARHEAD.block().get().defaultBlockState()
                    .setValue(F, net.minecraft.core.Direction.SOUTH));
            var be = dev.groundzero.content.ordnance.charge.ChargeBlock.at(level, at);
            if (be != null) {
                be.arm(level, null);
                be.trigger(level, null);
            }
            return;
        }
        var bombs = java.util.List.of(dev.groundzero.content.ordnance.GZOrdnance.HE_WARHEAD, dev.groundzero.content.ordnance.GZOrdnance.HEAVY_WARHEAD,
                dev.groundzero.content.ordnance.GZOrdnance.THERMOBARIC_WARHEAD, dev.groundzero.content.ordnance.GZOrdnance.CLUSTER_WARHEAD,
                dev.groundzero.content.ordnance.GZOrdnance.BUNKER_BUSTER, dev.groundzero.content.ordnance.GZOrdnance.NAPALM_WARHEAD,
                dev.groundzero.content.ordnance.GZOrdnance.MASSIVE_WARHEAD);
        for (int i = 0; i < bombs.size(); i++) {
            var at = new net.minecraft.core.BlockPos(x + (i % 2) * 3, ground, -6 + i * 2);
            level.setBlockAndUpdate(at, bombs.get(i).block().get().defaultBlockState().setValue(F, net.minecraft.core.Direction.SOUTH)
                    .setValue(dev.groundzero.content.ordnance.charge.ChargeBlock.ARMED, i % 2 == 0));
        }
    }

    /** The launch scenes' silo and computer, for the tick to launch from and open the screen of. */
    private static net.minecraft.core.BlockPos launchSilo, launchComputer, launchTarget;

    /**
     * A formed, loaded, powered Launch Silo and Launch Computer side by side, fronts to the camera; the silo aimed and
     * armed. With {@code extras}, five more silos behind them in different states, so the computer's list scrolls.
     */
    private static void launchSite(ServerLevel level, int x, int ground, boolean dive, boolean extras) {
        // The long shot aims 400 blocks off (the scope shows it in flight); the dive lands 35 blocks off, in view.
        launchTarget = dive ? new net.minecraft.core.BlockPos(x + 25, ground, 25) : new net.minecraft.core.BlockPos(x + 400, 64, 140);
        launchSilo = new net.minecraft.core.BlockPos(x, ground, -3);
        launchComputer = new net.minecraft.core.BlockPos(x - 4, ground, 5);
        formed(level, launchSilo, dev.groundzero.content.launch.GZLaunch.SILO_CONTROLLER.block().get(), dev.groundzero.content.launch.GZLaunch.SILO);
        formed(level, launchComputer, dev.groundzero.content.launch.GZLaunch.COMPUTER_CONTROLLER.block().get(),
                dev.groundzero.content.launch.GZLaunch.COMPUTER);
        if (level.getBlockEntity(launchSilo) instanceof dev.groundzero.content.launch.LaunchSiloBlockEntity silo) {
            silo.revalidate();
            silo.inventory().setItem(dev.groundzero.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE,
                    new net.minecraft.world.item.ItemStack(dev.groundzero.content.launch.GZLaunch.BASIC_MISSILE.get()));
            silo.inventory().setItem(dev.groundzero.content.launch.LaunchSiloBlockEntity.SLOT_WARHEAD,
                    new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.HE_WARHEAD.item().get()));
            silo.energy().generate(dev.groundzero.content.launch.LaunchSiloBlockEntity.ENERGY_CAPACITY);
            silo.setTarget(launchTarget.getX(), launchTarget.getZ());
            silo.arm(true);
        }
        if (level.getBlockEntity(launchComputer) instanceof dev.groundzero.content.launch.LaunchComputerBlockEntity computer) {
            computer.revalidate();
            computer.energy().generate(dev.groundzero.content.launch.LaunchComputerBlockEntity.ENERGY_CAPACITY);
        }
        for (int i = 0; extras && i < 5; i++) {
            var at = new net.minecraft.core.BlockPos(x + 12 + 7 * i, ground, -30);
            formed(level, at, dev.groundzero.content.launch.GZLaunch.SILO_CONTROLLER.block().get(), dev.groundzero.content.launch.GZLaunch.SILO);
            if (level.getBlockEntity(at) instanceof dev.groundzero.content.launch.LaunchSiloBlockEntity silo) {
                silo.revalidate();
                if (i != 1) {                                  // one with no missile
                    silo.inventory().setItem(dev.groundzero.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE,
                            new net.minecraft.world.item.ItemStack(dev.groundzero.content.launch.GZLaunch.BASIC_MISSILE.get()));
                    silo.inventory().setItem(dev.groundzero.content.launch.LaunchSiloBlockEntity.SLOT_WARHEAD,
                            new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.HEAVY_WARHEAD.item().get()));
                }
                if (i != 2) {                                  // one still charging
                    silo.energy().generate(dev.groundzero.content.launch.LaunchSiloBlockEntity.ENERGY_CAPACITY);
                }
                if (i >= 3) {                                  // two aimed elsewhere
                    silo.setTarget(x - 150 + 60 * i, -200 + 40 * i);
                }
            }
        }
    }

    private static net.minecraft.core.BlockPos radarAt;

    /**
     * A formed, powered Radar Array facing the camera, and missiles in the air round it (no warheads): two coming down
     * near it (incoming), two passing elsewhere.
     */
    private static void radarSite(ServerLevel level, int x, int ground) {
        radarAt = new net.minecraft.core.BlockPos(x, ground, 0);
        formed(level, radarAt, dev.groundzero.content.launch.GZLaunch.RADAR_CONTROLLER.block().get(), dev.groundzero.content.launch.GZLaunch.RADAR);
        if (level.getBlockEntity(radarAt) instanceof dev.groundzero.content.launch.RadarArrayBlockEntity radar) {
            radar.revalidate();
            radar.energy().generate(dev.groundzero.content.launch.RadarArrayBlockEntity.ENERGY_CAPACITY);
        }
        // A Large Radar Dish on its own beside it, as placed.
        var dish = new net.minecraft.core.BlockPos(x - 3, ground, -7);
        var dishBlock = dev.groundzero.content.launch.GZLaunch.LARGE_RADAR_DISH.block().get();
        level.setBlockAndUpdate(dish, dishBlock.defaultBlockState());
        dishBlock.setPlacedBy(level, dish, level.getBlockState(dish), null, net.minecraft.world.item.ItemStack.EMPTY);
        Vec3 c = Vec3.atBottomCenterOf(radarAt);
        var empty = net.minecraft.world.item.ItemStack.EMPTY;
        dev.groundzero.content.launch.Flights.launch(level, c.add(-1500, 0, 600), c.add(120, 0, -60), empty, null);
        dev.groundzero.content.launch.Flights.launch(level, c.add(900, 0, 1400), c.add(-200, 0, 150), empty, null);
        dev.groundzero.content.launch.Flights.launch(level, c.add(-1200, 0, -1500), c.add(1500, 0, -900), empty, null);
        dev.groundzero.content.launch.Flights.launch(level, c.add(1600, 0, 300), c.add(1300, 0, 1500), empty, null);
    }

    private static void formed(ServerLevel level, net.minecraft.core.BlockPos at, net.minecraft.world.level.block.Block controller,
            dev.groundzero.content.multiblock.MultiblockDefinition def) {
        level.setBlockAndUpdate(at, controller.defaultBlockState().setValue(dev.groundzero.content.machine.MachineBlock.FACING,
                net.minecraft.core.Direction.WEST));
        for (var cell : def.cells(at, net.minecraft.core.Direction.WEST)) {
            if (cell.key() != dev.groundzero.content.multiblock.MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                level.setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
            }
        }
    }

    private static void throwOne(ServerLevel level, ServerPlayer player, net.minecraft.world.item.Item item, Vec3 at, Vec3 velocity, int fuse) {
        var g = new dev.groundzero.content.ordnance.ThrownExplosive(level, player, new net.minecraft.world.item.ItemStack(item));
        g.setPos(at);
        g.setDeltaMovement(velocity);
        if (fuse >= 0) {
            g.setFuseForTest(fuse);
        }
        level.addFreshEntity(g);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null && !worldMade && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            try {
                var settings = new net.minecraft.world.level.LevelSettings("Fx shots", GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = net.minecraft.util.FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "fx_shots", "");
                mc.createWorldOpenFlows().createFreshLevel(folder, settings,
                        net.minecraft.client.gui.screens.worldselection.SelectWorldScreen.TEST_OPTIONS,
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createTestWorldDimensions, mc.gui.screen());
            } catch (java.io.IOException e) {
                GroundZero.LOGGER.error("Fx shots: could not make a world", e);
            }
            return;
        }
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        clock++;
        if (scene == null) {
            scene = SCENES.poll();
            if (scene == null) {
                GroundZero.LOGGER.info("Fx shots done");
                mc.stop();
                return;
            }
            step = -1;
            clock = 0;
            site++;
            mc.gui.setScreen(null);                        // a screen a scene opened stays shut for the next
            mc.options.renderDistance().set(24);
            Scene s = scene;
            int x = site * 1200;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set " + s.time());
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "gamerule doDaylightCycle false");
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SPECTATOR);
                level.getChunk(x >> 4, 0);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, 0);
                player.teleportTo(level, x - s.distance() + 0.5, ground + 1.6, 0.5, java.util.Set.of(), -90, s.pitch(), true);
            });
            return;
        }
        if (step == -1) {
            if (clock < 100) {
                return;                                    // let the chunks round the new site load
            }
            Scene s = scene;
            int x = site * 1200;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, 0);
                Vec3 at = new Vec3(x + 0.5, ground + 0.5, 0.5);
                if (s.profile().equals("grenades")) {
                    grenades(level, x, ground);
                    return;
                }
                if (s.profile().equals("chemical")) {
                    chemical(level, x, ground);
                    return;
                }
                if (s.profile().equals("charges")) {
                    charges(level, x, ground);
                    return;
                }
                if (s.profile().equals("mines")) {
                    mines(level, x, ground);
                    return;
                }
                if (s.profile().equals("radar")) {
                    radarSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("warheads")) {
                    bombs(level, x, ground, false);
                    return;
                }
                if (s.profile().startsWith("launch")) {
                    launchSite(level, x, ground, s.profile().equals("launch_dive"), s.name().equals("launch_ui"));
                    return;
                }
                if (s.profile().equals("napalm")) {
                    bombs(level, x, ground, true);
                    return;
                }
                if (s.profile().startsWith("suits")) {
                    suits(level, x, ground, s.profile().endsWith("back"));    // armour stands wearing the suits, no blast
                    return;
                }
                var blast = Blasts.detonate(level, at, Blasts.profile(s.profile()), null);
                if (s.profile().equals("reactor_meltdown")) {
                    dev.groundzero.content.nuclear.Meltdown.afterBlast(level, at, blast);   // the corium a real meltdown leaves
                }
            });
            step = 0;
            clock = 0;
            return;
        }
        if (scene.name().equals("charge_ui") && step >= 0 && step < 3 && clock == scene.moments().get(step) - 5 && UI_CHARGES[step] != null) {
            dev.groundzero.content.ordnance.charge.ChargeBlock.screenHook.accept(UI_CHARGES[step]);   // safe, armed, counting
        }
        if (scene.profile().startsWith("launch") && step >= 0 && clock == 25 && launchSilo != null) {
            var silo = launchSilo;
            var computerAt = launchComputer;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(computerAt) instanceof dev.groundzero.content.launch.LaunchComputerBlockEntity be) {
                    be.launchArmed(server.overworld(), null);              // LAUNCH: every armed silo
                }
            });
        }
        if (scene.name().equals("radar_ui") && step >= 0 && clock == 10 && radarAt != null) {
            dev.groundzero.content.launch.RadarArrayBlock.screenHook.accept(radarAt);
        }
        if (scene.name().equals("launch_ui") && step >= 0 && clock == 10 && launchComputer != null) {
            dev.groundzero.content.launch.LaunchComputerBlock.screenHook.accept(launchComputer);
        }
        if (step < scene.moments().size() && clock == scene.moments().get(step)) {
            String name = "fx_" + scene.name() + "_" + clock + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> GroundZero.LOGGER.info("Fx shot: {}", msg.getString()));
            step++;
        }
        if (step >= scene.moments().size() && clock > scene.moments().getLast() + 10) {
            scene = null;
        }
    }
}
