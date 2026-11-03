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
import dev.groundburst.registry.GBSounds;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.IEventBus;
import dev.groundburst.port.transfer.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Artillery (DESIGN.md §20.1): crewed guns aimed from a strategic view, World of Tanks' way: the 81 mm Mortar, the
 * 155 mm Howitzer and the Atomic Cannon, their shells, the shells in flight and the crewman's place.
 */
public final class GBArtillery {
    // --- the guns' figures (GunSpec has what each means) ---------------------------------------------------------------
    /**
     * The 81 mm Mortar: four charges (50, 100, 150 and 200 blocks at 45 degrees), the high arc only (45 to 85 degrees),
     * 16 to 200 blocks all round.
     */
    public static final GunSpec MORTAR_SPEC = new GunSpec("mortar", Calibre.MORTAR,
            List.of(ShellType.HE, ShellType.SMOKE, ShellType.INCENDIARY, ShellType.CHEMICAL, ShellType.CRYO, ShellType.EMP), 16,
            MortarBlockEntity.CHARGES, MortarBlockEntity.MIN_ELEVATION, MortarBlockEntity.MAX_ELEVATION, MortarBlockEntity.REST_ELEVATION,
            MortarBlockEntity.MIN_RANGE, MortarBlockEntity.MAX_RANGE, 180, 2f, 2.0, 1f, MortarBlockEntity.RELOAD, 1.6, 40,
            MortarBlockEntity.BLOOM, MortarBlockEntity.MAX_BLOOM, 0.06,
            MortarBlockEntity.SOCKET_Y, MortarBlockEntity.SOCKET_BACK, MortarBlockEntity.TUBE, 0.75, 0.45, GBSounds.MORTAR_FIRE::get, 6f, 1f);
    /**
     * The 155 mm Howitzer (after the M777): seven charges, each reaching two thirds further than the one before (79 to 800
     * blocks at 45 degrees), the high arc (45 to 70 degrees), 64 to 800 blocks, 30 degrees either side of its front.
     */
    public static final GunSpec HOWITZER_SPEC = new GunSpec("howitzer", Calibre.HOWITZER, List.of(ShellType.HE, ShellType.INCENDIARY), 8,
            new double[] {1.987, 2.408, 2.924, 3.550, 4.301, 5.215, 6.325}, 45, 70, 10, 64, 800, 30, 1f, 4.0, 1f, 100, 1.0, 60, 4, 6, 0.08,
            21.0 / 16, 0, 72.0 / 16, 1.2, 1.5, GBSounds.HOWITZER_FIRE::get, 12f, 2.5f);
    /**
     * The Atomic Cannon (after the M65 "Atomic Annie"): three charges (375, 750 and 1,500 blocks at 45 degrees), laid 15
     * to 55 degrees (the high arc where it can, else the low), 300 to 1,500 blocks, 20 degrees either side.
     */
    public static final GunSpec ATOMIC_SPEC = new GunSpec("atomic_cannon", Calibre.ATOMIC,
            List.of(ShellType.HE, ShellType.INCENDIARY, ShellType.NUCLEAR), 4, new double[] {4.330, 6.124, 8.660}, 15, 55, 5, 300, 1500, 20,
            0.5f, 8.0, 0.6f, 300, 0.8, 100, 3, 5, 0.1, 30.0 / 16, 0, 120.0 / 16, 2.6, 2.2, GBSounds.ATOMIC_FIRE::get, 16f, 4f);

    // --- the guns -------------------------------------------------------------------------------------------------------
    public static final WithItem<GunBlock> MORTAR = gun("mortar", "81 mm Mortar", Shapes.or(Block.box(2, 0, 2, 14, 3, 14), Block.box(6, 3, 5, 10, 12, 11)),
            () -> GBArtillery.MORTAR_ENTITY.get(), MortarBlockEntity::new, MortarBlockEntity.restPose(), 3f);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MortarBlockEntity>> MORTAR_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("mortar", () -> new BlockEntityType<>(MortarBlockEntity::new, MORTAR.block().get()));
    public static final WithItem<GunBlock> HOWITZER = gun("howitzer", "155 mm Howitzer", Shapes.or(Block.box(0, 0, 0, 16, 6, 16), Block.box(3, 6, 3, 13, 16, 13)),
            () -> GBArtillery.HOWITZER_ENTITY.get(), BarrelGunBlockEntity::howitzer, BarrelGunBlockEntity.restPose(HOWITZER_SPEC), 6f);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BarrelGunBlockEntity>> HOWITZER_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("howitzer", () -> new BlockEntityType<>(BarrelGunBlockEntity::howitzer, HOWITZER.block().get()));
    public static final WithItem<GunBlock> ATOMIC_CANNON = gun("atomic_cannon", "Atomic Cannon", Block.box(0, 0, 0, 16, 16, 16),
            () -> GBArtillery.ATOMIC_ENTITY.get(), BarrelGunBlockEntity::atomicCannon, BarrelGunBlockEntity.restPose(ATOMIC_SPEC), 10f);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BarrelGunBlockEntity>> ATOMIC_ENTITY =
            GBBlockEntities.BLOCK_ENTITIES.register("atomic_cannon", () -> new BlockEntityType<>(BarrelGunBlockEntity::atomicCannon, ATOMIC_CANNON.block().get()));

    /** Every gun's block, for the lists that name them all. */
    public static List<WithItem<GunBlock>> guns() {
        return List.of(MORTAR, HOWITZER, ATOMIC_CANNON);
    }

