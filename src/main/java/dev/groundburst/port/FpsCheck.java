package dev.groundburst.port;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Pair;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.StructureSpawnerItem;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * This branch's check of the frame rate at a village smithy ({@code gradlew runFpsCheck}; a player's report: "significant
 * FPS drops at the smithy" on 1.20.1): makes a normal world, finds a village with a weaponsmith (the smithy with a pool of
 * lava), and times every frame and server tick for twenty seconds at four places, as a player would stand there: open
 * ground away from the village, the village's middle, inside the smithy looking at its lava, and the smithy again after a
 * minute more (fires the lava sets may have grown). At the smithy it counts the fires and the blocks that changed round
 * it while it timed. The log's "Fps check:" lines; the smithy photographed into {@code run/screenshots/fps_smithy.png}.
 * {@code -Dgroundburst.fpsCheck.view} sets the view distance (12).
 * <p>With {@code -Dgroundburst.fpsCheck.machines} it times the mod's machines instead, in a flat world: every structure
 * built formed and every machine of a block or two set down, each farther from the next than the view reaches (so each is
 * drawn alone), each looked at from a few blocks off for five seconds after an empty view's five (each photographed,
 * {@code fps_m_<name>.png}), so a machine that costs far more than it should stands out.
 */
@EventBusSubscriber(modid = GroundBurst.MODID, value = Dist.CLIENT)
public final class FpsCheck {
    private static final boolean ON = Boolean.getBoolean("groundburst.fpsCheck");
    private static final boolean MACHINES = Boolean.getBoolean("groundburst.fpsCheck.machines");
    private static final String WORLD = "fps_check";
    private static final long SEED = Long.getLong("groundburst.fpsCheck.seed", 20261111L);
    private static final int VIEW = Integer.getInteger("groundburst.fpsCheck.view", 12);
    /** Ticks to let the ground load and draw at a place before timing, the ticks timed, the wait before the smithy's second look. */
    private static final int SETTLE = 300, MEASURE = 400, LATE = 1200;
    /** A machine's: a short settle and five seconds timed; machines stand farther apart than the view, each seen alone. */
    private static final int MACHINE_SETTLE = 100, MACHINE_MEASURE = 100, APART = 16 * (VIEW + 6);

    private record Spot(String name, Vec3 eye, Vec3 at, int settle, int measure) {
        Spot(String name, Vec3 eye, Vec3 at, int settle) {
            this(name, eye, at, settle, MEASURE);
        }
    }

    private static boolean started, finished;
    private static int ticks = -1, shown, waited;
    private static volatile @Nullable List<Spot> spots;
    private static volatile @Nullable BoundingBox smithy;
    private static volatile boolean timing;
    private static long lastFrame;
    private static volatile long tickStart;
    private static final LongArrayList FRAMES = new LongArrayList(), TICKS = new LongArrayList();
    private static @Nullable Map<BlockPos, BlockState> before;

    private FpsCheck() {}

    @SubscribeEvent
    static void frame(RenderFrameEvent.Pre event) {
        long now = System.nanoTime();
        if (ON && timing && lastFrame != 0) {
            synchronized (FRAMES) {
                FRAMES.add(now - lastFrame);
            }
        }
        lastFrame = now;
    }

    @SubscribeEvent
    static void serverTickStart(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
    }

    @SubscribeEvent
    static void serverTickEnd(ServerTickEvent.Post event) {
        if (ON && timing) {
            synchronized (TICKS) {
                TICKS.add(System.nanoTime() - tickStart);
            }
        }
    }

