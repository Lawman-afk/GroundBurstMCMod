package dev.groundzero.content.radiation;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;

/**
 * Radioactive blocks and items (DESIGN.md §21.8): their strength is the {@code groundzero:radioactivity} data map (a
 * block's dose rate a block away, an item's per item carried, RAD/h). Blocks within {@link #REACH} of a body count at
 * full strength within a block of it and by the inverse square beyond, through the blocks between; carried items count
 * in full, but nothing inside a Lead Container.
 */
public final class Emitters {
    /** How far radioactive blocks are felt, blocks. */
    public static final int REACH = 6;

    /** Strengths by block and item, looked up once each (cleared when data maps reload). */
    private static final Map<Block, Float> BLOCKS = new IdentityHashMap<>();
    private static final Map<Item, Float> ITEMS = new IdentityHashMap<>();

    private Emitters() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((DataMapsUpdatedEvent event) -> {
            synchronized (BLOCKS) {
                BLOCKS.clear();
            }
            synchronized (ITEMS) {
                ITEMS.clear();
            }
        });
    }

    /** A block's dose rate a block away, RAD/h (0: not radioactive). */
    public static float radioactivity(BlockState state) {
        Block block = state.getBlock();
        synchronized (BLOCKS) {
            Float value = BLOCKS.get(block);
            if (value == null) {
                GZRadiation.Radioactivity r = block.builtInRegistryHolder().getData(GZRadiation.BLOCK_RADIOACTIVITY);
                value = r == null ? 0f : r.rate();
                BLOCKS.put(block, value);
            }
            return value;
        }
    }

    /** One item's dose rate to whoever carries it, RAD/h (0: not radioactive). */
    public static float radioactivity(Item item) {
        synchronized (ITEMS) {
            Float value = ITEMS.get(item);
            if (value == null) {
                GZRadiation.Radioactivity r = item.builtInRegistryHolder().getData(GZRadiation.ITEM_RADIOACTIVITY);
                value = r == null ? 0f : r.rate();
                ITEMS.put(item, value);
            }
            return value;
        }
    }

    /**
     * A carried stack's dose rate, RAD/h: its strength times its count, and what is inside it if it is a bag or box (a
     * bundle, a shulker box: cloth and wood stop nothing). A Lead Container's contents give none.
     */
    public static float carried(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        float sum = radioactivity(stack.getItem()) * stack.getCount();
        if (stack.getItem() instanceof LeadContainerItem) {
            return sum;
        }
        var bundle = stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStackTemplate inside : bundle.items()) {
                sum += radioactivity(inside.item().value()) * inside.count();
            }
        }
        var container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (container != null) {
            for (ItemStackTemplate inside : container.nonEmptyItems()) {
                sum += radioactivity(inside.item().value()) * inside.count();
            }
        }
        return sum;
    }

    /** Everything {@code body} carries, armour and both hands included, RAD/h. */
    public static float carried(LivingEntity body) {
        float sum = 0;
        if (body instanceof Player player) {
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                sum += carried(inventory.getItem(i));
            }
        } else {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                sum += carried(body.getItemBySlot(slot));
            }
        }
        return sum;
    }

    /** The dose rate at {@code eye} from radioactive blocks within {@link #REACH}, through what lies between, RAD/h. */
    public static float blocks(Level level, Vec3 eye) {
        int minX = Mth.floor(eye.x) - REACH, maxX = Mth.floor(eye.x) + REACH;
        int minY = Mth.floor(eye.y) - REACH, maxY = Mth.floor(eye.y) + REACH;
        int minZ = Mth.floor(eye.z) - REACH, maxZ = Mth.floor(eye.z) + REACH;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        float sum = 0;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSectionsCount()) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(index);
                    // The palette says at once whether this section holds anything radioactive at all.
                    if (section.hasOnlyAir() || !section.maybeHas(s -> radioactivity(s) > 0)) {
                        continue;
                    }
                    int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
                    int y0 = Math.max(minY, sy << 4), y1 = Math.min(maxY, (sy << 4) + 15);
                    int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);
                    for (int y = y0; y <= y1; y++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                float value = radioactivity(section.getBlockState(x & 15, y & 15, z & 15));
                                if (value <= 0) {
                                    continue;
                                }
                                double ex = x + 0.5 - eye.x, ey = y + 0.5 - eye.y, ez = z + 0.5 - eye.z;
                                double d2 = ex * ex + ey * ey + ez * ez;
                                if (d2 > REACH * REACH) {
                                    continue;
                                }
                                at.set(x, y, z);
                                double share = Shielding.line(level, new Vec3(x + 0.5, y + 0.5, z + 0.5), eye, at);
                                sum += (float) (value * share / Math.max(1, d2));
                            }
                        }
                    }
                }
            }
        }
        return sum;
    }
}
