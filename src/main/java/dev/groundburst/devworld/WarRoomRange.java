package dev.groundburst.devworld;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.groundburst.GroundBurst;
import dev.groundburst.client.fx.DevWorlds;
import dev.groundburst.content.alarm.AlarmBlock;
import dev.groundburst.content.alarm.AlarmBlockEntity;
import dev.groundburst.content.alarm.Alarms;
import dev.groundburst.content.alarm.GBAlarms;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.command.DeadHandBlock;
import dev.groundburst.content.command.DeadHandBlockEntity;
import dev.groundburst.content.command.GBCommand;
import dev.groundburst.content.command.SeismographBlock;
import dev.groundburst.content.command.SeismographBlockEntity;
import dev.groundburst.content.command.WarRoomDisplayBlockEntity;
import dev.groundburst.content.construction.BlastDoorBlock;
import dev.groundburst.content.construction.BunkerLampBlock;
import dev.groundburst.content.construction.FilingCabinetBlock;
import dev.groundburst.content.construction.FuseBoxBlock;
import dev.groundburst.content.construction.GBConstruction;
import dev.groundburst.content.construction.MapTableBlock;
import dev.groundburst.content.construction.TapeComputerBlock;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchCodesItem;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.LaunchStationBlockEntity;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.manual.GBManual;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.StructureSpawnerItem;
import dev.groundburst.content.ordnance.GBOrdnance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development aid, left out of the released jar (build.gradle): the world the war room is tested in (DESIGN.md §17.17),
 * everything built, linked, loaded and powered, as the user asked ("a world for me already equipped with the neccasary
 * multiblocks built and ready for me to test"). With {@code -Dgroundburst.warRoom=true} (the {@code warRoom} Gradle run)
 * the client makes a flat creative world, "Ground Burst War Room" (run/saves/ground_burst_war_room), and builds:
 * <ul>
 * <li>at the spawn, the command bunker: the War Room Display on its north wall, the base's Launch Control Station, a
 * Seismograph, a Missile Warning Receiver (512 blocks), an Alert Display, a Klaxon and a Warning Beacon, the command
 * room's fittings; the Air-Raid Siren outside its blast door;</li>
 * <li>round it, a Radar Array, two more Seismographs 100 blocks east and west, the Dead Hand (armed, its rocket loaded, set
 * to fire on any strike), and the base's launchers (two Launch Silos with HE missiles, a Launch Complex with a tactical
 * nuke) aimed at the enemy's launchers (the war plan); 1200 blocks east, the base's far Missile Silo, beyond the command
 * rocket's first reach, for it to fly over (DESIGN.md §17.19);</li>
 * <li>900 blocks north, the enemy's launch site: two Launch Silos (HE) aimed at a mock town 150 blocks north-east of the
 * base, a Launch Complex (a tactical nuke) aimed at a field 250 blocks north-west of it; their network is run from the
 * ENEMY COMMAND booth beside the bunker, a second Launch Control Station;</li>
 * <li>everything powered by Creative Capacitor Banks; signs saying what each is and what to do; the player in creative
 * with both networks' Launch Codes, a spare Command Rocket and a Field Manual.</li>
 * </ul>
 * Then it photographs the base (run/screenshots/war_room_*.png) and quits, the world saved to copy into an instance's
 * saves. With {@code -Dgroundburst.warRoom.demo=true} it builds the same in a throwaway world
 * (ground_burst_war_room_demo) and plays the attack through instead: the enemy's salvo, the alarms, the seismographs, the
 * display's condition, the Dead Hand's alert and its firing, the base's retaliation, each photographed
 * (war_room_demo_*.png) and checked in the log ("War room check: ... OK/WRONG").
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = GroundBurst.MODID)
public final class WarRoomRange {
    /** The bunker's room (inside), the War Room Display's controller, the base's things (east of x, north of z), the far sites. */
    private static final BlockPos DISPLAY = new BlockPos(0, 1, -9), STATION = new BlockPos(-6, 1, -2), BOOTH = new BlockPos(-24, 1, 20),
            SEISMOGRAPH = new BlockPos(7, 1, 4), RADAR = new BlockPos(-26, 1, -4), DEAD_HAND = new BlockPos(20, 1, 14);
    private static final BlockPos SILO_1 = new BlockPos(30, 1, -12), SILO_2 = new BlockPos(38, 1, -12), COMPLEX = new BlockPos(48, 1, -14);
    /** The base's far Missile Silo, its top ring flush with the ground: the command rocket flies over it (DESIGN.md §17.19). */
    private static final BlockPos FAR_SILO = new BlockPos(1200, 0, -20);
    private static final BlockPos ENEMY_1 = new BlockPos(-8, 1, -900), ENEMY_2 = new BlockPos(0, 1, -900), ENEMY_3 = new BlockPos(12, 1, -898);
    private static final BlockPos TOWN = new BlockPos(110, 1, -110), FIELD = new BlockPos(-180, 1, -160);
    private static final int HUT = 100;

    private static boolean worldMade, built, armed;
    private static int waited, step;
    /** What the server made (read on the client: the demo's script). */
    private static volatile long baseNetwork, enemyNetwork;
    private static volatile int ground = Integer.MIN_VALUE;
    private static final List<String> CHECKS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private WarRoomRange() {}

    static boolean enabled() {
        return Boolean.getBoolean("groundburst.warRoom");
    }

    static boolean demo() {
        return Boolean.getBoolean("groundburst.warRoom.demo");
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post e) {
        if (enabled()) {
            tick();
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.gui.setScreen(null);
        }
        if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        }
        if (mc.level == null && !worldMade && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            try {
                String name = demo() ? "Ground Burst War Room (demo)" : "Ground Burst War Room";
                var settings = new net.minecraft.world.level.LevelSettings(name, GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = DevWorlds.fresh(mc, demo() ? "ground_burst_war_room_demo" : "ground_burst_war_room");
                mc.createWorldOpenFlows().createFreshLevel(folder, settings, new net.minecraft.world.level.levelgen.WorldOptions(0L, false, false),
                        dev.groundburst.client.fx.SitePhotographer::deepFlat, mc.gui.screen());
            } catch (java.io.IOException ex) {
                GroundBurst.LOGGER.error("War room range: could not make a world", ex);
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        if (!built) {
            built = true;
            server.execute(() -> build(server.overworld()));
            return;
        }
        waited++;
        if (!armed && waited == 100) {
            armed = true;
            server.execute(() -> arm(server.overworld()));
            return;
        }
        if (waited < 160 || ground == Integer.MIN_VALUE) {
            return;
        }
        if (demo()) {
            Demo.tick(mc, server);
        } else if (photos(mc, server)) {
            GroundBurst.LOGGER.info("War room range: built and photographed; the world is saved as it quits");
            mc.stop();
        }
    }

    // --- the pictures --------------------------------------------------------------------------------------------------------
    /** A picture: its name, the camera's place (feet), which way it looks (yaw: 0 south, 90 west, 180 north), how far down. */
    record View(String name, Vec3 at, float yaw, float pitch, int settle) {}

    private static List<View> views(int y) {
        return List.of(
                new View("display", new Vec3(0.5, y + 1, -2.5), 180, -8, 160),
                new View("seismograph", new Vec3(5.2, y + 1, 4.5), -90, 30, 100),
                new View("bunker", new Vec3(8.3, y + 1, 6.3), 140, 12, 120),
                new View("console", new Vec3(17.6, y + 1, 14.5), -90, 6, 120),
                new View("base", new Vec3(52.5, y + 30, 52.5), 135, 24, 200),
                new View("dead_hand", new Vec3(13.5, y + 1.5, 19.5), -125, 6, 120),
                new View("booth", new Vec3(BOOTH.getX() + 0.5, y + 1.5, BOOTH.getZ() + 7.5), 180, 8, 120),
                new View("enemy_site", new Vec3(2.5, y + 22, -858.5), 180, 26, 300),
                new View("town", new Vec3(TOWN.getX() + 0.5, y + 24, TOWN.getZ() + 40.5), 180, 30, 240),
                new View("far_silo", new Vec3(FAR_SILO.getX() + 14.5, y + 12, FAR_SILO.getZ() + 18.5), 140, 28, 240));
    }

    private static boolean photos(Minecraft mc, MinecraftServer server) {
        List<View> views = views(ground);
        hideHud(mc);
        if (step > views.size()) {
            return true;
        }
        View next = step < views.size() ? views.get(step) : new View("spawn", new Vec3(0.5, ground + 1, 18.5), 180, 6, 0);
        int at = waited - 160;
        if (at == 0) {
            teleport(server, next);
        }
        if (step < views.size() && at == next.settle()) {
            Screenshot.grab(mc.gameDirectory, "war_room_" + next.name() + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> GroundBurst.LOGGER.info("War room range: {}", msg.getString()));
        }
        if (at >= next.settle() + 10) {
            step++;
            waited = 159;
        }
        return false;
    }

    /** No hand or hotbar in the pictures. */
    static void hideHud(Minecraft mc) {
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
    }

    /** The camera to {@code v}: flying (or a view from the air falls to the ground), but on its feet at the spawn. */
    static void teleport(MinecraftServer server, View v) {
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
            p.getAbilities().flying = !v.name().equals("spawn");
            p.onUpdateAbilities();
            p.teleportTo(server.overworld(), v.at().x, v.at().y, v.at().z, Set.of(), v.yaw(), v.pitch(), true);
        });
    }

