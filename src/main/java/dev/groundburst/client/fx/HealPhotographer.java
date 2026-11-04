package dev.groundburst.client.fx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.Blasts;
import dev.groundburst.config.GBServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid for healing (DESIGN.md §21.9): with {@code -Dgroundburst.healShots} (the {@code healShots} Gradle
 * run) the client makes a flat creative world, turns healing on with short waits, builds two scenes (a farmstead for a
 * thermobaric warhead, a little town for a tactical nuke: houses with chests, doors, torches, windows, an item frame and
 * an armour stand, trees, a pond, a wheat field, sand), and photographs each before the blast, after it, while it heals
 * and once it has, into {@code run/screenshots/heal_<scene>_<stage>.png}. Then it compares every block of each scene
 * with what it was and writes {@code run/heal/heal_check.txt} (and the log's "Heal check" lines), and quits.
 */
public final class HealPhotographer {
    /** Ticks to wait at least at a camera, the ticks the section builder must have been quiet, the most to wait. */
    private static final int SETTLE = 30, STABLE = 20, MOST = 600;
    /** The longest the healing may take before the check gives up waiting (ticks). */
    private static final int HEAL_TIMEOUT = 20 * 60 * 8;

    private record Scene(String name, BlockPos centre, int half, int below, int above, String profile, Vec3 camera) {
        AABB box() {
            return new AABB(this.centre.getX() - this.half, this.centre.getY() - this.below, this.centre.getZ() - this.half,
                    this.centre.getX() + this.half + 1, this.centre.getY() + this.above + 1, this.centre.getZ() + this.half + 1);
        }
    }

    private record Shot(String file, Vec3 at, float yaw, float pitch, BlockPos look) {}

    private enum Stage { MAKE, BEFORE, BLAST, AFTER, HEALING, PART, HEALED, CHECK, DONE }

    private static final List<Scene> SCENES = new ArrayList<>();
    private static final Deque<Shot> SHOTS = new ArrayDeque<>();
    /** Each scene's blocks before (by scene), its chests' contents, filled on the server thread. */
    private static final Map<String, BlockState[]> BEFORE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, String> CHESTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean made, busy;
    /** What the server last said (it alone may look at its chunks): every scene's middle healing, every scene healed. */
    private static volatile boolean allHealing, allHealed;
    private static boolean worldMade;
    private static Stage stage = Stage.MAKE;
    private static int there, stable, waited, sinceBlast;
    private static Shot current;

    private HealPhotographer() {}

    public static boolean enabled() {
        return !System.getProperty("groundburst.healShots", "").isEmpty();
    }

    public static void register() {
        if (enabled()) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
            // An item dropped in a scene once the blasts are over is something healing did not remember: where it came from.
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) -> {
                if (e.getEntity() instanceof ItemEntity item && !e.getLevel().isClientSide() && stage.ordinal() >= Stage.AFTER.ordinal()
                        && traced < 6 && SCENES.stream().anyMatch(s -> s.box().contains(item.position()))) {
                    traced++;
                    GroundBurst.LOGGER.warn("Heal check: {} dropped at {} (stage {})", item.getItem(), item.blockPosition(), stage,
                            new Throwable("where it came from"));
                }
            });
        }
    }

    private static int traced;

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.setScreen(null);
        }
        if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        }
        if (mc.level == null && !worldMade && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            try {
                var settings = new net.minecraft.world.level.LevelSettings("Heal shots", GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = DevWorlds.fresh(mc, "heal_shots");
                mc.createWorldOpenFlows().createFreshLevel(folder, settings, new net.minecraft.world.level.levelgen.WorldOptions(0L, false, false),
                        HealPhotographer::flat, mc.screen);
            } catch (IOException e) {
                GroundBurst.LOGGER.error("Heal shots: could not make a world", e);
            }
            return;
        }
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        mc.options.renderDistance().set(16);
        mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        if (!mc.options.hideGui) {
            mc.options.hideGui = !mc.options.hideGui;
        }
        if (busy) {
            return;                                        // the server is working on the last step
        }
        if (current != null) {
            if (shoot(mc)) {
                current = null;
            }
            return;
        }
        Shot next = SHOTS.poll();
        if (next != null) {
            current = next;
            there = 0;
            stable = 0;
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(server.overworld(), next.at().x, next.at().y, next.at().z, java.util.Set.of(), next.yaw(), next.pitch(), true);
            });
            return;
        }
        waited++;
        sinceBlast++;
        ServerLevel level = server.overworld();
        if (waited % 20 == 0 && (stage == Stage.HEALING || stage == Stage.HEALED)) {
            server.execute(() -> {
                allHealing = SCENES.stream().allMatch(s -> healingAt(level, s) || healed(level, s));
                allHealed = SCENES.stream().allMatch(s -> healed(level, s));
            });
        }
        switch (stage) {
            case MAKE -> {
                busy = true;
                server.execute(() -> {
                    try {
                        make(level);
                    } catch (RuntimeException e) {
                        GroundBurst.LOGGER.error("Heal shots: could not build the scenes", e);
                    } finally {
                        busy = false;
                    }
                });
                stage = Stage.BEFORE;
            }
            case BEFORE -> {
                queue("before");
                stage = Stage.BLAST;
            }
            case BLAST -> {
                busy = true;
                server.execute(() -> {
                    for (Scene s : SCENES) {
                        Blasts.detonate(level, Vec3.atCenterOf(s.centre()), Blasts.profile(s.profile()), null);
                    }
                    busy = false;
                });
                waited = 0;
                sinceBlast = 0;
                stage = Stage.AFTER;
            }
            case AFTER -> {
                // The blasts done: the damage (before the 20 s wait is over).
                if (waited > 20 * 12 && dev.groundburst.blast.BlastManager.of(level).active() == 0) {
                    queue("after");
                    waited = 0;
                    stage = Stage.HEALING;
                }
            }
            case HEALING -> {
                // Once each scene's middle has begun to heal...
                if (waited % 20 == 10 && allHealing || waited > HEAL_TIMEOUT) {
                    waited = 0;
                    stage = Stage.PART;
                }
            }
            case PART -> {
                if (waited >= 20 * 6) {                    // ...six seconds on: part way
                    queue("healing");
                    waited = 0;
                    allHealed = false;
                    stage = Stage.HEALED;
                }
            }
            case HEALED -> {
                // (and the mushroom cloud mostly gone, for the pictures: it is the client's, and slow to clear)
                if (waited % 20 == 10 && allHealed && sinceBlast > 20 * 150 || waited > HEAL_TIMEOUT) {
                    if (waited > HEAL_TIMEOUT) {
                        GroundBurst.LOGGER.warn("Heal check: still healing after {} s", HEAL_TIMEOUT / 20);
                    }
                    waited = 0;
                    queue("healed");
                    stage = Stage.CHECK;
                }
            }
            case CHECK -> {
                if (waited > 40) {
                    busy = true;
                    server.execute(() -> {
                        try {
                            check(level);
                        } finally {
                            busy = false;
                        }
                    });
                    stage = Stage.DONE;
                    waited = 0;
                }
            }
            case DONE -> {
                if (waited > 40) {
                    GroundBurst.LOGGER.info("Heal shots done");
                    mc.stop();
                }
            }
        }
    }

    /** Takes the current shot once its ground has come in; true when taken. */
    private static boolean shoot(Minecraft mc) {
        Shot s = current;
        if (mc.player.position().distanceTo(s.at()) > 1.5) {
            return false;
        }
        there++;
        int cx = s.look().getX() >> 4, cz = s.look().getZ() >> 4;
        boolean arrived = true;
        for (int x = -3; x <= 3 && arrived; x++) {
            for (int z = -3; z <= 3 && arrived; z++) {
                arrived = mc.level.getChunkSource().getChunk(cx + x, cz + z, ChunkStatus.FULL, false) != null;
            }
        }
        stable = arrived && mc.levelRenderer.hasRenderedAllSections() ? stable + 1 : 0;
        if (there >= MOST || there >= SETTLE && stable >= STABLE) {
            Screenshot.grab(mc.gameDirectory, s.file(), mc.getMainRenderTarget(), 1,
                    msg -> GroundBurst.LOGGER.info("Heal shot: {}", msg.getString()));
            return true;
        }
        return false;
    }

    private static void queue(String what) {
        for (Scene s : SCENES) {
            Vec3 look = Vec3.atCenterOf(s.centre());
            Vec3 d = look.subtract(s.camera());
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) Math.toDegrees(Math.atan2(-d.y, Math.hypot(d.x, d.z)));
            SHOTS.add(new Shot("heal_" + s.name() + "_" + what + ".png", s.camera(), yaw, pitch, s.centre()));
        }
    }

    /** Whether the chunk at a scene's middle has begun to heal (on the server thread: off it the chunks read as not there). */
    private static boolean healingAt(ServerLevel level, Scene s) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(s.centre().getX() >> 4, s.centre().getZ() >> 4);
        return chunk != null && chunk.getExistingDataOrNull(dev.groundburst.heal.GBHeal.SCARS.get()) != null
                && dev.groundburst.heal.Healer.of(level).status(Vec3.atCenterOf(s.centre())).soonest() == 0;
    }

    /** Whether nothing is left to heal anywhere in a scene. */
    private static boolean healed(ServerLevel level, Scene s) {
        AABB b = s.box();
        for (int cx = (int) Math.floor(b.minX) >> 4; cx <= (int) Math.floor(b.maxX) >> 4; cx++) {
            for (int cz = (int) Math.floor(b.minZ) >> 4; cz <= (int) Math.floor(b.maxZ) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null && chunk.getExistingDataOrNull(dev.groundburst.heal.GBHeal.SCARS.get()) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    // --- the scenes --------------------------------------------------------------------------------------------------------------
    private static void make(ServerLevel level) {
        var commands = level.getServer().getCommands();
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        for (String c : List.of("time set noon", "gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
                "weather clear", "kill @e[type=!player]")) {
            commands.performPrefixedCommand(source, c);
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.SPECTATOR);
        // Healing on, with short waits (the server's own config, in this throwaway world).
        GBServerConfig.HEAL_ENABLED.set(true);
        GBServerConfig.HEAL_SCHEDULE.set(GBServerConfig.HealSchedule.AFTER_WAIT);
        GBServerConfig.HEAL_WAIT_SECONDS.set(20);
        GBServerConfig.HEAL_RANDOM_SECONDS.set(6);
        GBServerConfig.HEAL_BLOCKS_PER_SECOND.set(150);
        GBServerConfig.HEAL_LONGEST_SECONDS.set(30);
        GBServerConfig.HEAL_ORDER.set(GBServerConfig.HealOrder.BOTTOM_UP);
        GBServerConfig.HEAL_EFFECTS.set(true);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) - 1;
        BlockPos farm = new BlockPos(0, top, 0), town = new BlockPos(1000, top, 0);
        SCENES.add(new Scene("farmstead", farm, 30, 16, 16, "thermobaric_warhead", new Vec3(farm.getX() + 34, top + 26, farm.getZ() + 34)));
        SCENES.add(new Scene("town", town, 88, 30, 22, "tactical_nuclear_warhead", new Vec3(town.getX() + 95, top + 70, town.getZ() + 95)));
        for (Scene s : SCENES) {
            AABB b = s.box();
            commands.performPrefixedCommand(source, "forceload add " + (int) b.minX + " " + (int) b.minZ + " " + (int) b.maxX + " " + (int) b.maxZ);
            for (int cx = (int) b.minX >> 4; cx <= (int) b.maxX >> 4; cx++) {
                for (int cz = (int) b.minZ >> 4; cz <= (int) b.maxZ >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
        }
        farmstead(level, farm);
        town(level, town);
        for (Scene s : SCENES) {
            settle(level, s);
            BEFORE.put(s.name(), snapshot(level, s));
        }
        GroundBurst.LOGGER.info("Heal check: scenes built ({} blocks)", BEFORE.values().stream().mapToInt(a -> a.length).sum());
    }

    /** A house, its garden, a wheat field, a pond, two trees and a sand bank round {@code c}: the blast's middle is the yard. */
    private static void farmstead(ServerLevel level, BlockPos c) {
        house(level, c.offset(5, 1, -4), "farm");
        tree(level, c.offset(-9, 1, -7));
        tree(level, c.offset(-11, 1, 8));
        field(level, c.offset(-6, 0, 3), 6, 5);
        pond(level, c.offset(9, 0, 7), 4, 3);
        sandBank(level, c.offset(-2, 1, -12), c.offset(3, 2, -10));
        for (int i = -4; i <= 4; i++) {
            level.setBlock(c.offset(i, 1, -8), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(c.offset(-4, 2, -8), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(c.offset(4, 2, -8), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        flowers(level, c, 12);
    }

    /** A street of four houses, a well, trees, a field and a pond round {@code c} (the nuke bursts over the street). */
    private static void town(ServerLevel level, BlockPos c) {
        for (int x = -60; x <= 60; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(c.offset(x, 0, z), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        house(level, c.offset(-30, 1, -12), "town_a");
        house(level, c.offset(18, 1, -12), "town_b");
        house(level, c.offset(-50, 1, 5), "town_c");
        house(level, c.offset(45, 1, 6), "town_d");
        for (int[] t : new int[][] {{-20, 20}, {30, 25}, {-70, -30}, {60, -40}, {5, -55}, {-45, 45}, {72, 40}}) {
            tree(level, c.offset(t[0], 1, t[1]));
        }
        field(level, c.offset(20, 0, 30), 14, 9);         // (nine rows: none more than four from its water, or it dries anyway)
        pond(level, c.offset(-25, 0, 32), 7, 3);
        sandBank(level, c.offset(40, 1, -40), c.offset(46, 3, -34));
        flowers(level, c, 70);
    }

    /** A 7x7 plank house (stone floor, glass windows, a door, a torch, a chest of goods, an item frame and an armour stand by the door). */
    private static void house(ServerLevel level, BlockPos o, String name) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState(), log = Blocks.OAK_LOG.defaultBlockState();
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                level.setBlock(o.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y < 4; y++) {
                    boolean edge = x == 0 || z == 0 || x == 6 || z == 6, corner = (x == 0 || x == 6) && (z == 0 || z == 6);
                    BlockState s = !edge ? Blocks.AIR.defaultBlockState() : corner ? log : y == 1 && (x == 3 || z == 3) ? Blocks.GLASS.defaultBlockState() : planks;
                    level.setBlock(o.offset(x, y, z), s, Block.UPDATE_CLIENTS);
                }
                level.setBlock(o.offset(x, 4, z), Blocks.SPRUCE_SLAB.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        BlockPos door = o.offset(3, 0, 6);
        BlockState d = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        level.setBlock(door, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
        level.setBlock(door.above(), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(3, 2, 5), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS);
        BlockPos chest = o.offset(1, 0, 1);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(chest) instanceof Container box) {
            box.setItem(0, new ItemStack(Items.DIAMOND, 12));
            box.setItem(5, new ItemStack(Items.BREAD, 20));
            box.setItem(13, new ItemStack(Items.IRON_INGOT, 33));
        }
        CHESTS.put(chest.asLong() + "", name);
        level.setBlock(o.offset(5, 0, 1), Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(5, 0, 2), Blocks.FURNACE.defaultBlockState(), Block.UPDATE_CLIENTS);
        ItemFrame frame = new ItemFrame(level, o.offset(1, 2, 7), Direction.SOUTH);
        frame.setItem(new ItemStack(Items.MAP));
        level.addFreshEntity(frame);
        ArmorStand stand = new ArmorStand(level, o.getX() + 5.5, o.getY(), o.getZ() + 8.5);
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        level.addFreshEntity(stand);
    }

    /** An oak tree grown as a sapling grows one (its leaves count their way to the trunk). */
    private static void tree(ServerLevel level, BlockPos at) {
        var feature = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(net.minecraft.data.worldgen.features.TreeFeatures.OAK);
        feature.value().place(level, level.getChunkSource().getGenerator(), net.minecraft.util.RandomSource.create(at.asLong()), at);
    }

    /** A bank of sand, on earth (grass under a block turns to earth by itself in a while, blast or none). */
    private static void sandBank(ServerLevel level, BlockPos from, BlockPos to) {
        for (BlockPos p : BlockPos.betweenClosed(from, to)) {
            level.setBlock(p, Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (p.getY() == from.getY()) {
                level.setBlock(p.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Wheat ripe on wet farmland beside a channel of water. */
    private static void field(ServerLevel level, BlockPos o, int w, int d) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                BlockPos p = o.offset(x, 0, z);
                if (z == d / 2) {
                    level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                    continue;
                }
                level.setBlock(p, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7), Block.UPDATE_CLIENTS);
                level.setBlock(p.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** A pond two blocks deep, reeds on its bank. */
    private static void pond(ServerLevel level, BlockPos o, int r, int deep) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) {
                    continue;
                }
                for (int y = 0; y > -deep; y--) {
                    level.setBlock(o.offset(x, y, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void flowers(ServerLevel level, BlockPos c, int reach) {
        var random = net.minecraft.util.RandomSource.create(c.asLong());
        for (int i = 0; i < reach * 3; i++) {
            BlockPos p = c.offset(random.nextInt(2 * reach + 1) - reach, 1, random.nextInt(2 * reach + 1) - reach);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).is(Blocks.GRASS_BLOCK)) {
                level.setBlock(p, (random.nextBoolean() ? Blocks.SHORT_GRASS : random.nextBoolean() ? Blocks.POPPY : Blocks.DANDELION)
                        .defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Every block of a scene takes the shape its neighbours give it (fences join, grass under snow knows it). */
    private static void settle(ServerLevel level, Scene s) {
        AABB b = s.box();
        for (BlockPos p : BlockPos.betweenClosed((int) b.minX, (int) b.minY, (int) b.minZ, (int) b.maxX - 1, (int) b.maxY - 1, (int) b.maxZ - 1)) {
            BlockState was = level.getBlockState(p);
            if (was.isAir() || was.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) {
                continue;
            }
            BlockState now = Block.updateFromNeighbourShapes(was, level, p);
            if (now != was && !now.isAir()) {
                level.setBlock(p, now, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    private static BlockState[] snapshot(ServerLevel level, Scene s) {
        AABB b = s.box();
        int nx = (int) (b.maxX - b.minX), ny = (int) (b.maxY - b.minY), nz = (int) (b.maxZ - b.minZ);
        BlockState[] out = new BlockState[nx * ny * nz];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    out[i++] = level.getBlockState(p.set(b.minX + x, b.minY + y, b.minZ + z));
                }
            }
        }
        return out;
    }

    /** Every block of each scene against what it was; the chests, the decorations, the drops lying about. */
    private static void check(ServerLevel level) {
        StringBuilder report = new StringBuilder("Heal check, " + java.time.LocalDateTime.now() + "\n");
        for (Scene s : SCENES) {
            BlockState[] was = BEFORE.get(s.name()), now = snapshot(level, s);
            AABB b = s.box();
            int nx = (int) (b.maxX - b.minX), nz = (int) (b.maxZ - b.minZ);
            int same = 0, solid = 0;
            Map<String, Integer> kinds = new TreeMap<>();
            Map<String, List<String>> examples = new TreeMap<>();
            for (int i = 0; i < was.length; i++) {
                if (!was[i].isAir()) {
                    solid++;
                }
                if (was[i] == now[i]) {
                    same++;
                    continue;
                }
                String kind = was[i].getBlock().getName().getString() + " -> " + now[i].getBlock().getName().getString();
                kinds.merge(kind, 1, Integer::sum);
                List<String> some = examples.computeIfAbsent(kind, k -> new ArrayList<>());
                if (some.size() < 6) {
                    int x = i % nx, z = (i / nx) % nz, y = i / (nx * nz);
                    some.add("    " + (int) (b.minX + x) + " " + (int) (b.minY + y) + " " + (int) (b.minZ + z) + ": was " + was[i] + ", is " + now[i]);
                }
            }
            String line = String.format("%s (%s): %d of %d blocks as they were (%d differ; %d of them not air before)", s.name(), s.profile(),
                    same, was.length, was.length - same, solid);
            GroundBurst.LOGGER.info("Heal check: {}", line);
            report.append(line).append('\n');
            kinds.forEach((k, v) -> {
                report.append("  ").append(v).append(" x ").append(k).append('\n');
                examples.get(k).forEach(e -> report.append(e).append('\n'));
            });
            int frames = level.getEntitiesOfClass(ItemFrame.class, b).size(), stands = level.getEntitiesOfClass(ArmorStand.class, b).size();
            int items = level.getEntitiesOfClass(ItemEntity.class, b.inflate(8)).size();
            String extra = String.format("  item frames %d, armour stands %d (one of each a house), items lying about %d", frames, stands, items);
            GroundBurst.LOGGER.info("Heal check: {}", extra.trim());
            report.append(extra).append('\n');
        }
        for (Map.Entry<String, String> e : CHESTS.entrySet()) {
            BlockPos p = BlockPos.of(Long.parseLong(e.getKey()));
            String held = level.getBlockEntity(p) instanceof Container box
                    ? box.getItem(0).getCount() + " diamonds, " + box.getItem(5).getCount() + " bread, " + box.getItem(13).getCount() + " iron"
                    : "no chest";
            String line = "chest of " + e.getValue() + ": " + held + " (12, 20, 33 before)";
            GroundBurst.LOGGER.info("Heal check: {}", line);
            report.append(line).append('\n');
        }
        try {
            Path dir = level.getServer().getServerDirectory().resolve("heal");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("heal_check.txt"), report.toString());
        } catch (IOException e) {
            GroundBurst.LOGGER.error("Heal check: could not write the report", e);
        }
    }

    /** Flat land: bedrock, seventy layers of stone, dirt and grass (plains). */
    private static net.minecraft.world.level.levelgen.WorldDimensions flat(net.minecraft.core.HolderLookup.Provider registries) {
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        var flat = net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings.getDefault(biomes,
                        registries.lookupOrThrow(Registries.STRUCTURE_SET), registries.lookupOrThrow(Registries.PLACED_FEATURE))
                .withBiomeAndLayers(List.of(
                        new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(1, Blocks.BEDROCK),
                        new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(70, Blocks.STONE),
                        new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(3, Blocks.DIRT),
                        new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(1, Blocks.GRASS_BLOCK)),
                        java.util.Optional.empty(), biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS));
        return net.minecraft.world.level.levelgen.presets.WorldPresets.createFlatWorldDimensions(registries)
                .replaceOverworldGenerator(registries, new net.minecraft.world.level.levelgen.FlatLevelSource(flat));
    }
}
