package dev.groundburst.client.fx;

import java.util.List;

import dev.groundburst.GroundBurst;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid: a world to walk round the cosmetic blocks in (DESIGN.md §22.3 to §22.6). With
 * {@code -Dgroundburst.showroom=true} (the {@code showroom} Gradle run) the client makes a flat creative world, "Ground
 * Burst Showroom" (run/saves/ground_burst_showroom), lays out the four sets the photographer's boards build (the bunker
 * doors, the command room, the bunker fittings, the yard) along a path, each with signs naming it and saying what to
 * click, sets the spawn on the path in front of them, takes a picture down the row and of each, and quits (the world
 * saved). The time runs (the searchlight's beam shows at night); no mobs, no weather.
 */
public final class Showroom {
    /** The four sets: each board, its middle along the path (x), its signs' lines (name, then what to do). */
    private record Area(String name, int x, List<String> name4, List<String> hints) {}

    private static final List<Area> AREAS = List.of(
            new Area("doors", 0, List.of("", "Bunker doors", "", ""), List.of("Click a door,", "or power it,", "to open it.", "")),
            new Area("room", 34, List.of("", "Command room", "", ""), List.of("Click tape drives,", "open the cabinet;", "the consoles", "join up.")),
            new Area("fittings", 68, List.of("", "Bunker", "fittings", ""), List.of("Levers light", "the lamps. Click", "valve, fuse box,", "lockers, bunks.")),
            new Area("yard", 102, List.of("", "The yard", "", ""), List.of("Click the", "searchlight:", "best at night.", "Open the crates.")));
    /** The path's middle line (z), the spawn's place on it. */
    private static final int PATH_Z = -14;
    private static boolean worldMade, built;
    private static int waited;

    private Showroom() {}

    public static boolean enabled() {
        return Boolean.getBoolean("groundburst.showroom");
    }

    public static void register() {
        if (enabled()) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
            if (ShowroomWalk.enabled()) {
                ShowroomWalk.register();
            }
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
                var settings = new net.minecraft.world.level.LevelSettings("Ground Burst Showroom", GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = DevWorlds.fresh(mc, "ground_burst_showroom");
                mc.createWorldOpenFlows().createFreshLevel(folder, settings, new net.minecraft.world.level.levelgen.WorldOptions(0L, false, false),
                        SitePhotographer::deepFlat, mc.gui.screen());
            } catch (java.io.IOException e) {
                GroundBurst.LOGGER.error("Showroom: could not make a world", e);
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
        // Or the walk round the doors, timed (-PshowroomWalk), instead of the pictures.
        if (ShowroomWalk.enabled()) {
            if (++waited > 200 && ShowroomWalk.tick(mc, server, mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, PATH_Z) - 1)) {
                mc.stop();
            }
            return;
        }
        // Pictures down the row and of each set (eight seconds at each, for the ground and its light to come in), back to
        // the spawn, quit: the world is saved as the game stops.
        if (++waited < 300 || waited % 160 != 0) {
            return;
        }
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, PATH_Z) - 1;
        List<View> views = views(top);
        int step = (waited - 300) / 160;
        if (step > 0 && step <= views.size()) {
            Screenshot.grab(mc.gameDirectory, "showroom_" + views.get(step - 1).name() + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> GroundBurst.LOGGER.info("Showroom: {}", msg.getString()));
        }
        if (step <= views.size()) {
            View v = step < views.size() ? views.get(step) : new View("spawn", new Vec3(51.5, top + 1, PATH_Z - 2.5), 10);
            server.execute(() -> server.getPlayerList().getPlayers().getFirst()
                    .teleportTo(server.overworld(), v.at().x, v.at().y, v.at().z, java.util.Set.of(), 0, v.pitch(), true));
            return;
        }
        GroundBurst.LOGGER.info("Showroom built");
        mc.stop();
    }

    /** A picture: its name, where it is taken from (looking south, down onto the sets), how far down it looks. */
    private record View(String name, Vec3 at, float pitch) {}

    private static List<View> views(int top) {
        List<View> out = new java.util.ArrayList<>();
        out.add(new View("row", new Vec3(51.5, top + 18, PATH_Z - 20), 28));
        for (Area a : AREAS) {
            out.add(new View(a.name(), new Vec3(a.x() + 0.5, top + 3.5, PATH_Z - 3), 16));
        }
        return out;
    }

    /** On the server: the rules, the four sets, the path and signs, the spawn. */
    private static void build(ServerLevel level) {
        var commands = level.getServer().getCommands();
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        for (String c : List.of("time set 1000", "gamerule advance_weather false", "weather clear", "gamerule spawn_mobs false",
                "difficulty peaceful", "kill @e[type=!player]")) {
            commands.performPrefixedCommand(source, c);
        }
        for (int cx = -48 >> 4; cx <= 160 >> 4; cx++) {
            for (int cz = -64 >> 4; cz <= 48 >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) - 1;
        for (Area a : AREAS) {
            BlockPos o = new BlockPos(a.x(), top, 0);
            switch (a.name()) {
                case "doors" -> BunkerDoorBoard.build(level, o);
                case "room" -> CommandRoomBoard.build(level, o);
                case "fittings" -> FittingsBoard.build(level, o);
                default -> YardBoard.build(level, o);
            }
            sign(level, new BlockPos(a.x() + 1, top + 1, PATH_Z + 2), a.name4());            // (looking south, +x is on the left)
            sign(level, new BlockPos(a.x() - 1, top + 1, PATH_Z + 2), a.hints());
        }
        // At the spawn, what a redstone signal does to the props.
        sign(level, new BlockPos(51, top + 1, PATH_Z + 2), List.of("Redstone holds", "the props open", "or on: try a", "lever by them."));
        // The path from the first set to the last.
        for (BlockPos p : BlockPos.betweenClosed(-16, top, PATH_Z - 1, 116, top, PATH_Z + 1)) {
            level.setBlock(p, Blocks.DIRT_PATH.defaultBlockState(), 2);
        }
        commands.performPrefixedCommand(source, "setworldspawn 51 " + (top + 1) + " " + (PATH_Z - 2));
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);
        GroundBurst.LOGGER.info("Showroom: the four sets laid out on the grass at y {}", top + 1);
    }

    /** A standing oak sign facing the path (north), four lines on its front. */
    private static void sign(ServerLevel level, BlockPos at, List<String> lines) {
        level.setBlock(at, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8), 3);
        if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            SignText.Mutable text = sign.getText(SignTextSlot.FRONT).asMutable();
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                text.setLine(i, Component.literal(lines.get(i)));
            }
            sign.setText(text.asImmutable(), SignTextSlot.FRONT);
            sign.setChanged();
            level.sendBlockUpdated(at, sign.getBlockState(), sign.getBlockState(), 3);
        }
    }
}
