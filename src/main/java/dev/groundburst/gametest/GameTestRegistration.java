package dev.groundburst.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * How the tests {@link GBGameTests} lists reach the game here: each test's function in the vanilla TEST_FUNCTION
 * registry, and a test instance (function, structure, timeout) on {@link RegisterGameTestsEvent}. (The 1.21.1 build has
 * its own, a generator of that version's test functions.)
 */
final class GameTestRegistration {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, GroundBurst.MODID);

    private record Registered(GBGameTests.TestSpec spec, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function) {}

    private static final List<Registered> REGISTERED = new ArrayList<>();

    private GameTestRegistration() {}

    static void register(IEventBus modBus, List<GBGameTests.TestSpec> specs) {
        for (GBGameTests.TestSpec spec : specs) {
            REGISTERED.add(new Registered(spec, FUNCTIONS.register(spec.name(), spec::body)));
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(GameTestRegistration::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(GroundBurst.id("default"));
        for (Registered r : REGISTERED) {
            GBGameTests.TestSpec spec = r.spec();
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, spec.structure(), spec.maxTicks(), 0, true, net.minecraft.world.level.block.Rotation.NONE, false, 1, 1, spec.sky(), 0);
            event.registerTest(r.function().getId(), new FunctionGameTestInstance(r.function().getKey(), data));
        }
    }
}
