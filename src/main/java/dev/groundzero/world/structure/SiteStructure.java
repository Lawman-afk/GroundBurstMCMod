package dev.groundzero.world.structure;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
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
 * <li>{@code surface}: on the ground where it is flat enough ({@code max_slope} between the footprint's corners and
 * middle), never on water;</li>
 * <li>{@code buried}: {@code depth} blocks under the ground (a cave dug into the rock);</li>
 * <li>{@code floating}: hanging over the End's outer islands (or the void beside them).</li>
 * </ul>
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

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        WorldgenRandom random = context.random();
        Variant variant = pick(random);
        Rotation rotation = Rotation.getRandom(random);
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        StructureTemplateManager templates = context.structureTemplateManager();
        // The footprint (every part, turned), to read the ground at its corners.
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Part part : variant.parts()) {
            Vec3i size = templates.getOrCreate(part.template()).getSize(rotation);
            BlockPos corner = part.offset().rotate(rotation);
            BlockPos far = corner.offset(farCorner(size, rotation));
            minX = Math.min(minX, Math.min(corner.getX(), far.getX()));
            maxX = Math.max(maxX, Math.max(corner.getX(), far.getX()));
            minZ = Math.min(minZ, Math.min(corner.getZ(), far.getZ()));
            maxZ = Math.max(maxZ, Math.max(corner.getZ(), far.getZ()));
        }
        int[] ground = {
                height(context, x + minX, z + minZ), height(context, x + maxX, z + minZ), height(context, x + minX, z + maxZ),
                height(context, x + maxX, z + maxZ), height(context, x, z)};
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, sum = 0;
        for (int h : ground) {
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
            sum += h;
        }
        int y;
        int floor = context.heightAccessor().getMinY();
        switch (this.placement) {
            case SURFACE -> {
                if (hi - lo > this.maxSlope) {
                    return Optional.empty();
                }
                // Not on water: the sea floor under it must be the surface.
                int seabed = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(),
                        context.randomState());
                if (seabed < ground[4] - 1) {
                    return Optional.empty();
                }
                y = sum / ground.length;
            }
            case BURIED -> {
                y = lo - this.depth;
                if (y < floor + 8) {
                    return Optional.empty();
                }
            }
            case FLOATING -> {
                int island = ground[4];
                y = island <= floor + 1 ? 60 + random.nextInt(12) : island + 10 + random.nextInt(10);
            }
            default -> throw new IllegalStateException();
        }
        BlockPos origin = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(origin, builder -> {
            for (Part part : variant.parts()) {
                builder.addPiece(new SitePiece(templates, part, origin.offset(part.offset().rotate(rotation)), rotation));
            }
        }));
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
        return GZStructures.SITE.get();
    }
}
