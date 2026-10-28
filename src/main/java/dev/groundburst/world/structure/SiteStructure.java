package dev.groundburst.world.structure;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * A site of the world's Cold War past (DESIGN.md §22, Phase 15): one or more structure templates placed together about
 * an origin on the ground at the site's middle, turned as one. Each part says how the ground meets it (NeoForge's
 * per-piece terrain adjustment, {@link SitePiece}): a building on the surface has the ground smoothed under and round
 * it, a room underground has rock closed round it, so a silo's shaft can run down from a pad that sits on the land.
 * Variants are picked by weight (a silo intact, flooded or collapsed). Placement:
 * <ul>
 * <li>{@code surface}: on the ground where it is flat enough ({@code max_slope} across a grid of columns over the
 * footprint), dry everywhere on it and no lower than the sea's level (ground below it would flood);</li>
 * <li>{@code buried}: {@code depth} blocks under the ground (a cave dug into the rock), under dry land only;</li>
 * <li>{@code floating}: hanging over one of the End's outer islands (never over the bare void).</li>
 * </ul>
 * The rules were checked on generated worlds with the world survey ({@link WorldSurvey}): a pad on a riverbank, a test
 * site ringed by a lake, a bunker buried in a wooded mound were what the first checks found.
 */
public class SiteStructure extends Structure {
    public enum Placement implements StringRepresentable {
        SURFACE("surface"), BURIED("buried"), FLOATING("floating");

        public static final Codec<Placement> CODEC = StringRepresentable.fromEnum(Placement::values);
        private final String id;

