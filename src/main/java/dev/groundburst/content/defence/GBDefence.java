package dev.groundburst.content.defence;

import java.util.Map;
import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.machine.GeoBlockItem;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockControllerBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockDefinition.Entry;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.content.multiblock.PartCut;
import dev.groundburst.content.multiblock.StructureSpawnerItem;
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
import net.minecraft.world.item.Rarity;
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
 * Missile defence (DESIGN.md §18.2, §18.4, §18.5; Phase 10): the Interceptor Battery, the Interceptor Complex and its
 * blocks, their interceptor missiles, and the interceptors in the air.
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

    // --- the Interceptor Complex (DESIGN.md §18.5) ------------------------------------------------------------------------
    /** Its vertical launch system's modules: four interceptor cells under armoured hatches. */
    public static final WithItem<MultiblockPartBlock> LAUNCH_CELL = part("launch_cell", "Launch Cell", MapColor.COLOR_GRAY);
    /** Its fire-control radar's face: a panel of radar elements. */
    public static final WithItem<MultiblockPartBlock> PHASED_ARRAY = part("phased_array_panel", "Phased Array Panel", MapColor.COLOR_LIGHT_GRAY);
    public static final WithItem<MultiblockControllerBlock> COMPLEX_CONTROLLER = GBBlocks.register("interceptor_complex", "Interceptor Complex",
            GBTab.MISSILES, p -> new MultiblockControllerBlock(p, GBDefence.COMPLEX_ENTITY),
            p -> p.mapColor(MapColor.COLOR_GREEN).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InterceptorComplexBlockEntity>> COMPLEX_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("interceptor_complex", () -> new BlockEntityType<>(InterceptorComplexBlockEntity::new,
                    COMPLEX_CONTROLLER.block().get()));

    private static WithItem<MultiblockPartBlock> part(String id, String english, MapColor color) {
        return GBBlocks.register(id, english, GBTab.MISSILES, MultiblockPartBlock::new,
                p -> p.mapColor(color).strength(4f, 8f).requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));
    }

    /**
     * The Interceptor Complex: a 5x5 deck three high. Its front row Launch Pad with the controller in the middle; behind
     * it the launcher, six Launch Cells three across and two deep, with a strip of Launch Pad down each side and the
     * space over the cells kept clear (their hatches swing open); at the back a deckhouse of Machine Casing two deep and
     * three high, the middle three of its front face, over the deck, six Phased Array Panels (its radar), the Energy
     * Port and the Item Port low in its back wall. Formed, the deck's Launch Pads are cut to the model's walkway (14 px)
     * and the controller to its console.
     */
    public static final MultiblockDefinition COMPLEX = new MultiblockDefinition(GroundBurst.id("interceptor_complex"),
            new String[][] {
                    {"PPXPP", "PCCCP", "PCCCP", "MMMMM", "MEMIM"},
                    {"     ", " ... ", " ... ", "MAAAM", "MMMMM"},
                    {"     ", "     ", "     ", "MAAAM", "MMMMM"},
            },
            Map.of('P', Entry.of(() -> GBLaunch.LAUNCH_PAD.block().get()), 'C', Entry.of(() -> LAUNCH_CELL.block().get()),
                    'M', Entry.of(GBMultiblocks.MACHINE_CASING.block()), 'A', Entry.of(() -> PHASED_ARRAY.block().get()),
                    'E', Entry.of(() -> GBMultiblocks.ENERGY_PORT.block().get()), 'I', Entry.of(GBMultiblocks.ITEM_PORT.block()),
                    MultiblockDefinition.CONTROLLER, Entry.of(() -> COMPLEX_CONTROLLER.block().get())),
            MultiblockDefinition.layerCuts(0, new String[] {"DDCDD", "D   D", "D   D"},
                    Map.of('D', PartCut.DECK, 'C', PartCut.CONSOLE_NORTH)));

    public static final DeferredItem<StructureSpawnerItem> COMPLEX_SPAWNER = GBItems.register("interceptor_complex_spawner",
            "Structure Spawner: Interceptor Complex", GBTab.MISSILES, p -> new StructureSpawnerItem(p, () -> COMPLEX),
            p -> p.stacksTo(1).rarity(Rarity.EPIC));

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
                    + "warhead never going off. Missiles flying out from near it, bound elsewhere, it lets go. An EMP knocks it "
                    + "out, and fries interceptors flying through one.");

    /** JEI's page for the Interceptor Complex and its blocks. */
    public static final String INFO_COMPLEX = GBLang.add(GroundBurst.MODID + ".jei.info.interceptor_complex",
            "The Interceptor Complex is the multiblock missile defence: two and a half times an Interceptor Battery's reach, "
                    + "surer interceptors and bigger volleys. Build a 5x5 deck three high: the controller in the middle of a front "
                    + "row of Launch Pad; behind it six Launch Cells, three across and two deep, a Launch Pad either side and the "
                    + "space over them clear; at the back a deckhouse of Machine Casing two deep and three high, six Phased Array "
                    + "Panels in the middle of its front over the deck, and the Energy and Item Ports low in its back wall (the "
                    + "Holo-Projector shows it all). Its 24 cells fill from its store, one a second. Powered, it engages missiles "
                    + "coming down within 640 blocks of it that a working Radar Array tracks, or 240 its own radar sees: two "
                    + "interceptors at each, four at class L and bigger, fired two at a time; its radar guides them all the way "
                    + "in, so each kills a small missile 95%% of the time (less against bigger ones, those coming down fast and a "
                    + "superweapon's), radar or not. If they all miss and there is time, it fires again. It shoots down any "
                    + "missile coming down within its reach, yours too: switch it off with redstone while you fire close by. An EMP "
                    + "knocks it out.");

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
