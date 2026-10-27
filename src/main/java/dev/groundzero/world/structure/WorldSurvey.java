package dev.groundzero.world.structure;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.blast.Blast;
import dev.groundzero.blast.BlastProfile;
import dev.groundzero.blast.Blasts;
import dev.groundzero.config.GZServerConfig;
import dev.groundzero.content.fallout.Wasteland;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development aid: the {@code worldSurvey} Gradle run checks the old sites (Phase 15) and the wasteland (slice 12e) on a
 * normal world. The client half ({@code WorldSurveyClient}) makes the world (a fixed seed, or
 * {@code -Dgroundzero.worldSurvey.seed}) and takes the pictures this half asks for. This half, on the server thread:
 * <ol>
 * <li><b>census</b>: every place each site's structure set offers within {@link #REACH} blocks of the spawn (of the
 * End's middle for the research station) and what becomes of it: a site, or why not (the biome, water, the slope),
 * with each site's biome, variant and turn; the game's own search ({@code /locate}) beside the census's nearest; and a
 * few of vanilla's structures counted the same way, to compare;</li>
 * <li><b>visits</b>: the nearest sites of each kind (one of each silo variant) generated in full, measured (how the
 * ground meets each part: a pad's columns left hanging, the steps at its edge, caves and water opening onto the rooms
 * underground, other structures in the way) and pictured from two sides and above, and a part underground through the
 * rock (a spectator sees through it); the ground round each written out for {@code tools/preview/site_view.py --region};</li>
 * <li><b>blasts</b>: a tactical nuclear warhead set off on land with nobody near (the player watching from far off, as
 * when a missile is fired at a far target), and one with the player close by; for each, the wasteland's biome, the
 * crater lake (its level, how many blocks, its open edges and whether it spills once stirred) measured and pictured.</li>
 * </ol>
 * The report goes to {@code run/survey/survey_<time>.txt}, the sites to a {@code .csv} beside it.
 */
public final class WorldSurvey {
    public enum Stage { WAITING, RUNNING, FINISHED }

    /** A picture the client is to take (it clears {@link #shot} once taken): the camera is put at {@code at} first. */
    public record Shot(String name, ResourceKey<Level> dimension, Vec3 at, BlockPos look) {}

    public static volatile Stage stage = Stage.WAITING;
    public static volatile @Nullable Shot shot;
    /** Where the report was written. */
    public static volatile String report = "";

    /** Half the side of the square the census covers, in blocks. */
    static final int REACH = 10_240;
    /** Sites of each kind visited (the silo: one of each variant). */
    static final int VISITS = 2;
    static final String BLAST = "tactical_nuclear_warhead";

    private static final String what = System.getProperty("groundzero.worldSurvey", "");
    private static final ArrayDeque<Task> TASKS = new ArrayDeque<>();
    private static final StringBuilder out = new StringBuilder();
    private static final StringBuilder csv = new StringBuilder("site,x,y,z,chunk_x,chunk_z,biome,variant,rotation,distance\n");
    private static final Map<String, List<Found>> FOUND = new LinkedHashMap<>();

    private WorldSurvey() {}

    public static void register() {
        if (what.isEmpty()) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
    }

    public static boolean enabled() {
        return !what.isEmpty();
    }

    private static boolean wants(String part) {
        return what.equals("all") || List.of(what.split(",")).contains(part);
    }

    interface Task {
        /** One tick's work; true once done. */
        boolean tick(MinecraftServer server);
    }

    private static void tick(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().getFirst();
        if (stage == Stage.WAITING) {
            if (player == null) {
                return;
            }
            start(server, player);
            stage = Stage.RUNNING;
            return;
        }
        if (stage != Stage.RUNNING) {
            return;
        }
        Task task = TASKS.peek();
        if (task == null) {
            finish(server);
            return;
        }
        try {
            if (task.tick(server)) {
                TASKS.poll();
            }
        } catch (RuntimeException e) {
            GroundZero.LOGGER.error("World survey: a step failed", e);
            line("A step failed: " + e);
            TASKS.poll();
        }
    }

    private static void start(MinecraftServer server, ServerPlayer player) {
        var source = server.createCommandSourceStack().withSuppressedOutput();
        for (String c : List.of("time set noon", "gamerule advance_time false", "gamerule advance_weather false", "weather clear",
                "gamerule spawn_monsters false")) {
            server.getCommands().performPrefixedCommand(source, c);
        }
        player.setGameMode(GameType.SPECTATOR);
        ServerLevel overworld = server.overworld();
        line("World survey, seed " + overworld.getSeed() + ", spawn " + overworld.getRespawnData().pos().toShortString());
        if (wants("census")) {
            for (String name : GZStructures.NAMES) {
                TASKS.add(s -> {
                    census(s, name);
                    return true;
                });
            }
            TASKS.add(s -> {
                vanilla(s);
                return true;
            });
        }
        if (wants("visits")) {
            TASKS.add(s -> {
                if (FOUND.isEmpty()) {
                    for (String name : GZStructures.NAMES) {
                        census(s, name);                       // the visits need the census's sites
                    }
                }
                planVisits();
                return true;
            });
        }
        if (wants("blast")) {
            TASKS.add(new BlastTest("far", false));
            TASKS.add(new BlastTest("near", true));
        }
    }

    // --- census -----------------------------------------------------------------------------------------------------------
    enum Verdict { SITE, BIOME, WATER, SLOPE, DEPTH, VOID, PLACEMENT }

    /** A site the census found: where, its biome, variant and turn, how far from the centre, and how much the land under it rises and falls. */
    record Found(String name, ResourceKey<Level> dimension, ChunkPos chunk, BlockPos origin, String biome, String variant, String rotation,
                 double distance, int range) {}

    static ServerLevel levelFor(MinecraftServer server, String name) {
        return name.equals("exotic_research_station") ? server.getLevel(Level.END) : server.overworld();
    }

    static BlockPos centreFor(ServerLevel level) {
        return level.dimension() == Level.END ? BlockPos.ZERO : level.getRespawnData().pos();
    }

