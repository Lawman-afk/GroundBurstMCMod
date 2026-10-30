package dev.groundburst.gametest;

import java.util.Comparator;
import java.util.List;

import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.GBAssembly;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileItem;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.LaunchComplexBlockEntity;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.radiation.RadiationSource;
import dev.groundburst.content.radiation.RadiationSources;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.content.ordnance.charge.Charges;
import dev.groundburst.content.strategic.GBStrategic;
import dev.groundburst.content.strategic.StrategicAssembly;
import dev.groundburst.content.strategic.StrategicAssemblyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Phase 8 slices 8c and 8d (DESIGN.md §15.2): the ICBM Assembly Hall and the whole weapons it builds. */
final class StrategicTests {
    private StrategicTests() {}

    static void register() {
        GBGameTests.test("strategic_hall_builds_a_thermonuclear_missile", GBGameTests.FLOOR_16, 40, StrategicTests::builds);
        GBGameTests.test("strategic_hall_builds_a_cobalt_missile_and_takes_it_apart", GBGameTests.FLOOR_16, 40, StrategicTests::cobalt);
        GBGameTests.test("strategic_hall_needs_its_structure_and_parts", GBGameTests.FLOOR_16, 20, StrategicTests::needsParts);
        GBGameTests.test("intercontinental_missile_needs_a_heavy_launcher", GBGameTests.FLOOR_16, 20, StrategicTests::complexOnly);
        GBGameTests.test("thermonuclear_warhead_goes_off_from_a_missile", GBGameTests.FLOOR_16, 200, StrategicTests::thermonuclear);
        GBGameTests.test("cobalt_warhead_starts_its_plume", GBGameTests.FLOOR_16, 40, StrategicTests::cobaltPlume);
        GBGameTests.test("strategic_hall_builds_a_tsar_and_takes_it_apart", GBGameTests.FLOOR_16, 40, StrategicTests::tsar);
        GBGameTests.test("tsar_goes_off_from_a_missile", GBGameTests.FLOOR_16, 200, StrategicTests::tsarBlast);
        GBGameTests.test("icbm_hall_builds_an_antimatter_icbm_and_takes_it_apart", GBGameTests.FLOOR_16, 40, StrategicTests::exotic);
        GBGameTests.test("icbm_hall_works_faster_with_a_speed_chip", GBGameTests.FLOOR_16, 80, StrategicTests::speedChip);
    }

    /**
     * A quantum speed chip in the hall (DESIGN.md §7.3): 40% more work a tick, each tick drawing 40% more power (350 FE
     * for its 250), so a job costs the same.
     */
    private static void speedChip(GameTestHelper helper) {
        // Its own floor, leaving Charges.bombScale alone: no blast here, and tests running beside it may have one.
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
        StrategicAssemblyBlockEntity hall = hall(helper, true);
        parts(hall.inventory(), false);
        hall.upgrades().setItem(0, new ItemStack(dev.groundburst.content.machine.GBUpgrades.chip(
                dev.groundburst.content.machine.UpgradeChipItem.Kind.SPEED, dev.groundburst.content.machine.UpgradeChipItem.Tier.QUANTUM).get()));
        hall.energy().generate(StrategicAssemblyBlockEntity.CAPACITY);
        int full = hall.energy().stored();
        helper.assertTrue(hall.startAssembly(), "it starts");
        helper.runAfterDelay(40, () -> {
            int used = full - hall.energy().stored();
            helper.assertTrue(used > 0 && used % 350 == 0, "350 FE a tick: " + used);
            int ticks = used / 350;
            helper.assertTrue(Math.abs(hall.progress() - 1.4 * ticks) <= 1, "1.4 ticks' work a tick: " + hall.progress() + " in " + ticks);
            helper.succeed();
        });
    }

