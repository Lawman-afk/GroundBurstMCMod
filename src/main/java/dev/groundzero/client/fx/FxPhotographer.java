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
                new Scene("nuke_tactical", "tactical_nuclear_warhead", 200, -10, List.of(3, 20, 80, 300, 700)),
                new Scene("nuke_fission", "fission_warhead", 330, -14, List.of(3, 30, 120, 400, 900)),
                new Scene("nuke_boosted", "boosted_fission_warhead", 380, -16, List.of(3, 30, 120, 400, 900)),
                // From above late on: the stem's foot and the dust ring stay on the crater while the cap drifts.
                new Scene("nuke_tactical_aerial", "tactical_nuclear_warhead", 170, 24, List.of(300, 1200, 2400)),
                // A contaminated zone 180 blocks round: its fallout clouds from outside, then the green sky from inside.
                new Scene("fallout", "fallout", 260, -14, List.of(60, 400)),
                // Slice 8c: the Strategic Assembly Hall at work (the crane laying the missile section by section), its
                // screen, the Launch Complex with an intercontinental missile, the H-bomb, and the cobalt warhead's plume.
                new Scene("hall_aerial", "hall", 26, 24, List.of(60, 300, 700, 1000, 1260)),
                new Scene("hall_ui", "hall", 4, 0, List.of(30)),
                new Scene("hall_jei", "hall", 4, 0, List.of(25)),
                new Scene("complex_icbm", "complex_icbm", 34, -12, List.of(20)),
                new Scene("complex_icbm_close", "complex_icbm", 13, -38, List.of(20)),
                new Scene("nuke_thermonuclear", "thermonuclear_warhead", 600, -18, List.of(3, 40, 160, 500, 1200)),
                new Scene("cobalt_aerial", "cobalt_plume", 420, 28, List.of(400, 3300)),
                // Smoke in front of water, seen from above: the water must still show through the smoke.
                new Scene("water_smoke_aerial", "water_smoke", 16, 28, List.of(60, 160)),
                new Scene("fallout_inside", "fallout", 30, -32, List.of(60, 400)),
                new Scene("nuke_neutron", "neutron_warhead", 70, -8, List.of(3, 20, 60, 200, 400)),
                new Scene("warhead_dirty", "dirty_warhead", 30, 2, List.of(2, 8, 20, 60)),
                new Scene("complex", "complex", 34, -12, List.of(20, 150, 270, 335, 380)),
                new Scene("complex_close", "complex", 13, -38, List.of(20, 150, 360)),
                new Scene("launch_close", "launch", 11, -32, List.of(20, 150, 240)),
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
                new Scene("silo_ui", "launch", 5, 0, List.of(30)),
                new Scene("launch_dive", "launch_dive", 50, -20, List.of(285, 295, 302, 308)),
                new Scene("radar", "radar", 22, -16, List.of(20, 60)),
                new Scene("airburst", "airburst", 40, -18, List.of(4, 12, 30, 50)),
                new Scene("effects", "effects", 16, 22, List.of(8, 30, 70, 140)),
                new Scene("frozen", "frozen", 6, 18, List.of(10, 60)),
                new Scene("gas_wall", "gas_wall", 9, 20, List.of(60, 140)),
                new Scene("radar_ui", "radar", 6, 0, List.of(60, 130)),
                new Scene("assembly", "assembly", 5, 18, List.of(10, 30, 50)),
                new Scene("assembly_ui", "assembly", 4, 0, List.of(30, 40)),
                new Scene("assembly_jei", "assembly", 4, 0, List.of(25)),
                new Scene("tall_ui", "tall", 4, 0, List.of(30)),
                new Scene("assembly_missile_ui", "assembly", 4, 0, List.of(30)),
                new Scene("assembly_mow_ui", "assembly", 4, 0, List.of(30, 44)),
                new Scene("assembly_nuke_ui", "assembly", 4, 0, List.of(30, 44)),
                new Scene("assembly_neutron_ui", "assembly", 4, 0, List.of(30, 44)),
                new Scene("assembly_dirty_ui", "assembly", 4, 0, List.of(30, 44)),
                new Scene("launch_map_ui", "launch", 9, 0, List.of(40, 80)))) {
            if (only.isEmpty() || java.util.Arrays.stream(only.split(",")).anyMatch(o -> s.name().startsWith(o.trim()))) {   // -PfxOnly=a,b
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
    /** A pool two deep with a red stripe on its floor, and a smoke grenade going off on the near bank. */
    private static void waterSmoke(ServerLevel level, int x, int ground) {
        for (int dx = -4; dx <= 12; dx++) {
            for (int z = -8; z <= 8; z++) {
                level.setBlock(new net.minecraft.core.BlockPos(x + dx, ground - 3, z),
                        (z == 0 ? net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK : net.minecraft.world.level.block.Blocks.SAND).defaultBlockState(), 2);
                for (int y = ground - 2; y < ground; y++) {
                    level.setBlock(new net.minecraft.core.BlockPos(x + dx, y, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
                }
            }
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.SMOKE_GRENADE.get(), new Vec3(x - 6, ground + 0.05, 0.5), Vec3.ZERO, 5);
    }

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
        var bombs = dev.groundzero.content.ordnance.GZOrdnance.WARHEADS;
        for (int i = 0; i < bombs.size(); i++) {
            var at = new net.minecraft.core.BlockPos(x + (i % 2) * 3, ground, -9 + i * 2);
            level.setBlockAndUpdate(at, bombs.get(i).block().get().defaultBlockState().setValue(F, net.minecraft.core.Direction.SOUTH)
                    .setValue(dev.groundzero.content.ordnance.charge.ChargeBlock.ARMED, i % 2 == 0));
        }
    }

    /** The launch scenes' silo and computer, for the tick to launch from and open the screen of. */
    private static net.minecraft.core.BlockPos launchSilo, launchComputer, launchTarget;

    /**
     * A formed, powered Launch Complex with a Strategic tactical-nuke missile, aimed 400 blocks off and armed, and a Launch
     * Computer beside it (the "complex" scene launches at clock 25: 15 s of countdown, then ignition).
     */
    /** Where the hall scenes put the Strategic Assembly Hall's controller. */
    private static net.minecraft.core.BlockPos hallAt;

    /**
     * The Strategic Assembly Hall, its length across the camera's view (controller facing north, the hall running
     * south from it), powered and given a thermonuclear missile's parts with GPS, and set to work.
     */
    private static void hallSite(ServerLevel level, int x, int ground) {
        hallAt = new net.minecraft.core.BlockPos(x, ground, -6);
        formed(level, hallAt, dev.groundzero.content.strategic.GZStrategic.HALL_CONTROLLER.block().get(), dev.groundzero.content.strategic.GZStrategic.HALL,
                net.minecraft.core.Direction.NORTH);
        if (level.getBlockEntity(hallAt) instanceof dev.groundzero.content.strategic.StrategicAssemblyBlockEntity hall) {
            hall.revalidate();
            var inv = hall.inventory();
            inv.setItem(dev.groundzero.content.strategic.StrategicAssemblyBlockEntity.SLOT_PRIMARY,
                    new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.BOOSTED_FISSION_CORE.get()));
            inv.setItem(dev.groundzero.content.strategic.StrategicAssemblyBlockEntity.SLOT_SECONDARY,
                    new net.minecraft.world.item.ItemStack(dev.groundzero.content.strategic.GZStrategic.FUSION_SECONDARY.get()));
            inv.setItem(dev.groundzero.content.strategic.StrategicAssemblyBlockEntity.SLOT_BOOSTER,
                    new net.minecraft.world.item.ItemStack(dev.groundzero.content.strategic.GZStrategic.INTERCONTINENTAL_BOOSTER.get()));
            inv.setItem(dev.groundzero.content.strategic.StrategicAssemblyBlockEntity.SLOT_GUIDANCE,
                    new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.GPS_GUIDANCE.get()));
            hall.energy().generate(dev.groundzero.content.strategic.StrategicAssemblyBlockEntity.CAPACITY);
            hall.startAssembly();
        }
    }

    private static void complexSite(ServerLevel level, int x, int ground) {
        complexSite(level, x, ground, false);
    }

    /** The Launch Complex and its computer; with {@code icbm} a thermonuclear intercontinental missile on the pad, not armed. */
    private static void complexSite(ServerLevel level, int x, int ground, boolean icbm) {
        launchTarget = new net.minecraft.core.BlockPos(x + 400, 64, 140);
        launchSilo = new net.minecraft.core.BlockPos(x, ground, -4);
        launchComputer = new net.minecraft.core.BlockPos(x - 6, ground, 4);
        formed(level, launchSilo, dev.groundzero.content.launch.GZLaunch.COMPLEX_CONTROLLER.block().get(), dev.groundzero.content.launch.GZLaunch.COMPLEX);
        formed(level, launchComputer, dev.groundzero.content.launch.GZLaunch.COMPUTER_CONTROLLER.block().get(), dev.groundzero.content.launch.GZLaunch.COMPUTER);
        if (level.getBlockEntity(launchSilo) instanceof dev.groundzero.content.launch.LaunchComplexBlockEntity complex) {
            complex.revalidate();
            complex.inventory().setItem(dev.groundzero.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE, icbm
                    ? dev.groundzero.content.strategic.StrategicAssembly.assemble(net.minecraft.world.item.ItemStack.EMPTY,
                            new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.GPS_GUIDANCE.get()), net.minecraft.world.item.ItemStack.EMPTY)
                    : dev.groundzero.content.assembly.MissileAssembly.missile(new dev.groundzero.content.assembly.MissileSpec(new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.TACTICAL_NUCLEAR_WARHEAD.item().get()), dev.groundzero.content.assembly.SizeClass.XL, dev.groundzero.content.assembly.MissileParts.Motor.LIQUID, dev.groundzero.content.assembly.MissileParts.Guidance.GPS, false)));
            complex.energy().generate(dev.groundzero.content.launch.LaunchComplexBlockEntity.COMPLEX_ENERGY);
            complex.setTarget(launchTarget.getX(), launchTarget.getZ());
            complex.arm(true);
        }
        if (level.getBlockEntity(launchComputer) instanceof dev.groundzero.content.launch.LaunchComputerBlockEntity computer) {
            computer.revalidate();
            computer.energy().generate(dev.groundzero.content.launch.LaunchComputerBlockEntity.ENERGY_CAPACITY);
        }
    }

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
                    dev.groundzero.content.assembly.MissileAssembly.missile(new dev.groundzero.content.assembly.MissileSpec(new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.HE_WARHEAD.item().get()), dev.groundzero.content.assembly.SizeClass.L, dev.groundzero.content.assembly.MissileParts.Motor.SRM_II, dev.groundzero.content.assembly.MissileParts.Guidance.GPS, false)));
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
                            dev.groundzero.content.assembly.MissileAssembly.missile(new dev.groundzero.content.assembly.MissileSpec(new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.HEAVY_WARHEAD.item().get()), dev.groundzero.content.assembly.SizeClass.M, dev.groundzero.content.assembly.MissileParts.Motor.SRM_II, dev.groundzero.content.assembly.MissileParts.Guidance.GPS, false)));
                }
                if (i != 2) {                                  // one still charging
                    silo.energy().generate(dev.groundzero.content.launch.LaunchSiloBlockEntity.ENERGY_CAPACITY);
                }
                if (i >= 3) {                                  // two aimed elsewhere
                    silo.setTarget(x - 150 + 60 * i, -200 + 40 * i);
                }
                if (i == 3) {                                  // one held stopped by redstone (wants a signal, has none)
                    silo.cycleRedstoneMode();
                }
                if (i == 4) {                                  // one knocked out by an EMP
                    dev.groundzero.content.effect.EmpFields.add(level, net.minecraft.world.phys.Vec3.atCenterOf(at), 3, 6000, null);
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

    private static net.minecraft.core.BlockPos benchAt, pressAt;
    /** Set by the JEI plugin when JEI is running: opens its Assembly Bench recipes (the "assembly_jei" scene). */
    public static Runnable showAssemblyRecipes = () -> {};
    /** Set by the JEI plugin when JEI is running: opens the Strategic Assembly Hall's recipes (the "hall_jei" scene). */
    public static Runnable showHallRecipes = () -> {};

    /**
     * Phase 7: two Ordnance Assembly Benches facing the camera. The near one is assembling a Poison Gas Warhead (parts
     * loaded, powered, hoist working); the far one has a finished warhead waiting in its output slot.
     */
    private static void assemblySite(ServerLevel level, int x, int ground) {
        var block = dev.groundzero.content.assembly.GZAssembly.ASSEMBLY_BENCH.block().get();
        benchAt = new net.minecraft.core.BlockPos(x, ground, -1);
        var far = new net.minecraft.core.BlockPos(x + 2, ground, -4);
        for (var at : java.util.List.of(benchAt, far)) {
            var state = block.defaultBlockState().setValue(dev.groundzero.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.WEST);
            level.setBlockAndUpdate(at, state);
            block.setPlacedBy(level, at, state, null, net.minecraft.world.item.ItemStack.EMPTY);
        }
        if (level.getBlockEntity(benchAt) instanceof dev.groundzero.content.assembly.AssemblyBenchBlockEntity b) {
            b.energy().generate(dev.groundzero.content.assembly.AssemblyBenchBlockEntity.CAPACITY);
            b.inventory().setItem(0, new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.GAS_DISPENSER.get(), 3));
            b.inventory().setItem(1, new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.MEDIUM_CASING.get(), 3));
            b.inventory().setItem(2, new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.BAROMETRIC_FUZE.get(), 3));
            b.startAssembly();
            // The missile page's parts too (its screenshot shows them).
            b.inventory().setItem(4, new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.HEAVY_WARHEAD.item().get()));
            b.inventory().setItem(5, new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.MEDIUM_BODY.get()));
            b.inventory().setItem(6, new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.SRM_II.get()));
            b.inventory().setItem(7, new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.GPS_GUIDANCE.get()));
            b.inventory().setItem(8, new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.HARDENED_ELECTRONICS.get()));
        }
        if (level.getBlockEntity(far) instanceof dev.groundzero.content.assembly.AssemblyBenchBlockEntity b) {
            b.inventory().setItem(3, new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.HE_WARHEAD.item().get()));
        }
    }

    private static void formed(ServerLevel level, net.minecraft.core.BlockPos at, net.minecraft.world.level.block.Block controller,
            dev.groundzero.content.multiblock.MultiblockDefinition def) {
        formed(level, at, controller, def, net.minecraft.core.Direction.WEST);
    }

    private static void formed(ServerLevel level, net.minecraft.core.BlockPos at, net.minecraft.world.level.block.Block controller,
            dev.groundzero.content.multiblock.MultiblockDefinition def, net.minecraft.core.Direction facing) {
        level.setBlockAndUpdate(at, controller.defaultBlockState().setValue(dev.groundzero.content.machine.MachineBlock.FACING, facing));
        for (var cell : def.cells(at, facing)) {
            if (cell.key() != dev.groundzero.content.multiblock.MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                level.setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
            }
        }
    }

    /**
     * Slice 6e: a pond with a pig by it and a cryo grenade landing in it (left), an EMP grenade by a powered launch
     * computer (right), and a chemical warhead going off behind them.
     */
    private static void effects(ServerLevel level, int x, int ground) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        for (int dx = -2; dx <= 1; dx++) {
            for (int dz = -6; dz <= -3; dz++) {
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, ground - 1, dz), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
            }
        }
        var pig = net.minecraft.world.entity.EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (pig != null) {
            pig.setPos(x + 2.5, ground, -2.5);
            pig.setNoAi(true);
            level.addFreshEntity(pig);
        }
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.CRYO_GRENADE.get(), new Vec3(x, ground + 0.2, -3.5), Vec3.ZERO, 4);
        var computer = new net.minecraft.core.BlockPos(x + 1, ground, 5);
        formed(level, computer, dev.groundzero.content.launch.GZLaunch.COMPUTER_CONTROLLER.block().get(), dev.groundzero.content.launch.GZLaunch.COMPUTER);
        if (level.getBlockEntity(computer) instanceof dev.groundzero.content.launch.LaunchComputerBlockEntity c) {
            c.revalidate();
            c.energy().generate(dev.groundzero.content.launch.LaunchComputerBlockEntity.ENERGY_CAPACITY);
        }
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.EMP_GRENADE.get(), new Vec3(x - 1, ground + 0.2, 3.5), Vec3.ZERO, 26);
        var chem = new net.minecraft.core.BlockPos(x + 10, ground, 0);
        level.setBlockAndUpdate(chem, dev.groundzero.content.ordnance.GZOrdnance.CHEMICAL_WARHEAD.block().get().defaultBlockState());
        var be = dev.groundzero.content.ordnance.charge.ChargeBlock.at(level, chem);
        if (be != null) {
            be.arm(level, null);
            be.trigger(level, null);
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
                double lift = s.name().endsWith("_aerial") ? s.distance() * 0.5 : 1.6;    // an aerial scene looks down from above
                player.teleportTo(level, x - s.distance() + 0.5, ground + lift, 0.5, java.util.Set.of(), -90, s.pitch(), true);
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
                level.getChunk(x >> 4, 0);                 // a far scene's middle may have unloaded: its height would read the world's floor
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
                if (s.profile().equals("frozen")) {
                    // Animals by the camera, frozen solid in a burst of cold.
                    for (var type : List.of(net.minecraft.world.entity.EntityTypes.PIG, net.minecraft.world.entity.EntityTypes.COW,
                            net.minecraft.world.entity.EntityTypes.SHEEP)) {
                        var mob = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        if (mob != null) {
                            int i = level.getRandom().nextInt(3);
                            mob.setPos(x + 0.5 + (type == net.minecraft.world.entity.EntityTypes.COW ? 0 : 1.5), ground,
                                    type == net.minecraft.world.entity.EntityTypes.PIG ? -2 : type == net.minecraft.world.entity.EntityTypes.SHEEP ? 2 : 0.5 + i * 0);
                            mob.setYRot(90);
                            level.addFreshEntity(mob);
                        }
                    }
                    dev.groundzero.content.effect.Frost.freeze(level, new Vec3(x + 1, ground, 0.5), 5, 400, null);
                    return;
                }
                if (s.profile().equals("gas_wall")) {
                    // A stone wall across the view, a doorway in it; gas let out behind it.
                    for (int dz = -8; dz <= 8; dz++) {
                        for (int dy = 0; dy < 4; dy++) {
                            boolean door = dz == 3 && dy < 2;
                            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + 2, ground + dy, dz),
                                    door ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                                            : net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
                        }
                    }
                    ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
                    throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.GAS_GRENADE.get(), new Vec3(x + 5, ground + 0.2, -1.5), Vec3.ZERO, 2);
                    return;
                }
                if (s.profile().equals("effects")) {
                    effects(level, x, ground);
                    return;
                }
                if (s.profile().equals("airburst")) {
                    // A missile with a cluster warhead already high over a target 30 blocks in front of the camera.
                    Vec3 target = new Vec3(x + 0.5, ground, 0.5);
                    var f = dev.groundzero.content.launch.Flights.launch(level, target.add(-120, 0, 0), target,
                            new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.CLUSTER_WARHEAD.item().get()), null);
                    f.skipForTest(f.ticksToImpact() - 22);
                    return;
                }
                if (s.profile().equals("radar")) {
                    radarSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("assembly")) {
                    assemblySite(level, x, ground);
                    return;
                }
                if (s.profile().equals("tall")) {
                    var press = dev.groundzero.content.machine.GZMachines.INDUSTRIAL_PRESS.block().get();
                    pressAt = new net.minecraft.core.BlockPos(x, ground, 0);
                    var state = press.defaultBlockState().setValue(dev.groundzero.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.WEST);
                    level.setBlockAndUpdate(pressAt, state);
                    press.setPlacedBy(level, pressAt, state, null, net.minecraft.world.item.ItemStack.EMPTY);
                    return;
                }
                if (s.profile().equals("warheads")) {
                    bombs(level, x, ground, false);
                    return;
                }
                if (s.profile().equals("complex")) {
                    complexSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("complex_icbm")) {
                    complexSite(level, x, ground, true);
                    return;
                }
                if (s.profile().equals("hall")) {
                    hallSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("cobalt_plume")) {
                    dev.groundzero.content.ordnance.charge.Charges.impact(level, dev.groundzero.content.ordnance.charge.ChargeKind.COBALT_WARHEAD,
                            net.minecraft.core.BlockPos.containing(at), null);
                    return;
                }
                if (s.profile().startsWith("launch")) {
                    launchSite(level, x, ground, s.profile().equals("launch_dive"), s.name().equals("launch_ui") || s.name().equals("launch_map_ui"));
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
                if (s.profile().equals("water_smoke")) {
                    waterSmoke(level, x, ground);
                    return;
                }
                if (s.profile().equals("fallout")) {
                    dev.groundzero.content.nuclear.Contamination.of(level).add(new dev.groundzero.content.nuclear.Contamination.Zone(
                            new Vec3(x + 0.5, ground, 0.5), 180, 1f, level.getGameTime(), 48_000));
                    return;
                }
                // These two through the warhead's own path: the neutrons wither the plants, the waste goes up as dust.
                if (s.profile().equals("neutron_warhead")) {
                    dev.groundzero.content.ordnance.charge.Charges.impact(level, dev.groundzero.content.ordnance.charge.ChargeKind.NEUTRON_WARHEAD,
                            net.minecraft.core.BlockPos.containing(at), null);
                    return;
                }
                if (s.profile().equals("dirty_warhead")) {
                    dev.groundzero.content.ordnance.charge.Charges.burst(level, dev.groundzero.content.ordnance.charge.ChargeKind.DIRTY_WARHEAD, at, null);
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
        if ((scene.profile().startsWith("launch") || scene.profile().equals("complex")) && step >= 0 && clock == 25 && launchSilo != null) {
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
        if ((scene.name().equals("launch_ui") || scene.name().equals("launch_map_ui")) && step >= 0 && clock == 10 && launchComputer != null) {
            dev.groundzero.content.launch.LaunchComputerBlock.screenHook.accept(launchComputer);
        }
        // The map's mouse, as the game sends it: a right-button drag 40 px left and up, then a left click right of centre.
        if (scene.name().equals("launch_ui") && step >= 0 && (clock == 14 || clock == 18) && mc.gui.screen() != null) {
            var screen = mc.gui.screen();
            double cx = screen.width / 2.0 - 168 + 8 + 92, cy = screen.height / 2.0 - 107 + 14 + 92;
            var right = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT, 0);
            var left = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0);
            if (clock == 14) {
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(cx, cy, right), false);
                screen.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(cx - 40, cy - 20, right), -40, -20);
                screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(cx - 40, cy - 20, right));
            } else {
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(cx + 30, cy, left), false);
                screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(cx + 30, cy, left));
            }
        }
        // The computer's map zoomed all the way out (32 blocks a pixel: the whole 3000-block survey).
        if (scene.name().equals("launch_map_ui") && step >= 0 && clock == 14 && mc.gui.screen() != null) {
            var screen = mc.gui.screen();
            double cx = screen.width / 2.0 - 168 + 8 + 92, cy = screen.height / 2.0 - 107 + 14 + 92;
            for (int i = 0; i < 6; i++) {
                screen.mouseScrolled(cx, cy, 0, -1);
            }
        }
        // The biggest warhead on the bench (the preview must zoom out to fit it), then as a Heavy missile.
        // The neutron and dirty warheads have the longest stats lists (fallout rows, the neutron rows, a note).
        if ((scene.name().equals("assembly_nuke_ui") || scene.name().equals("assembly_neutron_ui") || scene.name().equals("assembly_dirty_ui"))
                && step >= 0 && clock == 8 && benchAt != null) {
            var bench = benchAt;
            boolean neutron = scene.name().equals("assembly_neutron_ui"), dirty = scene.name().equals("assembly_dirty_ui");
            server.execute(() -> {
                if (server.overworld().getBlockEntity(bench) instanceof dev.groundzero.content.assembly.AssemblyBenchBlockEntity be) {
                    be.inventory().setItem(0, new net.minecraft.world.item.ItemStack(dirty ? dev.groundzero.content.assembly.GZAssembly.DIRTY_PAYLOAD.get()
                            : neutron ? dev.groundzero.content.assembly.GZAssembly.NEUTRON_CORE.get() : dev.groundzero.content.assembly.GZAssembly.FISSION_CORE.get()));
                    be.inventory().setItem(1, new net.minecraft.world.item.ItemStack(dirty ? dev.groundzero.content.assembly.GZAssembly.LIGHT_CASING.get()
                            : dev.groundzero.content.assembly.GZAssembly.STRATEGIC_CASING.get()));
                    be.inventory().setItem(4, new net.minecraft.world.item.ItemStack(dirty ? dev.groundzero.content.ordnance.GZOrdnance.DIRTY_WARHEAD.item().get()
                            : neutron ? dev.groundzero.content.ordnance.GZOrdnance.NEUTRON_WARHEAD.item().get()
                            : dev.groundzero.content.ordnance.GZOrdnance.FISSION_WARHEAD.item().get()));
                    be.inventory().setItem(5, new net.minecraft.world.item.ItemStack(dirty ? dev.groundzero.content.assembly.GZAssembly.TACTICAL_BODY.get()
                            : dev.groundzero.content.assembly.GZAssembly.STRATEGIC_BODY.get()));
                    be.inventory().setItem(6, new net.minecraft.world.item.ItemStack(dirty ? dev.groundzero.content.assembly.GZAssembly.SRM_I.get()
                            : dev.groundzero.content.assembly.GZAssembly.LIQUID_FUEL_STAGE.get()));
                }
            });
        }
        if (scene.name().equals("assembly_mow_ui") && step >= 0 && clock == 8 && benchAt != null) {
            var bench = benchAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(bench) instanceof dev.groundzero.content.assembly.AssemblyBenchBlockEntity be) {
                    be.inventory().setItem(0, new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.MASSIVE_ORDNANCE_CHARGE.get()));
                    be.inventory().setItem(1, new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.HEAVY_CASING.get()));
                    be.inventory().setItem(4, new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.MASSIVE_WARHEAD.item().get()));
                    be.inventory().setItem(5, new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.HEAVY_BODY.get()));
                }
            });
        }
        if ((scene.name().equals("assembly_mow_ui") || scene.name().equals("assembly_nuke_ui") || scene.name().equals("assembly_neutron_ui")
                || scene.name().equals("assembly_dirty_ui")) && step >= 0 && clock == 36 && mc.gui.screen() != null) {
            var screen = mc.gui.screen();                                  // the Missiles tab
            double tx = screen.width / 2.0 - 144 - 12, ty = screen.height / 2.0 - 105 + 48;
            var left = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0);
            screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left), false);
            screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left));
        }
        // The bench's screen while it assembles.
        if (scene.name().equals("assembly_missile_ui") && step >= 0 && clock == 20 && mc.gui.screen() != null) {
            var screen = mc.gui.screen();                                  // the Missiles tab, on the window's left edge
            double tx = screen.width / 2.0 - 144 - 12, ty = screen.height / 2.0 - 105 + 48;
            var left = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0);
            screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left), false);
            screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left));
        }
        if ((scene.name().equals("assembly_ui") || scene.name().equals("assembly_missile_ui") || scene.name().equals("assembly_mow_ui")
                || scene.name().equals("assembly_nuke_ui") || scene.name().equals("assembly_neutron_ui") || scene.name().equals("assembly_dirty_ui"))
                && step >= 0 && clock == 12 && benchAt != null) {
            var bench = benchAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(bench) instanceof dev.groundzero.content.assembly.AssemblyBenchBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, bench);
                }
            });
        }
        if (scene.name().equals("hall_ui") && step >= 0 && clock == 12 && hallAt != null) {
            var hall = hallAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(hall) instanceof dev.groundzero.content.strategic.StrategicAssemblyBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, hall);
                }
            });
        }
        // A tall machine's screen with its side tab open (each half's faces).
        if (scene.name().equals("tall_ui") && step >= 0 && clock == 12 && pressAt != null) {
            var press = pressAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(press) instanceof dev.groundzero.content.machine.MachineBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);
                    player.openMenu(be, press);
                }
            });
        }
        if (scene.name().equals("tall_ui") && step >= 0 && clock == 20 && mc.gui.screen() != null) {
            var screen = mc.gui.screen();
            double tx = screen.width / 2.0 + 88 + 8, ty = screen.height / 2.0 - 83 + 14;
            var left = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0);
            screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left), false);
            screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left));
        }
        if (scene.name().equals("assembly_jei") && step >= 0 && clock == 12) {
            showAssemblyRecipes.run();
        }
        if (scene.name().equals("hall_jei") && step >= 0 && clock == 12) {
            showHallRecipes.run();
        }
        // Then its side-configuration tab opened (the two-block layout), for the second shot.
        if (scene.name().equals("assembly_ui") && step == 1 && clock == 36 && mc.gui.screen() != null) {
            var screen = mc.gui.screen();
            double tx = screen.width / 2.0 + 144 + 8, ty = screen.height / 2.0 - 105 + 14;
            var left = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0);
            screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left), false);
            screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left));
        }
        // The silo's own screen, a Massive Ordnance Warhead loaded (the longest name).
        if (scene.name().equals("silo_ui") && step >= 0 && clock == 12 && launchSilo != null) {
            var silo = launchSilo;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(silo) instanceof dev.groundzero.content.launch.LaunchSiloBlockEntity be) {
                    be.inventory().setItem(dev.groundzero.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE,
                            dev.groundzero.content.assembly.MissileAssembly.missile(new dev.groundzero.content.assembly.MissileSpec(new net.minecraft.world.item.ItemStack(dev.groundzero.content.ordnance.GZOrdnance.MASSIVE_WARHEAD.item().get()), dev.groundzero.content.assembly.SizeClass.L, dev.groundzero.content.assembly.MissileParts.Motor.SRM_II, dev.groundzero.content.assembly.MissileParts.Guidance.GPS, false)));
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, silo);
                }
            });
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
