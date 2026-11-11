package dev.groundburst.blast;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.DataPackRegistryEvent;

/** Wiring for the explosion engine: the blast profile registry, the tick hooks and the {@code /groundburst blast} command. */
public final class GBBlast {
    private static final String UNKNOWN = GBLang.add(GroundBurst.MODID + ".command.blast.unknown", "Unknown blast profile: %s");
    private static final String STARTED = GBLang.add(GroundBurst.MODID + ".command.blast.started", "Set off %s at %s, %s, %s");
    private static final DynamicCommandExceptionType UNKNOWN_PROFILE = new DynamicCommandExceptionType(id -> Component.translatable(UNKNOWN, id));

    private GBBlast() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((DataPackRegistryEvent.NewRegistry event) -> event.dataPackRegistry(BlastProfile.REGISTRY, BlastProfile.CODEC, BlastProfile.CODEC));
        GBLang.add("death.attack." + GroundBurst.MODID + ".blast", "%1$s was blown to pieces");
        GBLang.add("death.attack." + GroundBurst.MODID + ".blast.player", "%1$s was blown to pieces by %2$s");
        GBLang.add("death.attack." + GroundBurst.MODID + ".annihilated", "%1$s was annihilated");
        GBLang.add("death.attack." + GroundBurst.MODID + ".annihilated.player", "%1$s was annihilated by %2$s's antimatter");
        GBLang.add("death.attack." + GroundBurst.MODID + ".spaghettified", "%1$s was spaghettified");
        GBLang.add("death.attack." + GroundBurst.MODID + ".spaghettified.player", "%1$s was spaghettified by %2$s's singularity");
        GBLang.add("death.attack." + GroundBurst.MODID + ".flash_frozen", "%1$s was frozen solid");
        GBLang.add("death.attack." + GroundBurst.MODID + ".flash_frozen.player", "%1$s was frozen solid by %2$s's cryo bomb");
        GBLang.add("death.attack." + GroundBurst.MODID + ".particle_beam", "%1$s was vaporized by a particle beam");
        GBLang.add("death.attack." + GroundBurst.MODID + ".particle_beam.player", "%1$s was vaporized by %2$s's particle beam");
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent event) -> { if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return; BlastManager.resetBudget(); });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.LevelTickEvent event) -> {if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            if (((Object) event.level) instanceof ServerLevel level) {
                Annihilations.tick(level);                  // short-lived: ahead of the blasts in the budget
                Singularities.tick(level);
                Storms.tick(level);
                Freezes.tick(level);
                ParticleBeams.tick(level);
                BlastManager.of(level).tick();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (((Object) event.getLevel()) instanceof ServerLevel level) {
                BlastManager.forget(level);
                Annihilations.forget(level);
                Singularities.forget(level);
                Storms.forget(level);
                Freezes.forget(level);
                ParticleBeams.forget(level);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> commands(event.getDispatcher()));
        modBus.addListener((dev.groundburst.legacy.RegisterTicketControllersEvent event) -> event.register(BlastChunks.TICKETS));
        PlayBench.register();
        // Players arriving while blasts run are told about them (sound timing, the shockwave in slice 5c).
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
            if (((Object) event.getEntity()) instanceof net.minecraft.server.level.ServerPlayer player) {
                BlastManager.of((net.minecraft.server.level.ServerLevel) player.level()).syncTo(player);
                Annihilations.syncTo(player);
                Singularities.syncTo(player);
                Storms.syncTo(player);
                Freezes.syncTo(player);
                ParticleBeams.syncTo(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (((Object) event.getEntity()) instanceof net.minecraft.server.level.ServerPlayer player) {
                BlastManager.of((net.minecraft.server.level.ServerLevel) player.level()).syncTo(player);
                Annihilations.syncTo(player);
                Singularities.syncTo(player);
                Storms.syncTo(player);
                Freezes.syncTo(player);
                ParticleBeams.syncTo(player);
            }
        });
    }

    /** {@code /groundburst blast <profile> [<position>]}: sets off a blast (for testing and for map makers). */
    private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(GroundBurst.MODID)
                .requires(source_ -> source_.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("blast")
                        .then(Commands.argument("profile", ResourceKeyArgument.key(BlastProfile.REGISTRY))
                                .executes(ctx -> blast(ctx, ctx.getSource().getPosition()))
                                .then(Commands.argument("position", Vec3Argument.vec3())
                                        .executes(ctx -> blast(ctx, Vec3Argument.getVec3(ctx, "position")))))));
    }

    private static int blast(CommandContext<CommandSourceStack> ctx, Vec3 at) throws CommandSyntaxException {
        ResourceKey<BlastProfile> key = dev.groundburst.port.Args.getRegistryKey(ctx, "profile", BlastProfile.REGISTRY, UNKNOWN_PROFILE);
        if (Blasts.detonate(ctx.getSource().getLevel(), at, key, ctx.getSource().getEntity()) == null) {
            throw UNKNOWN_PROFILE.create(key.location());
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(STARTED, key.location().toString(),
                Math.round(at.x), Math.round(at.y), Math.round(at.z)), true);
        return 1;
    }
}
