package dev.groundburst.content.defence;

import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.machine.GeoBlockItem;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBTab;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Missile defence (DESIGN.md §18.2, Phase 10): the Interceptor Battery, its interceptor missiles, and the interceptors
 * in the air.
 */
public final class GBDefence {
    public static final WithItem<TallMachineBlock> BATTERY = GBBlocks.registerWithItem("interceptor_battery", "Interceptor Battery", GBTab.MISSILES,
            p -> new TallMachineBlock(p, GBDefence.BATTERY_ENTITY, true,
                    Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(1, 2, 1, 15, 16, 15)),
                    Block.box(2, 0, 2, 14, 13, 14)),
            p -> p.mapColor(MapColor.COLOR_GREEN).strength(5f, 10f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .pushReaction(PushReaction.IMMOVEABLE),
            (block, props) -> new GeoBlockItem(block, props, GroundBurst.id("interceptor_battery")));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, BlockEntityType<InterceptorBatteryBlockEntity>> BATTERY_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("interceptor_battery", () -> new BlockEntityType<>(InterceptorBatteryBlockEntity::new,
                    BATTERY.block().get()));

    /** An interceptor missile: the battery's ammunition, four to its cells. */
    public static final DeferredItem<InterceptorMissile> INTERCEPTOR_MISSILE = GBItems.register("interceptor_missile", "Interceptor Missile",
            GBTab.MISSILES, InterceptorMissile::new, p -> p.stacksTo(16));

    /** An interceptor in the air (its picture: Interceptors flies it). */
    public static final DeferredHolder<EntityType<?>, EntityType<InterceptorEntity>> INTERCEPTOR = GBLaunch.ENTITIES.registerEntityType("interceptor",
            InterceptorEntity::new, MobCategory.MISC, b -> b.sized(0.4f, 0.4f).clientTrackingRange(16).updateInterval(1));
    static final String T_ENTITY = GBLang.add("entity." + GroundBurst.MODID + ".interceptor", "Interceptor");

    /** JEI's page for the battery and its missiles. */
    public static final String INFO = GBLang.add(GroundBurst.MODID + ".jei.info.interceptor_battery",
            "The Interceptor Battery shoots down missiles coming down near it. Load Interceptor Missiles into it (by hand or "
                    + "by pipe): its four cells fill from its store, one every three seconds. Powered, it watches the sky: a missile "
                    + "bound for within 256 blocks of it, if a working Radar Array tracks it, or 96 on its own seeker, draws an "
                    + "interceptor, two for class L and bigger missiles. Each kills its missile 80%% of the time against a small one, "
                    + "less against bigger ones (48%% a Strategic missile, 40%% an intercontinental one), against those coming down "
                    + "fast from high up, and a superweapon's; without radar, six tenths as often. A missile shot down breaks up in the air, its "
                    + "warhead never going off. Missiles flying out from near it, bound elsewhere, it lets go.");

    private GBDefence() {}

    /** Loads the registrations; forgets a level's interceptors and radars when it unloads. */
    public static void init() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                Interceptors.forget(level);
                Interception.forget(level);
            }
        });
    }

    /** The Interceptor Missile: what it is for. */
    public static class InterceptorMissile extends Item {
        static final String T_TIP = GBLang.add(GroundBurst.MODID + ".interceptor.tip", "Ammunition for an Interceptor Battery");
        static final String T_TIP2 = GBLang.add(GroundBurst.MODID + ".interceptor.tip2", "Climbs at 100 blocks a second; bursts beside its missile");

        public InterceptorMissile(Properties properties) {
            super(properties);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable(T_TIP).withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable(T_TIP2).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
