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
                new Scene("meltdown_dusk", "reactor_meltdown", 40, 14, List.of(30, 200), "13000"),
                new Scene("suits", "suits", 3, 12, List.of(15)),
                new Scene("suits_back", "suits_back", 3, 12, List.of(15)),
                new Scene("grenades", "grenades", 7, 18, List.of(4, 12, 30, 62, 74, 90, 130)),
                new Scene("chemical", "chemical", 7, 18, List.of(20, 60, 120)))) {
            if (only.isEmpty() || s.name().startsWith(only)) {
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

    /** The chemical grenades: a poison gas cloud, a toxin splash, and a stick of dynamite close up between them. */
    private static void chemical(ServerLevel level, int x, int ground) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.GAS_GRENADE.get(), new Vec3(x + 2, ground + 0.05, 2.5), Vec3.ZERO, 5);
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.TOXIN_GRENADE.get(), new Vec3(x - 1, ground + 4, -2.5),
                new Vec3(0, -0.2, 0), -1);
        throwOne(level, player, dev.groundzero.content.ordnance.GZOrdnance.DYNAMITE.get(), new Vec3(x - 4, ground + 0.05, 0.5), Vec3.ZERO, 4000);
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
                player.teleportTo(level, x - s.distance() + 0.5, ground + 1.6, 0.5, java.util.Set.of(), -90, s.pitch(), true);
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
                if (s.profile().startsWith("suits")) {
                    suits(level, x, ground, s.profile().endsWith("back"));    // armour stands wearing the suits, no blast
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
