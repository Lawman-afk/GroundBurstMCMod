package dev.groundburst.content.launch;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.effect.EmpFields;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Laser Designator (DESIGN.md §17.13): hold use to lase (held to the eye, zoomed): a beam and a spot every player
 * sees, laser-guided missiles home on the spot, and a Launch Control Station of its network shows it. Its other mode
 * calls fire: held steady on a spot for two seconds it locks, and every armed launcher of its network that reaches the
 * spot is aimed at it and launched. Sneak-use switches mode; used on a station it takes the station's network. It runs
 * on a charge (a Battery Charger fills it) and an EMP knocks it out.
 */
public class LaserDesignatorItem extends Item {
    public static final int CAPACITY = 40_000, TRANSFER = 1_000, USE = 20;
    /** Ticks a spot must be held (within {@link #STEADY} blocks) to lock in call-for-fire mode; ticks between status updates. */
    public static final int LOCK = 40, STATUS = 5, EMP_TICKS = 200;
    private static final double STEADY = 2.0;

    /** What lasing does: guides missiles and marks the map, or calls fire too. */
    public enum Mode { DESIGNATE, FIRE }

    private static final String P = GroundBurst.MODID + ".designator.";
    private static final String T_NETWORK = GBLang.add(P + "network", "Network %s");
    private static final String T_NO_NETWORK = GBLang.add(P + "no_network", "No network: use it on a Launch Control Station");
    private static final String[] T_MODE = {GBLang.add(P + "mode.designate", "Mode: designate"), GBLang.add(P + "mode.fire", "Mode: call for fire")};
    private static final String T_CHARGE = GBLang.add(P + "charge", "Charge: %s%%");
    private static final String T_USE = GBLang.add(P + "use", "Hold use to lase; sneak-use to switch mode");
    private static final String T_FLAT = GBLang.add(P + "flat", "Flat: charge it in a Battery Charger");
    private static final String T_EMP = GBLang.add(P + "emp", "Knocked out by an EMP");
    private static final String T_LINKED = GBLang.add(P + "linked", "Designator linked to network %s");
    private static final String T_SAME = GBLang.add(P + "same", "Already on this station's network (%s)");
    private static final String T_FIRED = GBLang.add(P + "fired", "Fire called: %s launching");
    private static final String T_NONE_ARMED = GBLang.add(P + "none_armed", "Locked: no armed launcher of the network reaches it");

    /** Each lasing player's lock: where it is held, for how long, and whether this lock has called fire already. */
    private record Lock(Vec3 pos, int ticks, boolean fired) {}

    private static final Map<UUID, Lock> LOCKS = new HashMap<>();

    public LaserDesignatorItem(Properties properties) {
        super(properties.stacksTo(1).component(GBDataComponents.ENERGY.get(), CAPACITY).component(GBDataComponents.DESIGNATOR_MODE.get(), 0));
    }

    public static int stored(ItemStack stack) {
        return stack.getOrDefault(GBDataComponents.ENERGY.get(), 0);
    }

    public static Mode mode(ItemStack stack) {
        Mode[] modes = Mode.values();
        return modes[Math.clamp(stack.getOrDefault(GBDataComponents.DESIGNATOR_MODE.get(), 0), 0, modes.length - 1)];
    }

    /** Knocked out by an EMP (for a while after leaving the field). */
    public static boolean knockedOut(ItemStack stack, Level level) {
        return level.getGameTime() < stack.getOrDefault(GBDataComponents.EMP_UNTIL.get(), 0L);
    }

