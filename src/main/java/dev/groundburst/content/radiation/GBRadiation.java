package dev.groundburst.content.radiation;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.GroundBurst;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * The radiation system's registrations (DESIGN.md §21.8): the dose a living thing has taken (a data attachment, synced
 * to its player), what is radioactive (data maps on blocks and items), and the shielding tags.
 */
public final class GBRadiation {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            GroundBurst.MODID);

    /** The dose and dose rate of a living thing ({@link RadiationState}); a player's goes to them alone. */
    public static final Supplier<AttachmentType<RadiationState>> STATE = ATTACHMENTS.register("radiation",
            () -> AttachmentType.builder(() -> RadiationState.NONE).serialize(RadiationState.CODEC)
                    .sync((holder, to) -> holder == to, RadiationState.STREAM_CODEC).build());

    /** A dose rate in RAD/h: a block's a block away, or one item's to whoever carries it. */
    public record Radioactivity(float rate) {
        public static final Codec<Radioactivity> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, Float.MAX_VALUE).fieldOf("rate").forGetter(Radioactivity::rate)).apply(i, Radioactivity::new));
    }

    /** {@code groundburst:radioactivity} on blocks (data/groundburst/data_maps/block/radioactivity.json). */
    public static final DataMapType<Block, Radioactivity> BLOCK_RADIOACTIVITY = DataMapType.builder(GroundBurst.id("radioactivity"),
            Registries.BLOCK, Radioactivity.CODEC).synced(Radioactivity.CODEC, false).build();
    /** {@code groundburst:radioactivity} on items (data/groundburst/data_maps/item/radioactivity.json). */
    public static final DataMapType<Item, Radioactivity> ITEM_RADIOACTIVITY = DataMapType.builder(GroundBurst.id("radioactivity"),
            Registries.ITEM, Radioactivity.CODEC).synced(Radioactivity.CODEC, false).build();

    /** Blocks that stop radiation like lead (half of it in 0.2 blocks). */
    public static final TagKey<Block> SHIELD_LEAD = TagKey.create(Registries.BLOCK, GroundBurst.id("radiation_shielding/lead"));
    /** Dense blocks: obsidian, concrete, metal blocks (half of it in half a block). */
    public static final TagKey<Block> SHIELD_DENSE = TagKey.create(Registries.BLOCK, GroundBurst.id("radiation_shielding/dense"));

    private GBRadiation() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener((RegisterDataMapTypesEvent event) -> {
            event.register(BLOCK_RADIOACTIVITY);
            event.register(ITEM_RADIOACTIVITY);
        });
        RadiationSources.register();
        Emitters.register();
        Radiation.register();
    }
}
