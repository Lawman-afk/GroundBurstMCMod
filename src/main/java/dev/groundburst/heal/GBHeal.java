package dev.groundburst.heal;

import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBServerConfig;
import dev.groundburst.registry.GBLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import dev.groundburst.legacy.AttachmentType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
import dev.groundburst.legacy.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Wiring for healing (DESIGN.md §21.9): the chunk record, the hooks that keep the healer's list of damaged chunks
 * and tell it what players and the aftermath do, and {@code /groundburst heal}.
 */
public final class GBHeal {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(dev.groundburst.legacy.Attachments.REGISTRY_KEY,
            GroundBurst.MODID);
    /** A chunk's remembered damage (only on chunks that have some). */
    public static final Supplier<AttachmentType<ChunkScars>> SCARS = ATTACHMENTS.register("scars",
            () -> AttachmentType.builder(ChunkScars::new).serialize(ChunkScars.CODEC.codec()).build());

    private static final String NOW = GBLang.add(GroundBurst.MODID + ".command.heal.now", "Healing %s blocks in %s chunks now.");
    private static final String FORGOT = GBLang.add(GroundBurst.MODID + ".command.heal.forget",
            "Forgot the damage in %s chunks: %s blocks will stay as they are.");
    private static final String NONE = GBLang.add(GroundBurst.MODID + ".command.heal.none", "No damage is waiting to heal in the loaded chunks.");
    private static final String STATUS = GBLang.add(GroundBurst.MODID + ".command.heal.status",
            "%s blocks wait to heal in %s loaded chunks (%s of them healing now).");
    private static final String NEAREST_NOW = GBLang.add(GroundBurst.MODID + ".command.heal.nearest_now",
            "The nearest damage, round %s %s, is healing now.");
    private static final String NEAREST_IN = GBLang.add(GroundBurst.MODID + ".command.heal.nearest_in",
            "The nearest damage, round %s %s, begins to heal in %s.");
    private static final String NEAREST_WAITS = GBLang.add(GroundBurst.MODID + ".command.heal.nearest_waits",
            "The nearest damage, round %s %s, waits for %s.");
    private static final String MORNING = GBLang.add(GroundBurst.MODID + ".command.heal.morning", "the morning");
    private static final String COMMAND = GBLang.add(GroundBurst.MODID + ".command.heal.command", "/groundburst heal now");
    private static final String OFF = GBLang.add(GroundBurst.MODID + ".command.heal.off",
            "Healing is off, so new damage is not remembered: turn on Healing > Heal damage over time in the server config.");
    private static final String MINUTES = GBLang.add(GroundBurst.MODID + ".command.heal.minutes", "%s min %s s");
    private static final String SECONDS = GBLang.add(GroundBurst.MODID + ".command.heal.seconds", "%s s");

