package dev.groundzero.world.structure;

import dev.groundzero.content.gear.GZArmor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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
    private final TerrainAdjustment terrain;
    private final float integrity;
    private final String weathering;

    public SitePiece(StructureTemplateManager templates, SiteStructure.Part part, BlockPos corner, Rotation rotation) {
        super(GZStructures.SITE_PIECE.get(), 0, templates, part.template(), part.template().toString(),
                settings(rotation, part.integrity(), part.weathering()), corner);
        this.terrain = part.terrain();
        this.integrity = part.integrity();
        this.weathering = part.weathering();
    }

    public SitePiece(StructureTemplateManager templates, CompoundTag tag) {
        super(GZStructures.SITE_PIECE.get(), tag, templates, id -> settings(tag.read("Rot", Rotation.LEGACY_CODEC).orElse(Rotation.NONE),
                tag.getFloatOr("Integrity", 1), tag.getStringOr("Weathering", Weathering.NONE)));
        this.terrain = tag.read("Terrain", TerrainAdjustment.CODEC).orElse(TerrainAdjustment.NONE);
        this.integrity = tag.getFloatOr("Integrity", 1);
        this.weathering = tag.getStringOr("Weathering", Weathering.NONE);
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
     * The box the ground is shaped round. Rock closed round a part underground reaches about 12 blocks past its box, so
     * a part whose top lies just under a hardstand (a silo's shaft, a bunker's well) keeps it 13 blocks lower: the
     * ground round its top is left as it is, rather than heaped up over the site.
     */
    @Override
    public BoundingBox getBeardifierBox() {
        if (this.terrain == TerrainAdjustment.ENCAPSULATE && this.boundingBox.getYSpan() > 16) {
            BoundingBox b = this.boundingBox;
            return new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY() - 13, b.maxZ());
        }
        return this.boundingBox;
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return this.terrain;
    }

    @Override
    public int getGroundLevelDelta() {
        return 0;
    }

    /** For previews and tests: the template this piece places. */
    public Identifier templateId() {
        return Identifier.parse(this.templateName);
    }
}
