package dev.groundzero.gametest;

import java.util.Comparator;
import java.util.List;

import dev.groundzero.content.assembly.AssemblyStats;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.assembly.MissileAssembly;
import dev.groundzero.content.assembly.MissileItem;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.content.launch.LaunchComplexBlockEntity;
import dev.groundzero.content.launch.LaunchSiloBlockEntity;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.nuclear.Contamination;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.charge.ChargeKind;
import dev.groundzero.content.ordnance.charge.Charges;
import dev.groundzero.content.strategic.GZStrategic;
import dev.groundzero.content.strategic.StrategicAssembly;
import dev.groundzero.content.strategic.StrategicAssemblyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Phase 8 slice 8c (DESIGN.md §15.2): the Strategic Assembly Hall and the whole weapons it builds. */
final class StrategicTests {
    private StrategicTests() {}

    static void register() {
        GZGameTests.test("strategic_hall_builds_a_thermonuclear_missile", GZGameTests.FLOOR_16, 40, StrategicTests::builds);
        GZGameTests.test("strategic_hall_builds_a_cobalt_missile_and_takes_it_apart", GZGameTests.FLOOR_16, 40, StrategicTests::cobalt);
        GZGameTests.test("strategic_hall_needs_its_structure_and_parts", GZGameTests.FLOOR_16, 20, StrategicTests::needsParts);
        GZGameTests.test("intercontinental_missile_fires_only_from_the_complex", GZGameTests.FLOOR_16, 20, StrategicTests::complexOnly);
        GZGameTests.test("thermonuclear_warhead_goes_off_from_a_missile", GZGameTests.FLOOR_16, 200, StrategicTests::thermonuclear);
        GZGameTests.test("cobalt_warhead_leaves_a_plume_downwind", GZGameTests.FLOOR_16, 40, StrategicTests::cobaltPlume);
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
        helper.setBlock(rel, GZStrategic.HALL_CONTROLLER.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        if (build) {
            for (MultiblockDefinition.Cell cell : GZStrategic.HALL.cells(helper.absolutePos(rel), Direction.NORTH)) {
                if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                    helper.getLevel().setBlockAndUpdate(cell.pos(), GZStrategic.HALL.legend().get(cell.key()).preview().get());
                }
            }
        }
        var hall = (StrategicAssemblyBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(hall != null, "a hall controller");
        hall.revalidate();
        helper.assertTrue(hall.isFormed() == build,
                "formed: " + hall.isFormed() + " " + GZStrategic.HALL.check(helper.getLevel(), hall.getBlockPos(), Direction.NORTH));
        return hall;
    }

    private static void parts(Container inv, boolean jacket) {
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, new ItemStack(GZAssembly.BOOSTED_FISSION_CORE.get()));
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_SECONDARY, new ItemStack(GZStrategic.FUSION_SECONDARY.get()));
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_BOOSTER, new ItemStack(GZStrategic.INTERCONTINENTAL_BOOSTER.get()));
        if (jacket) {
            inv.setItem(StrategicAssemblyBlockEntity.SLOT_JACKET, new ItemStack(GZStrategic.COBALT_JACKET.get()));
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
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_GUIDANCE, new ItemStack(GZAssembly.GPS_GUIDANCE.get()));
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_MODULE, new ItemStack(GZAssembly.HARDENED_ELECTRONICS.get()));
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
            helper.assertTrue(look != null && look.strings().getFirst().equals("icbm_thermonuclear_warhead"), "its own model: " + look);
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
            helper.assertTrue(inv.getItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY).is(GZAssembly.BOOSTED_FISSION_CORE.get())
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_SECONDARY).is(GZStrategic.FUSION_SECONDARY.get())
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_JACKET).is(GZStrategic.COBALT_JACKET.get())
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_BOOSTER).is(GZStrategic.INTERCONTINENTAL_BOOSTER.get())
                    && inv.getItem(StrategicAssemblyBlockEntity.SLOT_GUIDANCE).isEmpty(), "every part back");
            helper.assertTrue(hall.laid() == 0, "the cradles empty");
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
        helper.assertTrue(!inv.canPlaceItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, new ItemStack(GZAssembly.FISSION_CORE.get())),
                "a plain Fission Core is no primary");
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, new ItemStack(GZAssembly.BOOSTED_FISSION_CORE.get()));
        helper.assertTrue(hall.problem() == StrategicAssembly.Problem.NO_SECONDARY, "no secondary: " + hall.problem());
        inv.setItem(StrategicAssemblyBlockEntity.SLOT_SECONDARY, new ItemStack(GZStrategic.FUSION_SECONDARY.get()));
        helper.assertTrue(hall.problem() == StrategicAssembly.Problem.NO_BOOSTER, "no booster: " + hall.problem());
        helper.assertTrue(!hall.startAssembly(), "nothing to start");
        helper.assertTrue(!inv.canPlaceItem(StrategicAssemblyBlockEntity.SLOT_OUTPUT, LaunchTests.missile()), "only its own missiles in its output");
        helper.succeed();
    }

    /**
     * A whole weapon fires only from a Launch Complex (a silo refuses it), and the bench neither builds its warhead into
     * a missile of its own nor takes it apart.
     */
    private static void complexOnly(GameTestHelper helper) {
        ItemStack icbm = StrategicAssembly.assemble(ItemStack.EMPTY, new ItemStack(GZAssembly.GPS_GUIDANCE.get()), ItemStack.EMPTY);
        LaunchSiloBlockEntity silo = LaunchTests.formedSilo(helper);
        helper.assertTrue(!silo.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, icbm), "a silo refuses it");
        LaunchComplexBlockEntity complex = NuclearTests.complex(helper, new BlockPos(9, 1, 9));
        helper.assertTrue(complex.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, icbm), "a complex takes it");
        ItemStack warhead = new ItemStack(GZOrdnance.THERMONUCLEAR_WARHEAD.item().get());
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
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(8, 0, 8))).isAir(), "a crater where it hit");
            var zone = Contamination.of(helper.getLevel()).zones().stream().filter(z -> z.center().distanceTo(centre) < 1).findFirst();
            helper.assertTrue(zone.isPresent() && Math.abs(zone.get().radius() - 360 * 0.02f) < 0.01f && zone.get().duration() == 72_000,
                    "360 blocks for three days: " + zone);
            Contamination.of(helper.getLevel()).removeNear(centre, 2);
        });
    }

    /**
     * Delivered (at a two-hundredth of its size, so its plume stays in this test's plot), the cobalt warhead leaves its
     * zone and four more strung out downwind, evenly spaced, each weaker than the last, all lasting a month.
     */
    private static void cobaltPlume(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 1, 8);
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(at));
        long now = helper.getLevel().getGameTime();
        float scale = Charges.bombScale;
        Charges.bombScale = 0.005f;
        Charges.impact(helper.getLevel(), ChargeKind.COBALT_WARHEAD, helper.absolutePos(at), null);
        Charges.bombScale = scale;
        List<Contamination.Zone> plume = Contamination.of(helper.getLevel()).zones().stream()
                .filter(z -> z.start() == now && z.center().distanceTo(centre) < 6)
                .sorted(Comparator.comparingDouble(z -> z.center().distanceTo(centre))).toList();
        for (Contamination.Zone z : plume) {
            Contamination.of(helper.getLevel()).removeNear(z.center(), 0.01);       // gone again before it can sicken anything
        }
        helper.assertTrue(plume.size() == 1 + Charges.COBALT_PLUME, "five zones: " + plume);
        for (int i = 0; i < plume.size(); i++) {
            Contamination.Zone z = plume.get(i);
            helper.assertTrue(Math.abs(z.center().distanceTo(centre) - Charges.COBALT_STEP * 0.005 * i) < 0.01, "evenly downwind: " + z);
            helper.assertTrue(z.duration() == 720_000, "a month: " + z);
            helper.assertTrue(i == 0 || z.strength() < plume.get(i - 1).strength(), "weaker downwind: " + plume);
        }
        helper.succeed();
    }
}
