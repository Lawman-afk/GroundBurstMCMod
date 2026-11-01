package dev.groundburst.gametest;

import dev.groundburst.content.assembler.AssemblerClass;
import dev.groundburst.content.assembler.GBAssemblers;
import dev.groundburst.content.assembler.MissileAssemblerBlockEntity;
import dev.groundburst.content.assembly.AssemblyBenchBlockEntity;
import dev.groundburst.content.assembly.Fuze;
import dev.groundburst.content.assembly.FuzeSettings;
import dev.groundburst.content.assembly.GBAssembly;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileItem;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.WideMachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.registry.GBDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The missile assemblers (DESIGN.md §10.4): each forms on its own structure and builds the missiles of its own class
 * from a warhead, its class's body and a motor that fits, laying them out section by section; and the Ordnance Assembly
 * Bench, its missile page gone, drops what an older world left in that page's slots.
 */
final class AssemblerTests {
    private AssemblerTests() {}

    static void register() {
        GBGameTests.test("light_missile_assembler_builds_its_missile", GBGameTests.FLOOR_16, 40, h -> builds(h, AssemblerClass.LIGHT));
        GBGameTests.test("medium_missile_assembler_builds_its_missile", GBGameTests.FLOOR_16, 40, h -> builds(h, AssemblerClass.MEDIUM));
        GBGameTests.test("heavy_missile_assembler_builds_its_missile", GBGameTests.FLOOR_16, 40, h -> builds(h, AssemblerClass.HEAVY));
        GBGameTests.test("strategic_missile_assembler_builds_its_missile", GBGameTests.FLOOR_16, 40, h -> builds(h, AssemblerClass.STRATEGIC));
        GBGameTests.test("missile_assembler_lays_its_sections_as_it_works", GBGameTests.FLOOR_16, 400, AssemblerTests::sections);
        GBGameTests.test("missile_assembler_needs_its_structure", GBGameTests.FLOOR_16, 20, AssemblerTests::needsStructure);
        GBGameTests.test("missile_assembler_takes_only_its_class", GBGameTests.FLOOR_16, 20, AssemblerTests::ownClass);
        GBGameTests.test("missile_assembler_motor_must_fit", GBGameTests.FLOOR_16, 20, AssemblerTests::motorFit);
        GBGameTests.test("missile_assembler_takes_a_missile_apart", GBGameTests.FLOOR_16, 20, AssemblerTests::takesApart);
        GBGameTests.test("missile_assembler_auto_assembles_the_next", GBGameTests.FLOOR_16, 40, AssemblerTests::auto);
        GBGameTests.test("bench_drops_its_old_missile_page", GBGameTests.FLOOR_16, 20, AssemblerTests::benchDrops);
        GBGameTests.test("launcher_arms_stay_set_for_their_missile", GBGameTests.FLOOR_16, 20, AssemblerTests::armsHold);
    }

