package dev.groundburst.compat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.CruiseLauncherBlockEntity;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.LaunchStationBlockEntity;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.StructureSpawnerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Builds a test world for the 1.21.1 build with Create and Create Aeronautics (run with {@code gradlew runTestRange
 * -PwithCreate}; nothing happens unless the {@code groundburst.testRange} property is set): a flat world, deep enough for
 * pits and a lake, with a command post (Launch Control Station, Launch Computer, Radar Array, an armory), a row of
 * launchers loaded and on the command post's network, artillery, launchers on Create's contraptions (a bearing turret, a
 * silo a piston raises from a pit), Create Aeronautics ships (a missile boat, an airship with a launcher aboard, target
 * ships) and a target range. It photographs itself (run/screenshots/range_*.png), saves and quits; the save is
 * run/saves/{@value #WORLD}, to be copied into the 1.21.1 instance's saves.
 */
@EventBusSubscriber(modid = GroundBurst.MODID, value = Dist.CLIENT)
public final class TestRange {
    private static final boolean ON = Boolean.getBoolean("groundburst.testRange");
    static final String WORLD = "Ground Burst Test Range";
    private static boolean started;
    private static int ticks = -1;
    /** Every launcher built, to be put on the command post's network once it has one. */
    static final List<BlockPos> LAUNCHERS = new ArrayList<>();
    private static long network;
    private static @Nullable String codes;

    // --- the layout (the ground is at y = 0: grass at -1) ------------------------------------------------------------
    static final BlockPos SPAWN = new BlockPos(0, 0, 6);
    static final BlockPos STATION = new BlockPos(-6, 0, -3), COMPUTER = new BlockPos(0, 0, -3), RADAR = new BlockPos(7, 0, -3);
    static final int ROW_Z = -42;
    static final BlockPos SILO = new BlockPos(-46, 0, ROW_Z), CRUISE = new BlockPos(-30, 0, ROW_Z), VLS = new BlockPos(-12, 0, ROW_Z),
            COMPLEX = new BlockPos(10, 0, ROW_Z), MISSILE_SILO = new BlockPos(36, 0, ROW_Z);
    static final BlockPos MORTAR = new BlockPos(-52, 0, -18), HOWITZER = new BlockPos(-44, 0, -18);
    /** The target range, far enough off for the big warheads. */
    public static final BlockPos RANGE = new BlockPos(0, 0, -420);

    private TestRange() {}

    @SubscribeEvent
    static void clientTick(ClientTickEvent.Post event) {
        if (!ON) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.getOverlay() == null && mc.level == null && mc.screen != null) {
            started = true;
            GroundBurst.LOGGER.info("Test range: making the world");
            createWorld(mc);
        }
        tick(mc);
    }

    private static void createWorld(Minecraft mc) {
        try (var access = mc.getLevelSource().createAccess(WORLD)) {
            access.deleteLevel();
        } catch (Exception ignored) {
            // none yet
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
        rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DOINSOMNIA).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261104L, false, false), registries -> {
            HolderGetter<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
            HolderGetter<StructureSet> sets = registries.lookupOrThrow(Registries.STRUCTURE_SET);
            HolderGetter<PlacedFeature> features = registries.lookupOrThrow(Registries.PLACED_FEATURE);
            // Deep enough for a pit a silo rises out of and a lake (64 layers: the ground at y = 0), no villages.
            FlatLevelGeneratorSettings flat = FlatLevelGeneratorSettings.getDefault(biomes, sets, features).withBiomeAndLayers(
                    List.of(new FlatLayerInfo(1, Blocks.BEDROCK), new FlatLayerInfo(56, Blocks.STONE), new FlatLayerInfo(6, Blocks.DIRT),
                            new FlatLayerInfo(1, Blocks.GRASS_BLOCK)),
                    Optional.of(HolderSet.direct()), biomes.getOrThrow(Biomes.PLAINS));
            WorldDimensions dims = registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
            return dims.replaceOverworldGenerator(registries, new FlatLevelSource(flat));
        }, mc.screen);
    }

    // --- the build, a step at a time ---------------------------------------------------------------------------------
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
            case 30 -> {
                command(mc, "time set 6000");
                command(mc, "weather clear");
                command(mc, "setworldspawn " + SPAWN.getX() + " " + SPAWN.getY() + " " + SPAWN.getZ());
                look(mc, Vec3.atBottomCenterOf(SPAWN).add(0, 1.6, 0), new Vec3(0, 2, -20));
            }
            case 40 -> onServer(mc, RangeGround::groundwork);
            case 60 -> onServer(mc, RangeGround::commandPost);
            case 80 -> onServer(mc, RangeGround::launchRow);
            case 90 -> onServer(mc, RangeGround::targetRange);
            case 95 -> onServer(mc, RangeGround::scenery);
            case 100 -> onServer(mc, level -> dev.groundburst.compat.create.RangeCreate.build(level));
            case 130 -> onServer(mc, RangeShips::build);
            case 150 -> onServer(mc, TestRange::linkAll);
            case 160 -> onServer(mc, level -> dev.groundburst.compat.create.RangeCreate.start(level));
            case 180 -> onServer(mc, RangeShips::assemble);
            case 185 -> onServer(mc, RangeShips::watch);
            case 260 -> onServer(mc, level -> GroundBurst.LOGGER.info("Test range: pit silo {}", dev.groundburst.compat.create.RangeCreate.pitSiloState(level)));
            case 262 -> onServer(mc, dev.groundburst.compat.create.RangeCreate::throwLever);           // down
            case 360 -> onServer(mc, level -> {
                GroundBurst.LOGGER.info("Test range: pit silo, lowered: {}", dev.groundburst.compat.create.RangeCreate.pitSiloState(level));
                dev.groundburst.compat.create.RangeCreate.throwLever(level);                              // and up again
            });
            case 460 -> onServer(mc, level -> {
                GroundBurst.LOGGER.info("Test range: pit silo, raised again: {}", dev.groundburst.compat.create.RangeCreate.pitSiloState(level));
                report(level);
            });
            // --- pictures, from a free camera with no hand or overlays (for review, and the mod's pages) ------------------
            case 470 -> {
                command(mc, "gamemode spectator");
                mc.options.hideGui = true;
            }
            case 475 -> look(mc, new Vec3(-75, 55, 75), new Vec3(-5, 0, -20));
            case 500 -> shot(mc, "overview");
            case 505 -> look(mc, new Vec3(22, 19, 50), new Vec3(41, 29, 70));
            case 525 -> shot(mc, "airship");
            case 530 -> look(mc, new Vec3(33.5, 29, 52.5), new Vec3(41.5, 26, 66));
            case 550 -> shot(mc, "airship_deck");
            case 555 -> look(mc, new Vec3(-62, 5, 37), new Vec3(-83, 1, 48));
            case 575 -> shot(mc, "missile_boat");
            case 580 -> look(mc, new Vec3(67, 5, -1), new Vec3(58, 1, -7));
            case 600 -> shot(mc, "turret");
            case 605 -> look(mc, new Vec3(93, 7, 1), new Vec3(84, 3, -8));
            case 625 -> shot(mc, "pit_silo");
            case 630 -> look(mc, new Vec3(-62, 3, -28), new Vec3(40, 5, -44));
            case 650 -> shot(mc, "launch_row");
            case 655 -> look(mc, new Vec3(11, 5, 13), new Vec3(0, 2, -3));
            case 675 -> shot(mc, "command_post");
            case 680 -> look(mc, new Vec3(34, 26, -366), new Vec3(0, 3, -416));
            case 700 -> shot(mc, "target_range");
            // Two at sunset, for the pages; the world is saved at noon.
            case 705 -> command(mc, "time set 12600");
            case 710 -> look(mc, new Vec3(66, 21, 96), new Vec3(41, 29, 68));
            case 735 -> shot(mc, "airship_sunset");
            case 740 -> look(mc, new Vec3(-58, 2.5, -24), new Vec3(30, 10, -46));
            case 760 -> shot(mc, "launch_row_sunset");
            case 765 -> {
                command(mc, "time set 6000");
                mc.options.hideGui = false;
                command(mc, "gamemode creative");
            }
            case 775 -> onServer(mc, TestRange::finish);
            case 795 -> {
                GroundBurst.LOGGER.info("Test range: done; saved as run/saves/{}", WORLD);
                mc.stop();
            }
            default -> {
                if (ticks > 200 && ticks < 470 && ticks % 60 == 0) {
                    onServer(mc, RangeShips::watch);
                }
            }
        }
    }

    /** Every launcher built onto the command post's network (a ship's before it is made a ship: the network follows it). */
    static void linkAll(ServerLevel level) {
        if (!(level.getBlockEntity(STATION) instanceof LaunchStationBlockEntity station)) {
            GroundBurst.LOGGER.warn("Test range: no station at {}", STATION);
            return;
        }
        LaunchNetwork.Network n = station.network(level);
        if (n == null) {
            return;
        }
        network = n.id;
        codes = n.code();
        LaunchNetwork nets = LaunchNetwork.of(level);
        for (BlockPos p : LAUNCHERS) {
            BlockEntity be = dev.groundburst.content.machine.Carried.blockEntity(level, p, BlockEntity.class);
            GroundBurst.LOGGER.info("Test range: launcher at {} on network {}: {}", p, codes, be == null ? "none there" : nets.link(level, network, be));
        }
        if (level.getBlockEntity(RADAR) != null) {
            GroundBurst.LOGGER.info("Test range: radar on network: {}", nets.link(level, network, level.getBlockEntity(RADAR)));
        }
    }

    private static void report(ServerLevel level) {
        LaunchNetwork.Network n = network == 0 ? null : LaunchNetwork.of(level).network(network);
        if (n == null) {
            GroundBurst.LOGGER.warn("Test range: no network");
            return;
        }
        for (LaunchNetwork.Member m : n.members()) {
            BlockEntity be = dev.groundburst.content.machine.Carried.blockEntity(level, m.pos, BlockEntity.class);
            String what = be instanceof MultiblockController c ? "formed " + c.isFormed() : be == null ? "none" : be.getClass().getSimpleName();
            if (be instanceof MachineBlockEntity machine) {
                what += ", " + machine.energy().stored() + " FE, " + machine.status() + (machine.carried() ? ", carried" : "");
            }
            GroundBurst.LOGGER.info("Test range: network {} member at {} (in the world at {}): {}", n.code(), m.pos, m.at(), what);
        }
    }

    /** The player put at the spawn with the tools to try it all, and the world saved. */
    private static void finish(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.getInventory().clearContent();
        ItemStack designator = item("groundburst:laser_designator", 1);
        ItemStack card = item("groundburst:launch_codes", 1);
        if (codes != null) {
            designator.set(dev.groundburst.registry.GBDataComponents.LAUNCH_CODES.get(), codes);
            card.set(dev.groundburst.registry.GBDataComponents.LAUNCH_CODES.get(), codes);
        }
        ItemStack[] hotbar = {designator, card, item("groundburst:field_manual", 1), item("groundburst:wrench", 1), item("create:wrench", 1),
                item("create:goggles", 1), item("groundburst:remote_detonator", 1), item("groundburst:c4", 16), item("groundburst:geiger_counter", 1)};
        for (int i = 0; i < hotbar.length; i++) {
            player.getInventory().setItem(i, hotbar[i]);
        }
        player.getInventory().selected = 0;
        player.teleportTo(level, SPAWN.getX() + 0.5, SPAWN.getY(), SPAWN.getZ() + 0.5, 180f, 0f);
        player.setRespawnPosition(level.dimension(), SPAWN, 180f, true, false);
        level.getServer().saveEverything(false, true, true);
        GroundBurst.LOGGER.info("Test range: player at the spawn with the designator and codes for network {}", codes);
    }

    // --- helpers -----------------------------------------------------------------------------------------------------
    static void onServer(Minecraft mc, Consumer<ServerLevel> job) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                try {
                    job.accept(server.overworld());
                } catch (RuntimeException e) {
                    GroundBurst.LOGGER.error("Test range: failed", e);
                }
            });
        }
    }

    static void command(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }

    static void serverCommand(ServerLevel level, String command) {
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    /** The player put at {@code from}, looking at {@code at}. */
    static void look(Minecraft mc, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) -Math.toDegrees(Math.atan2(d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        command(mc, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", from.x, from.y, from.z, yaw, pitch));
    }

    static void shot(Minecraft mc, String name) {
        mc.gui.getChat().clearMessages(false);
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, "range_" + name + ".png", mc.getMainRenderTarget(),
                message -> GroundBurst.LOGGER.info("Test range: {}", message.getString()));
    }

    /** A block by its id, with properties given as {@code name=value}. */
    public static BlockState state(String id, String... props) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        if (block == Blocks.AIR && !id.endsWith("air")) {
            GroundBurst.LOGGER.warn("Test range: no block {}", id);
        }
        BlockState state = block.defaultBlockState();
        for (String p : props) {
            String[] kv = p.split("=", 2);
            state = with(state, kv[0], kv[1]);
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, String name, String value) {
        @SuppressWarnings("unchecked")
        Property<T> property = (Property<T>) state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            GroundBurst.LOGGER.warn("Test range: {} has no property {}", state.getBlock(), name);
            return state;
        }
        Optional<T> v = property.getValue(value);
        if (v.isEmpty()) {
            GroundBurst.LOGGER.warn("Test range: {} {} has no value {}", state.getBlock(), name, value);
            return state;
        }
        return state.setValue(property, v.get());
    }

    public static void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, Block.UPDATE_ALL);
    }

    public static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (BlockPos p : BlockPos.betweenClosed(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1),
                Math.max(z0, z1))) {
            level.setBlock(p, s, Block.UPDATE_CLIENTS);
        }
    }

    public static ItemStack item(String id, int count) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) {
            GroundBurst.LOGGER.warn("Test range: no item {}", id);
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, count);
    }

    /** A missile: {@code warhead} (its id's path in Ground Burst) in a body of {@code size}, on {@code motor}. */
    public static ItemStack missile(String warhead, SizeClass size, MissileParts.Motor motor, MissileParts.Guidance guidance) {
        return MissileAssembly.missile(new MissileSpec(item("groundburst:" + warhead, 1), size, motor, guidance, false));
    }

    public static ItemStack cruise(String warhead) {
        return missile(warhead, SizeClass.S, MissileParts.Motor.CRUISE, MissileParts.Guidance.GPS);
    }

    /**
     * One of the mod's structures built whole at {@code c} (its controller), facing {@code facing}, formed and charged,
     * with a Creative Capacitor Bank against each of its Energy Ports to keep it charged. Its cells.
     */
    public static List<MultiblockDefinition.Cell> structure(ServerLevel level, MultiblockDefinition def, BlockPos c, Direction facing, boolean banks) {
        List<MultiblockDefinition.Cell> cells = StructureSpawnerItem.buildOrder(def, c, facing);
        for (MultiblockDefinition.Cell cell : cells) {
            BlockState s = cell.key() == '.' ? Blocks.AIR.defaultBlockState() : def.legend().get(cell.key()).preview().get();
            if (cell.key() == MultiblockDefinition.CONTROLLER) {
                s = s.setValue(MachineBlock.FACING, facing);
            }
            level.setBlock(cell.pos(), s, Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(c) instanceof MultiblockController controller) {
            controller.revalidate();
            GroundBurst.LOGGER.info("Test range: {} at {} formed: {}", def.id(), c, controller.isFormed());
        }
        if (level.getBlockEntity(c) instanceof MachineBlockEntity m) {
            m.energy().generate(m.energy().capacity());
        }
        if (banks) {
            Set<BlockPos> taken = new HashSet<>();
            for (MultiblockDefinition.Cell cell : cells) {
                taken.add(cell.pos());
            }
            for (MultiblockDefinition.Cell cell : cells) {
                if (cell.key() == 'E') {
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos n = cell.pos().relative(d);
                        if (!taken.contains(n) && level.getBlockState(n).isAir()) {
                            set(level, n, state("groundburst:creative_capacitor_bank"));
                            break;
                        }
                    }
                }
            }
        }
        return cells;
    }

    /** A chest of things with a sign over it. */
    public static void chest(ServerLevel level, BlockPos p, Direction facing, String label, ItemStack... items) {
        set(level, p, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, facing));
        if (level.getBlockEntity(p) instanceof ChestBlockEntity chest) {
            int slot = 0;
            for (ItemStack s : items) {
                if (!s.isEmpty() && slot < chest.getContainerSize()) {
                    chest.setItem(slot++, s);
                }
            }
        }
        sign(level, p.above(), facing, label);
    }

    /** A standing sign facing {@code facing}, its lines (up to four). */
    public static void sign(ServerLevel level, BlockPos p, Direction facing, String... lines) {
        int rotation = switch (facing) {
            case SOUTH -> 0;
            case WEST -> 4;
            case NORTH -> 8;
            default -> 12;
        };
        set(level, p, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation));
        if (level.getBlockEntity(p) instanceof SignBlockEntity sign) {
            var text = sign.getFrontText();
            for (int i = 0; i < Math.min(4, lines.length); i++) {
                text = text.setMessage(i, Component.literal(lines[i]));
            }
            sign.setText(text, true);
            sign.setChanged();
            level.sendBlockUpdated(p, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** A missile in a launcher's slot, if it takes it (logged when it does not). */
    public static void load(MachineBlockEntity m, int slot, ItemStack missile) {
        if (m.inventory().canPlaceItem(slot, missile)) {
            m.inventory().setItem(slot, missile);
        } else {
            GroundBurst.LOGGER.warn("Test range: {} at {} does not take {} in slot {}", m.getClass().getSimpleName(), m.getBlockPos(),
                    missile.getHoverName().getString(), slot);
        }
    }

    /** Remembered as a launcher to go on the network. */
    public static void launcher(BlockPos controller) {
        LAUNCHERS.add(controller.immutable());
    }

    static @Nullable LaunchSiloBlockEntity silo(ServerLevel level, BlockPos p) {
        return level.getBlockEntity(p) instanceof LaunchSiloBlockEntity s ? s : null;
    }

    static @Nullable CruiseLauncherBlockEntity cruiseAt(ServerLevel level, BlockPos p) {
        return level.getBlockEntity(p) instanceof CruiseLauncherBlockEntity c ? c : null;
    }

    static MultiblockDefinition def(String which) {
        return switch (which) {
            case "silo" -> GBLaunch.SILO;
            case "cruise" -> GBLaunch.CRUISE_LAUNCHER;
            case "vls" -> GBLaunch.VLS;
            case "complex" -> GBLaunch.COMPLEX;
            case "missile_silo" -> GBLaunch.MISSILE_SILO;
            case "computer" -> GBLaunch.COMPUTER;
            case "radar" -> GBLaunch.RADAR;
            case "station" -> GBLaunch.STATION;
            default -> throw new IllegalArgumentException(which);
        };
    }
}