    private GBHeal() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Load e) -> {
            if (((Object) e.getLevel()) instanceof ServerLevel level && ((Object) e.getChunk()) instanceof LevelChunk chunk
                    && dev.groundburst.legacy.Attachments.getExistingOrNull(chunk, SCARS.get()) != null) {
                Healer.of(level).track(chunk);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Unload e) -> {
            if (((Object) e.getLevel()) instanceof ServerLevel level && ((Object) e.getChunk()) instanceof LevelChunk chunk) {
                Healer.of(level).untrack(chunk);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.LevelTickEvent e) -> {if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            if (((Object) e.level) instanceof ServerLevel level) {
                Healer.of(level).tick();
                Explosions.forget();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
            if (((Object) e.getLevel()) instanceof ServerLevel level) {
                Healer.forget(level);
            }
        });
        // A player's block over remembered damage (after any other mod has had its say: a cancelled one is not placed).
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BlockEvent.EntityPlaceEvent e) -> {
            if (!(((Object) e.getLevel()) instanceof ServerLevel level) || !(e.getEntity() instanceof Player)) {
                return;
            }
            if (((Object) e) instanceof BlockEvent.EntityMultiPlaceEvent multi) {
                for (BlockSnapshot s : multi.getReplacedBlockSnapshots()) {
                    Scars.claim(level, s.getPos());
                }
            } else {
                Scars.claim(level, e.getPos());
            }
        });
        // Sand and gravel the damage left hanging fall: where they fell from heals, and where they landed.
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (!(((Object) e.getLevel()) instanceof ServerLevel level)) {
                return;
            }
            Entity entity = e.getEntity();
            if ((entity instanceof ItemEntity || entity instanceof ExperienceOrb) && Healer.of(level).hushed(entity.position())) {
                e.setCanceled(true);                       // a decoration taken down to hang again, a block that will heal: no drop
            } else if (((Object) entity) instanceof FallingBlockEntity falling && !e.loadedFromDisk()) {
                Scars.aftermath(level, falling.blockPosition(), falling.getBlockState(), falling.blockData, false);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((EntityLeaveLevelEvent e) -> {
            if (!(((Object) e.getLevel()) instanceof ServerLevel level) || e.getEntity().getRemovalReason() != Entity.RemovalReason.DISCARDED) {
                return;
            }
            if (((Object) e.getEntity()) instanceof FallingBlockEntity falling) {
                if (level.getBlockState(falling.blockPosition()) == falling.getBlockState()) {
                    Scars.aftermath(level, falling.blockPosition(), Blocks.AIR.defaultBlockState(), null, false);
                }
            } else if (((Object) e.getEntity()) instanceof HangingEntity hanging && !hanging.survives()) {
                Scars.lostWall(level, hanging);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> commands(e.getDispatcher()));
    }

    /**
     * {@code /groundburst heal now [radius]}: the damage in the loaded chunks (round you) heals at once;
     * {@code forget [radius]}: it is forgotten and stays; {@code status}: what is waiting.
     */
    private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(GroundBurst.MODID)
                .requires(source_ -> source_.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("heal")
                        .then(Commands.literal("now")
                                .executes(ctx -> now(ctx, -1))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100_000))
                                        .executes(ctx -> now(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("forget")
                                .executes(ctx -> forget(ctx, -1))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100_000))
                                        .executes(ctx -> forget(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("status").executes(GBHeal::status))));
    }

    private static int now(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack source = ctx.getSource();
        int[] done = Healer.of(source.getLevel()).rush(radius < 0 ? null : source.getPosition(), radius);
        if (done[0] == 0) {
            source.sendSuccess(() -> Component.translatable(NONE), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(NOW, done[1], done[0]), true);
        return done[0];
    }

    private static int forget(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack source = ctx.getSource();
        int[] done = Healer.of(source.getLevel()).forgetDamage(radius < 0 ? null : source.getPosition(), radius);
        if (done[0] == 0) {
            source.sendSuccess(() -> Component.translatable(NONE), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(FORGOT, done[0], done[1]), true);
        return done[0];
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!GBServerConfig.HEAL_ENABLED.get()) {
            source.sendSuccess(() -> Component.translatable(OFF), false);
        }
        Vec3 at = source.getPosition();
        Healer.Status s = Healer.of(source.getLevel()).status(at);
        if (s.chunks() == 0) {
            source.sendSuccess(() -> Component.translatable(NONE), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(STATUS, s.blocks(), s.chunks(), s.healing()), false);
        if (s.nearest() != null) {
            int x = s.nearest().getX(), z = s.nearest().getZ();
            Component line;
            if (s.soonest() == 0) {
                line = Component.translatable(NEAREST_NOW, x, z);
            } else if (s.soonest() > 0) {
                line = Component.translatable(NEAREST_IN, x, z, time(s.soonest()));
            } else {
                boolean morning = GBServerConfig.HEAL_SCHEDULE.get() == GBServerConfig.HealSchedule.MORNING;
                line = Component.translatable(NEAREST_WAITS, x, z, Component.translatable(morning ? MORNING : COMMAND));
            }
            source.sendSuccess(() -> line, false);
        }
        return s.chunks();
    }

    private static Component time(long ticks) {
        long seconds = (ticks + 19) / 20;
        return seconds >= 60 ? Component.translatable(MINUTES, seconds / 60, seconds % 60) : Component.translatable(SECONDS, seconds);
    }
}