    @SubscribeEvent
    static void clientTick(ClientTickEvent.Post event) {
        if (!ON || finished) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.setScreen(null);
        }
        if (!started && mc.getOverlay() == null && mc.level == null && mc.screen != null) {
            started = true;
            GroundBurst.LOGGER.info("Fps check: making the world (seed {}, view {})", SEED, VIEW);
            try (var access = mc.getLevelSource().createAccess(WORLD)) {
                access.deleteLevel();
            } catch (Exception ignored) {
                // none yet
            }
            LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(),
                    WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(SEED, !MACHINES, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(MACHINES ? WorldPresets.FLAT : WorldPresets.NORMAL)
                            .value().createWorldDimensions(),
                    mc.screen);
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        mc.options.renderDistance().set(VIEW);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.hideGui = true;
        ticks++;
        if (ticks == 40) {
            command(mc, "gamemode spectator");
            command(mc, "gamerule doDaylightCycle false");
            command(mc, "gamerule doWeatherCycle false");
            command(mc, "time set 6000");
            command(mc, "weather clear");
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    try {
                        spots = MACHINES ? machines(server) : search(server);
                    } catch (RuntimeException e) {
                        GroundBurst.LOGGER.error("Fps check: failed", e);
                        spots = List.of();
                    }
                });
            }
        }
        List<Spot> found = spots;
        if (found == null || ticks < 60) {
            return;
        }
        if (shown >= found.size()) {
            finished = true;
            GroundBurst.LOGGER.info("Fps check: done");
            mc.stop();
            return;
        }
        Spot s = found.get(shown);
        if (waited == 0) {
            Vec3 d = s.at.subtract(s.eye);
            float yaw = (float) -Math.toDegrees(Math.atan2(d.x, d.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
            command(mc, String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", s.eye.x, s.eye.y, s.eye.z, yaw, pitch));
        }
        waited++;
        if (waited == s.settle) {
            synchronized (FRAMES) {
                FRAMES.clear();
            }
            synchronized (TICKS) {
                TICKS.clear();
            }
            timing = true;
            if (s.name.startsWith("smithy")) {
                onServer(mc, level -> before = snapshot(level));
            }
        } else if (waited == s.settle + s.measure) {
            timing = false;
            report(mc, s);
            if (s.name.equals("smithy") || MACHINES) {
                Screenshot.grab(mc.gameDirectory, MACHINES ? "fps_m_" + s.name + ".png" : "fps_smithy.png", mc.getMainRenderTarget(),
                        m -> GroundBurst.LOGGER.info("Fps check: {}", m.getString()));
            }
            shown++;
            waited = 0;
        }
    }

    /** One place's frames and ticks, and at the smithy its fires and the blocks that changed while it was timed. */
    private static void report(Minecraft mc, Spot s) {
        long[] f, t;
        synchronized (FRAMES) {
            f = FRAMES.toLongArray();
        }
        synchronized (TICKS) {
            t = TICKS.toLongArray();
        }
        String frames = stats(f), server = stats(t);
        double fps = f.length == 0 ? 0 : f.length / (java.util.Arrays.stream(f).sum() / 1e9);
        if (s.name.startsWith("smithy")) {
            onServer(mc, level -> {
                Map<BlockPos, BlockState> after = snapshot(level);
                int changed = 0, fires = 0;
                for (Map.Entry<BlockPos, BlockState> e : after.entrySet()) {
                    if (before != null && before.get(e.getKey()) != e.getValue()) {
                        changed++;
                    }
                    if (e.getValue().is(BlockTags.FIRE)) {
                        fires++;
                    }
                }
                GroundBurst.LOGGER.info("Fps check: {}: frames ms mean/p95/max {} ({} fps, {} frames); server tick ms {}; fires round it {}, blocks changed {}",
                        s.name, frames, Math.round(fps), f.length, server, fires, changed);
            });
        } else {
            GroundBurst.LOGGER.info("Fps check: {}: frames ms mean/p95/max {} ({} fps, {} frames); server tick ms {}", s.name, frames,
                    Math.round(fps), f.length, server);
        }
    }

    private static String stats(long[] nanos) {
        if (nanos.length == 0) {
            return "-";
        }
        long[] sorted = nanos.clone();
        java.util.Arrays.sort(sorted);
        double mean = java.util.Arrays.stream(sorted).average().orElse(0) / 1e6;
        double p95 = sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.95))] / 1e6;
        double max = sorted[sorted.length - 1] / 1e6;
        return String.format(Locale.ROOT, "%.2f/%.2f/%.1f", mean, p95, max);
    }

    /** The blocks round the smithy (its box and six blocks on), as they are now. */
    private static Map<BlockPos, BlockState> snapshot(ServerLevel level) {
        Map<BlockPos, BlockState> out = new HashMap<>();
        BoundingBox b = smithy;
        if (b != null) {
            for (BlockPos p : BlockPos.betweenClosed(b.minX() - 6, b.minY() - 2, b.minZ() - 6, b.maxX() + 6, b.maxY() + 6, b.maxZ() + 6)) {
                out.put(p.immutable(), level.getBlockState(p));
            }
        }
        return out;
    }

    /** A village with a weaponsmith that has its lava, and the four places to stand (on the server's thread). */
    private static List<Spot> search(MinecraftServer server) {
        ServerLevel level = server.overworld();
        HolderSet<Structure> villages = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(StructureTags.VILLAGE).orElseThrow();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            BlockPos origin = new BlockPos((i % 5 - 2) * 1500, 0, (i / 5 - 2) * 1500);
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, villages, origin, 64, false);
            if (found == null || !seen.add(new net.minecraft.world.level.ChunkPos(found.getFirst()).toLong())) {
                continue;
            }
            BlockPos at = found.getFirst();
            StructureStart start = level.structureManager().getStartForStructure(SectionPos.of(at), found.getSecond().value(),
                    level.getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS));
            if (start == null || !start.isValid()) {
                continue;
            }
            for (StructurePiece piece : start.getPieces()) {
                if (!(piece instanceof PoolElementStructurePiece pool) || !pool.getElement().toString().contains("weaponsmith")) {
                    continue;
                }
                BoundingBox b = piece.getBoundingBox();
                for (int cx = (b.minX() >> 4) - 1; cx <= (b.maxX() >> 4) + 1; cx++) {
                    for (int cz = (b.minZ() >> 4) - 1; cz <= (b.maxZ() >> 4) + 1; cz++) {
                        level.getChunk(cx, cz);
                    }
                }
                BlockPos lava = null;
                for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                    if (level.getBlockState(p).is(Blocks.LAVA)) {
                        lava = p.immutable();
                        break;
                    }
                }
                if (lava == null) {
                    continue;
                }
                smithy = b;
                BoundingBox v = start.getBoundingBox();
                Vec3 lavaAt = Vec3.atCenterOf(lava);
                Vec3 inside = new Vec3((b.minX() + b.maxX() + 1) / 2.0, lava.getY() + 2.6, (b.minZ() + b.maxZ() + 1) / 2.0);
                if (inside.distanceTo(lavaAt) < 1.5) {
                    inside = inside.add(2, 0, 0);
                }
                int vx = (v.minX() + v.maxX()) / 2, vz = (v.minZ() + v.maxZ()) / 2;
                int vy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, vx, vz);
                int fx = v.maxX() + 220, fz = vz;
                int fy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, fx, fz);
                GroundBurst.LOGGER.info("Fps check: a weaponsmith ({}) at {} in the village at {}, its lava at {}", pool.getElement(), b, v, lava);
                return List.of(new Spot("field", new Vec3(fx + 0.5, fy + 2.6, fz + 0.5), new Vec3(fx + 40.5, fy, fz + 0.5), SETTLE),
                        new Spot("village", new Vec3(vx + 0.5, vy + 2.6, vz + 0.5), new Vec3(vx + 40.5, vy, vz + 0.5), SETTLE),
                        new Spot("smithy", inside, lavaAt, SETTLE),
                        new Spot("smithy_late", inside, lavaAt, LATE));
            }
        }
        GroundBurst.LOGGER.info("Fps check: no weaponsmith with its lava found: WRONG");
        return List.of();
    }

    /** Every structure built formed and every machine of a block or two set down, along a row; the places to look at each. */
    private static List<Spot> machines(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<Spot> out = new java.util.ArrayList<>();
        // Forty blocks up, clear of the flat world's few blocks of ground (a silo reaches far down).
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) + 40, x = 0;
        out.add(spot("empty", -40, y, 0, -40, y, 0));
        for (MultiblockDefinition def : GBMultiblocks.all()) {
            int left = 0;                                       // how far west of its controller the structure reaches
            for (MultiblockDefinition.Cell cell : StructureSpawnerItem.buildOrder(def, BlockPos.ZERO, Direction.NORTH)) {
                left = Math.max(left, -cell.pos().getX());
            }
            BlockPos c = new BlockPos(x + left, y, 0);
            int x0 = Integer.MAX_VALUE, y0 = x0, z0 = x0, x1 = Integer.MIN_VALUE, y1 = x1, z1 = x1;
            for (MultiblockDefinition.Cell cell : StructureSpawnerItem.buildOrder(def, c, Direction.NORTH)) {
                BlockState state = cell.key() == '.' ? Blocks.AIR.defaultBlockState() : def.legend().get(cell.key()).preview().get();
                if (cell.key() == MultiblockDefinition.CONTROLLER) {
                    state = state.setValue(MachineBlock.FACING, Direction.NORTH);
                }
                level.setBlockAndUpdate(cell.pos(), state);
                BlockPos p = cell.pos();
                x0 = Math.min(x0, p.getX());
                y0 = Math.min(y0, p.getY());
                z0 = Math.min(z0, p.getZ());
                x1 = Math.max(x1, p.getX());
                y1 = Math.max(y1, p.getY());
                z1 = Math.max(z1, p.getZ());
            }
            if (level.getBlockEntity(c) instanceof MultiblockController controller) {
                controller.revalidate();
            }
            out.add(spot(def.id().getPath(), x0, y0, z0, x1, y1, z1));
            x = x1 + APART;
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(GroundBurst.MODID) || !(block instanceof EntityBlock entityBlock)
                    || !(entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) instanceof MachineBlockEntity machine)
                    || machine instanceof MultiblockController) {
                continue;
            }
            BlockPos p = new BlockPos(x, y, 0);
            BlockState state = block.defaultBlockState();
            if (state.hasProperty(MachineBlock.FACING)) {
                state = state.setValue(MachineBlock.FACING, Direction.NORTH);
            }
            level.setBlockAndUpdate(p, state);
            block.setPlacedBy(level, p, state, null, new ItemStack(block));        // (a second half where it has one)
            out.add(spot(BuiltInRegistries.BLOCK.getKey(block).getPath(), x, y, 0, x + 1, y + 1, 1));
            x += APART;
        }
        GroundBurst.LOGGER.info("Fps check: {} machines and structures set out", out.size() - 1);
        return out;
    }

    /** Where to look at a thing in the box from: off its north face (its front), a little above. */
    private static Spot spot(String name, int x0, int y0, int z0, int x1, int y1, int z1) {
        Vec3 mid = new Vec3((x0 + x1 + 1) / 2.0, (y0 + y1 + 1) / 2.0, (z0 + z1 + 1) / 2.0);
        double size = Math.max(x1 - x0 + 1, Math.max(y1 - y0 + 1, z1 - z0 + 1));
        Vec3 eye = new Vec3(mid.x, mid.y + size * 0.35 + 1.5, z0 - size * 0.9 - 3);
        return new Spot(name, eye, mid, MACHINE_SETTLE, MACHINE_MEASURE);
    }

    private static void onServer(Minecraft mc, java.util.function.Consumer<ServerLevel> job) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> job.accept(server.overworld()));
        }
    }

    private static void command(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }
}
