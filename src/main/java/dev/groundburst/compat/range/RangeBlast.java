package dev.groundburst.compat.range;

import java.util.Locale;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.Blasts;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The test range's blast check ({@code gradlew runTestRange -PwithCreate -PrangeBlast=<profile>@<place>}): once the ships
 * are made, a blast of {@code profile} (a Ground Burst blast profile, e.g. nuclear_missile_warhead) goes off at a place
 * (airship, target_airship, boat, town, or x,y,z), every server tick slower than {@link #SLOW} ms is logged, and a
 * watchdog logs the server thread's stack whenever it has been stuck in one tick for {@link #STUCK} ms. Then the game
 * quits, saving the world as a player's quitting does.
 */
final class RangeBlast {
    static final int START = 200;
    private static final int RUN = 900;
    private static final long SLOW = 100, STUCK = 5000;
    private static volatile long tickStart, lastEnd = System.nanoTime();
    private static volatile boolean timing, watching;
    private static long slowest;
    private static int slowTicks, serverTicks;

    private RangeBlast() {}

    static void tick(Minecraft mc, int ticks, String spec) {
        if (ticks == START) {
            TestRange.onServer(mc, level -> setOff(level, spec));
        } else if (ticks > START && ticks < START + RUN && ticks % 40 == 0) {
            TestRange.onServer(mc, RangeShips::watch);
        } else if (ticks == START + RUN) {
            GroundBurst.LOGGER.info("Test range: blast check done: {} server ticks, {} slower than {} ms, the slowest {} ms; quitting",
                    serverTicks, slowTicks, SLOW, slowest);
            long quit = System.nanoTime();
            mc.stop();
            GroundBurst.LOGGER.info("Test range: quit asked after {} ms", (System.nanoTime() - quit) / 1_000_000);
        }
    }

    private static void setOff(ServerLevel level, String spec) {
        String[] parts = spec.split("@", 2);
        String profile = parts[0];
        Vec3 at = place(parts.length > 1 ? parts[1] : "airship");
        time(level.getServer());
        GroundBurst.LOGGER.info("Test range: blast check: {} at {}", profile, at);
        if (Blasts.detonate(level, at, Blasts.profile(profile), null) == null) {
            GroundBurst.LOGGER.warn("Test range: no blast profile {}", profile);
        }
    }

    private static Vec3 place(String name) {
        return switch (name) {
            case "airship" -> new Vec3(41.5, 28, 70.5);
            case "target_airship" -> new Vec3(57.5, 36, 130.5);
            case "boat" -> new Vec3(-83.5, 1, 47.5);
            case "town" -> Vec3.atBottomCenterOf(TestRange.RANGE).add(0, 1, 0);
            default -> {
                String[] xyz = name.split(",");
                yield new Vec3(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]));
            }
        };
    }

    /** Times every server tick from now on, and watches for one that does not end. */
    private static void time(MinecraftServer server) {
        if (timing) {
            return;
        }
        timing = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> tickStart = System.nanoTime());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            long now = System.nanoTime(), ms = (now - tickStart) / 1_000_000;
            lastEnd = now;
            serverTicks++;
            if (ms > SLOW) {
                slowTicks++;
                GroundBurst.LOGGER.info("Test range: server tick {} took {} ms", e.getServer().getTickCount(), ms);
            }
            slowest = Math.max(slowest, ms);
        });
        Thread server0 = server.getRunningThread();
        watching = true;
        Thread dog = new Thread(() -> {
            long told = 0;
            while (watching && server0.isAlive()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.nanoTime(), stuck = (now - Math.max(lastEnd, tickStart)) / 1_000_000;
                if (stuck > STUCK && (now - told) / 1_000_000 > STUCK) {
                    told = now;
                    StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "Test range: the server has been in one tick for %d ms:", stuck));
                    for (StackTraceElement f : server0.getStackTrace()) {
                        b.append("\n\tat ").append(f);
                    }
                    GroundBurst.LOGGER.warn(b.toString());
                }
            }
        }, "Test range watchdog");
        dog.setDaemon(true);
        dog.start();
    }
}
