package dev.groundburst.content.pipe;

import java.util.List;

import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Item pipes (DESIGN.md §6.5), three tiers like the fluid pipes and cables. */
public final class GBPipes {
    public static final GBBlocks.WithItem<ItemPipeBlock> IRON_ITEM_PIPE = pipe(ItemPipeTier.IRON, MapColor.METAL);
    public static final GBBlocks.WithItem<ItemPipeBlock> ITEM_PIPE = pipe(ItemPipeTier.STEEL, MapColor.METAL);
    public static final GBBlocks.WithItem<ItemPipeBlock> PNEUMATIC_ITEM_PIPE = pipe(ItemPipeTier.PNEUMATIC, MapColor.COLOR_YELLOW);

    private GBPipes() {}

    public static void init() {}

    public static List<GBBlocks.WithItem<ItemPipeBlock>> itemPipes() {
        return List.of(IRON_ITEM_PIPE, ITEM_PIPE, PNEUMATIC_ITEM_PIPE);
    }

    private static GBBlocks.WithItem<ItemPipeBlock> pipe(ItemPipeTier tier, MapColor color) {
        return GBBlocks.register(tier.id(), tier.english(), GBTab.MACHINES, p -> new ItemPipeBlock(p, tier),
                p -> p.mapColor(color).strength(2f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().forceSolidOn());
    }

    /** Any side but a switched-off one takes items into the run (a hopper above, a machine's auto-eject). */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.Item.BLOCK,
                (level, pos, state, be, side) -> side != null && PipeBlock.mode(state, side) == PipeBlock.Mode.DISABLED
                        ? null : ItemPipeNetwork.handler(level, pos, side),
                itemPipes().stream().map(p -> (Block) p.block().get()).toArray(Block[]::new));
    }
}