    /** Whether it can lase: charged, and not knocked out. */
    public static boolean works(ItemStack stack, Level level) {
        return stored(stack) > 0 && !knockedOut(stack, level);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72_000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPYGLASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            Mode next = Mode.values()[(mode(stack).ordinal() + 1) % Mode.values().length];
            stack.set(GBDataComponents.DESIGNATOR_MODE.get(), next.ordinal());
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable(T_MODE[next.ordinal()]).withStyle(next == Mode.FIRE ? ChatFormatting.RED : ChatFormatting.GOLD));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), GBSounds.DESIGNATOR_ON.get(), SoundSource.PLAYERS, 0.5f,
                        next == Mode.FIRE ? 0.8f : 1.1f);
            }
            return InteractionResult.SUCCESS;
        }
        if (!works(stack, level)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable(knockedOut(stack, level) ? T_EMP : T_FLAT).withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), GBSounds.DESIGNATOR_ON.get(), SoundSource.PLAYERS, 0.6f, 1f);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    /** Used on a Launch Control Station (its controller or a part): it takes the station's network. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !(LaunchCodesItem.target(level, context.getClickedPos()) instanceof LaunchStationBlockEntity station)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            LaunchNetwork.Network n = station.network(server);
            if (n != null) {
                boolean same = n.code().equals(LaunchCodesItem.code(stack));
                stack.set(GBDataComponents.LAUNCH_CODES.get(), n.code());
                player.sendOverlayMessage(Component.translatable(same ? T_SAME : T_LINKED, n.code()).withStyle(ChatFormatting.GOLD));
                server.playSound(null, context.getClickedPos(), GBSounds.DESIGNATOR_LOCK.get(), SoundSource.PLAYERS, 0.4f, 1.2f);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Its network (0: none, or its code is void). */
    static long network(ServerLevel level, ItemStack stack) {
        LaunchNetwork.Network n = LaunchNetwork.of(level).byCode(LaunchCodesItem.code(stack));
        return n == null ? 0 : n.id;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (!works(stack, level)) {
            player.stopUsingItem();
            return;
        }
        if (!player.isCreative()) {
            stack.set(GBDataComponents.ENERGY.get(), Math.max(0, stored(stack) - USE));
        }
        long net = network(server, stack);
        Designators.Hit hit = Designators.trace(server, player);
        UUID id = player.getUUID();
        if (hit == null) {
            Designators.stop(server, id);
            LOCKS.remove(id);
        } else {
            Designators.lase(server, id, net, hit.pos(), hit.entity() != null);
            if (mode(stack) == Mode.FIRE && net != 0) {
                lockOn(server, player, net, hit.pos());
            } else {
                LOCKS.remove(id);
            }
        }
        if (server.getGameTime() % STATUS == 0) {
            sendStatus(server, player, stack, net, hit);
        }
    }

    /** Call for fire: held steady for {@link #LOCK} ticks, the spot locks and its network's armed launchers fire on it. */
    private static void lockOn(ServerLevel level, ServerPlayer player, long net, Vec3 spot) {
        UUID id = player.getUUID();
        Lock lock = LOCKS.get(id);
        if (lock == null || lock.pos().distanceTo(spot) > STEADY) {
            LOCKS.put(id, new Lock(spot, 1, false));
            return;
        }
        Lock next = new Lock(lock.pos(), lock.ticks() + 1, lock.fired());
        LOCKS.put(id, next);
        if (next.ticks() == LOCK && !next.fired()) {
            LOCKS.put(id, new Lock(next.pos(), next.ticks(), true));
            int n = LaunchNetwork.of(level).callFire(level, net, BlockPos.containing(spot), player);
            player.sendOverlayMessage(n > 0 ? Component.translatable(T_FIRED, n).withStyle(ChatFormatting.RED)
                    : Component.translatable(T_NONE_ARMED).withStyle(ChatFormatting.GRAY));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), n > 0 ? GBSounds.DESIGNATOR_FIRE.get() : GBSounds.DESIGNATOR_LOCK.get(),
                    SoundSource.PLAYERS, 0.7f, 1f);
        } else if (next.ticks() == LOCK / 2) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), GBSounds.DESIGNATOR_LOCK.get(), SoundSource.PLAYERS, 0.35f, 0.8f);
        }
    }

    /** What its screen shows that only the server knows: the lock, the armed launchers reaching the spot, the missiles homing. */
    private static void sendStatus(ServerLevel level, ServerPlayer player, ItemStack stack, long net, Designators.@Nullable Hit hit) {
        if (player.connection == null || !player.connection.hasChannel(DesignatorPayloads.Status.TYPE)) {
            return;                                        // (a GameTest's player: nothing to show it on)
        }
        Lock lock = LOCKS.get(player.getUUID());
        float locked = lock == null ? 0 : Math.min(1, lock.ticks() / (float) LOCK);
        int armed = hit == null || net == 0 ? 0 : LaunchNetwork.of(level).armedReaching(net, BlockPos.containing(hit.pos()));
        int charge = (int) ((long) stored(stack) * 100 / CAPACITY);
        PacketDistributor.sendToPlayer(player, new DesignatorPayloads.Status(mode(stack).ordinal(), locked, lock != null && lock.fired(), armed,
                Designators.homingOn(level, player.getUUID()), net != 0 ? LaunchCodesItem.code(stack) : "", charge));
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        stopLasing(level, entity);
        return true;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        stopLasing(level, entity);
        return stack;
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        stopLasing(entity.level(), entity);
    }

    private static void stopLasing(Level level, Entity entity) {
        if (level instanceof ServerLevel server) {
            Designators.stop(server, entity.getUUID());
            LOCKS.remove(entity.getUUID());
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (level.getGameTime() % 20 == 0 && owner instanceof Player && EmpFields.inField(level, owner.position())) {
            stack.set(GBDataComponents.EMP_UNTIL.get(), level.getGameTime() + EMP_TICKS);
        }
    }

    static void clear() {
        LOCKS.clear();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        String code = LaunchCodesItem.code(stack);
        tooltip.accept(code.isEmpty() ? Component.translatable(T_NO_NETWORK).withStyle(ChatFormatting.GRAY)
                : Component.translatable(T_NETWORK, code).withStyle(ChatFormatting.GOLD));
        Mode m = mode(stack);
        tooltip.accept(Component.translatable(T_MODE[m.ordinal()]).withStyle(m == Mode.FIRE ? ChatFormatting.RED : ChatFormatting.YELLOW));
        tooltip.accept(Component.translatable(T_CHARGE, (int) ((long) stored(stack) * 100 / CAPACITY)).withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable(T_USE).withStyle(ChatFormatting.DARK_GRAY));
    }

}
