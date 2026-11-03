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
 * Pictures in run/screenshots/create_*.png.
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
    private static void look(Minecraft mc, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) -Math.toDegrees(Math.atan2(d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        command(mc, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", from.x, from.y, from.z, yaw, pitch));
    }

    /** A command run by the server itself (its answer in the log). */
    private static void serverCommand(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static void shot(Minecraft mc, String name) {
        mc.gui.getChat().clearMessages(false);
        Screenshot.grab(mc.gameDirectory, "create_" + name + ".png", mc.getMainRenderTarget(),
                message -> GroundBurst.LOGGER.info("Create check: {}", message.getString()));
    }

    private static void onServer(Minecraft mc, java.util.function.Consumer<ServerLevel> job) {
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
            case 550 -> {
                GroundBurst.LOGGER.info("Create check: done, {} of {} checks OK", passed, checks);
                mc.stop();
            }
            default -> {
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