    // --- building --------------------------------------------------------------------------------------------------------
    /** On the server: the rules, the chunks, everything, the networks, the player. */
    private static void build(ServerLevel level) {
        var commands = level.getServer().getCommands();
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        for (String c : List.of("time set 1000", "gamerule advance_weather false", "weather clear", "gamerule spawn_mobs false",
                "difficulty peaceful", "kill @e[type=!player]")) {
            commands.performPrefixedCommand(source, c);
        }
        for (int cx = -224 >> 4; cx <= 176 >> 4; cx++) {
            for (int cz = -208 >> 4; cz <= 64 >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        for (int cx = -48 >> 4; cx <= 48 >> 4; cx++) {
            for (int cz = -944 >> 4; cz <= -848 >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        for (int cx = (FAR_SILO.getX() - 24) >> 4; cx <= (FAR_SILO.getX() + 24) >> 4; cx++) {
            for (int cz = (FAR_SILO.getZ() - 24) >> 4; cz <= (FAR_SILO.getZ() + 24) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) - 1;
        Builder b = new Builder(level, y);
        b.bunker();
        b.outside();
        b.town();
        b.field();
        b.enemySite();
        b.farSilo();
        b.networks();
        b.player();
        ground = y;
        GroundBurst.LOGGER.info("War room range: built on the grass at y {}", y + 1);
    }

    /** A few seconds on (everything has ticked: powered): the Dead Hand armed, and what stands reported. */
    private static void arm(ServerLevel level) {
        int y = ground;
        if (level.getBlockEntity(new BlockPos(DEAD_HAND.getX(), y + DEAD_HAND.getY(), DEAD_HAND.getZ())) instanceof DeadHandBlockEntity dh) {
            dh.order(level, DeadHandBlockEntity.ARM, 0);
            dh.order(level, DeadHandBlockEntity.ARM, 0);
            GroundBurst.LOGGER.info("War room range: the Dead Hand {} (radius {}, at magnitude {}, {} s), its rocket {}", dh.state(),
                    DeadHandBlockEntity.RADII[dh.radius()], DeadHandBlockEntity.THRESHOLDS[dh.threshold()],
                    DeadHandBlockEntity.COUNTDOWNS[dh.countdownSetting()] / 20, dh.rocket() ? "loaded" : "missing");
        }
        LaunchNetwork nets = LaunchNetwork.of(level);
        for (long id : new long[] {baseNetwork, enemyNetwork}) {
            LaunchNetwork.Network n = nets.network(id);
            if (n != null) {
                GroundBurst.LOGGER.info("War room range: network {}: {} members, {} stations", n.code(), n.members().size(), n.stations().size());
            }
        }
    }

    /** Builds the range on the grass at {@code y} (+1 is the ground floor). */
    static final class Builder {
        final ServerLevel level;
        final int y;
        final BlockState concrete;

        Builder(ServerLevel level, int y) {
            this.level = level;
            this.y = y;
            this.concrete = GBLaunch.REINFORCED_CONCRETE.block().get().defaultBlockState();
        }

        BlockPos at(int x, int dy, int z) {
            return new BlockPos(x, this.y + dy, z);
        }

        BlockPos at(BlockPos rel) {
            return new BlockPos(rel.getX(), this.y + rel.getY(), rel.getZ());
        }

        void set(BlockPos p, BlockState s) {
            this.level.setBlock(p, s, Block.UPDATE_ALL);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (BlockPos p : BlockPos.betweenClosed(x0, this.y + y0, z0, x1, this.y + y1, z1)) {
                this.level.setBlock(p, s, Block.UPDATE_CLIENTS);
            }
        }

        // --- the command bunker ---------------------------------------------------------------------------------------
        /** Reinforced concrete, 21 by 19 outside and 7 high inside, a blast door in its south wall; the war room inside. */
        void bunker() {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (BlockPos p : BlockPos.betweenClosed(-10, this.y, -10, 10, this.y + 8, 8)) {
                boolean shell = p.getY() == this.y || p.getY() == this.y + 8 || p.getX() == -10 || p.getX() == 10 || p.getZ() == -10 || p.getZ() == 8;
                this.level.setBlock(p, shell ? this.concrete : air, Block.UPDATE_CLIENTS);
            }
            // The blast door, and a concrete apron and path out to the south.
            BlastDoorBlock door = GBConstruction.BLAST_DOOR.block().get();
            BlockState ds = door.defaultBlockState().setValue(BlastDoorBlock.FACING, Direction.SOUTH);
            BlockPos d = at(0, 1, 8);
            Direction across = BlastDoorBlock.across(ds);
            for (int u = 0; u < 2; u++) {
                for (int v = 0; v < 3; v++) {
                    this.level.setBlock(d.relative(across, u).above(v), air, Block.UPDATE_CLIENTS);
                }
            }
            set(d, ds);
            door.setPlacedBy(this.level, d, ds, null, ItemStack.EMPTY);
            fill(-3, 0, 9, 4, 0, 13, this.concrete);
            fill(0, 0, 14, 1, 0, 30, Blocks.DIRT_PATH.defaultBlockState());
            // The War Room Display on the north wall, its port fed from a bank set in the wall behind it.
            BlockPos display = at(DISPLAY);
            place(GBCommand.WAR_ROOM_CONTROLLER.block().get(), GBCommand.WAR_ROOM, display, Direction.SOUTH);
            power(GBCommand.WAR_ROOM, display, Direction.SOUTH, true);
            if (this.level.getBlockEntity(display) instanceof WarRoomDisplayBlockEntity w) {
                w.energy().generate(WarRoomDisplayBlockEntity.ENERGY_CAPACITY);
            }
            // The base's Launch Control Station on the west side, facing into the room.
            BlockPos station = at(STATION);
            place(GBLaunch.STATION_CONTROLLER.block().get(), GBLaunch.STATION, station, Direction.EAST);
            power(GBLaunch.STATION, station, Direction.EAST, false);
            charge(station);
            // The Seismograph by the east wall, its bank behind it.
            BlockPos seis = at(SEISMOGRAPH);
            set(seis, GBCommand.SEISMOGRAPH.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.WEST));
            bank(seis.east(), Direction.WEST);
            if (this.level.getBlockEntity(seis) instanceof SeismographBlockEntity s) {
                s.energy().generate(SeismographBlockEntity.ENERGY_CAPACITY);
                s.applySettings(this.level, SeismographBlockEntity.DEFAULT_SENSITIVITY, 2, 0);   // an alarm at magnitude 3
            }
            // The alarms: the receiver (512 blocks), the display by the board, the klaxon high on the wall, the beacon on the
            // ceiling; the siren outside on its post.
            alarm(GBAlarms.RECEIVER.block().get().defaultBlockState().setValue(AlarmBlock.HORIZONTAL, Direction.EAST), at(-9, 1, 6), new int[] {3, 0});
            alarm(GBAlarms.DISPLAY.block().get().defaultBlockState().setValue(AlarmBlock.HORIZONTAL, Direction.SOUTH), at(6, 3, -9), null);
            alarm(GBAlarms.KLAXON.block().get().defaultBlockState().setValue(AlarmBlock.HORIZONTAL, Direction.SOUTH), at(-6, 5, -9), null);
            alarm(GBAlarms.BEACON.block().get().defaultBlockState().setValue(AlarmBlock.Six.FACING, Direction.DOWN), at(0, 7, 2), null);
            fill(5, 1, 11, 5, 3, 11, Blocks.POLISHED_ANDESITE.defaultBlockState());
            alarm(GBAlarms.SIREN.block().get().defaultBlockState().setValue(AlarmBlock.HORIZONTAL, Direction.SOUTH), at(5, 4, 11), null);
            // The room's fittings: lamps on the ceiling (lit from above), consoles and cabinets down the east wall, the tape
            // drives on the west, two desks facing the board, the map table, a fuse box and an extinguisher by the door.
            for (int[] l : new int[][] {{-5, -5}, {5, -5}, {-5, 3}, {5, 3}, {0, -2}}) {
                set(at(l[0], 8, l[1]), Blocks.REDSTONE_BLOCK.defaultBlockState());
                set(at(l[0], 7, l[1]), GBConstruction.BUNKER_LAMP.block().get().defaultBlockState()
                        .setValue(DirectionalBlock.FACING, Direction.DOWN).setValue(BunkerLampBlock.LIT, true));
            }
            for (int z = -8; z <= -5; z++) {
                set(at(9, 1, z), GBConstruction.CONTROL_CONSOLE.block().get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            }
            for (int z = -2; z <= -1; z++) {
                set(at(9, 1, z), GBConstruction.FILING_CABINET.block().get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST)
                        .setValue(FilingCabinetBlock.OPEN, z == -1));
            }
            TapeComputerBlock tape = GBConstruction.TAPE_COMPUTER.block().get();
            for (int z : new int[] {1, 3}) {
                BlockPos p = at(-9, 1, z);
                BlockState s = tape.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
                        .setValue(TapeComputerBlock.ACTIVE, true).setValue(TapeComputerBlock.SWITCHED, true);
                set(p, s);
                tape.setPlacedBy(this.level, p, s, null, ItemStack.EMPTY);
            }
            for (int x = -1; x <= 1; x++) {
                set(at(x, 1, 0), GBConstruction.COMMAND_DESK.block().get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            }
            MapTableBlock table = GBConstruction.SITUATION_MAP_TABLE.block().get();
            BlockPos tp = at(1, 1, 4);
            BlockState ts = table.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
            set(tp, ts);
            table.setPlacedBy(this.level, tp, ts, null, ItemStack.EMPTY);
            set(at(4, 2, 7), GBConstruction.FUSE_BOX.block().get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                    .setValue(FuseBoxBlock.POWERED, true));
            set(at(-3, 1, 7), GBConstruction.FIRE_EXTINGUISHER.block().get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            // What to do, on the walls.
            wallSign(at(-5, 2, -9), Direction.SOUTH, "WAR ROOM", "DISPLAY", "Click it to set", "its view.");
            wallSign(at(5, 2, -9), Direction.SOUTH, "Codes on it show", "a network (the", "base's: shown", "now).");
            // (Rotation in sixteenths: each sign faces where a player comes from, beside what it tells of, never in front.)
            sign(at(-5, 1, -5), 12, "BASE LAUNCH", "CONTROL", "Pick, aim, ARM,", "LAUNCH.");
            sign(at(-5, 1, 1), 12, "The war plan:", "every launcher", "aimed at the", "enemy site.");
            sign(at(7, 1, 2), 4, "SEISMOGRAPH", "Every blast:", "its size, how", "far, which way.");
            sign(at(7, 1, 6), 4, "Its alarm: a", "Blast alert at", "magnitude 3", "(channel 0).");
            sign(at(-8, 1, 5), 12, "WARNING", "RECEIVER: any", "missile bound", "within 512.");
        }

        /** A Launch Control Station's stored power topped up (its far launchers' orders need it working). */
        void charge(BlockPos controller) {
            if (this.level.getBlockEntity(controller) instanceof MachineBlockEntity m) {
                m.energy().generate(m.energy().capacity());
            }
        }

        // --- round the bunker ------------------------------------------------------------------------------------------
        void outside() {
            // The ENEMY COMMAND booth: red concrete, open to the south, the enemy's Launch Control Station inside.
            int bx = BOOTH.getX(), bz = BOOTH.getZ();
            fill(bx - 3, 0, bz - 3, bx + 3, 0, bz + 3, Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState());
            fill(bx - 3, 1, bz - 3, bx + 3, 4, bz - 3, Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState());
            fill(bx - 3, 1, bz - 2, bx - 3, 4, bz + 2, Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState());
            fill(bx + 3, 1, bz - 2, bx + 3, 4, bz + 2, Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState());
            fill(bx - 3, 5, bz - 3, bx + 3, 5, bz + 2, Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState());
            fill(bx - 2, 1, bz - 2, bx + 2, 4, bz + 2, Blocks.AIR.defaultBlockState());
            BlockPos booth = at(BOOTH);
            place(GBLaunch.STATION_CONTROLLER.block().get(), GBLaunch.STATION, booth, Direction.SOUTH);
            power(GBLaunch.STATION, booth, Direction.SOUTH, true);
            charge(booth);
            set(at(bx, 5, bz + 1), Blocks.REDSTONE_BLOCK.defaultBlockState());                // in the roof, its lamp under it
            set(at(bx, 4, bz + 1), GBConstruction.BUNKER_LAMP.block().get().defaultBlockState()
                    .setValue(DirectionalBlock.FACING, Direction.DOWN).setValue(BunkerLampBlock.LIT, true));
            sign(at(bx - 2, 1, bz + 4), 0, "ENEMY COMMAND", "Play the enemy:", "its launchers", "are 900 north.");
            sign(at(bx + 2, 1, bz + 4), 0, "They aim at the", "town (north-", "east) and the", "field (n.-west).");
            sign(at(bx, 1, bz + 5), 0, "Pick them all,", "ARM, LAUNCH:", "then watch the", "war room.");
            // The Radar Array west of the bunker.
            BlockPos radar = at(RADAR);
            place(GBLaunch.RADAR_CONTROLLER.block().get(), GBLaunch.RADAR, radar, Direction.SOUTH);
            power(GBLaunch.RADAR, radar, Direction.SOUTH, false);
            sign(at(RADAR.getX(), 1, RADAR.getZ() + 3), 0, "RADAR ARRAY", "Tracks every", "missile within", "2048 blocks.");
            // The two far seismographs, a hut each.
            for (int x : new int[] {-HUT, HUT}) {
                hut(x);
            }
            // The Dead Hand, east of the door, its console facing the bunker.
            BlockPos dh = at(DEAD_HAND);
            place(GBCommand.DEAD_HAND_CONTROLLER.block().get(), GBCommand.DEAD_HAND, dh, Direction.WEST);
            power(GBCommand.DEAD_HAND, dh, Direction.WEST, false);
            if (this.level.getBlockEntity(dh) instanceof DeadHandBlockEntity d) {
                d.energy().generate(DeadHandBlockEntity.ENERGY_CAPACITY);
                d.load(this.level);
                d.order(this.level, DeadHandBlockEntity.SET_RADIUS, 2);       // 512
                d.order(this.level, DeadHandBlockEntity.SET_THRESHOLD, 1);    // magnitude 4: a tactical nuke
                d.order(this.level, DeadHandBlockEntity.SET_COUNTDOWN, 0);    // 30 s
                d.order(this.level, DeadHandBlockEntity.SET_COMMAND, 1);      // on any strike (the station here works)
                d.order(this.level, DeadHandBlockEntity.SET_TARGETS, 0);      // the war plan
                d.order(this.level, DeadHandBlockEntity.SET_CHANNEL, 0);
            }
            // Its signs either side of its console, the ground in front of it clear.
            sign(at(DEAD_HAND.getX() - 2, 1, DEAD_HAND.getZ() - 5), 4, "DEAD HAND", "Armed. A strike", "of magnitude 4", "within 512...");
            sign(at(DEAD_HAND.getX() - 2, 1, DEAD_HAND.getZ() - 3), 4, "...readies all", "base launchers:", "they count down", "and wait.");
            sign(at(DEAD_HAND.getX() - 2, 1, DEAD_HAND.getZ() + 3), 4, "At 0 its rocket", "flies over them;", "each fires as", "its signal comes.");
            sign(at(DEAD_HAND.getX() - 2, 1, DEAD_HAND.getZ() + 5), 4, "HOLD stands all", "down. It fires", "on any strike", "(set: command).");
            // The base's launchers east of the bunker: two silos (HE), a complex (a tactical nuke).
            ItemStack he = MissileAssembly.missile(new MissileSpec(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.M,
                    MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS, false));
            ItemStack nuke = MissileAssembly.missile(new MissileSpec(new ItemStack(GBOrdnance.TACTICAL_NUCLEAR_WARHEAD.item().get()), SizeClass.XL,
                    MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS, false));
            launcher(GBLaunch.SILO_CONTROLLER.block().get(), GBLaunch.SILO, at(SILO_1), Direction.NORTH, he.copy());
            launcher(GBLaunch.SILO_CONTROLLER.block().get(), GBLaunch.SILO, at(SILO_2), Direction.NORTH, he.copy());
            launcher(GBLaunch.COMPLEX_CONTROLLER.block().get(), GBLaunch.COMPLEX, at(COMPLEX), Direction.NORTH, nuke.copy());
            sign(at(SILO_1.getX() - 4, 1, SILO_1.getZ() + 5), 0, "BASE LAUNCHERS", "Aimed at the", "enemy site", "(900 north).");
            sign(at(SILO_1.getX() - 4, 1, SILO_1.getZ() + 7), 0, "One more: a", "Missile Silo", "1200 blocks", "east.");
            // The spawn's signs.
            sign(at(-2, 1, 16), 0, "GROUND BURST", "WAR ROOM", "Range: in the", "bunker.");
            sign(at(3, 1, 16), 0, "Codes in your", "pack: Base and", "Enemy. A spare", "Command Rocket.");
        }

        /** A seismograph's hut {@code x} blocks east (or west) of the bunker. */
        void hut(int x) {
            fill(x - 2, 0, -2, x + 2, 0, 2, this.concrete);
            for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                fill(x + c[0], 1, c[1], x + c[0], 3, c[1], Blocks.IRON_BARS.defaultBlockState());
            }
            fill(x - 2, 4, -2, x + 2, 4, 2, GBConstruction.CORRUGATED_STEEL_SLAB.block().get().defaultBlockState());
            BlockPos s = at(x, 1, 0);
            set(s, GBCommand.SEISMOGRAPH.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.SOUTH));
            bank(s.north(), Direction.SOUTH);
            if (this.level.getBlockEntity(s) instanceof SeismographBlockEntity sb) {
                sb.energy().generate(SeismographBlockEntity.ENERGY_CAPACITY);
            }
            sign(at(x, 1, 3), 0, "SEISMOGRAPH", x < 0 ? "west station" : "east station", "On the base's", "network.");
        }

        /** A launcher built, powered (a bank, and its store filled) and loaded with {@code missile}. */
        void launcher(Block controller, MultiblockDefinition def, BlockPos at, Direction facing, ItemStack missile) {
            place(controller, def, at, facing);
            power(def, at, facing, false);
            if (this.level.getBlockEntity(at) instanceof LaunchSiloBlockEntity s) {
                s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, missile);
                s.energy().generate(s.energy().capacity());
            }
        }

        // --- the targets ----------------------------------------------------------------------------------------------
        /** A mock town: houses round a square, a church tower. */
        void town() {
            int tx = TOWN.getX(), tz = TOWN.getZ();
            fill(tx - 6, 0, tz - 6, tx + 6, 0, tz + 6, Blocks.COBBLESTONE.defaultBlockState());
            int[][] houses = {{-16, -14}, {-4, -16}, {9, -15}, {-17, 2}, {12, 1}, {-15, 14}, {-2, 15}, {10, 14}};
            for (int[] h : houses) {
                house(tx + h[0], tz + h[1]);
            }
            fill(tx - 2, 1, tz - 2, tx + 2, 12, tz + 2, Blocks.STONE_BRICKS.defaultBlockState());
            fill(tx - 1, 1, tz - 1, tx + 1, 11, tz + 1, Blocks.AIR.defaultBlockState());
            fill(tx - 2, 13, tz - 2, tx + 2, 13, tz + 2, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            set(at(tx, 14, tz), Blocks.DARK_OAK_PLANKS.defaultBlockState());
            sign(at(tx, 1, tz + 4), 0, "THE TOWN", "The enemy's HE", "missiles aim", "here.");
        }

        void house(int x, int z) {
            fill(x - 3, 0, z - 3, x + 3, 0, z + 3, Blocks.OAK_PLANKS.defaultBlockState());
            fill(x - 3, 1, z - 3, x + 3, 3, z + 3, Blocks.COBBLESTONE.defaultBlockState());
            fill(x - 2, 1, z - 2, x + 2, 3, z + 2, Blocks.AIR.defaultBlockState());
            for (int[] w : new int[][] {{0, -3}, {0, 3}, {-3, 0}, {3, 0}}) {
                set(at(x + w[0], 2, z + w[1]), Blocks.GLASS_PANE.defaultBlockState());
            }
            for (int k = 0; k <= 3; k++) {
                fill(x - 3 + k, 4 + k, z - 3, x + 3 - k, 4 + k, z + 3, k == 3 ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
            BlockPos door = at(x + 1, 1, z + 3);
            this.level.setBlock(door, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            this.level.setBlock(door.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockState ds = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
            set(door, ds);
            Blocks.OAK_DOOR.setPlacedBy(this.level, door, ds, null, ItemStack.EMPTY);
        }

        /** The field the enemy's nuke aims at: hay bales round its middle. */
        void field() {
            int fx = FIELD.getX(), fz = FIELD.getZ();
            for (int[] h : new int[][] {{0, 0}, {6, 3}, {-5, 4}, {3, -6}, {-4, -5}, {9, -2}, {-8, 0}}) {
                fill(fx + h[0], 1, fz + h[1], fx + h[0] + 1, 2, fz + h[1] + 1, Blocks.HAY_BLOCK.defaultBlockState());
            }
            sign(at(fx, 1, fz + 10), 0, "THE FIELD", "The enemy's", "tactical nuke", "aims here.");
        }

        /** The enemy's launch site, 900 blocks north: two silos (HE) on the town, a complex (a tactical nuke) on the field. */
        void enemySite() {
            fill(-16, 0, -906, 20, 0, -892, this.concrete);
            ItemStack he = MissileAssembly.missile(new MissileSpec(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.M,
                    MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS, false));
            ItemStack nuke = MissileAssembly.missile(new MissileSpec(new ItemStack(GBOrdnance.TACTICAL_NUCLEAR_WARHEAD.item().get()), SizeClass.XL,
                    MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS, false));
            launcher(GBLaunch.SILO_CONTROLLER.block().get(), GBLaunch.SILO, at(ENEMY_1), Direction.SOUTH, he.copy());
            launcher(GBLaunch.SILO_CONTROLLER.block().get(), GBLaunch.SILO, at(ENEMY_2), Direction.SOUTH, he.copy());
            launcher(GBLaunch.COMPLEX_CONTROLLER.block().get(), GBLaunch.COMPLEX, at(ENEMY_3), Direction.SOUTH, nuke.copy());
            for (int x = -16; x <= 20; x += 4) {
                set(at(x, 1, -889), GBConstruction.JERSEY_BARRIER.block().get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
            }
            sign(at(2, 1, -887), 0, "ENEMY LAUNCH", "SITE", "Run from ENEMY", "COMMAND (base).");
        }

        /**
         * The base's far Missile Silo, 1200 blocks east (DESIGN.md §17.19): dug in, its top ring flush with the ground, a
         * heavy HE missile in it, aimed (with the rest) at the enemy site. Out of the command rocket's first reach: it fires
         * as the rocket flies over.
         */
        void farSilo() {
            BlockPos s = at(FAR_SILO);
            fill(FAR_SILO.getX() - 6, 0, FAR_SILO.getZ() - 4, FAR_SILO.getX() + 6, 0, FAR_SILO.getZ() + 9, this.concrete);
            place(GBLaunch.MISSILE_SILO_CONTROLLER.block().get(), GBLaunch.MISSILE_SILO, s, Direction.NORTH);
            power(GBLaunch.MISSILE_SILO, s, Direction.NORTH, true);
            if (this.level.getBlockEntity(s) instanceof LaunchSiloBlockEntity silo) {
                silo.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, MissileAssembly.missile(new MissileSpec(
                        new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.L, MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS, false)));
                silo.energy().generate(silo.energy().capacity());
            }
            sign(at(FAR_SILO.getX() - 4, 1, FAR_SILO.getZ() + 7), 0, "FAR SILO", "On the base's", "network. Fires", "as the rocket");
            sign(at(FAR_SILO.getX() + 4, 1, FAR_SILO.getZ() + 7), 0, "...comes over:", "its signal is", "heard 256-480", "blocks round.");
        }

        // --- the networks ---------------------------------------------------------------------------------------------
        /** The base's network (its station's) and the enemy's (the booth's): members linked with codes, launchers aimed. */
        void networks() {
            LaunchNetwork nets = LaunchNetwork.of(this.level);
            ItemStack base = codes(at(STATION)), enemy = codes(at(BOOTH));
            List<BlockPos> members = new ArrayList<>(List.of(at(DISPLAY), at(SEISMOGRAPH), at(-HUT, 1, 0), at(HUT, 1, 0), at(RADAR), at(DEAD_HAND),
                    at(SILO_1), at(SILO_2), at(COMPLEX), at(FAR_SILO)));
            for (BlockPos p : members) {
                link(base, p);
            }
            for (BlockPos p : List.of(at(ENEMY_1), at(ENEMY_2), at(ENEMY_3))) {
                link(enemy, p);
            }
            LaunchNetwork.Network b = station(at(STATION)), e = station(at(BOOTH));
            if (b == null || e == null) {
                GroundBurst.LOGGER.error("War room range: a station did not form ({}, {})", b, e);
                return;
            }
            baseNetwork = b.id;
            enemyNetwork = e.id;
            // The war plan: each base launcher on an enemy launcher; the enemy's on the town's houses and the field.
            nets.aim(this.level, b.id, List.of(at(SILO_1)), ENEMY_1.getX(), ENEMY_1.getZ(), LaunchNetwork.Pattern.POINT, 0);
            nets.aim(this.level, b.id, List.of(at(SILO_2)), ENEMY_2.getX(), ENEMY_2.getZ(), LaunchNetwork.Pattern.POINT, 0);
            nets.aim(this.level, b.id, List.of(at(COMPLEX)), ENEMY_3.getX() + 2, ENEMY_3.getZ() - 2, LaunchNetwork.Pattern.POINT, 0);
            nets.aim(this.level, b.id, List.of(at(FAR_SILO)), ENEMY_2.getX() + 2, ENEMY_2.getZ() + 6, LaunchNetwork.Pattern.POINT, 0);
            nets.aim(this.level, e.id, List.of(at(ENEMY_1)), TOWN.getX() - 16, TOWN.getZ() - 14, LaunchNetwork.Pattern.POINT, 0);
            nets.aim(this.level, e.id, List.of(at(ENEMY_2)), TOWN.getX() + 12, TOWN.getZ() + 1, LaunchNetwork.Pattern.POINT, 0);
            nets.aim(this.level, e.id, List.of(at(ENEMY_3)), FIELD.getX(), FIELD.getZ(), LaunchNetwork.Pattern.POINT, 0);
            base.set(DataComponents.CUSTOM_NAME, Component.literal("Launch Codes: Base (" + b.code() + ")"));
            enemy.set(DataComponents.CUSTOM_NAME, Component.literal("Launch Codes: Enemy (" + e.code() + ")"));
            this.codes = List.of(base, enemy);
            GroundBurst.LOGGER.info("War room range: base network {} ({} members), enemy network {} ({} members)", b.code(), b.members().size(),
                    e.code(), e.members().size());
        }

        List<ItemStack> codes = List.of();

        ItemStack codes(BlockPos station) {
            ItemStack card = new ItemStack(GBLaunch.LAUNCH_CODES.get());
            if (this.level.getBlockEntity(station) instanceof LaunchStationBlockEntity s) {
                LaunchCodesItem.use(this.level, card, s, false);
            }
            return card;
        }

        LaunchNetwork.Network station(BlockPos at) {
            return this.level.getBlockEntity(at) instanceof LaunchStationBlockEntity s ? s.network(this.level) : null;
        }

        void link(ItemStack card, BlockPos at) {
            var be = LaunchCodesItem.target(this.level, at);
            if (be == null) {
                GroundBurst.LOGGER.error("War room range: nothing to link at {}", at);
                return;
            }
            Component said = LaunchCodesItem.use(this.level, card, be, false);
            GroundBurst.LOGGER.info("War room range: codes on {} at {}: {}", be.getClass().getSimpleName(), at.toShortString(), said.getString());
        }

        /** The player: creative, at the spawn, with both cards, a spare rocket and the Field Manual. */
        void player() {
            var commands = this.level.getServer().getCommands();
            var source = this.level.getServer().createCommandSourceStack().withSuppressedOutput();
            commands.performPrefixedCommand(source, "setworldspawn 0 " + (this.y + 1) + " 18");
            ServerPlayer player = this.level.getServer().getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            for (ItemStack c : this.codes) {
                player.getInventory().add(c.copy());
            }
            player.getInventory().add(new ItemStack(GBCommand.COMMAND_ROCKET.get()));
            player.getInventory().add(new ItemStack(GBManual.FIELD_MANUAL.get()));
            player.teleportTo(this.level, 0.5, this.y + 1, 18.5, Set.of(), 180, 6, true);
        }

        // --- pieces ---------------------------------------------------------------------------------------------------
        /** A multiblock placed whole (as its Structure Spawner places it) and formed. */
        void place(Block controller, MultiblockDefinition def, BlockPos at, Direction facing) {
            for (MultiblockDefinition.Cell cell : StructureSpawnerItem.buildOrder(def, at, facing)) {
                BlockState s;
                if (cell.key() == '.') {
                    s = Blocks.AIR.defaultBlockState();
                } else if (cell.key() == MultiblockDefinition.CONTROLLER) {
                    s = controller.defaultBlockState().setValue(MachineBlock.FACING, facing);
                } else {
                    s = def.legend().get(cell.key()).preview().get();
                }
                set(cell.pos(), s);
            }
            if (this.level.getBlockEntity(at) instanceof MultiblockController c) {
                c.revalidate();
                GroundBurst.LOGGER.info("War room range: {} at {} {}", def.id(), at.toShortString(), c.isFormed() ? "formed" : "NOT FORMED");
            }
        }

        /**
         * A Creative Capacitor Bank by each of the structure's Energy Ports, on a side clear of it, facing the port (on
         * the ground beside it, or in the wall behind it where {@code inWall}).
         */
        void power(MultiblockDefinition def, BlockPos at, Direction facing, boolean inWall) {
            Set<BlockPos> cells = new java.util.HashSet<>();
            for (MultiblockDefinition.Cell c : def.cells(at, facing)) {
                cells.add(c.pos().immutable());
            }
            for (BlockPos port : def.positionsOf('E', at, facing)) {
                for (Direction d : List.of(facing.getOpposite(), facing.getClockWise(), facing.getCounterClockWise(), facing)) {
                    BlockPos p = port.relative(d);
                    if (!cells.contains(p) && (inWall || this.level.getBlockState(p).isAir())) {
                        bank(p, d.getOpposite());
                        break;
                    }
                }
            }
        }

        /** A Creative Capacitor Bank at {@code p}, its front (which gives power out) facing {@code toward}. */
        void bank(BlockPos p, Direction toward) {
            set(p, GBMachines.CAPACITOR_BANKS.get(3).block().get().defaultBlockState().setValue(MachineBlock.FACING, toward));
        }

        void alarm(BlockState state, BlockPos p, int[] options) {
            set(p, state);
            if (options != null && this.level.getBlockEntity(p) instanceof AlarmBlockEntity a) {
                a.applySettings(0, options);
            }
        }

        /** A standing oak sign (rotation in sixteenths: 0 faces south, 4 west, 8 north, 12 east), four lines. */
        void sign(BlockPos at, int rotation, String... lines) {
            set(at, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation));
            write(at, lines);
        }

        /** An oak sign on a wall, facing {@code facing}. */
        void wallSign(BlockPos at, Direction facing, String... lines) {
            set(at, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing));
            write(at, lines);
        }

        private void write(BlockPos at, String... lines) {
            if (this.level.getBlockEntity(at) instanceof SignBlockEntity sign) {
                SignText.Mutable text = sign.getText(SignTextSlot.FRONT).asMutable();
                for (int i = 0; i < Math.min(4, lines.length); i++) {
                    text.setLine(i, Component.literal(lines[i]));
                }
                sign.setText(text.asImmutable(), SignTextSlot.FRONT);
                sign.setChanged();
                this.level.sendBlockUpdated(at, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    // --- the demo: the attack played through, photographed and checked ---------------------------------------------------
    /**
     * The attack in order, each step waiting for what the world does (the silos' countdowns, the missiles' flights, the Dead
     * Hand's thirty seconds, its rocket's flight): the calm; the enemy's salvo launched and in the air (INCOMING, the
     * alarms); the strikes (the seismographs' records, the network's fixes, ATTACK, the Dead Hand in alert, its console, the
     * base's launchers standing by); the lid opening; zero (the rocket out of its tube, the base's launchers firing at once,
     * the War Room Display's screen with the rocket's signal round it); the far silo firing as the rocket comes over; the
     * rocket down and the base's missiles on their way. What the server holds is read into {@link #polled} every ten ticks.
     */
    static final class Demo {
        private static int phase, at, mark = -1;
        /**
         * Read on the server: the missiles in the air (all, and the base's), the Dead Hand's state and its ticks to zero,
         * whether the far silo has fired.
         */
        private static volatile int flying, ours, deadHand;
        private static volatile long toZero = Long.MAX_VALUE;
        private static volatile boolean farFired;

        private Demo() {}

        /** One check: logged OK or WRONG, with what was found. */
        static void check(String what, boolean ok, Object found) {
            String line = String.format("War room check: %s: %s (%s)", what, ok ? "OK" : "WRONG", found);
            CHECKS.add(line);
            GroundBurst.LOGGER.info(line);
        }

        static void shot(Minecraft mc, String name) {
            Screenshot.grab(mc.gameDirectory, "war_room_demo_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> GroundBurst.LOGGER.info("War room demo: {}", msg.getString()));
        }

        private static void polled(ServerLevel level, int y) {
            List<Flights.Flight> all = Flights.of(level).all();
            flying = all.size();
            ours = (int) all.stream().filter(f -> f.start().z > -300 && !f.harmless()).count();
            DeadHandBlockEntity dh = deadHandOf(level, y);
            deadHand = dh.state().ordinal();
            toZero = dh.state() == DeadHandBlockEntity.State.ALERT ? dh.alertUntil() - level.getGameTime() : Long.MAX_VALUE;
            farFired = farOf(level, y).inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty();
        }

        static void tick(Minecraft mc, MinecraftServer server) {
            int y = ground;
            hideHud(mc);
            at++;
            ServerLevel level = server.overworld();
            if (at % 10 == 0) {
                server.execute(() -> polled(level, y));
            }
            View inside = new View("inside", new Vec3(0.5, y + 1, -2.5), 180, -8, 0);
            // Before its console (within a screen's reach), and up by its tube's corner: the lid, the rocket.
            View console = new View("console", new Vec3(16.5, y + 1, 17.5), -127, 8, 0);
            View roof = new View("roof", new Vec3(24.5, y + 5, 22.5), 155, 14, 0);
            View base = new View("base", new Vec3(52.5, y + 30, 52.5), 135, 24, 0);
            View far = new View("far", new Vec3(FAR_SILO.getX() - 9.5, y + 6, FAR_SILO.getZ() + 14.5), -145, 14, 0);
            BlockPos display = new BlockPos(DISPLAY.getX(), y + DISPLAY.getY(), DISPLAY.getZ());
            switch (phase) {
                case 0 -> {                                      // the calm: in the room, the board at 5
                    if (at == 1) {
                        teleport(server, inside);
                    } else if (at == 120) {
                        server.execute(() -> calm(level, y));
                        shot(mc, "1_calm");
                        dev.groundburst.content.launch.LaunchStationBlock.screenHook.accept(new BlockPos(STATION.getX(), y + STATION.getY(), STATION.getZ()));
                    } else if (at == 180) {
                        shot(mc, "1b_station_map");
                        mc.gui.setScreen(null);
                        dev.groundburst.content.command.WarRoomDisplayBlock.screenHook.accept(display);
                    } else if (at == 240) {
                        shot(mc, "1c_war_room_screen");
                        mc.gui.setScreen(null);
                        teleport(server, new View("seismograph", new Vec3(4.6, y + 1, 4.5), -90, 28, 0));
                    } else if (at == 290) {
                        shot(mc, "1d_seismograph");
                        teleport(server, inside);
                    } else if (at == 320) {
                        server.execute(() -> launchEnemy(level));
                        next();
                    }
                }
                case 1 -> {                                      // counting down, then in the air: INCOMING, the alarms
                    if (mark < 0 && at > 20 && flying >= 3) {
                        mark = at;
                    } else if (mark > 0 && at == mark + 60) {
                        server.execute(() -> incoming(level, y));
                        shot(mc, "2_incoming");
                    } else if (mark > 0 && at > mark + 80 && flying == 0) {
                        next();                                  // all three down
                    } else if (at > 3600) {
                        check("the salvo came down within three minutes", false, flying + " still flying");
                        next();
                    }
                }
                case 2 -> {                                      // the strikes: the records, the fixes, ATTACK, the Dead Hand's alert
                    if (at == 60) {
                        server.execute(() -> struck(level, y));
                        shot(mc, "3_attack");
                        dev.groundburst.content.launch.LaunchStationBlock.screenHook.accept(new BlockPos(STATION.getX(), y + STATION.getY(), STATION.getZ()));
                    } else if (at == 110) {
                        shot(mc, "3b_station_attack");
                        mc.gui.setScreen(null);
                    } else if (at == 115) {
                        SeismographBlock.screenHook.accept(new BlockPos(SEISMOGRAPH.getX(), y + SEISMOGRAPH.getY(), SEISMOGRAPH.getZ()));
                    } else if (at == 160) {
                        shot(mc, "4_seismograph_screen");
                        mc.gui.setScreen(null);
                        teleport(server, console);
                    } else if (at == 250) {
                        shot(mc, "5_dead_hand_alert");
                        DeadHandBlock.screenHook.accept(new BlockPos(DEAD_HAND.getX(), y + DEAD_HAND.getY(), DEAD_HAND.getZ()));
                    } else if (at == 310) {
                        shot(mc, "6_dead_hand_console");
                        mc.gui.setScreen(null);
                        server.execute(() -> standingBy(level, y));
                        teleport(server, base);
                    } else if (at > 310 && mark == -1 && toZero <= 60) {
                        mark = at;                               // the base's launchers at their last seconds
                        shot(mc, "6b_launchers_standing_by");
                        teleport(server, roof);
                    } else if (mark > 0 && toZero <= 15) {
                        shot(mc, "7_dead_hand_lid");             // its lid swinging open in the last two seconds
                        mark = -3;
                    } else if (at > 310 && deadHand == DeadHandBlockEntity.State.FIRING.ordinal()) {
                        next();
                    } else if (at > 1500) {
                        check("the Dead Hand fired within its countdown", false, DeadHandBlockEntity.State.values()[deadHand]);
                        next();
                    }
                }
                case 3 -> {                                      // zero: the rocket out, the base's launchers away, the screen
                    if (at == 12) {
                        shot(mc, "8_command_rocket");
                    } else if (at == 30) {
                        server.execute(() -> fired(level, y));
                        teleport(server, inside);
                    } else if (at == 45) {
                        dev.groundburst.content.command.WarRoomDisplayBlock.screenHook.accept(display);
                    } else if (at == 95) {
                        shot(mc, "9_war_room_screen_rocket");
                        mc.gui.setScreen(null);
                        teleport(server, far);
                    } else if (at > 95 && farFired) {
                        server.execute(() -> farCheck(level, y));
                        next();
                    } else if (at > 1200) {
                        check("the far silo fired as the rocket came over", false, "it never fired");
                        next();
                    }
                }
                case 4 -> {                                      // the far silo's missile away; then the rocket down
                    if (at == 6) {
                        shot(mc, "10_far_silo_fires");           // lit at the foot of its shaft, the vents smoking
                    } else if (at == 66) {
                        shot(mc, "10b_far_silo_away");           // out of the shaft and climbing
                    } else if (at > 66 && at < 900 && deadHand == DeadHandBlockEntity.State.SPENT.ordinal()) {
                        at = 1000;
                        server.execute(() -> spent(level, y));
                        teleport(server, inside);
                    } else if (at == 1060) {
                        dev.groundburst.content.command.WarRoomDisplayBlock.screenHook.accept(display);
                    } else if (at == 1110) {
                        shot(mc, "11_war_room_screen_after");
                        mc.gui.setScreen(null);
                        server.execute(() -> retaliation(level, y));
                        next();
                    } else if (at == 900) {
                        check("the command rocket came down within its course", false, DeadHandBlockEntity.State.values()[deadHand]);
                        server.execute(() -> retaliation(level, y));
                        next();
                    }
                }
                default -> {
                    if (at == 20) {
                        long wrong = CHECKS.stream().filter(c -> c.contains("WRONG")).count();
                        GroundBurst.LOGGER.info("War room demo: {} checks, {} wrong", CHECKS.size(), wrong);
                        mc.stop();
                    }
                }
            }
        }

        private static void next() {
            phase++;
            at = 0;
            mark = -1;
        }

        static DeadHandBlockEntity deadHandOf(ServerLevel level, int y) {
            return (DeadHandBlockEntity) level.getBlockEntity(new BlockPos(DEAD_HAND.getX(), y + DEAD_HAND.getY(), DEAD_HAND.getZ()));
        }

        static WarRoomDisplayBlockEntity displayOf(ServerLevel level, int y) {
            return (WarRoomDisplayBlockEntity) level.getBlockEntity(new BlockPos(DISPLAY.getX(), y + DISPLAY.getY(), DISPLAY.getZ()));
        }

        static SeismographBlockEntity seismographOf(ServerLevel level, int y) {
            return (SeismographBlockEntity) level.getBlockEntity(new BlockPos(SEISMOGRAPH.getX(), y + SEISMOGRAPH.getY(), SEISMOGRAPH.getZ()));
        }

        static LaunchSiloBlockEntity farOf(ServerLevel level, int y) {
            return (LaunchSiloBlockEntity) level.getBlockEntity(new BlockPos(FAR_SILO.getX(), y + FAR_SILO.getY(), FAR_SILO.getZ()));
        }

        /** The base's launchers by the bunker: the two silos and the complex. */
        static List<LaunchSiloBlockEntity> baseLaunchers(ServerLevel level, int y) {
            List<LaunchSiloBlockEntity> out = new ArrayList<>();
            for (BlockPos p : List.of(SILO_1, SILO_2, COMPLEX)) {
                if (level.getBlockEntity(new BlockPos(p.getX(), y + p.getY(), p.getZ())) instanceof LaunchSiloBlockEntity s) {
                    out.add(s);
                }
            }
            return out;
        }

        static void calm(ServerLevel level, int y) {
            WarRoomDisplayBlockEntity d = displayOf(level, y);
            check("the display formed, lit and showing the base", d.isFormed() && d.shownPowered() && d.shownNetwork() == baseNetwork,
                    d.isFormed() + "/" + d.shownPowered() + "/" + d.shownNetwork());
            check("the board at 5, NORMAL", d.condition() == 5, d.condition());
            DeadHandBlockEntity dh = deadHandOf(level, y);
            check("the Dead Hand watching, its rocket loaded", dh.state() == DeadHandBlockEntity.State.WATCHING && dh.rocket(), dh.state());
            LaunchNetwork.Network n = LaunchNetwork.of(level).network(baseNetwork);
            check("the base's network: 9 members (the display shows it, a member it is not)", n != null && n.members().size() == 9
                    && n.member(d.getBlockPos()) == null, n == null ? null : n.members().size());
            check("every seismograph powered", seismographOf(level, y).powered(), seismographOf(level, y).status());
            LaunchSiloBlockEntity far = farOf(level, y);
            check("the far silo formed, loaded and ready", far.isFormed() && far.readiness() == LaunchSiloBlockEntity.Readiness.READY, far.readiness());
        }

        /** The enemy's three launchers armed and launched from its network (as its station's LAUNCH does). */
        static void launchEnemy(ServerLevel level) {
            LaunchNetwork nets = LaunchNetwork.of(level);
            LaunchNetwork.Network e = nets.network(enemyNetwork);
            List<BlockPos> all = new ArrayList<>();
            for (LaunchNetwork.Member m : e.members()) {
                all.add(m.pos);
            }
            int armed = nets.arm(level, e.id, all, true);
            int fired = nets.launch(level, e.id, false, null);
            check("the enemy's salvo ordered", armed == 3 && fired == 3, armed + " armed, " + fired + " fired");
        }

        static void incoming(ServerLevel level, int y) {
            check("the enemy's missiles in the air", Flights.of(level).all().size() >= 3, Flights.of(level).all().size());
            WarRoomDisplayBlockEntity d = displayOf(level, y);
            check("the board at 2, INCOMING", d.condition() == 2, d.condition());
            Alarms.Alert a = Alarms.status(level, 0);
            check("the base's alarms: an attack", a != null && a.kind() == Alarms.Kind.ATTACK, a);
        }

        static void struck(ServerLevel level, int y) {
            List<SeismographBlockEntity.Record> r = seismographOf(level, y).records();
            check("the bunker's seismograph recorded the strikes", r.size() >= 1, r.size() + " records: " + r.stream()
                    .map(x -> String.format("M%.1f %d m %.0f deg", x.magnitude(), x.distance(), x.bearing())).toList());
            check("the tactical nuke among them, about M4.1", r.stream().anyMatch(x -> Math.abs(x.magnitude() - 4.06f) < 0.35f), r.size());
            LaunchNetwork.Network n = LaunchNetwork.of(level).network(baseNetwork);
            long fixed = n.blasts().stream().filter(LaunchNetwork.Blast::fixed).count();
            check("the network fixed the blasts", fixed >= 1, fixed + " of " + n.blasts().size());
            WarRoomDisplayBlockEntity d = displayOf(level, y);
            check("the board at 1, ATTACK", d.condition() == 1, d.condition());
            DeadHandBlockEntity dh = deadHandOf(level, y);
            check("the Dead Hand in alert", dh.state() == DeadHandBlockEntity.State.ALERT, dh.state());
            Alarms.Alert a = Alarms.status(level, 0);
            check("the base's alarms: a blast", a != null && a.kind() == Alarms.Kind.BLAST, a);
        }

        /** In the alert: every base launcher (the far silo too) readied to stand by for the rocket. */
        static void standingBy(ServerLevel level, int y) {
            DeadHandBlockEntity dh = deadHandOf(level, y);
            int n = LaunchNetwork.of(level).standingFor(baseNetwork, dh.getBlockPos());
            check("the base's four launchers stand by for the Dead Hand", n == 4, n);
        }

        /** Zero: the rocket away; the three launchers by the bunker lit at once; the far silo still waiting for the signal. */
        static void fired(ServerLevel level, int y) {
            DeadHandBlockEntity dh = deadHandOf(level, y);
            check("the Dead Hand fired: its rocket in the air", dh.state() == DeadHandBlockEntity.State.FIRING && !dh.rocket()
                    && dh.rocketFlight() != null, dh.state());
            var line = dh.log().stream().filter(e -> e.line() == DeadHandBlockEntity.Line.FIRED).findFirst();
            check("with the base's four launchers standing by", line.isPresent() && line.get().b() == 4, line.map(l -> l.b()).orElse(-1));
            long lit = baseLaunchers(level, y).stream().filter(s -> s.inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty()).count();
            check("the three by the bunker lit at zero, no countdown after it", lit == 3, lit + " of 3");
            LaunchSiloBlockEntity far = farOf(level, y);
            check("the far silo waiting at T-1 for the signal", far.standingBy() && far.countdown() == 1, far.countdown());
        }

        /** The far silo fired: the rocket was within its signal's reach of it. */
        static void farCheck(ServerLevel level, int y) {
            DeadHandBlockEntity dh = deadHandOf(level, y);
            Flights.Flight rocket = Flights.of(level).all().stream().filter(f -> f.id().equals(dh.rocketFlight())).findFirst().orElse(null);
            BlockPos far = farOf(level, y).getBlockPos();
            double d = rocket == null ? -1 : Math.hypot(rocket.position().x - far.getX() - 0.5, rocket.position().z - far.getZ() - 0.5);
            check("the far silo fired as the rocket came over (within its reach)", rocket != null
                    && d <= dev.groundburst.content.command.CommandRocket.REACH_TOP + 60, rocket == null ? "the rocket gone" : (int) d + " blocks off");
            long n = Flights.of(level).all().stream().filter(f -> f.start().z > -300 && !f.harmless()).count();
            check("all four of the base's missiles in the air", n >= 4, n);
        }

        static void spent(ServerLevel level, int y) {
            DeadHandBlockEntity dh = deadHandOf(level, y);
            var line = dh.log().stream().filter(e -> e.line() == DeadHandBlockEntity.Line.ROCKET_DOWN).findFirst();
            check("the rocket down harmless: all four had the order", dh.state() == DeadHandBlockEntity.State.SPENT && line.isPresent()
                    && line.get().b() == 4, line.map(l -> l.b()).orElse(-1));
        }

        static void retaliation(ServerLevel level, int y) {
            long n = Flights.of(level).all().stream().filter(f -> f.start().z > -300 && !f.harmless()).count();
            check("the far silo's missile still on its way (the base's by the bunker down)", n >= 1, n);
            WarRoomDisplayBlockEntity d = displayOf(level, y);
            check("the board at 1 still (the strike lately)", d.condition() == 1, d.condition());
        }
    }
}
