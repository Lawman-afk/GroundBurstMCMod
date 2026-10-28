package dev.groundburst.client.fx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import dev.groundburst.GroundBurst;
import dev.groundburst.world.structure.GBStructures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid for building the world's sites (DESIGN.md §22.2): with {@code -Dgroundburst.siteShots=<sites>} (the
 * {@code siteShots} Gradle run: {@code -Psite=radar_outpost,test_site} or {@code all}, {@code -PsiteVariant=0}) the
 * client makes a flat creative world, places each site on the grass as generation would place it (turned no way), and
 * photographs it at noon from its four corners, from the ground on two sides and from above, into
 * {@code run/screenshots/site_<name>_<variant>_<view>.png}; then quits. Each picture waits for the ground round the site
 * to come in and the section builder to go quiet, as the world survey's do. {@code board} in place of a site shows the
 * Construction set in use ({@link ConstructionBoard}).
 */
public final class SitePhotographer {
    /** Every site there is, in the order they are photographed with {@code all}. */
    private static final List<String> SITES = List.of("radar_outpost", "test_site", "abandoned_silo", "command_bunker",
            "crashed_missile", "waste_storage", "exotic_research_station");
    /** Ticks to wait at least once there, the ticks the section builder must have been idle, the most to wait. */
    private static final int SETTLE = 40, STABLE = 25, MOST = 900;
    /** Sites stand this far apart along x, so one's chunks never hold another's. */
    private static final int SPACING = 512;

    /** A camera: the way it faces (Minecraft's yaw: 0 south, 90 west), how far down it looks, how far off as a share of the site's size. */
    private record View(String name, float yaw, float pitch, double reach, boolean eye) {}

    private static final List<View> VIEWS = List.of(
            new View("ne", 45, 30, 1.6, false), new View("se", 135, 30, 1.6, false),
            new View("sw", 225, 30, 1.6, false), new View("nw", 315, 30, 1.6, false),
            new View("eye_s", 165, 0, 1.15, true), new View("eye_n", 345, 0, 1.15, true),
            new View("top", 0, 72, 1.7, false));

    private record Shot(String file, Vec3 at, float yaw, float pitch, BlockPos look) {}

    private static final Deque<String> TODO = new ArrayDeque<>();
    /** Filled on the server thread as a site is placed, taken on the client's. */
    private static final Deque<Shot> SHOTS = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static volatile boolean placing;
    private static boolean worldMade;
    private static int index, variant, there, stable, idle;
    private static Shot current;

    private SitePhotographer() {}

    public static boolean enabled() {
        return !System.getProperty("groundburst.siteShots", "").isEmpty();
    }

