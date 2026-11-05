package dev.groundburst.content.naval;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.ordnance.GBOrdnance;
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
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Naval weapons (DESIGN.md §14.6): sea mines, moored and drifting, and depth charges; their entities and dispensers. */
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
