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
 * sees, and a Launch Control Station of its network shows it. A left click while lasing acts by its mode: designate
 * aims every launcher of its network loaded with a laser-guided missile at the spot and arms it; call for fire launches
 * them, and their missiles then follow the laser of the player who called fire: they go where it points. Sneak-use
 * switches mode; used on a station it takes the station's network. It runs on a charge (a Battery Charger fills it) and
 * an EMP knocks it out.
 */
public class LaserDesignatorItem extends Item {
    public static final int CAPACITY = 40_000, TRANSFER = 1_000, USE = 20;
    /** Ticks between status updates; ticks an EMP knocks it out for; ticks a left click's result shows. */
    public static final int STATUS = 5, EMP_TICKS = 200, RESULT_TICKS = 40;
    /** Ticks between left clicks it answers (a held button repeats). */
    private static final int CLICK_GAP = 4;

    /** What a left click does: arm the network's laser-guided launchers on the spot, or launch them. */
    public enum Mode { DESIGNATE, FIRE }

    /** A left click's result, for its holder's sight (its ordinal goes over the wire). */
    public enum Result { NONE, DESIGNATED, NONE_IN_REACH, FIRED, NONE_ARMED, NO_RETURN, NO_NETWORK }

    private static final String P = GroundBurst.MODID + ".designator.";
    private static final String T_NETWORK = GBLang.add(P + "network", "Network %s");
    private static final String T_NO_NETWORK = GBLang.add(P + "no_network", "No network: use it on a Launch Control Station");
    private static final String[] T_MODE = {GBLang.add(P + "mode.designate", "Mode: designate"), GBLang.add(P + "mode.fire", "Mode: call for fire")};
    private static final String T_CHARGE = GBLang.add(P + "charge", "Charge: %s%%");
    private static final String T_USE = GBLang.add(P + "use", "Hold use to lase, left-click to designate or fire; sneak-use to switch mode");
    private static final String T_FLAT = GBLang.add(P + "flat", "Flat: charge it in a Battery Charger");
    private static final String T_EMP = GBLang.add(P + "emp", "Knocked out by an EMP");
    private static final String T_LINKED = GBLang.add(P + "linked", "Designator linked to network %s");
    private static final String T_SAME = GBLang.add(P + "same", "Already on this station's network (%s)");

    /** Each holder's last left click: when, what came of it, and how many launchers it took. */
    private record Click(long time, Result result, int count) {}