    private static Structure.GenerationContext context(ServerLevel level, ChunkPos chunk, Structure structure) {
        ServerChunkCache chunks = level.getChunkSource();
        ChunkGenerator generator = chunks.getGenerator();
        RandomState random = chunks.randomState();
        Climate.Sampler sampler = random.createClimateSampler(SamplerContext.builder().enableCaches().build());
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(), sampler, random,
                level.getStructureTemplateManager(), chunks.getGeneratorState().getLevelSeed(), chunk, level, structure.biomes()::contains);
    }

    private static void census(MinecraftServer server, String name) {
        long t0 = System.nanoTime();
        ServerLevel level = levelFor(server, name);
        Holder.Reference<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(GZStructures.structure(name));
        StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(GZStructures.set(name));
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
        ServerChunkCache chunks = level.getChunkSource();
        ChunkGeneratorStructureState state = chunks.getGeneratorState();
        ChunkGenerator generator = chunks.getGenerator();
        RandomState random = chunks.randomState();
        Climate.Sampler sampler = random.createClimateSampler(SamplerContext.builder().enableCaches().build());
        long seed = state.getLevelSeed();
        BlockPos centre = centreFor(level);
        int c0x = centre.getX() >> 4, c0z = centre.getZ() >> 4, reach = REACH >> 4, spacing = placement.spacing();
        EnumMap<Verdict, Integer> counts = new EnumMap<>(Verdict.class);
        Map<String, Integer> rejected = new TreeMap<>(), sited = new TreeMap<>(), variants = new TreeMap<>();
        List<Found> found = new ArrayList<>();
        SiteStructure site = (SiteStructure) holder.value();
        // For a site on the surface: how uneven the ground is wherever it could otherwise stand (the right land, dry, not
        // low), so max_slope can be set for how common the site should be.
        int[] ranges = new int[64];
        int dryCells = 0, wetCells = 0, lowCells = 0;
        int cells = 0;
        for (int i = Math.floorDiv(c0x - reach, spacing); i <= Math.floorDiv(c0x + reach, spacing); i++) {
            for (int j = Math.floorDiv(c0z - reach, spacing); j <= Math.floorDiv(c0z + reach, spacing); j++) {
                ChunkPos c = placement.getPotentialStructureChunk(seed, i * spacing, j * spacing);
                if (Math.abs(c.x() - c0x) > reach || Math.abs(c.z() - c0z) > reach) {
                    continue;
                }
                cells++;
                if (level.dimension() == Level.OVERWORLD && !name.equals("waste_storage")) {
                    SiteStructure.Ground g = site.ground(context(level, c, site));
                    Holder<Biome> at = generator.getBiomeSource().createResolver(sampler).getNoiseBiome(QuartPos.fromBlock(c.getMiddleBlockX()),
                            QuartPos.fromBlock(g.middle()), QuartPos.fromBlock(c.getMiddleBlockZ()));
                    if (site.biomes().contains(at)) {
                        if (g.wet()) {
                            wetCells++;
                        } else if (g.average() < level.getSeaLevel()) {
                            lowCells++;
                        } else {
                            dryCells++;
                            ranges[Math.min(63, g.range())]++;
                        }
                    }
                }
                Verdict v;
                if (!placement.isStructureChunk(state, c.x(), c.z())) {
                    v = Verdict.PLACEMENT;
                } else {
                    Structure.GenerationContext ctx = context(level, c, holder.value());
                    Optional<Structure.GenerationStub> stub = ((SiteStructure) holder.value()).findGenerationPoint(ctx);
                    if (stub.isPresent()) {
                        BlockPos o = stub.get().position();
                        Holder<Biome> biome = generator.getBiomeSource().createResolver(sampler).getNoiseBiome(QuartPos.fromBlock(o.getX()),
                                QuartPos.fromBlock(o.getY()), QuartPos.fromBlock(o.getZ()));
                        String b = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
                        if (ctx.isValidBiome(stub.get())) {
                            v = Verdict.SITE;
                            var pieces = stub.get().getPiecesBuilder().build().pieces();
                            SitePiece first = (SitePiece) pieces.getFirst();
                            String variant = first.templateId().getPath().replace("site/", "");
                            double d = Math.hypot(o.getX() - centre.getX(), o.getZ() - centre.getZ());
                            int range = level.dimension() == Level.OVERWORLD ? site.ground(context(level, c, site)).range() : 0;
                            Found f = new Found(name, level.dimension(), c, o, b, variant, first.rotation().getSerializedName(), d, range);
                            found.add(f);
                            sited.merge(b, 1, Integer::sum);
                            variants.merge(variant, 1, Integer::sum);
                        } else {
                            v = Verdict.BIOME;
                            rejected.merge(b, 1, Integer::sum);
                        }
                    } else {
                        int x = c.getMiddleBlockX(), z = c.getMiddleBlockZ();
                        int top = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                        int floor = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                        Holder<Biome> biome = generator.getBiomeSource().createResolver(sampler).getNoiseBiome(QuartPos.fromBlock(x),
                                QuartPos.fromBlock(top), QuartPos.fromBlock(z));
                        if (!holder.value().biomes().contains(biome)) {
                            v = Verdict.BIOME;
                            rejected.merge(biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("?"), 1, Integer::sum);
                        } else if (floor < top - 1) {
                            v = Verdict.WATER;
                        } else if (name.equals("waste_storage")) {
                            v = Verdict.DEPTH;
                        } else if (name.equals("exotic_research_station")) {
                            v = Verdict.VOID;
                        } else {
                            v = Verdict.SLOPE;
                        }
                    }
                }
                counts.merge(v, 1, Integer::sum);
            }
        }
        found.sort(Comparator.comparingDouble(Found::distance));
        FOUND.put(name, found);
        String nearVillages = level.dimension() == Level.OVERWORLD && !name.equals("waste_storage") ? villagesNear(level, found) : "";
        double area = Math.pow(2.0 * REACH / 1000, 2);
        line("");
        line(String.format(Locale.ROOT, "== %s (spacing %d, separation %d, %s): %d places, %d sites in %.0f km² = %.2f per 100 km²; %.1f s",
                name, spacing, placement.separation(), level.dimension().identifier().getPath(), cells, found.size(), area,
                found.size() / area * 100, (System.nanoTime() - t0) / 1e9));
        line("   verdicts " + counts);
        line("   sites by biome " + sited);
        line("   variants " + variants);
        if (!nearVillages.isEmpty()) {
            line("   " + nearVillages);
        }
        if (dryCells > 0) {
            StringBuilder h = new StringBuilder();
            int sum = 0;
            for (int k = 0; k <= 16; k++) {
                sum += ranges[k];
                if (k >= 3) {
                    h.append(k).append(':').append(sum).append(' ');
                }
            }
            line(String.format(Locale.ROOT, "   the right land: %d on water, %d low, %d dry; of those, standing with max_slope k: %s",
                    wetCells, lowCells, dryCells, h));
        }
        line("   rejected biomes (top) " + top(rejected, 8));
        line(String.format(Locale.ROOT, "   typical distance to the nearest from anywhere %.0f blocks; between neighbours %.0f; from the %s %.0f",
                typicalNearest(found, centre), neighbourMean(found), level.dimension() == Level.END ? "End's middle" : "spawn",
                found.isEmpty() ? Double.NaN : found.getFirst().distance()));
        // The game's own search, from the same place.
        long l0 = System.nanoTime();
        var near = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), centre, 100, false);
        String census = found.isEmpty() ? "none" : found.getFirst().chunk().toString();
        line(String.format(Locale.ROOT, "   /locate: %s (census nearest %s) in %.1f s", near == null ? "none" : near.getFirst().toShortString()
                + " chunk " + ChunkPos.containing(near.getFirst()), census, (System.nanoTime() - l0) / 1e9));
        for (Found f : found) {
            csv.append(name).append(',').append(f.origin().getX()).append(',').append(f.origin().getY()).append(',').append(f.origin().getZ())
                    .append(',').append(f.chunk().x()).append(',').append(f.chunk().z()).append(',').append(f.biome()).append(',')
                    .append(f.variant()).append(',').append(f.rotation()).append(',').append(Math.round(f.distance())).append('\n');
        }
    }

    private static final Map<Long, Boolean> VILLAGES = new HashMap<>();

    /** How many of the sites have a village standing within 4, 6, 8 and 10 chunks of their own chunk. */
    private static String villagesNear(ServerLevel level, List<Found> found) {
        StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(Identifier.withDefaultNamespace("villages"));
        if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
            return "";
        }
        long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
        int[] within = new int[11];
        for (Found f : found) {
            int best = Integer.MAX_VALUE;
            int spacing = placement.spacing();
            for (int i = Math.floorDiv(f.chunk().x() - 10, spacing); i <= Math.floorDiv(f.chunk().x() + 10, spacing); i++) {
                for (int j = Math.floorDiv(f.chunk().z() - 10, spacing); j <= Math.floorDiv(f.chunk().z() + 10, spacing); j++) {
                    ChunkPos v = placement.getPotentialStructureChunk(seed, i * spacing, j * spacing);
                    int d = Math.max(Math.abs(v.x() - f.chunk().x()), Math.abs(v.z() - f.chunk().z()));
                    if (d > 10) {
                        continue;
                    }
                    boolean stands = VILLAGES.computeIfAbsent(v.pack(), k -> set.structures().stream()
                            .anyMatch(e -> e.structure().value().findValidGenerationPoint(context(level, v, e.structure().value())).isPresent()));
                    if (stands) {
                        best = Math.min(best, d);
                    }
                }
            }
            if (best <= 10) {
                within[best]++;
            }
        }
        int sum = 0;
        StringBuilder b = new StringBuilder("villages standing within chunks of a site (4, 6, 8, 10): ");
        for (int d = 0; d <= 10; d++) {
            sum += within[d];
            if (d == 4 || d == 6 || d == 8 || d == 10) {
                b.append(sum).append(d == 10 ? "" : ", ");
            }
        }
        return b.append(" of ").append(found.size()).toString();
    }

    /** A few of vanilla's structure sets counted over the same square, to compare. */
    private static void vanilla(MinecraftServer server) {
        ServerLevel level = server.overworld();
        line("");
        line("== vanilla, the same square:");
        for (String setName : List.of("villages", "pillager_outposts", "desert_pyramids", "igloos", "woodland_mansions", "ruined_portals",
                "trial_chambers", "end_cities")) {
            long t0 = System.nanoTime();
            if (setName.equals("end_cities")) {
                level = server.getLevel(Level.END);
            }
            StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(Identifier.withDefaultNamespace(setName));
            if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
                continue;
            }
            ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
            long seed = state.getLevelSeed();
            BlockPos centre = centreFor(level);
            int c0x = centre.getX() >> 4, c0z = centre.getZ() >> 4, reach = REACH >> 4, spacing = placement.spacing();
            int cells = 0;
            List<Found> found = new ArrayList<>();
            for (int i = Math.floorDiv(c0x - reach, spacing); i <= Math.floorDiv(c0x + reach, spacing); i++) {
                for (int j = Math.floorDiv(c0z - reach, spacing); j <= Math.floorDiv(c0z + reach, spacing); j++) {
                    ChunkPos c = placement.getPotentialStructureChunk(seed, i * spacing, j * spacing);
                    if (Math.abs(c.x() - c0x) > reach || Math.abs(c.z() - c0z) > reach) {
                        continue;
                    }
                    cells++;
                    if (!placement.isStructureChunk(state, c.x(), c.z())) {
                        continue;
                    }
                    for (StructureSet.StructureSelectionEntry e : set.structures()) {
                        if (e.structure().value().findValidGenerationPoint(context(level, c, e.structure().value())).isPresent()) {
                            BlockPos o = c.getMiddleBlockPosition(64);
                            found.add(new Found(setName, level.dimension(), c, o, "", "", "", Math.hypot(o.getX() - centre.getX(), o.getZ() - centre.getZ()), 0));
                            break;
                        }
                    }
                }
            }
            double area = Math.pow(2.0 * REACH / 1000, 2);
            line(String.format(Locale.ROOT, "   %s (spacing %d): %d places, %d sites = %.2f per 100 km²; typical distance to the nearest %.0f; %.1f s",
                    setName, spacing, cells, found.size(), found.size() / area * 100, typicalNearest(found, centre), (System.nanoTime() - t0) / 1e9));
        }
    }

    private static String top(Map<String, Integer> m, int n) {
        StringBuilder b = new StringBuilder();
        m.entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(n)
                .forEach(e -> b.append(e.getKey()).append(' ').append(e.getValue()).append(", "));
        return b.toString();
    }

    /** The mean distance from points spread over the middle of the census square to the nearest site. */
    private static double typicalNearest(List<Found> found, BlockPos centre) {
        if (found.isEmpty()) {
            return Double.NaN;
        }
        double sum = 0;
        int n = 0;
        for (int i = -10; i <= 10; i++) {
            for (int j = -10; j <= 10; j++) {
                double x = centre.getX() + i * REACH * 0.07, z = centre.getZ() + j * REACH * 0.07;
                double best = Double.MAX_VALUE;
                for (Found f : found) {
                    best = Math.min(best, Math.hypot(f.origin().getX() - x, f.origin().getZ() - z));
                }
                sum += best;
                n++;
            }
        }
        return sum / n;
    }

    private static double neighbourMean(List<Found> found) {
        if (found.size() < 2) {
            return Double.NaN;
        }
        double sum = 0;
        for (Found a : found) {
            double best = Double.MAX_VALUE;
            for (Found b : found) {
                if (a != b) {
                    best = Math.min(best, Math.hypot(a.origin().getX() - b.origin().getX(), a.origin().getZ() - b.origin().getZ()));
                }
            }
            sum += best;
        }
        return sum / found.size();
    }

    // --- visits -----------------------------------------------------------------------------------------------------------
    private static void planVisits() {
        for (Map.Entry<String, List<Found>> e : FOUND.entrySet()) {
            List<Found> chosen = new ArrayList<>();
            if (e.getKey().equals("abandoned_silo")) {
                Set<String> seen = new java.util.HashSet<>();
                for (Found f : e.getValue()) {
                    if (seen.add(f.variant())) {
                        chosen.add(f);
                    }
                }
            } else {
                chosen.addAll(e.getValue().subList(0, Math.min(VISITS - 1, e.getValue().size())));
            }
            // And the one on the most uneven land the rules let it stand on (the hardest fit).
            e.getValue().stream().max(Comparator.comparingInt(Found::range)).filter(f -> !chosen.contains(f)).ifPresent(chosen::add);
            for (int i = 0; i < chosen.size(); i++) {
                TASKS.add(new Visit(chosen.get(i), i + 1));
            }
        }
    }

    /** Loads chunks with forced tickets, a batch at a time; done once all are full. */
    static final class Loader {
        final ServerLevel level;
        final long[] keys;
        final LongArrayList batch = new LongArrayList();
        final LongOpenHashSet held = new LongOpenHashSet();
        int next;

        Loader(ServerLevel level, BoundingBox box, int margin) {
            this.level = level;
            LongArrayList k = new LongArrayList();
            for (int x = (box.minX() - margin) >> 4; x <= (box.maxX() + margin) >> 4; x++) {
                for (int z = (box.minZ() - margin) >> 4; z <= (box.maxZ() + margin) >> 4; z++) {
                    k.add(ChunkPos.pack(x, z));
                }
            }
            this.keys = k.toLongArray();
        }

        Loader(ServerLevel level, long[] keys) {
            this.level = level;
            this.keys = keys;
        }

        boolean tick() {
            for (int i = 0; i < this.batch.size(); i++) {
                long k = this.batch.getLong(i);
                if (this.level.getChunkSource().getChunkNow(ChunkPos.getX(k), ChunkPos.getZ(k)) == null) {
                    return false;
                }
            }
            this.batch.clear();
            if (this.next >= this.keys.length) {
                return true;
            }
            for (int i = 0; i < 96 && this.next < this.keys.length; i++) {
                long k = this.keys[this.next++];
                if (this.held.add(k)) {
                    this.level.setChunkForced(ChunkPos.getX(k), ChunkPos.getZ(k), true);
                }
                this.batch.add(k);
            }
            return false;
        }

        void release() {
            for (long k : this.held) {
                this.level.setChunkForced(ChunkPos.getX(k), ChunkPos.getZ(k), false);
            }
            this.held.clear();
        }
    }

    /** Puts the camera at {@code at} looking at {@code look} and asks the client for a picture; done once it is taken. */
    static final class Picture implements Task {
        final String name;
        final ResourceKey<Level> dimension;
        final Vec3 at;
        final Vec3 look;
        boolean asked;

        Picture(String name, ResourceKey<Level> dimension, Vec3 at, Vec3 look) {
            this.name = name;
            this.dimension = dimension;
            this.at = at;
            this.look = look;
        }

        @Override
        public boolean tick(MinecraftServer server) {
            if (!this.asked) {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                Vec3 d = this.look.subtract(this.at);
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
                player.teleportTo(server.getLevel(this.dimension), this.at.x, this.at.y, this.at.z, Set.of(), yaw, pitch, true);
                shot = new Shot("survey_" + this.name + ".png", this.dimension, this.at, BlockPos.containing(this.look));
                this.asked = true;
                return false;
            }
            return shot == null;
        }
    }

    static final class Visit implements Task {
        final Found site;
        final int n;
        final String tag;
        int step;
        @Nullable StructureStart start;
        @Nullable Loader loader;
        final ArrayDeque<Task> pictures = new ArrayDeque<>();

        Visit(Found site, int n) {
            this.site = site;
            this.n = n;
            this.tag = site.name() + "_" + n + (site.name().equals("abandoned_silo") ? "_" + site.variant().replace("silo_surface_", "") : "");
        }

        @Override
        public boolean tick(MinecraftServer server) {
            ServerLevel level = server.getLevel(this.site.dimension());
            switch (this.step) {
                case 0 -> {
                    Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(GZStructures.structure(this.site.name()));
                    ChunkAccess chunk = level.getChunk(this.site.chunk().x(), this.site.chunk().z(), ChunkStatus.STRUCTURE_STARTS, true);
                    this.start = chunk.getStartForStructure(structure);
                    if (this.start == null || !this.start.isValid()) {
                        line("");
                        line("-- visit " + this.tag + ": no structure start at " + this.site.chunk() + " (the census said there would be one)");
                        return true;
                    }
                    this.loader = new Loader(level, this.start.getBoundingBox(), 24);
                    // The camera goes there now, so the client gets the ground while the server makes it.
                    BoundingBox b = this.start.getBoundingBox();
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    player.teleportTo(level, b.getCenter().getX() + 40, b.maxY() + 30, b.getCenter().getZ() + 40, Set.of(), 135, 30, true);
                    this.step = 1;
                }
                case 1 -> {
                    if (this.loader.tick()) {
                        analyse(level);
                        planPictures(level);
                        this.step = 2;
                    }
                }
                case 2 -> {
                    Task p = this.pictures.peek();
                    if (p == null) {
                        this.loader.release();
                        return true;
                    }
                    if (p.tick(server)) {
                        this.pictures.poll();
                    }
                }
                default -> {
                    return true;
                }
            }
            return false;
        }

        private void analyse(ServerLevel level) {
            StructureStart s = this.start;
            BoundingBox all = s.getBoundingBox();
            line("");
            line(String.format(Locale.ROOT, "-- visit %s: %s at %s (%s, %s, turned %s, the land rising and falling %d), %.0f blocks from the %s; box %s",
                    this.tag, this.site.name(), this.site.origin().toShortString(), this.site.biome(), this.site.variant(), this.site.rotation(),
                    this.site.range(), this.site.distance(), level.dimension() == Level.END ? "End's middle" : "spawn", box(all)));
            for (StructurePiece piece : s.getPieces()) {
                if (!(piece instanceof SitePiece p)) {
                    continue;
                }
                BoundingBox b = p.getBoundingBox();
                String part = p.templateId().getPath().replace("site/", "");
                if (p.getTerrainAdjustment() == TerrainAdjustment.ENCAPSULATE || p.getTerrainAdjustment() == TerrainAdjustment.NONE
                        && b.maxY() < level.getHeight(Heightmap.Types.OCEAN_FLOOR, b.getCenter().getX(), b.getCenter().getZ()) - 4) {
                    underground(level, part, p, b);
                } else {
                    surface(level, part, p, b);
                }
            }
            // Other structures with a part in the site's box.
            Map<String, Integer> others = new TreeMap<>();
            for (int cx = all.minX() >> 4; cx <= all.maxX() >> 4; cx++) {
                for (int cz = all.minZ() >> 4; cz <= all.maxZ() >> 4; cz++) {
                    ChunkAccess c = level.getChunk(cx, cz);
                    for (Map.Entry<Structure, it.unimi.dsi.fastutil.longs.LongSet> e : c.getAllReferences().entrySet()) {
                        String id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(e.getKey()).toString();
                        if (!id.equals(GroundZero.id(this.site.name()).toString())) {
                            for (long startKey : e.getValue()) {
                                StructureStart other = level.getChunk(ChunkPos.getX(startKey), ChunkPos.getZ(startKey), ChunkStatus.STRUCTURE_STARTS, true)
                                        .getStartForStructure(e.getKey());
                                if (other != null && other.isValid() && other.getPieces().stream().anyMatch(q -> q.getBoundingBox().intersects(all))) {
                                    others.merge(id, 1, Integer::sum);
                                }
                            }
                        }
                    }
                }
            }
            line("   other structures in its box: " + (others.isEmpty() ? "none" : others.toString()));
            export(level, all, this.tag);
        }

        /**
         * A part on the ground: the columns of its bottom left hanging, water in it, and the steps where the land meets
         * its box (the land's top against the site's ground: 0 is flush).
         */
        private void surface(ServerLevel level, String part, SitePiece p, BoundingBox b) {
            int bottom = b.minY(), floor = 0, hanging = 0, water = 0;
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    BlockState at = level.getBlockState(new BlockPos(x, bottom, z));
                    if (!at.isAir() && at.getFluidState().isEmpty()) {
                        floor++;
                        BlockState under = level.getBlockState(new BlockPos(x, bottom - 1, z));
                        if (under.isAir() || !under.getFluidState().isEmpty() || under.canBeReplaced()) {
                            hanging++;
                        }
                    }
                    for (int y = bottom; y <= b.maxY(); y++) {
                        if (level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)) {
                            water++;
                        }
                    }
                }
            }
            int g = p.ground() + 1;                        // the first air over the site's ground, as the heightmap counts
            // The ground just outside the box: how far above (a wall) or below (a drop) the site's ground it lies.
            int ring = 0, above = 0, below = 0, maxAbove = 0, maxBelow = 0;
            double sum = 0;
            for (int d = 1; d <= 3; d++) {
                for (int x = b.minX() - d; x <= b.maxX() + d; x++) {
                    for (int z = b.minZ() - d; z <= b.maxZ() + d; z++) {
                        if (x > b.minX() - d && x < b.maxX() + d && z > b.minZ() - d && z < b.maxZ() + d) {
                            continue;
                        }
                        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);   // first air over the ground
                        int step = h - g;
                        ring++;
                        sum += Math.abs(step);
                        if (step >= 3) {
                            above++;
                        }
                        if (step <= -3) {
                            below++;
                        }
                        maxAbove = Math.max(maxAbove, step);
                        maxBelow = Math.max(maxBelow, -step);
                    }
                }
            }
            line(String.format(Locale.ROOT, "   %s (%s, ground y %d, bottom y %d): bottom %d columns, %d left hanging (%.1f%%); water in it %d; "
                    + "land round it: mean step %.1f, %d%% walls of 3+ (highest %d), %d%% drops of 3+ (deepest %d)", part,
                    p.getTerrainAdjustment().getSerializedName(), p.ground(), bottom, floor, hanging, floor == 0 ? 0 : 100.0 * hanging / floor, water,
                    ring == 0 ? 0 : sum / ring, ring == 0 ? 0 : 100 * above / ring, maxAbove, ring == 0 ? 0 : 100 * below / ring, maxBelow));
        }

        /** A part underground: what lies just outside its walls (rock, or caves, water, lava opening onto it) and fluids in it. */
        private void underground(ServerLevel level, String part, SitePiece p, BoundingBox b) {
            BoundingBox shell = p.getBeardifierBox();
            int n = 0, air = 0, water = 0, lava = 0, inWater = 0, inLava = 0, flowingIn = 0;
            for (int x = b.minX() - 1; x <= b.maxX() + 1; x++) {
                for (int z = b.minZ() - 1; z <= b.maxZ() + 1; z++) {
                    for (int y = b.minY() - 1; y <= shell.maxY(); y++) {
                        boolean inside = x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ() && y >= b.minY();
                        BlockState s = level.getBlockState(new BlockPos(x, y, z));
                        if (inside) {
                            if (s.getFluidState().is(FluidTags.WATER)) {
                                inWater++;
                                if (!s.getFluidState().isSource()) {
                                    flowingIn++;
                                }
                            }
                            if (s.getFluidState().is(FluidTags.LAVA)) {
                                inLava++;
                            }
                            continue;
                        }
                        n++;
                        if (s.isAir()) {
                            air++;
                        } else if (s.getFluidState().is(FluidTags.WATER)) {
                            water++;
                        } else if (s.getFluidState().is(FluidTags.LAVA)) {
                            lava++;
                        }
                    }
                }
            }
            line(String.format(Locale.ROOT, "   %s (%s, y %d..%d): shell of %d blocks: %.1f%% open air, %d water, %d lava; in it %d water (%d flowing), %d lava",
                    part, p.getTerrainAdjustment().getSerializedName(), b.minY(), b.maxY(), n, n == 0 ? 0 : 100.0 * air / n, water, lava, inWater,
                    flowingIn, inLava));
        }

        private void planPictures(ServerLevel level) {
            BoundingBox all = this.start.getBoundingBox();
            BlockPos o = this.site.origin();
            ResourceKey<Level> dim = level.dimension();
            Vec3 look = new Vec3(all.getCenter().getX() + 0.5, o.getY() + 2, all.getCenter().getZ() + 0.5);
            double span = Math.max(all.getXSpan(), all.getZSpan());
            double d = span * 0.95 + 14;
            boolean buried = this.site.name().equals("waste_storage");
            if (!buried) {
                this.pictures.add(new Picture(this.tag + "_a", dim, look.add(0.72 * d, 0.62 * d, 0.72 * d), look));
                this.pictures.add(new Picture(this.tag + "_b", dim, look.add(-0.72 * d, 0.62 * d, -0.72 * d), look));
                this.pictures.add(new Picture(this.tag + "_top", dim, look.add(0.01, 1.25 * d + 12, 0.01), look));
                int lx = Mth.floor(look.x + 0.95 * d), lz = Mth.floor(look.z - 0.3 * d);
                int ly = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
                this.pictures.add(new Picture(this.tag + "_low", dim, new Vec3(lx + 0.5, ly + 2.6, lz + 0.5), look.add(0, -1, 0)));
            }
            // A part underground, through the rock (a spectator inside stone sees the hollows round it).
            for (StructurePiece piece : this.start.getPieces()) {
                if (piece instanceof SitePiece p && (p.getTerrainAdjustment() == TerrainAdjustment.ENCAPSULATE || buried)) {
                    BoundingBox b = p.getBoundingBox();
                    Vec3 mid = Vec3.atCenterOf(b.getCenter());
                    double r = Math.max(b.getYSpan() * 0.75, Math.max(b.getXSpan(), b.getZSpan()) * 0.7) + 8;
                    this.pictures.add(new Picture(this.tag + "_xray", dim, mid.add(r * 0.8, r * 0.25, r * 0.6), mid));
                    if (buried) {
                        this.pictures.add(new Picture(this.tag + "_xray2", dim, mid.add(-r * 0.7, r * 0.35, -r * 0.7), mid));
                    }
                    break;
                }
            }
        }
    }

    private static String box(BoundingBox b) {
        return b.minX() + "," + b.minY() + "," + b.minZ() + " to " + b.maxX() + "," + b.maxY() + "," + b.maxZ();
    }

    /**
     * Writes the blocks round a site to {@code run/survey/<tag>.json} (size, corner, palette) and {@code .bin} (the palette
     * indices, little-endian shorts, x fastest, then z, then y) for {@code tools/preview/site_view.py --region}.
     */
    static void export(ServerLevel level, BoundingBox all, String tag) {
        int x0 = all.minX() - 10, z0 = all.minZ() - 10, y0 = Math.max(level.getMinY(), all.minY() - 6);
        int x1 = all.maxX() + 10, z1 = all.maxZ() + 10, y1 = Math.min(level.getMaxY(), all.maxY() + 14);
        int sx = x1 - x0 + 1, sy = y1 - y0 + 1, sz = z1 - z0 + 1;
        Map<String, Integer> palette = new LinkedHashMap<>();
        ByteBuffer buf = ByteBuffer.allocate(sx * sy * sz * 2).order(ByteOrder.LITTLE_ENDIAN);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    BlockState s = level.getBlockState(at.set(x, y, z));
                    String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
                    buf.putShort((short) (int) palette.computeIfAbsent(id, k -> palette.size()));
                }
            }
        }
        Path dir = level.getServer().getServerDirectory().resolve("survey");
        StringBuilder json = new StringBuilder("{\"size\": [" + sx + ", " + sy + ", " + sz + "], \"corner\": [" + x0 + ", " + y0 + ", " + z0
                + "], \"palette\": [");
        int i = 0;
        for (String id : palette.keySet()) {
            json.append(i++ == 0 ? "" : ", ").append('"').append(id).append('"');
        }
        json.append("]}\n");
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(tag + ".json"), json);
            Files.write(dir.resolve(tag + ".bin"), buf.array());
        } catch (IOException e) {
            GroundZero.LOGGER.error("World survey: could not write {}", tag, e);
        }
    }

    // --- blasts -----------------------------------------------------------------------------------------------------------
    /**
     * A tactical nuclear warhead on land {@code 1 200} (far) or {@code 2 400} (near) blocks east of the spawn, where the
     * ground is wet enough for a crater lake: the ground round it generated first and let go, then set off with the
     * player 400 blocks off (beyond the view distance: the crater's chunks unloaded, as for a missile fired at a far
     * target) or 110 off (in view); then the wasteland and the lake measured and pictured.
     */
    static final class BlastTest implements Task {
        final String tag;
        final boolean near;
        int step, waited;
        BlockPos at = BlockPos.ZERO;
        @Nullable BlastProfile profile;
        @Nullable Loader loader;
        @Nullable Blast job;
        final ArrayDeque<Task> pictures = new ArrayDeque<>();
        long fired;

        BlastTest(String tag, boolean near) {
            this.tag = tag;
            this.near = near;
        }

        @Override
        public boolean tick(MinecraftServer server) {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            switch (this.step) {
                case 0 -> {
                    var found = level.registryAccess().lookupOrThrow(BlastProfile.REGISTRY).getOptional(Blasts.profile(BLAST));
                    this.profile = found.orElseThrow().scaled(GZServerConfig.RADIUS_MULTIPLIER.get().floatValue(), GZServerConfig.MAX_RADIUS.get());
                    this.at = pick(level, centreFor(level).offset(this.near ? 2400 : 1200, 0, 0));
                    int r = (int) Math.ceil(this.profile.outerRadius()) + 32;
                    this.loader = new Loader(level, new BoundingBox(this.at.getX() - r, 0, this.at.getZ() - r, this.at.getX() + r, 0, this.at.getZ() + r), 0);
                    line("");
                    line(String.format(Locale.ROOT, "-- blast %s: %s at %s (%s), crater %.0f, scorch %.0f, outer %.0f; generating %d chunks", this.tag, BLAST,
                            this.at.toShortString(), biome(level, this.at), this.profile.crater(), this.profile.scorch(), this.profile.outerRadius(),
                            this.loader.keys.length));
                    player.teleportTo(level, this.at.getX() + 0.5, this.at.getY() + 120, this.at.getZ() + 400.5, Set.of(), 180, 15, true);
                    this.step = 1;
                }
                case 1 -> {
                    if (this.loader.tick()) {
                        this.loader.release();
                        // The watcher: 400 blocks off (the crater's chunks let go and unloaded) or 110 (in view).
                        double off = this.near ? 110 : 400;
                        level.getChunk(this.at.getX() >> 4, (this.at.getZ() + (int) off) >> 4);
                        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, this.at.getX(), this.at.getZ() + (int) off);
                        player.teleportTo(level, this.at.getX() + 0.5, ground + (this.near ? 60 : 90), this.at.getZ() + off + 0.5, Set.of(), 180,
                                this.near ? 25 : 12, true);
                        this.waited = 0;
                        this.step = 2;
                    }
                }
                case 2 -> {
                    if (++this.waited < 300) {
                        return false;                          // let the generated chunks unload
                    }
                    level.getChunk(this.at.getX() >> 4, this.at.getZ() >> 4);
                    Vec3 burst = new Vec3(this.at.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, this.at.getX(), this.at.getZ()) + 0.5,
                            this.at.getZ() + 0.5);
                    int loaded = 0, total = 0;
                    int cr = (int) Math.ceil(this.profile.scorch() / 16) + 1;
                    for (int x = -cr; x <= cr; x++) {
                        for (int z = -cr; z <= cr; z++) {
                            total++;
                            loaded += level.getChunkSource().getChunkNow((this.at.getX() >> 4) + x, (this.at.getZ() >> 4) + z) != null ? 1 : 0;
                        }
                    }
                    this.job = Blasts.detonate(level, burst, Blasts.profile(BLAST), null);
                    this.fired = level.getGameTime();
                    line(String.format(Locale.ROOT, "   set off at %s with %d of the %d chunks round the scorch loaded", BlockPos.containing(burst).toShortString(),
                            loaded, total));
                    this.waited = 0;
                    this.step = 3;
                }
                case 3 -> {
                    if (this.job == null || !this.job.isDone()) {
                        if (level.getGameTime() - this.fired > 24_000) {
                            line("   timed out");
                            return true;
                        }
                        return false;
                    }
                    if (!Wasteland.idle(level) || ++this.waited < 60) {
                        return false;
                    }
                    line(String.format(Locale.ROOT, "   finished %d ticks after the burst (%d blocks changed); the wasteland's work done", level.getGameTime()
                            - this.fired, this.job.blocksChanged()));
                    int r = (int) Math.ceil(Math.max(this.profile.scorch(), this.profile.crater() * 1.8f)) + 24;
                    this.loader = new Loader(level, new BoundingBox(this.at.getX() - r, 0, this.at.getZ() - r, this.at.getX() + r, 0, this.at.getZ() + r), 0);
                    this.step = 4;
                }
                case 4 -> {
                    if (this.loader.tick()) {
                        measure(level);
                        planPictures(level);
                        this.step = 5;
                    }
                }
                case 5 -> {
                    Task p = this.pictures.peek();
                    if (p == null) {
                        stir(level);
                        this.waited = 0;
                        this.step = 6;
                    } else if (p.tick(server)) {
                        this.pictures.poll();
                    }
                }
                case 6 -> {
                    if (++this.waited < 200) {
                        return false;
                    }
                    int[] after = water(level);
                    line(String.format(Locale.ROOT, "   200 ticks after the lake's edges were stirred: %d water blocks, %d of them flowing", after[0], after[1]));
                    this.pictures.add(new Picture("blast_" + this.tag + "_stirred", level.dimension(),
                            new Vec3(this.at.getX() + this.profile.crater() * 1.6, this.lakeY + this.profile.crater() * 1.3, this.at.getZ() + this.profile.crater() * 1.6),
                            Vec3.atCenterOf(this.at.atY(this.lakeY))));
                    this.step = 7;
                }
                case 7 -> {
                    Task p = this.pictures.peek();
                    if (p == null) {
                        this.loader.release();
                        return true;
                    }
                    if (p.tick(server)) {
                        this.pictures.poll();
                    }
                }
                default -> {
                    return true;
                }
            }
            return false;
        }

        int lakeY = 64;

        /** The wasteland over the scorched ground (and none past it), and the lake: its level, size, and open edges. */
        private void measure(ServerLevel level) {
            float scorch = this.profile.scorch(), crater = this.profile.crater();
            float paint = Math.max(scorch, crater * 1.5f);
            int in = 0, inPainted = 0, out = 0, outPainted = 0;
            for (float d = 0; d <= paint * 1.5f; d += 6) {
                for (int a = 0; a < 24; a++) {
                    double ang = Math.PI * 2 * a / 24;
                    int x = Mth.floor(this.at.getX() + 0.5 + Math.cos(ang) * d), z = Mth.floor(this.at.getZ() + 0.5 + Math.sin(ang) * d);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    boolean w = level.getBiome(new BlockPos(x, y, z)).is(Wasteland.BIOME);
                    if (d <= paint - 4) {
                        in++;
                        inPainted += w ? 1 : 0;
                    } else if (d >= paint + 6) {
                        out++;
                        outPainted += w ? 1 : 0;
                    }
                }
            }
            line(String.format(Locale.ROOT, "   wasteland: %d of %d points inside %.0f painted (%.0f%%), %d of %d past it", inPainted, in, paint,
                    100.0 * inPainted / Math.max(1, in), outPainted, out));
            // The crater: its floor and its rim (the lowest crest on rays out to 1.8 times the crater).
            int cx = this.at.getX(), cz = this.at.getZ();
            int floor = Integer.MAX_VALUE, rim = Integer.MAX_VALUE;
            for (int dx = -(int) (crater * 0.5f); dx <= crater * 0.5f; dx += 2) {
                for (int dz = -(int) (crater * 0.5f); dz <= crater * 0.5f; dz += 2) {
                    floor = Math.min(floor, level.getHeight(Heightmap.Types.OCEAN_FLOOR, cx + dx, cz + dz) - 1);
                }
            }
            for (int a = 0; a < 180; a++) {
                double ang = Math.PI * 2 * a / 180;
                int crest = Integer.MIN_VALUE;
                for (float d = crater * 0.85f; d <= crater * 1.8f; d += 1) {
                    crest = Math.max(crest, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(cx + 0.5 + Math.cos(ang) * d),
                            Mth.floor(cz + 0.5 + Math.sin(ang) * d)) - 1);
                }
                rim = Math.min(rim, crest);
            }
            int[] w = water(level);
            this.lakeY = w[2] == Integer.MIN_VALUE ? floor : w[2];
            line(String.format(Locale.ROOT, "   crater: floor y %d, lowest rim y %d, sea level %d; lake: %d water blocks (%d flowing) in %d columns, top at y %s; "
                    + "open edges at the top %d, holes under it %d", floor, rim, level.getSeaLevel(), w[0], w[1], w[3],
                    w[2] == Integer.MIN_VALUE ? "-" : Integer.toString(w[2]), w[4], w[5]));
        }

        /** Water in the crater: {blocks, flowing, the commonest top level, columns, open edges at the top, air under it}. */
        private int[] water(ServerLevel level) {
            int ext = (int) Math.ceil(this.profile.crater() * 1.9f);
            int blocks = 0, flowing = 0, columns = 0, open = 0, holes = 0;
            Map<Integer, Integer> tops = new HashMap<>();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -ext; dx <= ext; dx++) {
                for (int dz = -ext; dz <= ext; dz++) {
                    int x = this.at.getX() + dx, z = this.at.getZ() + dz;
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                    if (!level.getFluidState(p.set(x, top, z)).is(FluidTags.WATER)) {
                        continue;
                    }
                    columns++;
                    tops.merge(top, 1, Integer::sum);
                    int y = top;
                    while (level.getFluidState(p.set(x, y, z)).is(FluidTags.WATER) && y > level.getMinY()) {
                        blocks++;
                        if (!level.getFluidState(p).isSource()) {
                            flowing++;
                        }
                        y--;
                    }
                    if (level.getBlockState(p.set(x, y, z)).isAir()) {
                        holes++;
                    }
                    for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        BlockState n = level.getBlockState(p.set(x + dir.getStepX(), top, z + dir.getStepZ()));
                        if (n.isAir() || n.canBeReplaced() && n.getFluidState().isEmpty()) {
                            open++;
                        }
                    }
                }
            }
            int common = tops.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Integer.MIN_VALUE);
            return new int[] {blocks, flowing, common, columns, open, holes};
        }

        /** Stirs the lake's top layer (a fluid tick for each block of it), as a block placed beside it would. */
        private void stir(ServerLevel level) {
            int ext = (int) Math.ceil(this.profile.crater() * 1.9f);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int n = 0;
            for (int dx = -ext; dx <= ext; dx++) {
                for (int dz = -ext; dz <= ext; dz++) {
                    int x = this.at.getX() + dx, z = this.at.getZ() + dz;
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                    if (level.getFluidState(p.set(x, top, z)).is(FluidTags.WATER)) {
                        level.scheduleTick(p.immutable(), level.getFluidState(p).getType(), 1);
                        n++;
                    }
                }
            }
            line("   stirred " + n + " columns of the lake's top");
        }

        private void planPictures(ServerLevel level) {
            float c = this.profile.crater(), s = this.profile.scorch();
            Vec3 mid = Vec3.atCenterOf(this.at.atY(this.lakeY));
            ResourceKey<Level> dim = level.dimension();
            this.pictures.add(new Picture("blast_" + this.tag + "_a", dim, mid.add(c * 1.6, c * 1.3, c * 1.6), mid));
            this.pictures.add(new Picture("blast_" + this.tag + "_top", dim, mid.add(0.01, s * 1.9 + 20, 0.01), mid));
            int rx = this.at.getX() - (int) (c * 1.25f);
            int ry = level.getHeight(Heightmap.Types.MOTION_BLOCKING, rx, this.at.getZ());
            this.pictures.add(new Picture("blast_" + this.tag + "_shore", dim, new Vec3(rx + 0.5, ry + 3, this.at.getZ() + 0.5), mid.add(c * 0.5, -2, 0)));
            this.pictures.add(new Picture("blast_" + this.tag + "_edge", dim, mid.add(-s * 0.2, s * 0.45, s * 1.25), mid.add(0, 0, s * 0.85)));
        }
    }

    /** A spot on land near {@code from} (walking east a chunk at a time) in a wet biome, not on water. */
    static BlockPos pick(ServerLevel level, BlockPos from) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        for (int i = 0; i < 400; i++) {
            int x = from.getX() + i * 16, z = from.getZ();
            int top = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
            int floor = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
            if (floor < top - 1 || top < level.getSeaLevel() + 2 || top > level.getSeaLevel() + 24) {
                continue;
            }
            Holder<Biome> b = generator.getBiomeSource().createResolver(random.createClimateSampler(SamplerContext.builder().build()))
                    .getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(top), QuartPos.fromBlock(z));
            if (b.value().hasPrecipitation() && !b.is(net.minecraft.tags.BiomeTags.IS_RIVER) && !b.is(net.minecraft.tags.BiomeTags.IS_BEACH)) {
                return new BlockPos(x, top, z);
            }
        }
        return from;
    }

    static String biome(ServerLevel level, BlockPos at) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        return generator.getBiomeSource().createResolver(random.createClimateSampler(SamplerContext.builder().build()))
                .getNoiseBiome(QuartPos.fromBlock(at.getX()), QuartPos.fromBlock(at.getY()), QuartPos.fromBlock(at.getZ()))
                .unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
    }

    // --- the report -------------------------------------------------------------------------------------------------------
    private static void line(String s) {
        out.append(s).append('\n');
        GroundZero.LOGGER.info("World survey: {}", s);
    }

    private static void finish(MinecraftServer server) {
        stage = Stage.FINISHED;
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path dir = server.getServerDirectory().resolve("survey");
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("survey_" + stamp + ".txt"), out);
            Files.writeString(dir.resolve("sites_" + stamp + ".csv"), csv);
            report = dir.resolve("survey_" + stamp + ".txt").toAbsolutePath().toString();
            GroundZero.LOGGER.info("World survey: report written to {}", report);
        } catch (IOException e) {
            GroundZero.LOGGER.error("World survey: could not write the report", e);
        }
    }
}