    // --- the shells -------------------------------------------------------------------------------------------------------
    /** Each calibre's shells, of each kind it fires. */
    public static final Map<Calibre, Map<ShellType, DeferredItem<ShellItem>>> SHELLS = shells();
    /** The mortar's shells (its first set: the names its recipes and tests use). */
    public static final Map<ShellType, DeferredItem<ShellItem>> MORTAR_SHELLS = SHELLS.get(Calibre.MORTAR);

    /** The item of {@code calibre}'s shell of {@code type} (null: there is none). */
    public static DeferredItem<ShellItem> shell(Calibre calibre, ShellType type) {
        return SHELLS.get(calibre).get(type);
    }

    // --- in flight, and the crewman's place ------------------------------------------------------------------------------
    /** A shell in the air. */
    public static final DeferredHolder<EntityType<?>, EntityType<ArtilleryShell>> SHELL = GBOrdnance.ENTITIES.registerEntityType("artillery_shell",
            ArtilleryShell::new, MobCategory.MISC, b -> b.sized(0.3f, 0.3f).clientTrackingRange(16).updateInterval(1).noSave());
    /** A gun's crewman's place (saved: the game lets nobody ride what is not, and a crewman saved there comes back so). */
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
        GBLang.add("entity." + GroundBurst.MODID + ".gun_seat", "Gun Crew Place");
    }

    /** JEI's pages for the guns and their shells. */
    public static final String INFO = GBLang.add(GroundBurst.MODID + ".jei.info.mortar",
            "The 81 mm Mortar lobs shells 16 to 200 blocks, high over anything in between. Right-click it with shells to load its "
                    + "rack (16 of each kind; hoppers and pipes load it too), or with anything else to man it (Shift steps away). Manned, "
                    + "it follows where you look; scroll out, or press V, for the strategic view: a camera high over the ground, the "
                    + "mouse moving the aim over it, the wheel its height. 1 to 6 pick the shell (HE, smoke, incendiary, chemical, "
                    + "cryo, EMP), left click fires. It fires from its rack, then from your inventory. It is laid on your aim at its "
                    + "own pace, and a shell falls where it is laid, somewhere in the aiming circle drawn on the ground: the circle "
                    + "grows with range, blooms as it fires and while it is laid round, and shrinks back over two seconds, so a "
                    + "steady crew shoots closest.");
    public static final String INFO_HOWITZER = GBLang.add(GroundBurst.MODID + ".jei.info.howitzer",
            "The 155 mm Howitzer reaches 64 to 800 blocks, 30 degrees either side of where it is placed facing. It is crewed and "
                    + "aimed as the mortar is (scroll out or V for the strategic view; far ground the game has not loaded is shown "
                    + "as the gun's map of it), fires HE and incendiary shells (8 of each in its rack) every five seconds, and is "
                    + "steadier than the mortar: a narrower circle that takes three seconds to settle.");
    public static final String INFO_ATOMIC = GBLang.add(GroundBurst.MODID + ".jei.info.atomic_cannon",
            "The Atomic Cannon throws 280 mm shells 300 to 1,500 blocks, 20 degrees either side of where it is placed facing: HE, "
                    + "incendiary, and the nuclear shell, a tactical nuclear burst wherever it lands. Crewed and aimed as the other "
                    + "guns are; it takes fifteen seconds to load and five to settle, and its rack holds 4 of each.");

    private GBArtillery() {}

    public static void init() {}

    private static WithItem<GunBlock> gun(String id, String english, net.minecraft.world.phys.shapes.VoxelShape shape,
            java.util.function.Supplier<BlockEntityType<? extends GunBlockEntity>> type,
            java.util.function.BiFunction<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, ? extends GunBlockEntity> factory,
            Map<String, float[]> pose, float hardness) {
        return GBBlocks.registerWithItem(id, english, GBTab.ORDNANCE, p -> new GunBlock(p, shape, type, factory),
                p -> p.mapColor(MapColor.COLOR_GREEN).strength(hardness, hardness * 2).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                        .pushReaction(PushReaction.BLOCK),
                (block, props) -> new GeoBlockItem(block, props, GroundBurst.id(id)).posed(pose));
    }

    private static Map<Calibre, Map<ShellType, DeferredItem<ShellItem>>> shells() {
        Map<Calibre, Map<ShellType, DeferredItem<ShellItem>>> out = new EnumMap<>(Calibre.class);
        for (GunSpec g : List.of(MORTAR_SPEC, HOWITZER_SPEC, ATOMIC_SPEC)) {
            Map<ShellType, DeferredItem<ShellItem>> kinds = new EnumMap<>(ShellType.class);
            for (ShellType t : g.shells()) {
                Calibre c = g.calibre();
                kinds.put(t, GBItems.register(c.item(t), c.english + " " + t.english + " Shell", GBTab.ORDNANCE, p -> new ShellItem(p, c, t),
                        p -> t == ShellType.NUCLEAR ? p.stacksTo(4).rarity(Rarity.RARE) : p.stacksTo(c == Calibre.MORTAR ? 16 : c == Calibre.HOWITZER ? 8 : 4)));
            }
            out.put(g.calibre(), kinds);
        }
        return out;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(Capabilities.Item.BLOCK, MORTAR_ENTITY.get(), (be, side) -> be.rack());
            event.registerBlockEntity(Capabilities.Item.BLOCK, HOWITZER_ENTITY.get(), (be, side) -> be.rack());
            event.registerBlockEntity(Capabilities.Item.BLOCK, ATOMIC_ENTITY.get(), (be, side) -> be.rack());
        });
        modBus.addListener((RegisterTicketControllersEvent event) -> event.register(TICKETS));
    }
}