    private static final Map<UUID, Click> CLICKS = new HashMap<>();
    /** Each lasing player's beam as last sent to those who see it ({@link #NOWHERE}: on nothing). */
    private static final Map<UUID, Vec3> LAST_SENT = new HashMap<>();
    private static final Vec3 NOWHERE = new Vec3(Double.NaN, Double.NaN, Double.NaN);
    /** The photographers' runs (a development aid): the holder is sent its own beam too, to picture it as others see it. */
    private static final boolean BEAM_TO_SELF = Boolean.getBoolean("groundburst.fxShots");

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
        Designators.Hit hit = Designators.trace(server, player, Designators.range(player));
        beam(player, hit);
        if (hit == null) {
            Designators.stop(server, player.getUUID());
        } else {
            Designators.lase(server, player.getUUID(), net, hit.pos(), hit.entity() != null);
        }
        if (server.getGameTime() % STATUS == 0) {
            sendStatus(server, player, stack, net, hit == null ? null : BlockPos.containing(hit.pos()));
        }
    }

    /**
     * A left click while lasing (its holder's client sends it): designate aims every launcher of the network loaded with
     * a laser-guided missile that reaches the spot at it, and arms it; call for fire launches every one armed, and their
     * missiles follow this player's laser in. Its result shows in the holder's sight at once.
     */
    public static void click(ServerPlayer player) {
        ItemStack stack = player.getUseItem();
        ServerLevel level = player.level();
        if (!player.isUsingItem() || !(stack.getItem() instanceof LaserDesignatorItem) || !works(stack, level)) {
            return;
        }
        long now = level.getGameTime();
        Click last = CLICKS.get(player.getUUID());
        if (last != null && now - last.time() < CLICK_GAP) {
            return;
        }
        long net = network(level, stack);
        Designators.Spot spot = Designators.spotOf(level, player.getUUID());
        Result result;
        int count = 0;
        if (net == 0) {
            result = Result.NO_NETWORK;
        } else if (mode(stack) == Mode.DESIGNATE) {
            count = spot == null ? 0 : LaunchNetwork.of(level).designate(level, net, BlockPos.containing(spot.pos()), player);
            result = spot == null ? Result.NO_RETURN : count > 0 ? Result.DESIGNATED : Result.NONE_IN_REACH;
        } else {
            count = LaunchNetwork.of(level).fireLaser(level, net, player);
            result = count > 0 ? Result.FIRED : Result.NONE_ARMED;
        }
        CLICKS.put(player.getUUID(), new Click(now, result, count));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), result == Result.FIRED ? GBSounds.DESIGNATOR_FIRE.get()
                : GBSounds.DESIGNATOR_LOCK.get(), SoundSource.PLAYERS, 0.6f, result == Result.DESIGNATED || result == Result.FIRED ? 1.2f : 0.7f);
        sendStatus(level, player, stack, net, spot == null ? null : BlockPos.containing(spot.pos()));
    }

    /** A holder's last left click's result while it shows (null: none lately). */
    static @Nullable Click lastClick(ServerPlayer player) {
        Click c = CLICKS.get(player.getUUID());
        return c != null && player.level().getGameTime() - c.time() <= RESULT_TICKS ? c : null;
    }

    /**
     * The beam for every player who can see its holder (not the holder: its own client traces its own, exactly): where it
     * lands, or that it lands on nothing. Sent each tick it moves, and every few ticks while it is still.
     */
    private static void beam(ServerPlayer player, Designators.@Nullable Hit hit) {
        Vec3 at = hit == null ? null : hit.surface();
        Vec3 last = LAST_SENT.get(player.getUUID());
        long now = player.level().getGameTime();
        boolean moved = at == null ? last != NOWHERE : last == null || last == NOWHERE || last.distanceToSqr(at) > 1e-4;
        if (!moved && now % STATUS != 0) {
            return;
        }
        LAST_SENT.put(player.getUUID(), at == null ? NOWHERE : at);
        DesignatorPayloads.Beam payload = at == null ? new DesignatorPayloads.Beam(player.getId(), false, 0, 0, 0)
                : new DesignatorPayloads.Beam(player.getId(), true, at.x, at.y, at.z);
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);
        if (BEAM_TO_SELF && player.connection != null && player.connection.hasChannel(DesignatorPayloads.Beam.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * What its sight shows that only the server knows: the network's laser-guided launchers that could be armed on the
     * spot and those armed, the missiles homing on it, and its last left click's result.
     */
    private static void sendStatus(ServerLevel level, ServerPlayer player, ItemStack stack, long net, @Nullable BlockPos spot) {
        if (player.connection == null || !player.connection.hasChannel(DesignatorPayloads.Status.TYPE)) {
            return;                                        // (a GameTest's player: nothing to show it on)
        }
        int[] counts = net == 0 ? new int[2] : LaunchNetwork.of(level).laserCounts(net, spot);
        int charge = (int) ((long) stored(stack) * 100 / CAPACITY);
        Click click = lastClick(player);
        PacketDistributor.sendToPlayer(player, new DesignatorPayloads.Status(mode(stack).ordinal(), counts[0], counts[1],
                Designators.homingOn(level, player.getUUID()), net != 0 ? LaunchCodesItem.code(stack) : "", charge,
                click == null ? Result.NONE.ordinal() : click.result().ordinal(), click == null ? 0 : click.count()));
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
            LAST_SENT.remove(entity.getUUID());
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (level.getGameTime() % 20 == 0 && owner instanceof Player && EmpFields.inField(level, owner.position())) {
            stack.set(GBDataComponents.EMP_UNTIL.get(), level.getGameTime() + EMP_TICKS);
        }
    }

    static void clear() {
        CLICKS.clear();
        LAST_SENT.clear();
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
