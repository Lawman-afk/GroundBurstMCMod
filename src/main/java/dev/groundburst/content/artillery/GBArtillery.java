package dev.groundburst.content.artillery;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.GeoBlockItem;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Artillery (DESIGN.md §20.1): crewed guns aimed from a strategic view, World of Tanks' way. So far the 81 mm Mortar,
 * its six shells, the shells in flight and the crewman's seat.
 */
public final class GBArtillery {
    public static final WithItem<MortarBlock> MORTAR = GBBlocks.registerWithItem("mortar", "81 mm Mortar", GBTab.ORDNANCE, MortarBlock::new,
            p -> p.mapColor(MapColor.COLOR_GREEN).strength(3f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .pushReaction(PushReaction.IMMOVEABLE),
            (block, props) -> new GeoBlockItem(block, props, GroundBurst.id("mortar")).posed(MortarBlockEntity.restPose()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MortarBlockEntity>> MORTAR_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("mortar", () -> new BlockEntityType<>(MortarBlockEntity::new, MORTAR.block().get()));

    /** The mortar's shells, one of each kind. */
    public static final Map<ShellType, DeferredItem<ShellItem>> MORTAR_SHELLS = shells();

    /** A shell in the air. */
    public static final DeferredHolder<EntityType<?>, EntityType<ArtilleryShell>> SHELL = GBOrdnance.ENTITIES.registerEntityType("artillery_shell",
            ArtilleryShell::new, MobCategory.MISC, b -> b.sized(0.3f, 0.3f).clientTrackingRange(16).updateInterval(1).noSave());
    /** A gun's crewman's seat (saved: the game lets nobody ride what is not, and a crewman saved seated comes back so). */
    public static final DeferredHolder<EntityType<?>, EntityType<GunSeat>> SEAT = GBOrdnance.ENTITIES.registerEntityType("gun_seat",
            GunSeat::new, MobCategory.MISC, b -> b.sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(10));

    /** Shells keep the chunks they fly through ticking (owned by each shell; none are kept over a restart). */
    public static final TicketController TICKETS = new TicketController(GroundBurst.id("artillery"), (level, tickets) -> {
        for (var owner : List.copyOf(tickets.getEntityTickets().keySet())) {
            tickets.removeAllTickets(owner);
        }
    });

    static {
        GBLang.add("entity." + GroundBurst.MODID + ".artillery_shell", "Artillery Shell");
        GBLang.add("entity." + GroundBurst.MODID + ".gun_seat", "Gun Seat");
    }

    /** JEI's page for the mortar and its shells. */
    public static final String INFO = GBLang.add(GroundBurst.MODID + ".jei.info.mortar",
            "The 81 mm Mortar lobs shells 16 to 200 blocks, high over anything in between. Right-click it with shells to load its "
                    + "rack (16 of each kind; hoppers and pipes load it too), or with anything else to man it (Shift gets up). Manned, "
                    + "it follows where you look; scroll out, or press V, for the strategic view: a camera high over the ground, the "
                    + "mouse moving the aim over it, the wheel its height. 1 to 6 pick the shell (HE, smoke, incendiary, chemical, "
                    + "cryo, EMP), left click fires. It fires from its rack, then from your inventory. It is laid on your aim at its "
                    + "own pace, and a shell falls where the tube points, somewhere in the aiming circle drawn on the ground: the "
                    + "circle grows with range, blooms as it fires and while it turns, and shrinks back over two seconds, so a "
                    + "steady crew shoots closest.");

    private GBArtillery() {}

    public static void init() {}

    private static Map<ShellType, DeferredItem<ShellItem>> shells() {
        Map<ShellType, DeferredItem<ShellItem>> out = new EnumMap<>(ShellType.class);
        for (ShellType t : ShellType.values()) {
            out.put(t, GBItems.register("mortar_shell_" + t.key, "81 mm " + t.english + " Shell", GBTab.ORDNANCE, p -> new ShellItem(p, t),
                    p -> p.stacksTo(16)));
        }
        return out;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(Capabilities.Item.BLOCK, MORTAR_ENTITY.get(), (be, side) -> be.rack()));
        modBus.addListener((RegisterTicketControllersEvent event) -> event.register(TICKETS));
    }
}