    private static void floor(GameTestHelper helper) {
        Charges.bombScale = 0.3f;
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /** The hall built round a controller at (8, 1, 1) facing north: 7 wide (x 5-11), 13 deep (z 1-13), 6 high. */
    private static StrategicAssemblyBlockEntity hall(GameTestHelper helper, boolean build) {
        BlockPos rel = new BlockPos(8, 1, 1);
        helper.setBlock(rel, GBStrategic.HALL_CONTROLLER.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        if (build) {
            for (MultiblockDefinition.Cell cell : GBStrategic.HALL.cells(helper.absolutePos(rel), Direction.NORTH)) {
                if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                    helper.getLevel().setBlockAndUpdate(cell.pos(), GBStrategic.HALL.legend().get(cell.key()).preview().get());
                }
            }
        }
        var hall = (StrategicAssemblyBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(hall != null, "a hall controller");
        hall.revalidate();
        helper.assertTrue(hall.isFormed() == build,
                "formed: " + hall.isFormed() + " " + GBStrategic.HALL.check(helper.getLevel(), hall.getBlockPos(), Direction.NORTH));
        return hall;
    }

    private static void parts(Container inv, boolean jacket) {
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, new ItemStack(GBAssembly.BOOSTED_FISSION_CORE.get()));
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_SECONDARY, new ItemStack(GBStrategic.FUSION_SECONDARY.get()));
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_BOOSTER, new ItemStack(GBStrategic.INTERCONTINENTAL_BOOSTER.get()));
        if (jacket) {
            inv.setItem(StrategicAssemblyBlockEntity.SLOT_JACKET, new ItemStack(GBStrategic.COBALT_JACKET.get()));
        }
    }

    /**
     * Formed and fed a primary, a secondary, a booster, GPS and hardened electronics, the hall builds (a minute's work,
     * skipped here) a hardened, GPS-guided intercontinental missile with a thermonuclear warhead, 3000 blocks' range,
     * its own model; the parts are used up and the whole missile lies on the cradles.
     */
    private static void builds(GameTestHelper helper) {
        floor(helper);
        StrategicAssemblyBlockEntity hall = hall(helper, true);
        Container inv = hall.inventory();
        parts(inv, false);
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_GUIDANCE, new ItemStack(GBAssembly.GPS_GUIDANCE.get()));
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_MODULE, new ItemStack(GBAssembly.HARDENED_ELECTRONICS.get()));
        helper.assertTrue(hall.problem() == StrategicAssembly.Problem.NONE, "ready: " + hall.problem());
        hall.energy().generate(StrategicAssemblyBlockEntity.CAPACITY);
        helper.assertTrue(hall.startAssembly(), "it starts");
        helper.assertTrue(!hall.startAssembly(), "but not twice");
        hall.skipToEndForTest();
        helper.succeedWhen(() -> {
            ItemStack out = inv.getItem(StrategicAssemblyBlockEntity.SLOT_OUTPUT);
            MissileSpec spec = MissileItem.spec(out);
            helper.assertTrue(spec != null && spec.intercontinental() && spec.range() == 3000 && spec.needsComplex()
                    && spec.guidance() == MissileParts.Guidance.GPS && spec.hardened(), "an intercontinental missile: " + spec);
            helper.assertTrue(AssemblyStats.kindOf(spec.warhead()) == ChargeKind.THERMONUCLEAR_WARHEAD, "a thermonuclear warhead: " + spec.warhead());
            for (int slot = StrategicAssemblyBlockEntity.SLOT_PRIMARY; slot <= StrategicAssemblyBlockEntity.SLOT_MODULE; slot++) {
                helper.assertTrue(inv.getItem(slot).isEmpty(), "the parts used: slot " + slot);
            }
            var look = out.get(DataComponents.CUSTOM_MODEL_DATA);
            helper.assertTrue(look != null && look.strings().getFirst().equals("thermonuclear_warhead"), "its own model: " + look);
            helper.assertTrue(hall.laid() == StrategicAssemblyBlockEntity.SECTIONS, "the whole missile on the cradles: " + hall.laid());
        });
    }

    /** A Cobalt-59 Jacket makes it a cobalt warhead; TAKE APART gives every part back, the jacket with them. */
    private static void cobalt(GameTestHelper helper) {
        floor(helper);
        StrategicAssemblyBlockEntity hall = hall(helper, true);
        Container inv = hall.inventory();
        parts(inv, true);
        hall.energy().generate(StrategicAssemblyBlockEntity.CAPACITY);
        helper.assertTrue(hall.startAssembly(), "it starts");
        hall.skipToEndForTest();
        helper.succeedWhen(() -> {
            MissileSpec spec = MissileItem.spec(inv.getItem(StrategicAssemblyBlockEntity.SLOT_OUTPUT));
            helper.assertTrue(spec != null && AssemblyStats.kindOf(spec.warhead()) == ChargeKind.COBALT_WARHEAD, "a cobalt missile: " + spec);
            helper.assertTrue(spec.guidance() == MissileParts.Guidance.UNGUIDED && !spec.hardened(), "unguided, not hardened: " + spec);
            helper.assertTrue(hall.takeApart(), "taken apart");
            helper.assertTrue(inv.getItem(StrategicAssemblyBlockEntity.SLOT_OUTPUT).isEmpty(), "the missile gone");
            helper.assertTrue(inv.getItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY).is(GBAssembly.BOOSTED_FISSION_CORE.get())
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_SECONDARY).is(GBStrategic.FUSION_SECONDARY.get())
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_JACKET).is(GBStrategic.COBALT_JACKET.get())
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_BOOSTER).is(GBStrategic.INTERCONTINENTAL_BOOSTER.get())
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_GUIDANCE).isEmpty(), "every part back");
            helper.assertTrue(hall.laid() == 0, "the cradles empty");
        });
    }

    /**
     * A Tertiary Stage in the jacket slot makes the Tsar, with a Twin Boosted Core for its primary (a single Boosted
     * Fission Core will not do, nor the twin for anything else): its own model, a minute and a half's work, the Tsar on
     * the cradles; TAKE APART gives the Tertiary Stage and the twin core back with the rest.
     */
    private static void tsar(GameTestHelper helper) {
        floor(helper);
        StrategicAssemblyBlockEntity hall = hall(helper, true);
        Container inv = hall.inventory();
        parts(inv, false);
        ItemStack tertiary = new ItemStack(GBStrategic.TERTIARY_STAGE.get());
        helper.assertTrue(inv.canPlaceItem(StrategicAssemblyBlockEntity.SLOT_JACKET, tertiary), "the jacket slot takes a Tertiary Stage");
        helper.assertTrue(!inv.canPlaceItem(StrategicAssemblyBlockEntity.SLOT_SECONDARY, tertiary), "the secondary's slot does not");
        ItemStack twin = new ItemStack(GBStrategic.TWIN_BOOSTED_CORE.get());
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, twin.copy());
        helper.assertTrue(hall.problem() == StrategicAssembly.Problem.TWIN_FOR_TSAR, "the twin core is for a Tsar: " + hall.problem());
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, new ItemStack(GBAssembly.BOOSTED_FISSION_CORE.get()));
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_JACKET, tertiary);
        helper.assertTrue(hall.problem() == StrategicAssembly.Problem.NO_TWIN, "a Tsar wants the twin core: " + hall.problem());
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, twin);
        helper.assertTrue(hall.problem() == StrategicAssembly.Problem.NONE, "ready: " + hall.problem());
        helper.assertTrue(StrategicAssembly.ticks(tertiary) == StrategicAssembly.TSAR_TICKS, "a minute and a half");
        hall.energy().generate(StrategicAssemblyBlockEntity.CAPACITY);
        helper.assertTrue(hall.startAssembly(), "it starts");
        hall.skipToEndForTest();
        helper.succeedWhen(() -> {
            ItemStack out = inv.getItem(StrategicAssemblyBlockEntity.SLOT_OUTPUT);
            MissileSpec spec = MissileItem.spec(out);
            helper.assertTrue(spec != null && spec.intercontinental() && AssemblyStats.kindOf(spec.warhead()) == ChargeKind.TSAR_WARHEAD,
                    "a Tsar missile: " + spec);
            var look = out.get(DataComponents.CUSTOM_MODEL_DATA);
            helper.assertTrue(look != null && look.strings().getFirst().equals("tsar_warhead"), "its own model: " + look);
            var shown = hall.shownMissile();
            helper.assertTrue(hall.laid() == StrategicAssemblyBlockEntity.SECTIONS && shown != null
                    && shown.looks().size() == StrategicAssemblyBlockEntity.SECTIONS && shown.looks().getLast().equals("tsar_warhead_part4"),
                    "the Tsar on the cradles: " + shown);
            helper.assertTrue(hall.takeApart(), "taken apart");
            helper.assertTrue(inv.getItem(StrategicAssemblyBlockEntity.SLOT_JACKET).is(GBStrategic.TERTIARY_STAGE.get())
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY).is(GBStrategic.TWIN_BOOSTED_CORE.get())
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_BOOSTER).is(GBStrategic.INTERCONTINENTAL_BOOSTER.get()), "every part back");
        });
    }

    /**
     * An Antimatter Trap in the primary slot and a booster make an Antimatter ICBM (a secondary with it makes the hall
     * refuse): a minute and a half's work, its own model on the cradles; TAKE APART gives the trap and the booster back.
     */
    private static void exotic(GameTestHelper helper) {
        floor(helper);
        StrategicAssemblyBlockEntity hall = hall(helper, true);
        Container inv = hall.inventory();
        ItemStack trap = new ItemStack(GBAssembly.ANTIMATTER_TRAP.get());
        helper.assertTrue(inv.canPlaceItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, trap), "the primary slot takes an Antimatter Trap");
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, trap);
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_SECONDARY, new ItemStack(GBStrategic.FUSION_SECONDARY.get()));
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_BOOSTER, new ItemStack(GBStrategic.INTERCONTINENTAL_BOOSTER.get()));
        helper.assertTrue(hall.problem() == StrategicAssembly.Problem.EXOTIC_ALONE, "no secondary with it: " + hall.problem());
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_SECONDARY, ItemStack.EMPTY);
        helper.assertTrue(hall.problem() == StrategicAssembly.Problem.NONE, "ready: " + hall.problem());
        helper.assertTrue(StrategicAssembly.ticks(trap, ItemStack.EMPTY) == StrategicAssembly.EXOTIC_TICKS, "a minute and a half");
        hall.energy().generate(StrategicAssemblyBlockEntity.CAPACITY);
        helper.assertTrue(hall.startAssembly(), "it starts");
        hall.skipToEndForTest();
        helper.succeedWhen(() -> {
            ItemStack out = inv.getItem(StrategicAssemblyBlockEntity.SLOT_OUTPUT);
            MissileSpec spec = MissileItem.spec(out);
            helper.assertTrue(spec != null && spec.intercontinental() && spec.exotic() && spec.needsComplex()
                    && AssemblyStats.kindOf(spec.warhead()) == ChargeKind.ANTIMATTER_WARHEAD, "an Antimatter ICBM: " + spec);
            var shown = hall.shownMissile();
            helper.assertTrue(hall.laid() == StrategicAssemblyBlockEntity.SECTIONS && shown != null
                    && shown.looks().getLast().equals("antimatter_warhead_part4"), "on the cradles: " + shown);
            helper.assertTrue(hall.takeApart(), "taken apart");
            helper.assertTrue(inv.getItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY).is(GBAssembly.ANTIMATTER_TRAP.get())
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_SECONDARY).isEmpty()
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_BOOSTER).is(GBStrategic.INTERCONTINENTAL_BOOSTER.get()), "the trap and the booster back");
        });
    }

    /** Unbuilt, the hall will not work; built, it says what is missing, and its slots take only their own parts. */
    private static void needsParts(GameTestHelper helper) {
        floor(helper);
        StrategicAssemblyBlockEntity bare = hall(helper, false);
        helper.assertTrue(bare.problem() == StrategicAssembly.Problem.NOT_FORMED, "not built: " + bare.problem());
        StrategicAssemblyBlockEntity hall = hall(helper, true);
        Container inv = hall.inventory();
        helper.assertTrue(hall.problem() == StrategicAssembly.Problem.NO_PRIMARY, "no primary: " + hall.problem());
        helper.assertTrue(!inv.canPlaceItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, new ItemStack(GBAssembly.FISSION_CORE.get())),
                "a plain Fission Core is no primary");
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, new ItemStack(GBAssembly.BOOSTED_FISSION_CORE.get()));
        helper.assertTrue(hall.problem() == StrategicAssembly.Problem.NO_SECONDARY, "no secondary: " + hall.problem());
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_SECONDARY, new ItemStack(GBStrategic.FUSION_SECONDARY.get()));
        helper.assertTrue(hall.problem() == StrategicAssembly.Problem.NO_BOOSTER, "no booster: " + hall.problem());
        helper.assertTrue(!hall.startAssembly(), "nothing to start");
        helper.assertTrue(!inv.canPlaceItem(StrategicAssemblyBlockEntity.SLOT_OUTPUT, LaunchTests.missile()), "only its own missiles in its output");
        helper.succeed();
    }

    /**
     * A whole weapon fires only from a heavy launcher (the basic Launch Silo refuses it; the Launch Complex takes it, as the
     * Missile Silo does: SiloTests), and the bench neither builds its warhead into
     * a missile of its own nor takes it apart.
     */
    private static void complexOnly(GameTestHelper helper) {
        ItemStack icbm = StrategicAssembly.assemble(ItemStack.EMPTY, new ItemStack(GBAssembly.GPS_GUIDANCE.get()), ItemStack.EMPTY);
        LaunchSiloBlockEntity silo = LaunchTests.formedSilo(helper);
        helper.assertTrue(!silo.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, icbm), "a silo refuses it");
        LaunchComplexBlockEntity complex = NuclearTests.complex(helper, new BlockPos(9, 1, 9));
        helper.assertTrue(complex.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, icbm), "a complex takes it");
        ItemStack warhead = new ItemStack(GBOrdnance.THERMONUCLEAR_WARHEAD.item().get());
        helper.assertTrue(MissileAssembly.fit(warhead, SizeClass.XL) == MissileAssembly.Problem.WHOLE_WEAPON, "the bench will not fit its warhead");
        helper.assertTrue(MissileAssembly.takeApart(icbm).isEmpty(), "nor take it apart");
        helper.succeed();
    }

    /** Delivered (at a fiftieth of its size), the thermonuclear warhead leaves a crater and fallout 360 blocks round for three days. */
    private static void thermonuclear(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 1, 8);
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(at));
        float scale = Charges.bombScale;
        Charges.bombScale = 0.02f;
        Charges.impact(helper.getLevel(), ChargeKind.THERMONUCLEAR_WARHEAD, helper.absolutePos(at), null);
        Charges.bombScale = scale;
        helper.succeedWhen(() -> {
            helper.assertTrue(dug(helper), "a crater where it hit");
            RadiationSource zone = NuclearTests.sourceAt(helper, centre);
            helper.assertTrue(Math.abs(zone.radius() - 360 * 0.02f) < 0.01f && NuclearTests.lasts(zone, 72_000), "360 blocks for three days: " + zone);
            RadiationSources.of(helper.getLevel()).removeNear(centre, 2);
        });
    }

    /**
     * Whether the floor round the burst point (8, 1, 8) is dug: at a test's tiny scale the straight-down ray has only
     * just enough for one block of stone, so any of the nine under and beside the burst will do.
     */
    private static boolean dug(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(7, 0, 7, 9, 0, 9)) {
            if (helper.getLevel().getBlockState(helper.absolutePos(p)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Delivered (at a fiftieth of its size), the Tsar leaves a crater and fallout 600 blocks round for three days; and
     * the server's radius limit lets it reach all the way at full size (700 blocks: the limit is 720 by default).
     */
    private static void tsarBlast(GameTestHelper helper) {
        floor(helper);
        helper.assertTrue(dev.groundburst.config.GBServerConfig.MAX_RADIUS.getDefault() >= 700, "the default limit reaches the Tsar's 700 blocks");
        BlockPos at = new BlockPos(8, 1, 8);
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(at));
        float scale = Charges.bombScale;
        Charges.bombScale = 0.02f;
        Charges.impact(helper.getLevel(), ChargeKind.TSAR_WARHEAD, helper.absolutePos(at), null);
        Charges.bombScale = scale;
        helper.succeedWhen(() -> {
            helper.assertTrue(dug(helper), "a crater where it hit");
            RadiationSource zone = NuclearTests.sourceAt(helper, centre);
            helper.assertTrue(Math.abs(zone.radius() - 600 * 0.02f) < 0.01f && NuclearTests.lasts(zone, 72_000), "600 blocks for three days: " + zone);
            RadiationSources.of(helper.getLevel()).removeNear(centre, 2);
        });
    }

    /**
     * Delivered (at a two-hundredth of its size), the cobalt warhead leaves its month-long zone and starts its plume at
     * the burst (slice 12b: the debris cloud carried downwind): the cobalt reach, the burst's own half-life.
     */
    private static void cobaltPlume(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        BlockPos at = new BlockPos(8, 1, 8);
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(at));
        long now = level.getGameTime();
        float scale = Charges.bombScale;
        Charges.bombScale = 0.005f;
        Charges.impact(level, ChargeKind.COBALT_WARHEAD, helper.absolutePos(at), null);
        Charges.bombScale = scale;
        RadiationSource zone = RadiationSources.of(level).sources().stream()
                .filter(z -> z.start() == now && z.center().distanceTo(centre) < 1).findFirst().orElse(null);
        var plume = dev.groundburst.content.fallout.Plumes.of(level).plumes().stream()
                .filter(p -> Math.hypot(p.x() - centre.x, p.z() - centre.z) < 1).findFirst().orElse(null);
        RadiationSources.of(level).removeNear(centre, 1);                // gone again before it can sicken anything
        dev.groundburst.content.fallout.Plumes.of(level).removeNear(centre, 1);
        helper.assertTrue(zone != null && NuclearTests.lasts(zone, 720_000), "its zone lasts a month: " + zone);
        helper.assertTrue(plume != null, "a plume at the burst");
        helper.assertTrue(Math.abs(plume.reach() - Charges.COBALT_REACH * 0.005f) < 0.01, "the cobalt reach: " + plume);
        helper.assertTrue(plume.halfLife() == zone.halfLife(), "the burst's own half-life: " + plume + " " + zone);
        helper.succeed();
    }
}
