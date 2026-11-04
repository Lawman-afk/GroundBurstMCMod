package dev.groundburst.compat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.CruiseLauncherBlockEntity;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSite;
import dev.groundburst.content.launch.Launches;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.StructureSpawnerItem;
import dev.groundburst.content.ordnance.GBOrdnance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * This build's check of launchers on moving structures ({@code gradlew runCreateCheck -PwithCreate}; main's DESIGN.md
 * §17.16): in a flat creative world with Create, Sable and Create Aeronautics loaded, a Cruise Missile Launcher on a
 * Create mechanical bearing turned by a creative motor (its deck held together and carried), and another made into a
 * Sable ship and turned 30 degrees; each fires, and the log says where its missile lit and which way it headed against
 * where its box's tail and its boxes' heading are in the world at that moment ("Create check: ... OK" or "... WRONG").
 * Then a factory, an assembler yard, a blasted platform and a blasted ship, and last the ships under the slower weapons
 * and aimed at ({@link ShipCheck}). Pictures in run/screenshots/create_*.png.
 */
@EventBusSubscriber(modid = GroundBurst.MODID, value = Dist.CLIENT)
public final class CreateCheck {
    private static final boolean ON = Boolean.getBoolean("groundburst.createCheck");
    private static final String WORLD = "create_check";
    /** The bearing's launcher: controller (front left of its deck, facing north), bearing and motor under its third row. */
    private static final BlockPos BEARING_LAUNCHER = new BlockPos(2, -58, 21), BEARING = new BlockPos(2, -59, 23), MOTOR = new BlockPos(2, -60, 23);
    /** The ship's launcher on a stone platform; where the ship is set down, turned 30 degrees. */
    private static final BlockPos SHIP_LAUNCHER = new BlockPos(30, -59, 21);
    private static final Vec3 SHIP_AT = new Vec3(32, -57, 30);
    private static boolean started, stopped;
    private static int ticks = -1;
    /** The launcher a check is waiting on, and the flights there were before it fired. */
    private static @Nullable Supplier<CruiseLauncherBlockEntity> watched;
    private static String watchedName = "";
    private static final Set<UUID> before = new HashSet<>();
    private static int checks, passed;

    private CreateCheck() {}

