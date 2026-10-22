package dev.groundzero.blast;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;

/** Wiring for the explosion engine: the blast profile registry, the tick hooks and the {@code /groundzero blast} command. */
public final class GZBlast {
    private static final String UNKNOWN = GZLang.add(GroundZero.MODID + ".command.blast.unknown", "Unknown blast profile: %s");
    private static final String STARTED = GZLang.add(GroundZero.MODID + ".command.blast.started", "Set off %s at %s, %s, %s");
    private static final DynamicCommandExceptionType UNKNOWN_PROFILE = new DynamicCommandExceptionType(id -> Component.translatable(UNKNOWN, id));

    private GZBlast() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((NewDatapackRegistryEvent event) -> event.worldRegistry(BlastProfile.REGISTRY, BlastProfile.CODEC, BlastProfile.CODEC));
        GZLang.add("death.attack." + GroundZero.MODID + ".blast", "%1$s was blown to pieces");
        GZLang.add("death.attack." + GroundZero.MODID + ".blast.player", "%1$s was blown to pieces by %2$s");
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> BlastManager.resetBudget());
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                BlastManager.of(level).tick();
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                BlastManager.forget(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> commands(event.getDispatcher()));
        modBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent event) -> event.register(BlastChunks.TICKETS));
        BlastBench.register();
        // Players arriving while blasts run are told about them (sound timing, the shockwave in slice 5c).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                BlastManager.of(player.level()).syncTo(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                BlastManager.of(player.level()).syncTo(player);
            }
        });
    }

    /** {@code /groundzero blast <profile> [<position>]}: sets off a blast (for testing and for map makers). */
    private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(GroundZero.MODID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("blast")
                        .then(Commands.argument("profile", ResourceKeyArgument.key(BlastProfile.REGISTRY))
                                .executes(ctx -> blast(ctx, ctx.getSource().getPosition()))
                                .then(Commands.argument("position", Vec3Argument.vec3())
                                        .executes(ctx -> blast(ctx, Vec3Argument.getVec3(ctx, "position")))))));
    }

    private static int blast(CommandContext<CommandSourceStack> ctx, Vec3 at) throws CommandSyntaxException {
        ResourceKey<BlastProfile> key = ResourceKeyArgument.getRegistryKey(ctx, "profile", BlastProfile.REGISTRY, UNKNOWN_PROFILE);
        if (Blasts.detonate(ctx.getSource().getLevel(), at, key, ctx.getSource().getEntity()) == null) {
            throw UNKNOWN_PROFILE.create(key.identifier());
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(STARTED, key.identifier().toString(),
                Math.round(at.x), Math.round(at.y), Math.round(at.z)), true);
        return 1;
    }
}