        Placement(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

    /**
     * A template of the site: where its corner sits from the origin (before turning), how the ground meets it, the
     * share of its blocks that survive ({@code integrity}, less for a ruin), and its weathering ({@link Weathering}).
     */
    public record Part(Identifier template, BlockPos offset, TerrainAdjustment terrain, float integrity, String weathering) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("template").forGetter(Part::template),
                BlockPos.CODEC.fieldOf("offset").forGetter(Part::offset),
                TerrainAdjustment.CODEC.optionalFieldOf("terrain", TerrainAdjustment.NONE).forGetter(Part::terrain),
                Codec.floatRange(0, 1).optionalFieldOf("integrity", 1f).forGetter(Part::integrity),
                Codec.STRING.optionalFieldOf("weathering", Weathering.NONE).forGetter(Part::weathering)
        ).apply(i, Part::new));
    }

    public record Variant(int weight, List<Part> parts) {
        public static final Codec<Variant> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1000).fieldOf("weight").forGetter(Variant::weight),
                Part.CODEC.listOf(1, 16).fieldOf("parts").forGetter(Variant::parts)
        ).apply(i, Variant::new));
    }

    public static final MapCodec<SiteStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Variant.CODEC.listOf(1, 16).fieldOf("variants").forGetter(s -> s.variants),
            Placement.CODEC.fieldOf("placement").forGetter(s -> s.placement),
            Codec.intRange(0, 64).optionalFieldOf("max_slope", 6).forGetter(s -> s.maxSlope),
            Codec.intRange(0, 128).optionalFieldOf("depth", 0).forGetter(s -> s.depth)
    ).apply(i, SiteStructure::new));

    private final List<Variant> variants;
    private final Placement placement;
    private final int maxSlope, depth;

    public SiteStructure(StructureSettings settings, List<Variant> variants, Placement placement, int maxSlope, int depth) {
        super(settings);
        this.variants = variants;
        this.placement = placement;
        this.maxSlope = maxSlope;
        this.depth = depth;
    }

    /** The ground is read on a grid this many columns a side over the footprint (its corners and middle among them). */
    static final int GRID = 5;

    /**
     * The ground under a site at a chunk, as its placement reads it: the land at the chunk's middle, the lowest and highest
     * land and its average over the parts that meet the surface (a part closed in by rock underground does not care how
     * the land lies over it), and whether water lies anywhere over the whole footprint.
     */
    public record Ground(int middle, int lo, int hi, int average, boolean wet) {
        public int range() {
            return this.hi - this.lo;
        }
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        WorldgenRandom random = context.random();
        Variant variant = pick(random);
        Rotation rotation = Rotation.getRandom(random);
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        int middle = height(context, x, z);
        // The wrong land: no need to read its ground (the biome is checked again where the site's origin ends up).
        if (!context.validBiome().test(context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(middle),
                QuartPos.fromBlock(z)))) {
            return Optional.empty();
        }
        StructureTemplateManager templates = context.structureTemplateManager();
        int floor = context.heightAccessor().getMinY();
        if (this.placement == Placement.FLOATING) {
            if (middle <= floor + 1) {
                return Optional.empty();                   // the bare void: only over an island
            }
            BlockPos origin = new BlockPos(x, middle + 10 + random.nextInt(10), z);
            return Optional.of(stub(templates, variant, rotation, origin, middle));
        }
        Ground ground = read(context, variant, rotation, x, z, middle);
        int y;
        if (this.placement == Placement.SURFACE) {
            if (ground.range() > this.maxSlope || ground.wet() || ground.average() < context.chunkGenerator().getSeaLevel()) {
                return Optional.empty();                   // too steep, on water, or low enough to flood
            }
            y = ground.average();
        } else {
            y = ground.lo() - this.depth;
            if (y < floor + 8 || ground.wet()) {
                return Optional.empty();                   // too deep, or under the sea, a lake or a river
            }
        }
        return Optional.of(stub(templates, variant, rotation, new BlockPos(x, y, z), ground.average()));
    }

    /** The ground a site would read at the context's chunk (the world survey's census), with the variant and turn it would pick. */
    public Ground ground(GenerationContext context) {
        WorldgenRandom random = context.random();
        Variant variant = pick(random);
        Rotation rotation = Rotation.getRandom(random);
        int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
        return read(context, variant, rotation, x, z, height(context, x, z));
    }

    private Ground read(GenerationContext context, Variant variant, Rotation rotation, int x, int z, int middle) {
        StructureTemplateManager templates = context.structureTemplateManager();
        int[] land = footprint(templates, variant, rotation, true), all = footprint(templates, variant, rotation, false);
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, sum = 0;
        boolean wet = false;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                int lx = x + land[0] + (land[2] - land[0]) * i / (GRID - 1), lz = z + land[1] + (land[3] - land[1]) * j / (GRID - 1);
                int h = height(context, lx, lz);
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
                sum += h;
                int wx = x + all[0] + (all[2] - all[0]) * i / (GRID - 1), wz = z + all[1] + (all[3] - all[1]) * j / (GRID - 1);
                wet |= wx == lx && wz == lz ? wet(context, lx, lz, h) : wet(context, wx, wz, height(context, wx, wz));
            }
        }
        return new Ground(middle, lo, hi, Math.round((float) sum / (GRID * GRID)), wet);
    }

    /**
     * The footprint of a variant turned, from the origin: {minX, minZ, maxX, maxZ}. {@code land}: only the parts that meet
     * the surface (all of them if none does).
     */
    private static int[] footprint(StructureTemplateManager templates, Variant variant, Rotation rotation, boolean land) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean any = variant.parts().stream().anyMatch(p -> p.terrain() != TerrainAdjustment.ENCAPSULATE);
        for (Part part : variant.parts()) {
            if (land && any && part.terrain() == TerrainAdjustment.ENCAPSULATE) {
                continue;
            }
            Vec3i size = templates.getOrCreate(part.template()).getSize(rotation);
            BlockPos corner = part.offset().rotate(rotation);
            BlockPos far = corner.offset(farCorner(size, rotation));
            minX = Math.min(minX, Math.min(corner.getX(), far.getX()));
            maxX = Math.max(maxX, Math.max(corner.getX(), far.getX()));
            minZ = Math.min(minZ, Math.min(corner.getZ(), far.getZ()));
            maxZ = Math.max(maxZ, Math.max(corner.getZ(), far.getZ()));
        }
        return new int[] {minX, minZ, maxX, maxZ};
    }

    private static GenerationStub stub(StructureTemplateManager templates, Variant variant, Rotation rotation, BlockPos origin, int ground) {
        return new GenerationStub(origin, builder -> {
            for (Part part : variant.parts()) {
                builder.addPiece(new SitePiece(templates, part, origin.offset(part.offset().rotate(rotation)), rotation, ground));
            }
        });
    }

    /** Where a template's far corner lies from its near one once turned (it turns about its near corner). */
    private static BlockPos farCorner(Vec3i rotatedSize, Rotation rotation) {
        int sx = rotatedSize.getX() - 1, sz = rotatedSize.getZ() - 1;
        return switch (rotation) {
            case NONE -> new BlockPos(sx, 0, sz);
            case CLOCKWISE_90 -> new BlockPos(-sx, 0, sz);
            case CLOCKWISE_180 -> new BlockPos(-sx, 0, -sz);
            case COUNTERCLOCKWISE_90 -> new BlockPos(sx, 0, -sz);
        };
    }

    /** Whether water lies over the ground at a column: the solid ground under it is not its surface. */
    private static boolean wet(GenerationContext context, int x, int z, int surface) {
        return context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(),
                context.randomState()) < surface;
    }

    private static int height(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
                context.randomState());
    }

    private Variant pick(WorldgenRandom random) {
        int total = this.variants.stream().mapToInt(Variant::weight).sum();
        int roll = random.nextInt(total);
        for (Variant v : this.variants) {
            roll -= v.weight();
            if (roll < 0) {
                return v;
            }
        }
        return this.variants.getFirst();
    }

    /** The variants (for previews). */
    public List<Variant> variants() {
        return this.variants;
    }

    @Override
    public StructureType<?> type() {
        return GBStructures.SITE.get();
    }
}
