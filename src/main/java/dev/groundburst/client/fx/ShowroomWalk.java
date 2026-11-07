package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development aid: a walk round the showroom's bunker doors timing every frame and every server tick ({@code -PshowroomWalk}
 * with {@code runShowroom}). It clicks as a player does (the client's own clicks, sent to the server): the open floor
 * hatch's lid six times, each Blast Door open and shut, the Pressure Doors, the hatches; walks along the wall and through,
 * looks round, walks the row; then writes each step's frames and ticks, and every slow one with the time it came, to
 * run/bench/showroom_walk_&lt;time&gt;.txt (with {@code -Pjfr=<name>} a flight recording to match them against).
 */
final class ShowroomWalk {
    /** A step: its name, how many client ticks it lasts, what it does as it starts. */
    private record Step(String name, int ticks, Consumer<Minecraft> act) {}

    private static volatile String current = "";
    private static volatile boolean timing;
    private static final Map<String, LongArrayList> FRAMES = new LinkedHashMap<>(), TICKS = new LinkedHashMap<>();
    private static final List<String> SLOW = new ArrayList<>();
    private static List<Step> steps;
    private static int index, left;
    private static long lastFrame, tickStart;

    private ShowroomWalk() {}

    static boolean enabled() {
        return Boolean.getBoolean("groundburst.showroom.walk");
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> frame());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> tickStart = System.nanoTime());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> serverTick());
    }

    private static void frame() {
        long now = System.nanoTime();
        if (timing && lastFrame != 0) {
            long d = now - lastFrame;
            synchronized (FRAMES) {
                FRAMES.computeIfAbsent(current, k -> new LongArrayList()).add(d);
                if (d > 40_000_000L) {
                    SLOW.add(String.format("%tT.%<tL  frame %6.1f ms  %s", System.currentTimeMillis(), d / 1e6, current));
                }
            }
        }
        lastFrame = now;
    }

    private static void serverTick() {
        if (!timing) {
            return;
        }
        long d = System.nanoTime() - tickStart;
        synchronized (FRAMES) {
            TICKS.computeIfAbsent(current, k -> new LongArrayList()).add(d);
            if (d > 50_000_000L) {
                SLOW.add(String.format("%tT.%<tL  tick  %6.1f ms  %s", System.currentTimeMillis(), d / 1e6, current));
            }
        }
    }

    /** A client tick of the walk; true once it is over and its report written. */
    static boolean tick(Minecraft mc, MinecraftServer server, int top) {
        if (steps == null) {
            steps = steps(server, top);
            index = -1;
            left = 0;
        }
        // As the user plays: sixteen chunks, a chunk the player changes rebuilt at once; frames as fast as they come.
        mc.options.renderDistance().set(16);
        mc.options.prioritizeChunkUpdates().set(net.minecraft.client.PrioritizeChunkUpdates.PLAYER_AFFECTED);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
        if (--left > 0) {
            return false;
        }
        if (++index >= steps.size()) {
            timing = false;
            report(mc);
            return true;
        }
        Step s = steps.get(index);
        current = s.name();
        timing = true;
        left = s.ticks();
        s.act().accept(mc);
        return false;
    }

    private static void teleport(MinecraftServer server, double x, double y, double z, float yaw, float pitch) {
        server.execute(() -> server.getPlayerList().getPlayers().getFirst().teleportTo(server.overworld(), x, y, z, java.util.Set.of(), yaw, pitch, true));
    }

    /** A click as the player makes it: on {@code pos}'s face {@code face} at {@code at}. */
    private static void click(Minecraft mc, BlockPos pos, Direction face, Vec3 at) {
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(at, face, pos, false));
        }
    }

    private static List<Step> steps(MinecraftServer server, int top) {
        int y = top + 1;
        List<Step> out = new ArrayList<>();
        out.add(new Step("settle at the doors", 300, mc -> teleport(server, 1.5, y, -12, 0, 10)));
        out.add(new Step("before the open hatch", 60, mc -> teleport(server, -1.3, y, -3.2, 180, 25)));
        // The user's clicks on the open hatch's lid (the lid stands above the hatch's block: the server turns them away).
        for (int i = 0; i < 6; i++) {
            out.add(new Step("clicks on the hatch's lid", 4, mc -> click(mc, new BlockPos(-2, top, -6), Direction.SOUTH, new Vec3(-1.28, top + 1.65, -5.0))));
        }
        out.add(new Step("after the lid clicks", 60, mc -> { }));
        // Each Blast Door clicked (two shut, two open), then again: open ones' leaves stand in front of their hinges.
        int[][] doors = {{-8, 0}, {-1, 1}, {4, 1}, {10, 0}};
        for (int round = 0; round < 2; round++) {
            for (int[] d : doors) {
                boolean open = (d[1] == 1) == (round == 0);
                out.add(new Step("blast doors clicked", 12, mc -> {
                    teleport(server, d[0] + 0.5, y, -4.5, 0, 10);
                }));
                out.add(new Step("blast doors clicked", 8, mc -> click(mc, new BlockPos(d[0], y + 1, open ? -1 : 0), Direction.NORTH,
                        new Vec3(d[0] + 0.5, y + 1.5, open ? -1.0 : 0.0))));
            }
            out.add(new Step("blast doors moving", 90, mc -> teleport(server, 1.5, y, -9, 0, 10)));
        }
        // The Pressure Doors (one shut, one open), twice.
        for (int round = 0; round < 2; round++) {
            for (int x : new int[] {-5, 1}) {
                out.add(new Step("pressure doors clicked", 6, mc -> teleport(server, x + 0.5, y, -2.5, 0, 10)));
                out.add(new Step("pressure doors clicked", 20, mc -> click(mc, new BlockPos(x, y, 0), Direction.NORTH, new Vec3(x + 0.5, y + 0.5, 0.0))));
            }
        }
        // The floor hatches, then the ceiling hatch, each opened and shut.
        out.add(new Step("hatches clicked", 6, mc -> teleport(server, -3.5, y, -8.5, 0, 50)));
        for (int i = 0; i < 2; i++) {
            out.add(new Step("hatches clicked", 20, mc -> click(mc, new BlockPos(-5, top, -6), Direction.UP, new Vec3(-4.5, top + 1.0, -5.5))));
            out.add(new Step("hatches clicked", 20, mc -> click(mc, new BlockPos(-2, top, -6), Direction.UP, new Vec3(-1.5, top + 0.97, -5.95))));
        }
        out.add(new Step("hatches clicked", 6, mc -> teleport(server, 7.5, y, -10, 0, -40)));
        for (int i = 0; i < 2; i++) {
            out.add(new Step("hatches clicked", 20, mc -> click(mc, new BlockPos(7, y + 3, -8), Direction.DOWN, new Vec3(7.5, y + 3.0, -7.5))));
        }
        // Along the wall and back, looking at it; round about; then the row from end to end.
        for (int i = 0; i <= 112; i++) {
            double x = -13.5 + i * 0.25;
            out.add(new Step("walking along the wall", 1, mc -> teleport(server, x, y, -3, 0, 5)));
        }
        for (int i = 0; i < 100; i++) {
            float yaw = i * 3.6f;
            out.add(new Step("looking round at the doors", 1, mc -> teleport(server, 0.5, y, -5, yaw, 5)));
        }
        for (int i = 0; i <= 240; i++) {
            double x = -10 + i * 0.5;
            out.add(new Step("walking the row", 1, mc -> teleport(server, x, y, -14, 0, 10)));
        }
        out.add(new Step("standing at the yard", 100, mc -> { }));
        return out;
    }

    private static void report(Minecraft mc) {
        StringBuilder sb = new StringBuilder("Showroom walk: frames and server ticks by step (ms)\n\n");
        sb.append(String.format("%-30s %6s %7s %7s %7s %7s %5s | %6s %7s %7s%n", "step", "frames", "mean", "p50", "p99", "max", ">50", "ticks", "mean", "max"));
        synchronized (FRAMES) {
            java.util.Set<String> names = new java.util.LinkedHashSet<>(FRAMES.keySet());
            names.addAll(TICKS.keySet());
            for (String name : names) {
                long[] f = sorted(FRAMES.get(name)), t = sorted(TICKS.get(name));
                long over = java.util.Arrays.stream(f).filter(v -> v > 50_000_000L).count();
                sb.append(String.format("%-30s %6d %7.2f %7.2f %7.2f %7.1f %5d | %6d %7.2f %7.1f%n", name, f.length, mean(f), at(f, 0.5), at(f, 0.99),
                        at(f, 1), over, t.length, mean(t), at(t, 1)));
            }
            sb.append("\nSlow frames (over 40 ms) and ticks (over 50 ms), as they came:\n");
            SLOW.forEach(s -> sb.append(s).append('\n'));
        }
        try {
            java.nio.file.Path dir = mc.gameDirectory.toPath().resolve("bench");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve("showroom_walk_" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
            java.nio.file.Files.writeString(file, sb.toString());
            GroundBurst.LOGGER.info("Showroom walk written to {}\n{}", file, sb);
        } catch (java.io.IOException e) {
            GroundBurst.LOGGER.error("Showroom walk: could not write the report", e);
        }
    }

    private static long[] sorted(LongArrayList list) {
        long[] a = list == null ? new long[0] : list.toLongArray();
        java.util.Arrays.sort(a);
        return a;
    }

    private static double mean(long[] a) {
        return a.length == 0 ? 0 : java.util.Arrays.stream(a).average().orElse(0) / 1e6;
    }

    private static double at(long[] a, double q) {
        return a.length == 0 ? 0 : a[(int) Math.min(a.length - 1, Math.round(q * (a.length - 1)))] / 1e6;
    }
}
