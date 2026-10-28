package dev.groundburst.world.structure;

import java.util.List;

import dev.groundburst.GroundBurst;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The world's structures (DESIGN.md §22, Phase 15): the site structure type and its piece, the structures' keys, their
 * biome tags (which biomes each may stand in: {@code groundburst:has_structure/<name>}, a pack can change them) and
 * their loot tables. The structures themselves are data ({@code GBStructureData}); their templates come from
 * {@code tools/structure/sites.py}.
 */
public final class GBStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, GroundBurst.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, GroundBurst.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<SiteStructure>> SITE = TYPES.register("site", () -> () -> SiteStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> SITE_PIECE = PIECES.register("site_piece",
            () -> (StructurePieceType.StructureTemplateType) SitePiece::new);

    /** The structures, by name (each with a structure set and a biome tag of the same name). */
    public static final List<String> NAMES = List.of("abandoned_silo", "command_bunker", "radar_outpost", "test_site", "crashed_missile",
            "waste_storage", "exotic_research_station");

    private GBStructures() {}

    public static ResourceKey<Structure> structure(String name) {
        return ResourceKey.create(Registries.STRUCTURE, GroundBurst.id(name));
    }

    public static ResourceKey<StructureSet> set(String name) {
        return ResourceKey.create(Registries.STRUCTURE_SET, GroundBurst.id(name));
    }

    public static TagKey<Biome> biomes(String name) {
        return TagKey.create(Registries.BIOME, GroundBurst.id("has_structure/" + name));
    }

    public static ResourceKey<LootTable> loot(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, GroundBurst.id("chests/" + name));
    }

    /**
     * Places a variant of a site unturned with its origin at {@code origin}, the way world generation would (its pieces
     * weathered and decayed, its markers turned to stands), but on whatever ground is there (no terrain adaptation):
     * for previews and tests. {@code ground}: first fill stone round the parts that lie under the origin (a flat world is
     * too thin for them). {@code cutZ}: then clear everything north of that z, to show the rooms.
     */
    public static net.minecraft.world.level.levelgen.structure.BoundingBox preview(net.minecraft.server.level.ServerLevel level, String name,
            int variant, net.minecraft.core.BlockPos origin, boolean ground, @org.jspecify.annotations.Nullable Integer cutZ) {
        SiteStructure site = (SiteStructure) level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(structure(name));
        var templates = level.getStructureTemplateManager();
        java.util.List<SitePiece> pieces = new java.util.ArrayList<>();
        for (SiteStructure.Part part : site.variants().get(variant).parts()) {
            pieces.add(new SitePiece(templates, part, origin.offset(part.offset()), net.minecraft.world.level.block.Rotation.NONE, origin.getY()));
        }
        net.minecraft.world.level.levelgen.structure.BoundingBox all = net.minecraft.world.level.levelgen.structure.BoundingBox
                .encapsulatingBoxes(pieces.stream().map(SitePiece::getBoundingBox).toList()).orElseThrow();
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        // A cut is made by never placing what lies north of it (clearing it afterwards would leave the light there stale).
        int fromZ = cutZ == null ? Integer.MIN_VALUE : cutZ;
        if (ground) {
            for (var p : net.minecraft.core.BlockPos.betweenClosed(all.minX() - 3, all.minY() - 3, Math.max(all.minZ() - 3, fromZ),
                    all.maxX() + 3, origin.getY() - 1, all.maxZ() + 3)) {
                level.setBlock(p, stone, 2);
            }
        }
        var random = net.minecraft.util.RandomSource.create(origin.asLong());
        for (SitePiece piece : pieces) {
            var box = piece.getBoundingBox();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    if ((cz << 4) + 15 < fromZ) {
                        continue;
                    }
                    var chunkBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx << 4, box.minY(),
                            Math.max(cz << 4, fromZ), (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
                    piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), random, chunkBox,
                            new net.minecraft.world.level.ChunkPos(cx, cz), origin);
                }
            }
        }
        if (cutZ != null) {
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            for (var p : net.minecraft.core.BlockPos.betweenClosed(all.minX() - 3, all.minY() - 3, all.minZ() - 3, all.maxX() + 3,
                    all.maxY() + 3, cutZ - 1)) {
                if (!level.getBlockState(p).isAir()) {
                    level.setBlock(p, air, 2 | 16);
                }
            }
        }
        return all;
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
        WorldSurvey.register();
    }
}