    /**
     * A launcher's arms are set for the missile loaded (its own hold and girth), and stay set for it once it has gone
     * (they swing clear where they held it as it lifts off), until another is loaded.
     */
    private static void armsHold(GameTestHelper helper) {
        var silo = LaunchTests.formedSilo(helper);
        helper.assertTrue(silo.gripsFor() == null && !silo.grips().isEmpty(), "an empty silo's arm rests as modelled");
        silo.inventory().setItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE, MissileAssembly.missile(new MissileSpec(
                warheadOf(AssemblerClass.LIGHT), SizeClass.S, MissileParts.Motor.SRM_I, MissileParts.Guidance.UNGUIDED, false)));
        helper.assertTrue("he_warhead".equals(silo.gripsFor()), "set for the HE missile: " + silo.gripsFor());
        silo.inventory().setItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE, ItemStack.EMPTY);
        helper.assertTrue("he_warhead".equals(silo.gripsFor()), "still set for it once it has gone: " + silo.gripsFor());
        silo.inventory().setItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE, MissileAssembly.missile(new MissileSpec(
                warheadOf(AssemblerClass.HEAVY), SizeClass.L, MissileParts.Motor.SRM_II, MissileParts.Guidance.UNGUIDED, false)));
        helper.assertTrue("massive_warhead".equals(silo.gripsFor()), "and for the next: " + silo.gripsFor());
        helper.succeed();
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /** An assembler built round a controller at (8, 1, 1) facing north (its structure reaches back to z 9 and up 13). */
    static MissileAssemblerBlockEntity assembler(GameTestHelper helper, AssemblerClass kind, boolean build) {
        floor(helper);
        BlockPos rel = new BlockPos(8, 1, 1);
        helper.setBlock(rel, GBAssemblers.controller(kind).block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        MultiblockDefinition def = GBAssemblers.definition(kind);
        if (build) {
            for (MultiblockDefinition.Cell cell : def.cells(helper.absolutePos(rel), Direction.NORTH)) {
                if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                    helper.getLevel().setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
                }
            }
        }
        var be = (MissileAssemblerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(be != null && be.kind() == kind, "a " + kind + " controller");
        be.revalidate();
        helper.assertTrue(be.isFormed() == build, kind + " formed: " + be.isFormed() + " " + def.check(helper.getLevel(), be.getBlockPos(), Direction.NORTH));
        return be;
    }

    /** A warhead of each class, its body and the longest-range motor that fits it. */
    private static ItemStack warheadOf(AssemblerClass kind) {
        return new ItemStack((switch (kind) {
            case LIGHT -> GBOrdnance.HE_WARHEAD;
            case MEDIUM -> GBOrdnance.GAS_WARHEAD;
            case HEAVY -> GBOrdnance.MASSIVE_WARHEAD;
            case STRATEGIC -> GBOrdnance.FISSION_WARHEAD;
        }).item().get());
    }

    private static void load(MissileAssemblerBlockEntity a, ItemStack warhead, ItemStack guidance, ItemStack module) {
        SizeClass size = a.kind().size;
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_WARHEAD, warhead);
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_BODY, new ItemStack(GBAssembly.BODIES.get(size.ordinal()).get()));
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_MOTOR,
                new ItemStack(GBAssembly.MOTORS.get(MissileAssembly.rocketsFor(size).getLast().ordinal()).get()));
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_GUIDANCE, guidance);
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_MODULE, module);
    }

    /** Each assembler builds its class's missile (the warhead's own), every part used, the whole missile on show. */
    private static void builds(GameTestHelper helper, AssemblerClass kind) {
        MissileAssemblerBlockEntity a = assembler(helper, kind, true);
        a.energy().generate(kind.capacity);
        ItemStack warhead = warheadOf(kind);
        warhead.set(GBDataComponents.FUZE.get(), FuzeSettings.of(Fuze.BAROMETRIC, 40));
        load(a, warhead, new ItemStack(GBAssembly.GPS_GUIDANCE.get()), new ItemStack(GBAssembly.HARDENED_ELECTRONICS.get()));
        helper.assertTrue(a.startAssembly(), "assembly starts: " + a.problem());
        a.skipToEndForTest();
        helper.succeedWhen(() -> {
            MissileSpec s = MissileItem.spec(a.inventory().getItem(MissileAssemblerBlockEntity.SLOT_OUTPUT));
            helper.assertTrue(s != null, "a missile");
            helper.assertTrue(s.warhead().is(warheadOf(kind).getItem()) && s.body() == kind.size
                    && s.motor() == MissileAssembly.rocketsFor(kind.size).getLast() && s.guidance() == MissileParts.Guidance.GPS && s.hardened(),
                    "its parts: " + s);
            helper.assertTrue(s.warhead().getOrDefault(GBDataComponents.FUZE.get(), FuzeSettings.IMPACT).value() == 40, "the fuze kept its setting");
            for (int i = MissileAssemblerBlockEntity.SLOT_WARHEAD; i <= MissileAssemblerBlockEntity.SLOT_MODULE; i++) {
                helper.assertTrue(a.inventory().getItem(i).isEmpty(), "part slot " + i + " used");
            }
            var shown = a.shownMissile();
            helper.assertTrue(a.laid() == MissileAssemblerBlockEntity.SECTIONS && shown != null && shown.looks().size() == MissileAssemblerBlockEntity.SECTIONS
                    && shown.lying() == kind.lying && shown.warhead().equals(s.warheadId()), "the finished missile on show: " + shown);
        });
    }

    /**
     * AUTO ASSEMBLE: with it on, a job starts by itself once the parts are in; the next waits while the finished missile
     * sits in the output, and starts as soon as it is taken out (fresh parts already fed in).
     */
    private static void auto(GameTestHelper helper) {
        MissileAssemblerBlockEntity a = assembler(helper, AssemblerClass.LIGHT, true);
        a.energy().generate(AssemblerClass.LIGHT.capacity);
        helper.assertTrue(a.handleButton(MissileAssemblerBlockEntity.BUTTON_AUTO) && a.autoAssemble(), "auto on");
        load(a, warheadOf(AssemblerClass.LIGHT), ItemStack.EMPTY, ItemStack.EMPTY);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(a.assembling(), "it started by itself");
            a.skipToEndForTest();
        });
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(MissileItem.spec(a.inventory().getItem(MissileAssemblerBlockEntity.SLOT_OUTPUT)) != null, "the first is done");
            load(a, warheadOf(AssemblerClass.LIGHT), ItemStack.EMPTY, ItemStack.EMPTY);   // fresh parts, as a pipe would bring them
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!a.assembling(), "the next waits while the finished one is in the output");
            a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        });
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(a.assembling(), "and starts once it is taken out");
            helper.succeed();
        });
    }

    /** While it works, sections appear one by one: none at the start, all three by the end. */
    private static void sections(GameTestHelper helper) {
        MissileAssemblerBlockEntity a = assembler(helper, AssemblerClass.LIGHT, true);
        a.energy().generate(AssemblerClass.LIGHT.capacity);
        load(a, warheadOf(AssemblerClass.LIGHT), ItemStack.EMPTY, ItemStack.EMPTY);
        helper.assertTrue(a.startAssembly() && a.laid() == 0, "started, nothing laid yet");
        helper.runAfterDelay(AssemblerClass.LIGHT.ticks / 2, () -> helper.assertTrue(a.laid() >= 1 && a.laid() < MissileAssemblerBlockEntity.SECTIONS,
                "half way: " + a.laid() + " sections"));
        helper.succeedWhen(() -> helper.assertTrue(!a.assembling() && a.laid() == MissileAssemblerBlockEntity.SECTIONS
                && !a.inventory().getItem(MissileAssemblerBlockEntity.SLOT_OUTPUT).isEmpty(), "finished: " + a.laid()));
    }

    /** With its structure unbuilt it will not start, and says so. */
    private static void needsStructure(GameTestHelper helper) {
        MissileAssemblerBlockEntity a = assembler(helper, AssemblerClass.MEDIUM, false);
        load(a, warheadOf(AssemblerClass.MEDIUM), ItemStack.EMPTY, ItemStack.EMPTY);
        helper.assertTrue(a.problem() == MissileAssembly.Problem.NOT_FORMED, "not formed: " + a.problem());
        helper.assertTrue(!a.startAssembly(), "it will not start");
        helper.succeed();
    }

    /** The Medium Missile Assembler takes Medium warheads and its own body only: not a Heavy warhead nor a Tactical body. */
    private static void ownClass(GameTestHelper helper) {
        MissileAssemblerBlockEntity a = assembler(helper, AssemblerClass.MEDIUM, true);
        var inv = a.inventory();
        helper.assertTrue(!inv.canPlaceItem(MissileAssemblerBlockEntity.SLOT_WARHEAD, new ItemStack(GBOrdnance.MASSIVE_WARHEAD.item().get())),
                "no slot for a class L warhead");
        helper.assertTrue(!inv.canPlaceItem(MissileAssemblerBlockEntity.SLOT_WARHEAD, new ItemStack(GBOrdnance.THERMOBARIC_WARHEAD.item().get())),
                "nor the thermobaric one (class L now)");
        helper.assertTrue(inv.canPlaceItem(MissileAssemblerBlockEntity.SLOT_WARHEAD, new ItemStack(GBOrdnance.CLUSTER_WARHEAD.item().get())),
                "a class M one goes in");
        helper.assertTrue(!inv.canPlaceItem(MissileAssemblerBlockEntity.SLOT_BODY, new ItemStack(GBAssembly.TACTICAL_BODY.get())), "not a Tactical body");
        helper.assertTrue(!inv.canPlaceItem(MissileAssemblerBlockEntity.SLOT_OUTPUT, MissileAssembly.missile(new MissileSpec(
                warheadOf(AssemblerClass.LIGHT), SizeClass.S, MissileParts.Motor.SRM_I, MissileParts.Guidance.UNGUIDED, false))), "nor a Light missile");
        // Set straight in (as a command might), a warhead of another class is named for what it is and where it goes.
        load(a, new ItemStack(GBOrdnance.MASSIVE_WARHEAD.item().get()), ItemStack.EMPTY, ItemStack.EMPTY);
        helper.assertTrue(a.problem() == MissileAssembly.Problem.WRONG_CLASS, "the wrong class: " + a.problem());
        helper.assertTrue(!a.startAssembly(), "it will not start");
        helper.succeed();
    }

    /** A motor that does not fit the class's body (set straight in) stops it. */
    private static void motorFit(GameTestHelper helper) {
        MissileAssemblerBlockEntity a = assembler(helper, AssemblerClass.MEDIUM, true);
        load(a, warheadOf(AssemblerClass.MEDIUM), ItemStack.EMPTY, ItemStack.EMPTY);
        helper.assertTrue(!a.inventory().canPlaceItem(MissileAssemblerBlockEntity.SLOT_MOTOR, new ItemStack(GBAssembly.LIQUID_FUEL_STAGE.get())),
                "no slot for a Liquid-fuel Stage");
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_MOTOR, new ItemStack(GBAssembly.LIQUID_FUEL_STAGE.get()));
        helper.assertTrue(a.problem() == MissileAssembly.Problem.MOTOR_DOES_NOT_FIT, "does not fit: " + a.problem());
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_MOTOR, new ItemStack(GBAssembly.SRM_I.get()));
        helper.assertTrue(a.problem() == MissileAssembly.Problem.NONE, "a Solid Rocket Motor I fits: " + a.problem());
        helper.succeed();
    }

    /** A missile comes apart into its warhead (fuze and all), body, motor and guidance (no module: none fitted). */
    private static void takesApart(GameTestHelper helper) {
        MissileAssemblerBlockEntity a = assembler(helper, AssemblerClass.LIGHT, true);
        ItemStack w = warheadOf(AssemblerClass.LIGHT);
        w.set(GBDataComponents.FUZE.get(), FuzeSettings.of(Fuze.BAROMETRIC, 25));
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_OUTPUT, MissileAssembly.missile(new MissileSpec(w, SizeClass.S,
                MissileParts.Motor.SRM_I, MissileParts.Guidance.INERTIAL, false)));
        helper.assertTrue(a.laid() == MissileAssemblerBlockEntity.SECTIONS, "a finished missile lies on the cradles");
        helper.assertTrue(a.takeApart(), "taken apart");
        var inv = a.inventory();
        helper.assertTrue(inv.getItem(MissileAssemblerBlockEntity.SLOT_WARHEAD).getOrDefault(GBDataComponents.FUZE.get(), FuzeSettings.IMPACT).value() == 25,
                "the warhead, its fuze set as it was");
        helper.assertTrue(inv.getItem(MissileAssemblerBlockEntity.SLOT_BODY).is(GBAssembly.TACTICAL_BODY.get()), "the body");
        helper.assertTrue(inv.getItem(MissileAssemblerBlockEntity.SLOT_MOTOR).is(GBAssembly.SRM_I.get()), "the motor");
        helper.assertTrue(inv.getItem(MissileAssemblerBlockEntity.SLOT_GUIDANCE).is(GBAssembly.INERTIAL_GUIDANCE.get()), "the guidance");
        helper.assertTrue(inv.getItem(MissileAssemblerBlockEntity.SLOT_MODULE).isEmpty(), "no module");
        helper.assertTrue(inv.getItem(MissileAssemblerBlockEntity.SLOT_OUTPUT).isEmpty() && a.laid() == 0, "the missile is gone, the cradles clear");
        helper.succeed();
    }

    /** What an older world left in the bench's old missile page (slots 4-9) is dropped beside it, and nothing goes back in. */
    private static void benchDrops(GameTestHelper helper) {
        floor(helper);
        BlockPos rel = new BlockPos(8, 1, 8);
        Block block = GBAssembly.ASSEMBLY_BENCH.block().get();
        helper.setBlock(rel, block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        helper.setBlock(rel.west(), block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH)
                .setValue(WideMachineBlock.PART, WideMachineBlock.Part.SIDE));
        var b = (AssemblyBenchBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(b != null, "a bench");
        b.inventory().setItem(4, new ItemStack(GBOrdnance.HE_WARHEAD.item().get()));
        b.inventory().setItem(5, new ItemStack(GBAssembly.TACTICAL_BODY.get()));
        helper.assertTrue(!b.inventory().canPlaceItem(6, new ItemStack(GBAssembly.SRM_I.get())), "the old slots take nothing");
        helper.succeedWhen(() -> {
            helper.assertTrue(b.inventory().getItem(4).isEmpty() && b.inventory().getItem(5).isEmpty(), "emptied");
            var around = new AABB(helper.absolutePos(rel)).inflate(3);
            var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
            helper.assertTrue(dropped.stream().anyMatch(e -> e.getItem().is(GBOrdnance.HE_WARHEAD.item().get()))
                    && dropped.stream().anyMatch(e -> e.getItem().is(GBAssembly.TACTICAL_BODY.get())), "dropped beside it: " + dropped);
        });
    }
}
