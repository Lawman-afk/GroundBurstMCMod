package dev.groundburst.content.naval;

import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockDefinition.Entry;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.content.multiblock.StructureSpawnerItem;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBTab;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Naval weapons (DESIGN.md §14.6): sea mines, moored and drifting, and depth charges; their entities and dispensers; the
 * Depth Charge Rack that rolls charges off a ship's stern.
 */
public final class GBNaval {
    public static final DeferredHolder<EntityType<?>, EntityType<SeaMine>> SEA_MINE = GBOrdnance.ENTITIES.registerEntityType("sea_mine",
            SeaMine::new, MobCategory.MISC, b -> b.sized(1.0f, 1.0f).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<DepthCharge>> DEPTH_CHARGE_ENTITY = GBOrdnance.ENTITIES.registerEntityType(
            "depth_charge", DepthCharge::new, MobCategory.MISC, b -> b.sized(0.6f, 0.6f).clientTrackingRange(8).updateInterval(2));

    public static final DeferredItem<SeaMineItem> MOORED_MINE = GBItems.register("moored_mine", "Moored Sea Mine", GBTab.ORDNANCE,
            p -> new SeaMineItem(p, true), p -> p.stacksTo(8));
    public static final DeferredItem<SeaMineItem> DRIFTING_MINE = GBItems.register("drifting_mine", "Drifting Sea Mine", GBTab.ORDNANCE,
            p -> new SeaMineItem(p, false), p -> p.stacksTo(8));
    public static final DeferredItem<DepthChargeItem> DEPTH_CHARGE = GBItems.register("depth_charge", "Depth Charge", GBTab.ORDNANCE,
            DepthChargeItem::new, p -> p.stacksTo(16));

    // --- the Depth Charge Rack ---------------------------------------------------------------------------------------------
    /** The rack's middle: two rails sloping down to its release end (DESIGN.md §14.6). */
    public static final WithItem<MultiblockPartBlock> RAIL = GBBlocks.register("depth_charge_rail", "Depth Charge Rail", GBTab.ORDNANCE,
            MultiblockPartBlock::new, p -> p.mapColor(MapColor.METAL).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .noOcclusion().pushReaction(PushReaction.BLOCK));
    /** The rack's release end (its controller): the trap the charges roll off over. */
    public static final WithItem<DepthChargeRackBlock> RACK = GBBlocks.register("depth_charge_rack", "Depth Charge Rack", GBTab.ORDNANCE,
            p -> new DepthChargeRackBlock(p, GBNaval.RACK_ENTITY), p -> p.mapColor(MapColor.METAL).strength(5f, 12f).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DepthChargeRackBlockEntity>> RACK_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("depth_charge_rack", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(DepthChargeRackBlockEntity::new,
                    RACK.block().get()).build(null));
    /**
     * The rack (DESIGN.md §14.6): one block wide and four long, the release end (the controller) at its front over the
     * water, two Depth Charge Rails behind it and an Item Port at its top end, where pipes, hoppers and Create's funnels
     * load it.
     */
    public static final MultiblockDefinition RACK_STRUCTURE = new MultiblockDefinition(GroundBurst.id("depth_charge_rack"),
            new String[][] {{"X", "R", "R", "I"}},
            Map.of('R', Entry.of(() -> RAIL.block().get()), 'I', Entry.of(GBMultiblocks.ITEM_PORT.block()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> RACK.block().get())));
    /** Creative: places a whole Depth Charge Rack where you click. */
    public static final DeferredItem<StructureSpawnerItem> RACK_SPAWNER = GBItems.register("depth_charge_rack_spawner",
            "Structure Spawner: Depth Charge Rack", GBTab.ORDNANCE, p -> new StructureSpawnerItem(p, () -> RACK_STRUCTURE),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final java.util.function.Supplier<SoundEvent> RACK_RELEASE = dev.groundburst.registry.GBSounds.RACK_RELEASE;
    /** JEI and the Field Manual: how the rack is built and used. */
    public static final String INFO_RACK = GBLang.add(GroundBurst.MODID + ".info.depth_charge_rack",
            "Rolls depth charges off a ship's stern. Build it one block wide and four long: the Depth Charge Rack at the end "
                    + "over the water (placed facing out), two Depth Charge Rails behind it and an Item Port at the top end. Load "
                    + "up to six charges by hand, or by pipe, hopper or Create's funnels into the Item Port: they roll down to the "
                    + "trap. A redstone pulse on any of its blocks rolls one off; a held signal rolls one off each interval, a "
                    + "pattern. Its screen releases one or all of them, sets the interval, and can set every charge it rolls off "
                    + "to one depth. A comparator reads how many it holds. It works aboard a Create ship, and on a contraption from "
                    + "its screen.");

    static {
        GBLang.add("entity." + GroundBurst.MODID + ".sea_mine", "Sea Mine");
        GBLang.add("entity." + GroundBurst.MODID + ".depth_charge", "Depth Charge");
    }

    private GBNaval() {}

    public static void init() {}

    /** {@code stack} dropped as an item at {@code at} (a mine taken up, a depth charge come to rest on dry ground). */
    static void drop(net.minecraft.server.level.ServerLevel level, Vec3 at, ItemStack stack) {
        var item = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(GBNaval::dispensers));
    }

    /**
     * A dispenser rolls a depth charge out of its front (a ship's stern rack: a short shove), and lays a sea mine in the
     * water in front of it or up to two blocks under its front (a minelayer); with no water there, it drops the mine.
     */
    private static void dispensers() {
        DispenserBlock.registerBehavior(DEPTH_CHARGE.get(), new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                Direction d = source.state().getValue(DispenserBlock.FACING);
                Vec3 at = source.center().add(d.getStepX() * 0.8, d.getStepY() * 0.8, d.getStepZ() * 0.8);
                Vec3 v = new Vec3(d.getStepX() * 0.25, d == Direction.DOWN ? -0.1 : d == Direction.UP ? 0.4 : 0.06, d.getStepZ() * 0.25);
                DepthCharge.release(source.level(), at, v, stack, null);
                stack.shrink(1);
                return stack;
            }
        });
        DefaultDispenseItemBehavior lay = new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                Direction d = source.state().getValue(DispenserBlock.FACING);
                BlockPos front = source.pos().relative(d);
                for (int i = 0; i < 3 && stack.getItem() instanceof SeaMineItem mine; i++) {
                    BlockPos at = front.below(i);
                    if (Water.water(source.level(), at)) {
                        SeaMine.lay(source.level(), Vec3.atCenterOf(at), mine.moored(), SeaMineItem.depthOf(stack), null);
                        stack.shrink(1);
                        return stack;
                    }
                }
                return super.execute(source, stack);
            }
        };
        DispenserBlock.registerBehavior(MOORED_MINE.get(), lay);
        DispenserBlock.registerBehavior(DRIFTING_MINE.get(), lay);
    }
}