    public static void register() {
        if (!enabled()) {
            return;
        }
        String which = System.getProperty("groundburst.siteShots");
        TODO.addAll(which.equals("all") ? SITES : List.of(which.split(",")));
        variant = Integer.getInteger("groundburst.siteShots.variant", 0);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;              // another window taking the focus must not pause the shots
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.gui.setScreen(null);
        }
        if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        }
        if (mc.level == null && !worldMade && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            try {
                var settings = new net.minecraft.world.level.LevelSettings("Site shots", GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = net.minecraft.util.FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "site_shots", "");
                // Flat, and no structures: no village beside a site in its pictures.
                mc.createWorldOpenFlows().createFreshLevel(folder, settings, new net.minecraft.world.level.levelgen.WorldOptions(0L, false, false),
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createTestWorldDimensions, mc.gui.screen());
            } catch (java.io.IOException e) {
                GroundBurst.LOGGER.error("Site shots: could not make a world", e);
            }
            return;
        }
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        mc.options.renderDistance().set(16);
        mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();                           // no hand or hotbar in the pictures
        }
        if (current == null) {
            current = SHOTS.poll();
            if (current == null) {
                if (placing) {
                    return;                                // the next site is being placed
                }
                String name = TODO.poll();
                if (name == null) {
                    if (++idle == 20) {
                        GroundBurst.LOGGER.info("Site shots done");
                        mc.stop();
                    }
                    return;
                }
                placing = true;
                int x = index++ * SPACING;
                server.execute(() -> place(server.overworld(), name, x));
                return;
            }
            there = 0;
            stable = 0;
            Shot s = current;
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(server.overworld(), s.at().x, s.at().y, s.at().z, java.util.Set.of(), s.yaw(), s.pitch(), true);
            });
            return;
        }
        Shot s = current;
        if (mc.player.position().distanceTo(s.at()) > 1.5) {
            return;                                        // the camera is on its way
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
            if (there >= MOST) {
                GroundBurst.LOGGER.warn("Site shots: {} taken before the ground had all come in", s.file());
            }
            Screenshot.grab(mc.gameDirectory, s.file(), mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> GroundBurst.LOGGER.info("Site shot: {}", msg.getString()));
            current = null;
        }
    }

    /** On the server: the site on the grass at {@code x}, and its pictures queued. */
    private static void place(ServerLevel level, String name, int x) {
        try {
            var commands = level.getServer().getCommands();
            var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
            commands.performPrefixedCommand(source, "time set noon");
            commands.performPrefixedCommand(source, "gamerule advance_time false");
            commands.performPrefixedCommand(source, "gamerule advance_weather false");
            commands.performPrefixedCommand(source, "gamerule spawn_mobs false");
            commands.performPrefixedCommand(source, "weather clear");
            commands.performPrefixedCommand(source, "kill @e[type=!player]");
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.SPECTATOR);
            for (int cx = (x - 96) >> 4; cx <= (x + 96) >> 4; cx++) {
                for (int cz = -96 >> 4; cz <= 96 >> 4; cz++) {
                    level.getChunk(cx, cz);                // the ground the site and its views stand on
                }
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, 0) - 1;     // the land's top block, as generation reads it
            // The land the site stands on where it generates: sand for the test site, grass elsewhere (the flat world is sand).
            var land = (name.equals("test_site") ? net.minecraft.world.level.block.Blocks.SAND
                    : net.minecraft.world.level.block.Blocks.GRASS_BLOCK).defaultBlockState();
            for (BlockPos p : BlockPos.betweenClosed(x - 96, top, -96, x + 96, top, 96)) {
                level.setBlock(p, land, 2);
            }
            // The biome too, so the grass is the colour it is where the site generates (the flat world is a desert).
            commands.performPrefixedCommand(source, "gamerule max_block_modifications 4000000");
            commands.performPrefixedCommand(source, "fillbiome " + (x - 96) + " " + (top - 8) + " -96 " + (x + 96) + " " + (top + 64)
                    + " 96 minecraft:" + (name.equals("test_site") ? "desert" : "plains"));
            BlockPos origin = new BlockPos(x, top, 0);
            if (name.equals("exotic_research_station")) {
                origin = origin.above(14);                 // it floats over an island in the End
            }
            BoundingBox box = name.equals("board") ? ConstructionBoard.build(level, origin)
                    : GBStructures.preview(level, name, variant, origin, false, null);
            queue(name, box, top);
        } catch (RuntimeException e) {
            GroundBurst.LOGGER.error("Site shots: could not place {}", name, e);
        } finally {
            placing = false;
        }
    }

    private static void queue(String name, BoundingBox box, int ground) {
        double cx = (box.minX() + box.maxX() + 1) / 2.0, cz = (box.minZ() + box.maxZ() + 1) / 2.0;
        int above = Math.max(4, box.maxY() - ground);
        double cy = ground + 1 + above * 0.4;
        double radius = 0.5 * Math.sqrt(Math.pow(box.getXSpan(), 2) + Math.pow(box.getZSpan(), 2) + Math.pow(above, 2));
        BlockPos look = BlockPos.containing(cx, cy, cz);
        for (View v : VIEWS) {
            double d = v.reach() * radius + 6;
            double yaw = Math.toRadians(v.yaw());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);                       // the way the camera faces, flat
            Vec3 at;
            float pitch;
            if (v.eye()) {
                at = new Vec3(cx - fx * d, ground + 2.62, cz - fz * d);           // standing on the grass
                pitch = (float) -Math.toDegrees(Math.atan2(cy - at.y, d));        // looking up at the site's middle height
            } else {
                double p = Math.toRadians(v.pitch());
                at = new Vec3(cx - fx * d * Math.cos(p), cy + d * Math.sin(p), cz - fz * d * Math.cos(p));
                pitch = v.pitch();
            }
            SHOTS.add(new Shot("site_" + name + "_" + variant + "_" + v.name() + ".png", at, v.yaw(), pitch, look));
        }
    }
}
