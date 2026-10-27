package dev.groundzero.world.structure;

import dev.groundzero.content.gear.GZArmor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * One template of a {@link SiteStructure}, turned with the site, weathered ({@link Weathering}) and decayed (its
 * integrity), with the ground meeting it its own way (NeoForge's {@link PieceBeardifierModifier}). Data markers in the
 * template (structure blocks in data mode) become what cannot be a block: {@code stand:hazmat}, {@code stand:lead} and
 * {@code stand:bare} armour stands (wearing a hazmat suit, a lead-lined one, nothing), facing the template's south.
 */
public class SitePiece extends TemplateStructurePiece implements PieceBeardifierModifier {
    /** How far rock closed round a part underground reaches past its box (vanilla's encapsulation: 12 blocks). */
    static final int ENCAPSULATE_REACH = 12;
    /** The deepest a part on the surface is filled under where it overhangs the land. */
    static final int FOUNDATION = 16;
    private final TerrainAdjustment terrain;
    private final float integrity;
    private final String weathering;
    /** The site's ground: the height its surface stands at (a part may reach below it, or lie deep under it). */
    private final int ground;

    public SitePiece(StructureTemplateManager templates, SiteStructure.Part part, BlockPos corner, Rotation rotation, int ground) {
        super(GZStructures.SITE_PIECE.get(), 0, templates, part.template(), part.template().toString(),
                settings(rotation, part.integrity(), part.weathering()), corner);
        this.terrain = part.terrain();
        this.integrity = part.integrity();
        this.weathering = part.weathering();
        this.ground = ground;
    }

    public SitePiece(StructureTemplateManager templates, CompoundTag tag) {
        super(GZStructures.SITE_PIECE.get(), tag, templates, id -> settings(tag.read("Rot", Rotation.LEGACY_CODEC).orElse(Rotation.NONE),
                tag.getFloatOr("Integrity", 1), tag.getStringOr("Weathering", Weathering.NONE)));
        this.terrain = tag.read("Terrain", TerrainAdjustment.CODEC).orElse(TerrainAdjustment.NONE);
        this.integrity = tag.getFloatOr("Integrity", 1);
        this.weathering = tag.getStringOr("Weathering", Weathering.NONE);
        this.ground = tag.getIntOr("Ground", this.boundingBox.minY());
    }

    static StructurePlaceSettings settings(Rotation rotation, float integrity, String weathering) {
        StructurePlaceSettings s = new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        Weathering.processors(weathering).forEach(s::addProcessor);
        if (integrity < 1) {
            s.addProcessor(new BlockRotProcessor(integrity));
        }
        return s;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
        tag.store("Terrain", TerrainAdjustment.CODEC, this.terrain);
        tag.putFloat("Integrity", this.integrity);
        tag.putString("Weathering", this.weathering);
        tag.putInt("Ground", this.ground);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunk,
                            ChunkPos chunkPos, BlockPos reference) {
        super.postProcess(level, structures, generator, random, chunk, chunkPos, reference);
        if (this.terrain == TerrainAdjustment.BEARD_THIN) {
            foundation(level, chunk);
        }
    }

    /**
     * Fills under a part's bottom where it overhangs the land (its downhill edge on a slope the beard did not fill in
     * all the way), down to the ground: dirt where the ground is soil, else the ground's own block, as vanilla fills
     * under its desert temples and igloos.
     */
    private void foundation(WorldGenLevel level, BoundingBox chunk) {
        BoundingBox b = this.boundingBox;
        int bottom = b.minY();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(b.minX(), chunk.minX()); x <= Math.min(b.maxX(), chunk.maxX()); x++) {
            for (int z = Math.max(b.minZ(), chunk.minZ()); z <= Math.min(b.maxZ(), chunk.maxZ()); z++) {
                BlockState base = level.getBlockState(at.set(x, bottom, z));
                if (base.isAir() || !base.getFluidState().isEmpty() || !open(level.getBlockState(at.set(x, bottom - 1, z)))) {
                    continue;
                }
                int y = bottom - 1;
                while (y > level.getMinY() + 1 && y > bottom - FOUNDATION && open(level.getBlockState(at.set(x, y, z)))) {
                    y--;
                }
                BlockState ground = level.getBlockState(at.set(x, y, z));
                BlockState fill = ground.is(net.minecraft.tags.BlockTags.DIRT) || !ground.isSolidRender() ? Blocks.DIRT.defaultBlockState() : ground;
                for (int k = y + 1; k < bottom; k++) {
                    level.setBlock(at.set(x, k, z), fill, 2);
                }
            }
        }
    }

    private static boolean open(BlockState state) {
        return state.isAir() || state.liquid() || state.canBeReplaced();
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos at, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        if (!marker.startsWith("stand:") || !box.isInside(at)) {
            return;
        }
        ArmorStand stand = new ArmorStand(level.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        stand.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw(this.placeSettings.getRotation()), 0);
        var set = switch (marker) {
            case "stand:hazmat" -> GZArmor.HAZMAT_SET;
            case "stand:lead" -> GZArmor.LEAD_HAZMAT_SET;
            default -> null;
        };
        if (set != null) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int k = 0; k < 4; k++) {
                if (random.nextFloat() < 0.85f) {                 // a piece or two gone now and then
                    stand.setItemSlot(slots[k], new ItemStack(set.get(k).get()));
                }
            }
        }
        level.addFreshEntity(stand);
    }

    /** The yaw a stand facing the template's south (+z) has once the site is turned. */
    private static float yaw(Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 270;
        };
    }

    /**
     * The box the ground is shaped round. Rock closed round a part underground reaches {@link #ENCAPSULATE_REACH} blocks
     * past its box, so a part whose top lies near the site's ground (a silo's shaft, a bunker's rooms under its
     * blockhouse) has its box cut down to {@code ground - 13}: the ground over it is left as it is, rather than heaped up
     * over the site (the world survey found a bunker buried in a wooded mound before this).
     */
    @Override
    public BoundingBox getBeardifierBox() {
        BoundingBox b = this.boundingBox;
        if (this.terrain == TerrainAdjustment.ENCAPSULATE && b.maxY() > this.ground - 1 - ENCAPSULATE_REACH) {
            int top = Math.max(b.minY(), this.ground - 1 - ENCAPSULATE_REACH);
            return new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), top, b.maxZ());
        }
        return b;
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return this.terrain;
    }

    /**
     * Where the ground meets a part on the surface, counted from its box's bottom: the site's ground, so a part that
     * reaches below it (a test site's crater, a crashed missile's furrow) has the land smoothed to its rim, not to the
     * bottom of its pit.
     */
    @Override
    public int getGroundLevelDelta() {
        return this.terrain == TerrainAdjustment.BEARD_THIN ? Math.max(0, this.ground - this.boundingBox.minY()) : 0;
    }

    /** The site's ground (the world survey). */
    public int ground() {
        return this.ground;
    }

    /** How the site is turned (the world survey). */
    public Rotation rotation() {
        return this.placeSettings.getRotation();
    }

    /** For previews and tests: the template this piece places. */
    public Identifier templateId() {
        return Identifier.parse(this.templateName);
    }
}
