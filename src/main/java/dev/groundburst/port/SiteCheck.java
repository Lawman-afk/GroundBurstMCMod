package dev.groundburst.port;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Pair;

import dev.groundburst.GroundBurst;
import dev.groundburst.world.structure.SitePiece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * This branch's check that the mod's sites come out of this version's world generation ({@code gradlew runSiteCheck}):
 * makes a normal world, finds the nearest of each site to its spawn (the End's in the End), has the chunks it stands in
 * made, and logs for each where it is, how many of the mod's blocks it holds, and for each part of it closed in rock
 * underground (a bunker's rooms, a silo's shaft, a waste store's vault: main's {@link SitePiece}, encapsulated) how much
 * of the ground two blocks round it is open (air or water: a cave or the sea at its walls) and the density the ground was
 * shaped with out from its sides (the beardifier's, at 2, 6 and 10 blocks: the same numbers on every version that closes
 * the part in rock as 1.21 does). Then it photographs each from the air ({@code run/screenshots/sites_<site>.png}) and
 * quits. The log's lines begin "Site check:".
 */
@EventBusSubscriber(modid = GroundBurst.MODID, value = Dist.CLIENT)
public final class SiteCheck {
    private static final boolean ON = Boolean.getBoolean("groundburst.siteCheck");
    private static final String WORLD = "site_check";
    private static final long SEED = 20261111L;
    /** How far from the spawn to look for each site, chunks. */
    private static final int SEARCH = 150;
    /** How long to let a site's ground load and draw before its picture, ticks. */
    private static final int SETTLE = 260;

    /** A site found: its name, its dimension, its box, the ground's height at its middle, and where its picture is taken from. */
    private record Site(String name, ResourceKey<Level> dimension, BoundingBox box, int ground, Vec3 eye) {}

    private static boolean started, finished;
    private static int ticks = -1, shown, waited;
    /** The sites found, once the server has looked (null until then). */
    private static volatile @Nullable List<Site> sites;

    private SiteCheck() {}

    @SubscribeEvent
    static void clientTick(ClientTickEvent.Post event) {
        if (!ON) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.getOverlay() == null && mc.level == null && mc.screen != null) {
            started = true;
            GroundBurst.LOGGER.info("Site check: making the world (seed {})", SEED);
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
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(SEED, true, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(),
                mc.screen);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || finished) {
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.setScreen(null);
        }
        ticks++;
        if (ticks == 40) {
            command(mc, "gamemode spectator");                  // (the camera stays where it is put; no hand in the pictures)
            mc.options.hideGui = true;
            command(mc, "gamerule doDaylightCycle false");
            command(mc, "gamerule doWeatherCycle false");
            command(mc, "gamerule doMobSpawning false");
            command(mc, "time set 6000");
            command(mc, "weather clear");
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    try {
                        sites = survey(server);
                    } catch (RuntimeException e) {
                        GroundBurst.LOGGER.error("Site check: failed", e);
                        sites = List.of();
                    }
                });
            }
        }
        List<Site> found = sites;
        if (found == null || ticks < 60) {
            return;
        }
        if (shown < found.size()) {
            Site s = found.get(shown);
            if (waited == 0) {
                // From the south-east, high over the site's corner, looking at its middle.
                Vec3 mid = new Vec3((s.box.minX() + s.box.maxX() + 1) / 2.0, s.ground, (s.box.minZ() + s.box.maxZ() + 1) / 2.0);
                Vec3 eye = s.eye;
                Vec3 d = mid.subtract(eye);
                float yaw = (float) -Math.toDegrees(Math.atan2(d.x, d.z));
                float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
                command(mc, String.format(Locale.ROOT, "execute in %s run tp @s %.1f %.1f %.1f %.1f %.1f", s.dimension.location(), eye.x, eye.y, eye.z,
                        yaw, pitch));
            }
            waited++;
            if (waited == SETTLE - 10) {
                mc.gui.getChat().clearMessages(false);
            } else if (waited >= SETTLE) {
                Screenshot.grab(mc.gameDirectory, "sites_" + s.name + ".png", mc.getMainRenderTarget(),
                        message -> GroundBurst.LOGGER.info("Site check: {}", message.getString()));
                shown++;
                waited = 0;
            }
            return;
        }
        finished = true;
        GroundBurst.LOGGER.info("Site check: done, {} sites found", found.size());
        mc.stop();
    }

    /** Finds, makes and measures the nearest of each of the mod's sites (on the server's thread). */
    private static List<Site> survey(MinecraftServer server) {
        List<Site> out = new ArrayList<>();
        var structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<ResourceKey<Structure>> ours = new ArrayList<>();
        for (ResourceKey<Structure> key : structures.registryKeySet()) {
            if (key.location().getNamespace().equals(GroundBurst.MODID)) {
                ours.add(key);
            }
        }
        ours.sort((a, b) -> a.location().compareTo(b.location()));
        for (ResourceKey<Structure> key : ours) {
            Holder<Structure> holder = structures.getHolderOrThrow(key);
            String name = key.location().getPath();
            Site site = null;
            for (ResourceKey<Level> dimension : List.of(Level.OVERWORLD, Level.END)) {
                ServerLevel level = server.getLevel(dimension);
                if (level != null && (site = find(level, name, holder)) != null) {
                    break;
                }
            }
            if (site == null) {
                GroundBurst.LOGGER.info("Site check: {}: none within {} chunks: WRONG", name, SEARCH);
            } else {
                out.add(site);
            }
        }
        return out;
    }

    /** The nearest {@code holder} in {@code level}, made and measured (null: none there). */
    private static @Nullable Site find(ServerLevel level, String name, Holder<Structure> holder) {
        long t0 = System.nanoTime();
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder),
                BlockPos.ZERO, SEARCH, false);
        if (found == null) {
            return null;
        }
        BlockPos at = found.getFirst();
        StructureStart start = level.structureManager().getStartForStructure(SectionPos.of(at), holder.value(),
                level.getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS));
        if (start == null || !start.isValid()) {
            GroundBurst.LOGGER.info("Site check: {}: found at {} but no start there: WRONG", name, at);
            return null;
        }
        BoundingBox box = start.getBoundingBox();
        for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
            for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
                level.getChunk(cx, cz);                                   // made whole (and so placed)
            }
        }
        int midX = (box.minX() + box.maxX()) / 2, midZ = (box.minZ() + box.maxZ()) / 2;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, midX, midZ);
        int ours = 0;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getNamespace().equals(GroundBurst.MODID)) {
                ours++;
            }
        }
        GroundBurst.LOGGER.info("Site check: {} at {} {} {} in the {} ({} blocks from the spawn; {} parts; {} of the mod's blocks; found in {} ms): {}",
                name, box.minX(), box.minY(), box.minZ(), level.dimension().location().getPath(), Math.round(Math.hypot(at.getX(), at.getZ())),
                start.getPieces().size(), ours, (System.nanoTime() - t0) / 1_000_000, ours > 0 ? "OK" : "WRONG");
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof SitePiece part && closedIn(part)) {
                measure(level, name, start, part);
            }
        }
        // The picture's camera: off the site's south-east corner and well up, and always clear of the ground there.
        double span = Math.max(box.getXSpan(), box.getZSpan());
        int ex = midX + (int) (span * 0.75 + 12), ez = midZ + (int) (span * 0.75 + 12);
        double ey = Math.max(ground + span * 0.55 + 14, level.getHeight(Heightmap.Types.MOTION_BLOCKING, ex, ez) + 8);
        return new Site(name, level.dimension(), box, ground, new Vec3(ex + 0.5, ey, ez + 0.5));
    }

    /** Whether a site's part is closed in rock (encapsulated). */
    private static boolean closedIn(SitePiece part) {
        return part.getTerrainAdjustment() == TerrainAdjustment.ENCAPSULATE;
    }

    /** How open the ground two blocks round a part closed in rock is, and the density the ground was shaped with by it. */
    private static void measure(ServerLevel level, String name, StructureStart start, SitePiece part) {
        BoundingBox b = part.getBoundingBox(), rock = part.getBeardifierBox();
        int open = 0, shell = 0;
        for (BlockPos p : BlockPos.betweenClosed(b.minX() - 2, b.minY() - 2, b.minZ() - 2, b.maxX() + 2, Math.min(b.maxY(), rock.maxY()) + 2, b.maxZ() + 2)) {
            if (inAnyPart(start, p)) {
                continue;
            }
            shell++;
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty()) {
                open++;
            }
        }
        int cx = (rock.minX() + rock.maxX()) / 2, cy = (rock.minY() + rock.maxY()) / 2, cz = (rock.minZ() + rock.maxZ()) / 2;
        Beardifier beardifier = Beardifier.forStructuresInChunk(level.structureManager(), new ChunkPos(cx >> 4, cz >> 4));
        StringBuilder density = new StringBuilder();
        for (int d : new int[] {2, 6, 10}) {
            density.append(String.format(Locale.ROOT, " %d: w %.3f e %.3f n %.3f s %.3f below %.3f;", d,
                    beardifier.compute(new DensityFunction.SinglePointContext(rock.minX() - d, cy, cz)),
                    beardifier.compute(new DensityFunction.SinglePointContext(rock.maxX() + d, cy, cz)),
                    beardifier.compute(new DensityFunction.SinglePointContext(cx, cy, rock.minZ() - d)),
                    beardifier.compute(new DensityFunction.SinglePointContext(cx, cy, rock.maxZ() + d)),
                    beardifier.compute(new DensityFunction.SinglePointContext(cx, rock.minY() - d, cz))));
        }
        GroundBurst.LOGGER.info("Site check: {}: part {} closed in rock: {} of {} blocks round it open; the ground's shaping at{}", name, b, open,
                shell, density);
    }

    private static boolean inAnyPart(StructureStart start, BlockPos p) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece.getBoundingBox().isInside(p)) {
                return true;
            }
        }
        return false;
    }

    private static void command(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }
}
