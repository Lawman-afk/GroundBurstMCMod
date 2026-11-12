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
    private static final String INFO = GBLang.add(GroundBurst.MODID + ".command.blast.info", "%s as this server sets it off, blocks: %s");
    private static final String RINGS = GBLang.add(GroundBurst.MODID + ".command.blast.rings",
            "Its far aftermath: charred to %s blocks, broken to %s, withered to %s.");
    private static final DynamicCommandExceptionType UNKNOWN_PROFILE = new DynamicCommandExceptionType(id -> Component.translatable(UNKNOWN, id));

    private GBBlast() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((DataPackRegistryEvent.NewRegistry event) -> event.dataPackRegistry(BlastProfile.REGISTRY, BlastProfile.CODEC, BlastProfile.CODEC));
        // The terrain tables (DESIGN.md §21.10): the server's only (no network codec: clients never need them).
        modBus.addListener((DataPackRegistryEvent.NewRegistry event) -> event.dataPackRegistry(TerrainTable.REGISTRY, TerrainTable.CODEC));
        // The swap lines read again whenever the server config loads or changes.
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Loading event) -> {
            if (event.getConfig().getSpec() == dev.groundburst.config.GBServerConfig.SPEC) {
                TerrainRules.reload();
            }
        });
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == dev.groundburst.config.GBServerConfig.SPEC) {
                TerrainRules.reload();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent event) -> TerrainTables.clear());
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
                        .then(Commands.literal("info").then(Commands.argument("profile", ResourceKeyArgument.key(BlastProfile.REGISTRY))
                                .executes(GBBlast::info)))
                        .then(Commands.argument("profile", ResourceKeyArgument.key(BlastProfile.REGISTRY))
                                .executes(ctx -> blast(ctx, ctx.getSource().getPosition()))
                                .then(Commands.argument("position", Vec3Argument.vec3())
                                        .executes(ctx -> blast(ctx, Vec3Argument.getVec3(ctx, "position")))))));
    }

    /**
     * {@code /groundburst blast info <profile>}: a profile as the server will set it off (its settings by weapon and kind,
     * the radius multiplier and cap; DESIGN.md §21.10), with its far aftermath's rings if it has any.
     */
    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceKey<BlastProfile> key = dev.groundburst.port.Args.getRegistryKey(ctx, "profile", BlastProfile.REGISTRY, UNKNOWN_PROFILE);
        var found = ctx.getSource().getLevel().registryAccess().registryOrThrow(BlastProfile.REGISTRY).getOptional(key);
        if (found.isEmpty()) {
            throw UNKNOWN_PROFILE.create(key.location());
        }
        BlastProfile p = BlastSettings.asSetOff(key.location(), found.get(), 1);
        java.util.Locale l = java.util.Locale.ROOT;
        String zones = String.format(l, "core %.0f, crater %.0f (depth %.2f), shock %.0f, scorch %.0f, fire %.0f, lethal %.0f, hurts to %.0f",
                p.core(), p.crater(), p.craterDepth(), p.structural(), p.scorch(), p.fire(), p.lethal(), p.damage());
        String rest = String.format(l, "lethal damage %.0f, edge damage %.0f, knockback %.1f, front %.1f blocks a tick, streaks %.2f, lobes %.2f, "
                + "ejecta %.2f, drops %.2f, terrain %s", p.lethalDamage(), p.edgeDamage(), p.knockback(), p.frontSpeed(), p.streaks(), p.lobes(),
                p.ejecta(), p.drops(), p.terrain());
        ctx.getSource().sendSuccess(() -> Component.translatable(INFO, key.location().toString(), zones), false);
        ctx.getSource().sendSuccess(() -> Component.literal(rest), false);
        float[] rings = dev.groundburst.content.fallout.FarAftermath.rings(key.location(), p);
        if (rings != null) {
            ctx.getSource().sendSuccess(() -> Component.translatable(RINGS, Math.round(rings[0]), Math.round(rings[1]), Math.round(rings[2])), false);
        }
        return 1;
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