    @SubscribeEvent
    static void clientTick(ClientTickEvent.Post event) {
        if (!ON) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.getOverlay() == null && mc.level == null && mc.screen != null) {
            started = true;
            GroundBurst.LOGGER.info("Create check: making the world (Create {}, Sable {})", ModList.get().isLoaded("create"),
                    ModList.get().isLoaded("sable"));
            createWorld(mc);
        }
        tick(mc);
    }

    /** After each server tick: a launcher standing in the world (a ship's) being watched may have lit a missile. */
    @SubscribeEvent
    static void serverTick(LevelTickEvent.Post event) {
        if (ON && watched != null && event.getLevel() instanceof ServerLevel level && level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                && watched.get() instanceof CruiseLauncherBlockEntity be && !be.carried()) {
            check(level, be);
        }
    }

    /** A carried launcher being watched, just after its carrier ticked it (the contraption as it was when it fired). */
    private static void afterCarriedTick(ServerLevel level, dev.groundburst.content.machine.MachineBlockEntity machine) {
        if (watched != null && watched.get() == machine && machine instanceof CruiseLauncherBlockEntity be) {
            check(level, be);
        }
    }

    /** Its missile lit: where and which way, against where its box's tail is in the world and the way its boxes point. */
    private static void check(ServerLevel level, CruiseLauncherBlockEntity be) {
        for (Flights.Flight f : Flights.of(level).all()) {
            if (before.add(f.id())) {
                Vec3 tail = be.inWorld(be.tailPoint(CruiseLauncherBlockEntity.SLOT_A));
                double heading = be.worldHeading();
                double off = f.position().distanceTo(tail);
                double turn = Math.abs(Math.atan2(Math.sin(f.heading() - heading), Math.cos(f.heading() - heading)));
                boolean ok = off < 0.05 && turn < 1e-3;
                checks++;
                passed += ok ? 1 : 0;
                GroundBurst.LOGGER.info("Create check: {} lit at {} (its box's tail in the world {}), heading {} deg (its boxes {} deg; built facing {} deg): {}",
                        watchedName, f.position(), tail, Math.round(Math.toDegrees(f.heading())), Math.round(Math.toDegrees(heading)),
                        Math.round(Math.toDegrees(be.heading())), ok ? "OK" : "WRONG");
                watched = null;
                return;
            }
        }
    }

    private static void createWorld(Minecraft mc) {
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

    /** The player put at {@code from}, looking at {@code at}. */
    static void look(Minecraft mc, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) -Math.toDegrees(Math.atan2(d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        command(mc, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", from.x, from.y, from.z, yaw, pitch));
    }

    /** A command run by the server itself (its answer in the log). */
    static void serverCommand(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    static void shot(Minecraft mc, String name) {
        mc.gui.getChat().clearMessages(false);
        Screenshot.grab(mc.gameDirectory, "create_" + name + ".png", mc.getMainRenderTarget(),
                message -> GroundBurst.LOGGER.info("Create check: {}", message.getString()));
    }

    static void onServer(Minecraft mc, java.util.function.Consumer<ServerLevel> job) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                try {
                    job.accept(server.overworld());
                } catch (RuntimeException e) {
                    GroundBurst.LOGGER.error("Create check: failed", e);
                }
            });
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
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
                look(mc, new Vec3(-9.5, -54, 23.5), new Vec3(2.5, -57, 23.5));
            }
            case 50 -> onServer(mc, level -> {
                if (ModList.get().isLoaded("create")) {
                    dev.groundburst.compat.create.CreateCompat.afterCarriedTick(CreateCheck::afterCarriedTick);
                }
                // The bearing's launcher, loaded and armed at a target 400 blocks east; then the motor and the bearing.
                CruiseLauncherBlockEntity be = launcher(level, BEARING_LAUNCHER);
                arm(level, be, new BlockPos(420, -60, 30));
                link(level, be);
                level.setBlockAndUpdate(BEARING, block("create:mechanical_bearing").setValue(BlockStateProperties.FACING, Direction.UP));
                level.setBlockAndUpdate(MOTOR, block("create:creative_motor").setValue(BlockStateProperties.FACING, Direction.UP));
            });
            case 140 -> onServer(mc, level -> {
                LaunchSite site = Launches.site(level, BEARING_LAUNCHER);
                boolean carried = site instanceof CruiseLauncherBlockEntity c && c.carried();
                checks++;
                passed += carried ? 1 : 0;
                GroundBurst.LOGGER.info("Create check: the bearing carries the launcher: {} ({}; where its controller is now {}): {}",
                        carried, site == null ? "none" : site.getClass().getSimpleName(), site == null ? "-" : site.worldBlockPos(),
                        carried ? "OK" : "WRONG");
            });
            case 150 -> shot(mc, "bearing_turned");
            case 155 -> onServer(mc, level -> fire(level, "the bearing's launcher", () ->
                    Launches.site(level, BEARING_LAUNCHER) instanceof CruiseLauncherBlockEntity c ? c : null));
            case 175 -> shot(mc, "bearing_boxes_up");
            case 205 -> shot(mc, "bearing_fired");
            // --- the Sable ship ---------------------------------------------------------------------------------------------
            case 260 -> {
                look(mc, new Vec3(12.5, -53, 27.5), new Vec3(31, -58, 27));
                onServer(mc, level -> {
                    for (int x = -1; x <= 2; x++) {
                        for (int z = -1; z <= 6; z++) {
                            level.setBlockAndUpdate(SHIP_LAUNCHER.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState());
                        }
                    }
                    CruiseLauncherBlockEntity be = launcher(level, SHIP_LAUNCHER);
                    arm(level, be, new BlockPos(420, -60, 40));
                    link(level, be);
                });
            }
            case 280 -> onServer(mc, level -> {
                MinecraftServer server = level.getServer();
                serverCommand(server, "sable assemble area " + (SHIP_LAUNCHER.getX() - 1) + " " + (SHIP_LAUNCHER.getY() - 1) + " " + (SHIP_LAUNCHER.getZ() - 1)
                        + " " + (SHIP_LAUNCHER.getX() + 2) + " " + (SHIP_LAUNCHER.getY() + 6) + " " + (SHIP_LAUNCHER.getZ() + 6));
            });
            case 290 -> onServer(mc, level -> serverCommand(level.getServer(),
                    "sable teleport @l " + SHIP_AT.x + " " + SHIP_AT.y + " " + SHIP_AT.z + " 30 0"));
            case 340 -> onServer(mc, level -> {
                CruiseLauncherBlockEntity be = shipLauncher(level);
                boolean aboard = be != null && !be.getBlockPos().equals(SHIP_LAUNCHER);
                checks++;
                passed += aboard ? 1 : 0;
                GroundBurst.LOGGER.info("Create check: the launcher is aboard the ship: {} (its block {}, where it is in the world {}, armed {}): {}",
                        aboard, be == null ? "-" : be.getBlockPos(), be == null ? "-" : be.worldBlockPos(), be != null && be.armed(), aboard ? "OK" : "WRONG");
                linked(level, "the ship's launcher", be == null ? null : be.getBlockPos());
            });
            case 350 -> shot(mc, "ship");
            case 355 -> onServer(mc, level -> fire(level, "the ship's launcher", () -> shipLauncher(level)));
            case 375 -> shot(mc, "ship_boxes_up");
            case 405 -> shot(mc, "ship_fired");
            case 410 -> look(mc, new Vec3(-9.5, -54, 23.5), new Vec3(2.5, -57, 23.5));
            case 520 -> onServer(mc, level -> {
                CruiseLauncherBlockEntity down = null;
                for (BlockPos p : BlockPos.betweenClosed(BEARING.offset(-7, 0, -7), BEARING.offset(7, 2, 7))) {
                    if (level.getBlockEntity(p) instanceof CruiseLauncherBlockEntity c && !c.carried()) {
                        down = c;
                    }
                }
                boolean ok = down != null && down.isFormed() && Launches.site(level, down.getBlockPos()) == down
                        && !down.getBlockPos().equals(BEARING_LAUNCHER) && down.controllerFacing().getAxis() == Direction.Axis.X;
                checks++;
                passed += ok ? 1 : 0;
                GroundBurst.LOGGER.info("Create check: set down by the bearing: {} (at {}, facing {}, formed {}, found by the computers {}, missiles left {}, power {} FE): {}",
                        down != null, down == null ? "-" : down.getBlockPos(), down == null ? "-" : down.controllerFacing(),
                        down != null && down.isFormed(), down != null && Launches.site(level, down.getBlockPos()) == down,
                        down == null ? "-" : down.loaded(), down == null ? "-" : down.energy().stored(), ok ? "OK" : "WRONG");
                linked(level, "the set-down launcher", down == null ? null : down.getBlockPos());
            });
            case 545 -> shot(mc, "bearing_set_down");
            // --- a factory on a second bearing: power, pipes, menus, nothing dropped -------------------------------------------
            case 560 -> onServer(mc, CreateCheck::buildFactory);
            case 561 -> onServer(mc, level -> {
                level.setBlockAndUpdate(BEARING_2, block("create:mechanical_bearing").setValue(BlockStateProperties.FACING, Direction.UP));
                level.setBlockAndUpdate(BEARING_2.below(), block("create:creative_motor").setValue(BlockStateProperties.FACING, Direction.UP));
            });
            case 562 -> look(mc, new Vec3(-31.5, -53, 23.5), new Vec3(-18.5, -56, 23.5));
            case 580 -> onServer(mc, level -> {
                int dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, FACTORY_AREA).size();
                boolean carried = dev.groundburst.content.machine.Carried.at(level, FACTORY_LAUNCHER) != null
                        && dev.groundburst.content.machine.Carried.at(level, FACTORY_GENERATOR) != null
                        && dev.groundburst.content.machine.Carried.at(level, FACTORY_COMPUTER) != null;
                check(carried && dropped == 0, "the bearing carries the launcher, the generator and the computer, nothing dropped (" + dropped
                        + " items on the ground)");
            });
            case 690 -> shot(mc, "factory_aboard");
            case 700 -> onServer(mc, level -> {
                var launcher = dev.groundburst.content.machine.Carried.at(level, FACTORY_LAUNCHER) instanceof CruiseLauncherBlockEntity c ? c : null;
                var computer = dev.groundburst.content.machine.Carried.at(level, FACTORY_COMPUTER) instanceof dev.groundburst.content.launch.LaunchComputerBlockEntity c ? c : null;
                check(launcher != null && launcher.loaded() == 2, "the pipe aboard brought a missile from the chest aboard into the Item Port ("
                        + (launcher == null ? "-" : launcher.loaded()) + " loaded)");
                var separator = dev.groundburst.content.machine.Carried.at(level, FACTORY_SEPARATOR) instanceof dev.groundburst.content.machine.MachineBlockEntity m ? m : null;
                check(separator != null && (separator.tank(0).amount() > 0 || separator.tank(1).amount() > 0),
                        "the fluid pipe aboard brought water from the tank aboard into the separator (" + (separator == null ? "-"
                                : separator.tank(0).amount() + " mB water, " + separator.tank(1).amount() + " mB out") + ")");
                check(launcher != null && launcher.energy().stored() > 0 && computer != null && computer.powered(),
                        "the generator aboard powers the launcher (" + (launcher == null ? "-" : launcher.energy().stored()) + " FE) and the computer ("
                        + (computer != null && computer.powered()) + ")");
            });
            case 705 -> {
                command(mc, "tp @s -23.5 -60 23.5");             // on the grass, under the turning blocks (they would shove a player off)
                onServer(mc, level -> {
                    var player = level.getServer().getPlayerList().getPlayers().getFirst();
                    check(dev.groundburst.compat.create.CheckScenes.click(player, FACTORY_LAUNCHER), "right-clicking the carried launcher opens its menu");
                });
            }
            case 730 -> {
                check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, "its menu is open on the client ("
                        + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) + ")");
                shot(mc, "factory_menu");
            }
            case 735 -> {
                if (mc.player != null) {
                    mc.player.closeContainer();
                }
                check(dev.groundburst.compat.create.CheckScenes.click(mc.player, FACTORY_COMPUTER), "right-clicking the carried Launch Computer (on the client)");
            }
            case 770 -> {
                check(mc.screen instanceof dev.groundburst.client.screen.LaunchComputerScreen, "its screen is open ("
                        + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) + ")");
                shot(mc, "factory_computer");
            }
            case 775 -> {
                mc.setScreen(null);
                onServer(mc, level -> level.setBlockAndUpdate(BEARING_2.below(), Blocks.AIR.defaultBlockState()));   // the bearing stops
            }
            case 815 -> onServer(mc, level -> {
                int dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, FACTORY_AREA).size();
                CruiseLauncherBlockEntity launcher = null;
                boolean pipePulls = false;
                for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(FACTORY_AREA.minX, FACTORY_AREA.minY, FACTORY_AREA.minZ),
                        BlockPos.containing(FACTORY_AREA.maxX, FACTORY_AREA.maxY, FACTORY_AREA.maxZ))) {
                    if (level.getBlockEntity(p) instanceof CruiseLauncherBlockEntity c && !c.carried()) {
                        launcher = c;
                    }
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof dev.groundburst.content.pipe.ItemPipeBlock) {
                        for (Direction d : Direction.values()) {
                            if (dev.groundburst.content.pipe.PipeBlock.mode(s, d) == dev.groundburst.content.pipe.PipeBlock.Mode.PULL
                                    && level.getBlockState(p.relative(d)).is(Blocks.CHEST)) {
                                pipePulls = true;
                            }
                        }
                    }
                }
                check(dropped == 0, "set down, nothing dropped (" + dropped + " items on the ground)");
                check(pipePulls, "set down, the pipe still pulls from the chest (its wrench setting kept, turned with it)");
                check(launcher != null && launcher.isFormed() && launcher.loaded() == 2, "set down, the launcher formed with its two missiles ("
                        + (launcher == null ? "-" : launcher.isFormed() + ", " + launcher.loaded()) + ")");
            });
            case 820 -> shot(mc, "factory_set_down");
            // --- a yard on a third bearing: a Medium Missile Assembler partway through a missile, a loaded Launch Silo -------
            case 840 -> onServer(mc, CreateCheck::buildYard);
            case 841 -> onServer(mc, level -> {
                level.setBlockAndUpdate(YARD_ASSEMBLER.below(), block("create:mechanical_bearing").setValue(BlockStateProperties.FACING, Direction.UP));
                level.setBlockAndUpdate(YARD_ASSEMBLER.below(2), block("create:creative_motor").setValue(BlockStateProperties.FACING, Direction.UP));
            });
            case 842 -> look(mc, new Vec3(-38.5, -50, -9.5), new Vec3(-18.5, -55, -9.5));
            case 860 -> onServer(mc, level -> {
                int dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, YARD_AREA).size();
                var assembler = yardAssembler(level);
                yardProgress = assembler == null ? Integer.MAX_VALUE : assembler.progress();
                check(assembler != null && yardSilo(level) != null && dropped == 0, "the bearing carries the assembler mid-build and the loaded silo, nothing dropped ("
                        + dropped + " items on the ground)");
            });
            case 895 -> shot(mc, "yard_aboard");
            case 900 -> onServer(mc, level -> {
                var assembler = yardAssembler(level);
                var silo = yardSilo(level);
                check(assembler != null && assembler.assembling() && assembler.progress() > yardProgress, "the assembler goes on building aboard ("
                        + (assembler == null ? "-" : yardProgress + " -> " + assembler.progress() + " ticks of work, " + assembler.status()) + ")");
                check(silo != null && !silo.inventory().getItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty(),
                        "the silo aboard holds its missile");
            });
            case 903 -> command(mc, "tp @s -19.5 -57 -9.5");
            case 905 -> onServer(mc, level -> {
                var player = level.getServer().getPlayerList().getPlayers().getFirst();
                check(dev.groundburst.compat.create.CheckScenes.click(player, yardSiloAt.east()), "right-clicking the carried silo's pad opens the silo's menu");
            });
            case 925 -> {
                check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, "its menu is open on the client ("
                        + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) + ")");
                shot(mc, "yard_silo_menu");
            }
            case 927 -> {
                mc.player.closeContainer();
                onServer(mc, level -> {
                    var player = level.getServer().getPlayerList().getPlayers().getFirst();
                    check(dev.groundburst.compat.create.CheckScenes.click(player, YARD_ASSEMBLER), "right-clicking the carried assembler opens its menu");
                });
            }
            case 947 -> {
                check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, "its menu is open on the client ("
                        + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) + ")");
                shot(mc, "yard_assembler_menu");
            }
            case 950 -> {
                mc.player.closeContainer();
                onServer(mc, level -> {
                    var assembler = yardAssembler(level);
                    if (assembler != null) {
                        assembler.skipToEndForTest();
                    }
                });
            }
            case 960 -> onServer(mc, level -> {
                var assembler = yardAssembler(level);
                check(assembler != null && !assembler.assembling() && builtOne(assembler), "the assembler finished its missile aboard ("
                        + (assembler == null ? "-" : contents(assembler)) + ")");
            });
            case 962 -> look(mc, new Vec3(-38.5, -50, -9.5), new Vec3(-18.5, -55, -9.5));
            case 965 -> onServer(mc, level -> level.setBlockAndUpdate(YARD_ASSEMBLER.below(2), Blocks.AIR.defaultBlockState()));   // the bearing stops
            case 1010 -> onServer(mc, level -> {
                int dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, YARD_AREA).size();
                dev.groundburst.content.assembler.MissileAssemblerBlockEntity assembler = null;
                dev.groundburst.content.launch.LaunchSiloBlockEntity silo = null;
                for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(YARD_AREA.minX, YARD_AREA.minY, YARD_AREA.minZ),
                        BlockPos.containing(YARD_AREA.maxX, YARD_AREA.maxY, YARD_AREA.maxZ))) {
                    if (level.getBlockEntity(p) instanceof dev.groundburst.content.assembler.MissileAssemblerBlockEntity a && !a.carried()) {
                        assembler = a;
                    } else if (level.getBlockEntity(p) instanceof dev.groundburst.content.launch.LaunchSiloBlockEntity s && !s.carried()) {
                        silo = s;
                    }
                }
                if (assembler != null) {
                    assembler.revalidate();                        // (as its own timer would: is the structure whole where it stands?)
                }
                if (silo != null) {
                    silo.revalidate();
                }
                check(dropped == 0, "set down, nothing dropped (" + dropped + " items on the ground)");
                check(assembler != null && assembler.isFormed() && builtOne(assembler), "set down, the assembler formed with just its one missile ("
                        + (assembler == null ? "-" : assembler.isFormed() + ", " + contents(assembler)) + ")");
                check(silo != null && silo.isFormed() && !silo.inventory().getItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty(),
                        "set down, the silo formed with its missile (" + (silo == null ? "-" : silo.isFormed() + ", "
                                + silo.inventory().getItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE)) + ")");
            });
            case 1015 -> shot(mc, "yard_set_down");
            // --- a wreck: a blast and a shell on a turning platform (a Create contraption) ---------------------------------
            case 1030 -> onServer(mc, CreateCheck::buildWreck);
            case 1031 -> onServer(mc, level -> {
                level.setBlockAndUpdate(WRECK_HUB.below(), block("create:mechanical_bearing").setValue(BlockStateProperties.FACING, Direction.UP));
                level.setBlockAndUpdate(WRECK_HUB.below(2), block("create:creative_motor").setValue(BlockStateProperties.FACING, Direction.UP));
            });
            case 1032 -> look(mc, new Vec3(-34.5, -52, -39.5), new Vec3(-19.5, -57, -39.5));
            case 1050 -> onServer(mc, level -> {
                var platform = dev.groundburst.compat.create.CheckScenes.carrying(level, WRECK_EDGE);
                wreckEntity = platform == null ? -1 : platform.getId();
                wreckBlocks = platform == null ? -1 : dev.groundburst.compat.create.CheckScenes.blocks(platform);
                check(platform != null && wreckBlocks == 51 && dev.groundburst.content.machine.Carried.at(level, WRECK_GENERATOR) != null,
                        "the bearing turns the platform with its chest and generator (" + wreckBlocks + " blocks)");
            });
            case 1060 -> onServer(mc, level -> dev.groundburst.blast.Blasts.detonate(level, WRECK_BURST, dev.groundburst.blast.Blasts.profile("test/small"), null));
            case 1062 -> onServer(mc, level -> {
                var platform = dev.groundburst.compat.create.CheckScenes.byId(level, wreckEntity);
                int now = platform == null ? -1 : dev.groundburst.compat.create.CheckScenes.blocks(platform);
                boolean gone = platform != null && !dev.groundburst.compat.create.CheckScenes.holds(platform, WRECK_CHEST)
                        && !dev.groundburst.compat.create.CheckScenes.holds(platform, WRECK_GENERATOR)
                        && !dev.groundburst.compat.create.CheckScenes.holds(platform, WRECK_HUB);
                check(gone && now == wreckBlocks - 3 && dev.groundburst.compat.create.CheckScenes.holds(platform, WRECK_EDGE),
                        "a blast over the hub broke the chest, the generator and the hub out of the turning platform, its rim kept ("
                                + wreckBlocks + " -> " + now + " blocks)");
                int items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, WRECK_AREA).size();
                check(items == 0, "nothing spilled: the chest's diamonds went with it (" + items + " items on the ground)");
                check(dev.groundburst.content.machine.Carried.at(level, WRECK_GENERATOR) == null, "the generator broken aboard is no longer carried");
                wreckBlocks = now;
            });
            case 1065 -> {
                int seen = dev.groundburst.compat.create.CheckScenes.clientBlocks(wreckEntity);
                check(seen == wreckBlocks, "players see the platform without them (the client's copy: " + seen + " blocks)");
            }
            case 1070 -> shot(mc, "wreck_blasted");
            case 1075 -> onServer(mc, level -> dev.groundburst.content.artillery.ArtilleryShell.launch(level, new Vec3(-32.5, -57.2, -39.5),
                    new Vec3(2.5, 0, 0), dev.groundburst.content.artillery.Calibre.MORTAR, dev.groundburst.content.artillery.ShellType.HE, null));
            case 1086 -> onServer(mc, level -> {
                var platform = dev.groundburst.compat.create.CheckScenes.byId(level, wreckEntity);
                int now = platform == null ? -1 : dev.groundburst.compat.create.CheckScenes.blocks(platform);
                boolean flying = !level.getEntitiesOfClass(dev.groundburst.content.artillery.ArtilleryShell.class, WRECK_AREA.inflate(16)).isEmpty();
                check(platform != null && now < wreckBlocks && !flying, "a mortar shell fired at the platform's rim burst on it (" + wreckBlocks + " -> "
                        + now + " blocks; shell still flying " + flying + ")");
                wreckBlocks = now;
            });
            case 1088 -> onServer(mc, level -> level.setBlockAndUpdate(WRECK_HUB.below(2), Blocks.AIR.defaultBlockState()));   // the bearing stops
            case 1130 -> onServer(mc, level -> {
                int items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, WRECK_AREA).size();
                int placed = 0;
                for (BlockPos p : BlockPos.betweenClosed(WRECK_HUB.offset(-5, 0, -5), WRECK_HUB.offset(5, 2, 5))) {
                    placed += level.getBlockState(p).isAir() ? 0 : 1;
                }
                check(items == 0 && placed == wreckBlocks, "set down with just the blocks it had left, none where blocks were broken ("
                        + placed + " of " + wreckBlocks + "; " + items + " items on the ground)");
            });
            case 1135 -> shot(mc, "wreck_set_down");
            // --- a ship hit: a blast beside a Sable ship, then a shell into it -----------------------------------------------
            case 1140 -> onServer(mc, level -> {
                for (BlockPos p : BlockPos.betweenClosed(SHIP2_MIN, SHIP2_MAX)) {
                    level.setBlockAndUpdate(p, Blocks.OAK_PLANKS.defaultBlockState());
                }
            });
            case 1145 -> onServer(mc, level -> serverCommand(level.getServer(), "sable assemble area " + SHIP2_MIN.getX() + " " + SHIP2_MIN.getY() + " "
                    + SHIP2_MIN.getZ() + " " + SHIP2_MAX.getX() + " " + SHIP2_MAX.getY() + " " + SHIP2_MAX.getZ()));
            case 1150 -> look(mc, new Vec3(48.5, -55, 27.5), new Vec3(62, -59.5, 20));
            case 1165 -> onServer(mc, level -> {
                ship2 = dev.groundburst.compat.sable.SableScenes.shipIn(level, new net.minecraft.world.phys.AABB(SHIP2_MIN).minmax(
                        new net.minecraft.world.phys.AABB(SHIP2_MAX)).inflate(1));
                ship2Blocks = ship2 == null ? -1 : dev.groundburst.compat.sable.SableScenes.blocks(level, ship2);
                ship2At = ship2 == null ? Vec3.ZERO : dev.groundburst.compat.sable.SableScenes.position(ship2);
                GroundBurst.LOGGER.info("Create check: the second ship: {}, {} blocks", ship2 == null ? "none" : dev.groundburst.compat.sable.SableScenes.describe(ship2),
                        ship2Blocks);
                dev.groundburst.blast.Blasts.detonate(level, SHIP2_BURST, dev.groundburst.blast.Blasts.profile("mortar_shell_he"), null);
            });
            case 1167 -> onServer(mc, level -> {
                int now = ship2 == null ? -1 : dev.groundburst.compat.sable.SableScenes.blocks(level, ship2);
                check(ship2 != null && ship2Blocks == 50 && now > 0 && now < ship2Blocks, "a shell's blast beside the ship broke its near side (" + ship2Blocks
                        + " -> " + now + " blocks)");
                ship2Blocks = now;
            });
            case 1185 -> onServer(mc, level -> {
                Vec3 at = ship2 == null ? Vec3.ZERO : dev.groundburst.compat.sable.SableScenes.position(ship2);
                check(ship2 != null && at.x - ship2At.x > 0.2, "the shock shoved the ship east, away from the burst (moved " + at.subtract(ship2At) + ")");
            });
            case 1186 -> shot(mc, "ship_blasted");
            case 1190 -> onServer(mc, level -> {
                if (ship2 != null) {
                    net.minecraft.world.phys.AABB box = dev.groundburst.compat.sable.SableScenes.box(ship2);
                    Vec3 c = box.getCenter();
                    dev.groundburst.content.artillery.ArtilleryShell.launch(level, new Vec3(box.minX - 10, c.y + 0.3, c.z), new Vec3(2.5, 0, 0),
                            dev.groundburst.content.artillery.Calibre.MORTAR, dev.groundburst.content.artillery.ShellType.HE, null);
                }
            });
            case 1205 -> onServer(mc, level -> {
                int now = ship2 == null || dev.groundburst.compat.sable.SableScenes.gone(ship2) ? 0 : dev.groundburst.compat.sable.SableScenes.blocks(level, ship2);
                check(ship2 != null && now < ship2Blocks, "a mortar shell fired at the ship burst on it (" + ship2Blocks + " -> " + now + " blocks)");
            });
            case 1206 -> shot(mc, "ship_shelled");
            case ShipCheck.END -> {
                GroundBurst.LOGGER.info("Create check: done, {} of {} checks OK", passed, checks);
                mc.stop();
            }
            default -> {
                if (ticks >= ShipCheck.START && ticks < ShipCheck.END) {
                    ShipCheck.tick(mc, ticks);                   // ships under the slower weapons, and aimed at
                }
                // The bearing stops once its launcher has turned a quarter (its boxes pointing east): Create sets its blocks
                // down turned a quarter, the launcher somewhere new.
                if (ticks > 415 && ticks < 505 && !stopped) {
                    onServer(mc, level -> {
                        if (!stopped && Launches.site(level, BEARING_LAUNCHER) instanceof CruiseLauncherBlockEntity c && c.carried()
                                && Math.abs(Math.atan2(Math.sin(c.worldHeading() - Math.PI / 2), Math.cos(c.worldHeading() - Math.PI / 2))) < 0.1) {
                            stopped = true;
                            GroundBurst.LOGGER.info("Create check: the bearing stops with the launcher's boxes heading {} deg",
                                    Math.round(Math.toDegrees(c.worldHeading())));
                            level.setBlockAndUpdate(MOTOR, Blocks.AIR.defaultBlockState());   // (stopping lets the carried copy go)
                        }
                    });
                }
            }
        }
    }

    /** The factory scene: a second bearing (its motor under it), and on it the things it carries. */
    private static final BlockPos BEARING_2 = new BlockPos(-20, -59, 23), FACTORY_LAUNCHER = new BlockPos(-20, -58, 21),
            FACTORY_PIPE = new BlockPos(-18, -58, 22), FACTORY_CHEST = new BlockPos(-17, -58, 22), FACTORY_GENERATOR = new BlockPos(-18, -58, 24),
            FACTORY_COMPUTER = new BlockPos(-18, -58, 25), FACTORY_SEPARATOR = new BlockPos(-17, -58, 24),
            FACTORY_FLUID_PIPE = new BlockPos(-17, -58, 23), FACTORY_TANK = new BlockPos(-16, -58, 23);
    private static final net.minecraft.world.phys.AABB FACTORY_AREA = new net.minecraft.world.phys.AABB(-28, -62, 13, -9, -45, 34);

    /** The wreck scene: a 7x7 stone platform on a fourth bearing (under its hub), a chest of diamonds and a generator on it. */
    private static final BlockPos WRECK_HUB = new BlockPos(-20, -58, -40), WRECK_CHEST = new BlockPos(-19, -57, -40),
            WRECK_GENERATOR = new BlockPos(-21, -57, -40), WRECK_EDGE = new BlockPos(-23, -58, -40);
    /** A small blast three blocks over the hub: it reaches the chest, the generator and the hub, not the rim. */
    private static final Vec3 WRECK_BURST = new Vec3(-19.5, -54.5, -39.5);
    private static final net.minecraft.world.phys.AABB WRECK_AREA = new net.minecraft.world.phys.AABB(-30, -62, -50, -10, -45, -30);
    private static int wreckEntity = -1, wreckBlocks;
    /** The second ship: a 5x2x5 block of planks made a ship, and a shell's burst a block and a half off its west side. */
    private static final BlockPos SHIP2_MIN = new BlockPos(60, -60, 18), SHIP2_MAX = new BlockPos(64, -59, 22);
    private static final Vec3 SHIP2_BURST = new Vec3(58.5, -59.0, 20.5);
    private static @Nullable Object ship2;
    private static int ship2Blocks;
    private static Vec3 ship2At = Vec3.ZERO;

    /** The wreck's platform, glued: the hub over the bearing, a chest of ten diamonds east of it and a generator west. */
    private static void buildWreck(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(WRECK_HUB.offset(-3, 0, -3), WRECK_HUB.offset(3, 0, 3))) {
            level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(WRECK_CHEST, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(WRECK_CHEST) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 10));
        }
        level.setBlockAndUpdate(WRECK_GENERATOR, block("groundburst:combustion_generator"));
        if (level.getBlockEntity(WRECK_GENERATOR) instanceof dev.groundburst.content.machine.MachineBlockEntity generator) {
            generator.inventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.COAL, 4));
        }
        dev.groundburst.compat.create.CheckScenes.glue(level, WRECK_HUB.offset(-3, 0, -3), WRECK_HUB.offset(3, 1, 3));
    }

    /** The yard scene: a third bearing under the assembler's controller (its motor under that), a Launch Silo beside it. */
    private static final BlockPos YARD_ASSEMBLER = new BlockPos(-20, -58, -10);
    private static final net.minecraft.world.phys.AABB YARD_AREA = new net.minecraft.world.phys.AABB(-36, -62, -26, -4, -45, 6);
    private static BlockPos yardSiloAt = BlockPos.ZERO;
    private static int yardProgress;

    /**
     * A Medium Missile Assembler (facing north, its controller over the bearing) holding a medium missile's parts and full
     * power, its build started; a Launch Silo holding a medium missile, its pad against the deck; all glued together.
     */
    private static void buildYard(ServerLevel level) {
        List<MultiblockDefinition.Cell> cells = new java.util.ArrayList<>(build(level, dev.groundburst.content.assembler.GBAssemblers.MEDIUM, YARD_ASSEMBLER));
        int east = cells.stream().mapToInt(c -> c.pos().getX()).max().orElseThrow();
        int west = StructureSpawnerItem.buildOrder(GBLaunch.SILO, YARD_ASSEMBLER, Direction.NORTH).stream().mapToInt(c -> c.pos().getX()).min().orElseThrow();
        yardSiloAt = YARD_ASSEMBLER.east(east + 1 - west);
        cells.addAll(build(level, GBLaunch.SILO, yardSiloAt));

        var assembler = (dev.groundburst.content.assembler.MissileAssemblerBlockEntity) level.getBlockEntity(YARD_ASSEMBLER);
        assembler.revalidate();
        assembler.energy().generate(dev.groundburst.content.assembler.AssemblerClass.MEDIUM.capacity);
        var inv = assembler.inventory();
        inv.setItem(dev.groundburst.content.assembler.MissileAssemblerBlockEntity.SLOT_WARHEAD, new ItemStack(GBOrdnance.GAS_WARHEAD.item().get()));
        inv.setItem(dev.groundburst.content.assembler.MissileAssemblerBlockEntity.SLOT_BODY,
                new ItemStack(dev.groundburst.content.assembly.GBAssembly.BODIES.get(SizeClass.M.ordinal()).get()));
        inv.setItem(dev.groundburst.content.assembler.MissileAssemblerBlockEntity.SLOT_MOTOR,
                new ItemStack(dev.groundburst.content.assembly.GBAssembly.MOTORS.get(MissileAssembly.rocketsFor(SizeClass.M).getLast().ordinal()).get()));
        inv.setItem(dev.groundburst.content.assembler.MissileAssemblerBlockEntity.SLOT_GUIDANCE,
                new ItemStack(dev.groundburst.content.assembly.GBAssembly.GPS_GUIDANCE.get()));
        boolean started = assembler.startAssembly();

        var silo = (dev.groundburst.content.launch.LaunchSiloBlockEntity) level.getBlockEntity(yardSiloAt);
        silo.revalidate();
        silo.inventory().setItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE, MissileAssembly.missile(new MissileSpec(
                new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.M, MissileParts.Motor.SRM_II, MissileParts.Guidance.GPS, false)));
        GroundBurst.LOGGER.info("Create check: the yard's assembler formed {}, building {} ({}); its silo at {} formed {}", assembler.isFormed(),
                started, assembler.problem(), yardSiloAt, silo.isFormed());

        BlockPos lo = cells.getFirst().pos(), hi = lo;
        for (MultiblockDefinition.Cell c : cells) {
            if (c.key() != '.') {
                lo = BlockPos.min(lo, c.pos());
                hi = BlockPos.max(hi, c.pos());
            }
        }
        dev.groundburst.compat.create.CheckScenes.glue(level, lo, hi);
    }

    /** The yard's assembler and silo while the bearing carries them. */
    private static dev.groundburst.content.assembler.@Nullable MissileAssemblerBlockEntity yardAssembler(ServerLevel level) {
        return dev.groundburst.content.machine.Carried.at(level, YARD_ASSEMBLER) instanceof dev.groundburst.content.assembler.MissileAssemblerBlockEntity a ? a : null;
    }

    private static dev.groundburst.content.launch.@Nullable LaunchSiloBlockEntity yardSilo(ServerLevel level) {
        return dev.groundburst.content.machine.Carried.at(level, yardSiloAt) instanceof dev.groundburst.content.launch.LaunchSiloBlockEntity s ? s : null;
    }

    /** Its parts made into one missile: the missile out, every part slot empty (nothing kept twice). */
    private static boolean builtOne(dev.groundburst.content.assembler.MissileAssemblerBlockEntity a) {
        ItemStack out = a.inventory().getItem(dev.groundburst.content.assembler.MissileAssemblerBlockEntity.SLOT_OUTPUT);
        for (int i = dev.groundburst.content.assembler.MissileAssemblerBlockEntity.SLOT_WARHEAD;
                i < dev.groundburst.content.assembler.MissileAssemblerBlockEntity.SLOT_OUTPUT; i++) {
            if (!a.inventory().getItem(i).isEmpty()) {
                return false;
            }
        }
        return a.isOwnMissile(out) && out.getCount() == 1;
    }

    private static String contents(dev.groundburst.content.assembler.MissileAssemblerBlockEntity a) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < a.inventory().getContainerSize(); i++) {
            if (!a.inventory().getItem(i).isEmpty()) {
                s.append(s.isEmpty() ? "" : ", ").append(i).append(": ").append(a.inventory().getItem(i));
            }
        }
        return s.isEmpty() ? "empty" : s.toString();
    }

    /** A structure built whole, facing north, its controller at {@code c}; its cells. */
    private static List<MultiblockDefinition.Cell> build(ServerLevel level, MultiblockDefinition def, BlockPos c) {
        List<MultiblockDefinition.Cell> cells = StructureSpawnerItem.buildOrder(def, c, Direction.NORTH);
        for (MultiblockDefinition.Cell cell : cells) {
            BlockState state = cell.key() == '.' ? Blocks.AIR.defaultBlockState() : def.legend().get(cell.key()).preview().get();
            if (cell.key() == MultiblockDefinition.CONTROLLER) {
                state = state.setValue(MachineBlock.FACING, Direction.NORTH);
            }
            level.setBlockAndUpdate(cell.pos(), state);
        }
        return cells;
    }

    static void check(boolean ok, String what) {
        checks++;
        passed += ok ? 1 : 0;
        GroundBurst.LOGGER.info("Create check: {}: {}", what, ok ? "OK" : "WRONG");
    }

    /**
     * A Cruise Missile Launcher holding one missile and no power; a chest of two more beside its Item Port and a pipe
     * pulling from the chest into the port; a Combustion Generator with coal; a Launch Computer; all glued together.
     */
    private static void buildFactory(ServerLevel level) {
        CruiseLauncherBlockEntity launcher = launcher(level, FACTORY_LAUNCHER);
        launcher.inventory().setItem(CruiseLauncherBlockEntity.SLOT_A, cruise());
        level.setBlockAndUpdate(FACTORY_CHEST, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(FACTORY_CHEST) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(0, cruise());
            chest.setItem(1, cruise());
        }
        level.setBlockAndUpdate(FACTORY_PIPE, block("groundburst:item_pipe"));
        BlockState pipe = level.getBlockState(FACTORY_PIPE).setValue(dev.groundburst.content.pipe.PipeBlock.SIDES.get(Direction.EAST),
                dev.groundburst.content.pipe.PipeBlock.Mode.PULL);
        level.setBlockAndUpdate(FACTORY_PIPE, pipe);
        level.setBlockAndUpdate(FACTORY_GENERATOR, block("groundburst:combustion_generator"));
        if (level.getBlockEntity(FACTORY_GENERATOR) instanceof dev.groundburst.content.machine.MachineBlockEntity generator) {
            generator.inventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.COAL, 8));
        }
        MultiblockDefinition def = GBLaunch.COMPUTER;
        for (MultiblockDefinition.Cell cell : StructureSpawnerItem.buildOrder(def, FACTORY_COMPUTER, Direction.NORTH)) {
            BlockState state = cell.key() == '.' ? Blocks.AIR.defaultBlockState() : def.legend().get(cell.key()).preview().get();
            if (cell.key() == MultiblockDefinition.CONTROLLER) {
                state = state.setValue(MachineBlock.FACING, Direction.NORTH);
            }
            level.setBlockAndUpdate(cell.pos(), state);
        }
        if (level.getBlockEntity(FACTORY_COMPUTER) instanceof dev.groundburst.content.multiblock.MultiblockController c) {
            c.revalidate();
            GroundBurst.LOGGER.info("Create check: the factory's computer formed: {}", c.isFormed());
        }
        GroundBurst.LOGGER.info("Create check: the factory's pipe: {}", level.getBlockState(FACTORY_PIPE));
        // Fluids: a Create tank of water, a fluid pipe pulling from it into an Electrolytic Separator.
        level.setBlockAndUpdate(FACTORY_TANK, block("create:fluid_tank"));
        var tank = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, FACTORY_TANK, null);
        int filled = tank == null ? 0 : tank.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 8000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        level.setBlockAndUpdate(FACTORY_SEPARATOR, block("groundburst:electrolytic_separator"));
        level.setBlockAndUpdate(FACTORY_FLUID_PIPE, block("groundburst:fluid_pipe"));
        BlockState fluidPipe = level.getBlockState(FACTORY_FLUID_PIPE).setValue(dev.groundburst.content.pipe.PipeBlock.SIDES.get(Direction.EAST),
                dev.groundburst.content.pipe.PipeBlock.Mode.PULL);
        level.setBlockAndUpdate(FACTORY_FLUID_PIPE, fluidPipe);
        GroundBurst.LOGGER.info("Create check: the factory's tank holds {} mB; its fluid pipe: {}", filled, level.getBlockState(FACTORY_FLUID_PIPE));
        dev.groundburst.compat.create.CheckScenes.glue(level, new BlockPos(-20, -58, 21), new BlockPos(-16, -54, 26));
    }

    private static ItemStack cruise() {
        return MissileAssembly.missile(new MissileSpec(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.S,
                MissileParts.Motor.CRUISE, MissileParts.Guidance.GPS, false));
    }

    private static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
    }

    /** A formed Cruise Missile Launcher facing north with its controller at {@code c}. */
    private static CruiseLauncherBlockEntity launcher(ServerLevel level, BlockPos c) {
        MultiblockDefinition def = GBLaunch.CRUISE_LAUNCHER;
        for (MultiblockDefinition.Cell cell : StructureSpawnerItem.buildOrder(def, c, Direction.NORTH)) {
            BlockState state = cell.key() == '.' ? Blocks.AIR.defaultBlockState() : def.legend().get(cell.key()).preview().get();
            if (cell.key() == MultiblockDefinition.CONTROLLER) {
                state = state.setValue(MachineBlock.FACING, Direction.NORTH);
            }
            level.setBlockAndUpdate(cell.pos(), state);
        }
        CruiseLauncherBlockEntity be = (CruiseLauncherBlockEntity) level.getBlockEntity(c);
        be.revalidate();
        GroundBurst.LOGGER.info("Create check: launcher at {} formed: {}", c, be.isFormed());
        return be;
    }

    /** Two cruise missiles and the power for both; aimed at {@code target} and armed. */
    private static void arm(ServerLevel level, CruiseLauncherBlockEntity be, BlockPos target) {
        for (int slot : new int[] {CruiseLauncherBlockEntity.SLOT_A, CruiseLauncherBlockEntity.SLOT_B}) {
            be.inventory().setItem(slot, MissileAssembly.missile(new MissileSpec(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.S,
                    MissileParts.Motor.CRUISE, MissileParts.Guidance.GPS, false)));
        }
        be.energy().generate(CruiseLauncherBlockEntity.ENERGY_CAPACITY);
        boolean ok = be.setTarget(target.getX(), target.getZ()) && be.arm(true);
        GroundBurst.LOGGER.info("Create check: launcher at {} aimed at {} and armed: {}", be.getBlockPos(), target, ok);
    }

    /** The check's network: launchers linked to it before they are carried off must stay on it. */
    private static long network;

    private static void link(ServerLevel level, CruiseLauncherBlockEntity be) {
        LaunchNetwork nets = LaunchNetwork.of(level);
        if (network == 0) {
            network = nets.create(level, new BlockPos(-20, -60, 0));
        }
        GroundBurst.LOGGER.info("Create check: launcher at {} linked: {}", be.getBlockPos(), nets.link(level, network, be));
    }

    /** Whether the launcher with its key at {@code pos} is still on the check's network, at that key. */
    private static void linked(ServerLevel level, String name, @Nullable BlockPos pos) {
        LaunchNetwork.Network n = pos == null ? null : LaunchNetwork.of(level).networkOf(pos);
        boolean ok = n != null && n.id == network;
        checks++;
        passed += ok ? 1 : 0;
        GroundBurst.LOGGER.info("Create check: {} still on its network at {}: {}", name, pos, ok ? "OK" : "WRONG");
    }

    /** The launcher aboard the ship: the one near where the ship was set down. */
    private static @Nullable CruiseLauncherBlockEntity shipLauncher(ServerLevel level) {
        for (LaunchSite site : Launches.silosNear(level, BlockPos.containing(SHIP_AT))) {
            if (site instanceof CruiseLauncherBlockEntity c && !c.carried() && site.worldBlockPos().distSqr(BlockPos.containing(SHIP_AT)) < 20 * 20) {
                return c;
            }
        }
        return null;
    }

    /** Fires {@code name} (its countdown cut short), watching for its first missile. */
    private static void fire(ServerLevel level, String name, Supplier<CruiseLauncherBlockEntity> launcher) {
        CruiseLauncherBlockEntity be = launcher.get();
        if (be == null) {
            checks++;
            GroundBurst.LOGGER.info("Create check: {} not found: WRONG", name);
            return;
        }
        before.clear();
        for (Flights.Flight f : Flights.of(level).all()) {
            before.add(f.id());
        }
        boolean counting = be.launchArmed(level, null);
        be.skipCountdownForTest();
        watched = launcher;
        watchedName = name;
        GroundBurst.LOGGER.info("Create check: {} ordered to fire: {}", name, counting);
    }
}
